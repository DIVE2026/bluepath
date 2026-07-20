package com.bluepath.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.ProgramItem;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecommendationEngineScheduleFilterTest {

    private static final List<String> NO_TAGS = Collections.emptyList();

    private static String daysFromToday(int offset) {
        Calendar calendar = Calendar.getInstance(Locale.KOREA);
        calendar.add(Calendar.DAY_OF_MONTH, offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(calendar.getTime());
    }

    private static ProgramItem program(String title, String topic, String target, String method,
                                       String startDate, String endDate) {
        return new ProgramItem("p1", title, target, startDate, endDate, method, topic, "설명", "테스트");
    }

    @Test
    public void onlineAndOfflineSplitFollowsMethod() {
        ProgramItem offline = program("독도 교실", "독도·해양문화", "가족", "오프라인", daysFromToday(-1), daysFromToday(1));
        ProgramItem online = program("항해 이론", "항해", "성인", "온라인", daysFromToday(-1), daysFromToday(1));
        ProgramItem hybrid = program("선박 실습", "선박", "성인", "온라인·오프라인", daysFromToday(-1), daysFromToday(1));

        assertTrue(RecommendationEngine.isOfflineProgram(offline));
        assertFalse(RecommendationEngine.isOnlineProgram(offline));
        assertTrue(RecommendationEngine.isOnlineProgram(online));
        assertFalse(RecommendationEngine.isOfflineProgram(online));
        assertTrue(RecommendationEngine.isOnlineProgram(hybrid));
        assertTrue(RecommendationEngine.isOfflineProgram(hybrid));
    }

    @Test
    public void emptyMethodCountsAsOffline() {
        ProgramItem unknown = program("갯벌 체험", "해양환경", "가족", "", daysFromToday(-1), daysFromToday(1));
        assertTrue(RecommendationEngine.isOfflineProgram(unknown));
        assertFalse(RecommendationEngine.isOnlineProgram(unknown));
    }

    @Test
    public void queryMatchesAcrossFieldsAndSynonyms() {
        ProgramItem item = program("바다 생태 탐구", "해양환경", "초등학생 포함 가족", "오프라인", daysFromToday(-1), daysFromToday(1));
        assertTrue(RecommendationEngine.matchesProgramFilter(item, "생태", NO_TAGS, "전체"));
        // "해양환경" 자체는 본문에 없어도 환경 동의어 규칙으로 매칭되어야 한다.
        assertTrue(RecommendationEngine.matchesProgramFilter(item, "해양환경", NO_TAGS, "전체"));
        assertFalse(RecommendationEngine.matchesProgramFilter(item, "항만 물류", NO_TAGS, "전체"));
        assertTrue(RecommendationEngine.matchesProgramFilter(item, "", NO_TAGS, "전체"));
        assertTrue(RecommendationEngine.matchesProgramFilter(item, null, NO_TAGS, "전체"));
    }

    @Test
    public void tagsCombineWithAndSemantics() {
        ProgramItem item = program("가족 바다 생태 교실", "해양환경", "초등학생 포함 가족", "오프라인", daysFromToday(-1), daysFromToday(1));
        assertTrue(RecommendationEngine.matchesProgramFilter(item, "", Arrays.asList("해양환경", "가족"), "전체"));
        assertFalse(RecommendationEngine.matchesProgramFilter(item, "", Arrays.asList("해양환경", "항해"), "전체"));
    }

    @Test
    public void statusFilterUsesScheduleStatus() {
        ProgramItem running = program("진행 중 교육", "항해", "성인", "오프라인", daysFromToday(-3), daysFromToday(3));
        ProgramItem upcoming = program("모집 예정 교육", "항해", "성인", "오프라인", daysFromToday(5), daysFromToday(9));
        ProgramItem archived = program("종료 교육", "항해", "성인", "오프라인", daysFromToday(-9), daysFromToday(-5));

        assertTrue(RecommendationEngine.matchesProgramFilter(running, "", NO_TAGS, "진행 중"));
        assertFalse(RecommendationEngine.matchesProgramFilter(upcoming, "", NO_TAGS, "진행 중"));
        assertTrue(RecommendationEngine.matchesProgramFilter(upcoming, "", NO_TAGS, "진행 전"));
        assertFalse(RecommendationEngine.matchesProgramFilter(running, "", NO_TAGS, "진행 전"));
        assertTrue(RecommendationEngine.matchesProgramFilter(archived, "", NO_TAGS, "진행 완료"));
        assertFalse(RecommendationEngine.matchesProgramFilter(running, "", NO_TAGS, "진행 완료"));
        assertTrue(RecommendationEngine.matchesProgramFilter(archived, "", NO_TAGS, "전체"));
    }

    @Test
    public void eventFilterMatchesCategoryAndStatus() {
        EventItem event = new EventItem("e1", "바다 영화제", daysFromToday(-1), daysFromToday(1),
                "누구나", "영화·영상", "해양 다큐 상영", "테스트");
        assertTrue(RecommendationEngine.matchesEventFilter(event, "영화", NO_TAGS, "전체"));
        assertTrue(RecommendationEngine.matchesEventFilter(event, "", NO_TAGS, "진행 중"));
        assertFalse(RecommendationEngine.matchesEventFilter(event, "항만", NO_TAGS, "전체"));
    }

    @Test
    public void coversIsoDateIncludesBoundaries() {
        assertTrue(RecommendationEngine.coversIsoDate("2026-07-10", "2026-07-20", "2026-07-10"));
        assertTrue(RecommendationEngine.coversIsoDate("2026-07-10", "2026-07-20", "2026-07-20"));
        assertTrue(RecommendationEngine.coversIsoDate("2026-07-10", "2026-07-20", "2026-07-15"));
        assertFalse(RecommendationEngine.coversIsoDate("2026-07-10", "2026-07-20", "2026-07-09"));
        assertFalse(RecommendationEngine.coversIsoDate("2026-07-10", "2026-07-20", "2026-07-21"));
        assertFalse(RecommendationEngine.coversIsoDate("", "2026-07-20", "2026-07-15"));
        assertFalse(RecommendationEngine.coversIsoDate("2026-07-20", "2026-07-10", "2026-07-15"));
    }

    @Test
    public void singleDayProgramCoversOnlyThatDay() {
        String day = daysFromToday(0);
        assertTrue(RecommendationEngine.coversIsoDate(day, day, day));
        assertFalse(RecommendationEngine.coversIsoDate(day, day, daysFromToday(1)));
    }
}
