package de.julien.flightradius;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

public class MonitorService extends Service implements LocationListener {
    private static final String CHANNEL_STATUS = "monitor_status_v4";
    private static final String CHANNEL_ALERTS = "military_alerts_v3";
    static final String ACTION_STOP = "de.julien.flightradius.STOP";
    private static final String ACTION_NOTIFICATION_DISMISSED =
            "de.julien.flightradius.NOTIFICATION_DISMISSED";
    static final String ACTION_RADIUS_CHANGED = "de.julien.flightradius.RADIUS_CHANGED";
    static final String ACTION_SOURCES_CHANGED = "de.julien.flightradius.SOURCES_CHANGED";
    static final String ACTION_VIEWPORT_CHANGED = "de.julien.flightradius.VIEWPORT_CHANGED";
    private static final long DISMISSED_RESEND_DELAY_MS = 5 * 60 * 1000L;
    private static final long ADSB_LOL_MILITARY_REFRESH_MS = 60_000L;
    private static final long ADSB_LOL_BASE_REFRESH_MS = 1_000L;
    private static final long AIRPLANES_REFRESH_MS = 180_000L;
    private static final long AIRPLANES_BUSINESS_REFRESH_MS = 1_200L;
    private static final long ADSBX_REFRESH_MS = 30_000L;
    private static final long MAP_AIRCRAFT_CACHE_MS = 2 * 60_000L;
    private static final int MAX_MAP_CACHE_AIRCRAFT = 2_000;
    private static final double MAP_MAX_POSITION_AGE_SECONDS = 210d;
    private static final int EXPANDED_MAP_RADIUS_NM = 250;
    private static final int MAP_SUPPLEMENTAL_RADIUS_NM = 45;
    private static final long MAP_SUPPLEMENTAL_DWELL_MS = 12_000L;
    private static final double NAUTICAL_MILE_KM = 1.852d;
    private static final long WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L;
    private static final long WAKE_LOCK_RENEW_MS = 9 * 60_000L;
    private static final int STATUS_NOTIFICATION_ID = 1001;
    private static final String MILITARY_ENDPOINT = "https://api.adsb.lol/v2/mil";
    private static volatile String latestAllAircraftJson = "[]";
    private static volatile double latestOwnLatitude = Double.NaN;
    private static volatile double latestOwnLongitude = Double.NaN;
    private static volatile boolean mapVisible;
    private static volatile boolean mapLoading;
    private static volatile double mapCenterLatitude = Double.NaN;
    private static volatile double mapCenterLongitude = Double.NaN;
    private static volatile int mapRadiusNm = 25;
    private static volatile String isolatedMapHex = "";
    private static volatile long mapViewportChangedAtMs = System.currentTimeMillis();
    private static final Map<String, JSONObject> mapAircraftCache = new LinkedHashMap<>();
    private static final Map<String, Long> mapAircraftCacheTimes = new HashMap<>();

    private final Map<String, JSONObject> lastKnownAlerts = new HashMap<>();
    private final Map<String, JSONObject> sessionHistory = new LinkedHashMap<>();
    private final Map<String, Double> geoidOffsetFeet = new HashMap<>();
    private final Map<String, Long> notificationSuppressedUntil = new ConcurrentHashMap<>();
    private final Map<String, Long> customConditionSince = new ConcurrentHashMap<>();
    private final Set<Integer> aircraftNotificationIds = Collections.newSetFromMap(
            new ConcurrentHashMap<Integer, Boolean>());
    private HandlerThread workerThread;
    private Handler worker;
    private ExecutorService networkPool;
    private LocationManager locationManager;
    private PowerManager.WakeLock monitorWakeLock;
    private volatile Location latestLocation;
    private static volatile boolean running;
    private boolean pollingScheduled;
    private boolean explicitlyStopped;
    private JSONArray cachedAdsbLolMilitary = new JSONArray();
    private JSONArray cachedAdsbLolRegional = new JSONArray();
    private JSONArray cachedAdsbLolAlerts = new JSONArray();
    private JSONArray cachedAirplanes = new JSONArray();
    private JSONArray cachedAdsbExchange = new JSONArray();
    private long lastAdsbLolMilitaryFetchMs;
    private long lastAdsbLolRegionalFetchMs;
    private long lastAdsbLolAlertsFetchMs;
    private long lastAirplanesFetchMs;
    private long lastAdsbExchangeFetchMs;
    private final AdaptiveBackoff adsbLolBackoff = new AdaptiveBackoff();
    private final AdaptiveBackoff airplanesBackoff = new AdaptiveBackoff();

    static boolean isRunning() { return running; }
    static synchronized String latestAllAircraftJson() {
        latestAllAircraftJson = visibleMapAircraftCacheJson(System.currentTimeMillis()).toString();
        return latestAllAircraftJson;
    }
    static double latestOwnLatitude() { return latestOwnLatitude; }
    static double latestOwnLongitude() { return latestOwnLongitude; }
    static void setMapVisible(boolean visible) { mapVisible = visible; }
    static boolean isMapLoading() { return mapVisible && mapLoading; }
    static boolean setMapViewport(double latitude, double longitude, int radiusNm) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return false;
        int boundedRadius = boundedViewportRadiusNm(radiusNm);
        boolean changed = Double.isNaN(mapCenterLatitude)
                || DistanceCalculator.kilometers(mapCenterLatitude, mapCenterLongitude,
                latitude, longitude) > Math.max(.5d, boundedRadius * NAUTICAL_MILE_KM * .03d)
                || Math.abs(mapRadiusNm - boundedRadius) >= Math.max(1, boundedRadius / 20);
        mapCenterLatitude = latitude;
        mapCenterLongitude = longitude;
        mapRadiusNm = boundedRadius;
        if (changed) {
            mapViewportChangedAtMs = System.currentTimeMillis();
            pruneMapAircraftCacheToViewport();
        }
        return changed;
    }

    static synchronized boolean setMapIsolatedAircraft(String rawHex) {
        String hex = rawHex == null ? "" : rawHex.replace("~", "")
                .trim().toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        if (hex.length() != 6) hex = "";
        if (hex.equals(isolatedMapHex)) return false;
        isolatedMapHex = hex;
        mapViewportChangedAtMs = System.currentTimeMillis();
        clearMapAircraftCache();
        return true;
    }

    static int boundedViewportRadiusNm(int requestedRadiusNm) {
        return Math.max(1, Math.min(EXPANDED_MAP_RADIUS_NM, requestedRadiusNm));
    }

    static int requestRadiusNm(int alertRadiusKm, boolean expandedMap) {
        if (expandedMap) return EXPANDED_MAP_RADIUS_NM;
        return Math.max(1, Math.min(EXPANDED_MAP_RADIUS_NM,
                (int) Math.ceil(alertRadiusKm / NAUTICAL_MILE_KM)));
    }

    static long airplanesBaseRefreshMs(boolean businessRateAuthorized) {
        return businessRateAuthorized
                ? AIRPLANES_BUSINESS_REFRESH_MS : AIRPLANES_REFRESH_MS;
    }

    static boolean shouldQueryMapSupplementalSources(
            boolean expandedMap, int radiusNm, long viewportStableMs) {
        return !expandedMap || radiusNm <= MAP_SUPPLEMENTAL_RADIUS_NM
                || viewportStableMs >= MAP_SUPPLEMENTAL_DWELL_MS;
    }

    static boolean viewportCoversAlertArea(double viewportLatitude,
                                           double viewportLongitude,
                                           int viewportRadiusNm,
                                           double ownLatitude,
                                           double ownLongitude,
                                           int alertRadiusKm) {
        double offsetKm = DistanceCalculator.kilometers(viewportLatitude, viewportLongitude,
                ownLatitude, ownLongitude);
        return !Double.isNaN(offsetKm)
                && offsetKm + alertRadiusKm <= viewportRadiusNm * NAUTICAL_MILE_KM;
    }

    static String mapAircraftEndpoint(double latitude, double longitude, int radiusNm,
                                      String isolatedHex) {
        if (isolatedHex != null && isolatedHex.matches("[0-9a-fA-F]{6}")) {
            return "https://api.adsb.lol/v2/hex/" + isolatedHex.toLowerCase(Locale.US);
        }
        return String.format(Locale.US,
                "https://api.adsb.lol/v2/lat/%.5f/lon/%.5f/dist/%d",
                latitude, longitude, boundedViewportRadiusNm(radiusNm));
    }

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            long startedAt = System.currentTimeMillis();
            try {
                pollAircraft();
            }
            finally {
                if (worker != null) {
                    long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
                    worker.postDelayed(this, Math.max(0L, nextAdsbLolDelayMs() - elapsed));
                }
            }
        }
    };

    private final Runnable renewWakeLockTask = new Runnable() {
        @Override public void run() {
            if (monitorWakeLock == null || worker == null) return;
            if (monitorWakeLock.isHeld()) monitorWakeLock.release();
            monitorWakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
            worker.postDelayed(this, WAKE_LOCK_RENEW_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(STATUS_NOTIFICATION_ID,
                statusNotification("INITIALIZING",
                        L10n.t(this, "waiting_location"), 0));
        PowerManager power = getSystemService(PowerManager.class);
        if (power != null) {
            monitorWakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MAR:active-monitoring");
            monitorWakeLock.setReferenceCounted(false);
            monitorWakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        }

        workerThread = new HandlerThread("military-live-monitor");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        if (monitorWakeLock != null) {
            worker.postDelayed(renewWakeLockTask, WAKE_LOCK_RENEW_MS);
        }
        networkPool = Executors.newFixedThreadPool(4);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        long storedLatitude = AppPreferences.get(this).getLong(
                AppPreferences.KEY_OWN_LATITUDE, Double.doubleToRawLongBits(Double.NaN));
        long storedLongitude = AppPreferences.get(this).getLong(
                AppPreferences.KEY_OWN_LONGITUDE, Double.doubleToRawLongBits(Double.NaN));
        double latitude = Double.longBitsToDouble(storedLatitude);
        double longitude = Double.longBitsToDouble(storedLongitude);
        if (!Double.isNaN(latitude) && !Double.isNaN(longitude)) {
            Location stored = new Location("stored");
            stored.setLatitude(latitude);
            stored.setLongitude(longitude);
            stored.setTime(System.currentTimeMillis());
            latestLocation = stored;
            latestOwnLatitude = latitude;
            latestOwnLongitude = longitude;
        }
        loadSessionHistory();
        if (hasLocationPermission()) {
            registerProvider(LocationManager.GPS_PROVIDER);
            registerProvider(LocationManager.NETWORK_PROVIDER);
        }
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            explicitlyStopped = true;
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_RUNNING, false)
                    .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                    .apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!AppPreferences.get(this).getBoolean(AppPreferences.KEY_RUNNING, false)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_NOTIFICATION_DISMISSED.equals(intent.getAction())) {
            String hex = intent.getStringExtra("hex");
            if (hex != null && !hex.isEmpty()) {
                notificationSuppressedUntil.put(hex,
                        System.currentTimeMillis() + DISMISSED_RESEND_DELAY_MS);
                aircraftNotificationIds.remove(hex.hashCode());
                getSystemService(NotificationManager.class).cancel(hex.hashCode());
            }
        }
        if (intent != null && (ACTION_RADIUS_CHANGED.equals(intent.getAction())
                || ACTION_SOURCES_CHANGED.equals(intent.getAction())
                || ACTION_VIEWPORT_CHANGED.equals(intent.getAction()))
                && pollingScheduled && worker != null) {
            worker.post(() -> {
                worker.removeCallbacks(pollTask);
                worker.post(pollTask);
            });
        } else if (!pollingScheduled && worker != null) {
            pollingScheduled = true;
            worker.post(pollTask);
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        boolean shouldRestart = !explicitlyStopped && AppPreferences.get(this)
                .getBoolean(AppPreferences.KEY_MONITORING_ENABLED, false);
        if (shouldRestart) {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_RUNNING, true)
                    .putString(AppPreferences.KEY_CONNECTION, "restarting").apply();
        } else {
            AppPreferences.get(this).edit()
                    .putBoolean(AppPreferences.KEY_RUNNING, false)
                    .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                    .putString(AppPreferences.KEY_CONNECTION, "standby").apply();
            AppPreferences.clearLiveTelemetry(this);
            clearMapAircraftCache();
        }
        if (locationManager != null) locationManager.removeUpdates(this);
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (workerThread != null) workerThread.quitSafely();
        if (networkPool != null) networkPool.shutdownNow();
        if (monitorWakeLock != null && monitorWakeLock.isHeld()) {
            monitorWakeLock.release();
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (!shouldRestart) {
            for (Integer notificationId : aircraftNotificationIds) {
                notificationManager.cancel(notificationId);
            }
            aircraftNotificationIds.clear();
            notificationManager.cancel(STATUS_NOTIFICATION_ID);
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        super.onDestroy();
    }

    @Override public void onLocationChanged(Location location) {
        latestLocation = location;
        publishOwnLocation(location);
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void registerProvider(String provider) {
        try {
            if (!locationManager.isProviderEnabled(provider)) return;
            locationManager.requestLocationUpdates(provider, 30_000L, 50f, this);
            Location lastKnown = locationManager.getLastKnownLocation(provider);
            if (lastKnown != null && (latestLocation == null
                    || lastKnown.getTime() > latestLocation.getTime())) {
                latestLocation = lastKnown;
                publishOwnLocation(lastKnown);
            }
        } catch (IllegalArgumentException | SecurityException ignored) { }
    }

    private void publishOwnLocation(Location location) {
        latestOwnLatitude = location.getLatitude();
        latestOwnLongitude = location.getLongitude();
        AppPreferences.get(this).edit()
                .putLong(AppPreferences.KEY_OWN_LATITUDE,
                        Double.doubleToRawLongBits(latestOwnLatitude))
                .putLong(AppPreferences.KEY_OWN_LONGITUDE,
                        Double.doubleToRawLongBits(latestOwnLongitude))
                .apply();
    }

    private void pollAircraft() {
        Location own = latestLocation;
        int radiusKm = AppPreferences.get(this)
                .getInt(AppPreferences.KEY_RADIUS_KM, AppPreferences.DEFAULT_RADIUS_KM);
        if (own == null) {
            publishTelemetry("no_location", 0, new JSONArray(), "", Double.NaN, Double.NaN);
            updateStatus("NO LOCATION", "", 0);
            return;
        }

        boolean expandedMap = mapVisible;
        boolean viewportAvailable = expandedMap && !Double.isNaN(mapCenterLatitude)
                && !Double.isNaN(mapCenterLongitude);
        double queryLatitude = viewportAvailable ? mapCenterLatitude : own.getLatitude();
        double queryLongitude = viewportAvailable ? mapCenterLongitude : own.getLongitude();
        String isolatedHex = expandedMap ? isolatedMapHex : "";
        boolean isolatedMapQuery = !isolatedHex.isEmpty();
        int radiusNm = viewportAvailable ? mapRadiusNm
                : requestRadiusNm(radiusKm, false);
        int alertRadiusNm = requestRadiusNm(radiusKm, false);
        boolean regionalCoversAlertArea = !isolatedMapQuery
                && (!viewportAvailable || viewportCoversAlertArea(
                queryLatitude, queryLongitude, radiusNm,
                own.getLatitude(), own.getLongitude(), radiusKm));
        String localEndpoint = mapAircraftEndpoint(
                queryLatitude, queryLongitude, radiusNm, isolatedHex);

        if (expandedMap) mapLoading = true;
        try {
            long now = System.currentTimeMillis();
            long viewportStableMs = Math.max(0L, now - mapViewportChangedAtMs);
            boolean querySupplemental = shouldQueryMapSupplementalSources(expandedMap,
                    radiusNm, viewportStableMs);
            Future<JSONArray> regionalFuture = networkPool.submit(
                    () -> fetchAircraft(localEndpoint, null, "adsb.lol"));
            Future<JSONArray> alertsFuture = null;
            if (!regionalCoversAlertArea) {
                String alertsEndpoint = String.format(Locale.US,
                        "https://api.adsb.lol/v2/lat/%.5f/lon/%.5f/dist/%d",
                        own.getLatitude(), own.getLongitude(), alertRadiusNm);
                alertsFuture = networkPool.submit(
                        () -> fetchAircraft(alertsEndpoint, null, "adsb.lol"));
            }
            Future<JSONArray> militaryFuture = null;
            Future<JSONArray> airplanesFuture = null;
            Future<JSONArray> adsbxFuture = null;
            if (querySupplemental
                    && now - lastAdsbLolMilitaryFetchMs >= ADSB_LOL_MILITARY_REFRESH_MS) {
                militaryFuture = networkPool.submit(
                        () -> fetchAircraft(MILITARY_ENDPOINT, null, "adsb.lol"));
            }
            long persistedAirplanesAttempt = AppPreferences.get(this).getLong(
                    AppPreferences.KEY_AIRPLANES_LAST_ATTEMPT_MS, 0L);
            if (querySupplemental
                    && now - Math.max(lastAirplanesFetchMs, persistedAirplanesAttempt)
                    >= nextAirplanesDelayMs()) {
                lastAirplanesFetchMs = now;
                AppPreferences.get(this).edit()
                        .putLong(AppPreferences.KEY_AIRPLANES_LAST_ATTEMPT_MS, now).apply();
                String airplanesEndpoint = String.format(Locale.US,
                        "https://api.airplanes.live/v2/point/%.5f/%.5f/%d",
                        queryLatitude, queryLongitude, radiusNm);
                airplanesFuture = networkPool.submit(
                        () -> fetchAircraft(airplanesEndpoint, null, "airplanes.live"));
            }
            String adsbxKey = ProviderCredentials.adsbExchangeKey(this);
            if (querySupplemental && !adsbxKey.isEmpty()
                    && now - lastAdsbExchangeFetchMs >= ADSBX_REFRESH_MS) {
                String adsbxEndpoint = String.format(Locale.US,
                        "https://api.adsbexchange.com/v2/lat/%.5f/lon/%.5f/dist/%d/",
                        queryLatitude, queryLongitude, radiusNm);
                adsbxFuture = networkPool.submit(
                        () -> fetchAircraft(adsbxEndpoint, adsbxKey, "adsbexchange"));
            }

            boolean receivedLiveFeed = false;
            JSONArray regional = awaitOptional(regionalFuture);
            if (regional != null) {
                cachedAdsbLolRegional = regional;
                lastAdsbLolRegionalFetchMs = now;
                receivedLiveFeed = true;
                if (expandedMap) {
                    long receivedAt = System.currentTimeMillis();
                    JSONArray immediateMapAircraft = compactMapAircraft(
                            taggedCopy(regional, "ADSB.lol", 0d),
                            queryLatitude, queryLongitude, radiusNm);
                    updateMapAircraftCache(immediateMapAircraft, receivedAt);
                    latestAllAircraftJson = visibleMapAircraftCacheJson(receivedAt).toString();
                    mapLoading = false;
                }
            }
            JSONArray alertAircraft = awaitOptional(alertsFuture);
            if (alertAircraft != null) {
                cachedAdsbLolAlerts = alertAircraft;
                lastAdsbLolAlertsFetchMs = now;
                receivedLiveFeed = true;
            }
            JSONArray military = awaitOptional(militaryFuture);
            if (military != null) {
                cachedAdsbLolMilitary = military;
                lastAdsbLolMilitaryFetchMs = now;
                receivedLiveFeed = true;
            }
            JSONArray airplanes = awaitOptional(airplanesFuture);
            if (airplanes != null) {
                cachedAirplanes = airplanes;
                lastAirplanesFetchMs = now;
                receivedLiveFeed = true;
            }
            JSONArray adsbx = awaitOptional(adsbxFuture);
            if (adsbx != null) {
                cachedAdsbExchange = adsbx;
                lastAdsbExchangeFetchMs = now;
                receivedLiveFeed = true;
            }
            if (!receivedLiveFeed && cachedAdsbLolRegional.length() == 0
                    && cachedAdsbLolAlerts.length() == 0
                    && cachedAirplanes.length() == 0 && cachedAdsbExchange.length() == 0) {
                throw new IllegalStateException("No aircraft feed available");
            }

            JSONArray aircraft = AircraftData.mergeByHex(
                    taggedCopy(cachedAdsbLolRegional, "ADSB.lol",
                            ageSeconds(now, lastAdsbLolRegionalFetchMs)),
                    taggedCopy(cachedAdsbLolAlerts, "ADSB.lol alerts",
                            ageSeconds(now, lastAdsbLolAlertsFetchMs)),
                    taggedCopy(cachedAdsbLolMilitary, "ADSB.lol military",
                            ageSeconds(now, lastAdsbLolMilitaryFetchMs)),
                    taggedCopy(cachedAirplanes, "Airplanes.live",
                            ageSeconds(now, lastAirplanesFetchMs)),
                    taggedCopy(cachedAdsbExchange, "ADS-B Exchange",
                            ageSeconds(now, lastAdsbExchangeFetchMs)));
            JSONArray liveAircraft = new JSONArray();
            JSONArray mapAircraftSource = isolatedMapQuery
                    ? taggedCopy(cachedAdsbLolRegional, "ADSB.lol", ageSeconds(
                    now, lastAdsbLolRegionalFetchMs)) : aircraft;
            JSONArray allAircraft = compactMapAircraft(
                    mapAircraftSource, queryLatitude, queryLongitude, radiusNm);
            Set<String> currentlyInside = new HashSet<>();
            long scanTime = System.currentTimeMillis();
            int alertTargetCount = 0;
            List<CustomAlertRules.Rule> customRules = CustomAlertRules.load(this);
            String nearestCallsign = "";
            double nearestDistanceKm = Double.NaN;
            double nearestAltitudeFt = Double.NaN;
            if (aircraft != null) {
                for (int i = 0; i < aircraft.length(); i++) {
                    JSONObject plane = aircraft.optJSONObject(i);
                    // Airplanes.live's free-plan interval is 180 seconds. Keep its last
                    // provider position until the next permitted cross-check instead of
                    // flapping a source-only contact in and out every minute.
                    double[] position = AircraftData.recentPosition(
                            plane, MAP_MAX_POSITION_AGE_SECONDS);
                    if (position == null) continue;
                    double aircraftLat = position[0];
                    double aircraftLon = position[1];
                    double distanceKm = DistanceCalculator.kilometers(
                            own.getLatitude(), own.getLongitude(), aircraftLat, aircraftLon);
                    if (Double.isNaN(distanceKm) || distanceKm > radiusKm) continue;

                    String hex = plane.optString("hex", "unknown").replace("~", "");
                    String callsign = AircraftData.callsign(plane);
                    double altitudeFt = altitudeFeet(
                            plane.opt("alt_geom"), plane.opt("alt_baro"));
                    String customReason = CustomAlertRules.evaluate(customRules, plane, hex,
                            altitudeFt, scanTime, customConditionSince);
                    boolean builtInTarget = isAlertTarget(plane);
                    if (!builtInTarget && customReason.isEmpty()) continue;
                    alertTargetCount++;
                    currentlyInside.add(hex);
                    JSONObject compact = compactAircraft(plane, hex, callsign,
                            distanceKm, altitudeFt, aircraftLat, aircraftLon);
                    if (!customReason.isEmpty()) compact.put("alert_reason", customReason);
                    else if (AircraftData.isRotorcraft(plane)) {
                        compact.put("alert_reason", MapL10n.t(this, "rotorcraft"));
                    } else compact.put("alert_reason", MapL10n.t(this, "military"));
                    liveAircraft.put(compact);
                    updateSessionRecord(compact, scanTime);
                    String displayName = AircraftData.displayName(plane);
                    if (!displayName.isEmpty() && (Double.isNaN(nearestDistanceKm)
                            || distanceKm < nearestDistanceKm)) {
                        nearestCallsign = displayName;
                        nearestDistanceKm = distanceKm;
                        nearestAltitudeFt = altitudeFt;
                    }
                    if (!hex.isEmpty() && !"unknown".equals(hex)) {
                        lastKnownAlerts.put(hex, compact);
                        showAircraftNotification(compact, true);
                    }
                }
            }

            markMissingAircraftOutOfRange(currentlyInside);
            for (Map.Entry<String, JSONObject> entry : lastKnownAlerts.entrySet()) {
                if (!currentlyInside.contains(entry.getKey())) {
                    showAircraftNotification(entry.getValue(), false);
                }
            }
            publishSessionHistory();
            updateMapAircraftCache(allAircraft, scanTime);
            latestAllAircraftJson = visibleMapAircraftCacheJson(scanTime).toString();
            publishTelemetry("connected", alertTargetCount, liveAircraft, nearestCallsign,
                    nearestDistanceKm, nearestAltitudeFt);
            updateStatus("LIVE // " + nowTime(), "", alertTargetCount);
        } catch (Exception e) {
            AppPreferences.get(this).edit().putString(AppPreferences.KEY_CONNECTION, "error").apply();
            updateStatus("SIGNAL LOST", "", 0);
        } finally {
            mapLoading = false;
        }
    }

    private JSONArray fetchAircraft(String endpoint, String apiKey, String provider)
            throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", "MilitaryAircraftRadar/4.1");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            if (apiKey != null && !apiKey.isEmpty()) {
                connection.setRequestProperty("X-Api-Key", apiKey);
                connection.setRequestProperty("api-auth", apiKey);
            }
            int code = connection.getResponseCode();
            if (code == 429) {
                increaseRateLimitBackoff(provider);
                throw new IllegalStateException("Aircraft API HTTP 429");
            }
            if (code != 200) throw new IllegalStateException("Aircraft API HTTP " + code);
            StringBuilder json = new StringBuilder();
            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) {
                stream = new GZIPInputStream(stream);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }
            JSONArray aircraft = new JSONObject(json.toString()).optJSONArray("ac");
            return aircraft == null ? new JSONArray() : aircraft;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONArray compactMapAircraft(JSONArray aircraft, double queryLatitude,
                                         double queryLongitude, int radiusNm) {
        JSONArray result = new JSONArray();
        if (aircraft == null) return result;
        double radiusKm = radiusNm * NAUTICAL_MILE_KM;
        Location own = latestLocation;
        for (int i = 0; i < aircraft.length(); i++) {
            JSONObject plane = aircraft.optJSONObject(i);
            double[] position = AircraftData.recentPosition(
                    plane, MAP_MAX_POSITION_AGE_SECONDS);
            if (plane == null || position == null) continue;
            double queryDistance = DistanceCalculator.kilometers(
                    queryLatitude, queryLongitude, position[0], position[1]);
            if (Double.isNaN(queryDistance) || queryDistance > radiusKm) continue;
            double ownDistance = own == null ? Double.NaN : DistanceCalculator.kilometers(
                    own.getLatitude(), own.getLongitude(), position[0], position[1]);
            if (Double.isNaN(ownDistance)) ownDistance = queryDistance;
            try {
                result.put(compactAircraft(plane,
                        plane.optString("hex", "unknown").replace("~", ""),
                        AircraftData.callsign(plane), ownDistance,
                        altitudeFeet(plane.opt("alt_geom"), plane.opt("alt_baro")),
                        position[0], position[1]));
            } catch (Exception ignored) {
                // One malformed contact must not discard the other aircraft in the viewport.
            }
        }
        return result;
    }

    static synchronized JSONArray updateMapAircraftCache(JSONArray freshAircraft, long now) {
        if (freshAircraft != null) {
            for (int i = 0; i < freshAircraft.length(); i++) {
                JSONObject aircraft = freshAircraft.optJSONObject(i);
                if (aircraft == null) continue;
                String hex = aircraft.optString("hex", "").replace("~", "")
                        .trim().toLowerCase(Locale.US);
                if (hex.isEmpty() || "unknown".equals(hex)) continue;
                mapAircraftCache.remove(hex);
                mapAircraftCache.put(hex, aircraft);
                mapAircraftCacheTimes.put(hex, now);
            }
        }
        while (mapAircraftCache.size() > MAX_MAP_CACHE_AIRCRAFT) {
            String oldest = mapAircraftCache.keySet().iterator().next();
            mapAircraftCache.remove(oldest);
            mapAircraftCacheTimes.remove(oldest);
        }
        return mapAircraftCacheJson(now);
    }

    private static JSONArray mapAircraftCacheJson(long now) {
        JSONArray result = new JSONArray();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : mapAircraftCache.entrySet()) {
            long updated = mapAircraftCacheTimes.containsKey(entry.getKey())
                    ? mapAircraftCacheTimes.get(entry.getKey()) : 0L;
            long ageMs = Math.max(0L, now - updated);
            if (ageMs >= MAP_AIRCRAFT_CACHE_MS) {
                expired.add(entry.getKey());
                continue;
            }
            try {
                JSONObject copy = new JSONObject(entry.getValue().toString());
                copy.put("map_cache_age_seconds", ageMs / 1000d);
                result.put(copy);
            } catch (Exception ignored) { }
        }
        for (String hex : expired) {
            mapAircraftCache.remove(hex);
            mapAircraftCacheTimes.remove(hex);
        }
        return result;
    }

    static synchronized JSONArray visibleMapAircraftCacheJson(long now) {
        JSONArray visible = new JSONArray();
        List<String> expired = new ArrayList<>();
        double radiusKm = mapRadiusNm * NAUTICAL_MILE_KM;
        for (Map.Entry<String, JSONObject> entry : mapAircraftCache.entrySet()) {
            long updated = mapAircraftCacheTimes.containsKey(entry.getKey())
                    ? mapAircraftCacheTimes.get(entry.getKey()) : 0L;
            long ageMs = Math.max(0L, now - updated);
            if (ageMs >= MAP_AIRCRAFT_CACHE_MS) {
                expired.add(entry.getKey());
                continue;
            }
            JSONObject aircraft = entry.getValue();
            double latitude = aircraft.optDouble("lat", Double.NaN);
            double longitude = aircraft.optDouble("lon", Double.NaN);
            boolean inViewport = Double.isNaN(mapCenterLatitude)
                    || Double.isNaN(mapCenterLongitude);
            if (!inViewport) {
                double distance = DistanceCalculator.kilometers(
                        mapCenterLatitude, mapCenterLongitude, latitude, longitude);
                inViewport = !Double.isNaN(distance) && distance <= radiusKm;
            }
            if (!inViewport) continue;
            try {
                JSONObject copy = new JSONObject(aircraft.toString());
                copy.put("map_cache_age_seconds", ageMs / 1000d);
                visible.put(copy);
            } catch (Exception ignored) { }
        }
        for (String hex : expired) {
            mapAircraftCache.remove(hex);
            mapAircraftCacheTimes.remove(hex);
        }
        return visible;
    }

    private static synchronized void pruneMapAircraftCacheToViewport() {
        if (Double.isNaN(mapCenterLatitude) || Double.isNaN(mapCenterLongitude)) return;
        double radiusKm = mapRadiusNm * NAUTICAL_MILE_KM;
        List<String> outside = new ArrayList<>();
        for (Map.Entry<String, JSONObject> entry : mapAircraftCache.entrySet()) {
            JSONObject aircraft = entry.getValue();
            double distance = DistanceCalculator.kilometers(mapCenterLatitude,
                    mapCenterLongitude, aircraft.optDouble("lat", Double.NaN),
                    aircraft.optDouble("lon", Double.NaN));
            if (Double.isNaN(distance) || distance > radiusKm) outside.add(entry.getKey());
        }
        for (String hex : outside) {
            mapAircraftCache.remove(hex);
            mapAircraftCacheTimes.remove(hex);
        }
    }

    private static synchronized void clearMapAircraftCache() {
        mapAircraftCache.clear();
        mapAircraftCacheTimes.clear();
        latestAllAircraftJson = "[]";
    }

    private void increaseRateLimitBackoff(String provider) {
        long now = System.currentTimeMillis();
        if ("adsb.lol".equals(provider)) {
            adsbLolBackoff.recordRateLimit(now);
        } else if ("airplanes.live".equals(provider)) {
            airplanesBackoff.recordRateLimit(now);
        }
    }

    private long nextAdsbLolDelayMs() {
        return adsbLolBackoff.delayMs(
                ADSB_LOL_BASE_REFRESH_MS, System.currentTimeMillis());
    }

    private long nextAirplanesDelayMs() {
        boolean businessRate = AppPreferences.get(this).getBoolean(
                AppPreferences.KEY_AIRPLANES_BUSINESS_RATE, false);
        return airplanesBackoff.delayMs(
                airplanesBaseRefreshMs(businessRate), System.currentTimeMillis());
    }

    private JSONArray awaitOptional(Future<JSONArray> future) {
        if (future == null) return null;
        try {
            return future.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONArray taggedCopy(JSONArray source, String label, double cacheAgeSeconds)
            throws Exception {
        return AircraftData.tagSource(new JSONArray(source.toString()), label, cacheAgeSeconds);
    }

    private double ageSeconds(long now, long fetchedAt) {
        return fetchedAt <= 0L ? Double.POSITIVE_INFINITY
                : Math.max(0d, (now - fetchedAt) / 1000d);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel status = new NotificationChannel(CHANNEL_STATUS,
                L10n.t(this, "background_service"),
                NotificationManager.IMPORTANCE_MIN);
        status.setDescription(L10n.t(this, "background_description"));
        status.setShowBadge(false);
        status.enableLights(false);
        status.enableVibration(false);
        status.setSound(null, null);

        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS,
                L10n.t(this, "alert_channel"), NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription(L10n.t(this, "alert_description"));
        alerts.enableLights(true);
        alerts.setLightColor(MARColors.ORANGE);
        alerts.enableVibration(AppPreferences.get(this)
                .getBoolean(AppPreferences.KEY_VIBRATION, true));
        nm.createNotificationChannel(status);
        nm.createNotificationChannel(alerts);
    }

    private Notification statusNotification(String state, String detail, int count) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, MonitorService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notification_radar)
                .setContentTitle(L10n.t(this, "live_radar"))
                .setContentText(L10n.t(this, "monitoring_running"))
                .setColor(MARColors.DARK_MUTED)
                .setColorized(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(open)
                .addAction(android.R.drawable.ic_media_pause,
                        L10n.t(this, "stop_radar"), stop)
                .build();
    }

    private void updateStatus(String state, String detail, int count) {
        getSystemService(NotificationManager.class).notify(STATUS_NOTIFICATION_ID,
                statusNotification(state, detail, count));
    }

    private void showAircraftNotification(JSONObject aircraft, boolean inRange) {
        if (!running || !AppPreferences.get(this)
                .getBoolean(AppPreferences.KEY_RUNNING, false)) return;
        String callsign = AircraftData.normalizeCallsign(aircraft.optString("callsign", ""));
        String displayName = callsign.isEmpty() ? "NO CALLSIGN" : callsign;
        String hex = aircraft.optString("hex", "");
        if (hex.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long suppressedUntil = notificationSuppressedUntil.get(hex);
        if (suppressedUntil != null) {
            if (!inRange || suppressedUntil > now) return;
            notificationSuppressedUntil.remove(hex);
        }

        double distanceKm = aircraft.optDouble("distance_km", Double.NaN);
        double altitudeFt = aircraft.isNull("altitude_ft")
                ? Double.NaN : aircraft.optDouble("altitude_ft", Double.NaN);
        double aircraftLat = aircraft.optDouble("lat", Double.NaN);
        double aircraftLon = aircraft.optDouble("lon", Double.NaN);
        CharSequence details;
        String alertReason = aircraft.optString("alert_reason", "");
        if (inRange) {
            details = (alertReason.isEmpty() ? "" : alertReason + "  •  ")
                    + AppPreferences.distance(this, distanceKm) + "  •  "
                    + AppPreferences.altitude(this, altitudeFt);
        } else {
            String state = L10n.t(this, "out_of_range");
            SpannableString redState = new SpannableString(state + "  •  "
                    + AppPreferences.altitude(this, altitudeFt));
            redState.setSpan(new ForegroundColorSpan(MARColors.RED),
                    0, state.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            details = redState;
        }
        Intent trackerIntent = new Intent(this, MainActivity.class)
                .setData(android.net.Uri.parse("mar://aircraft/" + android.net.Uri.encode(hex)))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_map", true)
                .putExtra("callsign", callsign)
                .putExtra("hex", hex)
                .putExtra("lat", aircraftLat)
                .putExtra("lon", aircraftLon);
        PendingIntent tracker = PendingIntent.getActivity(this, hex.hashCode() ^ 0x5f3759df,
                trackerIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent dismissedIntent = new Intent(this, MonitorService.class)
                .setAction(ACTION_NOTIFICATION_DISMISSED)
                .setData(android.net.Uri.parse("mar://dismiss/" + android.net.Uri.encode(hex)))
                .putExtra("hex", hex);
        PendingIntent dismissed = PendingIntent.getService(this, hex.hashCode() ^ 0x4d4152,
                dismissedIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_radar)
                .setLargeIcon(radarBitmap(true))
                .setContentTitle(displayName)
                .setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details))
                .setSubText(alertReason.isEmpty() ? displayName : alertReason)
                .setColor(inRange ? MARColors.ORANGE : MARColors.RED)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tracker)
                .setDeleteIntent(dismissed)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true);
        Notification notification = builder.build();
        int notificationId = hex.hashCode();
        aircraftNotificationIds.add(notificationId);
        getSystemService(NotificationManager.class).notify(notificationId, notification);
    }

    static double altitudeFeet(Object geometricValue, Object barometricValue) {
        if (isGroundAltitude(barometricValue)) return 0d;
        double geometricFeet = altitudeValueFeet(geometricValue);
        if (!Double.isNaN(geometricFeet)) return geometricFeet;
        return altitudeValueFeet(barometricValue);
    }

    static boolean isGroundAltitude(Object value) {
        return value instanceof String && "ground".equalsIgnoreCase(
                ((String) value).trim());
    }

    static boolean isAlertTarget(JSONObject aircraft) {
        return MilitaryClassifier.isMilitary(aircraft)
                || AircraftData.isRotorcraft(aircraft);
    }

    private static double altitudeValueFeet(Object value) {
        if (value instanceof Number) {
            double feet = ((Number) value).doubleValue();
            return Double.isInfinite(feet) ? Double.NaN : feet;
        }
        if (value instanceof String && "ground".equalsIgnoreCase((String) value)) return 0;
        return Double.NaN;
    }

    private JSONObject compactAircraft(JSONObject plane, String hex, String callsign,
                                       double distanceKm, double altitudeFt,
                                       double latitude, double longitude) throws Exception {
        JSONObject item = new JSONObject();
        item.put("hex", hex);
        item.put("non_icao", plane.optString("hex", "").startsWith("~"));
        item.put("callsign", callsign);
        item.put("display_name", AircraftData.displayName(plane));
        item.put("registration", plane.optString("r", ""));
        item.put("country", IcaoCountry.providerOrHex(plane.optString("country",
                plane.optString("country_name", "")), hex));
        item.put("operator", plane.optString("ownOp",
                plane.optString("operator", "")));
        item.put("type", plane.optString("t", ""));
        item.put("data_source", plane.optString("type", ""));
        item.put("db_flags", plane.optInt("dbFlags", 0));
        item.put("description", plane.optString("desc",
                plane.optString("typeDescription", "")));
        item.put("category", plane.optString("category", ""));
        item.put("military", MilitaryClassifier.isMilitary(plane));
        item.put("rotorcraft", AircraftData.isRotorcraft(plane));
        item.put("sources", plane.optJSONArray("sources") == null
                ? new JSONArray() : plane.optJSONArray("sources"));
        item.put("distance_km", distanceKm);
        boolean onGround = AircraftData.isOnGround(plane);
        item.put("on_ground", onGround);
        item.put("altitude_ft", Double.isNaN(altitudeFt) ? JSONObject.NULL : altitudeFt);
        item.put("barometric_altitude_ft", onGround
                ? 0d : altitudeValueOrNull(plane.opt("alt_baro")));
        item.put("geometric_altitude_ft", altitudeValueOrNull(plane.opt("alt_geom")));
        item.put("geometric_msl_altitude_ft",
                geometricMslAltitudeOrNull(plane, latitude, longitude));
        item.put("qnh_hpa", finiteValueOrNull(plane.optDouble("nav_qnh", Double.NaN)));
        item.put("speed_knots", plane.optDouble("gs", 0));
        item.put("true_airspeed_knots",
                finiteValueOrNull(plane.optDouble("tas", Double.NaN)));
        item.put("indicated_airspeed_knots",
                finiteValueOrNull(plane.optDouble("ias", Double.NaN)));
        item.put("mach", finiteValueOrNull(plane.optDouble("mach", Double.NaN)));
        item.put("track", plane.optDouble("track", 0));
        item.put("true_heading",
                finiteValueOrNull(plane.optDouble("true_heading", Double.NaN)));
        item.put("magnetic_heading",
                finiteValueOrNull(plane.optDouble("mag_heading", Double.NaN)));
        item.put("magnetic_declination", finiteValueOrNull(plane.optDouble(
                "mag_declination", plane.optDouble("mag_decl", Double.NaN))));
        item.put("track_rate",
                finiteValueOrNull(plane.optDouble("track_rate", Double.NaN)));
        item.put("roll", finiteValueOrNull(plane.optDouble("roll", Double.NaN)));
        item.put("vertical_rate", plane.optDouble("geom_rate",
                plane.optDouble("baro_rate", 0)));
        item.put("barometric_rate", finiteValueOrNull(
                plane.optDouble("baro_rate", Double.NaN)));
        item.put("geometric_rate", finiteValueOrNull(
                plane.optDouble("geom_rate", Double.NaN)));
        item.put("selected_altitude_ft", finiteValueOrNull(plane.optDouble(
                "nav_altitude_mcp", plane.optDouble("nav_altitude_fms", Double.NaN))));
        item.put("selected_heading",
                finiteValueOrNull(plane.optDouble("nav_heading", Double.NaN)));
        item.put("nav_modes", plane.optJSONArray("nav_modes") == null
                ? plane.optString("nav_modes", "") : plane.optJSONArray("nav_modes"));
        item.put("wind_speed_knots",
                finiteValueOrNull(plane.optDouble("ws", Double.NaN)));
        item.put("wind_direction",
                finiteValueOrNull(plane.optDouble("wd", Double.NaN)));
        item.put("outside_air_temp_c",
                finiteValueOrNull(plane.optDouble("oat", Double.NaN)));
        item.put("total_air_temp_c",
                finiteValueOrNull(plane.optDouble("tat", Double.NaN)));
        item.put("rssi", finiteValueOrNull(plane.optDouble("rssi", Double.NaN)));
        item.put("messages", plane.optLong("messages", 0L));
        item.put("message_rate", finiteValueOrNull(plane.optDouble("msg_rate",
                plane.optDouble("message_rate", Double.NaN))));
        item.put("receivers", plane.optInt("receiver_count",
                plane.optInt("receivers", 0)));
        item.put("adsb_version", plane.has("version")
                ? plane.optInt("version", -1) : JSONObject.NULL);
        item.put("nac_p", plane.has("nac_p")
                ? plane.optInt("nac_p", -1) : JSONObject.NULL);
        item.put("nac_v", plane.has("nac_v")
                ? plane.optInt("nac_v", -1) : JSONObject.NULL);
        item.put("nic_baro", plane.has("nic_baro")
                ? plane.optInt("nic_baro", -1) : JSONObject.NULL);
        item.put("sil", plane.has("sil")
                ? plane.optInt("sil", -1) : JSONObject.NULL);
        item.put("rc_m", finiteValueOrNull(plane.optDouble("rc", Double.NaN)));
        item.put("squawk", plane.optString("squawk", ""));
        item.put("lat", latitude);
        item.put("lon", longitude);
        item.put("seen", plane.optDouble("seen", 0));
        item.put("seen_position", plane.optDouble("seen_pos",
                plane.optDouble("seen", 0)));
        item.put("position_epoch", Math.round(System.currentTimeMillis() / 1000d
                - plane.optDouble("seen_pos", plane.optDouble("seen", 0))));
        item.put("emergency", plane.optString("emergency", "none"));
        item.put("alert", plane.has("alert") ? plane.optInt("alert", 0) : JSONObject.NULL);
        item.put("spi", plane.has("spi") ? plane.optInt("spi", 0) : JSONObject.NULL);
        return item;
    }

    private Object altitudeValueOrNull(Object value) {
        double altitude = altitudeValueFeet(value);
        return Double.isNaN(altitude) ? JSONObject.NULL : altitude;
    }

    private Object finiteValueOrNull(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? JSONObject.NULL : value;
    }

    private Object geometricMslAltitudeOrNull(JSONObject plane, double latitude,
                                              double longitude) {
        double geometricFeet = altitudeValueFeet(plane.opt("alt_geom"));
        if (Double.isNaN(geometricFeet)
                || !AppPreferences.get(this).getBoolean(
                MapPreferences.EGM_CONVERSION, false)
                || Build.VERSION.SDK_INT < 34) return JSONObject.NULL;
        String cell = Math.round(latitude * 10d) + ":" + Math.round(longitude * 10d);
        Double cachedOffset = geoidOffsetFeet.get(cell);
        if (cachedOffset != null) return geometricFeet + cachedOffset;
        try {
            double mslFeet = Api34AltitudeConverter.toMslFeet(
                    this, latitude, longitude, geometricFeet);
            if (geoidOffsetFeet.size() >= 512) geoidOffsetFeet.clear();
            geoidOffsetFeet.put(cell, mslFeet - geometricFeet);
            return mslFeet;
        } catch (Exception ignored) {
            return JSONObject.NULL;
        }
    }

    @android.annotation.TargetApi(34)
    private static final class Api34AltitudeConverter {
        private static final android.location.altitude.AltitudeConverter CONVERTER =
                new android.location.altitude.AltitudeConverter();

        static synchronized double toMslFeet(android.content.Context context,
                                             double latitude, double longitude,
                                             double geometricFeet) throws Exception {
            Location location = new Location("aircraft");
            location.setLatitude(latitude);
            location.setLongitude(longitude);
            location.setAltitude(geometricFeet * 0.3048d);
            CONVERTER.addMslAltitudeToLocation(context, location);
            if (!location.hasMslAltitude()) throw new IllegalStateException("No MSL altitude");
            return location.getMslAltitudeMeters() / 0.3048d;
        }
    }

    private void loadSessionHistory() {
        String saved = AppPreferences.get(this).getString(
                AppPreferences.KEY_AIRCRAFT_HISTORY_JSON, "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String hex = item.optString("hex", "");
                if (!hex.isEmpty()) sessionHistory.put(hex, item);
            }
        } catch (Exception ignored) { }
    }

    private void updateSessionRecord(JSONObject current, long scanTime) {
        String hex = current.optString("hex", "");
        if (hex.isEmpty()) return;
        JSONObject previous = sessionHistory.get(hex);
        long firstSeen = previous == null ? scanTime
                : previous.optLong("first_seen_ms", scanTime);
        try {
            if (previous != null) {
                preserveText(current, previous, "callsign");
                preserveText(current, previous, "registration");
                preserveText(current, previous, "type");
            }
            current.put("first_seen_ms", firstSeen);
            current.put("last_seen_ms", scanTime);
            current.put("in_range", true);
            current.remove("out_of_range_since_ms");
            sessionHistory.put(hex, current);
        } catch (Exception ignored) { }
    }

    private void preserveText(JSONObject current, JSONObject previous, String key)
            throws Exception {
        if (current.optString(key, "").isEmpty()) {
            current.put(key, previous.optString(key, ""));
        }
    }

    private void markMissingAircraftOutOfRange(Set<String> currentlyInside) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, JSONObject> entry : sessionHistory.entrySet()) {
            if (currentlyInside.contains(entry.getKey())) continue;
            JSONObject item = entry.getValue();
            if (!item.optBoolean("in_range", false)) continue;
            try {
                item.put("in_range", false);
                item.put("out_of_range_since_ms", now);
            } catch (Exception ignored) { }
        }
    }

    private void publishSessionHistory() {
        List<JSONObject> records = new ArrayList<>(sessionHistory.values());
        Collections.sort(records, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject left, JSONObject right) {
                return Long.compare(right.optLong("last_seen_ms", 0L),
                        left.optLong("last_seen_ms", 0L));
            }
        });
        JSONArray array = new JSONArray();
        for (JSONObject record : records) array.put(record);
        AppPreferences.get(this).edit()
                .putString(AppPreferences.KEY_AIRCRAFT_HISTORY_JSON, array.toString())
                .apply();
    }

    private void publishTelemetry(String connection, int count, JSONArray aircraft,
                                  String nearestCallsign, double nearestDistanceKm,
                                  double nearestAltitudeFt) {
        AppPreferences.get(this).edit()
                .putString(AppPreferences.KEY_CONNECTION, connection)
                .putInt(AppPreferences.KEY_LIVE_COUNT, count)
                .putLong(AppPreferences.KEY_LAST_SCAN, System.currentTimeMillis())
                .putString(AppPreferences.KEY_AIRCRAFT_JSON, aircraft.toString())
                .putString(AppPreferences.KEY_NEAREST_CALLSIGN, nearestCallsign)
                .putLong(AppPreferences.KEY_NEAREST_DISTANCE_KM,
                        Double.doubleToRawLongBits(nearestDistanceKm))
                .putLong(AppPreferences.KEY_NEAREST_ALTITUDE_FT,
                        Double.doubleToRawLongBits(nearestAltitudeFt))
                .apply();
    }

    private Bitmap radarBitmap(boolean alert) {
        int size = 128;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(MARColors.INK);
        canvas.drawCircle(64, 64, 62, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(alert ? MARColors.ORANGE : MARColors.GREEN);
        canvas.drawCircle(64, 64, 50, paint);
        canvas.drawCircle(64, 64, 30, paint);
        canvas.drawLine(64, 14, 64, 114, paint);
        canvas.drawLine(14, 64, 114, 64, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(89, 39, 7, paint);
        return bitmap;
    }

    private String nowTime() {
        return new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date());
    }
}
