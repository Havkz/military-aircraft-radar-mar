package de.julien.flightradius;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class MilitaryClassifier {
    private static final int DATABASE_MILITARY_FLAG = 1;

    /*
     * ICAO type designators that identify purpose-built military aircraft.
     * This supplements, but never replaces, the upstream military database flag.
     * Keep mixed civilian types such as B737, A332, B350 and GLF5 out of this set.
     */
    private static final Set<String> MILITARY_TYPES = new HashSet<>(Arrays.asList(
            // Airlift, tanker and special-mission aircraft
            "A400", "C130", "C30J", "C17", "C5", "C5M", "C2", "C27J",
            "KC10", "K35R", "KC46", "K767", "E3CF", "E3TF", "E6", "E8",
            "P3", "P8", "P1", "B1", "B2", "B52", "TU95", "T160",
            "V22", "A50", "IL78",

            // Fighters, attack aircraft and military trainers
            "A10", "F4", "F5", "F14", "F15", "F16", "F18", "F22", "F35",
            "AV8B", "EUFI", "RFAL", "M200", "MG29", "TORN", "JAS39",
            "SU24", "SU25", "SU27", "SU30", "SU34", "SU35", "FA50", "J10",
            "J11", "J20", "J31", "T38", "TEX2", "HAWK", "L159", "M346",
            "T50", "PC21",

            // Purpose-built military rotorcraft and unmanned aircraft
            "H64", "AH64", "H53", "CH53", "NH90", "TIGR", "A129", "AH1",
            "KA50", "KA52", "MI24", "MI28", "RQ4", "MQ9", "Q4"
    ));

    private static final Set<String> MILITARY_CALLSIGN_PREFIXES = new HashSet<>(
            Arrays.asList("RCH", "RRR", "CNV", "PAT", "EVAC", "FORTE", "NCHO",
                    "GAF", "BAF", "FAF", "IAM", "ASY", "NOW", "AME", "CTM"));

    private MilitaryClassifier() { }

    static boolean isMilitary(JSONObject aircraft) {
        if (aircraft == null) return false;
        return isMilitary(
                aircraft.optInt("dbFlags", 0),
                aircraft.optString("t", ""),
                aircraft.optString("flight", ""));
    }

    static boolean isMilitary(int databaseFlags, String rawType, String rawCallsign) {
        if ((databaseFlags & DATABASE_MILITARY_FLAG) != 0) return true;

        String type = normalize(rawType);
        if (MILITARY_TYPES.contains(type)) return true;

        String callsign = normalize(rawCallsign).replace(" ", "");
        for (String prefix : MILITARY_CALLSIGN_PREFIXES) {
            if (callsign.startsWith(prefix) && containsDigit(callsign, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.US);
    }

    private static boolean containsDigit(String value, int start) {
        for (int i = Math.max(0, start); i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) return true;
        }
        return false;
    }
}
