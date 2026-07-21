package com.bluepath.app.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central catalog for the Ocean Skill Map and evidence portfolio.
 * Keeping the labels in one place makes the visual map and exported report consistent.
 */
public final class SkillProfileCatalog {
    public static final String[] TOPICS = {
            "해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류"
    };

    private static final Map<String, SkillDescriptor> DESCRIPTORS = new LinkedHashMap<>();

    static {
        put(new SkillDescriptor(
                "해양환경",
                "오염원 분석 · 수질 자료 해석",
                "환경관리 · 해양환경 모니터링 · 교육기획",
                "해양환경 교육 기획자",
                "해양오염 또는 기후변화 학습 1개를 완료하고 관련 퀴즈에 다시 도전하세요."
        ));
        put(new SkillDescriptor(
                "해양생물",
                "생물 분류 · 생태관계 해석",
                "생태조사 · 생물자원 관리 · 전시해설",
                "해양생태 해설사",
                "해양생물 분류 콘텐츠를 학습하고 관찰 결과를 한 문장으로 기록하세요."
        ));
        put(new SkillDescriptor(
                "항해",
                "선위결정 · 항해당직 · 항로 판단",
                "항해계획 · 항해장비 운용 · 선박조종",
                "항해사",
                "항법 또는 항해장비 학습을 완료한 뒤 항해 분야 퀴즈 3문항을 풀어보세요."
        ));
        put(new SkillDescriptor(
                "선박",
                "선박 구조 · 기관 및 장비 이해",
                "선박운용 · 기관관리 · 선박정비",
                "자율운항선박 엔지니어",
                "선박 구조 콘텐츠에서 핵심 장비 3가지를 골라 역할을 기록하세요."
        ));
        put(new SkillDescriptor(
                "독도·해양문화",
                "해양사 이해 · 문화 콘텐츠 해석",
                "문화유산 조사 · 전시콘텐츠 기획 · 해설",
                "해양문화 콘텐츠 기획자",
                "독도 또는 해양사 자료를 학습하고 관람객에게 설명할 핵심 문장을 작성하세요."
        ));
        put(new SkillDescriptor(
                "해양안전",
                "위험 식별 · 비상대응 · 안전설비 이해",
                "선박안전관리 · 비상대응 · 구명설비 운용",
                "선박안전관리자",
                "안전 또는 비상대응 콘텐츠를 완료하고 현장 안전 미션을 인증하세요."
        ));
        put(new SkillDescriptor(
                "항만·물류",
                "화물 흐름 · 항만 운영 · 데이터 판단",
                "항만운영 · 화물관리 · 해운물류 관리",
                "항만물류 전문가",
                "항만 운영 사례를 학습하고 화물이 이동하는 단계를 순서대로 정리하세요."
        ));
    }

    private SkillProfileCatalog() {}

    private static void put(SkillDescriptor descriptor) {
        DESCRIPTORS.put(descriptor.topic, descriptor);
    }

    public static SkillDescriptor descriptor(String topic) {
        SkillDescriptor descriptor = DESCRIPTORS.get(topic);
        return descriptor == null ? DESCRIPTORS.get("해양환경") : descriptor;
    }

    public static int averageMastery(Map<String, Integer> mastery) {
        int sum = 0;
        int count = 0;
        for (String topic : TOPICS) {
            Integer value = mastery == null ? null : mastery.get(topic);
            sum += clamp(value == null ? 50 : value);
            count++;
        }
        return count == 0 ? 50 : Math.round(sum / (float) count);
    }

    public static int careerReadiness(String targetCareer, Map<String, Integer> mastery) {
        List<String> required = requiredTopics(targetCareer);
        int sum = 0;
        for (String topic : required) {
            Integer score = mastery == null ? null : mastery.get(topic);
            sum += clamp(score == null ? 50 : score);
        }
        return required.isEmpty() ? averageMastery(mastery) : Math.round(sum / (float) required.size());
    }

    public static List<String> requiredTopics(String targetCareer) {
        String career = targetCareer == null ? "" : targetCareer;
        if (career.contains("항해사")) return Arrays.asList("항해", "해양안전", "선박");
        if (career.contains("안전")) return Arrays.asList("해양안전", "선박", "항해");
        if (career.contains("항만") || career.contains("물류")) return Arrays.asList("항만·물류", "해양안전", "선박");
        if (career.contains("생태") || career.contains("생물")) return Arrays.asList("해양생물", "해양환경", "독도·해양문화");
        if (career.contains("문화") || career.contains("콘텐츠")) return Arrays.asList("독도·해양문화", "해양환경", "해양생물");
        if (career.contains("선박") || career.contains("엔지니어")) return Arrays.asList("선박", "항해", "해양안전");
        return Arrays.asList("해양환경", "해양생물", "독도·해양문화");
    }

    public static List<String> strongestTopics(Map<String, Integer> mastery, int limit) {
        List<String> topics = new ArrayList<>(Arrays.asList(TOPICS));
        topics.sort((left, right) -> Integer.compare(
                mastery == null ? 50 : mastery.getOrDefault(right, 50),
                mastery == null ? 50 : mastery.getOrDefault(left, 50)
        ));
        return new ArrayList<>(topics.subList(0, Math.min(Math.max(0, limit), topics.size())));
    }

    public static List<String> weakestTopics(Map<String, Integer> mastery, int limit) {
        List<String> topics = new ArrayList<>(Arrays.asList(TOPICS));
        topics.sort((left, right) -> Integer.compare(
                mastery == null ? 50 : mastery.getOrDefault(left, 50),
                mastery == null ? 50 : mastery.getOrDefault(right, 50)
        ));
        return new ArrayList<>(topics.subList(0, Math.min(Math.max(0, limit), topics.size())));
    }

    public static int predictedNextScore(int score, int evidenceCount) {
        int evidenceBonus = evidenceCount <= 0 ? 8 : evidenceCount < 4 ? 6 : 4;
        return clamp(score + evidenceBonus);
    }

    public static String scoreLevel(int score) {
        if (score >= 85) return "탁월";
        if (score >= 70) return "성장";
        if (score >= 55) return "기초";
        return "보완 필요";
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    public static final class SkillDescriptor {
        public final String topic;
        public final String subSkills;
        public final String ncsCompetencies;
        public final String career;
        public final String nextAction;

        private SkillDescriptor(String topic, String subSkills, String ncsCompetencies,
                                String career, String nextAction) {
            this.topic = topic;
            this.subSkills = subSkills;
            this.ncsCompetencies = ncsCompetencies;
            this.career = career;
            this.nextAction = nextAction;
        }
    }
}
