package de.julien.flightradius;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class CustomAlertRules {
    static final String MODE_OFF = "off";
    static final String MODE_CUSTOM = "custom";
    static final int INTERVAL_ONE_MINUTE = 1;

    private CustomAlertRules() { }

    static String mode(Context context) {
        return customSquawks(context).isEmpty() ? MODE_OFF : MODE_CUSTOM;
    }

    static void setMode(Context context, String mode) {
        if (MODE_OFF.equals(mode)) saveSquawks(AppPreferences.get(context), new ArrayList<>());
    }

    static int intervalMinutes(Context context) {
        return INTERVAL_ONE_MINUTE;
    }

    static void setIntervalMinutes(Context context, int minutes) {
        AppPreferences.get(context).edit().putInt(
                AppPreferences.KEY_SQUAWK_ALERT_INTERVAL_MINUTES,
                INTERVAL_ONE_MINUTE).apply();
    }

    static String customSquawk(Context context) {
        List<String> values = customSquawks(context);
        return values.isEmpty() ? "" : values.get(0);
    }

    static void setCustomSquawk(Context context, String value) {
        if (!validSquawk(value)) throw new IllegalArgumentException("Invalid squawk");
        List<String> values = new ArrayList<>();
        values.add(value);
        saveSquawks(AppPreferences.get(context), values);
    }

    static String querySquawk(Context context) {
        return android.text.TextUtils.join(",", customSquawks(context));
    }

    static List<String> customSquawks(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        ensureMigrated(preferences);
        return readSquawks(preferences.getString(
                AppPreferences.KEY_SQUAWK_CUSTOM_CODES, "[]"));
    }

    static boolean addCustomSquawk(Context context, String value) {
        if (!validSquawk(value)) throw new IllegalArgumentException("Invalid squawk");
        List<String> values = customSquawks(context);
        if (values.contains(value)) return false;
        values.add(value);
        saveSquawks(AppPreferences.get(context), values);
        return true;
    }

    static void removeCustomSquawk(Context context, String value) {
        List<String> values = customSquawks(context);
        values.remove(value);
        saveSquawks(AppPreferences.get(context), values);
    }

    static boolean validSquawk(String value) {
        return value != null && value.matches("[0-7]{4}");
    }

    static long requestedIntervalMs(int minutes) {
        return 60_000L;
    }

    static long airplanesIntervalMs(int minutes, boolean businessAuthorized) {
        long requested = requestedIntervalMs(minutes);
        // The free plan allows 500 requests/day. While global squawk alerts are
        // enabled, point and fallback squawk calls share that allowance.
        return businessAuthorized ? requested : Math.max(requested, 360_000L);
    }

    private static List<String> readSquawks(String raw) {
        Set<String> values = new LinkedHashSet<>();
        try {
            JSONArray stored = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < stored.length(); i++) {
                String value = stored.optString(i, "").trim();
                if (validSquawk(value)) values.add(value);
            }
        } catch (Exception ignored) { }
        return new ArrayList<>(values);
    }

    private static void saveSquawks(SharedPreferences preferences, List<String> values) {
        JSONArray stored = new JSONArray();
        for (String value : new LinkedHashSet<>(values)) {
            if (validSquawk(value)) stored.put(value);
        }
        preferences.edit()
                .putString(AppPreferences.KEY_SQUAWK_CUSTOM_CODES, stored.toString())
                .putString(AppPreferences.KEY_SQUAWK_ALERT_MODE,
                        stored.length() == 0 ? MODE_OFF : MODE_CUSTOM)
                .putInt(AppPreferences.KEY_SQUAWK_ALERT_INTERVAL_MINUTES,
                        INTERVAL_ONE_MINUTE)
                .remove(AppPreferences.KEY_SQUAWK_CUSTOM_CODE)
                .apply();
    }

    private static void ensureMigrated(SharedPreferences preferences) {
        if (preferences.contains(AppPreferences.KEY_SQUAWK_CUSTOM_CODES)) return;
        List<String> migrated = new ArrayList<>();
        String oldCustom = preferences.getString(AppPreferences.KEY_SQUAWK_CUSTOM_CODE, "");
        boolean legacyModeStored = preferences.contains(AppPreferences.KEY_SQUAWK_ALERT_MODE);
        boolean legacyEnabled = MODE_CUSTOM.equals(preferences.getString(
                AppPreferences.KEY_SQUAWK_ALERT_MODE, MODE_OFF));
        if ((!legacyModeStored || legacyEnabled) && validSquawk(oldCustom)) {
            migrated.add(oldCustom);
        }
        if (!legacyModeStored) try {
            JSONArray legacy = new JSONArray(preferences.getString(
                    AppPreferences.KEY_ALERT_RULES, "[]"));
            for (int i = 0; i < legacy.length(); i++) {
                JSONObject rule = legacy.optJSONObject(i);
                if (rule == null || !rule.optBoolean("enabled", true)
                        || !"squawk".equals(rule.optString("type", ""))) continue;
                String candidate = rule.optString("squawk", "");
                if (validSquawk(candidate) && !migrated.contains(candidate)) migrated.add(candidate);
            }
        } catch (Exception ignored) { }
        saveSquawks(preferences, migrated);
    }
}
