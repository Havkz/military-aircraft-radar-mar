package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class AircraftData {
    private static final double MAX_LAST_POSITION_AGE_SECONDS = 60d;
    private static final Pattern ICAO_HELICOPTER_DESCRIPTION =
            Pattern.compile("^H[0-9][A-Z]$");
    private static final Pattern CALLSIGN_CONTENT = Pattern.compile(".*[A-Z0-9].*");
    private static final String[] POSITION_FIELDS = {
            "lat", "lon", "seen_pos", "lastPosition", "alt_baro", "alt_geom",
            "gs", "track", "baro_rate", "geom_rate", "type", "mlat", "tisb",
            "_cache_age_seconds"
    };

    private AircraftData() { }

    static JSONArray tagSource(JSONArray aircraft, String source, double cacheAgeSeconds)
            throws JSONException {
        JSONArray tagged = aircraft == null ? new JSONArray() : aircraft;
        for (int i = 0; i < tagged.length(); i++) {
            JSONObject plane = tagged.optJSONObject(i);
            if (plane == null) continue;
            JSONArray sources = new JSONArray();
            sources.put(source);
            plane.put("sources", sources);
            plane.put("_cache_age_seconds", Math.max(0d, cacheAgeSeconds));
        }
        return tagged;
    }

    static JSONArray mergeByHex(JSONArray... feeds) throws JSONException {
        Map<String, JSONObject> byHex = new LinkedHashMap<>();
        JSONArray withoutHex = new JSONArray();
        if (feeds != null) {
            for (JSONArray feed : feeds) appendMerged(byHex, withoutHex, feed);
        }
        JSONArray merged = new JSONArray();
        for (JSONObject aircraft : byHex.values()) merged.put(aircraft);
        for (int i = 0; i < withoutHex.length(); i++) merged.put(withoutHex.opt(i));
        return merged;
    }

    static double[] recentPosition(JSONObject aircraft) {
        return recentPosition(aircraft, MAX_LAST_POSITION_AGE_SECONDS);
    }

    static double[] recentPosition(JSONObject aircraft, double maxAgeSeconds) {
        if (aircraft == null) return null;
        double cacheAge = aircraft.optDouble("_cache_age_seconds", 0d);
        double latitude = aircraft.optDouble("lat", Double.NaN);
        double longitude = aircraft.optDouble("lon", Double.NaN);
        double seenPosition = aircraft.optDouble("seen_pos",
                aircraft.optDouble("seen", 0d));
        if (seenPosition >= 0d && seenPosition + cacheAge <= maxAgeSeconds
                && validPosition(latitude, longitude)) return new double[]{latitude, longitude};

        JSONObject lastPosition = aircraft.optJSONObject("lastPosition");
        if (lastPosition == null) return null;
        double ageSeconds = lastPosition.optDouble("seen_pos", Double.POSITIVE_INFINITY);
        latitude = lastPosition.optDouble("lat", Double.NaN);
        longitude = lastPosition.optDouble("lon", Double.NaN);
        if (ageSeconds < 0d || ageSeconds + cacheAge > maxAgeSeconds
                || !validPosition(latitude, longitude)) return null;
        return new double[]{latitude, longitude};
    }

    static String displayName(JSONObject aircraft) {
        String callsign = callsign(aircraft);
        return callsign.isEmpty() ? "NO CALLSIGN" : callsign;
    }

    static String callsign(JSONObject aircraft) {
        return aircraft == null ? "" : normalizeCallsign(aircraft.optString("flight", ""));
    }

    static String normalizeCallsign(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.US);
        if (value.isEmpty() || "N/A".equals(value) || "NONE".equals(value)
                || "NULL".equals(value) || "UNKNOWN".equals(value)
                || !CALLSIGN_CONTENT.matcher(value).matches()) return "";
        return value;
    }

    static boolean isRotorcraft(JSONObject aircraft) {
        if (aircraft == null) return false;
        if ("A7".equalsIgnoreCase(aircraft.optString("category", ""))) return true;
        String type = aircraft.optString("t", "").trim();
        if (type.isEmpty()) type = aircraft.optString("type", "").trim();
        type = type.toUpperCase(Locale.US);
        if ("G2CA".equals(type)) return true;
        String description = (aircraft.optString("desc", "") + " "
                + aircraft.optString("typeDescription", "")).toUpperCase(Locale.US);
        String compactDescription = aircraft.optString("desc", "")
                .trim().toUpperCase(Locale.US);
        return description.contains("HELICOPTER")
                || description.contains("ROTORCRAFT")
                || ICAO_HELICOPTER_DESCRIPTION.matcher(compactDescription).matches();
    }

    static boolean isOnGround(JSONObject aircraft) {
        if (aircraft == null) return false;
        Object barometricAltitude = aircraft.opt("alt_baro");
        return aircraft.optBoolean("ground", false)
                || barometricAltitude instanceof String
                && "ground".equalsIgnoreCase(((String) barometricAltitude).trim());
    }

    private static void appendMerged(Map<String, JSONObject> byHex, JSONArray withoutHex,
                                     JSONArray source) throws JSONException {
        if (source == null) return;
        for (int i = 0; i < source.length(); i++) {
            JSONObject aircraft = source.optJSONObject(i);
            if (aircraft == null) continue;
            String hex = aircraft.optString("hex", "")
                    .replace("~", "").trim().toLowerCase(Locale.US);
            if (hex.isEmpty()) {
                withoutHex.put(aircraft);
                continue;
            }
            JSONObject existing = byHex.get(hex);
            if (existing == null) {
                byHex.put(hex, aircraft);
            } else {
                mergeSources(existing, aircraft);
                existing.put("dbFlags", existing.optInt("dbFlags", 0)
                        | aircraft.optInt("dbFlags", 0));
                if (positionAgeSeconds(aircraft) < positionAgeSeconds(existing)) {
                    for (String key : POSITION_FIELDS) {
                        if (aircraft.has(key) && !aircraft.isNull(key)) {
                            existing.put(key, aircraft.opt(key));
                        }
                    }
                }
                Iterator<String> keys = aircraft.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object incoming = aircraft.opt(key);
                    boolean missing = !existing.has(key) || existing.isNull(key);
                    boolean incomplete = !meaningful(existing.opt(key))
                            && meaningful(incoming);
                    if ((missing && meaningful(incoming)) || incomplete) {
                        existing.put(key, incoming);
                    }
                }
            }
        }
    }

    static boolean meaningful(Object value) {
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof String) {
            String text = ((String) value).trim().toLowerCase(Locale.US);
            return !text.isEmpty() && !"-".equals(text) && !"—".equals(text)
                    && !"n/a".equals(text) && !"na".equals(text)
                    && !"none".equals(text) && !"null".equals(text)
                    && !"undefined".equals(text)
                    && !"unknown".equals(text) && !"unknown type".equals(text)
                    && !"no callsign".equals(text);
        }
        if (value instanceof JSONArray) return ((JSONArray) value).length() > 0;
        return true;
    }

    private static void mergeSources(JSONObject existing, JSONObject incoming)
            throws JSONException {
        JSONArray merged = new JSONArray();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        appendSources(merged, seen, existing.optJSONArray("sources"));
        appendSources(merged, seen, incoming.optJSONArray("sources"));
        existing.put("sources", merged);
    }

    private static void appendSources(JSONArray target, Map<String, Boolean> seen,
                                      JSONArray sources) {
        if (sources == null) return;
        for (int i = 0; i < sources.length(); i++) {
            String source = sources.optString(i, "");
            if (!source.isEmpty() && seen.put(source, Boolean.TRUE) == null) target.put(source);
        }
    }

    private static double positionAgeSeconds(JSONObject aircraft) {
        if (aircraft == null) return Double.POSITIVE_INFINITY;
        double cacheAge = aircraft.optDouble("_cache_age_seconds", 0d);
        if (validPosition(aircraft.optDouble("lat", Double.NaN),
                aircraft.optDouble("lon", Double.NaN))) {
            return Math.max(0d, aircraft.optDouble("seen_pos",
                    aircraft.optDouble("seen", 0d))) + cacheAge;
        }
        JSONObject last = aircraft.optJSONObject("lastPosition");
        if (last == null || !validPosition(last.optDouble("lat", Double.NaN),
                last.optDouble("lon", Double.NaN))) return Double.POSITIVE_INFINITY;
        return Math.max(0d, last.optDouble("seen_pos", Double.POSITIVE_INFINITY)) + cacheAge;
    }

    private static boolean validPosition(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }
}
