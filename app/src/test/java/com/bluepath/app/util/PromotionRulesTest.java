package com.bluepath.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PromotionRulesTest {
    @Test
    public void promotionCountsMatchProductRules() {
        assertEquals(10, PromotionRules.questionCount("브론즈"));
        assertEquals(7, PromotionRules.passCount("브론즈"));
        assertEquals(12, PromotionRules.questionCount("실버"));
        assertEquals(9, PromotionRules.passCount("실버"));
        assertEquals(15, PromotionRules.questionCount("골드"));
        assertEquals(10, PromotionRules.passCount("골드"));
        assertEquals(20, PromotionRules.questionCount("플래티넘"));
        assertEquals(16, PromotionRules.passCount("플래티넘"));
    }

    @Test
    public void tiersUseInlineShieldMarkersAndKeepOrder() {
        assertEquals("[[tier-shield:브론즈]] 브론즈", PromotionRules.displayName("브론즈"));
        assertEquals("[[tier-shield:다이아]] 다이아", PromotionRules.displayName("다이아"));
        assertEquals("브론즈", PromotionRules.stripShieldMarkers(PromotionRules.displayName("브론즈")));
        assertEquals("다이아", PromotionRules.nextTier("플래티넘"));
        assertTrue(PromotionRules.rank("다이아") > PromotionRules.rank("플래티넘"));
    }

    @Test
    public void manualIncludesDelayedGradingAndDiamondEvidenceWithoutMedals() {
        String manual = PromotionRules.fullManual();
        assertTrue(manual.contains("전 문항 선택 후 한 번에 채점"));
        assertTrue(manual.contains("자격 증빙 승인"));
        assertTrue(manual.contains("해양 프로젝트 승인"));
        assertFalse(manual.contains("🥉"));
        assertFalse(manual.contains("🥈"));
        assertFalse(manual.contains("🥇"));
        assertFalse(manual.contains("🏆"));
        assertFalse(manual.contains("💎"));
    }
}
