package de.julien.flightradius;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CustomAlertRules {
    static final String SQUAWK = "squawk";
    static final String LOW_LEVEL = "low_level";
    static final String ALTITUDE_BELOW = "altitude_below";
    static final String SPEED_BELOW = "speed_below";
    static final String SPEED_ABOVE = "speed_above";

    static final class Rule {
        String id = Long.toHexString(System.nanoTime());
        boolean enabled = true;
        String name = "";
        String type = SQUAWK;
        String squawk = "7700";
        double threshold = 3000d;
        double verticalRate = 250d;
        int durationSeconds;

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("enabled", enabled)
                    .put("name", name).put("type", type).put("squawk", squawk)
                    .put("threshold", threshold).put("vertical_rate", verticalRate)
                    .put("duration_seconds", durationSeconds);
        }

        static Rule from(JSONObject object) {
            Rule rule = new Rule();
            rule.id = object.optString("id", rule.id);
            rule.enabled = object.optBoolean("enabled", true);
            rule.name = object.optString("name", "");
            rule.type = object.optString("type", SQUAWK);
            rule.squawk = object.optString("squawk", "7700");
            rule.threshold = object.optDouble("threshold", 3000d);
            rule.verticalRate = object.optDouble("vertical_rate", 250d);
            rule.durationSeconds = Math.max(0, object.optInt("duration_seconds", 0));
            return rule;
        }
    }

    private CustomAlertRules() { }

    static List<Rule> load(Context context) {
        return parse(AppPreferences.get(context).getString(AppPreferences.KEY_ALERT_RULES, "[]"));
    }

    static List<Rule> parse(String raw) {
        List<Rule> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object != null) result.add(Rule.from(object));
            }
        } catch (Exception ignored) { }
        return result;
    }

    static void save(Context context, List<Rule> rules) {
        JSONArray array = new JSONArray();
        try {
            for (Rule rule : rules) array.put(rule.json());
        } catch (Exception ignored) { }
        AppPreferences.get(context).edit()
                .putString(AppPreferences.KEY_ALERT_RULES, array.toString()).apply();
    }

    static String evaluate(List<Rule> rules, JSONObject aircraft, String hex,
                           double altitudeFt, long now, Map<String, Long> conditionSince) {
        for (Rule rule : rules) {
            if (!rule.enabled) continue;
            String key = rule.id + ':' + hex;
            boolean matches = conditionMatches(rule, aircraft, altitudeFt);
            if (!matches) {
                conditionSince.remove(key);
                continue;
            }
            Long since = conditionSince.get(key);
            if (since == null) {
                since = now;
                conditionSince.put(key, since);
            }
            if (now - since >= rule.durationSeconds * 1000L) return description(rule);
        }
        return "";
    }

    static boolean validSquawk(String value) {
        return value != null && value.matches("[0-7]{4}");
    }

    private static boolean conditionMatches(Rule rule, JSONObject aircraft,
                                            double altitudeFt) {
        if (SQUAWK.equals(rule.type)) {
            return validSquawk(rule.squawk)
                    && rule.squawk.equals(aircraft.optString("squawk", "").trim());
        }
        if (Double.isNaN(altitudeFt) && (LOW_LEVEL.equals(rule.type)
                || ALTITUDE_BELOW.equals(rule.type))) return false;
        if (LOW_LEVEL.equals(rule.type)) {
            double rate = aircraft.optDouble("geom_rate",
                    aircraft.optDouble("baro_rate", Double.NaN));
            return altitudeFt <= rule.threshold && !Double.isNaN(rate)
                    && Math.abs(rate) <= Math.max(0d, rule.verticalRate);
        }
        if (ALTITUDE_BELOW.equals(rule.type)) return altitudeFt <= rule.threshold;
        double speed = aircraft.optDouble("gs", Double.NaN);
        if (Double.isNaN(speed)) return false;
        if (SPEED_BELOW.equals(rule.type)) return speed <= rule.threshold;
        return SPEED_ABOVE.equals(rule.type) && speed >= rule.threshold;
    }

    static String description(Rule rule) {
        if (!rule.name.trim().isEmpty()) return rule.name.trim();
        if (SQUAWK.equals(rule.type)) return "Squawk " + rule.squawk;
        if (LOW_LEVEL.equals(rule.type)) return String.format(Locale.US,
                "Low and level below %.0f ft", rule.threshold);
        if (ALTITUDE_BELOW.equals(rule.type)) return String.format(Locale.US,
                "Altitude below %.0f ft", rule.threshold);
        if (SPEED_BELOW.equals(rule.type)) return String.format(Locale.US,
                "Speed below %.0f kt", rule.threshold);
        return String.format(Locale.US, "Speed above %.0f kt", rule.threshold);
    }
}
