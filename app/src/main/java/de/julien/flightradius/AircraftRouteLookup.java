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
import java.util.Locale;

final class AircraftRouteLookup {
    private static final long CACHE_HIT_MS = 6 * 60 * 60_000L;
    private static final long CACHE_MISS_MS = 15 * 60_000L;
    private static final String ADSBDB_API = "https://api.adsbdb.com/v0/callsign/";
    private static final String VRS_ROUTES = "https://vrs-standing-data.adsb.lol/routes/";
    private static final String USER_AGENT = "MilitaryAircraftRadar/1.2.45 "
            + "(+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftRouteLookup() { }

    static JSONObject find(Context context, String rawCallsign,
                           double latitude, double longitude,
                           double track, double speedKnots) {
        String callsign = normalizeCallsign(rawCallsign);
        if (callsign.isEmpty() || !validPosition(latitude, longitude)) return empty();
        File cache = new File(context.getCacheDir(), "route-v2-" + callsign + ".json");
        JSONObject cached = readCache(cache);
        if (cached != null) {
            long age = Math.max(0L, System.currentTimeMillis() - cache.lastModified());
            long lifetime = cached.optBoolean("available", false)
                    ? CACHE_HIT_MS : CACHE_MISS_MS;
            if (age < lifetime) {
                if (!cached.optBoolean("available", false)) return cached;
                JSONObject validated = validatePosition(
                        cached, latitude, longitude, track, speedKnots);
                if (validated.optBoolean("available", false)) return validated;
            }
        }
        try {
            String prefix = callsign.substring(0, Math.min(2, callsign.length()));
            JSONObject result = parseVrs(get(VRS_ROUTES + prefix + "/" + callsign + ".json"),
                    callsign, latitude, longitude, track, speedKnots);
            if (!result.optBoolean("available", false)) {
                result = parseAdsbDb(get(ADSBDB_API + callsign), callsign,
                        latitude, longitude, track, speedKnots);
            }
            writeCache(cache, result);
            return result;
        } catch (Exception ignored) { return empty(); }
    }

    static JSONObject find(Context context, String rawCallsign,
                           double latitude, double longitude) {
        return find(context, rawCallsign, latitude, longitude, Double.NaN, 0d);
    }

    static JSONObject parse(String json, String rawCallsign,
                            double latitude, double longitude) {
        return parseAdsbDb(json, rawCallsign, latitude, longitude, Double.NaN, 0d);
    }

    private static JSONObject parseAdsbDb(String json, String rawCallsign,
                                          double latitude, double longitude,
                                          double track, double speedKnots) {
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
            if (originCode.isEmpty() || destinationCode.isEmpty()
                    || originCode.equals(destinationCode)
                    || !plausible(latitude, longitude, originLatitude, originLongitude,
                    destinationLatitude, destinationLongitude)
                    || !directionPlausible(latitude, longitude, track, speedKnots,
                    destinationLatitude, destinationLongitude)) return empty();
            return new JSONObject()
                    .put("available", true)
                    .put("verified", true)
                    .put("callsign", responseCallsign.isEmpty() ? callsign : responseCallsign)
                    .put("airline", airlineName)
                    .put("origin", originCode)
                    .put("origin_city", placeName(text(origin, "municipality")))
                    .put("origin_name", text(origin, "name"))
                    .put("origin_latitude", originLatitude)
                    .put("origin_longitude", originLongitude)
                    .put("destination", destinationCode)
                    .put("destination_city", placeName(text(destination, "municipality")))
                    .put("destination_name", text(destination, "name"))
                    .put("destination_latitude", destinationLatitude)
                    .put("destination_longitude", destinationLongitude)
                    .put("source", "ADSBDB");
        } catch (Exception ignored) { return empty(); }
    }

    static JSONObject parseVrs(String json, String rawCallsign,
                               double latitude, double longitude,
                               double track, double speedKnots) {
        try {
            String callsign = normalizeCallsign(rawCallsign);
            JSONObject route = new JSONObject(json);
            if (callsign.isEmpty() || !callsign.equals(normalizeCallsign(
                    route.optString("callsign", "")))) return empty();
            JSONArray airports = route.optJSONArray("_airports");
            if (airports == null || airports.length() < 2) return empty();
            JSONObject origin = airports.optJSONObject(0);
            JSONObject destination = airports.optJSONObject(airports.length() - 1);
            String originCode = vrsAirportCode(origin);
            String destinationCode = vrsAirportCode(destination);
            double originLatitude = number(origin, "lat");
            double originLongitude = number(origin, "lon");
            double destinationLatitude = number(destination, "lat");
            double destinationLongitude = number(destination, "lon");
            if (originCode.isEmpty() || destinationCode.isEmpty()
                    || originCode.equals(destinationCode)
                    || !plausible(latitude, longitude, originLatitude, originLongitude,
                    destinationLatitude, destinationLongitude)
                    || !directionPlausible(latitude, longitude, track, speedKnots,
                    destinationLatitude, destinationLongitude)) return empty();
            return new JSONObject()
                    .put("available", true)
                    .put("verified", true)
                    .put("callsign", callsign)
                    .put("origin", originCode)
                    .put("origin_city", placeName(text(origin, "location")))
                    .put("origin_name", text(origin, "name"))
                    .put("origin_latitude", originLatitude)
                    .put("origin_longitude", originLongitude)
                    .put("destination", destinationCode)
                    .put("destination_city", placeName(text(destination, "location")))
                    .put("destination_name", text(destination, "name"))
                    .put("destination_latitude", destinationLatitude)
                    .put("destination_longitude", destinationLongitude)
                    .put("source", "ADSB.lol VRS standing data");
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

    static boolean directionPlausible(double latitude, double longitude,
                                      double track, double speedKnots,
                                      double destinationLatitude,
                                      double destinationLongitude) {
        if (Double.isNaN(track) || Double.isInfinite(track) || speedKnots < 80d
                || !validPosition(latitude, longitude)
                || !validPosition(destinationLatitude, destinationLongitude)) return true;
        if (DistanceCalculator.kilometers(latitude, longitude,
                destinationLatitude, destinationLongitude) < 35d) return true;
        double bearing = bearing(latitude, longitude,
                destinationLatitude, destinationLongitude);
        double difference = Math.abs(((track - bearing + 540d) % 360d) - 180d);
        return difference <= 120d;
    }

    private static double bearing(double latitude, double longitude,
                                  double targetLatitude, double targetLongitude) {
        double from = Math.toRadians(latitude);
        double to = Math.toRadians(targetLatitude);
        double longitudeDelta = Math.toRadians(targetLongitude - longitude);
        double y = Math.sin(longitudeDelta) * Math.cos(to);
        double x = Math.cos(from) * Math.sin(to)
                - Math.sin(from) * Math.cos(to) * Math.cos(longitudeDelta);
        return (Math.toDegrees(Math.atan2(y, x)) + 360d) % 360d;
    }

    private static JSONObject validatePosition(JSONObject route,
                                               double latitude, double longitude,
                                               double track, double speedKnots) {
        if (!route.optBoolean("available", false)) return route;
        return plausible(latitude, longitude,
                route.optDouble("origin_latitude", Double.NaN),
                route.optDouble("origin_longitude", Double.NaN),
                route.optDouble("destination_latitude", Double.NaN),
                route.optDouble("destination_longitude", Double.NaN))
                && directionPlausible(latitude, longitude, track, speedKnots,
                route.optDouble("destination_latitude", Double.NaN),
                route.optDouble("destination_longitude", Double.NaN)) ? route : empty();
    }

    private static String airportCode(JSONObject airport) {
        String iata = text(airport, "iata_code").toUpperCase(Locale.US);
        if (iata.matches("[A-Z0-9]{3}")) return iata;
        String icao = text(airport, "icao_code").toUpperCase(Locale.US);
        return icao.matches("[A-Z0-9]{4}") ? icao : "";
    }

    private static String vrsAirportCode(JSONObject airport) {
        String iata = text(airport, "iata").toUpperCase(Locale.US);
        if (iata.matches("[A-Z0-9]{3}")) return iata;
        String icao = text(airport, "icao").toUpperCase(Locale.US);
        return icao.matches("[A-Z0-9]{4}") ? icao : "";
    }

    private static String text(JSONObject object, String key) {
        return object == null ? "" : object.optString(key, "").trim();
    }

    private static String placeName(String value) {
        return value.replace('-', ' ').replace('_', ' ').replaceAll("\\s+", " ").trim();
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

    private static String get(String endpoint) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(7_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            return connection.getResponseCode() == 200
                    ? read(connection.getInputStream()) : "";
        } catch (Exception ignored) { return ""; }
        finally { if (connection != null) connection.disconnect(); }
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
