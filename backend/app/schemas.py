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
    nickname: str | None = Field(default=None, min_length=2, max_length=20, pattern=r"^[0-9A-Za-z가-힣_.-]+$")




class NicknameAvailability(CamelModel):
    nickname: str
    available: bool
    message: str


class ProfileSummary(CamelModel):
    userId: str
    nickname: str
    profileImageUrl: str = ""
    tier: str = "브론즈"
    followerCount: int = 0
    followingCount: int = 0
    isFollowing: bool = False
    joinedAt: datetime


class ProfileImageResponse(CamelModel):
    profileImageUrl: str


class ReactionSummary(CamelModel):
    emoji: str
    count: int
    reactedByMe: bool = False


class CommunityCommentItem(CamelModel):
    id: str
    postId: str
    parentId: str | None = None
    author: ProfileSummary
    body: str
    createdAt: datetime
    updatedAt: datetime
    canEdit: bool = False
    reactions: list[ReactionSummary] = Field(default_factory=list)


class CommunityPostItem(CamelModel):
    id: str
    category: str
    author: ProfileSummary
    title: str
    body: str
    imageUrl: str = ""
    createdAt: datetime
    updatedAt: datetime
    canEdit: bool = False
    reactions: list[ReactionSummary] = Field(default_factory=list)
    comments: list[CommunityCommentItem] = Field(default_factory=list)


class CommunityPostUpdate(CamelModel):
    title: str = Field(min_length=2, max_length=240)
    body: str = Field(min_length=2, max_length=8000)


class CommunityCommentUpdate(CamelModel):
    body: str = Field(min_length=1, max_length=3000)


class CommunityReportRequest(CamelModel):
    targetType: str = Field(pattern="^(post|comment|user)$")
    targetId: str
    reason: str = Field(min_length=2, max_length=500)


class CommunityBlockResponse(CamelModel):
    blocked: bool


class CommunityModerationRequest(CamelModel):
    status: str = Field(pattern="^(resolved|dismissed)$")
    action: str = Field(default="none", pattern="^(none|delete|deactivate)$")
    reviewNote: str = Field(default="", max_length=1000)


class CommunityPostCreate(CamelModel):
    category: str = Field(pattern="^(free|question)$")
    title: str = Field(min_length=2, max_length=240)
    body: str = Field(min_length=2, max_length=8000)


class CommunityCommentCreate(CamelModel):
    body: str = Field(min_length=1, max_length=3000)
    parentId: str | None = None


class ReactionToggleRequest(CamelModel):
    targetType: str = Field(pattern="^(post|comment)$")
    targetId: str
    emoji: str = Field(min_length=1, max_length=16)


class ReactionToggleResponse(CamelModel):
    active: bool
    reactions: list[ReactionSummary]


class FollowResponse(CamelModel):
    following: bool
    followerCount: int
    followingCount: int


class DashboardResponse(CamelModel):
    profile: ProfileSummary
    activity: dict[str, int] = Field(default_factory=dict)


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
    nickname: str
    profileImageUrl: str = ""
    followerCount: int = 0
    followingCount: int = 0
    joinedAt: datetime


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
    answerIndex: int = -1
    explanation: str = ""
    sources: list[SourceItem] = Field(default_factory=list)


class QuizRequest(CamelModel):
    tier: str
    profile: dict[str, Any] = Field(default_factory=dict)
    videos: list[dict[str, Any]] = Field(default_factory=list)


class QuizResponse(CamelModel):
    source: str
    sessionId: str = ""
    expiresAt: datetime | None = None
    questions: list[QuizQuestion]


class QuizSubmissionRequest(CamelModel):
    sessionId: str
    answers: list[int]


class QuizSubmissionResponse(CamelModel):
    sessionId: str
    correctCount: int
    total: int
    passed: bool
    xpAwarded: int
    xp: int
    tier: str
    quizTierRank: int
    advancedQuizPassed: bool
    skillMastery: dict[str, int] = Field(default_factory=dict)
    skillEvidence: dict[str, int] = Field(default_factory=dict)
    questions: list[QuizQuestion] = Field(default_factory=list)


class ConversationMessage(CamelModel):
    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1, max_length=4000)


class AgentRequest(CamelModel):
    question: str = Field(min_length=1, max_length=2000)
    tier: str
    profile: dict[str, Any] = Field(default_factory=dict)
    promotionManual: str = ""
    history: list[ConversationMessage] = Field(default_factory=list, max_length=20)


class AgentResponse(CamelModel):
    answer: str
    sources: list[SourceItem] = Field(default_factory=list)


class LearningRecordInput(CamelModel):
    id: str
    recordType: str
    targetId: str
    title: str = ""
    status: str = ""
    updatedAt: int = 0
    synced: bool = False


class SyncRequest(CamelModel):
    snapshot: dict[str, Any]
    learningRecords: list[LearningRecordInput] = Field(default_factory=list)
    baseVersion: int = Field(default=0, ge=0)
    deviceId: str = Field(min_length=8, max_length=80)


class DiamondStatus(CamelModel):
    advancedQuizPassed: bool
    certificationStatus: str
    projectStatus: str
    eligible: bool
    message: str


class CloudLearningRecord(CamelModel):
    id: str
    recordType: str
    targetId: str
    title: str = ""
    status: str = ""
    updatedAt: int = 0


class SyncResponse(CamelModel):
    message: str
    syncedAt: str
    diamondStatus: DiamondStatus
    snapshot: dict[str, Any] = Field(default_factory=dict)
    learningRecords: list[CloudLearningRecord] = Field(default_factory=list)
    version: int = 0
    acceptedRecordIds: list[str] = Field(default_factory=list)
    rejectedRecordIds: list[str] = Field(default_factory=list)
    conflictResolved: bool = False


class CloudStateResponse(CamelModel):
    snapshot: dict[str, Any] = Field(default_factory=dict)
    diamondStatus: DiamondStatus
    learningRecords: list[CloudLearningRecord] = Field(default_factory=list)
    version: int = 0


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
    authors: str = ""
    year: str = ""
    doi: str = ""
    applicationUrl: str = ""
    applicationDeadline: str = ""
    capacity: int = 0
    waitlistAvailable: bool = False
    timezone: str = "Asia/Seoul"
    paperStatus: str = "current"
    versionNote: str = ""


class AiSearchRequest(CamelModel):
    query: str = Field(min_length=1, max_length=1000)
    resourceType: str = Field(pattern="^(video|schedule|paper)$")
    limit: int = Field(default=10, ge=1, le=30)
    history: list[ConversationMessage] = Field(default_factory=list, max_length=20)


class AiSearchResponse(CamelModel):
    summary: str
    items: list[AdminContentItem] = Field(default_factory=list)
    sources: list[SourceItem] = Field(default_factory=list)
    usedLiveWeb: bool = False


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


class RoutePlanRequest(CamelModel):
    targetCareer: str = Field(default="해양환경 교육 기획자", min_length=2, max_length=200)
    routeType: str = Field(
        default="balanced",
        pattern="^(balanced|fastest|experience|family|career|weekend|free)$",
    )
    profile: dict[str, Any] = Field(default_factory=dict)
    constraints: dict[str, Any] = Field(default_factory=dict)
    maxNodes: int = Field(default=5, ge=3, le=7)


class RouteNodeItem(CamelModel):
    id: str
    order: int
    nodeType: str
    targetId: str = ""
    title: str
    description: str = ""
    source: str = ""
    topic: str = "해양교육"
    minutes: int = 0
    expectedSkillGain: int = 0
    readinessGain: int = 0
    scheduleStatus: str = "available"
    availabilityLabel: str = "지금 시작 가능"
    ncsCompetencies: list[str] = Field(default_factory=list)
    whyThisOrder: str = ""
    recommendationReasons: list[str] = Field(default_factory=list)
    evidenceBasis: list[str] = Field(default_factory=list)
    actionLabel: str = "시작하기"
    actionUrl: str = ""
    completed: bool = False


class RoutePlanResponse(CamelModel):
    routeId: str
    targetCareer: str
    routeType: str
    summary: str
    coachMessage: str = ""
    currentSkillTopic: str
    currentMastery: int
    tier: str
    readinessBefore: int
    readinessAfter: int
    estimatedMinutes: int
    estimatedDays: int
    generatedBy: str = "rules"
    nodes: list[RouteNodeItem] = Field(default_factory=list)
    alternatives: list[str] = Field(default_factory=list)
    sources: list[SourceItem] = Field(default_factory=list)


class RouteSimulationRequest(CamelModel):
    routeId: str | None = None
    nodeId: str | None = None
    activityTitle: str = Field(default="", max_length=300)
    skillTopic: str = Field(default="해양교육", max_length=120)
    expectedSkillGain: int = Field(default=5, ge=0, le=30)
    readinessGain: int = Field(default=4, ge=0, le=30)
    profile: dict[str, Any] = Field(default_factory=dict)


class RouteSimulationResponse(CamelModel):
    activityTitle: str
    skillTopic: str
    masteryBefore: int
    masteryAfter: int
    readinessBefore: int
    readinessAfter: int
    weakItemsBefore: int
    weakItemsAfter: int
    nextRecommendation: str
    explanation: str
    confidence: int = Field(ge=0, le=100)
    evidenceBasis: list[str] = Field(default_factory=list)


class RouteRerouteRequest(CamelModel):
    routeId: str | None = None
    blockedNodeId: str | None = None
    reason: str = Field(default="schedule_conflict", max_length=120)
    profile: dict[str, Any] = Field(default_factory=dict)
    constraints: dict[str, Any] = Field(default_factory=dict)


class MissionQrPayload(CamelModel):
    exhibitCode: str = Field(min_length=2, max_length=120)
    exhibitTitle: str = Field(default="", max_length=240)
    sessionId: str = Field(min_length=8, max_length=80)
    issuedAt: datetime
    expiresAt: datetime
    nonce: str = Field(min_length=16, max_length=64)
    signature: str = Field(min_length=32, max_length=128)


class MissionQrIssueRequest(CamelModel):
    exhibitCode: str = Field(min_length=2, max_length=120)
    exhibitTitle: str = Field(min_length=2, max_length=240)
    sessionId: str | None = Field(default=None, min_length=8, max_length=80)
    validMinutes: int = Field(default=20, ge=5, le=120)


class MissionQrIssueResponse(MissionQrPayload):
    qrJson: str


class MissionGenerateRequest(CamelModel):
    exhibitCode: str = Field(min_length=2, max_length=120)
    exhibitTitle: str = Field(min_length=2, max_length=240)
    participantCount: int = Field(default=2, ge=2, le=6)
    qrPayload: MissionQrPayload
    profile: dict[str, Any] = Field(default_factory=dict)


class MissionRole(CamelModel):
    name: str
    audience: str
    task: str


class FamilyMissionResponse(CamelModel):
    missionId: str
    exhibitCode: str
    title: str
    story: str
    roles: list[MissionRole] = Field(default_factory=list)
    jointTask: str
    expectedSkillGains: dict[str, int] = Field(default_factory=dict)
    badge: str
    safetyNote: str
    followUpRecommendation: str
    generatedBy: str = "rules"


class MissionVerifyRequest(CamelModel):
    missionId: str
    completionNote: str = Field(min_length=10, max_length=2000)
    participantCount: int = Field(default=1, ge=1, le=8)
    qrPayload: MissionQrPayload


class MissionVerifyResponse(CamelModel):
    verified: bool
    newlyVerified: bool = False
    message: str
    badge: str
    acquiredCompetencies: dict[str, int] = Field(default_factory=dict)
    verifiedAt: datetime
    nextRecommendation: str


class RouteActivationRequest(CamelModel):
    routeId: str


class ProgramParticipationRequest(CamelModel):
    programId: str = Field(min_length=2, max_length=160)
    programTitle: str = Field(default="", max_length=300)
    status: str = Field(default="enrolled", pattern="^(enrolled|attended|completed)$")
    preAssessment: int | None = Field(default=None, ge=0, le=100)
    postAssessment: int | None = Field(default=None, ge=0, le=100)
    metadata: dict[str, Any] = Field(default_factory=dict)


class ProgramParticipationResponse(CamelModel):
    programId: str
    status: str
    enrolledAt: datetime
    attendedAt: datetime | None = None
    completedAt: datetime | None = None
    preAssessment: int | None = None
    postAssessment: int | None = None


class RouteOutcomeEventRequest(CamelModel):
    routeId: str | None = None
    nodeId: str | None = None
    eventType: str = Field(pattern="^(viewed|started|completed|skipped|rerouted|followup|career_opened)$")
    value: float = 1.0
    metadata: dict[str, Any] = Field(default_factory=dict)


class ProgramDraftRequest(CamelModel):
    topic: str = Field(min_length=2, max_length=120)
    targetAudience: str = Field(default="전 연령", max_length=160)
    durationMinutes: int = Field(default=60, ge=15, le=240)
    institutionContext: str = Field(default="국립해양박물관 현장 교육", max_length=1000)
    objective: str = Field(default="관심을 지속 학습과 해양 진로 탐색으로 연결", max_length=500)


class ProgramDraftResponse(CamelModel):
    title: str
    rationale: str
    targetAudience: str
    learningObjectives: list[str] = Field(default_factory=list)
    agenda30: list[str] = Field(default_factory=list)
    agenda60: list[str] = Field(default_factory=list)
    agenda90: list[str] = Field(default_factory=list)
    ncsCompetencies: list[str] = Field(default_factory=list)
    preQuestions: list[str] = Field(default_factory=list)
    postQuestions: list[str] = Field(default_factory=list)
    followUpLearning: list[str] = Field(default_factory=list)
    measurementPlan: list[str] = Field(default_factory=list)
    generatedBy: str = "rules"


class PaperCompletionRequest(CamelModel):
    contentId: str = Field(min_length=1, max_length=160)
    reflection: str = Field(min_length=40, max_length=4000)


class PaperCompletionResponse(CamelModel):
    verified: bool
    xpAwarded: int
    message: str
    verifiedAt: datetime


class VideoInterval(CamelModel):
    start: float = Field(ge=0)
    end: float = Field(gt=0)


class VideoEvidenceRequest(CamelModel):
    contentId: str
    durationSeconds: int = Field(ge=30, le=43200)
    intervals: list[VideoInterval] = Field(default_factory=list, max_length=2000)


class VideoEvidenceResponse(CamelModel):
    verified: bool
    watchedSeconds: int
    coveragePercent: int
    xpAwarded: int
    message: str


class VideoCompletionRequest(CamelModel):
    contentId: str = Field(min_length=1, max_length=160)
    reflection: str = Field(default="", max_length=2000)


class VideoCompletionResponse(CamelModel):
    completed: bool
    xpAwarded: int
    message: str
    completedAt: datetime


class GuardianConsentRequestCreate(CamelModel):
    guardianEmail: EmailStr
    consentVersion: str = Field(default="2026-07", min_length=4, max_length=40)


class GuardianConsentStatus(CamelModel):
    status: str
    guardianEmail: str = ""
    consentVersion: str = ""
    consentedAt: datetime | None = None


class GuardianConsentDetails(CamelModel):
    status: str
    learnerName: str
    guardianEmailMasked: str
    consentVersion: str
    expiresAt: datetime
    consentedAt: datetime | None = None
    terms: list[str] = Field(default_factory=list)


class GuardianConsentConfirm(CamelModel):
    token: str = Field(min_length=32, max_length=512)


class PortfolioIssueRequest(CamelModel):
    title: str = Field(default="BluePath Ocean Skill Passport", min_length=2, max_length=200)


class PortfolioCredentialResponse(CamelModel):
    credentialId: str
    verifyUrl: str
    signature: str
    issuedAt: datetime
    payload: dict[str, Any] = Field(default_factory=dict)
    revoked: bool = False
    valid: bool = True
