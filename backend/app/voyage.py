from __future__ import annotations

import hashlib
import hmac
import json
import math
import re
import secrets
import uuid
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone
from typing import Any

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from .config import get_settings
from .models import (
    Content,
    LearningRecord,
    MissionEvidence,
    MissionQrNonce,
    ProgramParticipation,
    RouteNode,
    RouteOutcomeEvent,
    RoutePlan,
    User,
    UserProfile,
)
from .schemas import (
    FamilyMissionResponse,
    MissionGenerateRequest,
    MissionQrIssueRequest,
    MissionQrIssueResponse,
    MissionQrPayload,
    MissionRole,
    MissionVerifyResponse,
    ProgramDraftRequest,
    ProgramDraftResponse,
    ProgramParticipationRequest,
    ProgramParticipationResponse,
    RouteNodeItem,
    RoutePlanRequest,
    RoutePlanResponse,
    RouteSimulationRequest,
    RouteSimulationResponse,
    SourceItem,
)
from .services import call_chat, retrieve_sources

settings = get_settings()

CAREER_MAP: dict[str, tuple[list[str], list[str]]] = {
    "해양환경 교육 기획자": (["해양환경", "해양생물", "해양교육"], ["환경교육 기획", "해양생태 조사", "교육성과 평가"]),
    "해양생태 해설사": (["해양생물", "해양환경", "독도·해양문화"], ["해양생태 해설", "관람객 소통", "체험 프로그램 운영"]),
    "항해사": (["항해", "해양안전", "선박"], ["항해당직", "선박운항", "비상대응"]),
    "항만 물류 운영자": (["항만·물류", "해양안전", "선박"], ["항만운영", "화물관리", "안전관리"]),
    "자율운항선박 엔지니어": (["선박", "항해", "해양안전"], ["선박시스템", "자율운항 데이터", "위험분석"]),
    "해양문화 콘텐츠 기획자": (["독도·해양문화", "해양교육", "해양생물"], ["전시콘텐츠 기획", "문화자원 해설", "교육프로그램 운영"]),
}

ROUTE_LABELS = {
    "balanced": "균형 항로",
    "fastest": "가장 빠른 항로",
    "experience": "체험 중심 항로",
    "family": "가족과 함께하는 항로",
    "career": "취업 준비 항로",
    "weekend": "주말 전용 항로",
    "free": "무료 프로그램 우선 항로",
}

ACTION_LABELS = {
    "video": "영상 시작",
    "program": "교육 일정 보기",
    "event": "현장 미션 열기",
    "quiz": "진단 퀴즈 시작",
    "project": "프로젝트 설계",
    "career": "진로 상담 열기",
}


def _clamp(value: int | float, minimum: int = 0, maximum: int = 100) -> int:
    return max(minimum, min(maximum, int(round(value))))


def _clean_json(raw: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw.strip(), flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start >= 0 and end > start:
        cleaned = cleaned[start : end + 1]
    value = json.loads(cleaned)
    return value if isinstance(value, dict) else {}


def _as_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _iso_utc(value: datetime) -> str:
    return _as_utc(value).isoformat().replace("+00:00", "Z")


def _qr_signature(exhibit_code: str, session_id: str, issued_at: datetime, expires_at: datetime, nonce: str) -> str:
    canonical = "|".join([exhibit_code, session_id, _iso_utc(issued_at), _iso_utc(expires_at), nonce])
    return hmac.new(
        settings.qr_signing_secret.encode("utf-8"),
        canonical.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def issue_mission_qr(db: Session, issuer: User, request: MissionQrIssueRequest) -> MissionQrIssueResponse:
    issued_at = datetime.now(timezone.utc)
    expires_at = issued_at + timedelta(minutes=request.validMinutes or settings.qr_token_minutes)
    session_id = request.sessionId or f"museum-{issued_at:%Y%m%d}-{secrets.token_hex(6)}"
    nonce = secrets.token_urlsafe(24)
    signature = _qr_signature(request.exhibitCode, session_id, issued_at, expires_at, nonce)
    record = MissionQrNonce(
        nonce=nonce,
        exhibit_code=request.exhibitCode,
        exhibit_title=request.exhibitTitle,
        session_id=session_id,
        issued_at=issued_at,
        expires_at=expires_at,
        signature=signature,
        issued_by=issuer.id,
    )
    db.add(record)
    db.commit()
    payload = MissionQrPayload(
        exhibitCode=request.exhibitCode,
        exhibitTitle=request.exhibitTitle,
        sessionId=session_id,
        issuedAt=issued_at,
        expiresAt=expires_at,
        nonce=nonce,
        signature=signature,
    )
    return MissionQrIssueResponse(**payload.model_dump(), qrJson=json.dumps(payload.model_dump(mode="json"), ensure_ascii=False, separators=(",", ":")))


def _validated_qr_nonce(db: Session, payload: MissionQrPayload, *, allow_used: bool = False) -> MissionQrNonce:
    record = db.scalar(select(MissionQrNonce).where(MissionQrNonce.nonce == payload.nonce))
    if record is None:
        raise ValueError("QR nonce not found")
    expected = _qr_signature(payload.exhibitCode, payload.sessionId, payload.issuedAt, payload.expiresAt, payload.nonce)
    if not hmac.compare_digest(expected, payload.signature) or not hmac.compare_digest(record.signature, payload.signature):
        raise ValueError("QR signature is invalid")
    if record.exhibit_code != payload.exhibitCode or record.session_id != payload.sessionId:
        raise ValueError("QR payload does not match the issued token")
    if _iso_utc(record.issued_at) != _iso_utc(payload.issuedAt) or _iso_utc(record.expires_at) != _iso_utc(payload.expiresAt):
        raise ValueError("QR timestamp does not match the issued token")
    now = datetime.now(timezone.utc)
    if _as_utc(record.issued_at) > now + timedelta(minutes=1):
        raise ValueError("QR token is not active yet")
    if _as_utc(record.expires_at) <= now:
        raise ValueError("QR token has expired")
    if record.used_at is not None and not allow_used:
        raise ValueError("QR nonce has already been used")
    return record


def _profile_snapshot(db: Session, user: User, supplied: dict[str, Any]) -> dict[str, Any]:
    stored = db.get(UserProfile, user.id)
    snapshot = dict(stored.snapshot or {}) if stored else {}
    snapshot.update({key: value for key, value in (supplied or {}).items() if value is not None})
    return snapshot


def _target_topics(target_career: str, interest: str) -> tuple[list[str], list[str]]:
    for name, values in CAREER_MAP.items():
        if name in target_career or target_career in name:
            return values
    inferred = []
    for topic in ["해양환경", "해양생물", "항해", "선박", "해양안전", "항만·물류", "독도·해양문화", "해양교육"]:
        if topic in target_career:
            inferred.append(topic)
    if interest and interest not in inferred:
        inferred.append(interest)
    if not inferred:
        inferred = [interest or "해양교육", "해양안전"]
    return inferred[:3], [f"{topic} 기초역량" for topic in inferred[:3]]


def _mastery(snapshot: dict[str, Any], topic: str) -> int:
    values = snapshot.get("skillMastery", {})
    if isinstance(values, dict):
        direct = values.get(topic)
        if isinstance(direct, (int, float)):
            return _clamp(direct)
        for key, value in values.items():
            if isinstance(value, (int, float)) and (str(key) in topic or topic in str(key)):
                return _clamp(value)
    level = str(snapshot.get("level", "입문"))
    return {"입문": 42, "기초": 50, "중급": 62, "심화": 74, "실무": 80}.get(level, 50)


def _tier_bonus(tier: str) -> int:
    return {"브론즈": 0, "실버": 3, "골드": 6, "플래티넘": 9, "다이아": 12}.get(tier, 0)


def _readiness(snapshot: dict[str, Any], topics: list[str]) -> int:
    scores = [_mastery(snapshot, topic) for topic in topics]
    completion = len(snapshot.get("completedContentIds", []) or [])
    tier = str(snapshot.get("tier", "브론즈"))
    return _clamp(sum(scores) / max(1, len(scores)) * 0.72 + min(12, completion * 1.5) + _tier_bonus(tier))


def _content_score(item: Content, topics: list[str], route_type: str, snapshot: dict[str, Any]) -> float:
    metadata = item.metadata_json or {}
    score = 0.0
    if item.topic in topics:
        score += 36 - topics.index(item.topic) * 5
    if any(topic in (item.title + " " + item.career_tag + " " + str(metadata.get("description", ""))) for topic in topics):
        score += 14
    interest = str(snapshot.get("interest", ""))
    if interest and (interest in item.topic or interest in item.title):
        score += 12
    completed = set(str(value) for value in (snapshot.get("completedContentIds", []) or []))
    if item.id in completed:
        score -= 80
    minutes = max(1, item.minutes or (12 if item.content_type == "video" else 60))
    if route_type == "fastest":
        score += max(0, 25 - minutes / 3)
    if route_type == "experience" and item.content_type in {"program", "event"}:
        score += 30
    if route_type == "family":
        target = str(metadata.get("target", ""))
        description = str(metadata.get("description", ""))
        if any(word in target + description for word in ["가족", "초등", "어린이", "전 연령"]):
            score += 32
        if item.content_type == "event":
            score += 10
    if route_type == "career" and (item.career_tag or item.content_type == "program"):
        score += 24
    if route_type == "weekend" and item.content_type in {"program", "event"}:
        score += 15
    if route_type == "free" and "무료" in (item.title + str(metadata.get("description", ""))):
        score += 30
    if item.content_type == "video":
        score += 8
    return score


def _availability(item: Content) -> tuple[str, str]:
    metadata = item.metadata_json or {}
    start = str(metadata.get("startAt", "")).strip()
    end = str(metadata.get("endAt", "")).strip()
    if item.content_type == "video":
        return "available", "지금 시작 가능"
    today = datetime.now(timezone.utc).date().isoformat()
    if end and end[:10] < today:
        return "closed", "종료 · 대체 항로 제공"
    if start and start[:10] > today:
        return "upcoming", f"{start[:10]} 시작 예정"
    return "available", "신청 가능 여부 확인"


def _node_from_content(item: Content, order: int, topic: str, node_type: str | None = None) -> dict[str, Any]:
    metadata = item.metadata_json or {}
    status, label = _availability(item)
    actual_type = node_type or item.content_type
    minutes = item.minutes or (12 if actual_type == "video" else 60)
    gain = 4 if actual_type == "video" else 8
    if actual_type == "event":
        gain = 9
    return {
        "order": order,
        "nodeType": actual_type,
        "targetId": item.id,
        "title": item.title,
        "description": str(metadata.get("description", "")) or f"{item.topic} 역량을 실제 학습 증거로 연결합니다.",
        "source": item.source,
        "topic": item.topic or topic,
        "minutes": minutes,
        "expectedSkillGain": gain,
        "readinessGain": max(3, gain - 1),
        "scheduleStatus": status,
        "availabilityLabel": label,
        "ncsCompetencies": [],
        "whyThisOrder": "현재 수준에서 바로 수행할 수 있는 활동을 먼저 배치했습니다.",
        "recommendationReasons": [f"관심 분야 {topic} 연계", f"현재 숙련도에 맞는 {item.difficulty or '맞춤'} 난이도"],
        "evidenceBasis": [f"교육 콘텐츠 데이터 · {item.source or 'BluePath'}", "학습자 프로필과 분야별 숙련도"],
        "actionLabel": ACTION_LABELS.get(actual_type, "열기"),
        "actionUrl": item.url or "",
        "completed": False,
        "metadata": {"target": metadata.get("target", "전체"), "method": metadata.get("method", "")},
    }


def _synthetic_node(order: int, node_type: str, title: str, topic: str, competencies: list[str], gain: int) -> dict[str, Any]:
    descriptions = {
        "quiz": "앞선 학습의 이해도를 진단하고 취약 문항을 다음 항로 계산에 반영합니다.",
        "project": "배운 내용을 실제 문제 해결 결과물로 바꾸어 검증 가능한 포트폴리오를 만듭니다.",
        "career": "NCS 직무 역량과 현재 증거의 차이를 확인하고 다음 교육·현장 경험을 연결합니다.",
    }
    return {
        "order": order,
        "nodeType": node_type,
        "targetId": f"generated-{node_type}-{order}",
        "title": title,
        "description": descriptions[node_type],
        "source": "BluePath AI navigator",
        "topic": topic,
        "minutes": {"quiz": 10, "project": 90, "career": 15}[node_type],
        "expectedSkillGain": gain,
        "readinessGain": max(3, gain - 1),
        "scheduleStatus": "available",
        "availabilityLabel": "지금 시작 가능",
        "ncsCompetencies": competencies,
        "whyThisOrder": "학습 후 진단과 적용을 이어 붙여 단순 관심에서 멈추지 않도록 배치했습니다.",
        "recommendationReasons": ["선행 활동의 결과를 검증", "NCS 역량 증거로 전환"],
        "evidenceBasis": ["퀴즈 학습 이력", "NCS 직무 역량 매핑"],
        "actionLabel": ACTION_LABELS[node_type],
        "actionUrl": "",
        "completed": False,
        "metadata": {},
    }


def _llm_route_enhancement(
    target_career: str,
    route_type: str,
    snapshot: dict[str, Any],
    nodes: list[dict[str, Any]],
    sources: list[Any],
) -> tuple[str, str, list[dict[str, Any]], str]:
    fallback_summary = f"{ROUTE_LABELS.get(route_type, '맞춤 항로')}로 {target_career}까지 학습·체험·진단·프로젝트를 연결했습니다."
    fallback_coach = "각 활동을 완료할 때마다 숙련도와 직무 준비도를 다시 계산해 다음 항로를 조정합니다."
    if not settings.llm_enabled:
        return fallback_summary, fallback_coach, nodes, "rules"
    evidence = "\n".join(f"- {item.title}: {item.content[:500]}" for item in sources[:4])
    prompt = f"""
당신은 해양교육 경로 설계자입니다. 아래의 결정론적 점수와 순서는 절대 변경하지 말고, 설명만 더 명확하고 설득력 있게 강화하세요.
대상 진로: {target_career}
항로 유형: {ROUTE_LABELS.get(route_type, route_type)}
학습자 프로필: {json.dumps(snapshot, ensure_ascii=False)}
항로 노드: {json.dumps([{k:v for k,v in node.items() if k != 'metadata'} for node in nodes], ensure_ascii=False)}
기관 근거:\n{evidence or '- 제공된 교육·NCS·관람객 데이터'}

반드시 한국어 JSON 하나만 반환하세요.
{{
  "summary": "한 문단",
  "coachMessage": "사용자의 다음 행동을 돕는 문장",
  "nodes": [
    {{"order": 1, "whyThisOrder": "설명", "recommendationReasons": ["근거1", "근거2"], "evidenceBasis": ["데이터 근거1"]}}
  ]
}}
과장된 취업 보장, 확정적 예측, 근거 없는 수치를 쓰지 마세요.
""".strip()
    try:
        payload = _clean_json(call_chat(prompt, temperature=0.25, max_tokens=1400))
        by_order = {int(item.get("order", 0)): item for item in payload.get("nodes", []) if isinstance(item, dict)}
        for node in nodes:
            extra = by_order.get(node["order"], {})
            why = str(extra.get("whyThisOrder", "")).strip()
            reasons = [str(value).strip() for value in extra.get("recommendationReasons", []) if str(value).strip()]
            basis = [str(value).strip() for value in extra.get("evidenceBasis", []) if str(value).strip()]
            if why:
                node["whyThisOrder"] = why
            if reasons:
                node["recommendationReasons"] = reasons[:4]
            if basis:
                node["evidenceBasis"] = basis[:4]
        return (
            str(payload.get("summary", fallback_summary)).strip() or fallback_summary,
            str(payload.get("coachMessage", fallback_coach)).strip() or fallback_coach,
            nodes,
            "llm_grounded",
        )
    except Exception:
        return fallback_summary, fallback_coach, nodes, "rules_fallback"


def create_route_plan(
    db: Session,
    user: User,
    request: RoutePlanRequest,
    excluded_target_ids: set[str] | None = None,
    reroute_reason: str = "",
    activate: bool = True,
) -> RoutePlanResponse:
    snapshot = _profile_snapshot(db, user, request.profile)
    target_career = request.targetCareer.strip()
    topics, competencies = _target_topics(target_career, str(snapshot.get("interest", "")))
    tier = str(snapshot.get("tier", "브론즈"))
    readiness_before = _readiness(snapshot, topics)
    excluded = excluded_target_ids or set()

    all_content = list(db.scalars(select(Content).where(Content.id.not_in(excluded)).limit(500)))
    ranked = sorted(all_content, key=lambda item: _content_score(item, topics, request.routeType, snapshot), reverse=True)
    if request.constraints.get("maxDifficulty") == "하":
        ranked.sort(key=lambda item: (item.difficulty != "하", -_content_score(item, topics, request.routeType, snapshot)))
    max_minutes_per_node = request.constraints.get("maxMinutesPerNode")
    if isinstance(max_minutes_per_node, (int, float)) and max_minutes_per_node > 0:
        shorter = [item for item in ranked if (item.minutes or (12 if item.content_type == "video" else 60)) <= int(max_minutes_per_node)]
        if shorter:
            ranked = shorter + [item for item in ranked if item not in shorter]
    videos = [item for item in ranked if item.content_type == "video"]
    experiences = [item for item in ranked if item.content_type in {"program", "event", "schedule"}]

    nodes: list[dict[str, Any]] = []
    if videos:
        nodes.append(_node_from_content(videos[0], 1, topics[0]))
    if experiences:
        chosen = experiences[0]
        if request.routeType == "family":
            family_items = [item for item in experiences if any(word in (str((item.metadata_json or {}).get("target", "")) + item.title) for word in ["가족", "초등", "어린이", "전체"])]
            if family_items:
                chosen = family_items[0]
        nodes.append(_node_from_content(chosen, len(nodes) + 1, topics[min(1, len(topics) - 1)]))
    if not nodes:
        nodes.append(_synthetic_node(1, "quiz", f"{topics[0]} 기초 진단", topics[0], competencies, 4))

    if len(nodes) < request.maxNodes:
        nodes.append(_synthetic_node(len(nodes) + 1, "quiz", f"{topics[0]} 맞춤 진단 퀴즈", topics[0], competencies, 5))
    if len(nodes) < request.maxNodes:
        project_title = "가족 심해 탐사 설계 미션" if request.routeType == "family" else f"{target_career} 직무 체험 프로젝트"
        project = _synthetic_node(len(nodes) + 1, "project", project_title, topics[-1], competencies, 9)
        if request.routeType == "fastest":
            project["title"] = f"{target_career} 30분 미니 프로젝트"
            project["minutes"] = 30
            project["expectedSkillGain"] = 6
            project["readinessGain"] = 5
            project["recommendationReasons"] = ["시간 부족을 반영한 최소 실행 단위", "완료 후 심화 프로젝트로 확장 가능"]
        nodes.append(project)
    if len(nodes) < request.maxNodes:
        nodes.append(_synthetic_node(len(nodes) + 1, "career", f"목표 항구 · {target_career}", topics[0], competencies, 6))

    nodes = nodes[: request.maxNodes]
    for index, node in enumerate(nodes, start=1):
        node["order"] = index
        if not node["ncsCompetencies"]:
            node["ncsCompetencies"] = competencies[:3]
        if reroute_reason:
            node["recommendationReasons"] = [f"재항해 사유 · {reroute_reason}"] + node["recommendationReasons"]

    sources = retrieve_sources(db, " ".join([target_career, *topics, *competencies]), limit=5)
    summary, coach_message, nodes, generated_by = _llm_route_enhancement(target_career, request.routeType, snapshot, nodes, sources)

    total_gain = sum(int(node["readinessGain"]) for node in nodes)
    readiness_after = _clamp(readiness_before + total_gain * 0.72)
    estimated_minutes = sum(max(0, int(node["minutes"])) for node in nodes)
    estimated_days = max(1, math.ceil(estimated_minutes / (45 if request.routeType == "fastest" else 30)))

    if activate:
        for old in db.scalars(select(RoutePlan).where(RoutePlan.user_id == user.id, RoutePlan.status == "active")):
            old.status = "rerouted" if reroute_reason else "superseded"

    plan = RoutePlan(
        user_id=user.id,
        target_career=target_career,
        route_type=request.routeType,
        summary=summary,
        coach_message=coach_message,
        readiness_before=readiness_before,
        readiness_after=readiness_after,
        estimated_minutes=estimated_minutes,
        estimated_days=estimated_days,
        generated_by=generated_by,
        status="active" if activate else "pending",
        context_json={"profile": snapshot, "constraints": request.constraints, "topics": topics, "rerouteReason": reroute_reason, "preparedAutomatically": not activate},
    )
    db.add(plan)
    db.flush()
    completed = set(str(value) for value in (snapshot.get("completedContentIds", []) or []))
    for node in nodes:
        db.add(
            RouteNode(
                plan_id=plan.id,
                order_index=node["order"],
                node_type=node["nodeType"],
                target_id=node["targetId"],
                title=node["title"],
                description=node["description"],
                source=node["source"],
                topic=node["topic"],
                minutes=node["minutes"],
                expected_skill_gain=node["expectedSkillGain"],
                readiness_gain=node["readinessGain"],
                schedule_status=node["scheduleStatus"],
                action_url=node["actionUrl"],
                why_this_order=node["whyThisOrder"],
                reasons_json=node["recommendationReasons"],
                evidence_json=node["evidenceBasis"],
                competencies_json=node["ncsCompetencies"],
                metadata_json={**node.get("metadata", {}), "availabilityLabel": node["availabilityLabel"], "actionLabel": node["actionLabel"], "completed": node["targetId"] in completed},
            )
        )
    db.add(RouteOutcomeEvent(user_id=user.id, route_plan_id=plan.id, event_type="viewed", metadata_json={"source": "route_created" if activate else "auto_reroute_preview"}))
    db.commit()
    db.refresh(plan)
    return route_plan_response(plan, sources)


def route_plan_response(plan: RoutePlan, sources: list[Any] | None = None) -> RoutePlanResponse:
    context = plan.context_json or {}
    snapshot = context.get("profile", {}) if isinstance(context.get("profile"), dict) else {}
    topics = context.get("topics", []) if isinstance(context.get("topics"), list) else []
    primary_topic = str(topics[0] if topics else snapshot.get("interest", "해양교육"))
    alternatives = [
        "40분 과정 대신 8~12분 영상과 미니 퀴즈로 축소",
        "마감된 현장 교육을 온라인 학습과 다음 달 체험으로 대체",
        "완료한 활동은 Ocean Skill Passport 증거로 자동 반영",
    ]
    node_items: list[RouteNodeItem] = []
    for node in sorted(plan.nodes, key=lambda value: value.order_index):
        metadata = node.metadata_json or {}
        node_items.append(
            RouteNodeItem(
                id=node.id,
                order=node.order_index,
                nodeType=node.node_type,
                targetId=node.target_id,
                title=node.title,
                description=node.description,
                source=node.source,
                topic=node.topic,
                minutes=node.minutes,
                expectedSkillGain=node.expected_skill_gain,
                readinessGain=node.readiness_gain,
                scheduleStatus=node.schedule_status,
                availabilityLabel=str(metadata.get("availabilityLabel", "지금 시작 가능")),
                ncsCompetencies=list(node.competencies_json or []),
                whyThisOrder=node.why_this_order,
                recommendationReasons=list(node.reasons_json or []),
                evidenceBasis=list(node.evidence_json or []),
                actionLabel=str(metadata.get("actionLabel", ACTION_LABELS.get(node.node_type, "열기"))),
                actionUrl=node.action_url,
                completed=bool(metadata.get("completed", False)),
            )
        )
    source_items = [SourceItem(title=item.title, url=item.url, organization=item.organization) for item in (sources or [])]
    return RoutePlanResponse(
        routeId=plan.id,
        targetCareer=plan.target_career,
        routeType=plan.route_type,
        summary=plan.summary,
        coachMessage=plan.coach_message,
        currentSkillTopic=primary_topic,
        currentMastery=_mastery(snapshot, primary_topic),
        tier=str(snapshot.get("tier", "브론즈")),
        readinessBefore=plan.readiness_before,
        readinessAfter=plan.readiness_after,
        estimatedMinutes=plan.estimated_minutes,
        estimatedDays=plan.estimated_days,
        generatedBy=plan.generated_by,
        nodes=node_items,
        alternatives=alternatives,
        sources=source_items,
    )


def simulate_route_node(db: Session, user: User, request: RouteSimulationRequest) -> RouteSimulationResponse:
    snapshot = _profile_snapshot(db, user, request.profile)
    plan = None
    node = None
    if request.routeId:
        plan = db.scalar(select(RoutePlan).where(RoutePlan.id == request.routeId, RoutePlan.user_id == user.id))
        if plan is None:
            raise ValueError("Route not found")
    if request.nodeId:
        node = db.scalar(
            select(RouteNode)
            .join(RoutePlan, RoutePlan.id == RouteNode.plan_id)
            .where(RouteNode.id == request.nodeId, RoutePlan.user_id == user.id)
        )
        if node is None:
            raise ValueError("Route node not found")
        if plan is not None and node.plan_id != plan.id:
            raise ValueError("Route node does not belong to the requested route")
        if plan is None:
            plan = db.scalar(select(RoutePlan).where(RoutePlan.id == node.plan_id, RoutePlan.user_id == user.id))

    title = node.title if node else request.activityTitle or "선택한 해양교육 활동"
    topic = node.topic if node else request.skillTopic
    skill_gain = node.expected_skill_gain if node else request.expectedSkillGain
    readiness_gain = node.readiness_gain if node else request.readinessGain
    mastery_before = _mastery(snapshot, topic)
    mastery_after = _clamp(mastery_before + skill_gain)
    readiness_before = plan.readiness_before if plan else _readiness(snapshot, [topic])
    readiness_after = _clamp(readiness_before + readiness_gain)
    weak_before = max(0, math.ceil((100 - mastery_before) / 12))
    weak_after = max(0, math.ceil((100 - mastery_after) / 12))
    next_node = None
    if plan and node:
        next_node = next((value for value in sorted(plan.nodes, key=lambda value: value.order_index) if value.order_index > node.order_index), None)
    next_recommendation = next_node.title if next_node else f"{topic} 실전 적용 프로젝트"
    evidence = ["현재 분야별 숙련도", "활동별 예상 역량 상승치", "목표 직무 준비도 가중치"]
    explanation = (
        f"‘{title}’은 {topic}의 현재 숙련도 {mastery_before}점에서 약 {skill_gain}점의 학습 효과를 반영합니다. "
        f"준비도는 {readiness_before}%에서 {readiness_after}%로 변할 수 있으며 실제 결과는 퀴즈와 활동 증거로 다시 보정됩니다."
    )
    confidence = 72 if node else 60
    if settings.llm_enabled:
        prompt = f"""
다음 수치는 결정론적으로 계산되었습니다. 숫자를 변경하지 말고, 과장 없이 학습자에게 이해하기 쉬운 한국어 설명과 다음 행동을 JSON으로 작성하세요.
활동: {title}
분야: {topic}
숙련도: {mastery_before} -> {mastery_after}
직무 준비도: {readiness_before} -> {readiness_after}
취약 항목 추정: {weak_before} -> {weak_after}
다음 추천: {next_recommendation}
JSON: {{"explanation":"...","nextRecommendation":"..."}}
""".strip()
        try:
            payload = _clean_json(call_chat(prompt, temperature=0.2, max_tokens=500))
            explanation = str(payload.get("explanation", explanation)).strip() or explanation
            next_recommendation = str(payload.get("nextRecommendation", next_recommendation)).strip() or next_recommendation
            confidence = 78
        except Exception:
            pass
    db.add(RouteOutcomeEvent(user_id=user.id, route_plan_id=plan.id if plan else None, node_id=node.id if node else None, event_type="viewed", metadata_json={"kind": "simulation"}))
    db.commit()
    return RouteSimulationResponse(
        activityTitle=title,
        skillTopic=topic,
        masteryBefore=mastery_before,
        masteryAfter=mastery_after,
        readinessBefore=readiness_before,
        readinessAfter=readiness_after,
        weakItemsBefore=weak_before,
        weakItemsAfter=weak_after,
        nextRecommendation=next_recommendation,
        explanation=explanation,
        confidence=confidence,
        evidenceBasis=evidence,
    )

def _family_mission_response(evidence: MissionEvidence) -> FamilyMissionResponse:
    mission = evidence.mission_json or {}
    roles = [MissionRole(**item) for item in mission.get("roles", []) if isinstance(item, dict)]
    return FamilyMissionResponse(
        missionId=evidence.id,
        exhibitCode=evidence.exhibit_code,
        title=evidence.title,
        story=str(mission.get("story", "")),
        roles=roles,
        jointTask=str(mission.get("jointTask", "")),
        expectedSkillGains=dict(evidence.skill_gains_json or {}),
        badge=evidence.badge,
        safetyNote=str(mission.get("safetyNote", "")),
        followUpRecommendation=str(mission.get("followUpRecommendation", "")),
        generatedBy=str(mission.get("generatedBy", "rules")),
    )


def generate_family_mission(db: Session, user: User, request: MissionGenerateRequest) -> FamilyMissionResponse:
    qr_record = _validated_qr_nonce(db, request.qrPayload)
    if request.exhibitCode != request.qrPayload.exhibitCode or request.exhibitCode != qr_record.exhibit_code:
        raise ValueError("Scanned QR exhibit does not match the mission request")
    if qr_record.mission_id:
        existing = db.get(MissionEvidence, qr_record.mission_id)
        if existing is None or existing.user_id != user.id:
            raise ValueError("QR nonce is already bound to another mission")
        return _family_mission_response(existing)

    snapshot = _profile_snapshot(db, user, request.profile)
    interest = str(snapshot.get("interest", "해양생물"))
    age = str(snapshot.get("ageGroup", "전 연령"))
    exhibit_title = request.exhibitTitle.strip() or qr_record.exhibit_title or request.exhibitCode
    title = f"{exhibit_title} 가족 협동 미션"
    story = "가족 탐사대가 전시 속 단서를 찾아 심해 탐사선의 안전한 귀환 계획을 완성합니다."
    roles = [
        MissionRole(name="탐험가", audience="어린이·입문자", task="전시에서 압력이나 부력과 관련된 단서 한 가지를 찾아 설명합니다."),
        MissionRole(name="항해사", audience="보호자·동행자", task="안전 장치 두 가지를 찾아 왜 필요한지 이야기합니다."),
    ]
    if request.participantCount >= 3:
        roles.append(MissionRole(name="기록관", audience="가족 구성원", task="발견한 단서를 사진 대신 짧은 문장이나 그림으로 기록합니다."))
    joint_task = "발견한 단서를 바탕으로 우리 가족만의 심해 잠수정과 비상대응 절차를 설계하세요."
    gains = {"선박": 5, "해양안전": 4, interest: 3}
    badge = "가족 심해 탐사대"
    safety = "이동 중에는 뛰지 말고 전시물과 관람 동선을 지켜 주세요. 촬영은 현장 안내에 따릅니다."
    follow_up = f"귀가 후 8분짜리 {interest} 입문 영상과 미니 퀴즈를 이어서 학습하세요."
    generated_by = "rules"
    if settings.llm_enabled:
        prompt = f"""
국립해양박물관 현장에서 수행할 가족 협동 미션을 설계하세요.
전시: {exhibit_title} ({request.exhibitCode})
참여자 수: {request.participantCount}
연령: {age}
관심: {interest}
현재 수준: {snapshot.get('level', '입문')}
조건: 전시물을 만지거나 위험 행동을 유도하지 말고, 보호자와 어린이가 서로 다른 역할을 맡으며 15분 이내에 끝나야 합니다.
한국어 JSON 하나만 반환:
{{"title":"...","story":"...","roles":[{{"name":"...","audience":"...","task":"..."}}],"jointTask":"...","expectedSkillGains":{{"선박":5,"해양안전":4}},"badge":"...","safetyNote":"...","followUpRecommendation":"..."}}
""".strip()
        try:
            payload = _clean_json(call_chat(prompt, temperature=0.45, max_tokens=1100))
            parsed_roles = [MissionRole(**item) for item in payload.get("roles", []) if isinstance(item, dict)]
            if parsed_roles:
                roles = parsed_roles[: max(2, request.participantCount)]
            parsed_gains = {str(k): _clamp(v, 1, 12) for k, v in payload.get("expectedSkillGains", {}).items() if isinstance(v, (int, float))}
            if parsed_gains:
                gains = parsed_gains
            title = str(payload.get("title", title)).strip() or title
            story = str(payload.get("story", story)).strip() or story
            joint_task = str(payload.get("jointTask", joint_task)).strip() or joint_task
            badge = str(payload.get("badge", badge)).strip() or badge
            safety = str(payload.get("safetyNote", safety)).strip() or safety
            follow_up = str(payload.get("followUpRecommendation", follow_up)).strip() or follow_up
            generated_by = "llm_guardrailed"
        except Exception:
            generated_by = "rules_fallback"

    evidence = MissionEvidence(
        user_id=user.id,
        mission_key=f"qr-{request.qrPayload.nonce}",
        exhibit_code=request.exhibitCode,
        title=title,
        badge=badge,
        participants=request.participantCount,
        status="generated",
        skill_gains_json=gains,
        mission_json={
            "story": story,
            "roles": [role.model_dump() for role in roles],
            "jointTask": joint_task,
            "safetyNote": safety,
            "followUpRecommendation": follow_up,
            "generatedBy": generated_by,
            "qrNonce": request.qrPayload.nonce,
            "qrSessionId": request.qrPayload.sessionId,
        },
    )
    db.add(evidence)
    db.flush()
    qr_record.mission_id = evidence.id
    db.commit()
    db.refresh(evidence)
    return _family_mission_response(evidence)


def verify_family_mission(
    db: Session,
    user: User,
    mission_id: str,
    completion_note: str,
    participant_count: int,
    qr_payload: MissionQrPayload,
) -> MissionVerifyResponse:
    evidence = db.scalar(select(MissionEvidence).where(MissionEvidence.id == mission_id, MissionEvidence.user_id == user.id))
    if not evidence:
        raise ValueError("Mission not found")
    mission = evidence.mission_json or {}
    next_recommendation = str(mission.get("followUpRecommendation", "관련 입문 영상과 미니 퀴즈를 이어서 학습하세요."))
    if evidence.status == "verified" and evidence.verified_at is not None:
        return MissionVerifyResponse(
            verified=True,
            newlyVerified=False,
            message="이미 인증된 미션입니다. 기존 Skill Passport 결과를 반환합니다.",
            badge=evidence.badge,
            acquiredCompetencies=dict(evidence.skill_gains_json or {}),
            verifiedAt=evidence.verified_at,
            nextRecommendation=next_recommendation,
        )

    note = completion_note.strip()
    if len(note) < 10:
        raise ValueError("Completion note must contain at least 10 non-space characters")
    if mission.get("qrNonce") != qr_payload.nonce or mission.get("qrSessionId") != qr_payload.sessionId:
        raise ValueError("QR payload does not belong to this mission")
    qr_record = _validated_qr_nonce(db, qr_payload)
    if qr_record.mission_id != evidence.id or qr_record.exhibit_code != evidence.exhibit_code:
        raise ValueError("QR token is not bound to this mission")

    verified_at = datetime.now(timezone.utc)
    consumed = db.execute(
        update(MissionQrNonce)
        .where(
            MissionQrNonce.nonce == qr_payload.nonce,
            MissionQrNonce.mission_id == evidence.id,
            MissionQrNonce.used_at.is_(None),
        )
        .values(used_at=verified_at)
    )
    if consumed.rowcount != 1:
        db.rollback()
        fresh = db.scalar(select(MissionEvidence).where(MissionEvidence.id == mission_id, MissionEvidence.user_id == user.id))
        if fresh and fresh.status == "verified" and fresh.verified_at:
            return MissionVerifyResponse(
                verified=True,
                newlyVerified=False,
                message="이미 인증된 미션입니다. 기존 Skill Passport 결과를 반환합니다.",
                badge=fresh.badge,
                acquiredCompetencies=dict(fresh.skill_gains_json or {}),
                verifiedAt=fresh.verified_at,
                nextRecommendation=str((fresh.mission_json or {}).get("followUpRecommendation", next_recommendation)),
            )
        raise ValueError("QR nonce has already been used")

    evidence.status = "verified"
    evidence.completion_note = note
    evidence.participants = participant_count
    evidence.verified_at = verified_at
    db.add(RouteOutcomeEvent(
        user_id=user.id,
        event_type="completed",
        metadata_json={"kind": "family_mission", "missionId": evidence.id, "badge": evidence.badge, "qrNonce": qr_payload.nonce},
    ))
    db.commit()
    return MissionVerifyResponse(
        verified=True,
        newlyVerified=True,
        message="현장 협동 미션을 Ocean Skill Passport 증거로 기록했습니다.",
        badge=evidence.badge,
        acquiredCompetencies=dict(evidence.skill_gains_json or {}),
        verifiedAt=evidence.verified_at,
        nextRecommendation=next_recommendation,
    )

def record_route_outcome(db: Session, user: User, route_id: str | None, node_id: str | None, event_type: str, value: float, metadata: dict[str, Any]) -> None:
    if route_id and not db.scalar(select(RoutePlan.id).where(RoutePlan.id == route_id, RoutePlan.user_id == user.id)):
        raise ValueError("Route not found")
    if node_id and not db.scalar(select(RouteNode.id).join(RoutePlan).where(RouteNode.id == node_id, RoutePlan.user_id == user.id)):
        raise ValueError("Route node not found")
    db.add(RouteOutcomeEvent(user_id=user.id, route_plan_id=route_id, node_id=node_id, event_type=event_type, value=value, metadata_json=metadata))
    db.commit()


def upsert_program_participation(
    db: Session, user: User, request: ProgramParticipationRequest
) -> ProgramParticipationResponse:
    record = db.scalar(
        select(ProgramParticipation).where(
            ProgramParticipation.user_id == user.id,
            ProgramParticipation.program_id == request.programId,
        )
    )
    now = datetime.now(timezone.utc)
    if record is None:
        record = ProgramParticipation(
            user_id=user.id,
            program_id=request.programId,
            program_title=request.programTitle,
            status="enrolled",
            enrolled_at=now,
        )
        db.add(record)
    rank = {"enrolled": 1, "attended": 2, "completed": 3}
    if rank[request.status] >= rank.get(record.status, 1):
        record.status = request.status
    if request.programTitle:
        record.program_title = request.programTitle
    if request.status in {"attended", "completed"} and record.attended_at is None:
        record.attended_at = now
    if request.status == "completed" and record.completed_at is None:
        record.completed_at = now
    if request.preAssessment is not None:
        record.pre_assessment = request.preAssessment
    if request.postAssessment is not None:
        record.post_assessment = request.postAssessment
    record.metadata_json = {**(record.metadata_json or {}), **(request.metadata or {})}
    db.commit()
    db.refresh(record)
    return ProgramParticipationResponse(
        programId=record.program_id,
        status=record.status,
        enrolledAt=record.enrolled_at,
        attendedAt=record.attended_at,
        completedAt=record.completed_at,
        preAssessment=record.pre_assessment,
        postAssessment=record.post_assessment,
    )


def outcome_analytics(db: Session) -> dict[str, Any]:
    plans = list(db.scalars(select(RoutePlan)))
    nodes = list(db.scalars(select(RouteNode)))
    missions = list(db.scalars(select(MissionEvidence)))
    events = list(db.scalars(select(RouteOutcomeEvent)))
    participations = list(db.scalars(select(ProgramParticipation)))
    event_counts = Counter(event.event_type for event in events)
    node_lookup = {node.id: node for node in nodes}

    participation_groups: defaultdict[str, list[ProgramParticipation]] = defaultdict(list)
    for item in participations:
        participation_groups[item.program_id].append(item)
    followups_by_program: Counter[str] = Counter(
        str((event.metadata_json or {}).get("programId"))
        for event in events
        if event.event_type == "followup" and (event.metadata_json or {}).get("programId")
    )
    program_outcomes = []
    for program_id, rows in participation_groups.items():
        participants = len({row.user_id for row in rows})
        completed = sum(1 for row in rows if row.completed_at is not None or row.status == "completed")
        attended = sum(1 for row in rows if row.attended_at is not None or row.status in {"attended", "completed"})
        gains = [
            row.post_assessment - row.pre_assessment
            for row in rows
            if row.pre_assessment is not None and row.post_assessment is not None
        ]
        title = next((row.program_title for row in rows if row.program_title), program_id)
        program_outcomes.append({
            "programId": program_id,
            "title": title,
            "participants": participants,
            "attendanceRate": round(attended * 100 / max(1, participants)),
            "completionRate": round(completed * 100 / max(1, participants)),
            "nextLearningRate": round(followups_by_program[program_id] * 100 / max(1, participants)),
            "averageSkillGain": round(sum(gains) / len(gains), 1) if gains else None,
            "assessmentPairs": len(gains),
        })
    program_outcomes.sort(key=lambda item: (item["participants"], item["completionRate"]), reverse=True)

    completed_by_node: Counter[str] = Counter(event.node_id for event in events if event.event_type == "completed" and event.node_id)
    started_by_node: Counter[str] = Counter(event.node_id for event in events if event.event_type == "started" and event.node_id)
    prototype_signals = []
    for node in nodes:
        if node.node_type not in {"program", "event"}:
            continue
        prototype_signals.append({
            "title": node.title,
            "routeNodeCreated": 1,
            "startEvents": started_by_node[node.id],
            "completionEvents": completed_by_node[node.id],
            "label": "prototype_proxy_not_unique_participants",
        })

    verified_missions = [item for item in missions if item.status == "verified"]
    family_rate = round(len(verified_missions) * 100 / max(1, len(missions)))
    career_opens = event_counts.get("career_opened", 0)
    dropoffs = []
    skipped_nodes = Counter(event.node_id for event in events if event.event_type in {"skipped", "rerouted"} and event.node_id)
    for node_id, count in skipped_nodes.most_common(5):
        node = node_lookup.get(node_id)
        if node:
            dropoffs.append({"stage": node.title, "count": count, "reason": "일정·난이도 또는 신청 가능성으로 재항해"})
    if not dropoffs:
        dropoffs.append({"stage": "현장 체험 후 후속 학습", "count": 0, "reason": "데이터가 쌓이면 실제 이탈 지점이 표시됩니다."})

    suggestions = []
    if family_rate < 70:
        suggestions.append("현장 미션 완료 직후 QR 인증 절차와 보호자 역할 안내를 점검하세요.")
    suggestions.append("체험 종료 24시간 후 후속 콘텐츠를 제안하고 programId 기반 전환 이벤트를 기록하세요.")
    if not program_outcomes:
        suggestions.append("운영 성과를 보려면 enrollment, attendance, pre/post assessment 데이터를 먼저 수집하세요.")

    return {
        "metricScope": "operational_enrollment_attendance_assessment",
        "dataQuality": "participants는 고유 등록 사용자, attendance는 실제 참석 시각, averageSkillGain은 사전·사후 평가 쌍만 집계합니다.",
        "routePlanCount": len(plans),
        "activeRouteCount": sum(1 for plan in plans if plan.status == "active"),
        "routeCompletionEvents": event_counts.get("completed", 0),
        "rerouteCount": event_counts.get("rerouted", 0),
        "familyMissionGenerated": len(missions),
        "familyMissionVerified": len(verified_missions),
        "familyMissionCompletionRate": family_rate,
        "careerExplorationCount": career_opens,
        "programOutcomes": program_outcomes[:12],
        "prototypeProgramSignals": prototype_signals[:50],
        "dropOffs": dropoffs,
        "aiSuggestions": suggestions,
        "eventDistribution": dict(event_counts),
    }

def generate_program_draft(db: Session, request: ProgramDraftRequest) -> ProgramDraftResponse:
    title = f"{request.topic} 스마트 항로 체험"
    rationale = f"{request.targetAudience}의 관심을 현장 체험, 짧은 진단, 후속 학습으로 연결하는 {request.durationMinutes}분 과정입니다."
    objectives = [f"{request.topic}의 핵심 개념을 설명한다", "현장 단서를 협동하여 탐색한다", "활동 결과를 다음 학습과 진로 탐색으로 연결한다"]
    agenda30 = ["5분 사전 진단", "15분 전시 협동 미션", "5분 결과 공유", "5분 후속 항로 선택"]
    agenda60 = ["10분 사전 진단과 역할 배정", "25분 현장 협동 미션", "15분 결과물 제작", "10분 진로·후속 학습 연결"]
    agenda90 = ["10분 진단", "35분 심화 미션", "25분 팀 프로젝트", "10분 피드백", "10분 개인 항로 설계"]
    ncs = [f"{request.topic} 기초역량", "문제해결", "의사소통", "안전관리"]
    pre = [f"{request.topic}에 대해 알고 있는 것을 한 문장으로 써 보세요.", "오늘 가장 궁금한 점은 무엇인가요?"]
    post = ["활동 전과 비교해 새롭게 알게 된 점은 무엇인가요?", "다음에 더 배우고 싶은 활동을 선택하세요."]
    follow = ["8분 입문 영상", "5문항 미니 퀴즈", "주말 현장 프로그램 또는 진로 프로젝트 추천"]
    measurement = ["완료율", "사전·사후 진단 변화", "24시간 내 다음 학습 전환율", "NCS 역량 증거 획득률"]
    generated_by = "rules"
    if settings.llm_enabled:
        evidence = retrieve_sources(db, f"{request.topic} {request.targetAudience} 해양교육 NCS", limit=4)
        context = "\n".join(f"- {item.title}: {item.content[:450]}" for item in evidence)
        prompt = f"""
해양교육 기관용 프로그램 초안을 설계하세요.
주제: {request.topic}
대상: {request.targetAudience}
기본 시간: {request.durationMinutes}분
기관 맥락: {request.institutionContext}
목표: {request.objective}
근거:\n{context or '- 제공 교육 운영·과정·NCS 데이터'}
한국어 JSON 하나만 반환:
{{"title":"...","rationale":"...","learningObjectives":["..."],"agenda30":["..."],"agenda60":["..."],"agenda90":["..."],"ncsCompetencies":["..."],"preQuestions":["..."],"postQuestions":["..."],"followUpLearning":["..."],"measurementPlan":["..."]}}
각 일정은 실행 가능한 활동으로 작성하고, 성과 측정 항목을 반드시 포함하세요.
""".strip()
        try:
            payload = _clean_json(call_chat(prompt, temperature=0.35, max_tokens=1600))
            title = str(payload.get("title", title)).strip() or title
            rationale = str(payload.get("rationale", rationale)).strip() or rationale
            def values(key: str, fallback: list[str]) -> list[str]:
                result = [str(value).strip() for value in payload.get(key, []) if str(value).strip()]
                return result or fallback
            objectives = values("learningObjectives", objectives)
            agenda30 = values("agenda30", agenda30)
            agenda60 = values("agenda60", agenda60)
            agenda90 = values("agenda90", agenda90)
            ncs = values("ncsCompetencies", ncs)
            pre = values("preQuestions", pre)
            post = values("postQuestions", post)
            follow = values("followUpLearning", follow)
            measurement = values("measurementPlan", measurement)
            generated_by = "llm_grounded"
        except Exception:
            generated_by = "rules_fallback"
    return ProgramDraftResponse(
        title=title,
        rationale=rationale,
        targetAudience=request.targetAudience,
        learningObjectives=objectives,
        agenda30=agenda30,
        agenda60=agenda60,
        agenda90=agenda90,
        ncsCompetencies=ncs,
        preQuestions=pre,
        postQuestions=post,
        followUpLearning=follow,
        measurementPlan=measurement,
        generatedBy=generated_by,
    )
