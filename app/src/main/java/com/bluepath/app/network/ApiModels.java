package com.bluepath.app.network;

import com.bluepath.app.local.LearningRecord;

import java.util.ArrayList;
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
        public List<QuizQuestionDto> questions = new ArrayList<>();
    }

    public static class AgentRequest {
        public String question;
        public String tier;
        public Map<String, Object> profile;
        public String promotionManual;

        public AgentRequest(String question, String tier, Map<String, Object> profile, String promotionManual) {
            this.question = question;
            this.tier = tier;
            this.profile = profile;
            this.promotionManual = promotionManual;
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

    public static class SyncRequest {
        public Map<String, Object> snapshot;
        public List<LearningRecord> learningRecords;

        public SyncRequest(Map<String, Object> snapshot, List<LearningRecord> learningRecords) {
            this.snapshot = snapshot;
            this.learningRecords = learningRecords;
        }
    }

    public static class SyncResponse {
        public String message;
        public String syncedAt;
        public DiamondStatus diamondStatus;
        public Map<String, Object> snapshot;
    }

    public static class CloudStateResponse {
        public Map<String, Object> snapshot;
        public DiamondStatus diamondStatus;
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
    }


    public static class AiSearchRequest {
        public String query;
        public String resourceType;
        public int limit;

        public AiSearchRequest(String query, String resourceType, int limit) {
            this.query = query;
            this.resourceType = resourceType;
            this.limit = limit;
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
        public List<ReactionSummary> reactions = new ArrayList<>();
    }

    public static class CommunityPostDto {
        public String id;
        public String category;
        public ProfileSummary author;
        public String title;
        public String body;
        public String createdAt;
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
}
