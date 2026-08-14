package de.julien.flightradius;

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
}
