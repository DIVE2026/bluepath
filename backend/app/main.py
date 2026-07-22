from __future__ import annotations

import asyncio
import json
import hashlib
import hmac
import secrets
import smtplib
from collections import Counter
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage
from pathlib import Path
from urllib.parse import urlencode

from fastapi import Depends, FastAPI, File, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import delete, func, inspect, or_, select, text, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .config import get_settings
from .database import Base, SessionLocal, engine, get_db
from .models import (
    CommunityBlock,
    CommunityComment,
    CommunityPost,
    CommunityReaction,
    CommunityReport,
    Content,
    DiamondEvidence,
    Follow,
    LearningRecord,
    MissionEvidence,
    PaperLearningEvidence,
    GuardianConsentRequest,
    PortfolioCredential,
    QuizSession,
    UserProgress,
    VideoLearningEvidence,
    PasswordResetToken,
    QuizBankItem,
    Reminder,
    RoutePlan,
    User,
    UserProfile,
)
from .schemas import (
    AdminContentItem,
    AdminQuizItem,
    AgentRequest,
    AgentResponse,
    AiSearchRequest,
    AiSearchResponse,
    AuthRequest,
    AuthResponse,
    CommunityBlockResponse,
    CommunityCommentCreate,
    CommunityCommentItem,
    CommunityCommentUpdate,
    CommunityModerationRequest,
    CommunityPostCreate,
    CommunityPostItem,
    CommunityPostUpdate,
    CommunityReportRequest,
    CloudStateResponse,
    DashboardResponse,
    PasswordResetConfirm,
    PasswordResetRequest,
    DiamondStatus,
    EvidenceRequest,
    EvidenceReviewRequest,
    FollowResponse,
    GenericResponse,
    NicknameAvailability,
    ProfileImageResponse,
    ProfileSummary,
    PaperCompletionRequest,
    PaperCompletionResponse,
    QuizQuestion,
    QuizRequest,
    QuizResponse,
    QuizSubmissionRequest,
    QuizSubmissionResponse,
    ReactionSummary,
    ReactionToggleRequest,
    ReactionToggleResponse,
    ReminderRequest,
    ReminderResponse,
    SyncRequest,
    SyncResponse,
    YouTubeSyncRequest,
    VideoEvidenceRequest,
    VideoEvidenceResponse,
    GuardianConsentRequestCreate,
    GuardianConsentStatus,
    GuardianConsentDetails,
    GuardianConsentConfirm,
    PortfolioIssueRequest,
    PortfolioCredentialResponse,
    FamilyMissionResponse,
    MissionGenerateRequest,
    MissionQrIssueRequest,
    MissionQrIssueResponse,
    MissionVerifyRequest,
    MissionVerifyResponse,
    ProgramDraftRequest,
    ProgramDraftResponse,
    ProgramParticipationRequest,
    ProgramParticipationResponse,
    RouteOutcomeEventRequest,
    RouteActivationRequest,
    RoutePlanRequest,
    RoutePlanResponse,
    RouteRerouteRequest,
    RouteSimulationRequest,
    RouteSimulationResponse,
)
from .security import create_access_token, get_current_user, hash_password, is_admin, require_admin, verify_password
from .services import (
    answer_agent,
    embed_missing_chunks,
    generate_quiz,
    import_content_file,
    import_knowledge_file,
    import_quiz_file,
    seed_contents,
    seed_knowledge,
    search_resources,
    seed_quizzes,
    sync_schedule_feeds,
    sync_youtube,
)
from .voyage import (
    create_route_plan,
    generate_family_mission,
    generate_program_draft,
    issue_mission_qr,
    outcome_analytics,
    record_route_outcome,
    route_plan_response,
    simulate_route_node,
    upsert_program_participation,
    verify_family_mission,
)

settings = get_settings()

TIER_NAMES = {1: "브론즈", 2: "실버", 3: "골드", 4: "플래티넘", 5: "다이아"}
TIER_RANKS = {value: key for key, value in TIER_NAMES.items()}
QUIZ_PASS_COUNTS = {"브론즈": 7, "실버": 9, "골드": 10, "플래티넘": 16}
AUTHORITATIVE_SNAPSHOT_KEYS = {
    "xp", "tier", "quizTierRank", "diamondAdvancedQuizPassed", "skillMastery", "skillEvidence", "skillEvidenceCounts",
    "completedContentIds", "verifiedVideoIds", "verifiedPaperIds", "missionBadges"
}


def get_or_create_progress(db: Session, user_id: str) -> UserProgress:
    progress = db.get(UserProgress, user_id)
    if progress is None:
        progress = UserProgress(user_id=user_id)
        db.add(progress)
        db.flush()
    return progress


def progress_tier(progress: UserProgress, diamond_eligible: bool = False) -> str:
    if diamond_eligible or progress.xp >= 4200:
        return "다이아"
    xp_rank = 4 if progress.xp >= 2800 else 3 if progress.xp >= 1600 else 2 if progress.xp >= 700 else 1
    rank = max(1, min(4, max(progress.quiz_tier_rank, xp_rank)))
    return TIER_NAMES.get(rank, "브론즈")


def apply_authoritative_progress(snapshot: dict, progress: UserProgress, *, diamond_eligible: bool = False) -> dict:
    result = dict(snapshot or {})
    result.update({
        "xp": max(0, progress.xp),
        "quizTierRank": max(1, min(4, progress.quiz_tier_rank)),
        "diamondAdvancedQuizPassed": bool(progress.diamond_advanced_quiz_passed),
        "skillMastery": dict(progress.skill_mastery_json or {}),
        "skillEvidenceCounts": dict(progress.skill_evidence_json or {}),
        "tier": progress_tier(progress, diamond_eligible),
    })
    return result


MINOR_AGE_GROUPS = {"초등학생", "중학생"}

def require_guardian_consent_for_minor(db: Session, user: User) -> None:
    profile = db.get(UserProfile, user.id)
    age_group = str((profile.snapshot or {}).get("ageGroup", "")).strip() if profile else ""
    if age_group in MINOR_AGE_GROUPS and not user.guardian_consent:
        raise HTTPException(status_code=403, detail="Verified guardian consent is required for this minor profile")
STATIC_DIR = Path(__file__).resolve().parents[1] / "static"
UPLOADS_DIR = Path(__file__).resolve().parents[1] / "uploads"
UPLOADS_DIR.mkdir(parents=True, exist_ok=True)
ASSETS_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets"


def apply_compatibility_migrations() -> None:
    """Apply additive columns needed by upgrades without destroying deployed data."""
    inspector = inspect(engine)
    tables = set(inspector.get_table_names())
    statements: list[str] = []
    if "users" in tables:
        columns = {column["name"] for column in inspector.get_columns("users")}
        if "nickname" not in columns:
            statements.append("ALTER TABLE users ADD COLUMN nickname VARCHAR(40)")
        if "profile_image_url" not in columns:
            statements.append("ALTER TABLE users ADD COLUMN profile_image_url TEXT NOT NULL DEFAULT ''")
        if "guardian_consent_version" not in columns:
            statements.append("ALTER TABLE users ADD COLUMN guardian_consent_version VARCHAR(40) NOT NULL DEFAULT ''")
        if "guardian_consented_at" not in columns:
            statements.append("ALTER TABLE users ADD COLUMN guardian_consented_at TIMESTAMP")
    if "user_profiles" in tables:
        columns = {column["name"] for column in inspector.get_columns("user_profiles")}
        if "version" not in columns:
            statements.append("ALTER TABLE user_profiles ADD COLUMN version INTEGER NOT NULL DEFAULT 0")
    if "learning_records" in tables:
        columns = {column["name"] for column in inspector.get_columns("learning_records")}
        if "device_id" not in columns:
            statements.append("ALTER TABLE learning_records ADD COLUMN device_id VARCHAR(80) NOT NULL DEFAULT ''")
    with engine.begin() as connection:
        for statement in statements:
            connection.execute(text(statement))
        if "users" in tables:
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_nickname ON users (nickname)"))
            connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nickname_ci ON users (lower(nickname))"))


async def scheduled_schedule_sync() -> None:
    interval = max(settings.schedule_sync_hours, 0) * 3600
    feeds = settings.schedule_feed_list
    if interval <= 0 or not feeds:
        return
    while True:
        try:
            with SessionLocal() as db:
                await asyncio.to_thread(sync_schedule_feeds, db, feeds, settings.schedule_feed_timeout_seconds)
        except Exception as exc:
            print(f"BluePath scheduled schedule sync failed: {exc}")
        await asyncio.sleep(interval)


async def scheduled_youtube_sync() -> None:
    interval = max(settings.youtube_sync_hours, 0) * 3600
    if interval <= 0 or not settings.youtube_api_key:
        return
    while True:
        await asyncio.sleep(interval)
        try:
            with SessionLocal() as db:
                await asyncio.to_thread(
                    sync_youtube,
                    db,
                    settings.youtube_query_list,
                    settings.youtube_sync_max_results,
                )
        except Exception as exc:  # keep the scheduler alive; deployment logs capture the failure
            print(f"BluePath scheduled YouTube sync failed: {exc}")


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings.validate_runtime()
    Base.metadata.create_all(bind=engine)
    apply_compatibility_migrations()
    with SessionLocal() as db:
        seed_contents(db)
        seed_knowledge(db)
        seed_quizzes(db)
        bootstrap_admin(db)
    schedulers = [
        asyncio.create_task(scheduled_youtube_sync()),
        asyncio.create_task(scheduled_schedule_sync()),
    ]
    try:
        yield
    finally:
        for scheduler in schedulers:
            scheduler.cancel()
        for scheduler in schedulers:
            try:
                await scheduler
            except asyncio.CancelledError:
                pass


app = FastAPI(title=settings.app_name, version="1.4.1", lifespan=lifespan)
app.mount("/uploads", StaticFiles(directory=UPLOADS_DIR), name="uploads")
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origin_list,
    allow_credentials=settings.cors_origin_list != ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok",
        "environment": settings.environment,
        "llmEnabled": settings.llm_enabled,
        "embeddingEnabled": bool(settings.embedding_model),
        "liveWebSearchEnabled": settings.web_search_enabled,
        "liveWebSearchProvider": settings.web_search_provider if settings.web_search_enabled else "",
        "scheduleFeedCount": len(settings.schedule_feed_list),
        "scheduleSyncEnabled": bool(settings.schedule_feed_list and settings.schedule_sync_hours > 0),
        "aiRequirements": {
            "llmRequired": settings.require_llm,
            "embeddingsRequired": settings.require_embeddings,
            "webSearchRequired": settings.require_web_search,
        },
        "aiReady": (not settings.require_llm or settings.llm_enabled)
        and (not settings.require_embeddings or bool(settings.embedding_model.strip()))
        and (not settings.require_web_search or settings.web_search_enabled),
    }


@app.get("/admin", include_in_schema=False)
def admin_dashboard() -> FileResponse:
    return FileResponse(STATIC_DIR / "admin.html")


@app.get("/reset-password", include_in_schema=False)
def password_reset_page() -> FileResponse:
    return FileResponse(STATIC_DIR / "reset-password.html")


@app.get("/guardian-consent", include_in_schema=False)
def guardian_consent_page() -> FileResponse:
    return FileResponse(STATIC_DIR / "guardian-consent.html")


@app.get("/api/v1/auth/nickname-available", response_model=NicknameAvailability)
def nickname_available(nickname: str = Query(min_length=2, max_length=20), db: Session = Depends(get_db)) -> NicknameAvailability:
    value = normalize_nickname(nickname)
    available = db.scalar(select(User.id).where(func.lower(User.nickname) == value.lower())) is None
    return NicknameAvailability(
        nickname=value,
        available=available,
        message="사용 가능한 닉네임입니다." if available else "이미 사용 중인 닉네임입니다.",
    )


@app.post("/api/v1/auth/register", response_model=AuthResponse)
def register(request: AuthRequest, db: Session = Depends(get_db)) -> AuthResponse:
    email = request.email.lower().strip()
    nickname = normalize_nickname(request.nickname or "")
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status_code=409, detail="Email is already registered")
    if db.scalar(select(User).where(func.lower(User.nickname) == nickname.lower())):
        raise HTTPException(status_code=409, detail="Nickname is already in use")
    user = User(
        email=email,
        password_hash=hash_password(request.password),
        display_name=nickname,
        nickname=nickname,
        guardian_email=str(request.guardianEmail) if request.guardianEmail else None,
        guardian_consent=False,
    )
    db.add(user)
    try:
        db.commit()
    except IntegrityError as exc:
        db.rollback()
        raise HTTPException(status_code=409, detail="Email or nickname is already registered") from exc
    db.refresh(user)
    return auth_response(user, db)


@app.post("/api/v1/auth/login", response_model=AuthResponse)
def login(request: AuthRequest, db: Session = Depends(get_db)) -> AuthResponse:
    user = db.scalar(select(User).where(User.email == request.email.lower().strip()))
    if not user or not verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account is disabled")
    return auth_response(user, db)


@app.post("/api/v1/auth/password-reset/request", response_model=GenericResponse)
def request_password_reset(request: PasswordResetRequest, db: Session = Depends(get_db)) -> GenericResponse:
    email = request.email.lower().strip()
    user = db.scalar(select(User).where(User.email == email, User.is_active.is_(True)))
    if user:
        now = datetime.now(timezone.utc)
        for token in db.scalars(
            select(PasswordResetToken).where(
                PasswordResetToken.user_id == user.id,
                PasswordResetToken.used_at.is_(None),
            )
        ):
            token.used_at = now
        raw_token = secrets.token_urlsafe(48)
        token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
        db.add(
            PasswordResetToken(
                user_id=user.id,
                token_hash=token_hash,
                expires_at=now + timedelta(minutes=max(5, settings.password_reset_token_minutes)),
            )
        )
        db.commit()
        try:
            send_password_reset_email(email, raw_token)
        except Exception as exc:
            # Keep the response generic so SMTP failures cannot reveal account existence.
            print(f"BluePath password reset delivery failed: {exc}")
    return GenericResponse(message="등록된 계정이라면 비밀번호 재설정 안내를 이메일로 보냈습니다.")


@app.post("/api/v1/auth/password-reset/confirm", response_model=GenericResponse)
def confirm_password_reset(request: PasswordResetConfirm, db: Session = Depends(get_db)) -> GenericResponse:
    token_hash = hashlib.sha256(request.token.encode("utf-8")).hexdigest()
    reset = db.scalar(select(PasswordResetToken).where(PasswordResetToken.token_hash == token_hash))
    now = datetime.now(timezone.utc)
    if not reset or reset.used_at is not None:
        raise HTTPException(status_code=400, detail="Invalid or already used reset token")
    expires_at = reset.expires_at
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    if expires_at <= now:
        raise HTTPException(status_code=400, detail="Reset token has expired")
    user = db.get(User, reset.user_id)
    if not user or not user.is_active:
        raise HTTPException(status_code=400, detail="Account is not available")
    user.password_hash = hash_password(request.newPassword)
    reset.used_at = now
    db.commit()
    return GenericResponse(message="비밀번호가 변경되었습니다. 새 비밀번호로 로그인해 주세요.")


@app.post("/api/v1/ai/quiz", response_model=QuizResponse)
def ai_quiz(
    request: QuizRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> QuizResponse:
    require_guardian_consent_for_minor(db, user)
    generated = generate_quiz(db, request)
    expires_at = datetime.now(timezone.utc) + timedelta(minutes=30)
    stored_questions = [item.model_dump(mode="json") for item in generated.questions]
    session = QuizSession(
        user_id=user.id, tier=request.tier, source=generated.source,
        questions_json=stored_questions, expires_at=expires_at,
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    public_questions = [
        {**item, "answerIndex": -1, "explanation": ""}
        for item in stored_questions
    ]
    return QuizResponse(
        source=generated.source, sessionId=session.id, expiresAt=expires_at,
        questions=public_questions,
    )


@app.post("/api/v1/ai/quiz/submit", response_model=QuizSubmissionResponse)
def submit_ai_quiz(
    request: QuizSubmissionRequest,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
) -> QuizSubmissionResponse:
    require_guardian_consent_for_minor(db, user)
    session = db.scalar(select(QuizSession).where(QuizSession.id == request.sessionId, QuizSession.user_id == user.id))
    if session is None:
        raise HTTPException(status_code=404, detail="Quiz session not found")
    if session.submitted_at is not None:
        return QuizSubmissionResponse(**session.result_json)
    expires_at = session.expires_at if session.expires_at.tzinfo else session.expires_at.replace(tzinfo=timezone.utc)
    if expires_at <= datetime.now(timezone.utc):
        raise HTTPException(status_code=410, detail="Quiz session has expired")
    questions = list(session.questions_json or [])
    if len(request.answers) != len(questions):
        raise HTTPException(status_code=422, detail="Every quiz question must have one answer")
    if any(answer < -1 or answer > 3 for answer in request.answers):
        raise HTTPException(status_code=422, detail="Quiz answers must be between 0 and 3")

    correct = 0
    topic_results: dict[str, list[bool]] = {}
    for item, answer in zip(questions, request.answers):
        is_correct = answer == int(item.get("answerIndex", -99))
        correct += int(is_correct)
        topic_results.setdefault(str(item.get("topic", "해양교육")), []).append(is_correct)
    pass_count = QUIZ_PASS_COUNTS.get(session.tier, len(questions) + 1)
    passed = correct >= pass_count

    previous = list(db.scalars(select(QuizSession).where(
        QuizSession.user_id == user.id, QuizSession.tier == session.tier, QuizSession.submitted_at.is_not(None),
    )))
    previous_best = max((int((item.result_json or {}).get("correctCount", 0)) for item in previous), default=0)
    already_passed = any(bool((item.result_json or {}).get("passed", False)) for item in previous)
    xp_awarded = 300 if passed and not already_passed else min(120, 20 + max(0, correct - previous_best) * 15) if correct > previous_best else 0

    progress = get_or_create_progress(db, user.id)
    progress.xp = max(0, progress.xp + xp_awarded)
    if passed:
        current_rank = TIER_RANKS.get(session.tier, 1)
        if session.tier == "플래티넘":
            progress.diamond_advanced_quiz_passed = True
        else:
            progress.quiz_tier_rank = max(progress.quiz_tier_rank, min(4, current_rank + 1))
    mastery = dict(progress.skill_mastery_json or {})
    evidence = dict(progress.skill_evidence_json or {})
    for topic, results in topic_results.items():
        old_count = max(0, int(evidence.get(topic, 0)))
        old_mastery = max(0, min(100, int(mastery.get(topic, 50))))
        hits = sum(1 for value in results if value)
        total = len(results)
        new_count = old_count + total
        mastery[topic] = round((old_mastery * old_count + hits * 100) / max(1, new_count))
        evidence[topic] = new_count
    progress.skill_mastery_json = mastery
    progress.skill_evidence_json = evidence

    status = diamond_status_for(db, user, progress=progress)
    profile = db.get(UserProfile, user.id) or UserProfile(user_id=user.id)
    profile.snapshot = apply_verified_evidence(db, user.id, apply_account_state(apply_authoritative_progress(profile.snapshot or {}, progress, diamond_eligible=status.eligible), user))
    profile.version = int(profile.version or 0) + 1
    db.add(profile)

    revealed = [QuizQuestion(**item) for item in questions]
    result = QuizSubmissionResponse(
        sessionId=session.id, correctCount=correct, total=len(questions), passed=passed,
        xpAwarded=xp_awarded, xp=progress.xp, tier=profile.snapshot.get("tier", "브론즈"),
        quizTierRank=progress.quiz_tier_rank, advancedQuizPassed=progress.diamond_advanced_quiz_passed,
        skillMastery=mastery, skillEvidence=evidence, questions=revealed,
    )
    session.submitted_at = datetime.now(timezone.utc)
    session.result_json = result.model_dump(mode="json")
    db.add(session)
    db.commit()
    return result


@app.post("/api/v1/ai/agent", response_model=AgentResponse)
def ai_agent(
    request: AgentRequest,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
) -> AgentResponse:
    return answer_agent(db, request)


@app.post("/api/v1/ai/search", response_model=AiSearchResponse)
def ai_search(
    request: AiSearchRequest,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
) -> AiSearchResponse:
    return search_resources(db, request)


@app.post("/api/v1/routes/plan", response_model=RoutePlanResponse)
def plan_route(
    request: RoutePlanRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> RoutePlanResponse:
    return create_route_plan(db, user, request)


@app.post("/api/v1/routes/simulate", response_model=RouteSimulationResponse)
def simulate_route(
    request: RouteSimulationRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> RouteSimulationResponse:
    try:
        return simulate_route_node(db, user, request)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


def _reroute_response(
    db: Session,
    user: User,
    request: RouteRerouteRequest,
    *,
    activate: bool,
) -> RoutePlanResponse:
    if not activate:
        pending = db.scalar(
            select(RoutePlan)
            .where(RoutePlan.user_id == user.id, RoutePlan.status == "pending")
            .order_by(RoutePlan.created_at.desc())
        )
        if pending is not None:
            return route_plan_response(pending)
    plan = None
    if request.routeId:
        plan = db.scalar(select(RoutePlan).where(RoutePlan.id == request.routeId, RoutePlan.user_id == user.id))
    if plan is None:
        plan = db.scalar(
            select(RoutePlan)
            .where(RoutePlan.user_id == user.id, RoutePlan.status == "active")
            .order_by(RoutePlan.created_at.desc())
        )
    if plan is None:
        raise HTTPException(status_code=404, detail="No route is available to recalculate")
    excluded = set()
    if request.blockedNodeId:
        blocked = next((node for node in plan.nodes if node.id == request.blockedNodeId), None)
        if blocked and blocked.target_id:
            excluded.add(blocked.target_id)
    profile = dict((plan.context_json or {}).get("profile", {}))
    profile.update(request.profile or {})
    constraints = dict((plan.context_json or {}).get("constraints", {}))
    constraints.update(request.constraints or {})
    route_type = plan.route_type
    max_nodes = max(3, min(7, len(plan.nodes) or 5))
    if request.reason in {"time_shortage", "inactivity"}:
        route_type = "fastest"
        max_nodes = min(max_nodes, 4)
        constraints["maxMinutesPerNode"] = 35
    elif request.reason == "weekend_only":
        route_type = "weekend"
    elif request.reason == "free_only":
        route_type = "free"
    elif request.reason == "too_difficult":
        constraints["maxDifficulty"] = "하"
    next_request = RoutePlanRequest(
        targetCareer=plan.target_career,
        routeType=route_type,
        profile=profile,
        constraints=constraints,
        maxNodes=max_nodes,
    )
    return create_route_plan(db, user, next_request, excluded, request.reason, activate=activate)


@app.post("/api/v1/routes/reroute", response_model=RoutePlanResponse)
def reroute(
    request: RouteRerouteRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> RoutePlanResponse:
    response = _reroute_response(db, user, request, activate=True)
    record_route_outcome(db, user, response.routeId, None, "rerouted", 1.0, {"reason": request.reason})
    return response


@app.post("/api/v1/routes/reroute/preview", response_model=RoutePlanResponse)
def preview_automatic_reroute(
    request: RouteRerouteRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> RoutePlanResponse:
    return _reroute_response(db, user, request, activate=False)


@app.get("/api/v1/routes/pending", response_model=RoutePlanResponse | None)
def pending_route(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> RoutePlanResponse | None:
    plan = db.scalar(
        select(RoutePlan)
        .where(RoutePlan.user_id == user.id, RoutePlan.status == "pending")
        .order_by(RoutePlan.created_at.desc())
    )
    return route_plan_response(plan) if plan else None


@app.post("/api/v1/routes/activate", response_model=RoutePlanResponse)
def activate_pending_route(
    request: RouteActivationRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> RoutePlanResponse:
    plan = db.scalar(
        select(RoutePlan).where(
            RoutePlan.id == request.routeId,
            RoutePlan.user_id == user.id,
            RoutePlan.status == "pending",
        )
    )
    if plan is None:
        raise HTTPException(status_code=404, detail="Pending route not found")
    for active in db.scalars(
        select(RoutePlan).where(RoutePlan.user_id == user.id, RoutePlan.status == "active")
    ):
        active.status = "rerouted"
    plan.status = "active"
    db.commit()
    record_route_outcome(db, user, plan.id, None, "rerouted", 1.0, {"reason": "automatic_preview_accepted"})
    db.refresh(plan)
    return route_plan_response(plan)


@app.post("/api/v1/routes/outcomes", response_model=GenericResponse)
def save_route_outcome(
    request: RouteOutcomeEventRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GenericResponse:
    try:
        record_route_outcome(db, user, request.routeId, request.nodeId, request.eventType, request.value, request.metadata)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    return GenericResponse(message="항로 활동을 기록했습니다.")


@app.post("/api/v1/admin/missions/qr-token", response_model=MissionQrIssueResponse)
def create_mission_qr_token(
    request: MissionQrIssueRequest,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> MissionQrIssueResponse:
    return issue_mission_qr(db, admin, request)


@app.post("/api/v1/missions/generate", response_model=FamilyMissionResponse)
def generate_mission(
    request: MissionGenerateRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FamilyMissionResponse:
    require_guardian_consent_for_minor(db, user)
    try:
        return generate_family_mission(db, user, request)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/api/v1/missions/verify", response_model=MissionVerifyResponse)
def verify_mission(
    request: MissionVerifyRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> MissionVerifyResponse:
    require_guardian_consent_for_minor(db, user)
    try:
        result = verify_family_mission(
            db, user, request.missionId, request.completionNote, request.participantCount, request.qrPayload
        )
        if result.newlyVerified:
            progress = get_or_create_progress(db, user.id)
            progress.xp += 150
            evidence = dict(progress.skill_evidence_json or {})
            mastery = dict(progress.skill_mastery_json or {})
            for topic, gain in (result.acquiredCompetencies or {}).items():
                count = int(evidence.get(topic, 0))
                old = int(mastery.get(topic, 50))
                evidence[topic] = count + 1
                mastery[topic] = min(100, round((old * count + min(100, 65 + int(gain) * 3)) / max(1, count + 1)))
            progress.skill_evidence_json = evidence
            progress.skill_mastery_json = mastery
            profile = db.get(UserProfile, user.id) or UserProfile(user_id=user.id)
            status = diamond_status_for(db, user, progress=progress)
            profile.snapshot = apply_verified_evidence(db, user.id, apply_account_state(apply_authoritative_progress(profile.snapshot or {}, progress, diamond_eligible=status.eligible), user))
            profile.version = int(profile.version or 0) + 1
            db.add(profile)
            db.commit()
        return result
    except ValueError as exc:
        status = 404 if str(exc) == "Mission not found" else 400
        raise HTTPException(status_code=status, detail=str(exc)) from exc


@app.post("/api/v1/program-participation", response_model=ProgramParticipationResponse)
def save_program_participation(
    request: ProgramParticipationRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ProgramParticipationResponse:
    require_guardian_consent_for_minor(db, user)
    return upsert_program_participation(db, user, request)


@app.get("/api/v1/catalog", response_model=list[AdminContentItem])
def learner_catalog(
    _: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[AdminContentItem]:
    items = db.scalars(select(Content).order_by(Content.content_type, Content.updated_at.desc()).limit(500))
    return [content_to_schema(item) for item in items]


@app.get("/api/v1/dashboard", response_model=DashboardResponse)
def dashboard(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DashboardResponse:
    counts: dict[str, int] = {}
    for record in db.scalars(select(LearningRecord).where(LearningRecord.user_id == user.id)):
        if record.record_type not in {"video", "paper"}:
            continue
        day = record.synced_at.date().isoformat()
        counts[day] = counts.get(day, 0) + 1
    for post in db.scalars(select(CommunityPost).where(CommunityPost.user_id == user.id)):
        day = post.created_at.date().isoformat()
        counts[day] = counts.get(day, 0) + 1
    for comment in db.scalars(select(CommunityComment).where(CommunityComment.user_id == user.id)):
        day = comment.created_at.date().isoformat()
        counts[day] = counts.get(day, 0) + 1
    return DashboardResponse(profile=profile_summary(db, user, user), activity=counts)


@app.post("/api/v1/profile/image", response_model=ProfileImageResponse)
async def upload_profile_image(
    request: Request,
    file: UploadFile = File(...),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ProfileImageResponse:
    content_type = (file.content_type or "").lower()
    if content_type not in {"image/jpeg", "image/png", "image/webp"}:
        raise HTTPException(status_code=400, detail="Only JPEG, PNG, and WebP profile images are supported")
    raw = await file.read(5_000_001)
    if len(raw) > 5_000_000:
        raise HTTPException(status_code=413, detail="Profile image must be 5 MB or smaller")
    signatures = {
        "image/jpeg": raw.startswith(b"\xff\xd8\xff"),
        "image/png": raw.startswith(b"\x89PNG\r\n\x1a\n"),
        "image/webp": len(raw) >= 12 and raw.startswith(b"RIFF") and raw[8:12] == b"WEBP",
    }
    if not raw or not signatures[content_type]:
        raise HTTPException(status_code=400, detail="Uploaded bytes do not match the declared image type")
    extension = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}[content_type]
    filename = f"profile-{user.id}-{secrets.token_hex(6)}{extension}"
    (UPLOADS_DIR / filename).write_bytes(raw)
    user.profile_image_url = str(request.base_url).rstrip("/") + f"/uploads/{filename}"
    db.commit()
    return ProfileImageResponse(profileImageUrl=user.profile_image_url)


def blocked_user_ids(db: Session, user_id: str) -> set[str]:
    outgoing = set(db.scalars(select(CommunityBlock.blocked_id).where(CommunityBlock.blocker_id == user_id)))
    incoming = set(db.scalars(select(CommunityBlock.blocker_id).where(CommunityBlock.blocked_id == user_id)))
    return outgoing | incoming


def community_blocked(db: Session, first_user_id: str, second_user_id: str) -> bool:
    if first_user_id == second_user_id:
        return False
    return db.scalar(select(CommunityBlock.id).where(or_(
        (CommunityBlock.blocker_id == first_user_id) & (CommunityBlock.blocked_id == second_user_id),
        (CommunityBlock.blocker_id == second_user_id) & (CommunityBlock.blocked_id == first_user_id),
    ))) is not None


def require_community_access(db: Session, actor_id: str, target_user_id: str) -> None:
    if community_blocked(db, actor_id, target_user_id):
        raise HTTPException(status_code=403, detail="Interaction is unavailable because one account blocked the other")


@app.get("/api/v1/community/posts", response_model=list[CommunityPostItem])
def list_community_posts(
    category: str = Query(default="free", pattern="^(free|question)$"),
    q: str = Query(default="", max_length=200),
    limit: int = Query(default=20, ge=1, le=50),
    offset: int = Query(default=0, ge=0),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[CommunityPostItem]:
    blocked_ids = list(blocked_user_ids(db, user.id))
    statement = select(CommunityPost).where(CommunityPost.category == category)
    if blocked_ids:
        statement = statement.where(CommunityPost.user_id.not_in(blocked_ids))
    query = q.strip()
    if query:
        pattern = f"%{query}%"
        author_ids = select(User.id).where(or_(User.nickname.ilike(pattern), User.display_name.ilike(pattern)))
        statement = statement.where(or_(
            CommunityPost.title.ilike(pattern),
            CommunityPost.body.ilike(pattern),
            CommunityPost.user_id.in_(author_ids),
        ))
    rows = list(db.scalars(statement.order_by(CommunityPost.created_at.desc()).offset(offset).limit(limit)))
    return [community_post_schema(db, item, user) for item in rows]


@app.post("/api/v1/community/posts", response_model=CommunityPostItem)
def create_community_post(
    request: CommunityPostCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CommunityPostItem:
    post = CommunityPost(user_id=user.id, category=request.category, title=request.title.strip(), body=request.body.strip())
    db.add(post)
    db.commit()
    db.refresh(post)
    return community_post_schema(db, post, user)


@app.post("/api/v1/community/posts/{post_id}/comments", response_model=CommunityCommentItem)
def create_community_comment(
    post_id: str,
    request: CommunityCommentCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CommunityCommentItem:
    post = db.get(CommunityPost, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    require_community_access(db, user.id, post.user_id)
    if request.parentId:
        parent = db.get(CommunityComment, request.parentId)
        if parent is None or parent.post_id != post_id:
            raise HTTPException(status_code=400, detail="Parent comment does not belong to this post")
        require_community_access(db, user.id, parent.user_id)
    comment = CommunityComment(post_id=post_id, user_id=user.id, parent_id=request.parentId, body=request.body.strip())
    db.add(comment)
    db.commit()
    db.refresh(comment)
    return community_comment_schema(db, comment, user)


@app.put("/api/v1/community/posts/{post_id}", response_model=CommunityPostItem)
def update_community_post(
    post_id: str,
    request: CommunityPostUpdate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CommunityPostItem:
    post = db.get(CommunityPost, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if post.user_id != user.id and not is_admin(user):
        raise HTTPException(status_code=403, detail="You cannot edit this post")
    post.title = request.title.strip()
    post.body = request.body.strip()
    post.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(post)
    return community_post_schema(db, post, user)


@app.delete("/api/v1/community/posts/{post_id}", response_model=GenericResponse)
def delete_community_post(
    post_id: str,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GenericResponse:
    post = db.get(CommunityPost, post_id)
    if post is None:
        raise HTTPException(status_code=404, detail="Post not found")
    if post.user_id != user.id and not is_admin(user):
        raise HTTPException(status_code=403, detail="You cannot delete this post")
    comment_ids = list(db.scalars(select(CommunityComment.id).where(CommunityComment.post_id == post_id)))
    db.execute(delete(CommunityReaction).where(
        or_(
            (CommunityReaction.target_type == "post") & (CommunityReaction.target_id == post_id),
            (CommunityReaction.target_type == "comment") & CommunityReaction.target_id.in_(comment_ids or [""]),
        )
    ))
    db.delete(post)
    db.commit()
    return GenericResponse(message="Post deleted")


@app.put("/api/v1/community/comments/{comment_id}", response_model=CommunityCommentItem)
def update_community_comment(
    comment_id: str,
    request: CommunityCommentUpdate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CommunityCommentItem:
    comment = db.get(CommunityComment, comment_id)
    if comment is None:
        raise HTTPException(status_code=404, detail="Comment not found")
    if comment.user_id != user.id and not is_admin(user):
        raise HTTPException(status_code=403, detail="You cannot edit this comment")
    comment.body = request.body.strip()
    comment.updated_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(comment)
    return community_comment_schema(db, comment, user)


@app.delete("/api/v1/community/comments/{comment_id}", response_model=GenericResponse)
def delete_community_comment(
    comment_id: str,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GenericResponse:
    comment = db.get(CommunityComment, comment_id)
    if comment is None:
        raise HTTPException(status_code=404, detail="Comment not found")
    if comment.user_id != user.id and not is_admin(user):
        raise HTTPException(status_code=403, detail="You cannot delete this comment")
    descendant_ids = [comment_id]
    pending = [comment_id]
    while pending:
        children = list(db.scalars(select(CommunityComment.id).where(CommunityComment.parent_id.in_(pending))))
        pending = [item for item in children if item not in descendant_ids]
        descendant_ids.extend(pending)
    db.execute(delete(CommunityReaction).where(
        CommunityReaction.target_type == "comment", CommunityReaction.target_id.in_(descendant_ids)
    ))
    db.delete(comment)
    db.commit()
    return GenericResponse(message="Comment deleted")


@app.post("/api/v1/community/reports", response_model=GenericResponse)
def report_community_target(
    request: CommunityReportRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GenericResponse:
    target_exists = (
        db.get(CommunityPost, request.targetId) if request.targetType == "post"
        else db.get(CommunityComment, request.targetId) if request.targetType == "comment"
        else db.get(User, request.targetId)
    )
    if target_exists is None:
        raise HTTPException(status_code=404, detail="Report target not found")
    existing = db.scalar(select(CommunityReport).where(
        CommunityReport.reporter_id == user.id,
        CommunityReport.target_type == request.targetType,
        CommunityReport.target_id == request.targetId,
    ))
    if existing:
        existing.reason = request.reason.strip()
        existing.status = "pending"
    else:
        db.add(CommunityReport(
            reporter_id=user.id, target_type=request.targetType,
            target_id=request.targetId, reason=request.reason.strip(),
        ))
    db.commit()
    return GenericResponse(message="Report submitted")


@app.post("/api/v1/community/users/{target_user_id}/block", response_model=CommunityBlockResponse)
def toggle_community_block(
    target_user_id: str,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CommunityBlockResponse:
    if target_user_id == user.id:
        raise HTTPException(status_code=400, detail="You cannot block yourself")
    if db.get(User, target_user_id) is None:
        raise HTTPException(status_code=404, detail="User not found")
    existing = db.scalar(select(CommunityBlock).where(
        CommunityBlock.blocker_id == user.id, CommunityBlock.blocked_id == target_user_id,
    ))
    blocked = existing is None
    if existing:
        db.delete(existing)
    else:
        db.add(CommunityBlock(blocker_id=user.id, blocked_id=target_user_id))
        db.execute(delete(Follow).where(
            or_(
                (Follow.follower_id == user.id) & (Follow.following_id == target_user_id),
                (Follow.follower_id == target_user_id) & (Follow.following_id == user.id),
            )
        ))
    db.commit()
    return CommunityBlockResponse(blocked=blocked)


@app.post("/api/v1/community/reactions", response_model=ReactionToggleResponse)
def toggle_community_reaction(
    request: ReactionToggleRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ReactionToggleResponse:
    allowed = {"👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🌊"}
    if request.emoji not in allowed:
        raise HTTPException(status_code=400, detail="Unsupported reaction emoji")
    target = db.get(CommunityPost if request.targetType == "post" else CommunityComment, request.targetId)
    if target is None:
        raise HTTPException(status_code=404, detail="Reaction target not found")
    require_community_access(db, user.id, target.user_id)
    existing = db.scalar(select(CommunityReaction).where(
        CommunityReaction.user_id == user.id,
        CommunityReaction.target_type == request.targetType,
        CommunityReaction.target_id == request.targetId,
        CommunityReaction.emoji == request.emoji,
    ))
    active = existing is None
    if existing:
        db.delete(existing)
    else:
        db.add(CommunityReaction(user_id=user.id, target_type=request.targetType, target_id=request.targetId, emoji=request.emoji))
    db.commit()
    return ReactionToggleResponse(active=active, reactions=reaction_summaries(db, request.targetType, request.targetId, user.id))


@app.post("/api/v1/community/users/{target_user_id}/follow", response_model=FollowResponse)
def toggle_follow(
    target_user_id: str,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> FollowResponse:
    if target_user_id == user.id:
        raise HTTPException(status_code=400, detail="You cannot follow yourself")
    target = db.get(User, target_user_id)
    if target is None:
        raise HTTPException(status_code=404, detail="User not found")
    require_community_access(db, user.id, target_user_id)
    existing = db.scalar(select(Follow).where(Follow.follower_id == user.id, Follow.following_id == target_user_id))
    following = existing is None
    if existing:
        db.delete(existing)
    else:
        db.add(Follow(follower_id=user.id, following_id=target_user_id))
    db.commit()
    return FollowResponse(
        following=following,
        followerCount=count_followers(db, target_user_id),
        followingCount=count_following(db, user.id),
    )


def merge_snapshot_value(server_value, client_value):
    if isinstance(server_value, dict) and isinstance(client_value, dict):
        result = dict(server_value)
        for key, value in client_value.items():
            if key not in result:
                result[key] = value
            else:
                result[key] = merge_snapshot_value(result[key], value)
        return result
    if isinstance(server_value, list) and isinstance(client_value, list):
        result = []
        seen = set()
        for value in server_value + client_value:
            marker = json.dumps(value, ensure_ascii=False, sort_keys=True) if isinstance(value, (dict, list)) else str(value)
            if marker not in seen:
                seen.add(marker)
                result.append(value)
        return result
    if isinstance(server_value, (int, float)) and isinstance(client_value, (int, float)):
        return max(server_value, client_value)
    if server_value in (None, "", False):
        return client_value
    return server_value


def safe_sync_timestamp(value) -> int:
    try:
        parsed = max(0, int(value))
    except (TypeError, ValueError):
        return 0
    ceiling = int(datetime.now(timezone.utc).timestamp() * 1000) + 5 * 60 * 1000
    return min(parsed, ceiling)


def bookmark_state(snapshot: dict) -> dict[str, dict]:
    result: dict[str, dict] = {}
    raw = (snapshot or {}).get("bookmarkState", {})
    if isinstance(raw, dict):
        for key, value in raw.items():
            if not str(key).strip() or not isinstance(value, dict):
                continue
            result[str(key)] = {
                "active": bool(value.get("active", False)),
                "updatedAt": safe_sync_timestamp(value.get("updatedAt", 0)),
            }
    for value in (snapshot or {}).get("bookmarks", []) if isinstance((snapshot or {}).get("bookmarks", []), list) else []:
        key = str(value).strip()
        if key and key not in result:
            result[key] = {"active": True, "updatedAt": 0}
    return result


def merge_timestamped_snapshot(server: dict, client: dict, merged: dict, *, conflict: bool) -> dict:
    result = dict(merged or {})
    if conflict:
        bundles = [
            ("profileUpdatedAt", ("ageGroup", "interest", "goal", "level", "persona")),
            ("reminderUpdatedAt", ("reminderEnabled", "reminderHour", "reminderMinute")),
            ("voyagePreferenceUpdatedAt", ("targetCareer", "routeType")),
        ]
        for timestamp_key, fields in bundles:
            server_at = safe_sync_timestamp((server or {}).get(timestamp_key, 0))
            client_at = safe_sync_timestamp((client or {}).get(timestamp_key, 0))
            if client_at > server_at:
                for field in fields:
                    if field in client:
                        result[field] = client[field]
                result[timestamp_key] = client_at
            else:
                for field in fields:
                    if field in server:
                        result[field] = server[field]
                result[timestamp_key] = server_at

        server_values = (server or {}).get("contentReflections", {})
        client_values = (client or {}).get("contentReflections", {})
        server_times = (server or {}).get("contentReflectionUpdatedAt", {})
        client_times = (client or {}).get("contentReflectionUpdatedAt", {})
        if not isinstance(server_values, dict): server_values = {}
        if not isinstance(client_values, dict): client_values = {}
        if not isinstance(server_times, dict): server_times = {}
        if not isinstance(client_times, dict): client_times = {}
        values: dict[str, str] = {}
        times: dict[str, int] = {}
        for key in set(server_values) | set(client_values):
            server_at = safe_sync_timestamp(server_times.get(key, 0))
            client_at = safe_sync_timestamp(client_times.get(key, 0))
            if key not in server_values or client_at > server_at:
                values[str(key)] = str(client_values.get(key, ""))
                times[str(key)] = client_at
            else:
                values[str(key)] = str(server_values.get(key, ""))
                times[str(key)] = server_at
        result["contentReflections"] = values
        result["contentReflectionUpdatedAt"] = times

    server_bookmarks = bookmark_state(server)
    client_bookmarks = bookmark_state(client)
    if conflict:
        states: dict[str, dict] = {}
        for key in set(server_bookmarks) | set(client_bookmarks):
            old = server_bookmarks.get(key)
            new = client_bookmarks.get(key)
            if old is None:
                states[key] = new
            elif new is None:
                states[key] = old
            elif safe_sync_timestamp(new.get("updatedAt", 0)) > safe_sync_timestamp(old.get("updatedAt", 0)):
                states[key] = new
            else:
                states[key] = old
    elif "bookmarkState" in client or "bookmarks" in client:
        states = client_bookmarks
    else:
        states = server_bookmarks
    result["bookmarkState"] = states
    result["bookmarks"] = sorted(key for key, value in states.items() if value and value.get("active"))
    return result


def sanitized_client_snapshot(snapshot: dict) -> dict:
    return {key: value for key, value in (snapshot or {}).items() if key not in AUTHORITATIVE_SNAPSHOT_KEYS and key not in {"guardianConsent"}}

def apply_account_state(snapshot: dict, user: User) -> dict:
    result = dict(snapshot or {})
    result["guardianConsent"] = bool(user.guardian_consent)
    result["guardianEmail"] = user.guardian_email or ""
    result["guardianConsentVersion"] = user.guardian_consent_version or ""
    result["guardianConsentedAt"] = user.guardian_consented_at.isoformat() if user.guardian_consented_at else ""
    return result

def valid_paper_evidence_ids(db: Session, user_id: str) -> list[str]:
    valid: list[str] = []
    evidences = list(db.scalars(select(PaperLearningEvidence).where(PaperLearningEvidence.user_id == user_id)))
    for evidence in evidences:
        content = db.get(Content, evidence.content_id)
        status = str((content.metadata_json or {}).get("paperStatus", "current")).strip().lower() if content else "missing"
        if content is not None and content.content_type == "paper" and status != "retracted":
            valid.append(evidence.content_id)
    return sorted(set(valid))


def apply_verified_evidence(db: Session, user_id: str, snapshot: dict) -> dict:
    result = dict(snapshot or {})
    video_ids = list(db.scalars(select(VideoLearningEvidence.content_id).where(VideoLearningEvidence.user_id == user_id)))
    paper_ids = valid_paper_evidence_ids(db, user_id)
    badges = list(db.scalars(select(MissionEvidence.badge).where(
        MissionEvidence.user_id == user_id, MissionEvidence.status == "verified"
    )))
    result["verifiedVideoIds"] = sorted(set(video_ids))
    result["verifiedPaperIds"] = paper_ids
    result["completedContentIds"] = sorted(set(video_ids) | set(paper_ids))
    result["missionBadges"] = sorted({badge for badge in badges if badge})
    return result


def valid_record_uuid(value: str) -> bool:
    try:
        import uuid
        return str(uuid.UUID(str(value))) == str(value).lower()
    except (ValueError, TypeError, AttributeError):
        return False


@app.get("/api/v1/sync", response_model=CloudStateResponse)
def get_cloud_state(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CloudStateResponse:
    profile = db.get(UserProfile, user.id)
    progress = get_or_create_progress(db, user.id)
    status = diamond_status_for(db, user, progress=progress)
    snapshot = apply_verified_evidence(db, user.id, apply_account_state(apply_authoritative_progress(profile.snapshot if profile else {}, progress, diamond_eligible=status.eligible), user))
    if profile is None:
        profile = UserProfile(user_id=user.id, snapshot=snapshot, version=0)
        db.add(profile)
        db.commit()
    elif snapshot != (profile.snapshot or {}):
        profile.snapshot = snapshot
        db.commit()
    return CloudStateResponse(
        snapshot=snapshot, version=int(profile.version or 0),
        diamondStatus=status, learningRecords=cloud_learning_records(db, user.id),
    )


@app.post("/api/v1/sync", response_model=SyncResponse)
def sync_progress(
    request: SyncRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> SyncResponse:
    profile = db.get(UserProfile, user.id) or UserProfile(user_id=user.id, snapshot={}, version=0)
    current_version = int(profile.version or 0)
    conflict = request.baseVersion != current_version
    client_snapshot = sanitized_client_snapshot(request.snapshot)
    if conflict:
        merged = merge_snapshot_value(profile.snapshot or {}, client_snapshot)
    else:
        merged = {**(profile.snapshot or {}), **client_snapshot}
    merged = merge_timestamped_snapshot(profile.snapshot or {}, client_snapshot, merged, conflict=conflict)

    progress = get_or_create_progress(db, user.id)
    status = diamond_status_for(db, user, progress=progress)
    profile.snapshot = apply_verified_evidence(db, user.id, apply_account_state(apply_authoritative_progress(merged, progress, diamond_eligible=status.eligible), user))
    profile.version = current_version + 1
    db.add(profile)

    accepted: list[str] = []
    rejected: list[str] = []
    for record in request.learningRecords:
        client_id = str(record.id).strip().lower()
        if not valid_record_uuid(client_id):
            rejected.append(client_id)
            continue
        existing = db.scalar(select(LearningRecord).where(
            LearningRecord.user_id == user.id, LearningRecord.client_record_id == client_id,
        ))
        if existing is None:
            existing = LearningRecord(user_id=user.id, client_record_id=client_id)
            db.add(existing)
        if int(existing.client_updated_at or 0) <= record.updatedAt:
            existing.device_id = request.deviceId.strip()
            existing.record_type = record.recordType
            existing.target_id = record.targetId
            existing.title = record.title
            existing.status = record.status
            existing.client_updated_at = record.updatedAt
            existing.synced_at = datetime.now(timezone.utc)
        accepted.append(client_id)
    db.commit()
    status = diamond_status_for(db, user, progress=progress)
    return SyncResponse(
        message="Cloud learning records are up to date.", syncedAt=datetime.now(timezone.utc).isoformat(),
        diamondStatus=status, snapshot=profile.snapshot, learningRecords=cloud_learning_records(db, user.id),
        version=profile.version, acceptedRecordIds=accepted, rejectedRecordIds=rejected, conflictResolved=conflict,
    )


@app.post("/api/v1/learning/paper/complete", response_model=PaperCompletionResponse)
def complete_paper_learning(
    request: PaperCompletionRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> PaperCompletionResponse:
    require_guardian_consent_for_minor(db, user)
    content = db.get(Content, request.contentId)
    if content is None or content.content_type != "paper":
        raise HTTPException(status_code=404, detail="Paper content not found")
    metadata = content.metadata_json or {}
    paper_status = str(metadata.get("paperStatus", "current")).strip().lower()
    if paper_status == "retracted":
        raise HTTPException(status_code=409, detail="Retracted papers cannot create learning credentials")
    reflection = request.reflection.strip()
    if len(reflection) < 40:
        raise HTTPException(status_code=422, detail="Reflection must summarize the claim, evidence, and learning in at least 40 characters")
    existing = db.scalar(select(PaperLearningEvidence).where(
        PaperLearningEvidence.user_id == user.id, PaperLearningEvidence.content_id == request.contentId,
    ))
    xp_awarded = 0
    now = datetime.now(timezone.utc)
    if existing is None:
        existing = PaperLearningEvidence(user_id=user.id, content_id=request.contentId, reflection=reflection)
        db.add(existing)
        xp_awarded = 120
    existing.reflection = reflection
    existing.paper_status = paper_status
    existing.verified_at = now
    progress = get_or_create_progress(db, user.id)
    if xp_awarded:
        progress.xp += xp_awarded
        topic = content.topic or "해양교육"
        evidence = dict(progress.skill_evidence_json or {})
        mastery = dict(progress.skill_mastery_json or {})
        count = int(evidence.get(topic, 0))
        evidence[topic] = count + 1
        mastery[topic] = round((int(mastery.get(topic, 50)) * count + 82) / max(1, count + 1))
        progress.skill_evidence_json = evidence
        progress.skill_mastery_json = mastery
    profile = db.get(UserProfile, user.id) or UserProfile(user_id=user.id, snapshot={}, version=0)
    status = diamond_status_for(db, user, progress=progress)
    profile.snapshot = apply_verified_evidence(db, user.id, apply_account_state(apply_authoritative_progress(profile.snapshot or {}, progress, diamond_eligible=status.eligible), user))
    profile.version = int(profile.version or 0) + 1
    db.add(profile)
    db.commit()
    return PaperCompletionResponse(verified=True, xpAwarded=xp_awarded, message="Paper learning evidence was verified.", verifiedAt=now)


def merged_video_intervals(intervals, duration: int) -> tuple[list[list[float]], int]:
    cleaned: list[tuple[float, float]] = []
    for item in intervals:
        start = max(0.0, min(float(item.start), float(duration)))
        end = max(0.0, min(float(item.end), float(duration)))
        if end <= start or end - start > 15.0:
            continue
        cleaned.append((start, end))
    cleaned.sort()
    merged: list[list[float]] = []
    for start, end in cleaned:
        if not merged or start > merged[-1][1] + 1.5:
            merged.append([start, end])
        else:
            merged[-1][1] = max(merged[-1][1], end)
    watched = round(sum(end - start for start, end in merged))
    return merged, watched


@app.post("/api/v1/learning/video/verify", response_model=VideoEvidenceResponse)
def verify_video_learning(
    request: VideoEvidenceRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> VideoEvidenceResponse:
    require_guardian_consent_for_minor(db, user)
    content = db.get(Content, request.contentId)
    if content is None or content.content_type != "video":
        raise HTTPException(status_code=404, detail="Video content not found")
    catalog_seconds = max(0, int(content.minutes or 0) * 60)
    if catalog_seconds >= 60 and request.durationSeconds < round(catalog_seconds * 0.7):
        raise HTTPException(status_code=422, detail="Reported video duration is inconsistent with the catalog")
    merged, watched = merged_video_intervals(request.intervals, request.durationSeconds)
    coverage = min(100, round(watched * 100 / max(1, request.durationSeconds)))
    verified = coverage >= 70 and watched >= 45
    if not verified:
        return VideoEvidenceResponse(
            verified=False, watchedSeconds=watched, coveragePercent=coverage, xpAwarded=0,
            message="At least 70 percent of distinct playback intervals must be watched.",
        )
    existing = db.scalar(select(VideoLearningEvidence).where(
        VideoLearningEvidence.user_id == user.id, VideoLearningEvidence.content_id == request.contentId,
    ))
    xp_awarded = 0
    if existing is None:
        existing = VideoLearningEvidence(user_id=user.id, content_id=request.contentId)
        db.add(existing)
        xp_awarded = 100
    existing.duration_seconds = request.durationSeconds
    existing.watched_seconds = watched
    existing.coverage_percent = coverage
    existing.intervals_json = merged
    existing.verified_at = datetime.now(timezone.utc)

    progress = get_or_create_progress(db, user.id)
    if xp_awarded:
        progress.xp += xp_awarded
        topic = content.topic or "해양교육"
        evidence = dict(progress.skill_evidence_json or {})
        mastery = dict(progress.skill_mastery_json or {})
        count = int(evidence.get(topic, 0))
        old = int(mastery.get(topic, 50))
        evidence[topic] = count + 1
        mastery[topic] = round((old * count + 80) / max(1, count + 1))
        progress.skill_evidence_json = evidence
        progress.skill_mastery_json = mastery
    profile = db.get(UserProfile, user.id) or UserProfile(user_id=user.id)
    status = diamond_status_for(db, user, progress=progress)
    profile.snapshot = apply_verified_evidence(db, user.id, apply_account_state(apply_authoritative_progress(profile.snapshot or {}, progress, diamond_eligible=status.eligible), user))
    profile.version = int(profile.version or 0) + 1
    db.add(profile)
    db.commit()
    return VideoEvidenceResponse(
        verified=True, watchedSeconds=watched, coveragePercent=coverage, xpAwarded=xp_awarded,
        message="Distinct playback coverage was verified by the server.",
    )


def send_guardian_consent_email(recipient: str, token: str, learner_name: str, consent_version: str) -> None:
    query = urlencode({"token": token})
    base = settings.password_reset_base_url.rsplit("/", 1)[0] + "/guardian-consent"
    consent_url = base.rstrip("?") + ("&" if "?" in base else "?") + query
    if not settings.smtp_host:
        print(f"BluePath guardian consent link for {recipient}: {consent_url}")
        return
    message = EmailMessage()
    message["Subject"] = "BluePath 보호자 동의 확인"
    message["From"] = settings.smtp_from_email
    message["To"] = recipient
    message.set_content(
        f"{learner_name} 학습자의 BluePath 이용 동의 요청입니다. "
        f"동의 문서 버전은 {consent_version}이며 링크는 24시간 동안 유효합니다.\n\n{consent_url}"
    )
    with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as smtp:
        if settings.smtp_use_tls:
            smtp.starttls()
        if settings.smtp_username:
            smtp.login(settings.smtp_username, settings.smtp_password)
        smtp.send_message(message)


@app.post("/api/v1/guardian-consent/request", response_model=GuardianConsentStatus)
def request_guardian_consent(
    request: GuardianConsentRequestCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GuardianConsentStatus:
    now = datetime.now(timezone.utc)
    raw_token = secrets.token_urlsafe(40)
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()
    db.execute(delete(GuardianConsentRequest).where(
        GuardianConsentRequest.user_id == user.id, GuardianConsentRequest.consented_at.is_(None),
    ))
    record = GuardianConsentRequest(
        user_id=user.id, guardian_email=str(request.guardianEmail).lower(), token_hash=token_hash,
        consent_version=request.consentVersion, expires_at=now + timedelta(hours=24),
    )
    user.guardian_email = record.guardian_email
    user.guardian_consent = False
    user.guardian_consent_version = ""
    user.guardian_consented_at = None
    db.add(record)
    db.commit()
    try:
        send_guardian_consent_email(record.guardian_email, raw_token, user.nickname or user.display_name, record.consent_version)
    except Exception as exc:
        print(f"BluePath guardian consent delivery failed: {exc}")
    return GuardianConsentStatus(status="pending", guardianEmail=record.guardian_email, consentVersion=record.consent_version)


def guardian_email_masked(email: str) -> str:
    local, _, domain = email.partition("@")
    if not domain:
        return "***"
    shown = local[:1] if local else ""
    return f"{shown}***@{domain}"


@app.get("/api/v1/guardian-consent/details", response_model=GuardianConsentDetails)
def guardian_consent_details(token: str = Query(min_length=32, max_length=512), db: Session = Depends(get_db)) -> GuardianConsentDetails:
    token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
    record = db.scalar(select(GuardianConsentRequest).where(GuardianConsentRequest.token_hash == token_hash))
    if record is None:
        raise HTTPException(status_code=404, detail="Guardian consent token is invalid")
    user = db.get(User, record.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=404, detail="Learner account is unavailable")
    now = datetime.now(timezone.utc)
    expires = record.expires_at if record.expires_at.tzinfo else record.expires_at.replace(tzinfo=timezone.utc)
    if record.revoked_at is not None:
        status = "revoked"
    elif record.consented_at is not None:
        status = "confirmed"
    elif expires <= now:
        status = "expired"
    else:
        status = "pending"
    return GuardianConsentDetails(
        status=status,
        learnerName=user.nickname or user.display_name or "BluePath 학습자",
        guardianEmailMasked=guardian_email_masked(record.guardian_email),
        consentVersion=record.consent_version,
        expiresAt=expires,
        consentedAt=record.consented_at,
        terms=[
            "계정 및 연령대, 학습 진행도와 인증 증거를 서비스 제공과 포트폴리오 발급을 위해 저장합니다.",
            "커뮤니티에 직접 게시한 내용은 다른 이용자에게 표시될 수 있으므로 개인정보를 작성하지 않아야 합니다.",
            "보호자 동의는 학습자 계정에서 철회할 수 있으며 철회 후에는 인증 학습과 포트폴리오 신규 발급이 제한됩니다.",
            "법적 보관 의무가 없는 개인정보의 삭제와 계정 처리는 서비스 개인정보 처리방침에 따릅니다.",
        ],
    )


@app.post("/api/v1/guardian-consent/confirm", response_model=GuardianConsentStatus)
def confirm_guardian_consent(request: GuardianConsentConfirm, db: Session = Depends(get_db)) -> GuardianConsentStatus:
    token_hash = hashlib.sha256(request.token.encode("utf-8")).hexdigest()
    record = db.scalar(select(GuardianConsentRequest).where(GuardianConsentRequest.token_hash == token_hash))
    now = datetime.now(timezone.utc)
    if record is None or record.revoked_at is not None:
        raise HTTPException(status_code=400, detail="Guardian consent token is invalid")
    expires = record.expires_at if record.expires_at.tzinfo else record.expires_at.replace(tzinfo=timezone.utc)
    if expires <= now:
        raise HTTPException(status_code=400, detail="Guardian consent token has expired")
    user = db.get(User, record.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=400, detail="Learner account is unavailable")
    record.consented_at = record.consented_at or now
    user.guardian_email = record.guardian_email
    user.guardian_consent = True
    user.guardian_consent_version = record.consent_version
    user.guardian_consented_at = record.consented_at
    db.commit()
    return GuardianConsentStatus(
        status="confirmed", guardianEmail=record.guardian_email, consentVersion=record.consent_version,
        consentedAt=record.consented_at,
    )


@app.get("/api/v1/guardian-consent/status", response_model=GuardianConsentStatus)
def guardian_consent_status(user: User = Depends(get_current_user)) -> GuardianConsentStatus:
    return GuardianConsentStatus(
        status="confirmed" if user.guardian_consent else "pending" if user.guardian_email else "not_requested",
        guardianEmail=user.guardian_email or "", consentVersion=user.guardian_consent_version or "",
        consentedAt=user.guardian_consented_at,
    )


@app.delete("/api/v1/guardian-consent", response_model=GuardianConsentStatus)
def revoke_guardian_consent(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> GuardianConsentStatus:
    now = datetime.now(timezone.utc)
    db.execute(
        update(GuardianConsentRequest)
        .where(GuardianConsentRequest.user_id == user.id, GuardianConsentRequest.revoked_at.is_(None))
        .values(revoked_at=now)
    )
    user.guardian_consent = False
    user.guardian_consent_version = ""
    user.guardian_consented_at = None
    db.commit()
    return GuardianConsentStatus(status="revoked", guardianEmail=user.guardian_email or "")


def portfolio_signature(payload: dict, issued_at: datetime) -> str:
    normalized = issued_at.replace(tzinfo=timezone.utc) if issued_at.tzinfo is None else issued_at.astimezone(timezone.utc)
    canonical = json.dumps(
        {**payload, "issuedAt": normalized.isoformat()},
        ensure_ascii=False, sort_keys=True, separators=(",", ":"),
    )
    return hmac.new(settings.qr_signing_secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()


def portfolio_payload(db: Session, user: User, title: str) -> dict:
    progress = get_or_create_progress(db, user.id)
    status = diamond_status_for(db, user, progress=progress)
    paper_ids = valid_paper_evidence_ids(db, user.id)
    return {
        "title": title, "userId": user.id, "nickname": user.nickname or user.display_name,
        "tier": progress_tier(progress, status.eligible), "xp": progress.xp,
        "skillMastery": dict(progress.skill_mastery_json or {}),
        "skillEvidence": dict(progress.skill_evidence_json or {}),
        "verifiedVideos": db.scalar(select(func.count()).select_from(VideoLearningEvidence).where(VideoLearningEvidence.user_id == user.id)) or 0,
        "verifiedPapers": len(paper_ids),
        "verifiedPaperIds": paper_ids,
        "verifiedMissions": db.scalar(select(func.count()).select_from(MissionEvidence).where(
            MissionEvidence.user_id == user.id, MissionEvidence.status == "verified"
        )) or 0,
        "diamondEligible": status.eligible,
    }


@app.post("/api/v1/portfolio/credentials", response_model=PortfolioCredentialResponse)
def issue_portfolio_credential(
    request_body: PortfolioIssueRequest, request: Request,
    user: User = Depends(get_current_user), db: Session = Depends(get_db),
) -> PortfolioCredentialResponse:
    require_guardian_consent_for_minor(db, user)
    payload = portfolio_payload(db, user, request_body.title)
    issued_at = datetime.now(timezone.utc)
    signature = portfolio_signature(payload, issued_at)
    credential = PortfolioCredential(user_id=user.id, payload_json=payload, signature=signature, issued_at=issued_at)
    db.add(credential)
    db.commit()
    db.refresh(credential)
    verify_url = str(request.base_url).rstrip("/") + f"/api/v1/portfolio/credentials/{credential.id}"
    return PortfolioCredentialResponse(
        credentialId=credential.id, verifyUrl=verify_url, signature=signature, issuedAt=issued_at, payload=payload, revoked=False, valid=True,
    )


@app.get("/api/v1/portfolio/credentials/{credential_id}", response_model=PortfolioCredentialResponse)
def verify_portfolio_credential(credential_id: str, request: Request, db: Session = Depends(get_db)) -> PortfolioCredentialResponse:
    credential = db.get(PortfolioCredential, credential_id)
    if credential is None:
        raise HTTPException(status_code=404, detail="Portfolio credential not found")
    verify_url = str(request.base_url).rstrip("/") + f"/api/v1/portfolio/credentials/{credential.id}"
    payload = credential.payload_json or {}
    signature_valid = hmac.compare_digest(credential.signature, portfolio_signature(payload, credential.issued_at))
    issued_papers = {str(value) for value in payload.get("verifiedPaperIds", []) if str(value).strip()}
    current_papers = set(valid_paper_evidence_ids(db, credential.user_id))
    evidence_valid = not issued_papers or issued_papers.issubset(current_papers)
    revoked = credential.revoked_at is not None
    return PortfolioCredentialResponse(
        credentialId=credential.id, verifyUrl=verify_url, signature=credential.signature, issuedAt=credential.issued_at,
        payload=payload, revoked=revoked, valid=signature_valid and evidence_valid and not revoked,
    )


@app.delete("/api/v1/portfolio/credentials/{credential_id}", response_model=GenericResponse)
def revoke_portfolio_credential(
    credential_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db),
) -> GenericResponse:
    credential = db.scalar(select(PortfolioCredential).where(
        PortfolioCredential.id == credential_id, PortfolioCredential.user_id == user.id,
    ))
    if credential is None:
        raise HTTPException(status_code=404, detail="Portfolio credential not found")
    credential.revoked_at = datetime.now(timezone.utc)
    db.commit()
    return GenericResponse(message="Portfolio credential was revoked.")


@app.post("/api/v1/diamond/evidence", response_model=GenericResponse)
def submit_diamond_evidence(
    request: EvidenceRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> GenericResponse:
    evidence = db.scalar(
        select(DiamondEvidence).where(
            DiamondEvidence.user_id == user.id,
            DiamondEvidence.evidence_type == request.evidenceType,
        )
    ) or DiamondEvidence(user_id=user.id, evidence_type=request.evidenceType, title="", evidence_url="")
    evidence.title = request.title
    evidence.evidence_url = str(request.evidenceUrl)
    evidence.status = "pending"
    evidence.review_note = ""
    evidence.reviewed_at = None
    db.add(evidence)
    db.commit()
    return GenericResponse(message="Evidence was submitted for administrator review.")


@app.get("/api/v1/diamond/status", response_model=DiamondStatus)
def diamond_status(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> DiamondStatus:
    return diamond_status_for(db, user)


@app.post("/api/v1/reminders", response_model=ReminderResponse)
def create_reminder(
    request: ReminderRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ReminderResponse:
    reminder = Reminder(
        user_id=user.id,
        title=request.title,
        remind_at=request.remindAt,
        reminder_type=request.reminderType,
    )
    db.add(reminder)
    db.commit()
    db.refresh(reminder)
    return ReminderResponse(
        id=reminder.id,
        title=reminder.title,
        remindAt=reminder.remind_at,
        reminderType=reminder.reminder_type,
        enabled=reminder.enabled,
    )


@app.get("/api/v1/reminders", response_model=list[ReminderResponse])
def list_reminders(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[ReminderResponse]:
    reminders = list(db.scalars(select(Reminder).where(Reminder.user_id == user.id).order_by(Reminder.remind_at)))
    return [
        ReminderResponse(
            id=item.id,
            title=item.title,
            remindAt=item.remind_at,
            reminderType=item.reminder_type,
            enabled=item.enabled,
        )
        for item in reminders
    ]


@app.get("/api/v1/admin/analytics/demand")
def ocean_demand_radar(
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    """Aggregate anonymous learning demand and bundled survey evidence for institutions."""
    profiles = list(db.scalars(select(UserProfile)))
    interest_counts: Counter[str] = Counter()
    goal_counts: Counter[str] = Counter()
    skill_totals: Counter[str] = Counter()
    skill_counts: Counter[str] = Counter()
    for profile in profiles:
        snapshot = profile.snapshot or {}
        interest = str(snapshot.get("interest", "")).strip()
        goal = str(snapshot.get("goal", "")).strip()
        if interest:
            interest_counts[interest] += 1
        if goal:
            goal_counts[goal] += 1
        mastery = snapshot.get("skillMastery", {})
        if isinstance(mastery, dict):
            for topic, raw_score in mastery.items():
                if isinstance(raw_score, (int, float)):
                    skill_totals[str(topic)] += int(raw_score)
                    skill_counts[str(topic)] += 1

    records = list(db.scalars(select(LearningRecord)))
    record_types = Counter(record.record_type for record in records)
    completed_targets = Counter(
        record.title or record.target_id
        for record in records
        if "completed" in (record.status or "").lower() or "passed" in (record.status or "").lower()
    )
    content_supply = Counter(
        item.topic or "해양교육"
        for item in db.scalars(select(Content))
        if item.content_type in {"video", "program", "schedule"}
    )

    average_mastery = {
        topic: round(skill_totals[topic] / skill_counts[topic])
        for topic in skill_totals
        if skill_counts[topic]
    }
    skill_gaps = sorted(
        ({"topic": topic, "average": score, "gap": 100 - score} for topic, score in average_mastery.items()),
        key=lambda item: item["average"],
    )
    demand_supply = []
    for topic in sorted(set(interest_counts) | set(content_supply)):
        demand = interest_counts.get(topic, 0)
        supply = content_supply.get(topic, 0)
        demand_supply.append({
            "topic": topic,
            "learnerDemand": demand,
            "contentSupply": supply,
            "opportunityScore": demand * 10 - supply,
        })
    demand_supply.sort(key=lambda item: item["opportunityScore"], reverse=True)

    survey = {"sampleSize": 0, "metrics": []}
    survey_path = ASSETS_DIR / "survey_insights.json"
    if survey_path.exists():
        survey = json.loads(survey_path.read_text(encoding="utf-8"))

    return {
        "profileCount": len(profiles),
        "learningRecordCount": len(records),
        "interestDistribution": dict(interest_counts.most_common()),
        "goalDistribution": dict(goal_counts.most_common()),
        "recordTypeDistribution": dict(record_types.most_common()),
        "skillGaps": skill_gaps,
        "demandSupply": demand_supply,
        "topCompleted": [{"title": title, "count": count} for title, count in completed_targets.most_common(8)],
        "survey": survey,
        "recommendations": build_demand_recommendations(interest_counts, content_supply, skill_gaps, survey),
    }


def build_demand_recommendations(
    interests: Counter[str],
    supply: Counter[str],
    skill_gaps: list[dict],
    survey: dict,
) -> list[str]:
    recommendations: list[str] = []
    if interests:
        topic, count = interests.most_common(1)[0]
        recommendations.append(
            f"학습자 관심이 가장 높은 ‘{topic}’ 분야({count}명)의 신규 체험·심화 과정을 우선 검토하세요."
        )
    if skill_gaps:
        gap = skill_gaps[0]
        recommendations.append(
            f"평균 숙련도가 가장 낮은 ‘{gap['topic']}’({gap['average']}점)에 진단-복습-프로젝트형 과정을 배치하세요."
        )
    for metric in survey.get("metrics", []):
        label = str(metric.get("label", ""))
        if "모바일" in label:
            recommendations.append(
                f"관람객 {metric.get('value', 0)}/{metric.get('total', survey.get('sampleSize', 0))}명의 모바일 안내 수요를 현장 협동 미션과 연결하세요."
            )
            break
    if interests:
        topic, demand = interests.most_common(1)[0]
        available = supply.get(topic, 0)
        recommendations.append(
            f"‘{topic}’ 수요 {demand}명 대비 관련 콘텐츠 {available}개의 공급 균형을 정기적으로 점검하세요."
        )
    if not recommendations:
        recommendations.append("학습 기록이 쌓이면 관심 분야, 역량 갭, 공급 부족을 바탕으로 과정 개설 우선순위를 제안합니다.")
    return recommendations


@app.get("/api/v1/admin/analytics/outcomes")
def education_outcomes(
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return outcome_analytics(db)


@app.post("/api/v1/admin/program-draft", response_model=ProgramDraftResponse)
def ai_program_draft(
    request: ProgramDraftRequest,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> ProgramDraftResponse:
    return generate_program_draft(db, request)


@app.get("/api/v1/admin/community/reports")
def admin_list_community_reports(
    status: str = Query(default="pending", pattern="^(pending|resolved|dismissed|all)$"),
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[dict]:
    statement = select(CommunityReport).order_by(CommunityReport.created_at.desc())
    if status != "all":
        statement = statement.where(CommunityReport.status == status)
    rows = list(db.scalars(statement.limit(500)))
    return [
        {
            "id": item.id,
            "reporterId": item.reporter_id,
            "targetType": item.target_type,
            "targetId": item.target_id,
            "reason": item.reason,
            "status": item.status,
            "reviewNote": item.review_note,
            "reviewedBy": item.reviewed_by,
            "reviewedAt": item.reviewed_at,
            "createdAt": item.created_at,
        }
        for item in rows
    ]


@app.post("/api/v1/admin/community/reports/{report_id}/review", response_model=GenericResponse)
def admin_review_community_report(
    report_id: str,
    request: CommunityModerationRequest,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> GenericResponse:
    report = db.get(CommunityReport, report_id)
    if report is None:
        raise HTTPException(status_code=404, detail="Report not found")
    target = (
        db.get(CommunityPost, report.target_id) if report.target_type == "post"
        else db.get(CommunityComment, report.target_id) if report.target_type == "comment"
        else db.get(User, report.target_id)
    )
    if request.action == "delete" and target is not None:
        if report.target_type == "post":
            delete_community_post(report.target_id, admin, db)
        elif report.target_type == "comment":
            delete_community_comment(report.target_id, admin, db)
        else:
            raise HTTPException(status_code=400, detail="User reports require deactivate or none")
    elif request.action == "deactivate":
        target_user = target if report.target_type == "user" else None
        if report.target_type == "post" and target is not None:
            target_user = db.get(User, target.user_id)
        elif report.target_type == "comment" and target is not None:
            target_user = db.get(User, target.user_id)
        if target_user is None:
            raise HTTPException(status_code=404, detail="Reported user not found")
        if is_admin(target_user):
            raise HTTPException(status_code=400, detail="Admin accounts cannot be deactivated here")
        target_user.is_active = False
    report.status = request.status
    report.review_note = request.reviewNote.strip()
    report.reviewed_by = admin.id
    report.reviewed_at = datetime.now(timezone.utc)
    db.add(report)
    db.commit()
    return GenericResponse(message="Community report review was saved.")


@app.get("/api/v1/admin/content", response_model=list[AdminContentItem])
def admin_list_content(
    content_type: str | None = None,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[AdminContentItem]:
    statement = select(Content).order_by(Content.updated_at.desc())
    if content_type:
        statement = statement.where(Content.content_type == content_type)
    return [content_to_schema(item) for item in db.scalars(statement.limit(300))]


@app.post("/api/v1/admin/content", response_model=AdminContentItem)
def admin_save_content(
    request: AdminContentItem,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> AdminContentItem:
    normalized_doi = request.doi.strip().lower().removeprefix("https://doi.org/").removeprefix("http://doi.org/")
    if request.contentType == "paper" and normalized_doi:
        duplicates = list(db.scalars(select(Content).where(Content.content_type == "paper", Content.id != request.id)))
        if any(str((row.metadata_json or {}).get("doi", "")).strip().lower().removeprefix("https://doi.org/").removeprefix("http://doi.org/") == normalized_doi for row in duplicates):
            raise HTTPException(status_code=409, detail="A paper with this DOI already exists")
    if request.contentType == "paper" and request.paperStatus not in {"current", "corrected", "retracted"}:
        raise HTTPException(status_code=422, detail="Unsupported paper status")
    item = db.get(Content, request.id) or Content(id=request.id, title=request.title)
    item.title = request.title
    item.content_type = request.contentType
    item.source = request.source
    item.url = request.url
    item.difficulty = request.difficulty
    item.required_tier = request.requiredTier
    item.topic = request.topic
    item.career_tag = request.careerTag
    item.minutes = request.minutes
    item.metadata_json = {
        **(item.metadata_json or {}),
        "startAt": request.startAt,
        "endAt": request.endAt,
        "target": request.target,
        "method": request.method,
        "category": request.category,
        "description": request.description,
        "authors": request.authors,
        "year": request.year,
        "doi": normalized_doi,
        "applicationUrl": request.applicationUrl,
        "applicationDeadline": request.applicationDeadline,
        "capacity": max(0, request.capacity),
        "waitlistAvailable": request.waitlistAvailable,
        "timezone": request.timezone or "Asia/Seoul",
        "paperStatus": request.paperStatus,
        "versionNote": request.versionNote,
    }
    db.add(item)
    db.commit()
    db.refresh(item)
    return content_to_schema(item)


@app.delete("/api/v1/admin/content/{content_id}", response_model=GenericResponse)
def admin_delete_content(
    content_id: str,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> GenericResponse:
    item = db.get(Content, content_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Content not found")
    db.delete(item)
    db.commit()
    return GenericResponse(message="Content was deleted.")


@app.get("/api/v1/admin/quizzes", response_model=list[AdminQuizItem])
def admin_list_quizzes(
    tier: str | None = None,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[AdminQuizItem]:
    statement = select(QuizBankItem).order_by(QuizBankItem.tier, QuizBankItem.id)
    if tier:
        statement = statement.where(QuizBankItem.tier == tier)
    return [quiz_to_schema(item) for item in db.scalars(statement.limit(500))]


@app.post("/api/v1/admin/quizzes", response_model=AdminQuizItem)
def admin_save_quiz(
    request: AdminQuizItem,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> AdminQuizItem:
    item = db.get(QuizBankItem, request.id) or QuizBankItem(id=request.id, tier=request.tier, question=request.question, answer_index=request.answerIndex)
    item.tier = request.tier
    item.topic = request.topic
    item.question = request.question
    item.options = request.options
    item.answer_index = request.answerIndex
    item.explanation = request.explanation
    item.source_title = request.sourceTitle
    item.source_url = request.sourceUrl
    item.active = request.active
    db.add(item)
    db.commit()
    db.refresh(item)
    return quiz_to_schema(item)


@app.delete("/api/v1/admin/quizzes/{quiz_id}", response_model=GenericResponse)
def admin_delete_quiz(
    quiz_id: str,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> GenericResponse:
    item = db.get(QuizBankItem, quiz_id)
    if item is None:
        raise HTTPException(status_code=404, detail="Quiz not found")
    db.delete(item)
    db.commit()
    return GenericResponse(message="Quiz was deleted.")


@app.post("/api/v1/admin/content/upload")
def admin_upload_content(
    file: UploadFile = File(...),
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    imported, errors = import_content_file(db, file)
    return {"imported": imported, "errors": errors}


@app.post("/api/v1/admin/knowledge/upload")
def admin_upload_knowledge(
    file: UploadFile = File(...),
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    imported, errors = import_knowledge_file(db, file)
    return {"imported": imported, "errors": errors, "nextStep": "Run RAG embedding update."}


@app.post("/api/v1/admin/quizzes/upload")
def admin_upload_quizzes(
    file: UploadFile = File(...),
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    imported, errors = import_quiz_file(db, file)
    return {"imported": imported, "errors": errors}


@app.post("/api/v1/admin/youtube/sync")
def admin_youtube_sync(
    request: YouTubeSyncRequest,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    imported = sync_youtube(db, request.queries, request.maxResultsPerQuery)
    return {"imported": imported}


@app.post("/api/v1/admin/schedules/sync")
def admin_sync_schedules(
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    if not settings.schedule_feed_list:
        raise HTTPException(status_code=400, detail="SCHEDULE_FEED_URLS is not configured")
    return sync_schedule_feeds(db, settings.schedule_feed_list, settings.schedule_feed_timeout_seconds)


@app.post("/api/v1/admin/rag/embed")
def admin_embed_knowledge(
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> dict:
    return {"embedded": embed_missing_chunks(db)}


@app.get("/api/v1/admin/diamond/pending")
def admin_pending_diamond(
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> list[dict]:
    rows = list(db.scalars(select(DiamondEvidence).where(DiamondEvidence.status == "pending")))
    return [
        {
            "id": item.id,
            "userId": item.user_id,
            "evidenceType": item.evidence_type,
            "title": item.title,
            "evidenceUrl": item.evidence_url,
            "status": item.status,
        }
        for item in rows
    ]


@app.post("/api/v1/admin/diamond/{evidence_id}/review", response_model=GenericResponse)
def admin_review_diamond(
    evidence_id: str,
    request: EvidenceReviewRequest,
    _: User = Depends(require_admin),
    db: Session = Depends(get_db),
) -> GenericResponse:
    evidence = db.get(DiamondEvidence, evidence_id)
    if evidence is None:
        raise HTTPException(status_code=404, detail="Evidence not found")
    evidence.status = request.status
    evidence.review_note = request.reviewNote
    evidence.reviewed_at = datetime.now(timezone.utc)
    db.commit()
    return GenericResponse(message="Evidence review was saved.")


def content_to_schema(item: Content) -> AdminContentItem:
    metadata = item.metadata_json or {}
    return AdminContentItem(
        id=item.id,
        title=item.title,
        contentType=item.content_type,
        source=item.source,
        url=item.url,
        difficulty=item.difficulty,
        requiredTier=item.required_tier,
        topic=item.topic,
        careerTag=item.career_tag,
        minutes=item.minutes,
        startAt=str(metadata.get("startAt", "")),
        endAt=str(metadata.get("endAt", "")),
        target=str(metadata.get("target", "전체")),
        method=str(metadata.get("method", "")),
        category=str(metadata.get("category", "")),
        description=str(metadata.get("description", "")),
        authors=str(metadata.get("authors", "")),
        year=str(metadata.get("year", "")),
        doi=str(metadata.get("doi", "")),
        applicationUrl=str(metadata.get("applicationUrl", item.url if item.content_type in {"program", "schedule", "event"} else "")),
        applicationDeadline=str(metadata.get("applicationDeadline", "")),
        capacity=int(metadata.get("capacity", 0) or 0),
        waitlistAvailable=bool(metadata.get("waitlistAvailable", False)),
        timezone=str(metadata.get("timezone", "Asia/Seoul")),
        paperStatus=str(metadata.get("paperStatus", "current")),
        versionNote=str(metadata.get("versionNote", "")),
    )


def quiz_to_schema(item: QuizBankItem) -> AdminQuizItem:
    return AdminQuizItem(
        id=item.id,
        tier=item.tier,
        topic=item.topic,
        question=item.question,
        options=list(item.options),
        answerIndex=item.answer_index,
        explanation=item.explanation,
        sourceTitle=item.source_title,
        sourceUrl=item.source_url,
        active=item.active,
    )


def auth_response(user: User, db: Session) -> AuthResponse:
    nickname = user.nickname or user.display_name or user.email.split("@")[0]
    return AuthResponse(
        accessToken=create_access_token(user),
        email=user.email,
        displayName=user.display_name,
        nickname=nickname,
        profileImageUrl=user.profile_image_url or "",
        followerCount=count_followers(db, user.id),
        followingCount=count_following(db, user.id),
        joinedAt=user.created_at,
    )


def normalize_nickname(value: str) -> str:
    nickname = value.strip()
    if not 2 <= len(nickname) <= 20:
        raise HTTPException(status_code=422, detail="Nickname must be 2-20 characters")
    if not all(ch.isalnum() or ch in "_.-" for ch in nickname):
        raise HTTPException(status_code=422, detail="Nickname contains unsupported characters")
    return nickname


def count_followers(db: Session, user_id: str) -> int:
    return len(list(db.scalars(select(Follow.id).where(Follow.following_id == user_id))))


def count_following(db: Session, user_id: str) -> int:
    return len(list(db.scalars(select(Follow.id).where(Follow.follower_id == user_id))))


def profile_summary(db: Session, target: User, viewer: User) -> ProfileSummary:
    snapshot = target.profile.snapshot if target.profile else {}
    progress = db.get(UserProgress, target.id)
    followed = db.scalar(select(Follow.id).where(Follow.follower_id == viewer.id, Follow.following_id == target.id)) is not None
    return ProfileSummary(
        userId=target.id,
        nickname=target.nickname or target.display_name or target.email.split("@")[0],
        profileImageUrl=target.profile_image_url or "",
        tier=progress_tier(progress) if progress else str(snapshot.get("tier", "브론즈")),
        followerCount=count_followers(db, target.id),
        followingCount=count_following(db, target.id),
        isFollowing=followed,
        joinedAt=target.created_at,
    )


def reaction_summaries(db: Session, target_type: str, target_id: str, viewer_id: str) -> list[ReactionSummary]:
    statement = select(CommunityReaction).where(
        CommunityReaction.target_type == target_type,
        CommunityReaction.target_id == target_id,
    )
    excluded = list(blocked_user_ids(db, viewer_id))
    if excluded:
        statement = statement.where(CommunityReaction.user_id.not_in(excluded))
    rows = list(db.scalars(statement))
    counts: Counter[str] = Counter(item.emoji for item in rows)
    mine = {item.emoji for item in rows if item.user_id == viewer_id}
    order = ["👍", "❤️", "😂", "😮", "😢", "👏", "🔥", "🌊"]
    return [ReactionSummary(emoji=emoji, count=counts[emoji], reactedByMe=emoji in mine) for emoji in order if counts[emoji]]


def community_comment_schema(db: Session, comment: CommunityComment, viewer: User) -> CommunityCommentItem:
    author = db.get(User, comment.user_id)
    return CommunityCommentItem(
        id=comment.id,
        postId=comment.post_id,
        parentId=comment.parent_id,
        author=profile_summary(db, author, viewer),
        body=comment.body,
        createdAt=comment.created_at,
        updatedAt=comment.updated_at,
        canEdit=comment.user_id == viewer.id or is_admin(viewer),
        reactions=reaction_summaries(db, "comment", comment.id, viewer.id),
    )


def community_post_schema(db: Session, post: CommunityPost, viewer: User) -> CommunityPostItem:
    author = db.get(User, post.user_id)
    blocked_ids = list(blocked_user_ids(db, viewer.id))
    comment_statement = select(CommunityComment).where(CommunityComment.post_id == post.id)
    if blocked_ids:
        comment_statement = comment_statement.where(CommunityComment.user_id.notin_(blocked_ids))
    comments = list(db.scalars(comment_statement.order_by(CommunityComment.created_at)))
    return CommunityPostItem(
        id=post.id,
        category=post.category,
        author=profile_summary(db, author, viewer),
        title=post.title,
        body=post.body,
        createdAt=post.created_at,
        updatedAt=post.updated_at,
        canEdit=post.user_id == viewer.id or is_admin(viewer),
        reactions=reaction_summaries(db, "post", post.id, viewer.id),
        comments=[community_comment_schema(db, item, viewer) for item in comments],
    )


def cloud_learning_records(db: Session, user_id: str) -> list[dict]:
    rows = db.scalars(
        select(LearningRecord).where(LearningRecord.user_id == user_id)
        .order_by(LearningRecord.client_updated_at.asc(), LearningRecord.synced_at.asc())
        .limit(2000)
    )
    return [
        {
            "id": item.client_record_id,
            "recordType": item.record_type,
            "targetId": item.target_id,
            "title": item.title,
            "status": item.status,
            "updatedAt": item.client_updated_at,
        }
        for item in rows
    ]


def diamond_status_for(db: Session, user: User, progress: UserProgress | None = None) -> DiamondStatus:
    progress = progress or db.get(UserProgress, user.id)
    advanced = bool(progress.diamond_advanced_quiz_passed) if progress else False
    evidence = list(db.scalars(select(DiamondEvidence).where(DiamondEvidence.user_id == user.id)))
    statuses = {item.evidence_type: item.status for item in evidence}
    certification = statuses.get("certification", "not_submitted")
    project = statuses.get("project", "not_submitted")
    eligible = advanced and certification == "approved" and project == "approved"
    message = "Diamond pathway complete." if eligible else "Complete the advanced quiz and receive approval for both evidence items."
    return DiamondStatus(
        advancedQuizPassed=advanced,
        certificationStatus=certification,
        projectStatus=project,
        eligible=eligible,
        message=message,
    )


def send_password_reset_email(recipient: str, token: str) -> None:
    query = urlencode({"token": token})
    reset_url = settings.password_reset_base_url.rstrip("?") + ("&" if "?" in settings.password_reset_base_url else "?") + query
    if not settings.smtp_host:
        print(f"BluePath password reset link for {recipient}: {reset_url}")
        return
    message = EmailMessage()
    message["Subject"] = "BluePath 비밀번호 재설정"
    message["From"] = settings.smtp_from_email
    message["To"] = recipient
    message.set_content(
        "BluePath 비밀번호를 재설정하려면 아래 링크를 열어 주세요. "
        f"이 링크는 {max(5, settings.password_reset_token_minutes)}분 동안 유효합니다.\n\n{reset_url}"
    )
    with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as smtp:
        if settings.smtp_use_tls:
            smtp.starttls()
        if settings.smtp_username:
            smtp.login(settings.smtp_username, settings.smtp_password)
        smtp.send_message(message)


def bootstrap_admin(db: Session) -> None:
    if not settings.admin_email or not settings.admin_password:
        return
    email = settings.admin_email.lower().strip()
    user = db.scalar(select(User).where(User.email == email))
    if user:
        changed = False
        if user.role != "super_admin":
            user.role = "super_admin"
            changed = True
        if not user.nickname:
            user.nickname = "BluePathAdmin"
            changed = True
        if changed:
            db.commit()
        return
    db.add(
        User(
            email=email,
            password_hash=hash_password(settings.admin_password),
            display_name="BluePath Admin",
            nickname="BluePathAdmin",
            role="super_admin",
        )
    )
    db.commit()
