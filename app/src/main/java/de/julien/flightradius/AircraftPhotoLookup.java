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
            "MilitaryAircraftRadar/1.2.20 (+https://github.com/Havkz/military-aircraft-radar-mar)";

    private AircraftPhotoLookup() { }

    static JSONObject find(String registration, String aircraftType) {
        String normalized = normalizeRegistration(registration);
        if (normalized.isEmpty()) return new JSONObject();
        try {
            JSONObject result = parsePlanespotters(get(
                    "https://api.planespotters.net/pub/photos/reg/" + path(registration)),
                    normalized);
            if (result.length() > 0) return result;
        } catch (Exception ignored) { }
        try {
            JSONObject result = parsePlanespotting(get(
                    "https://www.planespotting.be/index.php?page=aircraft&registration="
                            + path(registration)), normalized);
            if (result.length() > 0) return result;
        } catch (Exception ignored) { }
        try {
            return parseWikimedia(get(wikimediaUrl(registration, aircraftType)), normalized);
        } catch (Exception ignored) {
            return new JSONObject();
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
        return photo(thumbnail.optString("src"), link,
                "Planespotters.net", photo.optString("photographer"));
    }

    static JSONObject parsePlanespotting(String html, String normalizedRegistration)
            throws Exception {
        String registration = registrationPattern(normalizedRegistration);
        Pattern card = Pattern.compile("(?is)href=\\\"([^\\\"]*registration="
                + registration + "(?=[^A-Z0-9])[^\\\"]*)\\\".{0,900}?<img[^>]+src=\\\"(https://www\\.planespotting\\.be/uploads/[^\\\"]+-(?:thumb|spotlight)\\.[a-z0-9]+)\\\"");
        Matcher match = card.matcher(html);
        if (!match.find()) return new JSONObject();
        return photo(match.group(2), match.group(1).replace("&amp;", "&"),
                "Planespotting.be", "");
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
            return photo(image, info.optString("descriptionurl"),
                    "Wikimedia Commons", artist);
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
