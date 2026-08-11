package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

final class AircraftPhotoLookup {
    private static final String USER_AGENT =
            "MilitaryAircraftRadar/1.2.45 (+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftPhotoLookup() { }

    static JSONObject find(String registration, String aircraftType) {
        String normalized = normalizeRegistration(registration);
        if (normalized.isEmpty()) return new JSONObject();
        JSONObject planespotters = new JSONObject();
        JSONObject verifiedMetadata = new JSONObject();
        try {
            planespotters = parsePlanespotters(get(
                    "https://api.planespotters.net/pub/photos/reg/" + path(registration)),
                    normalized);
        } catch (Exception ignored) { }
        String planespottingHtml = "";
        try {
            planespottingHtml = get(
                    "https://www.planespotting.be/index.php?page=aircraft&registration="
                            + path(registration));
            JSONObject planespotting = parsePlanespotting(planespottingHtml, normalized);
            JSONObject planespottingMetadata = new JSONObject();
            addPlanespottingMetadata(planespottingMetadata, planespottingHtml, normalized);
            verifiedMetadata = planespottingMetadata;
            mergeMissing(planespotting, planespottingMetadata);
            if (planespotters.length() > 0) {
                mergeMissing(planespotters, planespotting);
                mergeMissing(planespotters, planespottingMetadata);
                if (planespottingMetadata.length() > 0) {
                    planespotters.put("metadata_source", "Planespotting.be");
                }
                return planespotters;
            }
            if (planespotting.length() > 0) return planespotting;
        } catch (Exception ignored) { }
        if (planespotters.length() > 0) return planespotters;
        try {
            JSONObject fallback = parseWikimedia(
                    get(wikimediaUrl(registration, aircraftType)), normalized);
            if (verifiedMetadata.length() > 0) {
                mergeMissing(fallback, verifiedMetadata);
                fallback.put("metadata_verified", true);
            }
            return fallback.length() > 0 ? fallback : verifiedMetadata;
        } catch (Exception ignored) {
            return verifiedMetadata;
        }
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
        if (!normalizeRegistration(link).contains(normalizedRegistration)) {
            return new JSONObject();
        }
        JSONObject result = photo(thumbnail.optString("src"), link,
                "Planespotters.net", photo.optString("photographer"));
        addPlanespottersLinkMetadata(result, link, normalizedRegistration);
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
        putIfText(result, "description", words(parts, manufacturer, parts.length));
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
            value.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return value.toString();
    }

    private static void putIfText(JSONObject target, String key, String value) throws Exception {
        if (value != null && !value.trim().isEmpty()) target.put(key, value.trim());
    }

    private static void mergeMissing(JSONObject target, JSONObject supplement) throws Exception {
        if (supplement == null) return;
        String[] keys = {"operator", "description", "type", "msn", "status",
                "metadata_source"};
        for (String key : keys) {
            if (target.optString(key).trim().isEmpty()
                    && !supplement.optString(key).trim().isEmpty()) {
                target.put(key, supplement.optString(key).trim());
            }
        }
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

    private static String path(String value) throws Exception {
        return URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20");
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
