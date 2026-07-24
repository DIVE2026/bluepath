package com.bluepath.app.util;

import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.network.ApiClient;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.network.BluePathApi;
import com.bluepath.app.storage.UserStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

public class MarineLlmClient {
    private static final int MAX_HISTORY_MESSAGES = 12;

    private final UserStore store;
    private final BluePathApi api;
    private final List<Map<String, String>> history = new ArrayList<>();
    private String activeQuizSessionId = "";

    public MarineLlmClient(UserStore store) {
        this.store = store;
        this.api = ApiClient.service();
    }

    public boolean isConfigured() {
        return ApiClient.isConfigured() && store.hasCloudSession();
    }

    public List<QuizQuestion> generateQuiz(String tier, UserProfile profile, List<ContentItem> videos) throws Exception {
        int expectedCount = PromotionRules.questionCount(tier);
        if (expectedCount <= 0) throw new IllegalArgumentException("현재 티어는 퀴즈 승급 대상이 아닙니다.");

        List<Map<String, Object>> videoContext = new ArrayList<>();
        for (int i = 0; i < Math.min(24, videos.size()); i++) {
            ContentItem item = videos.get(i);
            Map<String, Object> video = new HashMap<>();
            video.put("title", item.title);
            video.put("topic", item.topic);
            video.put("difficulty", item.difficulty);
            video.put("requiredTier", item.requiredTier);
            video.put("url", item.url);
            videoContext.add(video);
        }

        Response<ApiModels.QuizResponse> response = api.generateQuiz(
                authorization(),
                new ApiModels.QuizRequest(tier, profileMap(profile), videoContext)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IllegalStateException("BluePath AI quiz HTTP " + response.code());
        }

        ApiModels.QuizResponse body = response.body();
        activeQuizSessionId = safe(body.sessionId);
        if (activeQuizSessionId.isEmpty()) throw new IllegalStateException("서버 퀴즈 세션이 생성되지 않았습니다.");
        List<QuizQuestion> result = new ArrayList<>();
        if (body.questions != null) {
            for (int i = 0; i < body.questions.size(); i++) {
                ApiModels.QuizQuestionDto q = body.questions.get(i);
                if (q.options == null || q.options.size() != 4) continue;
                String question = safe(q.question);
                String explanation = safe(q.explanation);
                if (q.sources != null && !q.sources.isEmpty()) {
                    StringBuilder grounded = new StringBuilder(explanation);
                    grounded.append("\n\n근거 자료");
                    for (int sourceIndex = 0; sourceIndex < Math.min(3, q.sources.size()); sourceIndex++) {
                        ApiModels.SourceDto source = q.sources.get(sourceIndex);
                        grounded.append("\n• ").append(safe(source.title));
                        if (!safe(source.organization).isEmpty()) grounded.append(" — ").append(source.organization);
                        if (!safe(source.url).isEmpty()) grounded.append("\n  ").append(source.url);
                    }
                    explanation = grounded.toString();
                }
                if (question.isEmpty()) continue;
                result.add(new QuizQuestion(
                        safe(q.id).isEmpty() ? "gateway-" + System.currentTimeMillis() + "-" + i : q.id,
                        tier,
                        safe(q.topic).isEmpty() ? "해양교육" : q.topic,
                        question,
                        q.options.toArray(new String[0]),
                        q.answerIndex,
                        explanation
                ));
            }
        }
        if (result.size() != expectedCount) {
            throw new IllegalStateException("AI가 필요한 " + expectedCount + "문제를 완전한 형식으로 생성하지 못했습니다.");
        }
        return result;
    }

    public ApiModels.QuizSubmissionResponse submitQuiz(int[] selectedAnswers) throws Exception {
        if (activeQuizSessionId.isEmpty()) throw new IllegalStateException("활성 서버 퀴즈 세션이 없습니다.");
        List<Integer> answers = new ArrayList<>();
        for (int value : selectedAnswers) answers.add(value);
        Response<ApiModels.QuizSubmissionResponse> response = api.submitQuiz(
                authorization(), new ApiModels.QuizSubmissionRequest(activeQuizSessionId, answers)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IllegalStateException("BluePath quiz submit HTTP " + response.code());
        }
        return response.body();
    }

    public String getActiveQuizSessionId() {
        return activeQuizSessionId;
    }

    public void clearQuizSession() {
        activeQuizSessionId = "";
    }

    public String answerAgent(String question, UserProfile profile, String tier,
                              List<ContentItem> recommendations, String promotionManual) throws Exception {
        String safeQuestion = safe(question);
        if (safeQuestion.isEmpty()) throw new IllegalArgumentException("질문을 입력해 주세요.");

        List<Map<String, String>> historySnapshot = historySnapshot();
        Response<ApiModels.AgentResponse> response = api.answerAgent(
                authorization(),
                new ApiModels.AgentRequest(
                        safeQuestion,
                        tier,
                        profileMap(profile),
                        promotionManual,
                        historySnapshot)).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IllegalStateException("BluePath AI agent HTTP " + response.code());
        }

        ApiModels.AgentResponse body = response.body();
        String rawAnswer = safe(body.answer);
        rememberAgentTurn(safeQuestion, rawAnswer);

        StringBuilder answer = new StringBuilder(rawAnswer);
        if (body.sources != null && !body.sources.isEmpty()) {
            answer.append("\n\n근거 자료");
            for (int i = 0; i < Math.min(4, body.sources.size()); i++) {
                ApiModels.SourceDto source = body.sources.get(i);
                answer.append("\n• ").append(safe(source.title));
                if (!safe(source.organization).isEmpty()) answer.append(" — ").append(source.organization);
                if (!safe(source.url).isEmpty()) answer.append("\n  ").append(source.url);
            }
        }
        return answer.toString().trim();
    }

    public synchronized void clearAgentHistory() {
        history.clear();
    }

    private synchronized List<Map<String, String>> historySnapshot() {
        List<Map<String, String>> snapshot = new ArrayList<>();
        for (Map<String, String> message : history) {
            snapshot.add(new HashMap<>(message));
        }
        return snapshot;
    }

    private synchronized void rememberAgentTurn(String question, String answer) {
        addHistoryMessage("user", question);
        if (!answer.isEmpty()) addHistoryMessage("assistant", answer);
        while (history.size() > MAX_HISTORY_MESSAGES) history.remove(0);
    }

    private void addHistoryMessage(String role, String content) {
        Map<String, String> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        history.add(message);
    }

    private Map<String, Object> profileMap(UserProfile profile) {
        Map<String, Object> value = new HashMap<>();
        value.put("ageGroup", profile.ageGroup);
        value.put("interest", profile.interest);
        value.put("goal", profile.goal);
        value.put("level", profile.level);
        value.put("persona", profile.persona);
        value.put("xp", profile.xp);
        return value;
    }

    private String authorization() {
        return store.hasCloudSession() ? "Bearer " + store.getAccessToken() : "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
