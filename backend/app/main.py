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

from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .database import Base, SessionLocal, engine, get_db
from .models import Content, DiamondEvidence, LearningRecord, PasswordResetToken, QuizBankItem, Reminder, User, UserProfile
from .schemas import (
    AdminContentItem,
    AdminQuizItem,
    AgentRequest,
    AgentResponse,
    AuthRequest,
    AuthResponse,
    CloudStateResponse,
    PasswordResetConfirm,
    PasswordResetRequest,
    DiamondStatus,
    EvidenceRequest,
    EvidenceReviewRequest,
    GenericResponse,
    QuizRequest,
    QuizResponse,
    ReminderRequest,
    ReminderResponse,
    SyncRequest,
    SyncResponse,
    YouTubeSyncRequest,
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
    seed_quizzes,
    sync_youtube,
)

settings = get_settings()
STATIC_DIR = Path(__file__).resolve().parents[1] / "static"
ASSETS_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets"


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


app = FastAPI(title=settings.app_name, version="1.2.0", lifespan=lifespan)
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
    }


@app.get("/admin", include_in_schema=False)
def admin_dashboard() -> FileResponse:
    return FileResponse(STATIC_DIR / "admin.html")


@app.get("/reset-password", include_in_schema=False)
def password_reset_page() -> FileResponse:
    return FileResponse(STATIC_DIR / "reset-password.html")


@app.post("/api/v1/auth/register", response_model=AuthResponse)
def register(request: AuthRequest, db: Session = Depends(get_db)) -> AuthResponse:
    email = request.email.lower().strip()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(status_code=409, detail="Email is already registered")
    user = User(
        email=email,
        password_hash=hash_password(request.password),
        display_name=email.split("@")[0],
        guardian_email=str(request.guardianEmail) if request.guardianEmail else None,
        guardian_consent=False,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return auth_response(user)


@app.post("/api/v1/auth/login", response_model=AuthResponse)
def login(request: AuthRequest, db: Session = Depends(get_db)) -> AuthResponse:
    user = db.scalar(select(User).where(User.email == request.email.lower().strip()))
    if not user or not verify_password(request.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Incorrect email or password")
    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account is disabled")
    return auth_response(user)


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


@app.get("/api/v1/catalog", response_model=list[AdminContentItem])
def learner_catalog(
    _: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[AdminContentItem]:
    items = db.scalars(select(Content).order_by(Content.content_type, Content.updated_at.desc()).limit(500))
    return [content_to_schema(item) for item in items]


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


def auth_response(user: User) -> AuthResponse:
    return AuthResponse(
        accessToken=create_access_token(user),
        email=user.email,
        displayName=user.display_name,
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
        if user.role != "super_admin":
            user.role = "super_admin"
            db.commit()
        return
    db.add(
        User(
            email=email,
            password_hash=hash_password(settings.admin_password),
            display_name="BluePath Admin",
            role="super_admin",
        )
    )
    db.commit()
