package com.bluepath.app.util;

public final class PromotionRules {
    private PromotionRules() {}

    public static String emoji(String tier) {
        switch (tier) {
            case "브론즈": return "🥉";
            case "실버": return "🥈";
            case "골드": return "🥇";
            case "플래티넘": return "🏆";
            case "다이아": return "💎";
            default: return "🌊";
        }
    }

    public static String displayName(String tier) {
        return emoji(tier) + " " + tier;
    }

    public static String displayTransition(String fromTier) {
        return displayName(fromTier) + " → " + displayName(nextTier(fromTier));
    }

    public static int questionCount(String tier) {
        switch (tier) {
            case "브론즈": return 10;
            case "실버": return 12;
            case "골드": return 15;
            case "플래티넘": return 20;
            default: return 0;
        }
    }

    public static int passCount(String tier) {
        switch (tier) {
            case "브론즈": return 7;
            case "실버": return 9;
            case "골드": return 10;
            case "플래티넘": return 16;
            default: return 0;
        }
    }

    public static String nextTier(String tier) {
        switch (tier) {
            case "브론즈": return "실버";
            case "실버": return "골드";
            case "골드": return "플래티넘";
            case "플래티넘": return "다이아";
            default: return "다이아";
        }
    }

    public static int rank(String tier) {
        switch (tier) {
            case "브론즈": return 1;
            case "실버": return 2;
            case "골드": return 3;
            case "플래티넘": return 4;
            case "다이아": return 5;
            default: return 1;
        }
    }

    public static String tierForRank(int rank) {
        if (rank >= 5) return "다이아";
        if (rank == 4) return "플래티넘";
        if (rank == 3) return "골드";
        if (rank == 2) return "실버";
        return "브론즈";
    }

    public static String quizRule(String tier) {
        int total = questionCount(tier);
        int pass = passCount(tier);
        if ("플래티넘".equals(tier)) {
            return total + "문제 중 " + pass + "문제 이상 + 자격 증빙 + 해양 프로젝트 승인 시 "
                    + displayName("다이아") + " 승급";
        }
        if (total == 0) return "해당 티어는 퀴즈 단독 승급 대상이 아닙니다.";
        return total + "문제 중 " + pass + "문제 이상 정답 시 " + displayName(nextTier(tier)) + " 승급";
    }

    public static String fullManual() {
        return "[기존 XP 승급 기준]\n"
                + "• 🥉 브론즈: 0~699 XP\n"
                + "• 🥈 실버: 700 XP 이상\n"
                + "• 🥇 골드: 1,600 XP 이상\n"
                + "• 🏆 플래티넘: 2,800 XP 이상\n"
                + "• 💎 다이아: 4,200 XP 이상\n\n"
                + "[퀴즈 승급 기준]\n"
                + "• 🥉 브론즈 → 🥈 실버: 10문제 중 7문제 이상\n"
                + "• 🥈 실버 → 🥇 골드: 12문제 중 9문제 이상\n"
                + "• 🥇 골드 → 🏆 플래티넘: 15문제 중 10문제 이상\n"
                + "• 🏆 플래티넘 → 💎 다이아: 고급 20문제 중 16문제 이상, 자격 증빙 승인, 해양 프로젝트 승인\n"
                + "• 모든 문제는 4지선다이며, 전 문항 선택 후 한 번에 채점합니다.\n"
                + "• 채점 후 총점, 합격 여부, 문항별 정답과 해설을 제공합니다.\n\n"
                + "최종 티어는 XP 기준, 퀴즈 획득 티어, 다이아 인증 경로 중 가장 높은 단계가 적용됩니다.";
    }
}
