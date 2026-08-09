package de.julien.flightradius;

import org.json.JSONObject;

import java.util.Locale;

final class MilitaryClassifier {
    private static final int DATABASE_MILITARY_FLAG = 1;

    private MilitaryClassifier() { }

    static boolean isMilitary(JSONObject aircraft) {
        if (aircraft == null) return false;
        int databaseFlags = aircraft.optInt("dbFlags",
                aircraft.optInt("db_flags", 0));
        if ((databaseFlags & DATABASE_MILITARY_FLAG) != 0) return true;
        if (aircraft.optBoolean("military", false)
                || aircraft.optBoolean("isMilitary", false)
                || aircraft.optBoolean("is_military", false)
                || aircraft.optBoolean("_provider_military", false)) return true;
        org.json.JSONArray sources = aircraft.optJSONArray("sources");
        if (sources == null) return false;
        for (int i = 0; i < sources.length(); i++) {
            String source = normalize(sources.optString(i, ""));
            if (source.endsWith(" MILITARY") || source.equals("MILITARY")) return true;
        }
        return false;
    }

    static boolean isMilitary(int databaseFlags, String rawType, String rawCallsign) {
        return isMilitary(databaseFlags, rawType, rawCallsign, "");
    }

    static boolean isMilitary(int databaseFlags, String rawType, String rawCallsign,
                              String rawRegistration) {
        return (databaseFlags & DATABASE_MILITARY_FLAG) != 0;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

}
