package com.bluepath.app.util;

import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.storage.UserStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MarineLlmClient {
    private static final String MARINE_SYSTEM_PROMPT =
            "당신은 BluePath의 해양교육 전문 LLM이다. 해양환경, 해양생물, 항해, 선박기관, "
                    + "해기사, 해운, 조선해양, 스마트항만, 자율운항선박, 해양안전과 NCS 진로교육에 특화되어 있다. "
                    + "한국어로 정확하고 학습자 수준에 맞게 답한다. 제공된 근거를 우선 사용하고 불확실한 사실을 단정하지 않는다. "
                    + "안전·자격·법규 관련 내용은 공식 기관 확인이 필요함을 명시한다.";

    private final UserStore store;

    public MarineLlmClient(UserStore store) {
        this.store = store;
    }

    public boolean isConfigured() {
        return store.hasLlmConfig();
    }

    public List<QuizQuestion> generateQuiz(String tier, UserProfile profile, List<ContentItem> videos) throws Exception {
        int count = PromotionRules.questionCount(tier);
        if (count <= 0) throw new IllegalArgumentException("현재 티어는 퀴즈 승급 대상이 아닙니다.");

        StringBuilder anchors = new StringBuilder();
        int included = 0;
        for (ContentItem item : videos) {
            if (item.requiredTier.equals(tier) || PromotionRules.rank(item.requiredTier) <= PromotionRules.rank(tier)) {
                anchors.append("- ").append(item.title).append(" / ").append(item.topic).append('\n');
                if (++included >= 18) break;
            }
        }

        String userPrompt = "현재 티어: " + tier + "\n"
                + "사용자: " + profile.ageGroup + ", 관심=" + profile.interest + ", 목표=" + profile.goal + ", 수준=" + profile.level + "\n"
                + "승급 기준: " + PromotionRules.quizRule(tier) + "\n"
                + "학습 영상 주제:\n" + anchors + "\n"
                + "정확히 " + count + "개의 승급 문제를 생성하라. 모든 문제는 보기 4개인 4지선다여야 한다. "
                + "정답 위치가 한 번호에 몰리지 않게 분산하고, 질문끼리 중복하지 않는다. "
                + "선택 즉시 피드백하지 않고 최종 채점에 사용할 수 있도록 각 문항에 정답 인덱스와 해설을 포함한다. "
                + "다음 JSON 객체만 출력한다: {\"questions\":[{\"topic\":\"...\",\"question\":\"...\","
                + "\"options\":[\"...\",\"...\",\"...\",\"...\"],\"answerIndex\":0,\"explanation\":\"...\"}]}";

        String response = callChat(MARINE_SYSTEM_PROMPT + " 퀴즈 출제 시 객관적이고 교육적으로 타당한 내용만 사용한다.", userPrompt, 0.55);
        return parseQuizResponse(response, tier, count);
    }

    public String answerAgent(String question, UserProfile profile, String tier,
                              List<ContentItem> recommendations, String promotionManual) throws Exception {
        StringBuilder contentContext = new StringBuilder();
        for (int i = 0; i < Math.min(5, recommendations.size()); i++) {
            ContentItem item = recommendations.get(i);
            contentContext.append(i + 1).append(". ").append(item.title)
                    .append(" (난도 ").append(item.difficulty).append(", 분야 ").append(item.topic).append(")\n");
        }
        String userPrompt = "사용자 프로필: " + profile.ageGroup + ", 관심=" + profile.interest
                + ", 목표=" + profile.goal + ", 수준=" + profile.level + ", 현재 티어=" + tier + "\n"
                + "승급 매뉴얼:\n" + promotionManual + "\n"
                + "추천 가능한 영상:\n" + contentContext + "\n"
                + "사용자 질문: " + question + "\n"
                + "앱 안에서 바로 실행할 다음 행동을 포함해 3~7문장으로 답하라. 모르는 최신 일정이나 법규는 추측하지 말라.";
        return callChat(MARINE_SYSTEM_PROMPT, userPrompt, 0.35).trim();
    }

    private String callChat(String systemPrompt, String userPrompt, double temperature) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("LLM endpoint/model is not configured.");
        URL url = new URL(normalizedEndpoint(store.getLlmEndpoint()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (!store.getLlmApiKey().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + store.getLlmApiKey());
        }

        JSONObject payload = new JSONObject();
        payload.put("model", store.getLlmModel());
        payload.put("temperature", temperature);
        payload.put("max_tokens", 7000);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userPrompt));
        payload.put("messages", messages);

        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readAll(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("LLM HTTP " + code + ": " + response);

        JSONObject root = new JSONObject(response);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IllegalStateException("LLM 응답에 choices가 없습니다.");
        Object content = choices.getJSONObject(0).getJSONObject("message").get("content");
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            StringBuilder builder = new StringBuilder();
            JSONArray parts = (JSONArray) content;
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part != null) builder.append(part.optString("text"));
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    private List<QuizQuestion> parseQuizResponse(String raw, String tier, int expectedCount) throws Exception {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }

        JSONArray questions;
        int objectStart = cleaned.indexOf('{');
        int objectEnd = cleaned.lastIndexOf('}');
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');
        if (objectStart >= 0 && objectEnd > objectStart) {
            questions = new JSONObject(cleaned.substring(objectStart, objectEnd + 1)).getJSONArray("questions");
        } else if (arrayStart >= 0 && arrayEnd > arrayStart) {
            questions = new JSONArray(cleaned.substring(arrayStart, arrayEnd + 1));
        } else {
            throw new IllegalStateException("퀴즈 JSON을 찾을 수 없습니다.");
        }

        List<QuizQuestion> result = new ArrayList<>();
        for (int i = 0; i < questions.length() && result.size() < expectedCount; i++) {
            JSONObject q = questions.getJSONObject(i);
            JSONArray optionsJson = q.getJSONArray("options");
            int answerIndex = q.getInt("answerIndex");
            if (optionsJson.length() != 4 || answerIndex < 0 || answerIndex > 3) continue;
            String[] options = new String[4];
            for (int j = 0; j < 4; j++) options[j] = optionsJson.getString(j);
            String question = q.getString("question").trim();
            String explanation = q.getString("explanation").trim();
            if (question.isEmpty() || explanation.isEmpty()) continue;
            result.add(new QuizQuestion(
                    "llm-" + System.currentTimeMillis() + "-" + i,
                    tier,
                    q.optString("topic", "해양교육"),
                    question,
                    options,
                    answerIndex,
                    explanation
            ));
        }
        if (result.size() != expectedCount) {
            throw new IllegalStateException("LLM이 " + expectedCount + "문제를 완전한 형식으로 생성하지 못했습니다. 생성 수: " + result.size());
        }
        return result;
    }

    private String normalizedEndpoint(String endpoint) {
        String value = endpoint.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/chat/completions")) return value;
        if (value.endsWith("/v1")) return value + "/chat/completions";
        return value + "/v1/chat/completions";
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
