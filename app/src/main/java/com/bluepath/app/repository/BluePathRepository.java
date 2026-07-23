package com.bluepath.app.repository;

import android.content.Context;
import android.net.Uri;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.local.BluePathDatabase;
import com.bluepath.app.local.LearningRecord;
import com.bluepath.app.local.LearningRecordDao;
import com.bluepath.app.network.ApiClient;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.network.BluePathApi;
import com.bluepath.app.storage.UserStore;
import com.bluepath.app.util.NotificationHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Response;

public class BluePathRepository {
    private final Context context;
    private final UserStore store;
    private final LearningRecordDao learningDao;
    private final BluePathApi api;

    public BluePathRepository(Context context) {
        this.context = context.getApplicationContext();
        store = new UserStore(this.context);
        learningDao = BluePathDatabase.get(context).learningRecordDao();
        api = ApiClient.service();
    }

    public boolean isCloudConfigured() {
        return ApiClient.isConfigured();
    }

    public ApiModels.NicknameAvailability nicknameAvailable(String nickname) throws IOException {
        requireCloud();
        return requireBody(api.nicknameAvailable(nickname).execute(), "닉네임 중복 확인");
    }

    public void register(String email, String password, String guardianEmail, String nickname) throws IOException {
        requireCloud();
        Response<ApiModels.AuthResponse> response = api.register(
                new ApiModels.AuthRequest(email, password, guardianEmail, nickname)).execute();
        ApiModels.AuthResponse body = requireBody(response, "회원가입");
        store.saveCloudSession(body.email, body.displayName, body.nickname, body.profileImageUrl,
                body.followerCount, body.followingCount, body.joinedAt, body.accessToken);
        migrateLegacyLearningRecords();
        syncNow();
        refreshCatalog();
    }

    public void login(String email, String password) throws IOException {
        requireCloud();
        Response<ApiModels.AuthResponse> response = api.login(
                new ApiModels.AuthRequest(email, password, "", null)).execute();
        ApiModels.AuthResponse body = requireBody(response, "로그인");
        store.saveCloudSession(body.email, body.displayName, body.nickname, body.profileImageUrl,
                body.followerCount, body.followingCount, body.joinedAt, body.accessToken);
        migrateLegacyLearningRecords();
        pullCloudState();
        syncNow();
        refreshCatalog();
    }

    public String requestPasswordReset(String email) throws IOException {
        requireCloud();
        Response<ApiModels.GenericResponse> response = api.requestPasswordReset(
                new ApiModels.PasswordResetRequest(email)).execute();
        ApiModels.GenericResponse body = requireBody(response, "비밀번호 재설정 요청");
        return body.message == null ? "비밀번호 재설정 안내를 확인해 주세요." : body.message;
    }

    public ApiModels.CloudStateResponse pullCloudState() throws IOException {
        requireAuthenticated();
        Response<ApiModels.CloudStateResponse> response = api.cloudState(bearer()).execute();
        ApiModels.CloudStateResponse body = requireBody(response, "클라우드 기록 불러오기");
        store.applyCloudSnapshot(body.snapshot);
        store.setCloudVersion(body.version);
        restoreCloudRecords(body.learningRecords);
        restoreReminderSchedule();
        if (body.diamondStatus != null) store.applyDiamondStatus(body.diamondStatus);
        return body;
    }

    public String syncNow() throws IOException {
        requireAuthenticated();
        String accountId = store.getAccountScope();
        normalizeLegacyRecordIds(accountId);
        List<LearningRecord> pending = learningDao.unsynced(accountId);
        Response<ApiModels.SyncResponse> response = api.sync(
                bearer(), new ApiModels.SyncRequest(
                        store.toCloudSnapshot(), pending, store.getCloudVersion(), store.getDeviceId())).execute();
        ApiModels.SyncResponse body = requireBody(response, "클라우드 동기화");
        if (body.acceptedRecordIds != null && !body.acceptedRecordIds.isEmpty()) {
            learningDao.markSynced(accountId, body.acceptedRecordIds);
        }
        store.setCloudVersion(body.version);
        store.setLastSyncAt(body.syncedAt == null ? "방금 전" : body.syncedAt);
        if (body.snapshot != null) store.applyCloudSnapshot(body.snapshot);
        restoreCloudRecords(body.learningRecords);
        restoreReminderSchedule();
        if (body.diamondStatus != null) store.applyDiamondStatus(body.diamondStatus);
        return body.message == null ? "학습 기록을 동기화했습니다." : body.message;
    }

    public int refreshCatalog() throws IOException {
        requireAuthenticated();
        Response<List<ApiModels.ContentDto>> response = api.catalog(bearer()).execute();
        List<ApiModels.ContentDto> body = requireBody(response, "학습 카탈로그 업데이트");
        DataRepository.applyRemoteCatalog(body);
        return body.size();
    }


    public ApiModels.AiSearchResponse aiSearch(String query, String resourceType,
                                               List<ApiModels.ChatMessage> history) throws IOException {
        requireAuthenticated();
        return requireBody(api.aiSearch(
                bearer(), new ApiModels.AiSearchRequest(query, resourceType, 12, history)
        ).execute(), "AI 자료 검색");
    }

    public ApiModels.RoutePlanResponse planRoute(String targetCareer, String routeType) throws IOException {
        requireAuthenticated();
        java.util.Map<String, Object> constraints = new java.util.HashMap<>();
        constraints.put("weekendOnly", "weekend".equals(routeType));
        constraints.put("freeOnly", "free".equals(routeType));
        constraints.put("family", "family".equals(routeType));
        return requireBody(api.planRoute(
                bearer(),
                new ApiModels.RoutePlanRequest(targetCareer, routeType, store.toCloudSnapshot(), constraints, 5)
        ).execute(), "AI 항로 생성");
    }

    public ApiModels.RouteSimulationResponse simulateRoute(String routeId, ApiModels.RouteNodeDto node) throws IOException {
        requireAuthenticated();
        return requireBody(api.simulateRoute(
                bearer(), new ApiModels.RouteSimulationRequest(routeId, node, store.toCloudSnapshot())
        ).execute(), "미래 항로 시뮬레이션");
    }

    public ApiModels.RoutePlanResponse reroute(String routeId, String blockedNodeId, String reason) throws IOException {
        requireAuthenticated();
        java.util.Map<String, Object> constraints = new java.util.HashMap<>();
        constraints.put("reason", reason);
        return requireBody(api.reroute(
                bearer(),
                new ApiModels.RouteRerouteRequest(routeId, blockedNodeId, reason, store.toCloudSnapshot(), constraints)
        ).execute(), "자동 재항해");
    }

    public ApiModels.RoutePlanResponse previewReroute(String routeId, String reason) throws IOException {
        requireAuthenticated();
        java.util.Map<String, Object> constraints = new java.util.HashMap<>();
        constraints.put("reason", reason);
        return requireBody(api.previewReroute(
                bearer(),
                new ApiModels.RouteRerouteRequest(routeId, null, reason, store.toCloudSnapshot(), constraints)
        ).execute(), "자동 재항해 미리 생성");
    }

    public ApiModels.RoutePlanResponse pendingRoute() throws IOException {
        requireAuthenticated();
        retrofit2.Response<ApiModels.RoutePlanResponse> response = api.pendingRoute(bearer()).execute();
        if (response.code() == 204) return null;
        if (!response.isSuccessful()) return requireBody(response, "대기 중 항로 확인");
        return response.body();
    }

    public ApiModels.RoutePlanResponse activateRoute(String routeId) throws IOException {
        requireAuthenticated();
        return requireBody(api.activateRoute(
                bearer(), new ApiModels.RouteActivationRequest(routeId)
        ).execute(), "대체 항로 적용");
    }

    public void recordRouteOutcome(String routeId, String nodeId, String eventType) throws IOException {
        requireAuthenticated();
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("client", "android");
        requireBody(api.routeOutcome(
                bearer(), new ApiModels.RouteOutcomeRequest(routeId, nodeId, eventType, metadata)
        ).execute(), "항로 활동 기록");
    }

    public ApiModels.FamilyMissionResponse generateMission(ApiModels.MissionQrPayload qrPayload, int participantCount) throws IOException {
        requireAuthenticated();
        return requireBody(api.generateMission(
                bearer(),
                new ApiModels.MissionGenerateRequest(
                        qrPayload.exhibitCode, qrPayload.exhibitTitle, participantCount, qrPayload, store.toCloudSnapshot())
        ).execute(), "가족 협동 미션 생성");
    }

    public ApiModels.MissionVerifyResponse verifyMission(String missionId, String completionNote, int participantCount,
                                                          ApiModels.MissionQrPayload qrPayload) throws IOException {
        requireAuthenticated();
        return requireBody(api.verifyMission(
                bearer(), new ApiModels.MissionVerifyRequest(missionId, completionNote, participantCount, qrPayload)
        ).execute(), "현장 미션 인증");
    }

    public ApiModels.DashboardResponse refreshDashboard() throws IOException {
        requireAuthenticated();
        ApiModels.DashboardResponse body = requireBody(api.dashboard(bearer()).execute(), "홈 활동 불러오기");
        store.applyDashboard(body);
        return body;
    }

    public List<ApiModels.CommunityPostDto> communityPosts(String category, String query, int limit, int offset) throws IOException {
        requireAuthenticated();
        return requireBody(api.communityPosts(bearer(), category, query == null ? "" : query, limit, offset)
                .execute(), "커뮤니티 불러오기");
    }

    public List<ApiModels.CommunityPostDto> communityPosts(String category) throws IOException {
        return communityPosts(category, "", 20, 0);
    }

    public ApiModels.CommunityPostDto communityPost(String postId) throws IOException {
        requireAuthenticated();
        return requireBody(api.communityPost(bearer(), postId).execute(), "게시글 불러오기");
    }

    public ApiModels.CommunityPostDto createCommunityPost(String category, String title, String body) throws IOException {
        requireAuthenticated();
        ApiModels.CommunityPostDto result = requireBody(api.createCommunityPost(
                bearer(), new ApiModels.CommunityPostRequest(category, title, body)).execute(), "게시글 작성");
        store.recordActivity("community_post", 1);
        return result;
    }

    public ApiModels.CommunityPostDto uploadCommunityPostImage(String postId, Uri uri) throws IOException {
        requireAuthenticated();
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null || (!mimeType.equals("image/jpeg") && !mimeType.equals("image/png") && !mimeType.equals("image/webp"))) {
            throw new IOException("게시글 사진은 JPEG, PNG, WebP 형식만 첨부할 수 있습니다.");
        }
        String extension = mimeType.equals("image/png") ? ".png" : mimeType.equals("image/webp") ? ".webp" : ".jpg";
        File temp = File.createTempFile("bluepath-community-", extension, context.getCacheDir());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(temp)) {
                if (input == null) throw new IOException("선택한 사진을 읽을 수 없습니다.");
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 8_000_000L) throw new IOException("게시글 사진은 8MB 이하만 첨부할 수 있습니다.");
                    output.write(buffer, 0, read);
                }
            }
            RequestBody body = RequestBody.create(MediaType.parse(mimeType), temp);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", temp.getName(), body);
            return requireBody(api.uploadCommunityPostImage(bearer(), postId, part).execute(), "게시글 사진 업로드");
        } finally {
            temp.delete();
        }
    }

    public ApiModels.CommunityCommentDto createCommunityComment(String postId, String body, String parentId) throws IOException {
        requireAuthenticated();
        ApiModels.CommunityCommentDto result = requireBody(api.createCommunityComment(
                bearer(), postId, new ApiModels.CommunityCommentRequest(body, parentId)).execute(), "댓글 작성");
        store.recordActivity("community_comment", 1);
        return result;
    }

    public ApiModels.CommunityPostDto updateCommunityPost(String postId, String title, String body) throws IOException {
        requireAuthenticated();
        return requireBody(api.updateCommunityPost(bearer(), postId,
                new ApiModels.CommunityPostUpdateRequest(title, body)).execute(), "게시글 수정");
    }

    public void deleteCommunityPost(String postId) throws IOException {
        requireAuthenticated();
        requireBody(api.deleteCommunityPost(bearer(), postId).execute(), "게시글 삭제");
    }

    public ApiModels.CommunityCommentDto updateCommunityComment(String commentId, String body) throws IOException {
        requireAuthenticated();
        return requireBody(api.updateCommunityComment(bearer(), commentId,
                new ApiModels.CommunityCommentUpdateRequest(body)).execute(), "댓글 수정");
    }

    public void deleteCommunityComment(String commentId) throws IOException {
        requireAuthenticated();
        requireBody(api.deleteCommunityComment(bearer(), commentId).execute(), "댓글 삭제");
    }

    public void reportCommunity(String targetType, String targetId, String reason) throws IOException {
        requireAuthenticated();
        requireBody(api.reportCommunity(bearer(), new ApiModels.CommunityReportRequest(targetType, targetId, reason))
                .execute(), "커뮤니티 신고");
    }

    public boolean toggleBlock(String userId) throws IOException {
        requireAuthenticated();
        return requireBody(api.toggleBlock(bearer(), userId).execute(), "사용자 차단").blocked;
    }

    public ApiModels.ReactionToggleResponse toggleReaction(String targetType, String targetId, String emoji) throws IOException {
        requireAuthenticated();
        return requireBody(api.toggleReaction(bearer(), new ApiModels.ReactionToggleRequest(targetType, targetId, emoji)).execute(), "공감 반응");
    }

    public ApiModels.FollowResponse toggleFollow(String userId) throws IOException {
        requireAuthenticated();
        ApiModels.FollowResponse response = requireBody(api.toggleFollow(bearer(), userId).execute(), "팔로우");
        store.setFollowingCount(response.followingCount);
        return response;
    }

    public String uploadProfileImage(Uri uri) throws IOException {
        requireAuthenticated();
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null || (!mimeType.equals("image/jpeg") && !mimeType.equals("image/png") && !mimeType.equals("image/webp"))) {
            mimeType = "image/jpeg";
        }
        String extension = mimeType.equals("image/png") ? ".png" : mimeType.equals("image/webp") ? ".webp" : ".jpg";
        File temp = File.createTempFile("bluepath-profile-", extension, context.getCacheDir());
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(temp)) {
            if (input == null) throw new IOException("선택한 사진을 읽을 수 없습니다.");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        }
        RequestBody body = RequestBody.create(MediaType.parse(mimeType), temp);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", temp.getName(), body);
        ApiModels.ProfileImageResponse response = requireBody(api.uploadProfileImage(bearer(), part).execute(), "프로필 사진 업로드");
        store.setProfileImageUrl(response.profileImageUrl);
        temp.delete();
        return response.profileImageUrl;
    }

    public void recordLearning(String type, String targetId, String title, String status) {
        learningDao.insert(new LearningRecord(store.getAccountScope(), type, targetId, title, status,
                System.currentTimeMillis(), false));
    }

    public ApiModels.ProgramParticipationResponse saveProgramParticipation(
            String programId, String title, String status, Integer preAssessment, Integer postAssessment) throws IOException {
        requireAuthenticated();
        return requireBody(api.saveProgramParticipation(bearer(), new ApiModels.ProgramParticipationRequest(
                programId, title, status, preAssessment, postAssessment)).execute(), "교육 참여 기록");
    }

    public ApiModels.PaperCompletionResponse completePaper(String contentId, String reflection) throws IOException {
        requireAuthenticated();
        ApiModels.PaperCompletionResponse body = requireBody(api.completePaper(
                bearer(), new ApiModels.PaperCompletionRequest(contentId, reflection)).execute(), "논문 학습 검증");
        pullCloudState();
        return body;
    }

    public ApiModels.VideoEvidenceResponse verifyVideo(String contentId, int durationSeconds, List<ApiModels.VideoInterval> intervals) throws IOException {
        requireAuthenticated();
        ApiModels.VideoEvidenceResponse body = requireBody(api.verifyVideo(
                bearer(), new ApiModels.VideoEvidenceRequest(contentId, durationSeconds, intervals)).execute(), "영상 학습 검증");
        pullCloudState();
        return body;
    }

    public ApiModels.VideoCompletionResponse completeVideo(String contentId, String reflection) throws IOException {
        requireAuthenticated();
        ApiModels.VideoCompletionResponse body = requireBody(api.completeVideo(
                bearer(), new ApiModels.VideoCompletionRequest(contentId, reflection)).execute(), "영상 학습 완료");
        pullCloudState();
        return body;
    }

    public ApiModels.GuardianConsentStatus requestGuardianConsent(String email) throws IOException {
        requireAuthenticated();
        return requireBody(api.requestGuardianConsent(
                bearer(), new ApiModels.GuardianConsentRequest(email, "2026-07")).execute(), "보호자 동의 요청");
    }

    public ApiModels.GuardianConsentStatus refreshGuardianConsent() throws IOException {
        requireAuthenticated();
        ApiModels.GuardianConsentStatus status = requireBody(api.guardianConsentStatus(bearer()).execute(), "보호자 동의 상태 확인");
        store.saveGuardianConsent("confirmed".equals(status.status), status.guardianEmail);
        return status;
    }

    public ApiModels.GuardianConsentStatus revokeGuardianConsent() throws IOException {
        requireAuthenticated();
        ApiModels.GuardianConsentStatus status = requireBody(api.revokeGuardianConsent(bearer()).execute(), "보호자 동의 철회");
        store.saveGuardianConsent(false, status.guardianEmail);
        return status;
    }

    public ApiModels.PortfolioCredentialResponse issuePortfolioCredential() throws IOException {
        requireAuthenticated();
        return requireBody(api.issuePortfolioCredential(
                bearer(), new ApiModels.PortfolioIssueRequest("BluePath Ocean Skill Passport")).execute(), "포트폴리오 인증 발급");
    }

    public String submitDiamondEvidence(String evidenceType, String title, String evidenceUrl) throws IOException {
        requireAuthenticated();
        Response<ApiModels.GenericResponse> response = api.submitEvidence(
                bearer(), new ApiModels.EvidenceRequest(evidenceType, title, evidenceUrl)).execute();
        ApiModels.GenericResponse body = requireBody(response, "다이아 증빙 제출");
        store.markDiamondEvidenceSubmitted(evidenceType, title);
        return body.message == null ? "검토 요청을 제출했습니다." : body.message;
    }

    public ApiModels.DiamondStatus refreshDiamondStatus() throws IOException {
        requireAuthenticated();
        Response<ApiModels.DiamondStatus> response = api.diamondStatus(bearer()).execute();
        ApiModels.DiamondStatus body = requireBody(response, "다이아 진행 상태 조회");
        store.applyDiamondStatus(body);
        return body;
    }

    private void restoreCloudRecords(List<ApiModels.CloudLearningRecordDto> records) {
        if (records == null) return;
        String accountId = store.getAccountScope();
        for (ApiModels.CloudLearningRecordDto item : records) {
            if (item == null || item.recordType == null || item.targetId == null) continue;
            String clientId = normalizedClientRecordId(item.id, accountId + ":" + item.id);
            if (learningDao.countClientRecord(accountId, clientId) > 0) continue;
            String status = item.status == null ? "" : item.status;
            learningDao.insert(new LearningRecord(clientId, accountId, item.recordType, item.targetId,
                    item.title == null ? "" : item.title, status, item.updatedAt, true));
        }
    }

    private void migrateLegacyLearningRecords() {
        if (store.consumeLegacyRecordMigrationFlag()) {
            learningDao.reassignAccount("guest", store.getAccountScope());
        }
        normalizeLegacyRecordIds(store.getAccountScope());
    }

    private void normalizeLegacyRecordIds(String accountId) {
        for (LearningRecord record : learningDao.legacyWithoutUuid(accountId)) {
            record.clientRecordId = UUID.randomUUID().toString();
            learningDao.update(record);
        }
    }

    private String normalizedClientRecordId(String value, String seed) {
        try { return UUID.fromString(value).toString(); }
        catch (Exception ignored) { return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); }
    }

    private void restoreReminderSchedule() {
        if (store.isReminderEnabled()) {
            NotificationHelper.scheduleDaily(context, store.getReminderHour(), store.getReminderMinute());
        } else {
            NotificationHelper.cancelDaily(context);
        }
    }

    public void logout() {
        store.clearCloudSession();
    }

    private void requireCloud() {
        if (!isCloudConfigured()) {
            throw new IllegalStateException("BluePath cloud server is not configured in this build.");
        }
    }

    private void requireAuthenticated() {
        requireCloud();
        if (!store.hasCloudSession()) throw new IllegalStateException("로그인이 필요합니다.");
    }

    private String bearer() {
        return "Bearer " + store.getAccessToken();
    }

    private <T> T requireBody(Response<T> response, String action) throws IOException {
        if (response.isSuccessful() && response.body() != null) return response.body();
        String detail = response.errorBody() == null ? "" : response.errorBody().string();
        if (detail.length() > 220) detail = detail.substring(0, 220) + "…";
        throw new IOException(action + " 실패 (HTTP " + response.code() + ") " + detail);
    }
}
