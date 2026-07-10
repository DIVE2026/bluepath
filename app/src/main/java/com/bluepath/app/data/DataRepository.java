package com.bluepath.app.data;

import android.content.Context;

import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;

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
    private static List<QuizQuestion> quizCache;

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

    public static List<ProgramItem> programs() {
        return Arrays.asList(
                new ProgramItem("p-001", "독도의 날 기념프로그램", "전체", "2025-10-25", "2025-10-25", "오프라인", "독도·해양문화", "활동지, 독도 엽서, 독도 만들기, 참여형 퀴즈로 구성된 체험 프로그램"),
                new ProgramItem("p-002", "2025 해양문화아카데미 ‘세상을 바꾼 바다X인물’", "성인", "2025-09-18", "2025-10-30", "온·오프라인", "해양문화", "바다를 매개로 역사적 인물과 세계사의 연결을 학습하는 성인 강좌"),
                new ProgramItem("p-003", "가족 교육 프로그램 ‘잠수정 이야기’", "가족", "2026-02-07", "2026-02-08", "오프라인", "선박·해양문화", "초등학생 고학년 포함 가족 대상 잠수정 내부 탐방형 교육"),
                new ProgramItem("p-004", "기획전시 [수군, 해전] 연계 대면프로그램", "가족", "2025-09-13", "2025-09-27", "오프라인", "해양역사", "활동지와 수군 투구 만들기 체험 중심 가족 프로그램"),
                new ProgramItem("p-005", "교과서 안 해양박물관: 바다를 지켜줘!", "어린이", "2025-04-21", "2025-09-23", "오프라인", "해양환경", "초등학생 고학년 대상 교과 연계 해양환경 교육"),
                new ProgramItem("p-006", "제3기 해양문화 교육사 양성 프로그램", "전문가", "2024-06-19", "2024-08-21", "오프라인", "해양교육", "관련 전공자와 교육 경험자를 대상으로 해양문화 교육 역량 양성"),
                new ProgramItem("p-007", "3급면허취득원격교육(상선항해)", "전문가", "2025-01-01", "2025-12-31", "온라인", "항해·자격", "해기사 면허시험 합격자를 위한 원격교육 과정"),
                new ProgramItem("p-008", "기초안전교육", "성인/직장인", "2025-01-06", "2025-01-10", "오프라인", "해양안전", "국제항해 승선자를 위한 선박 구조, 구명, 통신, 소화, 생존 이론 교육")
        );
    }

    public static List<EventItem> events() {
        return Arrays.asList(
                new EventItem("e-001", "월드베스트 매직 콘서트", "2012-07-09", "2012-07-31", "전체", "공연", "국가대표 마술사들이 펼치는 마술 공연"),
                new EventItem("e-002", "사운드 오브 매직아트", "2012-08-01", "2012-11-30", "전체", "공연", "사운드와 마술, 마임이 어우러진 퍼포먼스"),
                new EventItem("e-003", "해로와 미로의 시간탐험대", "2012-12-01", "2012-12-31", "전체", "어린이 공연", "마술과 뮤지컬이 어우러진 어린이 마술뮤지컬"),
                new EventItem("e-004", "[4D영상관] 4D 독도영상", "2012-08-15", "2012-08-15", "전체", "체험", "독도 주변 바다의 해양현상과 생태계를 실감형 영상으로 체험"),
                new EventItem("e-005", "무료영화상영 ‘해양가족극장’", "2012-09-08", "2012-09-29", "전체", "영화", "가족이 즐길 수 있는 해양·자연 테마 영화 상영"),
                new EventItem("e-006", "남극의 눈물 제작 체험담 강연", "2013-04-11", "2013-04-11", "전체", "강연", "해양·극지 다큐멘터리 제작 과정과 현장 경험 공유")
        );
    }

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
