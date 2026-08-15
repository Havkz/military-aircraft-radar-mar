package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class AircraftData {
    private static final double MAX_LAST_POSITION_AGE_SECONDS = 60d;
    private static final Pattern ICAO_HELICOPTER_DESCRIPTION =
            Pattern.compile("^H[0-9][A-Z]$");
    private static final Pattern BELL_407_DESCRIPTION =
            Pattern.compile(".*\\bBELL\\s+407\\b.*");
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

    static JSONArray mergeFr24OnlyForTisb(JSONArray flightradar24,
                                           JSONArray... supplemental)
            throws JSONException {
        resolveNonIcaoAliases(flightradar24, supplemental);
        Set<String> restrictedHexes = tisbHexes(supplemental);
        JSONArray[] feeds = new JSONArray[(supplemental == null ? 0 : supplemental.length) + 1];
        feeds[0] = flightradar24;
        for (int i = 0; supplemental != null && i < supplemental.length; i++) {
            JSONArray filtered = new JSONArray();
            JSONArray source = supplemental[i];
            for (int j = 0; source != null && j < source.length(); j++) {
                JSONObject aircraft = source.optJSONObject(j);
                if (aircraft == null || isTisbSource(aircraft)
                        || restrictedHexes.contains(normalizedHex(aircraft))) continue;
                filtered.put(aircraft);
            }
            feeds[i + 1] = filtered;
        }
        return mergeByHex(feeds);
    }

    static void resolveNonIcaoAliases(JSONArray aircraft, JSONArray... references) {
        if (aircraft == null) return;
        Map<String, Map<String, JSONObject>> byRegistration = new HashMap<>();
        Map<String, Map<String, JSONObject>> byCallsign = new HashMap<>();
        if (references != null) {
            for (JSONArray reference : references) {
                for (int i = 0; reference != null && i < reference.length(); i++) {
                    JSONObject candidate = reference.optJSONObject(i);
                    String hex = normalizedHex(candidate);
                    if (candidate == null || hex.isEmpty()
                            || isNonIcao(candidate) || isTisbSource(candidate)) continue;
                    indexCandidate(byRegistration, normalizedRegistration(candidate),
                            hex, candidate);
                    indexCandidate(byCallsign, identityCallsign(candidate), hex, candidate);
                }
            }
        }
        for (int i = 0; i < aircraft.length(); i++) {
            JSONObject synthetic = aircraft.optJSONObject(i);
            String alias = normalizedHex(synthetic);
            if (synthetic == null || alias.isEmpty()
                    || !isNonIcao(synthetic)) continue;
            String realHex = uniqueMatchingRealHex(
                    synthetic, byRegistration, byCallsign);
            if (realHex.isEmpty() || realHex.equals(alias)) continue;
            try {
                synthetic.put("_non_icao_alias", alias);
                synthetic.put("hex", realHex);
                if (synthetic.has("non_icao")) synthetic.put("non_icao", false);
            } catch (JSONException ignored) { }
        }
    }

    private static void indexCandidate(
            Map<String, Map<String, JSONObject>> index, String identity,
            String hex, JSONObject candidate) {
        if (identity.isEmpty()) return;
        Map<String, JSONObject> matches = index.get(identity);
        if (matches == null) {
            matches = new LinkedHashMap<>();
            index.put(identity, matches);
        }
        JSONObject previous = matches.get(hex);
        if (previous == null
                || positionAgeSeconds(candidate) < positionAgeSeconds(previous)) {
            matches.put(hex, candidate);
        }
    }

    private static String uniqueMatchingRealHex(
            JSONObject synthetic,
            Map<String, Map<String, JSONObject>> byRegistration,
            Map<String, Map<String, JSONObject>> byCallsign) {
        String registration = normalizedRegistration(synthetic);
        Map<String, JSONObject> registrationMatches = new LinkedHashMap<>();
        Map<String, JSONObject> motionMatches = new LinkedHashMap<>();
        String callsign = identityCallsign(synthetic);
        if (!callsign.matches("[A-Z0-9]{4,8}")) callsign = "";
        Map<String, JSONObject> registrationCandidates = byRegistration.get(registration);
        if (registrationCandidates != null) {
            for (Map.Entry<String, JSONObject> entry : registrationCandidates.entrySet()) {
                if (positionsNear(synthetic, entry.getValue(), 50d)) {
                    registrationMatches.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Map<String, JSONObject> motionCandidates = byCallsign.get(callsign);
        if (motionCandidates != null) {
            for (Map.Entry<String, JSONObject> entry : motionCandidates.entrySet()) {
                if (sameMovingContact(synthetic, entry.getValue())) {
                    motionMatches.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (registrationMatches.size() == 1) {
            return registrationMatches.keySet().iterator().next();
        }
        return motionMatches.size() == 1
                ? motionMatches.keySet().iterator().next() : "";
    }

    private static boolean sameMovingContact(JSONObject first, JSONObject second) {
        if (!positionsNear(first, second, 12d)) return false;
        boolean comparedKinematics = false;
        double firstAltitude = altitudeFeet(first);
        double secondAltitude = altitudeFeet(second);
        if (!Double.isNaN(firstAltitude) && !Double.isNaN(secondAltitude)) {
            comparedKinematics = true;
            if (Math.abs(firstAltitude - secondAltitude) > 1_500d) return false;
        }
        double firstSpeed = speedKnots(first);
        double secondSpeed = speedKnots(second);
        if (!Double.isNaN(firstSpeed) && !Double.isNaN(secondSpeed)) {
            comparedKinematics = true;
            if (Math.abs(firstSpeed - secondSpeed) > 80d) return false;
        }
        if (firstSpeed >= 80d && secondSpeed >= 80d) {
            double firstTrack = first.optDouble("track", Double.NaN);
            double secondTrack = second.optDouble("track", Double.NaN);
            if (!Double.isNaN(firstTrack) && !Double.isNaN(secondTrack)) {
                double difference = Math.abs(
                        ((firstTrack - secondTrack + 540d) % 360d) - 180d);
                if (difference > 60d) return false;
            }
        }
        return comparedKinematics;
    }

    private static boolean positionsNear(
            JSONObject first, JSONObject second, double maximumKm) {
        double[] firstPosition = recentPosition(first, 90d);
        double[] secondPosition = recentPosition(second, 90d);
        if (firstPosition == null || secondPosition == null) return false;
        double distance = DistanceCalculator.kilometers(firstPosition[0], firstPosition[1],
                secondPosition[0], secondPosition[1]);
        return !Double.isNaN(distance) && distance <= maximumKm;
    }

    private static String identityCallsign(JSONObject aircraft) {
        if (aircraft == null) return "";
        return normalizeCallsign(aircraft.optString("flight",
                aircraft.optString("callsign", "")));
    }

    private static String normalizedRegistration(JSONObject aircraft) {
        if (aircraft == null) return "";
        for (String key : new String[]{"r", "registration", "reg"}) {
            String registration = aircraft.optString(key, "");
            if (meaningful(registration)) {
                return registration.trim().toUpperCase(Locale.US)
                        .replaceAll("[^A-Z0-9]", "");
            }
        }
        return "";
    }

    private static double altitudeFeet(JSONObject aircraft) {
        if (aircraft == null) return Double.NaN;
        for (String key : new String[]{"alt_geom", "alt_baro", "altitude_ft",
                "geometric_altitude_ft", "barometric_altitude_ft"}) {
            Object value = aircraft.opt(key);
            if (value instanceof Number) return ((Number) value).doubleValue();
            if (value instanceof String
                    && "ground".equalsIgnoreCase(((String) value).trim())) return 0d;
        }
        return Double.NaN;
    }

    private static double speedKnots(JSONObject aircraft) {
        if (aircraft == null) return Double.NaN;
        double speed = aircraft.optDouble("gs", Double.NaN);
        return Double.isNaN(speed)
                ? aircraft.optDouble("speed_knots", Double.NaN) : speed;
    }

    private static boolean isNonIcao(JSONObject aircraft) {
        return aircraft != null && (aircraft.optBoolean("non_icao", false)
                || aircraft.optString("hex", "").trim().startsWith("~"));
    }

    static JSONArray withoutTisbAircraft(JSONArray source) {
        JSONArray filtered = new JSONArray();
        Set<String> restrictedHexes = tisbHexes(source);
        for (int i = 0; source != null && i < source.length(); i++) {
            JSONObject aircraft = source.optJSONObject(i);
            if (aircraft != null && !isTisbSource(aircraft)
                    && !restrictedHexes.contains(normalizedHex(aircraft))) {
                filtered.put(aircraft);
            }
        }
        return filtered;
    }

    static Set<String> tisbHexes(JSONArray... feeds) {
        Set<String> result = new HashSet<>();
        if (feeds == null) return result;
        for (JSONArray feed : feeds) {
            for (int i = 0; feed != null && i < feed.length(); i++) {
                JSONObject aircraft = feed.optJSONObject(i);
                String hex = normalizedHex(aircraft);
                if (isTisbSource(aircraft) && !hex.isEmpty()) result.add(hex);
            }
        }
        return result;
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
        if ("G2CA".equals(type) || "B407".equals(type)
                || ICAO_HELICOPTER_DESCRIPTION.matcher(type).matches()) return true;
        String description = (aircraft.optString("desc", "") + " "
                + aircraft.optString("typeDescription", "") + " "
                + aircraft.optString("description", "")).toUpperCase(Locale.US);
        String compactDescription = aircraft.optString("desc", "")
                .trim().toUpperCase(Locale.US);
        return description.contains("HELICOPTER")
                || description.contains("ROTORCRAFT")
                || BELL_407_DESCRIPTION.matcher(description).matches()
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
                    && !"no callsign".equals(text) && !"private".equals(text)
                    && !"private owner".equals(text);
        }
        if (value instanceof JSONArray) return ((JSONArray) value).length() > 0;
        return true;
    }

    private static boolean isTisbSource(JSONObject aircraft) {
        if (aircraft == null) return false;
        for (String field : new String[]{"type", "data_source"}) {
            String source = aircraft.optString(field, "").trim()
                    .toLowerCase(Locale.US).replace('-', '_');
            if (source.matches("tisb(?:_.*)?")) return true;
        }
        return false;
    }

    private static String normalizedHex(JSONObject aircraft) {
        String hex = aircraft == null ? "" : aircraft.optString("hex", "")
                .replace("~", "").trim().toLowerCase(Locale.US);
        return hex.matches("[0-9a-f]{6}") ? hex : "";
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
