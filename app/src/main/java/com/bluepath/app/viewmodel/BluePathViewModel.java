package com.bluepath.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bluepath.app.network.ApiModels;
import com.bluepath.app.repository.BluePathRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BluePathViewModel extends AndroidViewModel {
    public static class OperationState {
        public final String type;
        public final boolean success;
        public final String message;

        public OperationState(String type, boolean success, String message) {
            this.type = type;
            this.success = success;
            this.message = message;
        }
    }

    private final BluePathRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<OperationState> operation = new MutableLiveData<>();

    public BluePathViewModel(@NonNull Application application) {
        super(application);
        repository = new BluePathRepository(application);
    }

    public LiveData<OperationState> operation() {
        return operation;
    }

    public boolean isCloudConfigured() {
        return repository.isCloudConfigured();
    }

    public void register(String email, String password, String guardianEmail, String nickname) {
        run("register", () -> {
            repository.register(email, password, guardianEmail, nickname);
            return "BluePath 계정이 생성되었습니다.";
        });
    }

    public void login(String email, String password) {
        run("login", () -> {
            repository.login(email, password);
            return "로그인했습니다.";
        });
    }

    public void requestPasswordReset(String email) {
        run("password_reset", () -> repository.requestPasswordReset(email));
    }

    public void syncNow() {
        run("sync", repository::syncNow);
    }

    public void refreshCatalog() {
        run("catalog", () -> {
            int count = repository.refreshCatalog();
            return count + "개의 최신 학습 자료를 불러왔습니다.";
        });
    }

    public void submitDiamondEvidence(String type, String title, String url) {
        run("diamond", () -> repository.submitDiamondEvidence(type, title, url));
    }

    public void refreshDiamondStatus() {
        run("diamond_status", () -> {
            ApiModels.DiamondStatus status = repository.refreshDiamondStatus();
            return status.message == null ? "다이아 진행 상태를 업데이트했습니다." : status.message;
        });
    }

    public void recordLearning(String type, String targetId, String title, String status) {
        executor.execute(() -> repository.recordLearning(type, targetId, title, status));
    }

    public void logout() {
        repository.logout();
        operation.setValue(new OperationState("logout", true, "로그아웃했습니다."));
    }

    private interface Task {
        String execute() throws Exception;
    }

    private void run(String type, Task task) {
        operation.postValue(new OperationState(type, true, "처리 중…"));
        executor.execute(() -> {
            try {
                operation.postValue(new OperationState(type, true, task.execute()));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                operation.postValue(new OperationState(type, false, message));
            }
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }
}
