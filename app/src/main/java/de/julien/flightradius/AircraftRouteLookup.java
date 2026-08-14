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
    private static final long CACHE_HIT_MS = 30 * 60_000L;
    private static final long CACHE_MISS_MS = 15 * 60_000L;
    private static final String ADSBDB_API = "https://api.adsbdb.com/v0/callsign/";
    private static final String VRS_ROUTES = "https://vrs-standing-data.adsb.lol/routes/";
    private static final String USER_AGENT = "MilitaryAircraftRadar/1.2.52 "
            + "(+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftRouteLookup() { }

    static JSONObject find(Context context, String rawHex, String rawCallsign,
                           double latitude, double longitude,
                           double track, double speedKnots) {
        String callsign = normalizeCallsign(rawCallsign);
        String hex = normalizeHex(rawHex);
        if ((callsign.isEmpty() && hex.isEmpty())
                || !validPosition(latitude, longitude)) return empty();
        File cache = new File(context.getCacheDir(), "route-v3-"
                + (hex.isEmpty() ? "nohex" : hex) + "-"
                + (callsign.isEmpty() ? "nocallsign" : callsign) + ".json");
        JSONObject cached = readCache(cache);
        if (cached != null) {
            long age = Math.max(0L, System.currentTimeMillis() - cache.lastModified());
            long lifetime = cached.optBoolean("available", false)
                    ? CACHE_HIT_MS : CACHE_MISS_MS;
            if (age < lifetime) {
                if (!cached.optBoolean("available", false) && hex.isEmpty()) return cached;
                JSONObject validated = validatePosition(
                        cached, latitude, longitude, track, speedKnots);
                if (validated.optBoolean("available", false)) return validated;
            }
        }
        try {
            JSONObject result = empty();
            String vrsJson = "";
            if (routeSourceCallsign(callsign)) {
                String prefix = callsign.substring(0, Math.min(2, callsign.length()));
                vrsJson = get(VRS_ROUTES + prefix + "/" + callsign + ".json");
                result = parseVrs(vrsJson,
                        callsign, latitude, longitude, track, speedKnots);
                if (!result.optBoolean("available", false)) {
                    result = parseAdsbDb(get(ADSBDB_API + callsign), callsign,
                            latitude, longitude, track, speedKnots);
                }
            }
            AirportDirectory.Airport traceOrigin = hex.isEmpty() ? null
                    : traceOrigin(context, AircraftTraceLookup.find(hex));
            if (traceOrigin != null && !vrsJson.isEmpty()) {
                JSONObject traceMatchedVrs = parseVrs(vrsJson, callsign,
                        latitude, longitude, track, speedKnots, traceOrigin);
                if (traceMatchedVrs.optBoolean("available", false)) {
                    result = traceMatchedVrs;
                }
            }
            result = validateOrSupplementWithTraceOrigin(result, traceOrigin);
            writeCache(cache, result);
            return result;
        } catch (Exception ignored) { return empty(); }
    }

    static JSONObject find(Context context, String rawHex, String rawCallsign,
                           double latitude, double longitude) {
        return find(context, rawHex, rawCallsign,
                latitude, longitude, Double.NaN, 0d);
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
        return parseVrs(json, rawCallsign, latitude, longitude,
                track, speedKnots, null);
    }

    private static JSONObject parseVrs(String json, String rawCallsign,
                                       double latitude, double longitude,
                                       double track, double speedKnots,
                                       AirportDirectory.Airport traceOrigin) {
        try {
            String callsign = normalizeCallsign(rawCallsign);
            JSONObject route = new JSONObject(json);
            if (callsign.isEmpty() || !callsign.equals(normalizeCallsign(
                    route.optString("callsign", "")))) return empty();
            JSONArray airports = route.optJSONArray("_airports");
            if (airports == null || airports.length() < 2) return empty();
            int segment = bestVrsSegment(airports, latitude, longitude,
                    track, speedKnots, traceOrigin);
            if (segment < 0) return empty();
            JSONObject origin = airports.optJSONObject(segment);
            JSONObject destination = airports.optJSONObject(segment + 1);
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

    static int bestVrsSegment(JSONArray airports, double latitude, double longitude,
                              double track, double speedKnots) {
        return bestVrsSegment(airports, latitude, longitude,
                track, speedKnots, null);
    }

    static int bestVrsSegment(JSONArray airports, double latitude,
                              double longitude, double track, double speedKnots,
                              AirportDirectory.Airport traceOrigin) {
        if (airports == null || airports.length() < 2) return -1;
        int best = -1;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int i = 0; i < airports.length() - 1; i++) {
            JSONObject origin = airports.optJSONObject(i);
            JSONObject destination = airports.optJSONObject(i + 1);
            double originLatitude = number(origin, "lat");
            double originLongitude = number(origin, "lon");
            double destinationLatitude = number(destination, "lat");
            double destinationLongitude = number(destination, "lon");
            if (!plausible(latitude, longitude, originLatitude, originLongitude,
                    destinationLatitude, destinationLongitude)
                    || !directionPlausible(latitude, longitude, track, speedKnots,
                    destinationLatitude, destinationLongitude)) continue;
            double routeKm = DistanceCalculator.kilometers(originLatitude, originLongitude,
                    destinationLatitude, destinationLongitude);
            double viaAircraftKm = DistanceCalculator.kilometers(
                    originLatitude, originLongitude, latitude, longitude)
                    + DistanceCalculator.kilometers(latitude, longitude,
                    destinationLatitude, destinationLongitude);
            double score = Math.max(0d, viaAircraftKm - routeKm);
            if (traceOrigin != null) {
                double originMismatchKm = DistanceCalculator.kilometers(
                        originLatitude, originLongitude,
                        traceOrigin.latitude, traceOrigin.longitude);
                if (!Double.isNaN(originMismatchKm) && originMismatchKm <= 30d) {
                    score -= 1_000_000d;
                }
            }
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static AirportDirectory.Airport traceOrigin(Context context, JSONArray trace) {
        if (trace == null || trace.length() == 0) return null;
        int start = AircraftTraceLookup.lastLegStart(trace);
        AirportDirectory.Airport best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        int end = Math.min(trace.length(), start + 80);
        for (int i = start; i < end; i++) {
            JSONArray point = trace.optJSONArray(i);
            if (point == null) continue;
            double altitude = point.optDouble(2, Double.NaN);
            if (!Double.isNaN(altitude) && altitude > 8_000d) break;
            double pointLatitude = point.optDouble(0, Double.NaN);
            double pointLongitude = point.optDouble(1, Double.NaN);
            if (!validPosition(pointLatitude, pointLongitude)) continue;
            AirportDirectory.Airport candidate = AirportDirectory.nearest(
                    context, pointLatitude, pointLongitude, 30d);
            if (candidate == null) continue;
            double distance = DistanceCalculator.kilometers(pointLatitude, pointLongitude,
                    candidate.latitude, candidate.longitude);
            if (!Double.isNaN(distance) && distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    static JSONObject validateOrSupplementWithTraceOrigin(
            JSONObject route, AirportDirectory.Airport traceOrigin) throws Exception {
        if (traceOrigin == null) return route;
        if (route != null && route.optBoolean("available", false)) {
            double sourceLatitude = route.optDouble("origin_latitude", Double.NaN);
            double sourceLongitude = route.optDouble("origin_longitude", Double.NaN);
            double mismatchKm = DistanceCalculator.kilometers(sourceLatitude,
                    sourceLongitude, traceOrigin.latitude, traceOrigin.longitude);
            if (!Double.isNaN(mismatchKm) && mismatchKm <= 30d) return route;
        }
        return new JSONObject()
                .put("available", true)
                .put("verified", true)
                .put("origin", traceOrigin.code)
                .put("origin_city", traceOrigin.city)
                .put("origin_name", traceOrigin.name)
                .put("origin_latitude", traceOrigin.latitude)
                .put("origin_longitude", traceOrigin.longitude)
                .put("destination", "?")
                .put("destination_city", "?")
                .put("destination_name", "Unknown destination")
                .put("source", "ADS-B trace + local airport directory");
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
        if ("?".equals(route.optString("destination"))
                && validPosition(route.optDouble("origin_latitude", Double.NaN),
                route.optDouble("origin_longitude", Double.NaN))) return route;
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

    private static boolean routeSourceCallsign(String callsign) {
        return callsign != null
                && callsign.matches("[A-Z]{2,3}[0-9][A-Z0-9]{0,4}");
    }

    private static String normalizeHex(String value) {
        String hex = value == null ? "" : value.replace("~", "").trim()
                .toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        return hex.length() == 6 ? hex : "";
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
