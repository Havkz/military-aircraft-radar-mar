package de.julien.flightradius;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

final class AppPreferences {
    static final String PREFS = "monitor_settings";
    static final String KEY_RADIUS_KM = "radius_km";
    static final String KEY_THEME = "theme";
    static final String KEY_UNITS = "units";
    static final String KEY_REFRESH_SECONDS = "refresh_seconds";
    static final String KEY_VIBRATION = "vibration";
    static final String KEY_ALERT_RULES = "custom_alert_rules";
    static final String KEY_SQUAWK_ALERT_MODE = "squawk_alert_mode";
    static final String KEY_SQUAWK_CUSTOM_CODE = "squawk_custom_code";
    static final String KEY_SQUAWK_CUSTOM_CODES = "squawk_custom_codes";
    static final String KEY_SQUAWK_ALERT_INTERVAL_MINUTES = "squawk_alert_interval_minutes";
    static final String KEY_SQUAWK_ADSB_LOL_LAST_ATTEMPT_MS =
            "squawk_adsb_lol_last_attempt_ms";
    static final String KEY_SQUAWK_AIRPLANES_LAST_ATTEMPT_MS =
            "squawk_airplanes_last_attempt_ms";
    static final String KEY_SQUAWK_ADSBX_LAST_ATTEMPT_MS =
            "squawk_adsbx_last_attempt_ms";
    static final String KEY_SQUAWK_ACTIVE_EVENTS = "squawk_active_events";
    static final String KEY_SQUAWK_ACKNOWLEDGED_EVENTS = "squawk_acknowledged_events";
    // Kept only for migrating the legacy detail screen; no longer exposed in settings.
    static final String KEY_TRACKER = "tracker";
    static final String KEY_RUNNING = "service_running";
    static final String KEY_MONITORING_ENABLED = "monitoring_enabled";
    static final String KEY_LIVE_COUNT = "live_count";
    static final String KEY_LAST_SCAN = "last_scan";
    static final String KEY_CONNECTION = "connection";
    static final String KEY_NEAREST_CALLSIGN = "nearest_callsign";
    static final String KEY_NEAREST_DISTANCE_KM = "nearest_distance_km";
    static final String KEY_NEAREST_ALTITUDE_FT = "nearest_altitude_ft";
    static final String KEY_AIRCRAFT_JSON = "aircraft_json";
    static final String KEY_AIRCRAFT_HISTORY_JSON = "aircraft_history_json";
    static final String KEY_AIRPLANES_LAST_ATTEMPT_MS = "airplanes_last_attempt_ms";
    static final String KEY_AIRPLANES_BUSINESS_RATE = "airplanes_business_rate";
    static final String KEY_OWN_LATITUDE = "own_latitude";
    static final String KEY_OWN_LONGITUDE = "own_longitude";
    static final String KEY_APP_VERSION = "app_version";
    static final int DEFAULT_RADIUS_KM = 50;

    private AppPreferences() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isDark(Context context) {
        String selected = get(context).getString(KEY_THEME, "oled");
        if ("oled".equals(selected)) return true;
        if ("light".equals(selected)) return false;
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean usesMetric(Context context) {
        return "metric".equals(get(context).getString(KEY_UNITS, "aviation"));
    }

    static int refreshSeconds(Context context) {
        return get(context).getInt(KEY_REFRESH_SECONDS, 10);
    }

    static void clearLiveTelemetry(Context context) {
        get(context).edit()
                .putInt(KEY_LIVE_COUNT, 0)
                .putLong(KEY_LAST_SCAN, 0L)
                .putString(KEY_CONNECTION, "standby")
                .putString(KEY_AIRCRAFT_JSON, "[]")
                .putString(KEY_AIRCRAFT_HISTORY_JSON, "[]")
                .putString(KEY_NEAREST_CALLSIGN, "")
                .putLong(KEY_NEAREST_DISTANCE_KM,
                        Double.doubleToRawLongBits(Double.NaN))
                .putLong(KEY_NEAREST_ALTITUDE_FT,
                        Double.doubleToRawLongBits(Double.NaN))
                .apply();
    }

    static String distance(Context context, double km) {
        if (Double.isNaN(km)) return "—";
        if (usesMetric(context)) return String.format(Locale.GERMANY, "%.1f km", km);
        return String.format(Locale.US, "%.1f NM", km / 1.852);
    }

    static String altitude(Context context, double feet) {
        if (Double.isNaN(feet)) return L10n.t(context, "altitude_unknown");
        if (usesMetric(context)) return String.format(Locale.GERMANY, "%,.0f m", feet * 0.3048);
        return String.format(Locale.US, "%,.0f ft", feet);
    }
}
