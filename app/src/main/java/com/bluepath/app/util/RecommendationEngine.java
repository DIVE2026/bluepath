package com.bluepath.app.util;

import com.bluepath.app.data.DataRepository;
import com.bluepath.app.model.CareerItem;
import com.bluepath.app.model.ContentItem;
import com.bluepath.app.model.EventItem;
import com.bluepath.app.model.ProgramItem;
import com.bluepath.app.model.QuizQuestion;
import com.bluepath.app.model.UserProfile;
import com.bluepath.app.storage.UserStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecommendationEngine {
    public static String persona(String ageGroup, String goal, String interest) {
        if (ageGroup.contains("초등") || ageGroup.contains("유아")) return "바다 탐험 키즈";
        if (ageGroup.contains("중") || ageGroup.contains("고")) return "진로 탐색 항해자";
        if (ageGroup.contains("대학") || goal.contains("취업") || goal.contains("자격")) return "해양 커리어 빌더";
        if (ageGroup.contains("직장") || goal.contains("직무")) return "블루 리스킬러";
        if (goal.contains("가족")) return "가족 해양 큐레이터";
        return "스마트 해양 탐험가";
    }

    public static List<ContentItem> recommendedContents(UserProfile p, String tier) {
        List<ContentItem> items = new ArrayList<>(DataRepository.contents());
        items.sort(Comparator.comparingInt(c -> -scoreContent(c, p, tier)));
        return items;
    }

    public static int scoreContent(ContentItem c, UserProfile p, String tier) {
        int score = 30;
        if (c.topic.contains(p.interest) || p.interest.contains(c.topic)) score += 30;
        if (c.careerTag.contains(p.interest) || c.title.contains(p.interest)) score += 15;
        if (tierRank(tier) >= tierRank(c.requiredTier)) score += 15;
        if (p.goal.contains("진로") && !c.careerTag.isEmpty()) score += 10;
        if (p.goal.contains("흥미") && c.difficulty.equals("하")) score += 10;
        if (p.goal.contains("직무") && (c.difficulty.equals("중") || c.difficulty.equals("상"))) score += 10;
        return Math.min(score, 100);
    }

    public static List<ProgramItem> recommendedPrograms(UserProfile p) {
        List<ProgramItem> items = new ArrayList<>(DataRepository.programs());
        items.sort(Comparator.comparingInt(program -> -scoreProgram(program, p)));
        return items;
    }

    public static int scoreProgram(ProgramItem pItem, UserProfile p) {
        int score = 30;
        if (pItem.topic.contains(p.interest) || pItem.title.contains(p.interest)) score += 30;
        if (p.ageGroup.contains("초") && (pItem.target.contains("어린이") || pItem.target.contains("가족"))) score += 20;
        if ((p.ageGroup.contains("중") || p.ageGroup.contains("고")) && (pItem.topic.contains("진로") || pItem.topic.contains("항해") || pItem.topic.contains("해양환경"))) score += 15;
        if ((p.ageGroup.contains("성인") || p.ageGroup.contains("직장")) && (pItem.target.contains("성인") || pItem.target.contains("전문가"))) score += 20;
        if (p.goal.contains("체험") && pItem.method.contains("오프라인")) score += 10;
        if (p.goal.contains("자격") && pItem.title.contains("면허")) score += 20;
        return Math.min(score, 100);
    }

    public static List<EventItem> recommendedEvents(UserProfile p) {
        List<EventItem> items = new ArrayList<>(DataRepository.events());
        items.sort(Comparator.comparingInt(e -> -scoreEvent(e, p)));
        return items;
    }

    public static int scoreEvent(EventItem e, UserProfile p) {
        int score = 40;
        if (p.goal.contains("흥미") || p.goal.contains("가족")) score += 20;
        if (e.category.contains("체험") || e.category.contains("공연") || e.category.contains("영화")) score += 15;
        if (e.title.contains(p.interest) || e.description.contains(p.interest)) score += 20;
        return Math.min(score, 100);
    }

    public static List<CareerItem> recommendedCareers(UserProfile p) {
        List<CareerItem> items = new ArrayList<>(DataRepository.careers());
        items.sort(Comparator.comparingInt(c -> -scoreCareer(c, p)));
        return items;
    }

    public static int scoreCareer(CareerItem c, UserProfile p) {
        int score = 30;
        if (c.field.contains(p.interest) || c.title.contains(p.interest) || c.description.contains(p.interest)) score += 35;
        if (p.goal.contains("진로") || p.goal.contains("취업") || p.goal.contains("직무")) score += 20;
        if (tierRank(UserStore.tierForXp(p.xp)) >= tierRank(c.recommendedTier)) score += 10;
        for (String unit : c.ncsUnits) if (unit.contains(p.interest)) score += 5;
        return Math.min(score, 100);
    }

    public static List<QuizQuestion> quizForTier(String tier, String interest) {
        List<QuizQuestion> result = new ArrayList<>();
        for (QuizQuestion q : DataRepository.quizzes()) {
            if (tierRank(q.tier) <= tierRank(tier) + 1) result.add(q);
        }
        result.sort(Comparator.comparingInt(q -> q.topic.contains(interest) ? 0 : 1));
        return result;
    }

    public static String nextAction(UserProfile p, String tier) {
        if (tier.equals("브론즈")) return "기초 영상 3개 또는 브론즈 퀴즈 70점 이상으로 실버 진입을 노려보세요.";
        if (tier.equals("실버")) return "관심 분야 중급 영상과 NCS 기초 퀴즈를 완료하면 골드 로드맵이 열립니다.";
        if (tier.equals("골드")) return "관심 직무를 선택하고 심화 퀴즈 80점 이상, 자격·교육 일정을 찜해 플래티넘을 준비하세요.";
        if (tier.equals("플래티넘")) return "자격증, 교육 수료, 경력 인증 중 하나를 등록하면 다이아 후보가 됩니다.";
        return "전문가 단계입니다. 멘토링·프로젝트 리뷰 기능을 통해 후배 학습자를 도울 수 있습니다.";
    }

    public static int tierRank(String tier) {
        switch (tier) {
            case "브론즈": return 1;
            case "실버": return 2;
            case "골드": return 3;
            case "플래티넘": return 4;
            case "다이아": return 5;
            default: return 1;
        }
    }

    public static String answerAgent(String question, UserProfile p, String tier) {
        String q = question == null ? "" : question.toLowerCase();
        if (q.contains("티어") || q.contains("승급") || q.contains("올리")) {
            return "현재 " + tier + " 단계입니다. " + nextAction(p, tier) + " 영상 시청만으로는 최대 30%까지 반영되고, 퀴즈·미션·일정 참여가 더 크게 반영됩니다.";
        }
        if (q.contains("교육") || q.contains("일정") || q.contains("프로그램")) {
            ProgramItem item = recommendedPrograms(p).get(0);
            return "추천 교육은 ‘" + item.title + "’입니다. 대상은 " + item.target + ", 기간은 " + item.startDate + "~" + item.endDate + "입니다. 추천 이유는 관심 분야 ‘" + p.interest + "’와 학습 목적 ‘" + p.goal + "’에 가장 가깝기 때문입니다.";
        }
        if (q.contains("직업") || q.contains("진로") || q.contains("ncs") || q.contains("항해사")) {
            CareerItem item = recommendedCareers(p).get(0);
            return "관심 분야 기준 추천 진로는 ‘" + item.title + "’입니다. 주요 NCS·역량은 " + join(item.ncsUnits, ", ") + "이고, 관련 근무지는 " + join(item.workplaces, ", ") + "입니다.";
        }
        if (q.contains("퀴즈") || q.contains("문제")) {
            return "퀴즈/티어 탭에서 현재 티어에 맞는 승급 퀴즈를 풀 수 있습니다. 일정 점수 이상이면 영상을 다 보지 않아도 성장 게이지가 빠르게 찹니다.";
        }
        if (q.contains("영상") || q.contains("콘텐츠")) {
            ContentItem item = recommendedContents(p, tier).get(0);
            return "추천 영상은 ‘" + item.title + "’입니다. 난도는 " + item.difficulty + ", 권장 티어는 " + item.requiredTier + "입니다.";
        }
        return "저는 BluePath AI Agent입니다. 교육 추천, 일정 확인, 티어 승급, 퀴즈, 진로 로드맵에 대해 질문해보세요. 현재 프로필 기준으로 관심 분야는 ‘" + p.interest + "’, 목표는 ‘" + p.goal + "’입니다.";
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
