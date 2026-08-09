package de.julien.flightradius;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MilitaryClassifier {
    private static final int DATABASE_MILITARY_FLAG = 1;
    private static final Pattern CALLSIGN_PREFIX = Pattern.compile("^([A-Z]{2,6})[0-9]");
    private static final Pattern GERMAN_MILITARY_REGISTRATION =
            Pattern.compile("^[0-9]{2}\\+[0-9]{2}$");
    private static final Set<String> MILITARY_CALLSIGN_PREFIXES = new HashSet<>(Arrays.asList(
            "GAF", "GAM", "GNY", "BAF", "FAF", "HAF", "IAM", "AME", "PLF",
            "CEF", "DAF", "NAF", "PAF", "RCH", "CNV", "PAT", "ASY", "RFR",
            "RRR", "NATO", "MMF", "TUAF", "USAF", "ARMY", "NAVY"));
    private static final Set<String> MILITARY_TYPE_CODES = new HashSet<>(Arrays.asList(
            "A400", "C17", "C130", "C30J", "H47", "F15", "F16", "F18", "F22",
            "F35", "EUFI", "TOR", "B1", "B2", "B52", "U2", "K35R", "KC10",
            "KC46", "E3TF"));
    private static final String[] MILITARY_TEXT_MARKERS = {
            "MILITARY", "AIR FORCE", "LUFTWAFFE", "BUNDESWEHR", "ARMED FORCES",
            "ROYAL AIR FORCE", "UNITED STATES ARMY", "UNITED STATES NAVY",
            "US ARMY", "US NAVY", "MARINE CORPS", "NATO"
    };

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
        if (sources != null) {
            for (int i = 0; i < sources.length(); i++) {
                String source = normalize(sources.optString(i, ""));
                if (source.endsWith(" MILITARY") || source.equals("MILITARY")) return true;
            }
        }
        String callsign = firstText(aircraft, "flight", "callsign", "callSign");
        String registration = firstText(aircraft, "r", "registration");
        String type = firstText(aircraft, "t", "icaoType", "aircraftType");
        String description = firstText(aircraft, "desc", "typeDescription", "description");
        String operator = firstText(aircraft, "ownOp", "operator", "operatorName");
        return heuristicMilitary(type, callsign, registration, description, operator);
    }

    static boolean isMilitary(int databaseFlags, String rawType, String rawCallsign) {
        return isMilitary(databaseFlags, rawType, rawCallsign, "");
    }

    static boolean isMilitary(int databaseFlags, String rawType, String rawCallsign,
                              String rawRegistration) {
        return (databaseFlags & DATABASE_MILITARY_FLAG) != 0
                || heuristicMilitary(rawType, rawCallsign, rawRegistration, "", "");
    }

    private static boolean heuristicMilitary(String rawType, String rawCallsign,
                                              String rawRegistration,
                                              String rawDescription, String rawOperator) {
        String callsign = normalize(rawCallsign).replace(" ", "");
        Matcher prefix = CALLSIGN_PREFIX.matcher(callsign);
        if (prefix.find() && MILITARY_CALLSIGN_PREFIXES.contains(prefix.group(1))) return true;
        String registration = normalize(rawRegistration).replace(" ", "");
        if (GERMAN_MILITARY_REGISTRATION.matcher(registration).matches()) return true;
        if (MILITARY_TYPE_CODES.contains(normalize(rawType).replace("-", ""))) return true;
        String evidence = normalize(rawDescription) + " " + normalize(rawOperator);
        for (String marker : MILITARY_TEXT_MARKERS) {
            if (evidence.contains(marker)) return true;
        }
        return false;
    }

    private static String firstText(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

}
