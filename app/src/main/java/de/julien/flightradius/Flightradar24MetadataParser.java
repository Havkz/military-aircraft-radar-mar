package de.julien.flightradius;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Flightradar24MetadataParser {
    private Flightradar24MetadataParser() { }

    static JSONObject parse(String html, String expectedRegistration) {
        JSONObject result = new JSONObject();
        String expected = normalizeRegistration(expectedRegistration);
        if (html == null || html.isEmpty() || expected.isEmpty()) return result;
        Matcher canonical = Pattern.compile(
                "(?is)<link[^>]+rel=[\"']canonical[\"'][^>]+href=[\"'][^\"']*/data/aircraft/([^\"'/?#]+)")
                .matcher(html);
        if (!canonical.find() || !expected.equals(
                normalizeRegistration(canonical.group(1)))) return result;
        try {
            result.put("registration", expectedRegistration.trim().toUpperCase(Locale.US));
            putMeaningful(result, "description", labelledValue(html, "AIRCRAFT"));
            putMeaningful(result, "type", labelledValue(html, "TYPE CODE"));
            String airline = labelledValue(html, "AIRLINE");
            String operator = labelledValue(html, "OPERATOR");
            putMeaningful(result, "airline", airline);
            putMeaningful(result, "operator",
                    AircraftData.meaningful(operator) ? operator : airline);
            putMeaningful(result, "msn", labelledValue(html, "SERIAL NUMBER (MSN)"));
            result.put("metadata_source", "Flightradar24");
        } catch (Exception ignored) { return new JSONObject(); }
        return result;
    }

    static JSONObject parsePhoto(String html, String expectedRegistration,
                                 String expectedType) {
        JSONObject result = new JSONObject();
        String expected = normalizeRegistration(expectedRegistration);
        if (html == null || html.isEmpty() || expected.isEmpty()) return result;
        Matcher canonical = Pattern.compile(
                "(?is)<link[^>]+rel=[\"']canonical[\"'][^>]+href=[\"'][^\"']*/data/aircraft/([^\"'/?#]+)")
                .matcher(html);
        if (!canonical.find() || !expected.equals(
                normalizeRegistration(canonical.group(1)))) return result;
        String pageType = normalizeType(labelledValue(html, "TYPE CODE"));
        String requestedType = normalizeType(expectedType);
        if (!pageType.isEmpty() && !requestedType.isEmpty()
                && !pageType.equals(requestedType)) return result;
        Matcher photo = Pattern.compile(
                "(?is)<span[^>]+class=[\"'][^\"']*label[^\"']*[\"'][^>]*>"
                        + "([^<|]{0,160}?)\\|\\s*Jetphotos\\s*</span>\\s*"
                        + "<a[^>]+href=[\"'](https://www\\.jetphotos\\.com/photo/\\d+)"
                        + "[\"'][^>]*>\\s*<img[^>]+src=[\"']"
                        + "(https://cdn\\.jetphotos\\.com/[^\"']+)[\"']")
                .matcher(html);
        if (!photo.find()) return result;
        try {
            result.put("image", photo.group(3).replace("&amp;", "&"));
            result.put("link", photo.group(2));
            result.put("source", "JetPhotos");
            result.put("credit", cleanPhotoText(photo.group(1)));
            result.put("registration", expectedRegistration.trim().toUpperCase(Locale.US));
            if (!pageType.isEmpty()) result.put("type", pageType);
            result.put("metadata_source", "Flightradar24");
        } catch (Exception ignored) { return new JSONObject(); }
        return result;
    }

    static JSONObject parseSearch(String json, String expectedHex) {
        JSONObject result = new JSONObject();
        String hex = normalizeHex(expectedHex);
        if (json == null || json.isEmpty() || hex.isEmpty()) return result;
        try {
            JSONArray matches = new JSONObject(json).optJSONArray("results");
            for (int i = 0; matches != null && i < matches.length(); i++) {
                JSONObject match = matches.optJSONObject(i);
                JSONObject detail = match == null ? null : match.optJSONObject("detail");
                if (detail == null
                        || !hex.equals(normalizeHex(detail.optString("hex")))) continue;
                String resultType = match.optString("type");
                if ("live".equalsIgnoreCase(resultType)) {
                    putMeaningful(result, "registration", detail.optString("reg"));
                    putMeaningful(result, "type", detail.optString("ac_type"));
                } else if ("aircraft".equalsIgnoreCase(resultType)) {
                    putMeaningful(result, "registration", match.optString("id"));
                    putMeaningful(result, "type", detail.optString("equip"));
                }
                if (result.length() == 0) continue;
                break;
            }
            if (result.length() > 0) result.put("metadata_source", "Flightradar24");
        } catch (Exception ignored) { return new JSONObject(); }
        return result;
    }

    static JSONObject parseFlightDetails(String json, String expectedHex) {
        JSONObject result = new JSONObject();
        String hex = normalizeHex(expectedHex);
        if (json == null || json.isEmpty() || hex.isEmpty()) return result;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject aircraft = root.optJSONObject("aircraft");
            if (aircraft == null || !hex.equals(normalizeHex(
                    aircraft.optString("hex")))) return result;
            JSONObject model = aircraft.optJSONObject("model");
            putMeaningful(result, "registration", aircraft.optString("registration"));
            putMeaningful(result, "type", model == null ? "" : model.optString("code"));
            putMeaningful(result, "description",
                    model == null ? "" : model.optString("text"));
            putMeaningful(result, "msn", aircraft.optString("msn"));
            addJsonPhoto(result, aircraft.optJSONObject("images"));
            String airline = root.optJSONObject("airline") == null ? ""
                    : root.optJSONObject("airline").optString("name");
            putMeaningful(result, "airline", airline);
            putMeaningful(result, "operator", airline);
            if (result.length() > 0) result.put("metadata_source", "Flightradar24");
        } catch (Exception ignored) { return new JSONObject(); }
        return result;
    }

    static void mergeMissing(JSONObject target, JSONObject supplement) throws Exception {
        if (target == null || supplement == null) return;
        java.util.Iterator<String> keys = supplement.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!AircraftData.meaningful(target.opt(key))
                    && AircraftData.meaningful(supplement.opt(key))) {
                target.put(key, supplement.opt(key));
            }
        }
    }

    private static void addJsonPhoto(JSONObject result, JSONObject images) throws Exception {
        JSONObject photo = firstPhoto(images == null ? null : images.optJSONArray("large"));
        if (photo == null) {
            photo = firstPhoto(images == null ? null : images.optJSONArray("medium"));
        }
        if (photo == null) {
            photo = firstPhoto(images == null ? null : images.optJSONArray("thumbnails"));
        }
        if (photo == null || !photo.optString("src").startsWith(
                "https://cdn.jetphotos.com/")) return;
        result.put("image", photo.optString("src"));
        result.put("link", photo.optString("link"));
        result.put("source", "JetPhotos");
        result.put("credit", photo.optString("copyright"));
    }

    private static JSONObject firstPhoto(JSONArray photos) {
        return photos == null || photos.length() == 0
                ? null : photos.optJSONObject(0);
    }

    private static String labelledValue(String html, String label) {
        Matcher matcher = Pattern.compile("(?is)<label>\\s*" + Pattern.quote(label)
                + "\\s*</label>\\s*<span[^>]*class=[\"'][^\"']*details[^\"']*[\"'][^>]*>(.*?)</span>")
                .matcher(html);
        return matcher.find() ? cleanHtml(matcher.group(1)) : "";
    }

    private static void putMeaningful(JSONObject result, String key, String value)
            throws Exception {
        if (AircraftData.meaningful(value)) result.put(key, value.trim());
    }

    private static String cleanHtml(String value) {
        return value == null ? "" : value.replaceAll("(?is)<[^>]+>", " ")
                .replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
    }

    private static String normalizeRegistration(String value) {
        return value == null ? "" : value.toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
    }

    private static String normalizeHex(String value) {
        String hex = value == null ? "" : value.replace("~", "").trim()
                .toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        return hex.length() == 6 ? hex : "";
    }

    private static String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.US)
                .replaceAll("[^A-Z0-9]", "");
        return type.matches("[A-Z0-9]{2,6}") ? type : "";
    }

    private static String cleanPhotoText(String value) {
        return value == null ? "" : value.replaceAll("(?is)<[^>]+>", " ")
                .replace("&copy;", "").replace("&#169;", "")
                .replace("&amp;", "&").replaceAll("\\s+", " ").trim();
    }
}
