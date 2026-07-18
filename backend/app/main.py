from __future__ import annotations

import asyncio
import json
import hashlib
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
from sqlalchemy import delete, func, inspect, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .config import get_settings
from .database import Base, SessionLocal, engine, get_db
from .models import (
    CommunityComment,
    CommunityPost,
    CommunityReaction,
    Content,
    DiamondEvidence,
    Follow,
    LearningRecord,
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
    CommunityCommentCreate,
    CommunityCommentItem,
    CommunityPostCreate,
    CommunityPostItem,
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
    QuizRequest,
    QuizResponse,
    ReactionSummary,
    ReactionToggleRequest,
    ReactionToggleResponse,
    ReminderRequest,
    ReminderResponse,
    SyncRequest,
    SyncResponse,
    YouTubeSyncRequest,
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
from .security import create_access_token, get_current_user, hash_password, require_admin, verify_password
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
STATIC_DIR = Path(__file__).resolve().parents[1] / "static"
UPLOADS_DIR = Path(__file__).resolve().parents[1] / "uploads"
UPLOADS_DIR.mkdir(parents=True, exist_ok=True)
ASSETS_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets"


def apply_compatibility_migrations() -> None:
    """Keep existing SQLite/PostgreSQL deployments bootable after additive profile changes."""
    inspector = inspect(engine)
    if "users" not in inspector.get_table_names():
        return
    columns = {column["name"] for column in inspector.get_columns("users")}
    statements: list[str] = []
    if "nickname" not in columns:
        statements.append("ALTER TABLE users ADD COLUMN nickname VARCHAR(40)")
    if "profile_image_url" not in columns:
        statements.append("ALTER TABLE users ADD COLUMN profile_image_url TEXT NOT NULL DEFAULT ''")
    with engine.begin() as connection:
        for statement in statements:
            connection.execute(text(statement))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS ix_users_nickname ON users (nickname)"))
        connection.execute(text("CREATE UNIQUE INDEX IF NOT EXISTS uq_users_nickname_ci ON users (lower(nickname))"))


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
    scheduler = asyncio.create_task(scheduled_youtube_sync())
    try:
        yield
    finally:
        scheduler.cancel()
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
    _: User = Depends(get_current_user),
) -> QuizResponse:
    return generate_quiz(db, request)


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
    try:
        return verify_family_mission(
            db, user, request.missionId, request.completionNote, request.participantCount, request.qrPayload
        )
    except ValueError as exc:
        status = 404 if str(exc) == "Mission not found" else 400
        raise HTTPException(status_code=status, detail=str(exc)) from exc


@app.post("/api/v1/program-participation", response_model=ProgramParticipationResponse)
def save_program_participation(
    request: ProgramParticipationRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> ProgramParticipationResponse:
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


@app.get("/api/v1/community/posts", response_model=list[CommunityPostItem])
def list_community_posts(
    category: str = Query(default="free", pattern="^(free|question)$"),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[CommunityPostItem]:
    rows = list(db.scalars(
        select(CommunityPost).where(CommunityPost.category == category).order_by(CommunityPost.created_at.desc()).limit(50)
    ))
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
    if request.parentId:
        parent = db.get(CommunityComment, request.parentId)
        if parent is None or parent.post_id != post_id:
            raise HTTPException(status_code=400, detail="Parent comment does not belong to this post")
    comment = CommunityComment(post_id=post_id, user_id=user.id, parent_id=request.parentId, body=request.body.strip())
    db.add(comment)
    db.commit()
    db.refresh(comment)
    return community_comment_schema(db, comment, user)


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


@app.get("/api/v1/sync", response_model=CloudStateResponse)
def get_cloud_state(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> CloudStateResponse:
    profile = db.get(UserProfile, user.id)
    return CloudStateResponse(
        snapshot=profile.snapshot if profile else {},
        diamondStatus=diamond_status_for(db, user),
    )


@app.post("/api/v1/sync", response_model=SyncResponse)
def sync_progress(
    request: SyncRequest,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> SyncResponse:
    profile = db.get(UserProfile, user.id) or UserProfile(user_id=user.id)
    profile.snapshot = request.snapshot
    db.add(profile)

    user.guardian_consent = bool(request.snapshot.get("guardianConsent", user.guardian_consent))
    guardian_email = str(request.snapshot.get("guardianEmail", "")).strip()
    if guardian_email:
        user.guardian_email = guardian_email

    for record in request.learningRecords:
        client_id = str(record.id)
        exists = db.scalar(
            select(LearningRecord).where(
                LearningRecord.user_id == user.id,
                LearningRecord.client_record_id == client_id,
            )
        )
        if exists:
            continue
        db.add(
            LearningRecord(
                user_id=user.id,
                client_record_id=client_id,
                record_type=record.recordType,
                target_id=record.targetId,
                title=record.title,
                status=record.status,
                client_updated_at=record.updatedAt,
            )
        )
    db.commit()
    status = diamond_status_for(db, user)
    return SyncResponse(
        message="Cloud learning records are up to date.",
        syncedAt=datetime.now(timezone.utc).isoformat(),
        diamondStatus=status,
        snapshot=profile.snapshot,
    )


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
    followed = db.scalar(select(Follow.id).where(Follow.follower_id == viewer.id, Follow.following_id == target.id)) is not None
    return ProfileSummary(
        userId=target.id,
        nickname=target.nickname or target.display_name or target.email.split("@")[0],
        profileImageUrl=target.profile_image_url or "",
        tier=str(snapshot.get("tier", "브론즈")),
        followerCount=count_followers(db, target.id),
        followingCount=count_following(db, target.id),
        isFollowing=followed,
        joinedAt=target.created_at,
    )


def reaction_summaries(db: Session, target_type: str, target_id: str, viewer_id: str) -> list[ReactionSummary]:
    rows = list(db.scalars(select(CommunityReaction).where(
        CommunityReaction.target_type == target_type,
        CommunityReaction.target_id == target_id,
    )))
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
        reactions=reaction_summaries(db, "comment", comment.id, viewer.id),
    )


def community_post_schema(db: Session, post: CommunityPost, viewer: User) -> CommunityPostItem:
    author = db.get(User, post.user_id)
    comments = list(db.scalars(
        select(CommunityComment).where(CommunityComment.post_id == post.id).order_by(CommunityComment.created_at)
    ))
    return CommunityPostItem(
        id=post.id,
        category=post.category,
        author=profile_summary(db, author, viewer),
        title=post.title,
        body=post.body,
        createdAt=post.created_at,
        reactions=reaction_summaries(db, "post", post.id, viewer.id),
        comments=[community_comment_schema(db, item, viewer) for item in comments],
    )


def diamond_status_for(db: Session, user: User) -> DiamondStatus:
    profile = db.get(UserProfile, user.id)
    snapshot = profile.snapshot if profile else {}
    advanced = bool(snapshot.get("diamondAdvancedQuizPassed", False))
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
