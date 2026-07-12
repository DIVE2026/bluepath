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

        public AuthRequest(String email, String password, String guardianEmail) {
            this.email = email;
            this.password = password;
            this.guardianEmail =
                    guardianEmail == null || guardianEmail.trim().isEmpty()
                            ? null
                            : guardianEmail.trim();
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

    public static class GenericResponse {
        public String message;
    }
}
