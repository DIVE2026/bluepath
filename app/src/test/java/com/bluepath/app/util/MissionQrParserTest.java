package com.bluepath.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.bluepath.app.network.ApiModels;

import org.junit.Test;

public class MissionQrParserTest {
    @Test
    public void parsesSignedMissionQrPayload() {
        String json = "{"
                + "\"exhibitCode\":\"submersible\","
                + "\"exhibitTitle\":\"잠수정 전시\","
                + "\"sessionId\":\"museum-session-001\","
                + "\"issuedAt\":\"2026-07-19T00:00:00Z\","
                + "\"expiresAt\":\"2026-07-19T00:10:00Z\","
                + "\"nonce\":\"1234567890abcdef1234567890abcdef\","
                + "\"signature\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\""
                + "}";
        ApiModels.MissionQrPayload payload = MissionQrParser.parse(json);
        assertEquals("submersible", payload.exhibitCode);
        assertEquals("museum-session-001", payload.sessionId);
    }

    @Test
    public void rejectsUnsignedOrIncompletePayload() {
        assertThrows(IllegalArgumentException.class, () -> MissionQrParser.parse("{\"exhibitCode\":\"x\"}"));
    }

    @Test
    public void awardsOnlyOnFirstVerification() {
        assertTrue(MissionVerificationPolicy.isCompletionNoteValid("가족이 안전장치를 찾아 기록했습니다."));
        assertTrue(MissionVerificationPolicy.shouldAward(true));
        org.junit.Assert.assertFalse(MissionVerificationPolicy.shouldAward(false));
    }
}
