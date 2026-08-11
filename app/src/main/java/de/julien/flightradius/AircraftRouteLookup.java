package de.julien.flightradius;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class AircraftRouteLookup {
    private static final long CACHE_HIT_MS = 6 * 60 * 60_000L;
    private static final long CACHE_MISS_MS = 15 * 60_000L;
    private static final String API = "https://api.adsbdb.com/v0/callsign/";
    private static final String USER_AGENT = "MilitaryAircraftRadar/1.2.44 "
            + "(+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftRouteLookup() { }

    static JSONObject find(Context context, String rawCallsign,
                           double latitude, double longitude) {
        String callsign = normalizeCallsign(rawCallsign);
        if (callsign.isEmpty() || !validPosition(latitude, longitude)) return empty();
        File cache = new File(context.getCacheDir(), "route-v1-" + callsign + ".json");
        JSONObject cached = readCache(cache);
        if (cached != null) {
            long age = Math.max(0L, System.currentTimeMillis() - cache.lastModified());
            long lifetime = cached.optBoolean("available", false)
                    ? CACHE_HIT_MS : CACHE_MISS_MS;
            if (age < lifetime) {
                if (!cached.optBoolean("available", false)) return cached;
                JSONObject validated = validatePosition(cached, latitude, longitude);
                if (validated.optBoolean("available", false)) return validated;
            }
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(API + callsign)
                    .openConnection();
            try {
                connection.setConnectTimeout(6_000);
                connection.setReadTimeout(8_000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                int code = connection.getResponseCode();
                JSONObject result = code == 200
                        ? parse(read(connection.getInputStream()), callsign, latitude, longitude)
                        : empty();
                writeCache(cache, result);
                return result;
            } finally { connection.disconnect(); }
        } catch (Exception ignored) { return empty(); }
    }

    static JSONObject parse(String json, String rawCallsign,
                            double latitude, double longitude) {
        try {
            String callsign = normalizeCallsign(rawCallsign);
            JSONObject route = new JSONObject(json).optJSONObject("response");
            route = route == null ? null : route.optJSONObject("flightroute");
            if (route == null || callsign.isEmpty()) return empty();
            String responseCallsign = normalizeCallsign(route.optString("callsign_icao",
                    route.optString("callsign", "")));
            String iataCallsign = normalizeCallsign(route.optString("callsign_iata", ""));
            if (!callsign.equals(responseCallsign) && !callsign.equals(iataCallsign)) {
                return empty();
            }
            JSONObject airline = route.optJSONObject("airline");
            JSONObject origin = route.optJSONObject("origin");
            JSONObject destination = route.optJSONObject("destination");
            String airlineName = text(airline, "name");
            String originCode = airportCode(origin);
            String destinationCode = airportCode(destination);
            double originLatitude = number(origin, "latitude");
            double originLongitude = number(origin, "longitude");
            double destinationLatitude = number(destination, "latitude");
            double destinationLongitude = number(destination, "longitude");
            if (airlineName.isEmpty() || originCode.isEmpty() || destinationCode.isEmpty()
                    || originCode.equals(destinationCode)
                    || !plausible(latitude, longitude, originLatitude, originLongitude,
                    destinationLatitude, destinationLongitude)) return empty();
            return new JSONObject()
                    .put("available", true)
                    .put("verified", true)
                    .put("callsign", responseCallsign.isEmpty() ? callsign : responseCallsign)
                    .put("airline", airlineName)
                    .put("origin", originCode)
                    .put("origin_city", text(origin, "municipality"))
                    .put("origin_name", text(origin, "name"))
                    .put("origin_latitude", originLatitude)
                    .put("origin_longitude", originLongitude)
                    .put("destination", destinationCode)
                    .put("destination_city", text(destination, "municipality"))
                    .put("destination_name", text(destination, "name"))
                    .put("destination_latitude", destinationLatitude)
                    .put("destination_longitude", destinationLongitude)
                    .put("source", "ADSBDB");
        } catch (Exception ignored) { return empty(); }
    }

    static boolean plausible(double latitude, double longitude,
                             double originLatitude, double originLongitude,
                             double destinationLatitude, double destinationLongitude) {
        if (!validPosition(latitude, longitude)
                || !validPosition(originLatitude, originLongitude)
                || !validPosition(destinationLatitude, destinationLongitude)) return false;
        double routeKm = DistanceCalculator.kilometers(originLatitude, originLongitude,
                destinationLatitude, destinationLongitude);
        if (Double.isNaN(routeKm) || routeKm < 10d) return false;
        double viaAircraftKm = DistanceCalculator.kilometers(originLatitude, originLongitude,
                latitude, longitude) + DistanceCalculator.kilometers(latitude, longitude,
                destinationLatitude, destinationLongitude);
        double permittedExcessKm = Math.max(140d, Math.min(1_200d, routeKm * .35d));
        return viaAircraftKm - routeKm <= permittedExcessKm;
    }

    private static JSONObject validatePosition(JSONObject route,
                                               double latitude, double longitude) {
        if (!route.optBoolean("available", false)) return route;
        return plausible(latitude, longitude,
                route.optDouble("origin_latitude", Double.NaN),
                route.optDouble("origin_longitude", Double.NaN),
                route.optDouble("destination_latitude", Double.NaN),
                route.optDouble("destination_longitude", Double.NaN)) ? route : empty();
    }

    private static String airportCode(JSONObject airport) {
        String iata = text(airport, "iata_code").toUpperCase(Locale.US);
        if (iata.matches("[A-Z0-9]{3}")) return iata;
        String icao = text(airport, "icao_code").toUpperCase(Locale.US);
        return icao.matches("[A-Z0-9]{4}") ? icao : "";
    }

    private static String text(JSONObject object, String key) {
        return object == null ? "" : object.optString(key, "").trim();
    }

    private static double number(JSONObject object, String key) {
        return object == null ? Double.NaN : object.optDouble(key, Double.NaN);
    }

    private static String normalizeCallsign(String value) {
        String callsign = AircraftData.normalizeCallsign(value).toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
        return callsign.matches("[A-Z0-9]{3,8}") ? callsign : "";
    }

    private static boolean validPosition(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }

    private static JSONObject readCache(File file) {
        if (!file.isFile()) return null;
        try { return new JSONObject(read(new FileInputStream(file))); }
        catch (Exception ignored) { return null; }
    }

    private static void writeCache(File file, JSONObject result) {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(result.toString().getBytes("UTF-8"));
        } catch (Exception ignored) { }
    }

    private static String read(InputStream stream) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static JSONObject empty() {
        try { return new JSONObject().put("available", false); }
        catch (Exception ignored) { return new JSONObject(); }
    }
}
