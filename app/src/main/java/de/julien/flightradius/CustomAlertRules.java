package de.julien.flightradius;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

final class CustomAlertRules {
    static final String MODE_OFF = "off";
    static final String MODE_CUSTOM = "custom";
    static final int INTERVAL_ONE_MINUTE = 1;
    static final int INTERVAL_FIVE_MINUTES = 5;

    private CustomAlertRules() { }

    static String mode(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        if (preferences.contains(AppPreferences.KEY_SQUAWK_ALERT_MODE)) {
            String stored = preferences.getString(
                    AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_OFF);
            String normalized = normalizeMode(stored);
            if (!normalized.equals(stored)) {
                preferences.edit()
                        .putString(AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_OFF)
                        .remove(AppPreferences.KEY_SQUAWK_CUSTOM_CODE).apply();
            }
            return normalized;
        }
        migrateLegacyRules(preferences);
        return normalizeMode(preferences.getString(
                AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_OFF));
    }

    static void setMode(Context context, String mode) {
        AppPreferences.get(context).edit()
                .putString(AppPreferences.KEY_SQUAWK_ALERT_MODE, normalizeMode(mode)).apply();
    }

    static int intervalMinutes(Context context) {
        int value = AppPreferences.get(context).getInt(
                AppPreferences.KEY_SQUAWK_ALERT_INTERVAL_MINUTES,
                INTERVAL_FIVE_MINUTES);
        return value == INTERVAL_ONE_MINUTE
                ? INTERVAL_ONE_MINUTE : INTERVAL_FIVE_MINUTES;
    }

    static void setIntervalMinutes(Context context, int minutes) {
        AppPreferences.get(context).edit().putInt(
                AppPreferences.KEY_SQUAWK_ALERT_INTERVAL_MINUTES,
                minutes == INTERVAL_ONE_MINUTE
                        ? INTERVAL_ONE_MINUTE : INTERVAL_FIVE_MINUTES).apply();
    }

    static String customSquawk(Context context) {
        String value = AppPreferences.get(context).getString(
                AppPreferences.KEY_SQUAWK_CUSTOM_CODE, "");
        return validSquawk(value) ? value : "";
    }

    static void setCustomSquawk(Context context, String value) {
        if (!validSquawk(value)) throw new IllegalArgumentException("Invalid squawk");
        AppPreferences.get(context).edit()
                .putString(AppPreferences.KEY_SQUAWK_CUSTOM_CODE, value).apply();
    }

    static String querySquawk(Context context) {
        return MODE_CUSTOM.equals(mode(context)) ? customSquawk(context) : "";
    }

    static boolean validSquawk(String value) {
        return value != null && value.matches("[0-7]{4}");
    }

    static long requestedIntervalMs(int minutes) {
        return (minutes == INTERVAL_ONE_MINUTE
                ? INTERVAL_ONE_MINUTE : INTERVAL_FIVE_MINUTES) * 60_000L;
    }

    static long airplanesIntervalMs(int minutes, boolean businessAuthorized) {
        long requested = requestedIntervalMs(minutes);
        // The free plan allows 500 requests/day. While global squawk alerts are
        // enabled, point and fallback squawk calls share that allowance.
        return businessAuthorized ? requested : Math.max(requested, 360_000L);
    }

    private static String normalizeMode(String value) {
        return MODE_CUSTOM.equals(value) ? MODE_CUSTOM : MODE_OFF;
    }

    private static void migrateLegacyRules(SharedPreferences preferences) {
        String custom = "";
        try {
            JSONArray legacy = new JSONArray(preferences.getString(
                    AppPreferences.KEY_ALERT_RULES, "[]"));
            for (int i = 0; i < legacy.length(); i++) {
                JSONObject rule = legacy.optJSONObject(i);
                if (rule == null || !rule.optBoolean("enabled", true)
                        || !"squawk".equals(rule.optString("type", ""))) continue;
                String candidate = rule.optString("squawk", "");
                if (validSquawk(candidate)) {
                    custom = candidate;
                    break;
                }
            }
        } catch (Exception ignored) { }
        SharedPreferences.Editor editor = preferences.edit();
        if (custom.isEmpty()) {
            editor.putString(AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_OFF);
        } else {
            editor.putString(AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_CUSTOM)
                    .putString(AppPreferences.KEY_SQUAWK_CUSTOM_CODE, custom);
        }
        editor.apply();
    }
}
