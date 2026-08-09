package de.julien.flightradius;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

final class CustomAlertRules {
    static final String MODE_OFF = "off";
    static final String MODE_EMERGENCY = "emergency";
    static final String MODE_CUSTOM = "custom";
    static final int INTERVAL_ONE_MINUTE = 1;
    static final int INTERVAL_FIVE_MINUTES = 5;

    private CustomAlertRules() { }

    static String mode(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        if (preferences.contains(AppPreferences.KEY_SQUAWK_ALERT_MODE)) {
            return normalizeMode(preferences.getString(
                    AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_OFF));
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
                AppPreferences.KEY_SQUAWK_CUSTOM_CODE, "7700");
        return validSquawk(value) ? value : "7700";
    }

    static void setCustomSquawk(Context context, String value) {
        if (!validSquawk(value)) throw new IllegalArgumentException("Invalid squawk");
        AppPreferences.get(context).edit()
                .putString(AppPreferences.KEY_SQUAWK_CUSTOM_CODE, value).apply();
    }

    static String querySquawks(Context context) {
        String mode = mode(context);
        if (MODE_EMERGENCY.equals(mode)) return "7500,7600,7700";
        if (MODE_CUSTOM.equals(mode)) return customSquawk(context);
        return "";
    }

    static Set<String> selectedSquawks(Context context) {
        return parseSquawks(querySquawks(context));
    }

    static Set<String> parseSquawks(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null) return result;
        for (String code : value.split(",")) {
            String trimmed = code.trim();
            if (validSquawk(trimmed)) result.add(trimmed);
        }
        return result;
    }

    static boolean validSquawk(String value) {
        return value != null && value.matches("[0-7]{4}");
    }

    static long requestedIntervalMs(int minutes) {
        return (minutes == INTERVAL_ONE_MINUTE
                ? INTERVAL_ONE_MINUTE : INTERVAL_FIVE_MINUTES) * 60_000L;
    }

    static long airplanesIntervalMs(int minutes, boolean businessAuthorized,
                                    int selectedCodeCount) {
        long requested = requestedIntervalMs(minutes);
        // The free plan allows 500 requests/day. While global squawk alerts are
        // enabled, point and squawk calls share that allowance. Airplanes.live
        // requires one request per squawk, so the emergency preset rotates slower.
        return businessAuthorized ? requested : Math.max(requested,
                360_000L * Math.max(1, selectedCodeCount));
    }

    private static String normalizeMode(String value) {
        if (MODE_EMERGENCY.equals(value) || MODE_CUSTOM.equals(value)) return value;
        return MODE_OFF;
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
