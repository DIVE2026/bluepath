package com.bluepath.app.network;

import com.bluepath.app.local.LearningRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {}

    public static class AuthRequest {
        public String email;
        public String password;
        public String guardianEmail;
        public String nickname;

        public AuthRequest(String email, String password, String guardianEmail, String nickname) {
            this.email = email;
            this.password = password;
            this.guardianEmail = guardianEmail == null || guardianEmail.trim().isEmpty() ? null : guardianEmail.trim();
            this.nickname = nickname == null || nickname.trim().isEmpty() ? null : nickname.trim();
        }
    }


    public static class PasswordResetRequest {
        public String email;

        public PasswordResetRequest(String email) {
            this.email = email;
        }
    }

    public static class AuthResponse {
        public String accessToken;
        public String tokenType;
        public String email;
        public String displayName;
        public String nickname;
        public String profileImageUrl;
        public int followerCount;
        public int followingCount;
        public String joinedAt;
    }

    public static class NicknameAvailability {
        public String nickname;
        public boolean available;
        public String message;
    }

    public static class ProfileSummary {
        public String userId;
        public String nickname;
        public String profileImageUrl;
        public String tier;
        public int followerCount;
        public int followingCount;
        public boolean isFollowing;
        public String joinedAt;
    }

    public static class DashboardResponse {
        public ProfileSummary profile;
        public Map<String, Integer> activity;
    }

    public static class ProfileImageResponse {
        public String profileImageUrl;
    }

    public static class QuizRequest {
        public String tier;
        public Map<String, Object> profile;
        public List<Map<String, Object>> videos;

        public QuizRequest(String tier, Map<String, Object> profile, List<Map<String, Object>> videos) {
            this.tier = tier;
            this.profile = profile;
            this.videos = videos;
        }
    }

    public static class QuizQuestionDto {
        public String id;
        public String tier;
        public String topic;
        public String question;
        public List<String> options = new ArrayList<>();
        public int answerIndex;
        public String explanation;
        public List<SourceDto> sources = new ArrayList<>();
    }

    public static class QuizResponse {
        public String source;
        public String sessionId;
        public String expiresAt;
        public List<QuizQuestionDto> questions = new ArrayList<>();
    }

    public static class QuizSubmissionRequest {
        public String sessionId;
        public List<Integer> answers;

        public QuizSubmissionRequest(String sessionId, List<Integer> answers) {
            this.sessionId = sessionId;
            this.answers = answers;
        }
    }

    public static class QuizSubmissionResponse {
        public String sessionId;
        public int correctCount;
        public int total;
        public boolean passed;
        public int xpAwarded;
        public int xp;
        public String tier;
        public int quizTierRank;
        public boolean advancedQuizPassed;
        public Map<String, Integer> skillMastery;
        public Map<String, Integer> skillEvidence;
        public List<QuizQuestionDto> questions = new ArrayList<>();
    }

    public static class ChatMessage {
        public String role;
        public String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static class AgentRequest {
        public String question;
        public String tier;
        public Map<String, Object> profile;
        public String promotionManual;
        public List<ChatMessage> history = new ArrayList<>();

        public AgentRequest(String question, String tier, Map<String, Object> profile,
                            String promotionManual, List<ChatMessage> history) {
            this.question = question;
            this.tier = tier;
            this.profile = profile;
            this.promotionManual = promotionManual;
            if (history != null) this.history.addAll(history);
        }
    }

    public static class AgentResponse {
        public String answer;
        public List<SourceDto> sources = new ArrayList<>();
    }

    public static class SourceDto {
        public String title;
        public String url;
        public String organization;
    }

    public static class LearningRecordPayload {
        public String id;
        public String recordType;
        public String targetId;
        public String title;
        public String status;
        public long updatedAt;
        public boolean synced;

        public LearningRecordPayload(LearningRecord record) {
            id = record.clientRecordId;
            recordType = record.recordType;
            targetId = record.targetId;
            title = record.title;
            status = record.status;
            updatedAt = record.updatedAt;
            synced = record.synced;
        }
    }

    public static class SyncRequest {
        public Map<String, Object> snapshot;
        public List<LearningRecordPayload> learningRecords;
        public int baseVersion;
        public String deviceId;

        public SyncRequest(Map<String, Object> snapshot, List<LearningRecord> records, int baseVersion, String deviceId) {
            this.snapshot = snapshot;
            this.learningRecords = new ArrayList<>();
            if (records != null) for (LearningRecord record : records) this.learningRecords.add(new LearningRecordPayload(record));
            this.baseVersion = baseVersion;
            this.deviceId = deviceId;
        }
    }

    public static class CloudLearningRecordDto {
        public String id;
        public String recordType;
        public String targetId;
        public String title;
        public String status;
        public long updatedAt;
    }

    public static class SyncResponse {
        public String message;
        public String syncedAt;
        public DiamondStatus diamondStatus;
        public Map<String, Object> snapshot;
        public List<CloudLearningRecordDto> learningRecords = new ArrayList<>();
        public int version;
        public List<String> acceptedRecordIds = new ArrayList<>();
        public List<String> rejectedRecordIds = new ArrayList<>();
        public boolean conflictResolved;
    }

    public static class CloudStateResponse {
        public Map<String, Object> snapshot;
        public DiamondStatus diamondStatus;
        public List<CloudLearningRecordDto> learningRecords = new ArrayList<>();
        public int version;
    }

    public static class EvidenceRequest {
        public String evidenceType;
        public String title;
        public String evidenceUrl;

        public EvidenceRequest(String evidenceType, String title, String evidenceUrl) {
            this.evidenceType = evidenceType;
            this.title = title;
            this.evidenceUrl = evidenceUrl;
        }
    }

    public static class DiamondStatus {
        public boolean advancedQuizPassed;
        public String certificationStatus = "not_submitted";
        public String projectStatus = "not_submitted";
        public boolean eligible;
        public String message;
    }

    public static class ContentDto {
        public String id;
        public String title;
        public String contentType;
        public String source;
        public String url;
        public String difficulty;
        public String requiredTier;
        public String topic;
        public String careerTag;
        public int minutes;
        public String startAt;
        public String endAt;
        public String target;
        public String method;
        public String category;
        public String description;
        public String authors;
        public String year;
        public String doi;
        public String applicationUrl;
        public String applicationDeadline;
        public int capacity;
        public boolean waitlistAvailable;
        public String timezone;
        public String paperStatus;
        public String versionNote;
    }


    public static class AiSearchRequest {
        public String query;
        public String resourceType;
        public int limit;
        public List<ChatMessage> history = new ArrayList<>();

        public AiSearchRequest(String query, String resourceType, int limit, List<ChatMessage> history) {
            this.query = query;
            this.resourceType = resourceType;
            this.limit = limit;
            if (history != null) this.history.addAll(history);
        }
    }

    public static class AiSearchResponse {
        public String summary;
        public List<ContentDto> items = new ArrayList<>();
        public List<SourceDto> sources = new ArrayList<>();
        public boolean usedLiveWeb;
    }

    public static class ReactionSummary {
        public String emoji;
        public int count;
        public boolean reactedByMe;
    }

    public static class CommunityCommentDto {
        public String id;
        public String postId;
        public String parentId;
        public ProfileSummary author;
        public String body;
        public String createdAt;
        public String updatedAt;
        public boolean canEdit;
        public List<ReactionSummary> reactions = new ArrayList<>();
    }

    public static class CommunityPostDto {
        public String id;
        public String category;
        public ProfileSummary author;
        public String title;
        public String body;
        public String imageUrl;
        public String createdAt;
        public String updatedAt;
        public boolean canEdit;
        public List<ReactionSummary> reactions = new ArrayList<>();
        public List<CommunityCommentDto> comments = new ArrayList<>();
    }

    public static class CommunityPostRequest {
        public String category;
        public String title;
        public String body;

        public CommunityPostRequest(String category, String title, String body) {
            this.category = category;
            this.title = title;
            this.body = body;
        }
    }

    public static class CommunityCommentRequest {
        public String body;
        public String parentId;

        public CommunityCommentRequest(String body, String parentId) {
            this.body = body;
            this.parentId = parentId;
        }
    }


    public static class CommunityPostUpdateRequest {
        public String title;
        public String body;
        public CommunityPostUpdateRequest(String title, String body) { this.title = title; this.body = body; }
    }

    public static class CommunityCommentUpdateRequest {
        public String body;
        public CommunityCommentUpdateRequest(String body) { this.body = body; }
    }

    public static class CommunityReportRequest {
        public String targetType;
        public String targetId;
        public String reason;
        public CommunityReportRequest(String targetType, String targetId, String reason) {
            this.targetType = targetType; this.targetId = targetId; this.reason = reason;
        }
    }

    public static class CommunityBlockResponse {
        public boolean blocked;
    }

    public static class ReactionToggleRequest {
        public String targetType;
        public String targetId;
        public String emoji;

        public ReactionToggleRequest(String targetType, String targetId, String emoji) {
            this.targetType = targetType;
            this.targetId = targetId;
            this.emoji = emoji;
        }
    }

    public static class ReactionToggleResponse {
        public boolean active;
        public List<ReactionSummary> reactions = new ArrayList<>();
    }

    public static class FollowResponse {
        public boolean following;
        public int followerCount;
        public int followingCount;
    }

    public static class GenericResponse {
        public String message;
    }

    public static class RoutePlanRequest {
        public String targetCareer;
        public String routeType;
        public Map<String, Object> profile;
        public Map<String, Object> constraints;
        public int maxNodes;

        public RoutePlanRequest(String targetCareer, String routeType, Map<String, Object> profile,
                                Map<String, Object> constraints, int maxNodes) {
            this.targetCareer = targetCareer;
            this.routeType = routeType;
            this.profile = profile;
            this.constraints = constraints;
            this.maxNodes = maxNodes;
        }
    }

    public static class RouteNodeDto {
        public String id;
        public int order;
        public String nodeType;
        public String targetId;
        public String title;
        public String description;
        public String source;
        public String topic;
        public int minutes;
        public int expectedSkillGain;
        public int readinessGain;
        public String scheduleStatus;
        public String availabilityLabel;
        public List<String> ncsCompetencies = new ArrayList<>();
        public String whyThisOrder;
        public List<String> recommendationReasons = new ArrayList<>();
        public List<String> evidenceBasis = new ArrayList<>();
        public String actionLabel;
        public String actionUrl;
        public boolean completed;
    }

    public static class RoutePlanResponse {
        public String routeId;
        public String targetCareer;
        public String routeType;
        public String summary;
        public String coachMessage;
        public String currentSkillTopic;
        public int currentMastery;
        public String tier;
        public int readinessBefore;
        public int readinessAfter;
        public int estimatedMinutes;
        public int estimatedDays;
        public String generatedBy;
        public List<RouteNodeDto> nodes = new ArrayList<>();
        public List<String> alternatives = new ArrayList<>();
        public List<SourceDto> sources = new ArrayList<>();
    }

    public static class RouteSimulationRequest {
        public String routeId;
        public String nodeId;
        public String activityTitle;
        public String skillTopic;
        public int expectedSkillGain;
        public int readinessGain;
        public Map<String, Object> profile;

        public RouteSimulationRequest(String routeId, RouteNodeDto node, Map<String, Object> profile) {
            this.routeId = routeId;
            this.nodeId = node == null ? null : node.id;
            this.activityTitle = node == null ? "" : node.title;
            this.skillTopic = node == null ? "해양교육" : node.topic;
            this.expectedSkillGain = node == null ? 5 : node.expectedSkillGain;
            this.readinessGain = node == null ? 4 : node.readinessGain;
            this.profile = profile;
        }
    }

    public static class RouteSimulationResponse {
        public String activityTitle;
        public String skillTopic;
        public int masteryBefore;
        public int masteryAfter;
        public int readinessBefore;
        public int readinessAfter;
        public int weakItemsBefore;
        public int weakItemsAfter;
        public String nextRecommendation;
        public String explanation;
        public int confidence;
        public List<String> evidenceBasis = new ArrayList<>();
    }

    public static class RouteRerouteRequest {
        public String routeId;
        public String blockedNodeId;
        public String reason;
        public Map<String, Object> profile;
        public Map<String, Object> constraints;

        public RouteRerouteRequest(String routeId, String blockedNodeId, String reason,
                                   Map<String, Object> profile, Map<String, Object> constraints) {
            this.routeId = routeId;
            this.blockedNodeId = blockedNodeId;
            this.reason = reason;
            this.profile = profile;
            this.constraints = constraints;
        }
    }

    public static class RouteActivationRequest {
        public String routeId;

        public RouteActivationRequest(String routeId) {
            this.routeId = routeId;
        }
    }

    public static class RouteOutcomeRequest {
        public String routeId;
        public String nodeId;
        public String eventType;
        public double value;
        public Map<String, Object> metadata;

        public RouteOutcomeRequest(String routeId, String nodeId, String eventType, Map<String, Object> metadata) {
            this.routeId = routeId;
            this.nodeId = nodeId;
            this.eventType = eventType;
            this.value = 1.0;
            this.metadata = metadata;
        }
    }

    public static class MissionQrPayload {
        public String exhibitCode;
        public String exhibitTitle;
        public String sessionId;
        public String issuedAt;
        public String expiresAt;
        public String nonce;
        public String signature;
    }

    public static class MissionGenerateRequest {
        public String exhibitCode;
        public String exhibitTitle;
        public int participantCount;
        public MissionQrPayload qrPayload;
        public Map<String, Object> profile;

        public MissionGenerateRequest(String exhibitCode, String exhibitTitle, int participantCount,
                                      MissionQrPayload qrPayload, Map<String, Object> profile) {
            this.exhibitCode = exhibitCode;
            this.exhibitTitle = exhibitTitle;
            this.participantCount = participantCount;
            this.qrPayload = qrPayload;
            this.profile = profile;
        }
    }

    public static class MissionRole {
        public String name;
        public String audience;
        public String task;
    }

    public static class FamilyMissionResponse {
        public String missionId;
        public String exhibitCode;
        public String title;
        public String story;
        public List<MissionRole> roles = new ArrayList<>();
        public String jointTask;
        public Map<String, Integer> expectedSkillGains;
        public String badge;
        public String safetyNote;
        public String followUpRecommendation;
        public String generatedBy;
    }

    public static class MissionVerifyRequest {
        public String missionId;
        public String completionNote;
        public int participantCount;
        public MissionQrPayload qrPayload;

        public MissionVerifyRequest(String missionId, String completionNote, int participantCount,
                                    MissionQrPayload qrPayload) {
            this.missionId = missionId;
            this.completionNote = completionNote;
            this.participantCount = participantCount;
            this.qrPayload = qrPayload;
        }
    }

    public static class MissionVerifyResponse {
        public boolean verified;
        public boolean newlyVerified;
        public String message;
        public String badge;
        public Map<String, Integer> acquiredCompetencies;
        public String verifiedAt;
        public String nextRecommendation;
    }

    public static class ProgramParticipationRequest {
        public String programId;
        public String programTitle;
        public String status;
        public Integer preAssessment;
        public Integer postAssessment;
        public Map<String, Object> metadata = new HashMap<>();
        public ProgramParticipationRequest(String programId, String programTitle, String status,
                                           Integer preAssessment, Integer postAssessment) {
            this.programId = programId; this.programTitle = programTitle; this.status = status;
            this.preAssessment = preAssessment; this.postAssessment = postAssessment;
            this.metadata.put("source", "android_schedule");
        }
    }

    public static class ProgramParticipationResponse {
        public String programId;
        public String status;
        public String enrolledAt;
        public String attendedAt;
        public String completedAt;
        public Integer preAssessment;
        public Integer postAssessment;
    }

    public static class PaperCompletionRequest {
        public String contentId;
        public String reflection;
        public PaperCompletionRequest(String contentId, String reflection) {
            this.contentId = contentId; this.reflection = reflection;
        }
    }

    public static class PaperCompletionResponse {
        public boolean verified;
        public int xpAwarded;
        public String message;
        public String verifiedAt;
    }

    public static class VideoInterval {
        public double start;
        public double end;
        public VideoInterval(double start, double end) { this.start = start; this.end = end; }
    }

    public static class VideoEvidenceRequest {
        public String contentId;
        public int durationSeconds;
        public List<VideoInterval> intervals;
        public VideoEvidenceRequest(String contentId, int durationSeconds, List<VideoInterval> intervals) {
            this.contentId = contentId; this.durationSeconds = durationSeconds; this.intervals = intervals;
        }
    }

    public static class VideoEvidenceResponse {
        public boolean verified;
        public int watchedSeconds;
        public int coveragePercent;
        public int xpAwarded;
        public String message;
    }

    public static class VideoCompletionRequest {
        public String contentId;
        public String reflection;
        public VideoCompletionRequest(String contentId, String reflection) {
            this.contentId = contentId;
            this.reflection = reflection;
        }
    }

    public static class VideoCompletionResponse {
        public boolean completed;
        public int xpAwarded;
        public String message;
        public String completedAt;
    }

    public static class GuardianConsentRequest {
        public String guardianEmail;
        public String consentVersion;
        public GuardianConsentRequest(String guardianEmail, String consentVersion) {
            this.guardianEmail = guardianEmail; this.consentVersion = consentVersion;
        }
    }

    public static class GuardianConsentStatus {
        public String status;
        public String guardianEmail;
        public String consentVersion;
        public String consentedAt;
    }

    public static class PortfolioIssueRequest {
        public String title;
        public PortfolioIssueRequest(String title) { this.title = title; }
    }

    public static class PortfolioCredentialResponse {
        public String credentialId;
        public String verifyUrl;
        public String signature;
        public String issuedAt;
        public Map<String, Object> payload;
        public boolean revoked;
    }

}
