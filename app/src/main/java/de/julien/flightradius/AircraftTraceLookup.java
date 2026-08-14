package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

final class AircraftTraceLookup {
    private static final int MAX_TRACE_POINTS = 2_000;
    private static final long CACHE_MS = 5 * 60_000L;
    private static final Map<String, JSONArray> CACHE = new HashMap<>();
    private static final Map<String, Long> CACHE_TIMES = new HashMap<>();
    private static final Map<String, Object> LOOKUP_LOCKS = new HashMap<>();
    private static final String USER_AGENT =
            "MilitaryAircraftRadar/1.2.52 (+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftTraceLookup() { }

    static JSONArray find(String rawHex) {
        String hex = rawHex == null ? "" : rawHex.replace("~", "")
                .trim().toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        if (hex.length() != 6) return new JSONArray();
        Object lookupLock;
        synchronized (AircraftTraceLookup.class) {
            JSONArray cached = CACHE.get(hex);
            long cachedAt = CACHE_TIMES.containsKey(hex) ? CACHE_TIMES.get(hex) : 0L;
            if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
                return copy(cached);
            }
            lookupLock = LOOKUP_LOCKS.computeIfAbsent(hex, ignored -> new Object());
        }
        synchronized (lookupLock) {
            synchronized (AircraftTraceLookup.class) {
                JSONArray cached = CACHE.get(hex);
                long cachedAt = CACHE_TIMES.containsKey(hex) ? CACHE_TIMES.get(hex) : 0L;
                if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
                    return copy(cached);
                }
            }
            JSONArray result = fetchMerged(hex);
            synchronized (AircraftTraceLookup.class) {
                CACHE.put(hex, copy(result));
                CACHE_TIMES.put(hex, System.currentTimeMillis());
                LOOKUP_LOCKS.remove(hex);
            }
            return result;
        }
    }

    private static JSONArray fetchMerged(String hex) {
        String path = "/data/traces/" + hex.substring(4)
                + "/trace_full_" + hex + ".json";
        ExecutorService sources = Executors.newFixedThreadPool(2);
        CompletionService<JSONArray> completed = new ExecutorCompletionService<>(sources);
        completed.submit(
                () -> parse(get("https://adsb.lol" + path, ""), MAX_TRACE_POINTS));
        completed.submit(() -> parse(get(
                "https://globe.airplanes.live" + path,
                "https://globe.airplanes.live/"), MAX_TRACE_POINTS));
        List<JSONArray> results = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                try {
                    JSONArray result = completed.take().get();
                    if (result.length() > 0) results.add(result);
                } catch (Exception ignored) { }
            }
            return merge(results.toArray(new JSONArray[0]));
        } finally {
            sources.shutdownNow();
        }
    }

    static JSONArray merge(JSONArray... sources) {
        List<JSONArray> points = new ArrayList<>();
        for (JSONArray source : sources) {
            if (source == null) continue;
            for (int i = 0; i < source.length(); i++) {
                JSONArray point = source.optJSONArray(i);
                if (point != null && point.length() >= 4) points.add(point);
            }
        }
        points.sort(Comparator.comparingLong(point -> point.optLong(3, 0L)));
        JSONArray merged = new JSONArray();
        JSONArray previous = null;
        for (JSONArray point : points) {
            if (previous != null
                    && Math.abs(point.optLong(3) - previous.optLong(3)) <= 2_000L
                    && DistanceCalculator.kilometers(point.optDouble(0), point.optDouble(1),
                    previous.optDouble(0), previous.optDouble(1)) < .75d) {
                if (point.optDouble(2, Double.NaN) <= 0d
                        && previous.optDouble(2, Double.NaN) > 0d) {
                    try {
                        merged.put(merged.length() - 1, point);
                        previous = point;
                    } catch (Exception ignored) { }
                }
                continue;
            }
            merged.put(point);
            previous = point;
        }
        return merged;
    }

    static int lastLegStart(JSONArray points) {
        if (points == null || points.length() < 2) return 0;
        int start = 0;
        for (int i = 1; i < points.length(); i++) {
            JSONArray previous = points.optJSONArray(i - 1);
            JSONArray point = points.optJSONArray(i);
            if (previous == null || point == null) continue;
            double previousAltitude = previous.optDouble(2, Double.NaN);
            double altitude = point.optDouble(2, Double.NaN);
            long gapMs = point.optLong(3, 0L) - previous.optLong(3, 0L);
            if (!Double.isNaN(previousAltitude) && previousAltitude <= 0d
                    && !Double.isNaN(altitude) && altitude > 0d) {
                start = i - 1;
            } else if (gapMs >= 20 * 60_000L
                    && ((!Double.isNaN(previousAltitude) && previousAltitude < 5_000d)
                    || (!Double.isNaN(altitude) && altitude < 5_000d))) {
                start = i;
            }
        }
        return start;
    }

    private static JSONArray copy(JSONArray source) {
        try { return new JSONArray(source.toString()); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    static JSONArray parse(String json, int maximumPoints) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray trace = root.optJSONArray("trace");
        JSONArray result = new JSONArray();
        if (trace == null || trace.length() == 0) return result;
        long baseTimeMs = Math.round(root.optDouble("timestamp", 0d) * 1000d);
        int step = Math.max(1, (int) Math.ceil(trace.length()
                / (double) Math.max(1, maximumPoints)));
        for (int i = 0; i < trace.length(); i++) {
            JSONArray point = trace.optJSONArray(i);
            boolean ground = groundPoint(point);
            boolean previousGround = i > 0 && groundPoint(trace.optJSONArray(i - 1));
            boolean nextGround = i + 1 < trace.length()
                    && groundPoint(trace.optJSONArray(i + 1));
            if (i % step == 0 || i == trace.length() - 1
                    || ground != previousGround || ground != nextGround) {
                appendPoint(result, point, baseTimeMs);
            }
        }
        return result;
    }

    private static boolean groundPoint(JSONArray point) {
        Object altitude = point == null ? null : point.opt(3);
        return altitude instanceof String
                && "ground".equalsIgnoreCase(((String) altitude).trim());
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
