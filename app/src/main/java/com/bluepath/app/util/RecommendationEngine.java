package com.bluepath.app.util;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.InstitutionItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.storage.UserStore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RecommendationEngine {
    private RecommendationEngine() {}

    public static String persona(String ageGroup, String goal, String interest) {
        if (ageGroup.contains("초등") || ageGroup.contains("유아")) return "바다 탐험 키즈";
        if (ageGroup.contains("중") || ageGroup.contains("고")) return "진로 탐색 항해자";
        if (ageGroup.contains("대학") || goal.contains("취업") || goal.contains("자격")) return "해양 커리어 빌더";
        if (ageGroup.contains("직장") || goal.contains("직무")) return "블루 리스킬러";
        if (goal.contains("가족")) return "가족 해양 큐레이터";
        return "스마트 해양 탐험가";
    }

    public static List<ContentItem> recommendedContents(UserProfile p, String tier) {
        return recommendedContents(p, tier, null);
    }

    public static List<ContentItem> recommendedContents(UserProfile p, String tier, UserStore store) {
        List<ContentItem> items = new ArrayList<>(DataRepository.contents());
        items.sort(Comparator.comparingInt(c -> -scoreContent(c, p, tier, store)));
        return items;
    }

    public static int scoreContent(ContentItem c, UserProfile p, String tier) {
        return scoreContent(c, p, tier, null);
    }

    public static int scoreContent(ContentItem c, UserProfile p, String tier, UserStore store) {
        int score = 25;
        if (topicMatches(c, p.interest)) score += 28;
        if (c.careerTag.contains(p.interest) || c.title.contains(p.interest)) score += 12;
        if (tierRank(tier) >= tierRank(c.requiredTier)) score += 15; else score -= 8;
        if (p.goal.contains("진로") && !c.careerTag.isEmpty()) score += 8;
        if (p.goal.contains("흥미") && c.difficulty.equals("하")) score += 8;
        if (p.goal.contains("직무") && (c.difficulty.equals("중") || c.difficulty.equals("상"))) score += 8;
        if (store != null) {
            int mastery = store.getSkillMastery(c.topic);
            if (store.getSkillEvidenceCount(c.topic) > 0 && mastery < 65) score += 12;
            if (store.isBookmarked(c.id)) score += 5;
            if (store.getCompletedContentIds().contains(c.id)) score -= 22;
            else if (store.isContentStarted(c.id)) score += 4;
        }
        return clamp(score);
    }

    public static List<String> contentReasons(ContentItem c, UserProfile p, String tier, UserStore store) {
        List<String> reasons = new ArrayList<>();
        if (topicMatches(c, p.interest)) reasons.add("관심 분야 ‘" + p.interest + "’와 연결");
        if (tierRank(tier) >= tierRank(c.requiredTier)) reasons.add("현재 " + PromotionRules.displayName(tier) + " 수준에서 학습 가능");
        else reasons.add(PromotionRules.displayName(c.requiredTier) + " 선행 수준이 필요");
        if (store != null && store.getSkillEvidenceCount(c.topic) > 0) {
            int mastery = store.getSkillMastery(c.topic);
            reasons.add(c.topic + " 숙련도 " + mastery + "점" + (mastery < 65 ? " 보완 우선" : " 유지·심화"));
        }
        if (store != null && store.getCompletedContentIds().contains(c.id)) reasons.add("이미 완료해 복습 항로로 배치");
        else reasons.add("아직 완료하지 않은 새 학습 자료");
        return reasons;
    }

    private static boolean topicMatches(ContentItem c, String interest) {
        String all = c.topic + " " + c.careerTag + " " + c.title;
        if (all.contains(interest) || interest.contains(c.topic)) return true;
        if (interest.contains("선박")) return all.contains("선박") || all.contains("조선") || all.contains("해기사") || all.contains("기관사");
        if (interest.contains("항해")) return all.contains("항해") || all.contains("해기사") || all.contains("해운");
        if (interest.contains("해양환경")) return all.contains("환경") || all.contains("쓰레기") || all.contains("생태");
        if (interest.contains("해양생물")) return all.contains("생물") || all.contains("고래") || all.contains("물고기");
        if (interest.contains("항만")) return all.contains("항만") || all.contains("물류");
        if (interest.contains("안전")) return all.contains("안전") || all.contains("비상") || all.contains("방제");
        return interest.contains("문화") && (all.contains("문화") || all.contains("독도"));
    }

    public static List<ProgramItem> recommendedPrograms(UserProfile p) {
        return recommendedPrograms(p, null);
    }

    public static List<ProgramItem> recommendedPrograms(UserProfile p, UserStore store) {
        List<ProgramItem> items = new ArrayList<>(DataRepository.programs());
        items.sort(Comparator
                .comparingInt((ProgramItem item) -> schedulePriority(item.startDate, item.endDate))
                .thenComparingInt(item -> -scoreProgram(item, p, store)));
        return items;
    }

    public static int scoreProgram(ProgramItem item, UserProfile p) {
        return scoreProgram(item, p, null);
    }

    public static int scoreProgram(ProgramItem item, UserProfile p, UserStore store) {
        int score = 28;
        if (textMatchesInterest(item.topic + " " + item.title + " " + item.description, p.interest)) score += 28;
        if (p.ageGroup.contains("초") && (item.target.contains("어린이") || item.target.contains("가족") || item.target.contains("초등"))) score += 18;
        if ((p.ageGroup.contains("중") || p.ageGroup.contains("고")) && (item.topic.contains("진로") || item.topic.contains("항해") || item.topic.contains("환경"))) score += 14;
        if ((p.ageGroup.contains("성인") || p.ageGroup.contains("직장") || p.ageGroup.contains("대학"))
                && (item.target.contains("성인") || item.target.contains("직장"))) score += 18;
        if (p.goal.contains("체험") && item.method.contains("오프라인")) score += 8;
        if (p.goal.contains("자격") && (item.title.contains("면허") || item.title.contains("자격") || item.title.contains("갱신"))) score += 18;
        if (store != null && store.getSkillEvidenceCount(item.topic) > 0 && store.getSkillMastery(item.topic) < 65) score += 10;
        String status = scheduleStatus(item.startDate, item.endDate);
        if (status.equals("진행 중")) score += 10;
        else if (status.equals("모집 예정")) score += 7;
        else if (status.equals("종료·아카이브")) score -= 30;
        else if (status.equals("데이터 확인 필요")) score -= 18;
        return clamp(score);
    }

    public static List<String> programReasons(ProgramItem item, UserProfile p, UserStore store) {
        List<String> reasons = new ArrayList<>();
        if (textMatchesInterest(item.topic + " " + item.title + " " + item.description, p.interest)) reasons.add("관심 분야와 교육 주제가 일치");
        if (item.target.contains(p.ageGroup) || (p.ageGroup.contains("초") && (item.target.contains("어린이") || item.target.contains("가족")))) reasons.add("연령·대상 조건이 적합");
        if (p.goal.contains("자격") && (item.title.contains("면허") || item.title.contains("자격") || item.title.contains("갱신"))) reasons.add("자격·직무 목표와 직접 연결");
        if (store != null && store.getSkillEvidenceCount(item.topic) > 0) reasons.add(item.topic + " 숙련도 " + store.getSkillMastery(item.topic) + "점 기반");
        reasons.add("일정 상태: " + scheduleStatus(item.startDate, item.endDate));
        reasons.add("근거: " + item.source);
        return reasons;
    }

    public static List<EventItem> recommendedEvents(UserProfile p) {
        List<EventItem> items = new ArrayList<>(DataRepository.events());
        items.sort(Comparator
                .comparingInt((EventItem item) -> schedulePriority(item.startDate, item.endDate))
                .thenComparingInt(item -> -scoreEvent(item, p)));
        return items;
    }

    public static int scoreEvent(EventItem e, UserProfile p) {
        int score = 38;
        if (p.goal.contains("흥미") || p.goal.contains("가족")) score += 18;
        if (e.category.contains("체험") || e.category.contains("공연") || e.category.contains("영화")) score += 12;
        if (textMatchesInterest(e.title + " " + e.description, p.interest)) score += 20;
        if (scheduleStatus(e.startDate, e.endDate).equals("종료·아카이브")) score -= 28;
        return clamp(score);
    }

    public static List<CareerItem> recommendedCareers(UserProfile p) {
        return recommendedCareers(p, UserStore.tierForXp(p.xp), null);
    }

    public static List<CareerItem> recommendedCareers(UserProfile p, String tier, UserStore store) {
        List<CareerItem> items = new ArrayList<>(DataRepository.careers());
        items.sort(Comparator.comparingInt(c -> -scoreCareer(c, p, tier, store)));
        return items;
    }

    public static int scoreCareer(CareerItem c, UserProfile p) {
        return scoreCareer(c, p, UserStore.tierForXp(p.xp), null);
    }

    public static int scoreCareer(CareerItem c, UserProfile p, String tier, UserStore store) {
        int score = 28;
        if (c.field.contains(p.interest) || c.title.contains(p.interest) || c.description.contains(p.interest)) score += 34;
        if (p.goal.contains("진로") || p.goal.contains("취업") || p.goal.contains("직무") || p.goal.contains("자격")) score += 18;
        if (tierRank(tier) >= tierRank(c.recommendedTier)) score += 10;
        for (String unit : c.ncsUnits) if (unit.contains(p.interest)) score += 4;
        if (store != null) {
            int mastery = store.getSkillMastery(c.field);
            if (store.getSkillEvidenceCount(c.field) > 0 && mastery < 65) score += 8;
        }
        return clamp(score);
    }

    public static List<String> careerReasons(CareerItem c, UserProfile p, String tier, UserStore store) {
        List<String> reasons = new ArrayList<>();
        if (c.field.contains(p.interest) || c.title.contains(p.interest) || c.description.contains(p.interest)) reasons.add("관심 분야 ‘" + p.interest + "’와 직무가 연결");
        reasons.add("현재 통합 티어 " + PromotionRules.displayName(tier) + " · 권장 " + PromotionRules.displayName(c.recommendedTier));
        if (store != null && store.getSkillEvidenceCount(c.field) > 0) reasons.add(c.field + " 숙련도 " + store.getSkillMastery(c.field) + "점 기반");
        reasons.add("NCS 핵심 역량 " + Math.min(3, c.ncsUnits.length) + "개 우선 학습");
        return reasons;
    }

    public static List<String> relatedInstitutions(CareerItem career, int limit) {
        List<String> categories = new ArrayList<>();
        String all = career.title + " " + career.field;
        if (all.contains("항해") || all.contains("선박")) {
            categories.add("해운회사"); categories.add("여객선사"); categories.add("교육기관");
        }
        if (all.contains("안전")) {
            categories.add("선급"); categories.add("정부기관"); categories.add("검수검정업체");
        }
        if (all.contains("항만") || all.contains("물류")) {
            categories.add("한국항만물류업체"); categories.add("국제해운대리점업체");
        }
        if (all.contains("환경") || all.contains("생태") || all.contains("교육")) {
            categories.add("유관기관"); categories.add("교육기관"); categories.add("정부기관");
        }
        List<String> result = new ArrayList<>();
        for (InstitutionItem item : DataRepository.institutions()) {
            for (String category : categories) {
                if (item.category.contains(category) && !result.contains(item.name)) {
                    result.add(item.name + " (" + item.category + ")");
                    break;
                }
            }
            if (result.size() >= limit) break;
        }
        return result;
    }

    public static boolean isOnlineProgram(ProgramItem item) {
        return item != null && item.method != null && item.method.contains("온라인");
    }

    public static boolean isOfflineProgram(ProgramItem item) {
        if (item == null) return false;
        // method가 비어 있으면 장소 기반 일정으로 간주해 달력에 남긴다.
        return item.method == null || item.method.trim().isEmpty() || item.method.contains("오프라인");
    }

    public static boolean matchesProgramFilter(ProgramItem item, String query, Collection<String> tags, String statusFilter) {
        if (item == null) return false;
        String haystack = safeText(item.title) + " " + safeText(item.description) + " "
                + safeText(item.topic) + " " + safeText(item.target) + " " + safeText(item.method);
        return matchesQuery(haystack, query)
                && matchesTags(haystack, tags)
                && matchesStatusFilter(item.startDate, item.endDate, statusFilter);
    }

    public static boolean matchesEventFilter(EventItem item, String query, Collection<String> tags, String statusFilter) {
        if (item == null) return false;
        String haystack = safeText(item.title) + " " + safeText(item.description) + " "
                + safeText(item.category) + " " + safeText(item.target);
        return matchesQuery(haystack, query)
                && matchesTags(haystack, tags)
                && matchesStatusFilter(item.startDate, item.endDate, statusFilter);
    }

    public static boolean matchesStatusFilter(String startDate, String endDate, String statusFilter) {
        if (statusFilter == null || statusFilter.trim().isEmpty() || "전체".equals(statusFilter)) return true;
        String status = scheduleStatus(startDate, endDate);
        if ("진행 중".equals(statusFilter)) return "진행 중".equals(status);
        if ("진행 전".equals(statusFilter)) return "모집 예정".equals(status);
        if ("진행 완료".equals(statusFilter)) return "종료·아카이브".equals(status);
        return true;
    }

    public static boolean coversIsoDate(String startDate, String endDate, String isoDate) {
        Date start = parseDate(startDate);
        Date end = parseDate(endDate);
        Date day = parseDate(isoDate);
        if (start == null || end == null || day == null || end.before(start)) return false;
        return !day.before(start) && !day.after(end);
    }

    private static boolean matchesQuery(String haystack, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        for (String token : query.trim().split("\\s+")) {
            if (!haystack.contains(token) && !textMatchesInterest(haystack, token)) return false;
        }
        return true;
    }

    private static boolean matchesTags(String haystack, Collection<String> tags) {
        if (tags == null || tags.isEmpty()) return true;
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) continue;
            if (!haystack.contains(tag) && !textMatchesInterest(haystack, tag)) return false;
        }
        return true;
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }

    public static String scheduleStatus(String startDate, String endDate) {
        Date start = parseDate(startDate);
        Date end = parseDate(endDate);
        if (start == null || end == null || end.before(start)) return "데이터 확인 필요";
        Date today = startOfToday();
        if (today.before(start)) return "모집 예정";
        if (!today.after(end)) return "진행 중";
        return "종료·아카이브";
    }

    public static boolean isArchived(String startDate, String endDate) {
        return "종료·아카이브".equals(scheduleStatus(startDate, endDate));
    }

    private static int schedulePriority(String startDate, String endDate) {
        String status = scheduleStatus(startDate, endDate);
        if (status.equals("진행 중")) return 0;
        if (status.equals("모집 예정")) return 1;
        if (status.equals("데이터 확인 필요")) return 2;
        return 3;
    }

    private static Date parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        format.setLenient(false);
        try {
            String normalized = value.trim();
            if (normalized.length() >= 10) normalized = normalized.substring(0, 10);
            return format.parse(normalized);
        } catch (ParseException ignored) {
            return null;
        }
    }

    private static Date startOfToday() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        try {
            return format.parse(format.format(new Date()));
        } catch (ParseException ignored) {
            return new Date();
        }
    }

    private static boolean textMatchesInterest(String text, String interest) {
        if (text == null || interest == null) return false;
        if (text.contains(interest)) return true;
        if (interest.contains("환경")) return text.contains("환경") || text.contains("생태") || text.contains("오염");
        if (interest.contains("생물")) return text.contains("생물") || text.contains("수족관") || text.contains("고래");
        if (interest.contains("항해")) return text.contains("항해") || text.contains("해기사") || text.contains("레이더");
        if (interest.contains("선박")) return text.contains("선박") || text.contains("기관") || text.contains("조선");
        if (interest.contains("안전")) return text.contains("안전") || text.contains("비상") || text.contains("구명");
        if (interest.contains("항만")) return text.contains("항만") || text.contains("물류");
        return interest.contains("문화") && (text.contains("문화") || text.contains("독도") || text.contains("역사"));
    }

    public static List<QuizQuestion> quizForTier(String tier, String interest) {
        List<QuizQuestion> result = new ArrayList<>();
        for (QuizQuestion q : DataRepository.quizzes()) if (q.tier.equals(tier)) result.add(q);
        result.sort(Comparator.comparingInt(q -> q.topic.contains(interest) ? 0 : 1));
        int count = PromotionRules.questionCount(tier);
        if (count > 0 && result.size() > count) result = new ArrayList<>(result.subList(0, count));

        List<QuizQuestion> balanced = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            QuizQuestion q = result.get(i);
            int shift = i % 4;
            String[] options = new String[4];
            for (int j = 0; j < 4; j++) options[(j + shift) % 4] = q.options[j];
            balanced.add(new QuizQuestion(q.id + "-r" + shift, q.tier, q.topic, q.question,
                    options, (q.answerIndex + shift) % 4, q.explanation));
        }
        return balanced;
    }

    public static String nextAction(UserProfile p, String tier) {
        if (tier.equals("브론즈")) return "10문제를 모두 답한 뒤 7문제 이상 맞히면 " + PromotionRules.displayName("실버") + "로 승급합니다.";
        if (tier.equals("실버")) return "12문제를 모두 답한 뒤 9문제 이상 맞히면 " + PromotionRules.displayName("골드") + "로 승급합니다.";
        if (tier.equals("골드")) return "15문제를 모두 답한 뒤 10문제 이상 맞히면 " + PromotionRules.displayName("플래티넘") + "으로 승급합니다.";
        if (tier.equals("플래티넘")) return "고급 20문제 중 16문제 이상을 맞히고 자격 증빙과 해양 프로젝트 승인을 완료하면 " + PromotionRules.displayName("다이아") + " 인증 항로를 완성합니다.";
        return "전문가 단계입니다. 멘토링과 프로젝트 리뷰로 후배 학습자를 도울 수 있습니다.";
    }

    public static int tierRank(String tier) {
        return PromotionRules.rank(tier);
    }

    public static String answerAgent(String question, UserProfile p, String tier) {
        String q = question == null ? "" : question.toLowerCase();
        if (q.contains("티어") || q.contains("승급") || q.contains("올리")) {
            return "현재 " + PromotionRules.displayName(tier) + " 단계입니다. " + nextAction(p, tier)
                    + " 반복 실패만으로는 XP가 지급되지 않으며, 최초 합격이나 최고점 향상에만 보상이 적용됩니다.";
        }
        if (q.contains("교육") || q.contains("일정") || q.contains("프로그램")) {
            List<ProgramItem> programs = recommendedPrograms(p);
            if (programs.isEmpty()) return "추천할 교육 일정이 없습니다.";
            ProgramItem item = programs.get(0);
            return "추천 교육은 ‘" + item.title + "’입니다. 일정 상태는 " + scheduleStatus(item.startDate, item.endDate)
                    + "이며, 근거 데이터는 " + item.source + "입니다.";
        }
        if (q.contains("직업") || q.contains("진로") || q.contains("ncs") || q.contains("항해사")) {
            CareerItem item = recommendedCareers(p, tier, null).get(0);
            return "추천 진로는 ‘" + item.title + "’입니다. 주요 역량은 " + join(item.ncsUnits, ", ")
                    + "이고, 관련 근무지는 " + join(item.workplaces, ", ") + "입니다.";
        }
        if (q.contains("퀴즈") || q.contains("문제")) {
            return "퀴즈 탭에서 현재 티어용 승급 퀴즈를 생성하세요. 모든 문항을 답한 뒤 최종 채점하며, 오답 주제는 역량 숙련도와 다음 추천에 반영됩니다.";
        }
        if (q.contains("영상") || q.contains("콘텐츠")) {
            ContentItem item = recommendedContents(p, tier).get(0);
            return "추천 영상은 ‘" + item.title + "’입니다. 영상을 연 뒤 최소 학습 시간을 충족하고 핵심 내용을 기록해야 완료와 XP가 인정됩니다.";
        }
        return "BluePath 해양 도메인 에이전트입니다. 교육 추천, 일정, 승급 퀴즈, 해양 직무와 NCS 로드맵을 질문해보세요. 현재 관심 분야는 ‘"
                + p.interest + "’, 목표는 ‘" + p.goal + "’입니다.";
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String join(String[] values, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(sep);
            sb.append(values[i]);
        }
        return sb.toString();
    }
}
