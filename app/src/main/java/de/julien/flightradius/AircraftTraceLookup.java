package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

final class AircraftTraceLookup {
    private static final int MAX_TRACE_POINTS = 2_000;
    private static final String USER_AGENT =
            "MilitaryAircraftRadar/1.2.38 (+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftTraceLookup() { }

    static JSONArray find(String rawHex) {
        String hex = rawHex == null ? "" : rawHex.replace("~", "")
                .trim().toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        if (hex.length() != 6) return new JSONArray();
        String path = "/data/traces/" + hex.substring(4)
                + "/trace_full_" + hex + ".json";
        ExecutorService sources = Executors.newFixedThreadPool(2);
        CompletionService<JSONArray> completed = new ExecutorCompletionService<>(sources);
        Future<JSONArray> adsbLol = completed.submit(
                () -> parse(get("https://adsb.lol" + path, ""), MAX_TRACE_POINTS));
        Future<JSONArray> airplanesLive = completed.submit(() -> parse(get(
                "https://globe.airplanes.live" + path,
                "https://globe.airplanes.live/"), MAX_TRACE_POINTS));
        try {
            for (int i = 0; i < 2; i++) {
                try {
                    JSONArray result = completed.take().get();
                    if (result.length() > 0) {
                        adsbLol.cancel(true);
                        airplanesLive.cancel(true);
                        return result;
                    }
                } catch (Exception ignored) { }
            }
            return new JSONArray();
        } finally {
            sources.shutdownNow();
        }
    }

    static JSONArray parse(String json, int maximumPoints) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray trace = root.optJSONArray("trace");
        JSONArray result = new JSONArray();
        if (trace == null || trace.length() == 0) return result;
        long baseTimeMs = Math.round(root.optDouble("timestamp", 0d) * 1000d);
        int step = Math.max(1, (int) Math.ceil(trace.length()
                / (double) Math.max(1, maximumPoints)));
        for (int i = 0; i < trace.length(); i += step) {
            appendPoint(result, trace.optJSONArray(i), baseTimeMs);
        }
        if ((trace.length() - 1) % step != 0) {
            appendPoint(result, trace.optJSONArray(trace.length() - 1), baseTimeMs);
        }
        return result;
    }

    private static void appendPoint(JSONArray result, JSONArray point, long baseTimeMs)
            throws Exception {
        if (point == null || point.length() < 4) return;
        double latitude = point.optDouble(1, Double.NaN);
        double longitude = point.optDouble(2, Double.NaN);
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
                || latitude < -90d || latitude > 90d
                || longitude < -180d || longitude > 180d) return;
        Object altitudeValue = point.opt(3);
        double altitude = altitudeValue instanceof Number
                ? ((Number) altitudeValue).doubleValue() : 0d;
        long timeMs = baseTimeMs + Math.round(point.optDouble(0, 0d) * 1000d);
        result.put(new JSONArray().put(latitude).put(longitude).put(altitude).put(timeMs));
    }

    private static String get(String endpoint, String referer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            if (!referer.isEmpty()) connection.setRequestProperty("Referer", referer);
            if (connection.getResponseCode() != 200) return "{}";
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line);
            }
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }
}
