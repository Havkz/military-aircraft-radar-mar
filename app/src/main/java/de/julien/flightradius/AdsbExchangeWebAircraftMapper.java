package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class AdsbExchangeWebAircraftMapper {
    private static final int MAX_AIRCRAFT = 10_000;

    private AdsbExchangeWebAircraftMapper() { }

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
        String hex = source.optString("icao", "").replace("~", "")
                .trim().toLowerCase(Locale.US);
        if (!hex.matches("[0-9a-f]{6}")) return null;
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
            putMeaningful(aircraft, "desc", source.opt("description"));
            putMeaningful(aircraft, "ownOp", source.opt("operator"));
            putFinite(aircraft, "gs", source.opt("speed"));
            putFinite(aircraft, "track", source.opt("track"));
            putFinite(aircraft, "baro_rate", source.opt("vspeed"));
            putFinite(aircraft, "alt_baro", source.opt("altitude"));
            putFinite(aircraft, "alt_geom", source.opt("geometricAltitude"));
            putMeaningful(aircraft, "squawk", source.opt("squawk"));
            if (source.optBoolean("military", false)) aircraft.put("dbFlags", 1);
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
        double seenPosition = source.optDouble("seenPosition", Double.NaN);
        if (!Double.isNaN(seenPosition) && !Double.isInfinite(seenPosition)
                && seenPosition >= 0d) return seenPosition;
        double timestamp = source.optDouble("timestamp", Double.NaN);
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
