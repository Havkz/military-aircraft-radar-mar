package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class AircraftData {
    private static final double MAX_LAST_POSITION_AGE_SECONDS = 60d;

    private AircraftData() { }

    static JSONArray mergeByHex(JSONArray primary, JSONArray supplemental) throws JSONException {
        Map<String, JSONObject> byHex = new LinkedHashMap<>();
        JSONArray withoutHex = new JSONArray();
        appendMerged(byHex, withoutHex, primary);
        appendMerged(byHex, withoutHex, supplemental);
        JSONArray merged = new JSONArray();
        for (JSONObject aircraft : byHex.values()) merged.put(aircraft);
        for (int i = 0; i < withoutHex.length(); i++) merged.put(withoutHex.opt(i));
        return merged;
    }

    static double[] recentPosition(JSONObject aircraft) {
        if (aircraft == null) return null;
        double latitude = aircraft.optDouble("lat", Double.NaN);
        double longitude = aircraft.optDouble("lon", Double.NaN);
        if (validPosition(latitude, longitude)) return new double[]{latitude, longitude};

        JSONObject lastPosition = aircraft.optJSONObject("lastPosition");
        if (lastPosition == null) return null;
        double ageSeconds = lastPosition.optDouble("seen_pos", Double.POSITIVE_INFINITY);
        latitude = lastPosition.optDouble("lat", Double.NaN);
        longitude = lastPosition.optDouble("lon", Double.NaN);
        if (ageSeconds < 0d || ageSeconds > MAX_LAST_POSITION_AGE_SECONDS
                || !validPosition(latitude, longitude)) return null;
        return new double[]{latitude, longitude};
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
                Iterator<String> keys = aircraft.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!existing.has(key) || existing.isNull(key)) {
                        existing.put(key, aircraft.opt(key));
                    }
                }
            }
        }
    }

    private static boolean validPosition(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }
}
