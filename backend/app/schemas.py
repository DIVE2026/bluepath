from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, EmailStr, Field, HttpUrl


class CamelModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class AuthRequest(CamelModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    guardianEmail: EmailStr | None = None


class PasswordResetRequest(CamelModel):
    email: EmailStr


class PasswordResetConfirm(CamelModel):
    token: str = Field(min_length=32, max_length=512)
    newPassword: str = Field(min_length=8, max_length=128)


class AuthResponse(CamelModel):
    accessToken: str
    tokenType: str = "bearer"
    email: EmailStr
    displayName: str


class SourceItem(CamelModel):
    title: str
    url: str = ""
    organization: str = ""


class QuizQuestion(CamelModel):
    id: str
    tier: str
    topic: str
    question: str
    options: list[str]
    answerIndex: int
    explanation: str
    sources: list[SourceItem] = Field(default_factory=list)


class QuizRequest(CamelModel):
    tier: str
    profile: dict[str, Any] = Field(default_factory=dict)
    videos: list[dict[str, Any]] = Field(default_factory=list)


class QuizResponse(CamelModel):
    source: str
    questions: list[QuizQuestion]


class AgentRequest(CamelModel):
    question: str = Field(min_length=1, max_length=2000)
    tier: str
    profile: dict[str, Any] = Field(default_factory=dict)
    promotionManual: str = ""


class AgentResponse(CamelModel):
    answer: str
    sources: list[SourceItem] = Field(default_factory=list)


class LearningRecordInput(CamelModel):
    id: int | str
    recordType: str
    targetId: str
    title: str = ""
    status: str = ""
    updatedAt: int = 0
    synced: bool = False


class SyncRequest(CamelModel):
    snapshot: dict[str, Any]
    learningRecords: list[LearningRecordInput] = Field(default_factory=list)


class DiamondStatus(CamelModel):
    advancedQuizPassed: bool
    certificationStatus: str
    projectStatus: str
    eligible: bool
    message: str


class SyncResponse(CamelModel):
    message: str
    syncedAt: str
    diamondStatus: DiamondStatus
    snapshot: dict[str, Any] = Field(default_factory=dict)


class CloudStateResponse(CamelModel):
    snapshot: dict[str, Any] = Field(default_factory=dict)
    diamondStatus: DiamondStatus


class EvidenceRequest(CamelModel):
    evidenceType: str = Field(pattern="^(certification|project)$")
    title: str = Field(min_length=2, max_length=300)
    evidenceUrl: HttpUrl


class EvidenceReviewRequest(CamelModel):
    status: str = Field(pattern="^(approved|rejected|pending)$")
    reviewNote: str = ""


class GenericResponse(CamelModel):
    message: str


class ReminderRequest(CamelModel):
    title: str
    remindAt: datetime
    reminderType: str = "learning"


class ReminderResponse(CamelModel):
    id: str
    title: str
    remindAt: datetime
    reminderType: str
    enabled: bool


class AdminContentItem(CamelModel):
    id: str
    title: str
    contentType: str = "video"
    source: str = ""
    url: str = ""
    difficulty: str = ""
    requiredTier: str = "브론즈"
    topic: str = "해양교육"
    careerTag: str = ""
    minutes: int = 0
    startAt: str = ""
    endAt: str = ""
    target: str = "전체"
    method: str = ""
    category: str = ""
    description: str = ""


class YouTubeSyncRequest(CamelModel):
    queries: list[str] = Field(default_factory=list)
    maxResultsPerQuery: int = Field(default=10, ge=1, le=50)


class AdminQuizItem(CamelModel):
    id: str
    tier: str = Field(pattern="^(브론즈|실버|골드|플래티넘)$")
    topic: str = "해양교육"
    question: str = Field(min_length=2)
    options: list[str] = Field(min_length=4, max_length=4)
    answerIndex: int = Field(ge=0, le=3)
    explanation: str = Field(min_length=2)
    sourceTitle: str = ""
    sourceUrl: str = ""
    active: bool = True
