package de.julien.flightradius;

import android.content.Context;
import android.net.Uri;

import java.util.Locale;

final class TrackerLinks {
    private TrackerLinks() { }

    static Uri selected(Context context, String hex, double lat, double lon) {
        String tracker = AppPreferences.get(context).getString(AppPreferences.KEY_TRACKER, "flightradar");
        if ("adsbexchange".equals(tracker)) {
            return Uri.parse("https://globe.adsbexchange.com/?icao=" + Uri.encode(hex));
        }
        if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
            return Uri.parse(String.format(Locale.US,
                    "https://www.flightradar24.com/%.5f,%.5f/10", lat, lon));
        }
        return Uri.parse("https://www.flightradar24.com/");
    }

    static String selectedName(Context context) {
        return "adsbexchange".equals(AppPreferences.get(context)
                .getString(AppPreferences.KEY_TRACKER, "flightradar"))
                ? "ADS-B Exchange" : "Flightradar24";
    }
}
