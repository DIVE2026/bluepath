from __future__ import annotations

import csv
import hashlib
import io
import json
import math
import re
from pathlib import Path
from typing import Any

import httpx
from fastapi import HTTPException, UploadFile
from sqlalchemy import select
from sqlalchemy.orm import Session

from .config import get_settings
from .models import Content, KnowledgeChunk, QuizBankItem
from .schemas import AgentRequest, AgentResponse, QuizQuestion, QuizRequest, QuizResponse, SourceItem

settings = get_settings()
ROOT = Path(__file__).resolve().parents[2]
FALLBACK_QUIZZES = ROOT / "app/src/main/assets/fallback_quizzes.json"
KNOWLEDGE_SEED = ROOT / "backend/data/knowledge_seed.json"

TIER_RULES = {
    "브론즈": (10, 7),
    "실버": (12, 9),
    "골드": (15, 10),
    "플래티넘": (20, 16),
}

SYSTEM_PROMPT = (
    "You are BluePath Marine AI, a Korean-language ocean education specialist. "
    "Use retrieved evidence first, distinguish facts from recommendations, and never invent sources. "
    "For maritime safety, licenses, law, or schedules, advise checking the latest official authority."
)


def seed_knowledge(db: Session) -> None:
    """Seed traceable RAG chunks from every bundled institutional data source.

    The operation is idempotent so deployments can add newly bundled records without
    deleting administrator-managed knowledge.
    """
    records: list[dict[str, Any]] = []
    if KNOWLEDGE_SEED.exists():
        records.extend(json.loads(KNOWLEDGE_SEED.read_text(encoding="utf-8")))

    assets_dir = ROOT / "app/src/main/assets"
    programs_path = assets_dir / "seed_programs.json"
    if programs_path.exists():
        for item in json.loads(programs_path.read_text(encoding="utf-8")):
            records.append({
                "id": f"rag-program-{item['id']}",
                "title": item.get("title", "해양 교육 과정"),
                "organization": item.get("source", "제공 교육 데이터"),
                "url": item.get("url", ""),
                "topic": item.get("topic", "해양교육"),
                "content": (
                    f"교육명: {item.get('title', '')}. 대상: {item.get('target', '전체')}. "
                    f"운영기간: {item.get('startDate', '')}~{item.get('endDate', '')}. "
                    f"방식: {item.get('method', '')}. 분야: {item.get('topic', '해양교육')}. "
                    f"설명: {item.get('description', '')}. "
                    "일정은 제공 데이터 기준이므로 현재 모집 여부는 공식 기관에서 다시 확인해야 한다."
                ),
                "metadata": {"recordType": "program", "recordId": item["id"]},
            })

    events_path = assets_dir / "seed_events.json"
    if events_path.exists():
        for item in json.loads(events_path.read_text(encoding="utf-8")):
            records.append({
                "id": f"rag-event-{item['id']}",
                "title": item.get("title", "해양 행사"),
                "organization": item.get("source", "제공 행사 데이터"),
                "url": item.get("url", ""),
                "topic": item.get("category", "해양교육"),
                "content": (
                    f"행사명: {item.get('title', '')}. 유형: {item.get('category', '행사')}. "
                    f"대상: {item.get('target', '전체')}. 운영기간: {item.get('startDate', '')}~{item.get('endDate', '')}. "
                    f"설명: {item.get('description', '')}. 종료된 행사는 현재 신청 과정이 아니라 기획·수요 분석용 아카이브다."
                ),
                "metadata": {"recordType": "event", "recordId": item["id"]},
            })

    institutions_path = assets_dir / "seed_institutions.json"
    if institutions_path.exists():
        for item in json.loads(institutions_path.read_text(encoding="utf-8")):
            records.append({
                "id": f"rag-institution-{item['id']}",
                "title": item.get("name", "해양 기관"),
                "organization": item.get("source", "해양기관 현황 데이터"),
                "url": item.get("url", ""),
                "topic": "해양기관",
                "content": (
                    f"기관명: {item.get('name', '')}. 기관 분류: {item.get('category', '유관기관')}. "
                    "해양 진로의 근무지·교육·협력기관 후보로 연결할 수 있다. 실제 채용과 교육 제공 여부는 해당 기관의 최신 공고를 확인해야 한다."
                ),
                "metadata": {"recordType": "institution", "recordId": item["id"], "category": item.get("category", "")},
            })

    survey_path = assets_dir / "survey_insights.json"
    if survey_path.exists():
        survey = json.loads(survey_path.read_text(encoding="utf-8"))
        sample_size = survey.get("sampleSize", 0)
        for index, metric in enumerate(survey.get("metrics", []), start=1):
            records.append({
                "id": f"rag-survey-{index:02d}",
                "title": metric.get("label", "관람객 인사이트"),
                "organization": survey.get("source", "국립해양박물관 관람객 만족도 설문"),
                "url": "",
                "topic": "관람객 수요",
                "content": (
                    f"관람객 설문 표본 {sample_size}명 중 {metric.get('value', 0)}명이 해당했다. "
                    f"지표: {metric.get('label', '')}. 해석: {metric.get('insight', '')}"
                ),
                "metadata": {"recordType": "survey", "sampleSize": sample_size},
            })

    ncs_records = [
        ("navigator", "항해사", "항해", "선위결정, 항해당직, 선박조종, 항해장비 운용, 비상대응", "항해 영상, 안전 교육, 시뮬레이션, 해기사 자격 과정과 현장 경험을 단계적으로 연결한다."),
        ("environment-educator", "해양환경 교육 기획자", "해양환경", "해양환경 이해, 교육 콘텐츠 기획, 관람객 소통, 프로그램 평가", "환경·생태 학습 후 체험 프로그램 설계와 교육 성과 평가 프로젝트로 확장한다."),
        ("ecology-interpreter", "해양생태 해설사", "해양생물", "해양생물 분류, 해양생태계 해설, 전시 해설, 체험 안전", "생물 관찰과 생태계 이해를 전시 해설 및 현장 체험 운영 증거로 연결한다."),
        ("safety-manager", "선박안전관리자", "해양안전", "선박안전관리, 비상대응, 구명설비 운용, 소화·생존 이론", "안전 이론, 비상대응 퀴즈, 공인 교육과 자격 증빙을 함께 확인한다."),
        ("port-logistics", "항만물류 전문가", "항만·물류", "화물관리, 항만운영, 해운물류 이해, 데이터 기반 운영", "항만·물류 콘텐츠와 데이터 분석 프로젝트, 현장 교육 과정을 순서대로 연결한다."),
    ]
    for slug, title, topic, units, pathway in ncs_records:
        records.append({
            "id": f"rag-ncs-{slug}",
            "title": f"{title} NCS 역량 항로",
            "organization": "BluePath NCS mapping",
            "url": "",
            "topic": topic,
            "content": f"직무: {title}. 핵심 역량: {units}. 권장 항로: {pathway}",
            "metadata": {"recordType": "ncs-career", "career": title},
        })

    changed = False
    for item in records:
        if db.get(KnowledgeChunk, item["id"]):
            continue
        db.add(
            KnowledgeChunk(
                id=item["id"],
                title=item["title"],
                organization=item.get("organization", ""),
                url=item.get("url", ""),
                content=item["content"],
                topic=item.get("topic", "해양교육"),
                metadata_json=item.get("metadata", {}),
            )
        )
        changed = True
    if changed:
        db.commit()


def seed_contents(db: Session) -> None:
    assets = [
        ("seed_videos.json", "video"),
        ("seed_programs.json", "program"),
        ("seed_events.json", "event"),
    ]
    changed = False
    for file_name, content_type in assets:
        seed_path = ROOT / "app/src/main/assets" / file_name
        if not seed_path.exists():
            continue
        for item in json.loads(seed_path.read_text(encoding="utf-8")):
            if db.get(Content, item["id"]):
                continue
            metadata = {
                "startAt": item.get("startDate", ""),
                "endAt": item.get("endDate", ""),
                "target": item.get("target", "전체"),
                "method": item.get("method", ""),
                "category": item.get("category", ""),
                "description": item.get("description", ""),
                "keyword": item.get("keyword", ""),
            }
            db.add(
                Content(
                    id=item["id"],
                    title=item["title"],
                    content_type=content_type,
                    source=item.get("source", "BluePath seed data"),
                    url=item.get("url", ""),
                    difficulty=item.get("difficulty", ""),
                    required_tier=item.get("requiredTier", "브론즈"),
                    topic=item.get("topic", "해양교육"),
                    career_tag=item.get("careerTag", ""),
                    minutes=item.get("minutes", 0),
                    metadata_json=metadata,
                )
            )
            changed = True
    if changed:
        db.commit()


def seed_quizzes(db: Session) -> None:
    if db.scalar(select(QuizBankItem.id).limit(1)):
        return
    for item in json.loads(FALLBACK_QUIZZES.read_text(encoding="utf-8")):
        db.add(
            QuizBankItem(
                id=item["id"],
                tier=item["tier"],
                topic=item.get("topic", "해양교육"),
                question=item["question"],
                options=item["options"],
                answer_index=item["answerIndex"],
                explanation=item["explanation"],
                source_title="BluePath verified quiz bank",
            )
        )
    db.commit()


def retrieve_sources(db: Session, query: str, limit: int = 5) -> list[KnowledgeChunk]:
    chunks = list(db.scalars(select(KnowledgeChunk).limit(500)))
    if not chunks:
        return []
    query_embedding = create_embedding(query) if settings.embedding_model and settings.llm_base_url else None
    query_tokens = tokenize(query)

    def score(chunk: KnowledgeChunk) -> float:
        lexical = lexical_score(query_tokens, tokenize(f"{chunk.title} {chunk.topic} {chunk.content}"))
        if query_embedding and chunk.embedding:
            return lexical + 4.0 * cosine_similarity(query_embedding, list(chunk.embedding))
        return lexical

    return sorted(chunks, key=score, reverse=True)[:limit]


def generate_quiz(db: Session, request: QuizRequest) -> QuizResponse:
    if request.tier not in TIER_RULES:
        raise HTTPException(status_code=400, detail="Unsupported promotion tier")
    count, pass_count = TIER_RULES[request.tier]
    interest = str(request.profile.get("interest", "해양교육"))
    sources = retrieve_sources(db, f"{request.tier} {interest} 해양 학습 승급 퀴즈", 6)

    if settings.llm_enabled:
        evidence = "\n\n".join(
            f"[{index + 1}] {item.title} ({item.organization})\n{item.content}\nURL: {item.url}"
            for index, item in enumerate(sources)
        )
        prompt = (
            f"Tier: {request.tier}. Learner profile: {json.dumps(request.profile, ensure_ascii=False)}\n"
            f"Promotion rule: {count} questions, pass with {pass_count}.\n"
            f"Evidence:\n{evidence}\n\n"
            f"Create exactly {count} Korean four-option multiple-choice questions. Every item must have topic, "
            "question, exactly four options, answerIndex from 0 to 3, explanation, and sourceNumbers. "
            "Distribute correct positions, avoid duplicates, and use only evidence-supported facts. "
            "Return JSON only: {\"questions\":[...]}"
        )
        try:
            raw = call_chat(prompt, temperature=0.35, max_tokens=9000)
            questions = validate_quiz_payload(raw, request.tier, count, sources)
            return QuizResponse(source=f"BluePath RAG · {settings.llm_model}", questions=questions)
        except Exception:
            pass

    return QuizResponse(source="BluePath verified offline quiz bank", questions=fallback_quiz(db, request.tier, count))


def answer_agent(db: Session, request: AgentRequest) -> AgentResponse:
    query = f"{request.question} {request.profile.get('interest', '')} {request.tier}"
    sources = retrieve_sources(db, query, 5)
    source_items = [SourceItem(title=s.title, url=s.url, organization=s.organization) for s in sources]

    if settings.llm_enabled:
        evidence = "\n\n".join(
            f"[{i + 1}] {s.title} ({s.organization})\n{s.content}\nURL: {s.url}"
            for i, s in enumerate(sources)
        )
        prompt = (
            f"Learner profile: {json.dumps(request.profile, ensure_ascii=False)}\n"
            f"Current tier: {request.tier}\nPromotion manual:\n{request.promotionManual}\n"
            f"Retrieved evidence:\n{evidence}\n\nQuestion: {request.question}\n"
            "Answer in Korean in 4-8 clear sentences. Cite evidence with [1], [2] markers when used, "
            "include one actionable next step inside BluePath, and state uncertainty when evidence is insufficient."
        )
        try:
            return AgentResponse(answer=call_chat(prompt, 0.25, 1200).strip(), sources=source_items)
        except Exception:
            pass

    interest = request.profile.get("interest", "해양")
    answer = (
        f"현재 관심 분야는 ‘{interest}’, 티어는 {request.tier}입니다. "
        "추천 영상에서 관련 주제를 먼저 학습한 뒤 승급 퀴즈를 풀고, 진로 탭에서 NCS 역량을 연결해 보세요. "
        "온라인 근거 검색을 사용할 수 없는 경우에는 앱의 검증된 로컬 콘텐츠를 기준으로 안내합니다."
    )
    return AgentResponse(answer=answer, sources=source_items)


def create_embedding(text: str) -> list[float] | None:
    if not settings.embedding_model or not settings.llm_base_url:
        return None
    endpoint = normalize_openai_url(settings.llm_base_url, "embeddings")
    headers = {"Content-Type": "application/json"}
    if settings.llm_api_key:
        headers["Authorization"] = f"Bearer {settings.llm_api_key}"
    response = httpx.post(
        endpoint,
        headers=headers,
        json={"model": settings.embedding_model, "input": text},
        timeout=45,
    )
    response.raise_for_status()
    return response.json()["data"][0]["embedding"]


def embed_missing_chunks(db: Session, limit: int = 100) -> int:
    if not settings.embedding_model:
        return 0
    chunks = list(db.scalars(select(KnowledgeChunk).where(KnowledgeChunk.embedding.is_(None)).limit(limit)))
    updated = 0
    for chunk in chunks:
        vector = create_embedding(f"{chunk.title}\n{chunk.content}")
        if vector:
            chunk.embedding = vector
            updated += 1
    db.commit()
    return updated


def import_content_file(db: Session, upload: UploadFile) -> tuple[int, list[str]]:
    rows = read_tabular_rows(upload)

    aliases = {
        "id": ["id", "video_id", "content_id", "프로그램 id", "행사 id"],
        "content_type": ["contentType", "content_type", "type", "유형", "콘텐츠 유형"],
        "title": ["title", "제목", "영상 제목", "프로그램명", "행사명"],
        "url": ["url", "링크", "영상 링크", "신청 링크"],
        "difficulty": ["difficulty", "난도", "난이도"],
        "required_tier": ["requiredTier", "required_tier", "권장 티어"],
        "topic": ["topic", "주제", "키워드"],
        "source": ["source", "출처", "기관", "기관명"],
        "career_tag": ["careerTag", "career_tag", "진로 태그"],
        "minutes": ["minutes", "duration", "소요 시간", "분"],
        "start_at": ["startAt", "start_at", "startDate", "시작일", "시작 날짜"],
        "end_at": ["endAt", "end_at", "endDate", "종료일", "종료 날짜"],
        "target": ["target", "대상", "교육 대상"],
        "method": ["method", "방식", "참여 방법"],
        "category": ["category", "분류", "행사 유형"],
        "description": ["description", "설명", "내용", "교육 내용"],
    }
    errors: list[str] = []
    imported = 0
    for index, row in enumerate(rows, start=2):
        normalized = {str(k).strip(): v for k, v in row.items()}
        values = {key: first_value(normalized, options) for key, options in aliases.items()}
        content_type = values["content_type"].lower() or "video"
        if content_type not in {"video", "program", "event", "schedule"}:
            errors.append(f"row {index}: content type must be video, program, event, or schedule")
            continue
        if not values["title"]:
            errors.append(f"row {index}: title is required")
            continue
        if content_type == "video" and not values["url"]:
            errors.append(f"row {index}: video URL is required")
            continue
        stable_value = values["url"] or f"{content_type}:{values['title']}:{values['start_at']}"
        content_id = values["id"] or stable_id("upload", stable_value)
        item = db.get(Content, content_id) or Content(id=content_id, title=values["title"])
        item.title = values["title"]
        item.url = values["url"]
        item.source = values["source"] or "Admin upload"
        item.difficulty = normalize_difficulty(values["difficulty"]) if values["difficulty"] else ""
        item.required_tier = values["required_tier"] or {"하": "브론즈", "중": "실버", "상": "골드"}.get(item.difficulty, "브론즈")
        item.topic = values["topic"] or "해양교육"
        item.content_type = content_type
        item.career_tag = values["career_tag"]
        try:
            item.minutes = int(float(values["minutes"])) if values["minutes"] else 0
        except ValueError:
            errors.append(f"row {index}: minutes must be numeric; saved as 0")
            item.minutes = 0
        item.metadata_json = {
            "startAt": values["start_at"],
            "endAt": values["end_at"],
            "target": values["target"] or "전체",
            "method": values["method"],
            "category": values["category"],
            "description": values["description"],
            "uploadedFile": upload.filename or "",
            "row": index,
        }
        db.add(item)
        imported += 1
    db.commit()
    return imported, errors


def import_knowledge_file(db: Session, upload: UploadFile) -> tuple[int, list[str]]:
    rows = read_tabular_rows(upload)

    aliases = {
        "id": ["id", "knowledge_id", "자료 id"],
        "title": ["title", "제목", "자료명"],
        "content": ["content", "본문", "내용", "설명"],
        "organization": ["organization", "기관", "기관명", "출처"],
        "url": ["url", "링크", "출처 링크"],
        "topic": ["topic", "주제", "분야", "키워드"],
    }
    errors: list[str] = []
    imported = 0
    for index, row in enumerate(rows, start=2):
        normalized = {str(k).strip(): v for k, v in row.items()}
        values = {key: first_value(normalized, options) for key, options in aliases.items()}
        if not values["title"] or not values["content"]:
            errors.append(f"row {index}: title and content are required")
            continue
        knowledge_id = values["id"] or stable_id("knowledge", values["title"] + values["content"][:120])
        item = db.get(KnowledgeChunk, knowledge_id) or KnowledgeChunk(
            id=knowledge_id, title=values["title"], content=values["content"]
        )
        item.title = values["title"]
        item.content = values["content"]
        item.organization = values["organization"] or "Institutional data upload"
        item.url = values["url"]
        item.topic = values["topic"] or "해양교육"
        item.metadata_json = {"uploadedFile": upload.filename or "", "row": index}
        item.embedding = None
        db.add(item)
        imported += 1
    db.commit()
    return imported, errors


def import_quiz_file(db: Session, upload: UploadFile) -> tuple[int, list[str]]:
    rows = read_tabular_rows(upload)
    aliases = {
        "id": ["id", "quiz_id", "문제 id", "퀴즈 id"],
        "tier": ["tier", "티어", "등급"],
        "topic": ["topic", "주제", "분야"],
        "question": ["question", "문제", "문항", "질문"],
        "option_1": ["option1", "option_1", "option 1", "선택지1", "선택지 1", "보기1", "보기 1"],
        "option_2": ["option2", "option_2", "option 2", "선택지2", "선택지 2", "보기2", "보기 2"],
        "option_3": ["option3", "option_3", "option 3", "선택지3", "선택지 3", "보기3", "보기 3"],
        "option_4": ["option4", "option_4", "option 4", "선택지4", "선택지 4", "보기4", "보기 4"],
        "answer_index": ["answerIndex", "answer_index", "정답 인덱스"],
        "answer_number": ["answerNumber", "answer_number", "정답번호", "정답 번호"],
        "answer_text": ["answer", "correctAnswer", "correct_answer", "정답"],
        "explanation": ["explanation", "해설", "정답 해설", "설명"],
        "source_title": ["sourceTitle", "source_title", "출처명", "근거 자료"],
        "source_url": ["sourceUrl", "source_url", "출처 링크", "근거 링크"],
        "active": ["active", "활성", "사용 여부"],
    }
    errors: list[str] = []
    imported = 0
    for index, row in enumerate(rows, start=2):
        normalized = {str(k).strip(): v for k, v in row.items()}
        values = {key: first_value(normalized, options) for key, options in aliases.items()}
        tier = normalize_tier(values["tier"])
        options = [values[f"option_{number}"] for number in range(1, 5)]
        if tier not in TIER_RULES:
            errors.append(f"row {index}: tier must be Bronze, Silver, Gold, or Platinum")
            continue
        if not values["question"]:
            errors.append(f"row {index}: question is required")
            continue
        if any(not option for option in options):
            errors.append(f"row {index}: exactly four non-empty options are required")
            continue
        if len(set(options)) != 4:
            errors.append(f"row {index}: all four options must be unique")
            continue
        if not values["explanation"]:
            errors.append(f"row {index}: explanation is required")
            continue

        try:
            answer_index = resolve_answer_index(values, options)
        except ValueError as exc:
            errors.append(f"row {index}: {exc}")
            continue

        identity = f"{tier}:{values['question']}:{'|'.join(options)}"
        quiz_id = values["id"] or stable_id("quiz", identity)
        item = db.get(QuizBankItem, quiz_id) or QuizBankItem(
            id=quiz_id,
            tier=tier,
            question=values["question"],
            answer_index=answer_index,
        )
        item.tier = tier
        item.topic = values["topic"] or "해양교육"
        item.question = values["question"]
        item.options = options
        item.answer_index = answer_index
        item.explanation = values["explanation"]
        item.source_title = values["source_title"]
        item.source_url = values["source_url"]
        item.active = parse_active(values["active"])
        db.add(item)
        imported += 1
    db.commit()
    return imported, errors


def sync_youtube(db: Session, queries: list[str], max_results: int) -> int:
    if not settings.youtube_api_key:
        raise HTTPException(status_code=503, detail="YOUTUBE_API_KEY is not configured")
    if not queries:
        queries = ["해양 교육", "해양 환경 교육", "스마트 항만", "자율운항선박"]
    imported = 0
    for query in queries:
        response = httpx.get(
            "https://www.googleapis.com/youtube/v3/search",
            params={
                "part": "snippet",
                "q": query,
                "type": "video",
                "maxResults": max_results,
                "key": settings.youtube_api_key,
                "relevanceLanguage": "ko",
            },
            timeout=30,
        )
        response.raise_for_status()
        for item in response.json().get("items", []):
            video_id = item.get("id", {}).get("videoId")
            snippet = item.get("snippet", {})
            if not video_id:
                continue
            content_id = f"youtube-{video_id}"
            content = db.get(Content, content_id) or Content(id=content_id, title=snippet.get("title", ""))
            content.title = snippet.get("title", "")
            content.source = snippet.get("channelTitle", "YouTube")
            content.url = f"https://www.youtube.com/watch?v={video_id}"
            content.content_type = "video"
            content.topic = query
            content.difficulty = content.difficulty or "중"
            content.required_tier = content.required_tier or "실버"
            content.metadata_json = {"publishedAt": snippet.get("publishedAt", ""), "query": query}
            db.add(content)
            imported += 1
    db.commit()
    return imported


def call_chat(user_prompt: str, temperature: float, max_tokens: int) -> str:
    endpoint = normalize_openai_url(settings.llm_base_url, "chat/completions")
    headers = {"Content-Type": "application/json"}
    if settings.llm_api_key:
        headers["Authorization"] = f"Bearer {settings.llm_api_key}"
    response = httpx.post(
        endpoint,
        headers=headers,
        json={
            "model": settings.llm_model,
            "temperature": temperature,
            "max_tokens": max_tokens,
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
        },
        timeout=90,
    )
    response.raise_for_status()
    content = response.json()["choices"][0]["message"]["content"]
    if isinstance(content, list):
        return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return str(content)


def validate_quiz_payload(raw: str, tier: str, expected: int, sources: list[KnowledgeChunk]) -> list[QuizQuestion]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw.strip(), flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    payload = json.loads(cleaned[start : end + 1] if start >= 0 and end > start else cleaned)
    items = payload.get("questions", [])
    if len(items) != expected:
        raise ValueError(f"Expected {expected} questions")
    result: list[QuizQuestion] = []
    for index, item in enumerate(items):
        question = str(item.get("question", "")).strip()
        topic = str(item.get("topic", "해양교육")).strip() or "해양교육"
        options = [str(value).strip() for value in item.get("options", [])]
        answer_index = item.get("answerIndex")
        explanation = str(item.get("explanation", "")).strip()
        if (
            not question
            or len(options) != 4
            or any(not option for option in options)
            or len(set(options)) != 4
            or isinstance(answer_index, bool)
            or not isinstance(answer_index, int)
            or answer_index not in range(4)
            or not explanation
        ):
            raise ValueError("Each question needs text, four unique options, a valid answerIndex, and an explanation")
        source_numbers = [
            n for n in item.get("sourceNumbers", [])
            if isinstance(n, int) and not isinstance(n, bool) and 1 <= n <= len(sources)
        ]
        if sources and not source_numbers:
            raise ValueError("Every RAG-generated question must cite at least one retrieved source")
        selected_sources = [sources[n - 1] for n in source_numbers[:3]]
        source_models = [SourceItem(title=s.title, url=s.url, organization=s.organization) for s in selected_sources]
        if selected_sources:
            explanation += "\n근거: " + ", ".join(s.title for s in selected_sources)
        result.append(
            QuizQuestion(
                id=f"rag-{tier}-{index + 1}",
                tier=tier,
                topic=topic,
                question=question,
                options=options,
                answerIndex=answer_index,
                explanation=explanation,
                sources=source_models,
            )
        )
    return result


def fallback_quiz(db: Session, tier: str, count: int) -> list[QuizQuestion]:
    items = list(
        db.scalars(
            select(QuizBankItem)
            .where(QuizBankItem.tier == tier, QuizBankItem.active.is_(True))
            .order_by(QuizBankItem.id)
        )
    )
    if len(items) < count:
        raise HTTPException(status_code=500, detail="Fallback quiz bank is incomplete")
    result = []
    for index, item in enumerate(items[:count]):
        shift = index % 4
        rotated = [""] * 4
        for option_index, option in enumerate(item.options):
            rotated[(option_index + shift) % 4] = option
        sources = []
        if item.source_title:
            sources.append(SourceItem(title=item.source_title, url=item.source_url, organization="BluePath"))
        result.append(
            QuizQuestion(
                id=f"{item.id}-r{shift}",
                tier=tier,
                topic=item.topic,
                question=item.question,
                options=rotated,
                answerIndex=(item.answer_index + shift) % 4,
                explanation=item.explanation,
                sources=sources,
            )
        )
    return result


def normalize_openai_url(base: str, suffix: str) -> str:
    value = base.rstrip("/")
    if value.endswith(f"/{suffix}"):
        return value
    if value.endswith("/v1"):
        return f"{value}/{suffix}"
    return f"{value}/v1/{suffix}"


def tokenize(text: str) -> set[str]:
    return {token for token in re.findall(r"[0-9A-Za-z가-힣]{2,}", text.lower())}


def lexical_score(query: set[str], document: set[str]) -> float:
    if not query:
        return 0.0
    return len(query & document) / len(query)


def cosine_similarity(a: list[float], b: list[float]) -> float:
    if len(a) != len(b) or not a:
        return 0.0
    dot = sum(x * y for x, y in zip(a, b))
    norm_a = math.sqrt(sum(x * x for x in a))
    norm_b = math.sqrt(sum(y * y for y in b))
    return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0


def first_value(row: dict[str, Any], aliases: list[str]) -> str:
    for alias in aliases:
        if alias in row and str(row[alias]).strip():
            return str(row[alias]).strip()
    return ""


def read_tabular_rows(upload: UploadFile) -> list[dict[str, Any]]:
    raw = upload.file.read()
    filename = (upload.filename or "").lower()
    if filename.endswith(".csv"):
        return list(csv.DictReader(io.StringIO(raw.decode("utf-8-sig"))))
    if filename.endswith((".xlsx", ".xls")):
        import pandas as pd

        return pd.read_excel(io.BytesIO(raw)).fillna("").to_dict(orient="records")
    raise HTTPException(status_code=400, detail="Only CSV and Excel files are supported")


def stable_id(prefix: str, value: str) -> str:
    digest = hashlib.sha256(value.encode("utf-8")).hexdigest()[:20]
    return f"{prefix}-{digest}"


def normalize_tier(value: str) -> str:
    aliases = {
        "bronze": "브론즈",
        "브론즈": "브론즈",
        "silver": "실버",
        "실버": "실버",
        "gold": "골드",
        "골드": "골드",
        "platinum": "플래티넘",
        "플래티넘": "플래티넘",
    }
    return aliases.get(value.strip().lower(), value.strip())


def resolve_answer_index(values: dict[str, str], options: list[str]) -> int:
    if values["answer_index"]:
        try:
            answer_index = int(float(values["answer_index"]))
        except ValueError as exc:
            raise ValueError("answerIndex must be an integer from 0 to 3") from exc
        if answer_index not in range(4):
            raise ValueError("answerIndex must be an integer from 0 to 3")
        return answer_index
    if values["answer_number"]:
        try:
            answer_number = int(float(values["answer_number"]))
        except ValueError as exc:
            raise ValueError("answerNumber must be an integer from 1 to 4") from exc
        if answer_number not in range(1, 5):
            raise ValueError("answerNumber must be an integer from 1 to 4")
        return answer_number - 1
    if values["answer_text"]:
        try:
            return options.index(values["answer_text"])
        except ValueError as exc:
            raise ValueError("answer text must exactly match one of the four options") from exc
    raise ValueError("provide answerIndex (0-3), answerNumber (1-4), or answer text")


def parse_active(value: str) -> bool:
    return value.strip().lower() not in {"false", "0", "no", "n", "inactive", "비활성", "사용 안 함"}


def normalize_difficulty(value: str) -> str:
    lowered = value.strip().lower()
    if lowered in {"하", "beginner", "easy", "초급"}:
        return "하"
    if lowered in {"상", "advanced", "hard", "고급"}:
        return "상"
    return "중"
