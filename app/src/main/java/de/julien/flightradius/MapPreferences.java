package de.julien.flightradius;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

final class MapPreferences {
    static final String TEXT_SCALE = "map_text_scale";
    static final String ICON_SCALE = "map_icon_scale";
    static final String DARK = "map_dark";
    static final String DARKER = "map_darker";
    static final String DIM = "map_dim";
    static final String COLORED_PLANES = "map_colored_planes";
    static final String COLORED_TRAILS = "map_colored_trails";
    static final String SHOW_LABELS = "map_show_labels";
    static final String LABEL_TRANSPARENCY = "map_label_transparency";
    static final String LABEL_UNITS = "map_label_units";
    static final String SMALL_LABELS = "map_small_labels";
    static final String GEOMETRIC_ALTITUDE = "map_geometric_altitude";
    static final String EGM_CONVERSION = "map_egm_conversion";
    static final String QNH_CORRECTION = "map_qnh_correction";
    static final String TRACK_UTC = "map_track_utc";
    static final String HISTORIC_TRACK_UTC = "map_historic_track_utc";
    static final String LAST_LEG_ONLY = "map_last_leg_only";
    static final String ALTITUDE_CHART = "map_altitude_chart";
    static final String INFOBLOCK = "map_infoblock";
    static final String WIDE_INFOBLOCK = "map_wide_infoblock";
    static final String HOVER_INFOBLOCK = "map_hover_infoblock";
    static final String AUTO_SELECT = "map_auto_select";
    static final String PICTURES_PLANESPOTTERS = "map_pictures_planespotters";
    static final String PICTURES_PLANESPOTTING = "map_pictures_planespotting";
    static final String GROUND_VEHICLES = "map_ground_vehicles";
    static final String NON_ICAO = "map_non_icao";
    static final String UPDATE_GPS = "map_update_gps";
    static final String INCLUDE_FILTERS_URL = "map_include_filters_url";
    static final String KEEP_FADED = "map_keep_faded";
    static final String DEBUG_TRACKS = "map_debug_tracks";
    static final String DEBUG_SHOW_ALL = "map_debug_show_all";

    private static final String[] KEYS = {
            TEXT_SCALE, ICON_SCALE, DARK, DARKER, DIM, COLORED_PLANES,
            COLORED_TRAILS, SHOW_LABELS, LABEL_TRANSPARENCY, LABEL_UNITS, SMALL_LABELS,
            GEOMETRIC_ALTITUDE, EGM_CONVERSION, QNH_CORRECTION,
            TRACK_UTC, HISTORIC_TRACK_UTC,
            LAST_LEG_ONLY, ALTITUDE_CHART, INFOBLOCK, WIDE_INFOBLOCK,
            HOVER_INFOBLOCK,
            AUTO_SELECT, PICTURES_PLANESPOTTERS, PICTURES_PLANESPOTTING,
            GROUND_VEHICLES, NON_ICAO, UPDATE_GPS, INCLUDE_FILTERS_URL,
            KEEP_FADED, DEBUG_TRACKS,
            DEBUG_SHOW_ALL
    };

    private MapPreferences() { }

    static JSONObject json(Context context) {
        SharedPreferences preferences = AppPreferences.get(context);
        JSONObject result = new JSONObject();
        try {
            result.put("textScale", preferences.getFloat(TEXT_SCALE, 1f));
            result.put("iconScale", preferences.getFloat(ICON_SCALE, 1f));
            result.put("dark", preferences.getBoolean(DARK, false));
            result.put("darker", preferences.getBoolean(DARKER, false));
            result.put("dim", preferences.getBoolean(DIM, false));
            result.put("coloredPlanes", preferences.getBoolean(COLORED_PLANES, true));
            result.put("coloredTrails", preferences.getBoolean(COLORED_TRAILS, true));
            result.put("showLabels", preferences.getBoolean(SHOW_LABELS, true));
            result.put("labelTransparency",
                    preferences.getFloat(LABEL_TRANSPARENCY, 0.6f));
            result.put("labelUnits", preferences.getBoolean(LABEL_UNITS, true));
            result.put("smallLabels", preferences.getBoolean(SMALL_LABELS, true));
            result.put("geometricAltitude",
                    preferences.getBoolean(GEOMETRIC_ALTITUDE, false));
            result.put("egmConversion", preferences.getBoolean(EGM_CONVERSION, false));
            result.put("qnhCorrection", preferences.getBoolean(QNH_CORRECTION, false));
            boolean legacyTrackUtc = preferences.getBoolean(TRACK_UTC, true);
            result.put("historicTrackUtc",
                    preferences.getBoolean(HISTORIC_TRACK_UTC, legacyTrackUtc));
            result.put("lastLegOnly", preferences.getBoolean(LAST_LEG_ONLY, true));
            result.put("altitudeChart", preferences.getBoolean(ALTITUDE_CHART, true));
            result.put("infoblock", preferences.getBoolean(INFOBLOCK, true));
            result.put("wideInfoblock", preferences.getBoolean(WIDE_INFOBLOCK, false));
            result.put("hoverInfoblock",
                    preferences.getBoolean(HOVER_INFOBLOCK, false));
            result.put("autoSelect", preferences.getBoolean(AUTO_SELECT, false));
            result.put("picturesPlanespotters",
                    preferences.getBoolean(PICTURES_PLANESPOTTERS, true));
            result.put("picturesPlanespotting",
                    preferences.getBoolean(PICTURES_PLANESPOTTING, false));
            result.put("groundVehicles", preferences.getBoolean(GROUND_VEHICLES, true));
            result.put("nonIcao", preferences.getBoolean(NON_ICAO, true));
            result.put("updateGps", preferences.getBoolean(UPDATE_GPS, true));
            result.put("includeFiltersUrl",
                    preferences.getBoolean(INCLUDE_FILTERS_URL, false));
            result.put("keepFaded", preferences.getBoolean(KEEP_FADED, false));
            result.put("debugTracks", preferences.getBoolean(DEBUG_TRACKS, false));
            result.put("debugShowAll", preferences.getBoolean(DEBUG_SHOW_ALL, false));
            result.put("metric", AppPreferences.usesMetric(context));
            result.put("strings", MapL10n.json(context));
            result.put("rtl", false);
        } catch (Exception ignored) { }
        return result;
    }

    static void reset(Context context) {
        SharedPreferences.Editor editor = AppPreferences.get(context).edit();
        for (String key : KEYS) editor.remove(key);
        editor.apply();
    }
}
