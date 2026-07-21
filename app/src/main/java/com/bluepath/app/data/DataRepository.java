package com.bluepath.app.data;

import android.content.Context;

import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.InstitutionItem;
import com.bluepath.app.model.PaperItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.network.ApiModels;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DataRepository {
    private static Context appContext;
    private static List<ContentItem> contentCache;
    private static List<ProgramItem> programCache;
    private static List<EventItem> eventCache;
    private static List<PaperItem> paperCache;
    private static List<QuizQuestion> quizCache;
    private static List<InstitutionItem> institutionCache;
    private static List<String> surveyInsightCache;
    private static int surveySampleSize;

    private DataRepository() {}

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
    }

    public static List<ContentItem> contents() {
        if (contentCache == null) contentCache = loadContents();
        return new ArrayList<>(contentCache);
    }

    private static List<ContentItem> loadContents() {
        if (appContext == null) return Collections.emptyList();
        List<ContentItem> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAsset("seed_videos.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                list.add(new ContentItem(
                        item.getString("id"),
                        item.getString("title"),
                        item.optString("source", "첨부 스프레드시트"),
                        item.getString("url"),
                        item.getString("difficulty"),
                        item.getString("requiredTier"),
                        item.optString("topic", "해양교육"),
                        item.optString("careerTag", "해양교육"),
                        item.optInt("minutes", 0)
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    public static synchronized List<PaperItem> papers() {
        if (paperCache == null) paperCache = loadPapers();
        return new ArrayList<>(paperCache);
    }

    private static List<PaperItem> loadPapers() {
        if (appContext == null) return Collections.emptyList();
        List<PaperItem> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAsset("seed_papers.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                list.add(new PaperItem(
                        item.getString("id"), item.optString("title", ""), item.optString("authors", ""),
                        item.optString("year", ""), item.optString("source", ""), item.optString("url", ""),
                        item.optString("topic", "해양교육"), item.optString("abstract", ""), item.optString("doi", "")
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    public static List<QuizQuestion> quizzes() {
        if (quizCache == null) quizCache = loadQuizzes();
        return new ArrayList<>(quizCache);
    }

    private static List<QuizQuestion> loadQuizzes() {
        if (appContext == null) return Collections.emptyList();
        List<QuizQuestion> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAsset("fallback_quizzes.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                JSONArray optionsJson = item.getJSONArray("options");
                if (optionsJson.length() != 4) continue;
                String[] options = new String[4];
                for (int j = 0; j < 4; j++) options[j] = optionsJson.getString(j);
                list.add(new QuizQuestion(
                        item.getString("id"),
                        item.getString("tier"),
                        item.optString("topic", "해양교육"),
                        item.getString("question"),
                        options,
                        item.getInt("answerIndex"),
                        item.getString("explanation")
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    private static String readAsset(String fileName) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream input = appContext.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        return builder.toString();
    }

    public static synchronized List<ProgramItem> programs() {
        if (programCache == null) programCache = loadPrograms();
        return new ArrayList<>(programCache);
    }

    private static List<ProgramItem> loadPrograms() {
        if (appContext == null) return Collections.emptyList();
        List<ProgramItem> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAsset("seed_programs.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                list.add(new ProgramItem(
                        item.getString("id"), item.getString("title"), item.optString("target", "전체"),
                        item.optString("startDate", ""), item.optString("endDate", ""),
                        item.optString("method", "오프라인"), item.optString("topic", "해양교육"),
                        item.optString("description", ""), item.optString("source", "제공 데이터"),
                        item.optString("applicationUrl", item.optString("url", ""))
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    public static synchronized List<EventItem> events() {
        if (eventCache == null) eventCache = loadEvents();
        return new ArrayList<>(eventCache);
    }

    private static List<EventItem> loadEvents() {
        if (appContext == null) return Collections.emptyList();
        List<EventItem> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAsset("seed_events.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                list.add(new EventItem(
                        item.getString("id"), item.getString("title"), item.optString("startDate", ""),
                        item.optString("endDate", ""), item.optString("target", "전체"),
                        item.optString("category", "행사"), item.optString("description", ""),
                        item.optString("source", "제공 데이터"), item.optString("applicationUrl", item.optString("url", ""))
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    public static synchronized List<InstitutionItem> institutions() {
        if (institutionCache == null) institutionCache = loadInstitutions();
        return new ArrayList<>(institutionCache);
    }

    private static List<InstitutionItem> loadInstitutions() {
        if (appContext == null) return Collections.emptyList();
        List<InstitutionItem> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readAsset("seed_institutions.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                list.add(new InstitutionItem(
                        item.getString("id"), item.optString("category", "유관기관"),
                        item.optString("name", ""), item.optString("source", "해양기관 현황 데이터")
                ));
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        return list;
    }

    public static synchronized List<String> surveyInsights() {
        if (surveyInsightCache == null) loadSurveyInsights();
        return new ArrayList<>(surveyInsightCache == null ? Collections.emptyList() : surveyInsightCache);
    }

    public static synchronized int surveySampleSize() {
        if (surveyInsightCache == null) loadSurveyInsights();
        return surveySampleSize;
    }

    private static void loadSurveyInsights() {
        surveyInsightCache = new ArrayList<>();
        surveySampleSize = 0;
        if (appContext == null) return;
        try {
            JSONObject root = new JSONObject(readAsset("survey_insights.json"));
            surveySampleSize = root.optInt("sampleSize", 0);
            JSONArray metrics = root.optJSONArray("metrics");
            if (metrics == null) return;
            for (int i = 0; i < metrics.length(); i++) {
                JSONObject metric = metrics.getJSONObject(i);
                int value = metric.optInt("value", 0);
                int total = metric.optInt("total", surveySampleSize);
                String label = metric.optString("label", "관람객 인사이트");
                String insight = metric.optString("insight", "");
                surveyInsightCache.add(label + " " + value + "/" + total + " · " + insight);
            }
        } catch (Exception ignored) {
            surveyInsightCache.clear();
        }
    }

    public static synchronized void applyRemoteCatalog(List<ApiModels.ContentDto> remote) {
        if (remote == null) return;
        if (contentCache == null) contentCache = loadContents();
        if (programCache == null) programCache = loadPrograms();
        if (eventCache == null) eventCache = loadEvents();
        if (paperCache == null) paperCache = loadPapers();
        for (ApiModels.ContentDto item : remote) {
            if (item == null || item.id == null || item.id.trim().isEmpty()) continue;
            if ("video".equals(item.contentType)) {
                replaceContent(new ContentItem(item.id, safe(item.title), safe(item.source), safe(item.url),
                        safeOr(item.difficulty, "중"), safeOr(item.requiredTier, "브론즈"),
                        safeOr(item.topic, "해양교육"), safe(item.careerTag), item.minutes));
            } else if ("program".equals(item.contentType) || "schedule".equals(item.contentType)) {
                replaceProgram(new ProgramItem(item.id, safe(item.title), safeOr(item.target, "전체"),
                        safe(item.startAt), safe(item.endAt), safeOr(item.method, "오프라인"),
                        safeOr(item.topic, "해양교육"), safe(item.description), safe(item.source), safe(item.url)));
            } else if ("event".equals(item.contentType)) {
                replaceEvent(new EventItem(item.id, safe(item.title), safe(item.startAt), safe(item.endAt),
                        safeOr(item.target, "전체"), safeOr(item.category, "행사"), safe(item.description), safe(item.source), safe(item.url)));
            } else if ("paper".equals(item.contentType)) {
                replacePaper(new PaperItem(item.id, safe(item.title), safe(item.authors), safe(item.year),
                        safe(item.source), safe(item.url), safeOr(item.topic, "해양교육"), safe(item.description), safe(item.doi)));
            }
        }
    }

    private static void replaceContent(ContentItem value) {
        for (int i = 0; i < contentCache.size(); i++) if (contentCache.get(i).id.equals(value.id)) { contentCache.set(i, value); return; }
        contentCache.add(value);
    }

    private static void replaceProgram(ProgramItem value) {
        for (int i = 0; i < programCache.size(); i++) if (programCache.get(i).id.equals(value.id)) { programCache.set(i, value); return; }
        programCache.add(value);
    }

    private static void replaceEvent(EventItem value) {
        for (int i = 0; i < eventCache.size(); i++) if (eventCache.get(i).id.equals(value.id)) { eventCache.set(i, value); return; }
        eventCache.add(value);
    }

    private static void replacePaper(PaperItem value) {
        for (int i = 0; i < paperCache.size(); i++) if (paperCache.get(i).id.equals(value.id)) { paperCache.set(i, value); return; }
        paperCache.add(value);
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String safeOr(String value, String fallback) { return value == null || value.trim().isEmpty() ? fallback : value; }

    public static List<CareerItem> careers() {
        return Arrays.asList(
                new CareerItem("c-001", "항해사", "항해·선박", "선박의 안전한 항해와 위치결정, 항해당직, 선박조종을 담당합니다.",
                        new String[]{"선위결정", "항해당직", "선박조종", "항해장비운용", "비상대응"},
                        new String[]{"해운회사", "여객선사", "해양수산연수원", "정부기관"}, "골드"),
                new CareerItem("c-002", "해양환경 교육 기획자", "해양환경", "해양오염, 생태계, 기후변화 이슈를 교육 콘텐츠와 체험 프로그램으로 설계합니다.",
                        new String[]{"해양환경 이해", "교육 콘텐츠 기획", "관람객 소통", "프로그램 평가"},
                        new String[]{"국립해양박물관", "해양환경공단", "해양교육기관", "유관기관·단체"}, "실버"),
                new CareerItem("c-003", "해양생태 해설사", "해양생물", "해양생물과 생태계를 대중에게 쉽게 설명하고 체험 교육을 운영합니다.",
                        new String[]{"해양생물 분류", "해양생태계 해설", "전시 해설", "안전한 체험 운영"},
                        new String[]{"박물관", "해양생물자원관", "교육기관", "전시기관"}, "실버"),
                new CareerItem("c-004", "선박안전관리자", "해양안전", "선박 운항 중 위험요소를 예방하고 비상대응 체계를 관리합니다.",
                        new String[]{"선박안전관리", "비상대응", "구명설비 운용", "소화·생존 이론"},
                        new String[]{"선급", "정부기관", "해운회사", "검수검정업체"}, "플래티넘"),
                new CareerItem("c-005", "항만물류 전문가", "항만·물류", "항만 물류 흐름을 관리하고 해운·항만 산업의 운영 효율을 높입니다.",
                        new String[]{"화물관리", "항만운영", "해운물류 이해", "데이터 기반 운영"},
                        new String[]{"한국항만물류업체", "해운회사", "국제해운대리점", "유관기관·단체"}, "골드")
        );
    }
}
