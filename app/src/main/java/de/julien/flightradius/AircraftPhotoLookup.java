package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

final class AircraftPhotoLookup {
    private static final String USER_AGENT =
            "MilitaryAircraftRadar/1.2.54 (+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftPhotoLookup() { }

    static JSONObject find(String registration, String aircraftType) {
        return find("", registration, aircraftType);
    }

    static JSONObject find(String hex, String registration, String aircraftType) {
        String normalized = normalizeRegistration(registration);
        JSONObject planespotters = new JSONObject();
        String normalizedHex = normalizeHex(hex);
        try {
            if (!normalizedHex.isEmpty()) {
                planespotters = parsePlanespotters(get(
                        "https://api.planespotters.net/pub/photos/hex/" + normalizedHex),
                        normalized);
            }
        } catch (Exception ignored) { }
        if (planespotters.length() == 0 && !normalized.isEmpty()) {
            try {
                planespotters = parsePlanespotters(get(
                        "https://api.planespotters.net/pub/photos/reg/" + path(registration)),
                        normalized);
            } catch (Exception ignored) { }
        }
        String resolvedRegistration = firstMeaningful(
                registration, planespotters.optString("registration"));
        String resolvedNormalized = normalizeRegistration(resolvedRegistration);
        JSONObject result = planespotters;
        if (!resolvedNormalized.isEmpty()) {
            try {
                String planespottingHtml = get(
                        "https://www.planespotting.be/index.php?page=aircraft&registration="
                                + path(resolvedRegistration));
                JSONObject planespotting = parsePlanespotting(
                        planespottingHtml, resolvedNormalized);
                JSONObject planespottingMetadata = new JSONObject();
                addPlanespottingMetadata(
                        planespottingMetadata, planespottingHtml, resolvedNormalized);
                mergeMissing(planespotting, planespottingMetadata);
                if (result.length() == 0) result = planespotting;
                else mergeMissing(result, planespotting);
            } catch (Exception ignored) { }
        }
        if (needsAircraftDatabaseFallback(result)) {
            try {
                JSONObject militaryFallback = findAdsbNl(
                        normalizedHex, resolvedRegistration);
                if (result.length() == 0) result = militaryFallback;
                else mergeMissing(result, militaryFallback);
            } catch (Exception ignored) { }
        }
        resolvedRegistration = firstMeaningful(
                registration, result.optString("registration"));
        resolvedNormalized = normalizeRegistration(resolvedRegistration);
        if (result.has("image") || resolvedNormalized.isEmpty()) return result;
        try {
            JSONObject fallback = parseWikimedia(
                    get(wikimediaUrl(resolvedRegistration, aircraftType)), resolvedNormalized);
            if (result.length() > 0) {
                mergeMissing(fallback, result);
                fallback.put("metadata_verified", true);
            }
            return fallback.length() > 0 ? fallback : result;
        } catch (Exception ignored) {
            return result;
        }
    }

    static JSONObject parseAdsbNlSearch(String json, String expectedHex,
                                         String expectedRegistration) throws Exception {
        String html = new JSONObject(json).optString("msg", "");
        String hex = normalizeHex(expectedHex);
        if (!hex.isEmpty() && !Pattern.compile("(?is)>\\s*" + Pattern.quote(hex)
                + "\\s*</div>").matcher(html).find()) return new JSONObject();
        Matcher registrationLink = Pattern.compile(
                "(?is)aircraft\\.php\\?id_aircraft=(\\d+)[^>]*>([^<]+)</a>")
                .matcher(html);
        if (!registrationLink.find()) return new JSONObject();
        String registration = cleanHtml(registrationLink.group(2));
        String expected = normalizeRegistration(expectedRegistration);
        if (!expected.isEmpty() && !normalizeRegistration(registration).equals(expected)) {
            return new JSONObject();
        }
        JSONObject result = new JSONObject()
                .put("_adsb_nl_id", registrationLink.group(1));
        putIfText(result, "registration", registration);
        Matcher operator = Pattern.compile(
                "(?is)<img[^>]+title=['\"]([^'\"]+)['\"][^>]*>").matcher(html);
        if (operator.find()) putIfText(result, "operator", cleanHtml(operator.group(1)));
        Matcher type = Pattern.compile(
                "(?is)checktype=[^'\"]+['\"][^>]*>([^<]+)</a>").matcher(html);
        if (type.find()) putIfText(result, "type", cleanHtml(type.group(1)));
        copyOperatorToAirline(result);
        result.put("metadata_source", "ADS-B.nl");
        return result;
    }

    static JSONObject parseAdsbNlDetail(String html, String expectedHex,
                                         String expectedRegistration) throws Exception {
        String registration = labelledDivValue(html, "REGISTRATION");
        String icao = labelledDivValue(html, "ICAO (ID-AIRCRAFT)")
                .replaceFirst("\\s*\\(.*$", "").trim();
        String expected = normalizeRegistration(expectedRegistration);
        String normalizedExpectedHex = normalizeHex(expectedHex);
        if ((!normalizedExpectedHex.isEmpty()
                && !normalizedExpectedHex.equals(normalizeHex(icao)))
                || (!expected.isEmpty()
                && !normalizeRegistration(registration).equals(expected))) {
            return new JSONObject();
        }
        String model = labelledDivValue(html, "AIRCRAFT MODEL");
        Matcher split = Pattern.compile("^([^()]*)\\(([^()]*)\\)").matcher(model);
        JSONObject result = new JSONObject();
        putIfText(result, "registration", registration);
        if (split.find()) {
            putIfText(result, "type", split.group(1));
            putIfText(result, "description",
                    split.group(2).trim().toUpperCase(Locale.US));
        } else {
            putIfText(result, "type", model);
        }
        result.put("metadata_source", "ADS-B.nl");
        return result;
    }

    private static JSONObject findAdsbNl(String hex, String registration) throws Exception {
        if (hex.isEmpty() && normalizeRegistration(registration).isEmpty()) {
            return new JSONObject();
        }
        String form = "reg=" + formValue(hex.isEmpty() ? registration : "")
                + "&type=&icao=" + formValue(hex) + "&call=";
        JSONObject search = parseAdsbNlSearch(post(
                "https://www.ads-b.nl/search/ajaxform.php", form), hex, registration);
        String detailId = search.optString("_adsb_nl_id", "");
        search.remove("_adsb_nl_id");
        if (detailId.isEmpty()) return search;
        JSONObject detail = parseAdsbNlDetail(get(
                "https://www.ads-b.nl/aircraft.php?id_aircraft=" + detailId),
                hex, firstMeaningful(registration, search.optString("registration")));
        mergeMissing(search, detail);
        return search;
    }

    private static boolean needsAircraftDatabaseFallback(JSONObject result) {
        return result == null || !AircraftData.meaningful(result.opt("registration"))
                || !AircraftData.meaningful(result.opt("operator"))
                || !AircraftData.meaningful(result.opt("description"));
    }

    private static String labelledDivValue(String html, String label) {
        Matcher matcher = Pattern.compile("(?is)>\\s*" + Pattern.quote(label)
                + "\\s*</div>\\s*<div[^>]*>(.*?)</div>").matcher(
                html == null ? "" : html);
        return matcher.find() ? cleanHtml(matcher.group(1)) : "";
    }

    static JSONObject parsePlanespotters(String json, String normalizedRegistration)
            throws Exception {
        JSONArray photos = new JSONObject(json).optJSONArray("photos");
        if (photos == null || photos.length() == 0) return new JSONObject();
        JSONObject photo = photos.optJSONObject(0);
        if (photo == null) return new JSONObject();
        JSONObject thumbnail = photo.optJSONObject("thumbnail_large");
        if (thumbnail == null || thumbnail.optString("src").isEmpty()) {
            thumbnail = photo.optJSONObject("thumbnail");
        }
        if (thumbnail == null || thumbnail.optString("src").isEmpty()) {
            return new JSONObject();
        }
        String link = photo.optString("link");
        String resolvedRegistration = registrationFromPlanespottersLink(link);
        if (!normalizedRegistration.isEmpty() && !normalizeRegistration(resolvedRegistration)
                .equals(normalizedRegistration)) {
            return new JSONObject();
        }
        JSONObject result = photo(thumbnail.optString("src"), link,
                "Planespotters.net", photo.optString("photographer"));
        putIfText(result, "registration", resolvedRegistration);
        addPlanespottersLinkMetadata(result, link,
                normalizeRegistration(resolvedRegistration));
        copyOperatorToAirline(result);
        return result;
    }

    static JSONObject parsePlanespotting(String html, String normalizedRegistration)
            throws Exception {
        String registration = registrationPattern(normalizedRegistration);
        Pattern card = Pattern.compile("(?is)href=\\\"([^\\\"]*registration="
                + registration + "(?=[^A-Z0-9])[^\\\"]*)\\\".{0,900}?<img[^>]+src=\\\"(https://www\\.planespotting\\.be/uploads/[^\\\"]+-(?:thumb|spotlight)\\.[a-z0-9]+)\\\"");
        Matcher match = card.matcher(html);
        if (!match.find()) return new JSONObject();
        JSONObject result = photo(match.group(2), match.group(1).replace("&amp;", "&"),
                "Planespotting.be", "");
        addPlanespottingMetadata(result, html, normalizedRegistration);
        return result;
    }

    private static void addPlanespottersLinkMetadata(JSONObject result, String link,
                                                      String normalizedRegistration)
            throws Exception {
        String path = link == null ? "" : link.replaceAll("[?#].*$", "");
        String slug = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.US);
        String[] parts = slug.split("-");
        int registrationEnd = -1;
        StringBuilder registration = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            registration.append(parts[i].replaceAll("[^a-z0-9]", ""));
            if (registration.toString().equalsIgnoreCase(normalizedRegistration)) {
                registrationEnd = i + 1;
                break;
            }
            if (registration.length() >= normalizedRegistration.length()) break;
        }
        if (registrationEnd < 0) return;
        int manufacturer = -1;
        for (int i = registrationEnd; i < parts.length; i++) {
            if (isManufacturer(parts[i])) {
                manufacturer = i;
                break;
            }
        }
        if (manufacturer < registrationEnd) return;
        putIfText(result, "operator", words(parts, registrationEnd, manufacturer));
        String description = aircraftDescription(words(parts, manufacturer, parts.length));
        putIfText(result, "description", description);
        putIfText(result, "type", icaoType(description));
        result.put("metadata_source", "Planespotters.net");
    }

    private static void addPlanespottingMetadata(JSONObject result, String html,
                                                  String normalizedRegistration)
            throws Exception {
        Matcher title = Pattern.compile("(?is)<meta[^>]+property=\\\"og:title\\\"[^>]+content=\\\"([^\\\"]+)\\\"")
                .matcher(html);
        if (!title.find() || !normalizeRegistration(title.group(1))
                .contains(normalizedRegistration)) return;
        int fieldsBefore = result.length();
        putIfText(result, "operator", labelledHtmlValue(html, "Operator"));
        String type = labelledHtmlValue(html, "Type");
        putIfText(result, "description", type.replaceFirst("\\s*\\([A-Z0-9-]{2,6}\\)\\s*$", ""));
        Matcher typeCode = Pattern.compile("\\(([A-Z0-9-]{2,6})\\)\\s*$").matcher(type);
        if (typeCode.find()) putIfText(result, "type", typeCode.group(1));
        putIfText(result, "msn", labelledHtmlValue(html, "MSN"));
        putIfText(result, "status", labelledHtmlValue(html, "Status"));
        copyOperatorToAirline(result);
        if (result.length() > fieldsBefore) result.put("metadata_source", "Planespotting.be");
    }

    private static String labelledHtmlValue(String html, String label) {
        Pattern pattern = Pattern.compile("(?is)<b>\\s*" + Pattern.quote(label)
                + "\\s*</b>\\s*<br\\s*/?>(.{0,500}?)(?=</li>|<li\\b)");
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) return "";
        return cleanHtml(matcher.group(1));
    }

    private static String cleanHtml(String value) {
        return value == null ? "" : value.replaceAll("(?is)<[^>]+>", " ")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static boolean isManufacturer(String value) {
        return value.matches("airbus|boeing|embraer|bombardier|cessna|gulfstream|dassault|"
                + "beech|beechcraft|bell|sikorsky|leonardo|eurocopter|robinson|piper|"
                + "lockheed|antonov|ilyushin|tupolev|saab|pilatus|cirrus|diamond|"
                + "textron|fairchild|fokker|atr|mcdonnell|douglas|dehavilland");
    }

    private static String words(String[] parts, int start, int end) {
        StringBuilder value = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (parts[i].isEmpty()) continue;
            if (value.length() > 0) value.append(' ');
            String part = parts[i];
            if (part.matches("gp")) {
                value.append(part.toUpperCase(Locale.US));
            } else {
                value.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return value.toString();
    }

    private static void putIfText(JSONObject target, String key, String value) throws Exception {
        if (value != null && !value.trim().isEmpty()) target.put(key, value.trim());
    }

    private static void mergeMissing(JSONObject target, JSONObject supplement) throws Exception {
        if (supplement == null) return;
        String[] keys = {"registration", "operator", "airline", "description",
                "type", "msn", "status", "metadata_source"};
        for (String key : keys) {
            if (!AircraftData.meaningful(target.opt(key))
                    && AircraftData.meaningful(supplement.opt(key))) {
                target.put(key, supplement.optString(key).trim());
            }
        }
        copyOperatorToAirline(target);
    }

    private static String registrationFromPlanespottersLink(String link) {
        String path = link == null ? "" : link.replaceAll("[?#].*$", "");
        String slug = path.substring(path.lastIndexOf('/') + 1)
                .toUpperCase(Locale.US).replaceAll("[^A-Z0-9-]", "");
        String[] parts = slug.split("-");
        if (parts.length == 0) return "";
        if (parts[0].matches("[A-Z0-9]{4,6}")) return parts[0];
        if (parts.length > 1 && parts[0].matches("[A-Z0-9]{1,3}")
                && parts[1].matches("[A-Z0-9]{1,5}")) {
            return parts[0] + "-" + parts[1];
        }
        return "";
    }

    private static String aircraftDescription(String value) {
        Matcher boeing = Pattern.compile(
                "(?i)^Boeing (737|747|757|767|777) ([2-9])[A-Z0-9]*$")
                .matcher(value == null ? "" : value.trim());
        if (boeing.matches()) return "Boeing " + boeing.group(1) + "-"
                + boeing.group(2) + "00";
        return value;
    }

    private static String icaoType(String description) {
        Matcher boeing = Pattern.compile(
                "(?i)^Boeing (737|747|757|767|777)-([2-9])00$")
                .matcher(description == null ? "" : description.trim());
        if (!boeing.matches()) return "";
        String family = boeing.group(1);
        return "B" + family.substring(0, 2) + boeing.group(2);
    }

    private static void copyOperatorToAirline(JSONObject result) throws Exception {
        if (!AircraftData.meaningful(result.opt("airline"))
                && AircraftData.meaningful(result.opt("operator"))) {
            result.put("airline", result.optString("operator").trim());
        }
    }

    private static String firstMeaningful(String... values) {
        for (String value : values) {
            if (AircraftData.meaningful(value)) return value.trim();
        }
        return "";
    }

    private static String normalizeHex(String value) {
        String hex = value == null ? "" : value.replace("~", "")
                .trim().toUpperCase(Locale.US);
        return hex.matches("[0-9A-F]{6}") ? hex : "";
    }

    static JSONObject parseWikimedia(String json, String normalizedRegistration)
            throws Exception {
        JSONObject pagesObject = new JSONObject(json).optJSONObject("query");
        if (pagesObject == null) return new JSONObject();
        pagesObject = pagesObject.optJSONObject("pages");
        if (pagesObject == null) return new JSONObject();
        JSONArray names = pagesObject.names();
        if (names == null) return new JSONObject();
        for (int i = 0; i < names.length(); i++) {
            JSONObject page = pagesObject.optJSONObject(names.optString(i));
            JSONArray imageInfo = page == null ? null : page.optJSONArray("imageinfo");
            JSONObject info = imageInfo == null ? null : imageInfo.optJSONObject(0);
            if (info == null) continue;
            JSONObject metadata = info.optJSONObject("extmetadata");
            String searchable = page.optString("title") + " "
                    + metadataValue(metadata, "ImageDescription") + " "
                    + metadataValue(metadata, "ObjectName");
            if (!normalizeRegistration(searchable).contains(normalizedRegistration)) continue;
            String image = info.optString("thumburl", info.optString("url"));
            if (image.isEmpty()) continue;
            String artist = metadataValue(metadata, "Artist")
                    .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            JSONObject fallback = photo(image, info.optString("descriptionurl"),
                    "Wikimedia Commons", artist);
            fallback.put("uncertain", true);
            return fallback;
        }
        return new JSONObject();
    }

    private static JSONObject photo(String image, String link, String source, String credit)
            throws Exception {
        return new JSONObject().put("image", image).put("link", link)
                .put("source", source).put("credit", credit);
    }

    private static String metadataValue(JSONObject metadata, String key) {
        JSONObject value = metadata == null ? null : metadata.optJSONObject(key);
        return value == null ? "" : value.optString("value");
    }

    private static String wikimediaUrl(String registration, String aircraftType)
            throws Exception {
        String query = "\\\"" + registration.trim() + "\\\" aircraft "
                + (aircraftType == null ? "" : aircraftType.trim());
        return "https://commons.wikimedia.org/w/api.php?action=query&generator=search"
                + "&gsrsearch=" + URLEncoder.encode(query, "UTF-8")
                + "&gsrnamespace=6&gsrlimit=5&prop=imageinfo"
                + "&iiprop=url%7Cextmetadata&iiurlwidth=640&format=json&origin=*";
    }

    private static String get(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(12_000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            if (connection.getResponseCode() != 200) return "";
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line).append('\n');
            }
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String post(String endpoint, String form) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(12_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(form.getBytes("UTF-8"));
            }
            if (connection.getResponseCode() != 200) return "";
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) result.append(line).append('\n');
            }
            return result.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String path(String value) throws Exception {
        return URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20");
    }

    private static String formValue(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value.trim(), "UTF-8");
    }

    private static String normalizeRegistration(String value) {
        return value == null ? "" : value.toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static String registrationPattern(String normalizedRegistration) {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < normalizedRegistration.length(); i++) {
            if (i > 0) pattern.append("[^A-Z0-9]*");
            pattern.append(Pattern.quote(String.valueOf(normalizedRegistration.charAt(i))));
        }
        return pattern.toString();
    }
}
