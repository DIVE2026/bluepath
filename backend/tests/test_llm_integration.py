from __future__ import annotations

import os
import sys
from pathlib import Path

import pytest

if os.getenv("RUN_LLM_INTEGRATION") != "1":
    pytest.skip("Set RUN_LLM_INTEGRATION=1 with real LLM credentials", allow_module_level=True)

required = ["LLM_BASE_URL", "LLM_MODEL"]
missing = [name for name in required if not os.getenv(name, "").strip()]
if missing:
    pytest.fail("Missing integration environment values: " + ", ".join(missing), pytrace=False)

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
os.environ.setdefault("DATABASE_URL", "sqlite:////tmp/bluepath_llm_integration.db")
os.environ.setdefault("JWT_SECRET", "integration-jwt-secret-that-is-more-than-thirty-two-bytes")
os.environ.setdefault("QR_SIGNING_SECRET", "integration-qr-secret-that-is-more-than-thirty-two-bytes")
os.environ.setdefault("ADMIN_EMAIL", "admin@bluepath.example.com")
os.environ.setdefault("ADMIN_PASSWORD", "AdminPassword123!")
os.environ.setdefault("YOUTUBE_SYNC_HOURS", "0")

from fastapi.testclient import TestClient

from backend.app.main import app


def header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def test_real_llm_route_and_guardrailed_mission() -> None:
    with TestClient(app) as client:
        admin = client.post("/api/v1/auth/login", json={
            "email": os.environ["ADMIN_EMAIL"],
            "password": os.environ["ADMIN_PASSWORD"],
            "guardianEmail": None,
        })
        assert admin.status_code == 200, admin.text
        admin_token = admin.json()["accessToken"]

        register = client.post("/api/v1/auth/register", json={
            "email": "llm-integration@bluepath.example.com",
            "password": "LearnerPassword123!",
            "guardianEmail": None,
            "nickname": "LlmIntegration",
        })
        if register.status_code == 409:
            login = client.post("/api/v1/auth/login", json={
                "email": "llm-integration@bluepath.example.com",
                "password": "LearnerPassword123!",
                "guardianEmail": None,
            })
            assert login.status_code == 200, login.text
            token = login.json()["accessToken"]
        else:
            assert register.status_code == 200, register.text
            token = register.json()["accessToken"]

        route = client.post("/api/v1/routes/plan", headers=header(token), json={
            "targetCareer": "해양환경 교육 기획자",
            "routeType": "balanced",
            "profile": {"interest": "해양환경", "level": "입문", "skillMastery": {"해양환경": 45}},
            "constraints": {},
            "maxNodes": 4,
        })
        assert route.status_code == 200, route.text
        assert route.json()["generatedBy"] == "llm_grounded", route.json()

        qr = client.post("/api/v1/admin/missions/qr-token", headers=header(admin_token), json={
            "exhibitCode": "llm-submersible",
            "exhibitTitle": "LLM 잠수정 전시",
            "validMinutes": 10,
        })
        assert qr.status_code == 200, qr.text
        qr_payload = qr.json()
        qr_payload.pop("qrJson", None)
        mission = client.post("/api/v1/missions/generate", headers=header(token), json={
            "exhibitCode": "llm-submersible",
            "exhibitTitle": "LLM 잠수정 전시",
            "participantCount": 2,
            "qrPayload": qr_payload,
            "profile": {"interest": "해양생물", "ageGroup": "가족"},
        })
        assert mission.status_code == 200, mission.text
        assert mission.json()["generatedBy"] == "llm_guardrailed", mission.json()
