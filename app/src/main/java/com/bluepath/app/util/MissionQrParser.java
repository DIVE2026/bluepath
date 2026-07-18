package com.bluepath.app.util;

import com.bluepath.app.network.ApiModels;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public final class MissionQrParser {
    private static final Gson GSON = new Gson();

    private MissionQrParser() {}

    public static ApiModels.MissionQrPayload parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("QR payload is empty");
        }
        final ApiModels.MissionQrPayload payload;
        try {
            payload = GSON.fromJson(raw, ApiModels.MissionQrPayload.class);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("QR payload is not valid JSON", exception);
        }
        if (payload == null
                || length(payload.exhibitCode) < 2
                || length(payload.sessionId) < 8
                || length(payload.issuedAt) < 10
                || length(payload.expiresAt) < 10
                || length(payload.nonce) < 16
                || length(payload.signature) < 32) {
            throw new IllegalArgumentException("QR payload is missing required signed fields");
        }
        if (payload.exhibitTitle == null || payload.exhibitTitle.trim().isEmpty()) {
            payload.exhibitTitle = payload.exhibitCode;
        }
        return payload;
    }

    private static int length(String value) {
        return value == null ? 0 : value.trim().length();
    }
}
