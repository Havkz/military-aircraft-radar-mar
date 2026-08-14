package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class Flightradar24AircraftMapper {
    private static final int MAX_AIRCRAFT = 10_000;

    private Flightradar24AircraftMapper() { }

    static JSONArray map(JSONArray visibleAircraft, long receivedAtMs) {
        JSONArray mapped = new JSONArray();
        if (visibleAircraft == null) return mapped;
        int count = Math.min(visibleAircraft.length(), MAX_AIRCRAFT);
        for (int i = 0; i < count; i++) {
            JSONObject source = visibleAircraft.optJSONObject(i);
            JSONObject aircraft = mapAircraft(source, receivedAtMs);
            if (aircraft != null) mapped.put(aircraft);
        }
        return mapped;
    }

    private static JSONObject mapAircraft(JSONObject source, long receivedAtMs) {
        if (source == null) return null;
        String rawHex = source.optString("icao", "").trim().toLowerCase(Locale.US);
        boolean nonIcao = rawHex.startsWith("~");
        String normalizedHex = rawHex.replace("~", "");
        if (!normalizedHex.matches("[0-9a-f]{6}")) return null;
        String hex = nonIcao ? "~" + normalizedHex : normalizedHex;
        double latitude = source.optDouble("latitude", Double.NaN);
        double longitude = source.optDouble("longitude", Double.NaN);
        if (!validPosition(latitude, longitude)) return null;

        try {
            JSONObject aircraft = new JSONObject();
            aircraft.put("hex", hex);
            aircraft.put("lat", latitude);
            aircraft.put("lon", longitude);
            aircraft.put("seen_pos", positionAgeSeconds(source, receivedAtMs));
            putMeaningful(aircraft, "flight", source.opt("callsign"));
            putMeaningful(aircraft, "r", source.opt("registration"));
            putMeaningful(aircraft, "t", source.opt("type"));
            putFinite(aircraft, "gs", source.opt("speed"));
            putFinite(aircraft, "track", source.opt("track"));
            putFinite(aircraft, "baro_rate", source.opt("vspeed"));
            putFinite(aircraft, "alt_baro", source.opt("altitude"));
            putMeaningful(aircraft, "squawk", source.opt("squawk"));
            if (source.optBoolean("onGround", false)) {
                aircraft.put("ground", true);
                aircraft.put("alt_baro", "ground");
            }
            return aircraft;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static double positionAgeSeconds(JSONObject source, long receivedAtMs) {
        double timestamp = source.optDouble("timestamp", Double.NaN);
        if (Double.isNaN(timestamp)) {
            double timestampMs = source.optDouble("timestampMs", Double.NaN);
            if (!Double.isNaN(timestampMs)) timestamp = timestampMs / 1000d;
        }
        if (Double.isNaN(timestamp) || timestamp <= 0d) return 0d;
        return Math.max(0d, receivedAtMs / 1000d - timestamp);
    }

    private static void putMeaningful(JSONObject target, String key, Object value)
            throws Exception {
        if (AircraftData.meaningful(value)) target.put(key, String.valueOf(value).trim());
    }

    private static void putFinite(JSONObject target, String key, Object value)
            throws Exception {
        if (!(value instanceof Number)) return;
        double number = ((Number) value).doubleValue();
        if (!Double.isNaN(number) && !Double.isInfinite(number)) target.put(key, number);
    }

    private static boolean validPosition(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }
}
