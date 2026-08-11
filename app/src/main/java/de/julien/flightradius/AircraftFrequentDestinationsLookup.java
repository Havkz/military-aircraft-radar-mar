package de.julien.flightradius;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

final class AircraftFrequentDestinationsLookup {
    private static final int HISTORY_DAYS = 30;
    private static final long CACHE_MS = 24 * 60 * 60_000L;
    private static final String HOST = "https://globe.airplanes.live";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android) "
            + "MilitaryAircraftRadar/1.2.36";

    private AircraftFrequentDestinationsLookup() { }

    static JSONObject find(Context context, String rawHex) {
        String hex = normalizeHex(rawHex);
        if (hex.isEmpty()) return empty();
        // v2 invalidates empty results produced while the packaged airport asset was opened
        // under its pre-aapt2 .gz filename.
        File cache = new File(context.getCacheDir(), "destinations-v2-" + hex + ".json");
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < CACHE_MS) {
            try { return new JSONObject(read(new FileInputStream(cache))); }
            catch (Exception ignored) { }
        }
        JSONObject result = analyze(context, hex);
        try (FileOutputStream output = new FileOutputStream(cache)) {
            output.write(result.toString().getBytes("UTF-8"));
        } catch (Exception ignored) { }
        return result;
    }

    private static JSONObject analyze(Context context, String hex) {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CompletionService<DayResult> completed = new ExecutorCompletionService<>(pool);
        int requests = 1 + HISTORY_DAYS;
        String suffix = "/traces/" + hex.substring(4) + "/trace_full_" + hex + ".json";
        completed.submit(() -> fetchDay(HOST + "/data" + suffix));
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        day.add(Calendar.DAY_OF_YEAR, -2);
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        for (int i = 0; i < HISTORY_DAYS; i++) {
            String date = format.format(day.getTime());
            completed.submit(() -> fetchDay(HOST + "/globe_history/" + date + suffix));
            day.add(Calendar.DAY_OF_YEAR, -1);
        }
        Map<String, Destination> counts = new HashMap<>();
        int scannedDays = 0;
        int landings = 0;
        try {
            for (int i = 0; i < requests; i++) {
                DayResult dayResult = completed.take().get();
                if (!dayResult.available) continue;
                scannedDays++;
                for (double[] landing : dayResult.landings) {
                    AirportDirectory.Airport airport = AirportDirectory.nearest(
                            context, landing[0], landing[1], 30d);
                    if (airport == null) continue;
                    landings++;
                    Destination destination = counts.get(airport.code);
                    if (destination == null) {
                        destination = new Destination(airport);
                        counts.put(airport.code, destination);
                    }
                    destination.count++;
                }
            }
        } catch (Exception ignored) { }
        finally { pool.shutdownNow(); }
        List<Destination> ranked = new ArrayList<>(counts.values());
        Collections.sort(ranked, Comparator.comparingInt((Destination d) -> d.count)
                .reversed().thenComparing(d -> d.airport.city));
        JSONArray destinations = new JSONArray();
        for (int i = 0; i < Math.min(8, ranked.size()); i++) {
            Destination destination = ranked.get(i);
            try {
                destinations.put(new JSONObject()
                        .put("city", destination.airport.city)
                        .put("airport", destination.airport.name)
                        .put("code", destination.airport.code)
                        .put("country", destination.airport.country)
                        .put("landings", destination.count));
            } catch (Exception ignored) { }
        }
        try {
            return new JSONObject().put("destinations", destinations)
                    .put("window_days", HISTORY_DAYS)
                    .put("scanned_days", scannedDays)
                    .put("matched_landings", landings);
        } catch (Exception ignored) { return empty(); }
    }

    static List<double[]> parseLandings(String json) throws Exception {
        JSONArray trace = new JSONObject(json).optJSONArray("trace");
        List<double[]> result = new ArrayList<>();
        if (trace == null) return result;
        boolean airborne = false;
        boolean previousGround = false;
        for (int i = 0; i < trace.length(); i++) {
            JSONArray point = trace.optJSONArray(i);
            if (point == null || point.length() < 4) continue;
            Object altitude = point.opt(3);
            boolean ground = altitude instanceof String
                    && "ground".equalsIgnoreCase(((String) altitude).trim());
            if (ground && airborne && !previousGround) {
                double latitude = point.optDouble(1, Double.NaN);
                double longitude = point.optDouble(2, Double.NaN);
                if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
                    result.add(new double[]{latitude, longitude});
                }
                airborne = false;
            } else if (!ground && altitude instanceof Number
                    && ((Number) altitude).doubleValue() > 300d) {
                airborne = true;
            }
            previousGround = ground;
        }
        return result;
    }

    private static DayResult fetchDay(String endpoint) {
        try {
            String json = get(endpoint);
            if (json.isEmpty()) return new DayResult(false, Collections.emptyList());
            return new DayResult(true, parseLandings(json));
        } catch (Exception ignored) {
            return new DayResult(false, Collections.emptyList());
        }
    }

    private static String get(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", HOST + "/");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            if (connection.getResponseCode() != 200) return "";
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            return read(stream);
        } finally { connection.disconnect(); }
    }

    private static String read(InputStream stream) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static String normalizeHex(String value) {
        String hex = value == null ? "" : value.replace("~", "").trim()
                .toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        return hex.length() == 6 ? hex : "";
    }

    private static JSONObject empty() {
        try { return new JSONObject().put("destinations", new JSONArray())
                .put("window_days", HISTORY_DAYS).put("scanned_days", 0)
                .put("matched_landings", 0); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static final class DayResult {
        final boolean available;
        final List<double[]> landings;
        DayResult(boolean available, List<double[]> landings) {
            this.available = available;
            this.landings = landings;
        }
    }

    private static final class Destination {
        final AirportDirectory.Airport airport;
        int count;
        Destination(AirportDirectory.Airport airport) { this.airport = airport; }
    }
}
