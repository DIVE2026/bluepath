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
        List<ContentItem> items = new ArrayList<>(DataRepository.contents());
        items.sort(Comparator.comparingInt(c -> -scoreContent(c, p, tier)));
        return items;
    }

    public static int scoreContent(ContentItem c, UserProfile p, String tier) {
        int score = 30;
        if (topicMatches(c, p.interest)) score += 30;
        if (c.careerTag.contains(p.interest) || c.title.contains(p.interest)) score += 15;
        if (tierRank(tier) >= tierRank(c.requiredTier)) score += 15;
        if (p.goal.contains("진로") && !c.careerTag.isEmpty()) score += 10;
        if (p.goal.contains("흥미") && c.difficulty.equals("하")) score += 10;
        if (p.goal.contains("직무") && (c.difficulty.equals("중") || c.difficulty.equals("상"))) score += 10;
        return Math.min(score, 100);
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
        if (tier.equals("브론즈")) return "LLM이 만든 10문제를 모두 답한 뒤 7문제 이상 맞히면 " + PromotionRules.displayName("실버") + "로 승급합니다.";
        if (tier.equals("실버")) return "LLM이 만든 12문제를 모두 답한 뒤 9문제 이상 맞히면 " + PromotionRules.displayName("골드") + "로 승급합니다.";
        if (tier.equals("골드")) return "LLM이 만든 15문제를 모두 답한 뒤 10문제 이상 맞히면 " + PromotionRules.displayName("플래티넘") + "으로 승급합니다.";
        if (tier.equals("플래티넘")) return "기존 XP 4,200점 또는 자격·교육·경력 인증 로드맵으로 " + PromotionRules.displayName("다이아") + "를 준비하세요.";
        return "전문가 단계입니다. 멘토링과 프로젝트 리뷰로 후배 학습자를 도울 수 있습니다.";
    }

    public static int tierRank(String tier) {
        return PromotionRules.rank(tier);
    }

    public static String answerAgent(String question, UserProfile p, String tier) {
        String q = question == null ? "" : question.toLowerCase();
        if (q.contains("티어") || q.contains("승급") || q.contains("올리")) {
            return "현재 " + PromotionRules.displayName(tier) + " 단계입니다. " + nextAction(p, tier)
                    + " 최종 티어는 기존 XP 기준과 퀴즈 획득 티어 중 높은 단계가 적용됩니다.";
        }
        if (q.contains("교육") || q.contains("일정") || q.contains("프로그램")) {
            ProgramItem item = recommendedPrograms(p).get(0);
            return "추천 교육은 ‘" + item.title + "’입니다. 대상은 " + item.target + ", 기간은 "
                    + item.startDate + "~" + item.endDate + "입니다. 관심 분야 ‘" + p.interest
                    + "’와 학습 목적 ‘" + p.goal + "’의 적합도를 기준으로 골랐습니다.";
        }
        if (q.contains("직업") || q.contains("진로") || q.contains("ncs") || q.contains("항해사")) {
            CareerItem item = recommendedCareers(p).get(0);
            return "추천 진로는 ‘" + item.title + "’입니다. 주요 역량은 " + join(item.ncsUnits, ", ")
                    + "이고, 관련 근무지는 " + join(item.workplaces, ", ") + "입니다.";
        }
        if (q.contains("퀴즈") || q.contains("문제")) {
            return "퀴즈 탭에서 현재 티어용 승급 퀴즈를 생성하세요. 선택 즉시 정답은 공개되지 않고, 모든 문항을 답한 뒤 최종 채점과 문항별 해설이 제공됩니다.";
        }
        if (q.contains("영상") || q.contains("콘텐츠")) {
            ContentItem item = recommendedContents(p, tier).get(0);
            return "추천 영상은 ‘" + item.title + "’입니다. 난도는 " + item.difficulty + ", 권장 티어는 " + PromotionRules.displayName(item.requiredTier) + "입니다.";
        }
        return "BluePath 해양 도메인 에이전트입니다. 교육 추천, 일정, 승급 퀴즈, 해양 직무와 NCS 로드맵을 질문해보세요. 현재 관심 분야는 ‘"
                + p.interest + "’, 목표는 ‘" + p.goal + "’입니다.";
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
