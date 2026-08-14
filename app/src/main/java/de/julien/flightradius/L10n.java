package de.julien.flightradius;

import android.app.Activity;
import android.content.Context;
import android.view.View;

final class L10n {
    private L10n() { }

    static void applyDirection(Activity activity) {
        activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    static String t(Context context, String key) {
        switch (key) {
            case "live_radar": return "LIVE RADAR";
            case "app_title": return "MILITARY\nAIRCRAFT RADAR";
            case "settings": return "Settings";
            case "contacts": return "CONTACTS";
            case "last_scan": return "LAST SCAN";
            case "nearest": return "NEAREST MILITARY CONTACT";
            case "scan_radius": return "SCAN RADIUS";
            case "recommended_distance": return "RECOMMENDED DISTANCE";
            case "recommended_short": return "RECOMMENDED";
            case "battery": return "BATTERY OPTIMIZATION";
            case "not_available": return "Not available";
            case "aircraft": return "AIRCRAFT";
            case "map": return "MAP";
            case "location_required": return "Location permission is required";
            case "radar_active": return "● RADAR ACTIVE";
            case "radar_standby": return "○ RADAR ON STANDBY";
            case "stop_monitoring": return "STOP MONITORING";
            case "start_monitoring": return "START MONITORING";
            case "paused": return "System paused";
            case "connected": return "Connected to live data";
            case "waiting_location": return "Waiting for location signal";
            case "connecting": return "Connecting …";
            case "no_contact": return "No contact";
            case "back": return "BACK";
            case "configuration": return "APP CONFIGURATION";
            case "appearance": return "APPEARANCE";
            case "theme": return "Theme";
            case "oled_dark": return "OLED Dark";
            case "light": return "Light";
            case "system": return "System";
            case "units": return "Units";
            case "aviation_units": return "Aviation (NM / ft / kt / ft/min)";
            case "metric_units": return "Metric (km / m / km/h / m/s)";
            case "live_section": return "LIVE RADAR";
            case "refresh_rate": return "Refresh rate";
            case "tracker_tap": return "Tracker on notification tap";
            case "adsb_recommended": return "ADS-B Exchange is recommended, as some aircraft may not be visible on Flightradar24.";
            case "vibration": return "Vibrate for new contact";
            case "information": return "INFORMATION";
            case "legal": return "LEGAL & DATA SOURCES";
            case "legal_data": return "Contains information from ADSB.lol, Airplanes.live, authorized embedded Flightradar24 and ADS-B Exchange visible-map sessions on Android, optionally the authenticated ADS-B Exchange API, and callsign route or airline data from ADSB.lol VRS standing data and ADSBDB. Registration countries may be inferred from ICAO 24-bit state allocations and can differ from the operator or current base. Routes are plausibility-checked against the live aircraft position and direction but may still be incomplete or inaccurate. Data is provided as-is.";
            case "legal_trackers": return "Flightradar24 and ADS-B Exchange are independent third-party services. MAR's Android visible-map source access is project-specific and authorized; both services can also be optional outbound destinations. MAR is not affiliated with or endorsed by them. All names and trademarks belong to their respective owners.";
            case "live_aircraft": return "LIVE AIRCRAFT";
            case "session_aircraft": return "SESSION AIRCRAFT";
            case "since_app_start": return "MILITARY CONTACTS SEEN SINCE APP START";
            case "military_in_radius": return "MILITARY CONTACTS IN SELECTED RADIUS";
            case "empty_list": return "No military contacts detected.\nLive scan continues.";
            case "no_callsign": return "NO CALLSIGN";
            case "distance": return "Distance";
            case "altitude": return "Altitude";
            case "ground_speed": return "Ground speed";
            case "track": return "Track";
            case "last_signal": return "Last signal";
            case "position": return "Position";
            case "status": return "Status";
            case "in_range": return "In range";
            case "out_of_range": return "Out of range";
            case "in_range_time": return "IN RANGE";
            case "first_in_range": return "First in range";
            case "last_in_range": return "Last in range";
            case "open_in": return "OPEN IN ";
            case "altitude_unknown": return "Altitude unknown";
            case "background_service": return "MAR background service";
            case "background_description": return "Silent Android indicator required for monitoring";
            case "alert_channel": return "Military aircraft detected";
            case "alert_description": return "Military, rotorcraft, and worldwide squawk alerts";
            case "monitoring_running": return "Monitoring is running";
            case "stop_radar": return "STOP RADAR";
            case "restart_reminders": return "Restart reminders";
            case "restart_description": return "Reminder to restart monitoring after a device reboot";
            case "restart_monitoring": return "Restart monitoring";
            case "tap_open_radar": return "Open radar";
            default: return key;
        }
    }
}
