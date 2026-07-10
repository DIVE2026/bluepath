package com.bluepath.app.repository;

import android.content.Context;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.local.BluePathDatabase;
import com.bluepath.app.local.LearningRecord;
import com.bluepath.app.local.LearningRecordDao;
import com.bluepath.app.network.ApiClient;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.network.BluePathApi;
import com.bluepath.app.storage.UserStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    public void register(String email, String password, String guardianEmail) throws IOException {
        requireCloud();
        Response<ApiModels.AuthResponse> response = api.register(
                new ApiModels.AuthRequest(email, password, guardianEmail)).execute();
        ApiModels.AuthResponse body = requireBody(response, "회원가입");
        store.saveCloudSession(body.email, body.displayName, body.accessToken);
        syncNow();
        refreshCatalog();
    }

    public void login(String email, String password) throws IOException {
        requireCloud();
        Response<ApiModels.AuthResponse> response = api.login(
                new ApiModels.AuthRequest(email, password, "")).execute();
        ApiModels.AuthResponse body = requireBody(response, "로그인");
        store.saveCloudSession(body.email, body.displayName, body.accessToken);
        ApiModels.CloudStateResponse cloud = pullCloudState();
        if (cloud.snapshot == null || cloud.snapshot.isEmpty()) syncNow();
        refreshCatalog();
    }

    public ApiModels.CloudStateResponse pullCloudState() throws IOException {
        requireAuthenticated();
        Response<ApiModels.CloudStateResponse> response = api.cloudState(bearer()).execute();
        ApiModels.CloudStateResponse body = requireBody(response, "클라우드 기록 불러오기");
        store.applyCloudSnapshot(body.snapshot);
        if (body.diamondStatus != null) store.applyDiamondStatus(body.diamondStatus);
        return body;
    }

    public String syncNow() throws IOException {
        requireAuthenticated();
        List<LearningRecord> pending = learningDao.unsynced();
        Response<ApiModels.SyncResponse> response = api.sync(
                bearer(),
                new ApiModels.SyncRequest(store.toCloudSnapshot(), pending)).execute();
        ApiModels.SyncResponse body = requireBody(response, "클라우드 동기화");
        List<Long> ids = new ArrayList<>();
        for (LearningRecord record : pending) ids.add(record.id);
        if (!ids.isEmpty()) learningDao.markSynced(ids);
        store.setLastSyncAt(body.syncedAt == null ? "방금 전" : body.syncedAt);
        if (body.snapshot != null) store.applyCloudSnapshot(body.snapshot);
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

    public void recordLearning(String type, String targetId, String title, String status) {
        learningDao.insert(new LearningRecord(type, targetId, title, status,
                System.currentTimeMillis(), false));
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
