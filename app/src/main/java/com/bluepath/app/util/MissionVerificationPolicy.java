package com.bluepath.app.util;

public final class MissionVerificationPolicy {
    private MissionVerificationPolicy() {}

    public static boolean isCompletionNoteValid(String note) {
        return note != null && note.trim().length() >= 10;
    }

    public static boolean shouldAward(boolean newlyVerified) {
        return newlyVerified;
    }
}
