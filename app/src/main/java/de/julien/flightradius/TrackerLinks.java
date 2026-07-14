package de.julien.flightradius;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;
import java.util.Locale;

final class TrackerLinks {
    private TrackerLinks() { }

    static Uri selected(Context context, String callsign, String hex, double lat, double lon) {
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

    static Intent selectedIntent(Context context, String callsign, String hex,
                                 double lat, double lon) {
        Uri uri = selected(context, callsign, hex, lat, lon);
        Intent browserFallback = new Intent(Intent.ACTION_VIEW, uri);
        String tracker = AppPreferences.get(context).getString(
                AppPreferences.KEY_TRACKER, "flightradar");
        if ("adsbexchange".equals(tracker)) {
            Intent webApp = adsbExchangeWebAppIntent(context, uri);
            return webApp != null ? webApp : browserFallback;
        }

        Intent appIntent = new Intent(Intent.ACTION_VIEW, uri)
                .setPackage("com.flightradar24free");
        if (appIntent.resolveActivity(context.getPackageManager()) != null) return appIntent;
        return browserFallback;
    }

    private static Intent adsbExchangeWebAppIntent(Context context, Uri uri) {
        Intent lookup = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE);
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> handlers = packageManager.queryIntentActivities(
                lookup, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo handler : handlers) {
            if (handler.activityInfo == null) continue;
            String packageName = handler.activityInfo.packageName;
            if (packageName == null || (!packageName.startsWith("org.chromium.webapk.")
                    && !packageName.startsWith("com.google.android.webapk."))) continue;
            return new Intent(Intent.ACTION_VIEW, uri)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setComponent(new ComponentName(packageName, handler.activityInfo.name));
        }
        return null;
    }
}
