package com.bluepath.app.storage;

import android.content.Context;
import android.content.SharedPreferences;

import com.bluepath.app.model.UserProfile;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.util.PromotionRules;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserStore {
    private static final String PREF = "bluepath_user";
    private static final String GLOBAL_PREF = "bluepath_global";
    private static final String GUEST_SCOPE = "guest";
    private static final String[] SKILL_TOPICS = {"해양환경", "해양생물", "항해", "선박", "독도·해양문화", "해양안전", "항만·물류", "해양교육"};
    private final Context context;
    private final SharedPreferences globalPrefs;
    private SharedPreferences prefs;
    private final SecureTokenStore secureTokenStore;

    public UserStore(Context context) {
        this.context = context.getApplicationContext();
        globalPrefs = this.context.getSharedPreferences(GLOBAL_PREF, Context.MODE_PRIVATE);
        secureTokenStore = new SecureTokenStore(globalPrefs);
        String activeScope = globalPrefs.getString("activeAccountScope", GUEST_SCOPE);
        prefs = preferencesForScope(activeScope);
        migrateLegacySkillState();
    }

    private SharedPreferences preferencesForScope(String scope) {
        if (scope == null || scope.trim().isEmpty() || GUEST_SCOPE.equals(scope)) {
            return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        }
        return context.getSharedPreferences(PREF + "_" + scope, Context.MODE_PRIVATE);
    }

    private String accountScope(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < 12; i++) result.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return result.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private void activateAccount(String email) {
        String scope = accountScope(email);
        SharedPreferences target = preferencesForScope(scope);
        String legacyEmail = prefs.getString("accountEmail", "");
        String migratedKey = "migratedScope_" + scope;
        if (!globalPrefs.getBoolean(migratedKey, false) && !legacyEmail.isEmpty()
                && legacyEmail.equalsIgnoreCase(email == null ? "" : email.trim())) {
            SharedPreferences.Editor targetEditor = target.edit();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String) targetEditor.putString(entry.getKey(), (String) value);
                else if (value instanceof Integer) targetEditor.putInt(entry.getKey(), (Integer) value);
                else if (value instanceof Long) targetEditor.putLong(entry.getKey(), (Long) value);
                else if (value instanceof Boolean) targetEditor.putBoolean(entry.getKey(), (Boolean) value);
                else if (value instanceof Float) targetEditor.putFloat(entry.getKey(), (Float) value);
                else if (value instanceof Set) targetEditor.putStringSet(entry.getKey(), new HashSet<>((Set<String>) value));
            }
            targetEditor.apply();
            globalPrefs.edit().putBoolean(migratedKey, true).putString("pendingLegacyRecordScope", scope).apply();
        }
        prefs = target;
        globalPrefs.edit().putString("activeAccountScope", scope).apply();
        migrateLegacySkillState();
    }

    private void activateGuest() {
        prefs = preferencesForScope(GUEST_SCOPE);
        globalPrefs.edit().putString("activeAccountScope", GUEST_SCOPE).apply();
        migrateLegacySkillState();
    }

    public boolean hasProfile() {
        return prefs.contains("ageGroup");
    }

    public UserProfile getProfile() {
        return new UserProfile(
                prefs.getString("ageGroup", "중학생"),
                prefs.getString("interest", "항해"),
                prefs.getString("goal", "진로탐색"),
                prefs.getString("level", "입문"),
                prefs.getString("persona", "진로 탐색 항해자"),
                prefs.getInt("xp", 0)
        );
    }

    public void saveProfile(UserProfile profile) {
        prefs.edit()
                .putString("ageGroup", profile.ageGroup)
                .putString("interest", profile.interest)
                .putString("goal", profile.goal)
                .putString("level", profile.level)
                .putString("persona", profile.persona)
                .putInt("xp", profile.xp)
                .putLong("profileUpdatedAt", System.currentTimeMillis())
                .apply();
    }

    public void addXp(int amount) {
        prefs.edit().putInt("xp", Math.max(0, prefs.getInt("xp", 0) + amount)).apply();
    }

    public void setXp(int xp) {
        prefs.edit().putInt("xp", Math.max(0, xp)).apply();
    }

    public String getTier() {
        String xpTier = tierForXp(prefs.getInt("xp", 0));
        String quizTier = PromotionRules.tierForRank(prefs.getInt("quizTierRank", 1));
        String tier = PromotionRules.rank(xpTier) >= PromotionRules.rank(quizTier) ? xpTier : quizTier;
        if (isDiamondEligible()) tier = "다이아";
        return tier;
    }

    public String getXpTier() {
        return tierForXp(prefs.getInt("xp", 0));
    }

    public String getQuizTier() {
        return PromotionRules.tierForRank(prefs.getInt("quizTierRank", 1));
    }

    public void promoteByQuiz(String fromTier) {
        if ("플래티넘".equals(fromTier)) {
            prefs.edit().putBoolean("diamondAdvancedQuizPassed", true).apply();
            return;
        }
        int targetRank = PromotionRules.rank(PromotionRules.nextTier(fromTier));
        int currentRank = prefs.getInt("quizTierRank", 1);
        if (targetRank > currentRank) prefs.edit().putInt("quizTierRank", targetRank).apply();
    }

    public static String tierForXp(int xp) {
        if (xp >= 4200) return "다이아";
        if (xp >= 2800) return "플래티넘";
        if (xp >= 1600) return "골드";
        if (xp >= 700) return "실버";
        return "브론즈";
    }

    public static int nextTierXp(String tier) {
        switch (tier) {
            case "브론즈": return 700;
            case "실버": return 1600;
            case "골드": return 2800;
            case "플래티넘": return 4200;
            default: return 4200;
        }
    }

    public static int tierBaseXp(String tier) {
        switch (tier) {
            case "실버": return 700;
            case "골드": return 1600;
            case "플래티넘": return 2800;
            case "다이아": return 4200;
            default: return 0;
        }
    }

    public Set<String> getCompletedContentIds() {
        return new HashSet<>(prefs.getStringSet("completed", new HashSet<>()));
    }

    public void markCompleted(String contentId) {
        Set<String> ids = getCompletedContentIds();
        boolean added = ids.add(contentId);
        prefs.edit().putStringSet("completed", ids).apply();
        if (added) recordActivity("learning", 1);
    }

    public void markContentStarted(String contentId) {
        if (contentId == null || contentId.trim().isEmpty()) return;
        if (getContentStartedAt(contentId) <= 0L) {
            prefs.edit().putLong("contentStarted_" + contentId, System.currentTimeMillis()).apply();
            recordActivity("learning", 1);
        }
    }

    public boolean isContentStarted(String contentId) {
        return getContentStartedAt(contentId) > 0L;
    }

    public long getContentStartedAt(String contentId) {
        return prefs.getLong("contentStarted_" + contentId, 0L);
    }

    public long secondsSinceContentStarted(String contentId) {
        long startedAt = getContentStartedAt(contentId);
        return startedAt <= 0L ? 0L : Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
    }

    public void saveContentReflection(String contentId, String reflection) {
        if (contentId == null || contentId.trim().isEmpty()) return;
        prefs.edit()
                .putString("contentReflection_" + contentId, reflection == null ? "" : reflection.trim())
                .putLong("contentReflectionUpdatedAt_" + contentId, System.currentTimeMillis())
                .apply();
    }

    public String getContentReflection(String contentId) {
        if (contentId == null || contentId.trim().isEmpty()) return "";
        return prefs.getString("contentReflection_" + contentId, "").trim();
    }

    public synchronized void recordVideoInterval(String contentId, double startSeconds, double endSeconds, int durationSeconds) {
        if (contentId == null || contentId.trim().isEmpty() || durationSeconds <= 0) return;
        double start = Math.max(0d, Math.min(startSeconds, durationSeconds));
        double end = Math.max(0d, Math.min(endSeconds, durationSeconds));
        if (end <= start || end - start > 3.5d) return;
        List<ApiModels.VideoInterval> intervals = getVideoIntervals(contentId);
        intervals.add(new ApiModels.VideoInterval(start, end));
        intervals.sort((a, b) -> Double.compare(a.start, b.start));
        List<ApiModels.VideoInterval> merged = new ArrayList<>();
        for (ApiModels.VideoInterval interval : intervals) {
            if (merged.isEmpty() || interval.start > merged.get(merged.size() - 1).end + 1.25d) {
                merged.add(new ApiModels.VideoInterval(interval.start, interval.end));
            } else {
                ApiModels.VideoInterval last = merged.get(merged.size() - 1);
                last.end = Math.max(last.end, interval.end);
            }
        }
        JSONArray encoded = new JSONArray();
        for (ApiModels.VideoInterval interval : merged) {
            JSONArray pair = new JSONArray();
            pair.put(interval.start);
            pair.put(interval.end);
            encoded.put(pair);
        }
        prefs.edit().putString("videoIntervals_" + contentId, encoded.toString())
                .putInt("videoDuration_" + contentId, durationSeconds).apply();
    }

    public List<ApiModels.VideoInterval> getVideoIntervals(String contentId) {
        List<ApiModels.VideoInterval> result = new ArrayList<>();
        if (contentId == null || contentId.trim().isEmpty()) return result;
        String raw = prefs.getString("videoIntervals_" + contentId, "[]");
        try {
            JSONArray values = new JSONArray(raw);
            for (int i = 0; i < values.length(); i++) {
                JSONArray pair = values.optJSONArray(i);
                if (pair == null || pair.length() < 2) continue;
                double start = pair.optDouble(0, -1d);
                double end = pair.optDouble(1, -1d);
                if (start >= 0d && end > start) result.add(new ApiModels.VideoInterval(start, end));
            }
        } catch (JSONException ignored) {}
        return result;
    }

    public List<ApiModels.VideoInterval> getVideoIntervalsForVerification(String contentId) {
        List<ApiModels.VideoInterval> chunks = new ArrayList<>();
        for (ApiModels.VideoInterval interval : getVideoIntervals(contentId)) {
            double cursor = interval.start;
            while (cursor < interval.end) {
                double end = Math.min(interval.end, cursor + 10d);
                chunks.add(new ApiModels.VideoInterval(cursor, end));
                cursor = end;
            }
        }
        return chunks;
    }

    public int getVideoDurationSeconds(String contentId) {
        return Math.max(0, prefs.getInt("videoDuration_" + contentId, 0));
    }

    public int getVideoWatchSeconds(String contentId) {
        double watched = 0d;
        for (ApiModels.VideoInterval interval : getVideoIntervals(contentId)) watched += Math.max(0d, interval.end - interval.start);
        return (int) Math.floor(watched);
    }

    public int getVideoProgressPercent(String contentId) {
        int duration = getVideoDurationSeconds(contentId);
        return duration <= 0 ? 0 : clampPercent((int) Math.floor(getVideoWatchSeconds(contentId) * 100d / duration));
    }

    public boolean hasVideoEnded(String contentId) {
        return getVideoProgressPercent(contentId) >= 99;
    }

    public void markVideoServerVerified(String contentId, boolean verified, int watchedSeconds, int coveragePercent) {
        if (contentId == null || contentId.trim().isEmpty()) return;
        prefs.edit().putBoolean("videoServerVerified_" + contentId, verified)
                .putInt("videoServerWatched_" + contentId, Math.max(0, watchedSeconds))
                .putInt("videoServerCoverage_" + contentId, clampPercent(coveragePercent)).apply();
    }

    public boolean hasVerifiedVideoCompletion(String contentId, int expectedMinutes) {
        return prefs.getBoolean("videoServerVerified_" + contentId, false);
    }

    public Set<String> getBookmarks() {
        return new HashSet<>(prefs.getStringSet("bookmarks", new HashSet<>()));
    }

    public boolean isBookmarked(String id) {
        return getBookmarks().contains(id);
    }

    public void toggleBookmark(String id) {
        if (id == null || id.trim().isEmpty()) return;
        String key = id.trim();
        Set<String> ids = getBookmarks();
        boolean active;
        if (ids.contains(key)) {
            ids.remove(key);
            active = false;
        } else {
            ids.add(key);
            active = true;
        }
        JSONObject state = bookmarkStateObject();
        try {
            JSONObject item = new JSONObject();
            item.put("active", active);
            item.put("updatedAt", System.currentTimeMillis());
            state.put(key, item);
        } catch (JSONException ignored) {}
        prefs.edit().putStringSet("bookmarks", ids).putString("bookmarkState", state.toString()).apply();
    }

    private JSONObject bookmarkStateObject() {
        try {
            return new JSONObject(prefs.getString("bookmarkState", "{}"));
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private Map<String, Object> bookmarkStateForCloud() {
        JSONObject state = bookmarkStateObject();
        for (String id : getBookmarks()) {
            if (state.has(id)) continue;
            try {
                JSONObject item = new JSONObject();
                item.put("active", true);
                item.put("updatedAt", 0L);
                state.put(id, item);
            } catch (JSONException ignored) {}
        }
        Map<String, Object> result = new HashMap<>();
        JSONArray names = state.names();
        if (names == null) return result;
        for (int i = 0; i < names.length(); i++) {
            String id = names.optString(i, "");
            JSONObject item = state.optJSONObject(id);
            if (id.isEmpty() || item == null) continue;
            Map<String, Object> value = new HashMap<>();
            value.put("active", item.optBoolean("active", false));
            value.put("updatedAt", item.optLong("updatedAt", 0L));
            result.put(id, value);
        }
        return result;
    }

    public int calculateQuizXpAward(String tier, int correct, boolean passed) {
        int previousBest = prefs.getInt("bestQuiz_" + tier, 0);
        boolean alreadyPassed = prefs.getBoolean("passedQuiz_" + tier, false);
        if (passed && !alreadyPassed) return 300;
        int improvement = Math.max(0, correct - previousBest);
        if (improvement > 0) return Math.min(120, 20 + improvement * 15);
        return 0;
    }

    public void applyServerQuizResult(ApiModels.QuizSubmissionResponse result, String attemptedTier, String source) {
        if (result == null) return;
        prefs.edit()
                .putInt("xp", Math.max(0, result.xp))
                .putInt("quizTierRank", Math.max(1, Math.min(4, result.quizTierRank)))
                .putBoolean("diamondAdvancedQuizPassed", result.advancedQuizPassed)
                .apply();
        if (result.skillMastery != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, Integer> entry : result.skillMastery.entrySet()) {
                editor.putInt("skillMastery_" + normalizeSkillTopic(entry.getKey()), clampPercent(entry.getValue()));
            }
            if (result.skillEvidence != null) {
                for (Map.Entry<String, Integer> entry : result.skillEvidence.entrySet()) {
                    editor.putInt("skillEvidence_" + normalizeSkillTopic(entry.getKey()), Math.max(0, entry.getValue()));
                }
            }
            editor.apply();
        }
        recordQuizAttempt(attemptedTier, result.correctCount, result.total, result.passed, source);
    }

    public void recordQuizAttempt(String tier, int correct, int total, boolean passed, String source) {
        int attempts = prefs.getInt("quizAttempts", 0) + 1;
        int best = Math.max(correct, prefs.getInt("bestQuiz_" + tier, 0));
        String date = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(new Date());
        String summary = date + " · " + PromotionRules.displayName(tier) + " " + correct + "/" + total + " · "
                + (passed ? "합격" : "재도전") + " · " + source;
        SharedPreferences.Editor editor = prefs.edit()
                .putInt("quizAttempts", attempts)
                .putInt("lastQuiz_" + tier, correct)
                .putInt("bestQuiz_" + tier, best)
                .putString("lastQuizSummary", summary);
        if (passed) editor.putBoolean("passedQuiz_" + tier, true);
        editor.apply();
    }

    public void recordSkillEvidence(String topic, boolean correct) {
        String key = normalizeSkillTopic(topic);
        int evidence = getSkillEvidenceCount(key);
        int mastery = getSkillMastery(key);
        int nextEvidence = evidence + 1;
        int nextMastery = Math.round((mastery * evidence + (correct ? 100f : 0f)) / nextEvidence);
        prefs.edit()
                .putInt("skillEvidence_" + key, nextEvidence)
                .putInt("skillMastery_" + key, clampPercent(nextMastery))
                .apply();
    }

    public int getSkillMastery(String topic) {
        String key = normalizeSkillTopic(topic);
        return clampPercent(prefs.getInt("skillMastery_" + key, 50));
    }

    public int getSkillEvidenceCount(String topic) {
        return Math.max(0, prefs.getInt("skillEvidence_" + normalizeSkillTopic(topic), 0));
    }

    public Map<String, Integer> getSkillMasteryMap() {
        Map<String, Integer> result = new HashMap<>();
        for (String topic : SKILL_TOPICS) result.put(topic, getSkillMastery(topic));
        return result;
    }

    public Map<String, Integer> getSkillEvidenceMap() {
        Map<String, Integer> result = new HashMap<>();
        for (String topic : SKILL_TOPICS) result.put(topic, getSkillEvidenceCount(topic));
        return result;
    }

    private int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private void migrateLegacySkillState() {
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        for (String topic : SKILL_TOPICS) {
            String masteryKey = "skillMastery_" + topic;
            String evidenceKey = "skillEvidence_" + topic;
            if (prefs.contains(masteryKey) && prefs.contains(evidenceKey)) continue;
            int legacyTotal = Math.max(0, prefs.getInt("skillTotal_" + topic, 0));
            int legacyCorrect = Math.max(0, prefs.getInt("skillCorrect_" + topic, 0));
            boolean syntheticPercentagePair = legacyTotal == 100 && legacyCorrect <= 100;
            int mastery = legacyTotal == 0 ? 50 : clampPercent(Math.round(legacyCorrect * 100f / legacyTotal));
            int evidence = syntheticPercentagePair ? 0 : legacyTotal;
            editor.putInt(masteryKey, mastery).putInt(evidenceKey, evidence);
            changed = true;
        }
        if (changed) editor.apply();
    }

    private String normalizeSkillTopic(String topic) {
        String value = topic == null ? "해양교육" : topic.trim();
        if (value.contains("환경") || value.contains("생태")) return "해양환경";
        if (value.contains("생물")) return "해양생물";
        if (value.contains("항해")) return "항해";
        if (value.contains("선박") || value.contains("기관")) return "선박";
        if (value.contains("독도") || value.contains("문화")) return "독도·해양문화";
        if (value.contains("안전") || value.contains("비상")) return "해양안전";
        if (value.contains("항만") || value.contains("물류")) return "항만·물류";
        return "해양교육";
    }

    public int getQuizAttempts() {
        return prefs.getInt("quizAttempts", 0);
    }

    public int getBestQuizScore(String tier) {
        return prefs.getInt("bestQuiz_" + tier, 0);
    }

    public String getLastQuizSummary() {
        return prefs.getString("lastQuizSummary", "아직 응시 기록이 없습니다.");
    }

    public void saveCloudSession(String email, String displayName, String nickname, String profileImageUrl,
                                 int followerCount, int followingCount, String joinedAt, String accessToken) {
        activateAccount(email);
        secureTokenStore.put(accessToken == null ? "" : accessToken);
        prefs.edit()
                .putString("accountEmail", email == null ? "" : email)
                .putString("accountDisplayName", displayName == null ? "BluePath Learner" : displayName)
                .putString("nickname", nickname == null || nickname.trim().isEmpty() ? displayName : nickname.trim())
                .putString("profileImageUrl", profileImageUrl == null ? "" : profileImageUrl)
                .putInt("followerCount", Math.max(0, followerCount))
                .putInt("followingCount", Math.max(0, followingCount))
                .putString("accountJoinedAt", normalizedJoinedAt(joinedAt))
                .apply();
    }

    public boolean hasCloudSession() {
        return !getAccessToken().isEmpty();
    }

    public String getAccountEmail() {
        return prefs.getString("accountEmail", "");
    }

    public String getAccountDisplayName() {
        return prefs.getString("accountDisplayName", "BluePath Learner");
    }


    public String getNickname() {
        String fallback = getAccountDisplayName();
        return prefs.getString("nickname", fallback == null ? "BluePath" : fallback);
    }

    public String getProfileImageUrl() {
        return prefs.getString("profileImageUrl", "");
    }

    public void setProfileImageUrl(String value) {
        prefs.edit().putString("profileImageUrl", value == null ? "" : value).apply();
    }

    public int getFollowerCount() {
        return prefs.getInt("followerCount", 0);
    }

    public int getFollowingCount() {
        return prefs.getInt("followingCount", 0);
    }

    public void setFollowCounts(int followerCount, int followingCount) {
        prefs.edit()
                .putInt("followerCount", Math.max(0, followerCount))
                .putInt("followingCount", Math.max(0, followingCount))
                .apply();
    }

    public void setFollowingCount(int followingCount) {
        prefs.edit().putInt("followingCount", Math.max(0, followingCount)).apply();
    }

    public void applyDashboard(ApiModels.DashboardResponse response) {
        if (response == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        if (response.profile != null) {
            if (response.profile.nickname != null && !response.profile.nickname.trim().isEmpty()) {
                editor.putString("nickname", response.profile.nickname.trim());
            }
            editor.putString("profileImageUrl", response.profile.profileImageUrl == null ? "" : response.profile.profileImageUrl)
                    .putInt("followerCount", Math.max(0, response.profile.followerCount))
                    .putInt("followingCount", Math.max(0, response.profile.followingCount));
            if (response.profile.joinedAt != null && !response.profile.joinedAt.trim().isEmpty()) {
                editor.putString("accountJoinedAt", response.profile.joinedAt.trim());
            }
        }
        if (response.activity != null) {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Integer> entry : response.activity.entrySet()) {
                try { json.put(entry.getKey(), Math.max(0, entry.getValue())); } catch (JSONException ignored) {}
            }
            editor.putString("serverActivity", json.toString());
        }
        editor.apply();
    }

    public void recordActivity(String type, int amount) {
        if (amount <= 0) return;
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
        JSONObject json;
        try {
            json = new JSONObject(prefs.getString("localActivity", "{}"));
            json.put(day, json.optInt(day, 0) + amount);
            prefs.edit().putString("localActivity", json.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    public Map<String, Integer> getActivityCounts() {
        Map<String, Integer> result = new HashMap<>();
        mergeActivityJson(result, prefs.getString("serverActivity", "{}"));
        mergeActivityJson(result, prefs.getString("localActivity", "{}"));
        return result;
    }

    private void mergeActivityJson(Map<String, Integer> target, String raw) {
        try {
            JSONObject json = new JSONObject(raw == null ? "{}" : raw);
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                target.put(key, Math.max(target.getOrDefault(key, 0), Math.max(0, json.optInt(key, 0))));
            }
        } catch (JSONException ignored) {
        }
    }


    public int getAccountJoinedYear() {
        int currentYear = Calendar.getInstance(Locale.KOREA).get(Calendar.YEAR);
        int joinedYear = parseYear(prefs.getString("accountJoinedAt", ""), currentYear);
        for (String day : getActivityCounts().keySet()) {
            int activityYear = parseYear(day, currentYear);
            if (activityYear >= 2000 && activityYear < joinedYear) joinedYear = activityYear;
        }
        return Math.max(2000, Math.min(currentYear, joinedYear));
    }

    private String normalizedJoinedAt(String joinedAt) {
        if (joinedAt != null && !joinedAt.trim().isEmpty()) return joinedAt.trim();
        String existing = prefs.getString("accountJoinedAt", "");
        if (existing != null && !existing.trim().isEmpty()) return existing.trim();
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
    }

    private int parseYear(String value, int fallback) {
        if (value == null || value.length() < 4) return fallback;
        try {
            int parsed = Integer.parseInt(value.substring(0, 4));
            return parsed >= 2000 && parsed <= fallback ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public String getAccessToken() {
        return secureTokenStore.get();
    }

    public void clearCloudSession() {
        secureTokenStore.clear();
        activateGuest();
    }

    public String getAccountScope() {
        return globalPrefs.getString("activeAccountScope", GUEST_SCOPE);
    }

    public boolean consumeLegacyRecordMigrationFlag() {
        String pending = globalPrefs.getString("pendingLegacyRecordScope", "");
        if (pending == null || !pending.equals(getAccountScope())) return false;
        globalPrefs.edit().remove("pendingLegacyRecordScope").apply();
        return true;
    }

    public String getDeviceId() {
        String value = globalPrefs.getString("deviceId", "");
        if (value == null || value.trim().isEmpty()) {
            value = UUID.randomUUID().toString();
            globalPrefs.edit().putString("deviceId", value).apply();
        }
        return value;
    }

    public int getCloudVersion() {
        return Math.max(0, prefs.getInt("cloudVersion", 0));
    }

    public void setCloudVersion(int version) {
        prefs.edit().putInt("cloudVersion", Math.max(0, version)).apply();
    }

    public void setLastSyncAt(String value) {
        prefs.edit().putString("lastSyncAt", value == null ? "" : value).apply();
    }

    public String getLastSyncAt() {
        return prefs.getString("lastSyncAt", "아직 동기화하지 않았습니다.");
    }

    public boolean requiresGuardianConsent() {
        String age = getProfile().ageGroup;
        return "초등학생".equals(age) || "중학생".equals(age);
    }

    public boolean hasGuardianConsent() {
        return prefs.getBoolean("guardianConsent", false);
    }

    public String getGuardianEmail() {
        return prefs.getString("guardianEmail", "");
    }

    public void saveGuardianConsent(boolean consent, String guardianEmail) {
        prefs.edit()
                .putBoolean("guardianConsent", consent)
                .putString("guardianEmail", guardianEmail == null ? "" : guardianEmail.trim())
                .putLong("guardianConsentAt", consent ? System.currentTimeMillis() : 0L)
                .apply();
    }

    public void setReminderEnabled(boolean enabled, int hour, int minute) {
        prefs.edit()
                .putBoolean("reminderEnabled", enabled)
                .putInt("reminderHour", hour)
                .putInt("reminderMinute", minute)
                .putLong("reminderUpdatedAt", System.currentTimeMillis())
                .apply();
    }

    public boolean isReminderEnabled() {
        return prefs.getBoolean("reminderEnabled", false);
    }

    public int getReminderHour() {
        return prefs.getInt("reminderHour", 19);
    }

    public int getReminderMinute() {
        return prefs.getInt("reminderMinute", 0);
    }

    public boolean isDiamondAdvancedQuizPassed() {
        return prefs.getBoolean("diamondAdvancedQuizPassed", false);
    }

    public String getCertificationStatus() {
        return prefs.getString("diamondCertificationStatus", "not_submitted");
    }

    public String getProjectStatus() {
        return prefs.getString("diamondProjectStatus", "not_submitted");
    }

    public String getCertificationTitle() {
        return prefs.getString("diamondCertificationTitle", "");
    }

    public String getProjectTitle() {
        return prefs.getString("diamondProjectTitle", "");
    }

    public void markDiamondEvidenceSubmitted(String type, String title) {
        SharedPreferences.Editor editor = prefs.edit();
        if ("certification".equals(type)) {
            editor.putString("diamondCertificationStatus", "pending")
                    .putString("diamondCertificationTitle", title == null ? "" : title);
        } else if ("project".equals(type)) {
            editor.putString("diamondProjectStatus", "pending")
                    .putString("diamondProjectTitle", title == null ? "" : title);
        }
        editor.apply();
    }

    public void applyDiamondStatus(ApiModels.DiamondStatus status) {
        if (status == null) return;
        prefs.edit()
                .putBoolean("diamondAdvancedQuizPassed", status.advancedQuizPassed)
                .putString("diamondCertificationStatus", safeStatus(status.certificationStatus))
                .putString("diamondProjectStatus", safeStatus(status.projectStatus))
                .apply();
    }

    public boolean isDiamondEligible() {
        return isDiamondAdvancedQuizPassed()
                && "approved".equals(getCertificationStatus())
                && "approved".equals(getProjectStatus());
    }

    public String getTargetCareer() {
        String fallback;
        String interest = getProfile().interest;
        if (interest.contains("항해")) fallback = "항해사";
        else if (interest.contains("항만")) fallback = "항만 물류 운영자";
        else if (interest.contains("선박")) fallback = "자율운항선박 엔지니어";
        else if (interest.contains("문화") || interest.contains("독도")) fallback = "해양문화 콘텐츠 기획자";
        else if (interest.contains("생물")) fallback = "해양생태 해설사";
        else fallback = "해양환경 교육 기획자";
        return prefs.getString("targetCareer", fallback);
    }

    public String getRouteType() {
        return prefs.getString("routeType", "balanced");
    }

    public void saveVoyagePreferences(String targetCareer, String routeType) {
        prefs.edit()
                .putString("targetCareer", targetCareer == null ? "해양환경 교육 기획자" : targetCareer)
                .putString("routeType", routeType == null ? "balanced" : routeType)
                .putLong("voyagePreferenceUpdatedAt", System.currentTimeMillis())
                .apply();
    }

    public void touchRouteActivity() {
        prefs.edit().putLong("lastRouteActivityAt", System.currentTimeMillis()).apply();
    }

    public long daysSinceRouteActivity() {
        long value = prefs.getLong("lastRouteActivityAt", 0L);
        if (value <= 0L) return 0L;
        return Math.max(0L, (System.currentTimeMillis() - value) / (24L * 60L * 60L * 1000L));
    }

    public void applySkillGain(String topic, int points) {
        String key = normalizeSkillTopic(topic);
        int next = clampPercent(getSkillMastery(key) + Math.max(0, points));
        prefs.edit()
                .putInt("skillMastery_" + key, next)
                .putInt("skillEvidence_" + key, getSkillEvidenceCount(key) + 1)
                .apply();
    }

    public Set<String> getMissionBadges() {
        return new HashSet<>(prefs.getStringSet("missionBadges", new HashSet<>()));
    }

    public void addMissionBadge(String badge) {
        if (badge == null || badge.trim().isEmpty()) return;
        Set<String> values = getMissionBadges();
        boolean added = values.add(badge.trim());
        prefs.edit().putStringSet("missionBadges", values).apply();
        if (added) recordActivity("mission", 1);
    }

    public void savePendingReroute(String routeId, String summary) {
        prefs.edit()
                .putString("pendingRerouteId", routeId == null ? "" : routeId)
                .putString("pendingRerouteSummary", summary == null ? "" : summary)
                .apply();
    }

    public String getPendingRerouteId() {
        return prefs.getString("pendingRerouteId", "");
    }

    public String getPendingRerouteSummary() {
        return prefs.getString("pendingRerouteSummary", "");
    }

    public boolean hasPendingReroute() {
        return !getPendingRerouteId().trim().isEmpty();
    }

    public void clearPendingReroute() {
        prefs.edit().remove("pendingRerouteId").remove("pendingRerouteSummary").apply();
    }

    public Map<String, Object> toCloudSnapshot() {
        UserProfile profile = getProfile();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("nickname", getNickname());
        snapshot.put("profileImageUrl", getProfileImageUrl());
        snapshot.put("ageGroup", profile.ageGroup);
        snapshot.put("interest", profile.interest);
        snapshot.put("goal", profile.goal);
        snapshot.put("level", profile.level);
        snapshot.put("persona", profile.persona);
        snapshot.put("xp", profile.xp);
        snapshot.put("tier", getTier());
        snapshot.put("quizTier", getQuizTier());
        snapshot.put("completedContentIds", new ArrayList<>(getCompletedContentIds()));
        snapshot.put("bookmarks", new ArrayList<>(getBookmarks()));
        snapshot.put("bookmarkState", bookmarkStateForCloud());
        snapshot.put("quizAttempts", getQuizAttempts());
        snapshot.put("lastQuizSummary", getLastQuizSummary());
        snapshot.put("bestQuizBronze", getBestQuizScore("브론즈"));
        snapshot.put("bestQuizSilver", getBestQuizScore("실버"));
        snapshot.put("bestQuizGold", getBestQuizScore("골드"));
        snapshot.put("bestQuizPlatinum", getBestQuizScore("플래티넘"));
        snapshot.put("guardianConsent", hasGuardianConsent());
        snapshot.put("guardianEmail", getGuardianEmail());
        snapshot.put("reminderEnabled", isReminderEnabled());
        snapshot.put("reminderHour", getReminderHour());
        snapshot.put("reminderMinute", getReminderMinute());
        snapshot.put("diamondAdvancedQuizPassed", isDiamondAdvancedQuizPassed());
        snapshot.put("diamondCertificationStatus", getCertificationStatus());
        snapshot.put("diamondProjectStatus", getProjectStatus());
        snapshot.put("skillMastery", getSkillMasteryMap());
        snapshot.put("skillEvidenceCounts", getSkillEvidenceMap());
        snapshot.put("contentReflections", stringPreferencesWithPrefix("contentReflection_"));
        snapshot.put("contentReflectionUpdatedAt", longPreferencesWithPrefix("contentReflectionUpdatedAt_"));
        snapshot.put("contentStartedAt", longPreferencesWithPrefix("contentStarted_"));
        snapshot.put("videoWatchSeconds", intPreferencesWithPrefix("videoWatchSeconds_"));
        snapshot.put("videoProgress", intPreferencesWithPrefix("videoProgress_"));
        snapshot.put("videoEnded", booleanPreferencesWithPrefix("videoEnded_"));
        snapshot.put("localActivity", prefs.getString("localActivity", "{}"));
        snapshot.put("targetCareer", getTargetCareer());
        snapshot.put("routeType", getRouteType());
        snapshot.put("missionBadges", new ArrayList<>(getMissionBadges()));
        snapshot.put("lastRouteActivityAt", prefs.getLong("lastRouteActivityAt", 0L));
        snapshot.put("profileUpdatedAt", prefs.getLong("profileUpdatedAt", 0L));
        snapshot.put("reminderUpdatedAt", prefs.getLong("reminderUpdatedAt", 0L));
        snapshot.put("voyagePreferenceUpdatedAt", prefs.getLong("voyagePreferenceUpdatedAt", 0L));
        return snapshot;
    }

    public void applyCloudSnapshot(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return;
        SharedPreferences.Editor editor = prefs.edit();
        putString(editor, snapshot, "ageGroup");
        putString(editor, snapshot, "interest");
        putString(editor, snapshot, "goal");
        putString(editor, snapshot, "level");
        putString(editor, snapshot, "persona");
        putInt(editor, snapshot, "xp", "xp");
        String quizTier = stringValue(snapshot.get("quizTier"));
        if (!quizTier.isEmpty()) editor.putInt("quizTierRank", PromotionRules.rank(quizTier));
        Object quizTierRank = snapshot.get("quizTierRank");
        if (quizTierRank instanceof Number) editor.putInt("quizTierRank", Math.max(1, Math.min(4, ((Number) quizTierRank).intValue())));
        putStringSet(editor, snapshot, "completedContentIds", "completed");
        applyBookmarkState(editor, snapshot.get("bookmarkState"), snapshot.get("bookmarks"));
        markVerifiedIds(editor, snapshot.get("verifiedVideoIds"), "videoServerVerified_");
        putInt(editor, snapshot, "quizAttempts", "quizAttempts");
        putString(editor, snapshot, "lastQuizSummary");
        putInt(editor, snapshot, "bestQuizBronze", "bestQuiz_브론즈");
        putInt(editor, snapshot, "bestQuizSilver", "bestQuiz_실버");
        putInt(editor, snapshot, "bestQuizGold", "bestQuiz_골드");
        putInt(editor, snapshot, "bestQuizPlatinum", "bestQuiz_플래티넘");
        putBoolean(editor, snapshot, "guardianConsent", "guardianConsent");
        putString(editor, snapshot, "guardianEmail");
        putBoolean(editor, snapshot, "reminderEnabled", "reminderEnabled");
        putInt(editor, snapshot, "reminderHour", "reminderHour");
        putInt(editor, snapshot, "reminderMinute", "reminderMinute");
        putBoolean(editor, snapshot, "diamondAdvancedQuizPassed", "diamondAdvancedQuizPassed");
        putString(editor, snapshot, "targetCareer");
        putString(editor, snapshot, "routeType");
        putStringSet(editor, snapshot, "missionBadges", "missionBadges");
        Object lastRouteActivityAt = snapshot.get("lastRouteActivityAt");
        if (lastRouteActivityAt instanceof Number) editor.putLong("lastRouteActivityAt", ((Number) lastRouteActivityAt).longValue());
        String certification = stringValue(snapshot.get("diamondCertificationStatus"));
        String project = stringValue(snapshot.get("diamondProjectStatus"));
        if (!certification.isEmpty()) editor.putString("diamondCertificationStatus", certification);
        if (!project.isEmpty()) editor.putString("diamondProjectStatus", project);
        Object skillMastery = snapshot.get("skillMastery");
        if (skillMastery instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) skillMastery).entrySet()) {
                String topic = stringValue(entry.getKey());
                Object value = entry.getValue();
                if (!topic.isEmpty() && value instanceof Number) {
                    editor.putInt("skillMastery_" + normalizeSkillTopic(topic), clampPercent(((Number) value).intValue()));
                }
            }
        }
        applyNumberMap(editor, snapshot.get("skillEvidenceCounts"), "skillEvidence_", true);
        applyStringMap(editor, snapshot.get("contentReflections"), "contentReflection_");
        applyNumberMap(editor, snapshot.get("contentReflectionUpdatedAt"), "contentReflectionUpdatedAt_", false);
        applyNumberMap(editor, snapshot.get("contentStartedAt"), "contentStarted_", false);
        // Playback intervals remain device-local; only server verified content IDs are restored across devices.
        putLong(editor, snapshot, "profileUpdatedAt", "profileUpdatedAt");
        putLong(editor, snapshot, "reminderUpdatedAt", "reminderUpdatedAt");
        putLong(editor, snapshot, "voyagePreferenceUpdatedAt", "voyagePreferenceUpdatedAt");
        String localActivity = stringValue(snapshot.get("localActivity"));
        if (!localActivity.isEmpty()) editor.putString("localActivity", localActivity);
        editor.apply();
    }

    private void applyBookmarkState(SharedPreferences.Editor editor, Object rawState, Object fallbackBookmarks) {
        Set<String> active = new HashSet<>();
        JSONObject stored = new JSONObject();
        if (rawState instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawState).entrySet()) {
                String id = stringValue(entry.getKey());
                if (id.isEmpty() || !(entry.getValue() instanceof Map)) continue;
                Map<?, ?> value = (Map<?, ?>) entry.getValue();
                boolean enabled = booleanValue(value.get("active"));
                long updatedAt = value.get("updatedAt") instanceof Number ? ((Number) value.get("updatedAt")).longValue() : 0L;
                try {
                    JSONObject item = new JSONObject();
                    item.put("active", enabled);
                    item.put("updatedAt", Math.max(0L, updatedAt));
                    stored.put(id, item);
                } catch (JSONException ignored) {}
                if (enabled) active.add(id);
            }
        } else {
            active.addAll(stringSetValue(fallbackBookmarks));
            for (String id : active) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("active", true);
                    item.put("updatedAt", 0L);
                    stored.put(id, item);
                } catch (JSONException ignored) {}
            }
        }
        editor.putStringSet("bookmarks", active).putString("bookmarkState", stored.toString());
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        return "true".equalsIgnoreCase(stringValue(value));
    }

    private Set<String> stringSetValue(Object value) {
        Set<String> result = new HashSet<>();
        if (!(value instanceof Iterable)) return result;
        for (Object item : (Iterable<?>) value) {
            String text = stringValue(item);
            if (!text.isEmpty()) result.add(text);
        }
        return result;
    }

    private Map<String, String> stringPreferencesWithPrefix(String prefix) {
        Map<String, String> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof String) {
                values.put(entry.getKey().substring(prefix.length()), (String) entry.getValue());
            }
        }
        return values;
    }

    private Map<String, Long> longPreferencesWithPrefix(String prefix) {
        Map<String, Long> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof Long) {
                values.put(entry.getKey().substring(prefix.length()), (Long) entry.getValue());
            }
        }
        return values;
    }

    private Map<String, Integer> intPreferencesWithPrefix(String prefix) {
        Map<String, Integer> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof Integer) {
                values.put(entry.getKey().substring(prefix.length()), (Integer) entry.getValue());
            }
        }
        return values;
    }

    private Map<String, Boolean> booleanPreferencesWithPrefix(String prefix) {
        Map<String, Boolean> values = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(prefix) && entry.getValue() instanceof Boolean) {
                values.put(entry.getKey().substring(prefix.length()), (Boolean) entry.getValue());
            }
        }
        return values;
    }

    private void markVerifiedIds(SharedPreferences.Editor editor, Object raw, String prefix) {
        if (!(raw instanceof Iterable)) return;
        for (Object item : (Iterable<?>) raw) {
            String id = stringValue(item);
            if (!id.isEmpty()) editor.putBoolean(prefix + id, true);
        }
    }

    private void applyStringMap(SharedPreferences.Editor editor, Object raw, String prefix) {
        if (!(raw instanceof Map)) return;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String key = stringValue(entry.getKey());
            if (!key.isEmpty()) editor.putString(prefix + key, stringValue(entry.getValue()));
        }
    }

    private void applyNumberMap(SharedPreferences.Editor editor, Object raw, String prefix, boolean asInt) {
        if (!(raw instanceof Map)) return;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String key = stringValue(entry.getKey());
            if (key.isEmpty() || !(entry.getValue() instanceof Number)) continue;
            Number number = (Number) entry.getValue();
            if (asInt) editor.putInt(prefix + key, Math.max(0, number.intValue()));
            else editor.putLong(prefix + key, Math.max(0L, number.longValue()));
        }
    }

    private void applyBooleanMap(SharedPreferences.Editor editor, Object raw, String prefix) {
        if (!(raw instanceof Map)) return;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            String key = stringValue(entry.getKey());
            if (!key.isEmpty() && entry.getValue() instanceof Boolean) {
                editor.putBoolean(prefix + key, (Boolean) entry.getValue());
            }
        }
    }

    private void putString(SharedPreferences.Editor editor, Map<String, Object> snapshot, String key) {
        String value = stringValue(snapshot.get(key));
        if (!value.isEmpty()) editor.putString(key, value);
    }

    private void putInt(SharedPreferences.Editor editor, Map<String, Object> snapshot, String sourceKey, String targetKey) {
        Object value = snapshot.get(sourceKey);
        if (value instanceof Number) editor.putInt(targetKey, ((Number) value).intValue());
    }

    private void putLong(SharedPreferences.Editor editor, Map<String, Object> snapshot, String sourceKey, String targetKey) {
        Object value = snapshot.get(sourceKey);
        if (value instanceof Number) editor.putLong(targetKey, Math.max(0L, ((Number) value).longValue()));
    }

    private void putBoolean(SharedPreferences.Editor editor, Map<String, Object> snapshot, String sourceKey, String targetKey) {
        Object value = snapshot.get(sourceKey);
        if (value instanceof Boolean) editor.putBoolean(targetKey, (Boolean) value);
    }

    private void putStringSet(SharedPreferences.Editor editor, Map<String, Object> snapshot, String sourceKey, String targetKey) {
        Object value = snapshot.get(sourceKey);
        if (!(value instanceof Iterable)) return;
        Set<String> result = new HashSet<>();
        for (Object item : (Iterable<?>) value) {
            String text = stringValue(item);
            if (!text.isEmpty()) result.add(text);
        }
        editor.putStringSet(targetKey, result);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeStatus(String status) {
        if (status == null || status.trim().isEmpty()) return "not_submitted";
        return status;
    }

    public void reset() {
        secureTokenStore.clear();
        prefs.edit().clear().apply();
    }
}
