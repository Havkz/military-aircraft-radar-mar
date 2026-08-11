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
    private static final long ADSB_LOL_MILITARY_REFRESH_MS = 60_000L;
    private static final long ADSB_LOL_BASE_REFRESH_MS = 1_000L;
    private static final long AIRPLANES_REFRESH_MS = 180_000L;
    private static final long AIRPLANES_BUSINESS_REFRESH_MS = 1_200L;
    private static final long ADSBX_REFRESH_MS = 30_000L;
    private static final long MAP_AIRCRAFT_CACHE_MS = 2 * 60_000L;
    private static final int MAX_MAP_CACHE_AIRCRAFT = 10_000;
    private static final double MAP_MAX_POSITION_AGE_SECONDS = 210d;
    private static final int EXPANDED_MAP_RADIUS_NM = 2_500;
    private static final int MAP_SUPPLEMENTAL_RADIUS_NM = 45;
    private static final long MAP_SUPPLEMENTAL_DWELL_MS = 12_000L;
    private static final double NAUTICAL_MILE_KM = 1.852d;
    private static final long WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L;
    private static final long WAKE_LOCK_RENEW_MS = 9 * 60_000L;
    private static final int STATUS_NOTIFICATION_ID = 1001;
    private static final String MILITARY_ENDPOINT = "https://api.adsb.lol/v2/mil";
    private static volatile long mapAircraftPayloadRevision;
    private static volatile MapAircraftPayload latestMapAircraftPayload =
            new MapAircraftPayload(0L, new String[]{"[]"}, 0);
    private static volatile double latestOwnLatitude = Double.NaN;
    private static volatile double latestOwnLongitude = Double.NaN;
    private static volatile boolean mapVisible;
    private static volatile boolean mapLoading;
    private static volatile double mapCenterLatitude = Double.NaN;
    private static volatile double mapCenterLongitude = Double.NaN;
    private static volatile int mapRadiusNm = 25;
    private static volatile String isolatedMapHex = "";
    private static volatile long mapViewportChangedAtMs = System.currentTimeMillis();
    private static volatile long mapViewportGeneration;
    private static final Map<String, JSONObject> mapAircraftCache = new LinkedHashMap<>();
    private static final Map<String, Long> mapAircraftCacheTimes = new HashMap<>();

    private static final long NOTIFICATION_ENTRY_GRACE_MS = 2 * 60_000L;
    private final Map<String, Long> notifiedInsideLastSeen = new HashMap<>();
    private final Map<String, Long> activeGlobalSquawks = new HashMap<>();
    private final Map<String, JSONObject> sessionHistory = new LinkedHashMap<>();
    private final Map<String, Double> geoidOffsetFeet = new HashMap<>();
    private final Set<Integer> aircraftNotificationIds = Collections.newSetFromMap(
            new ConcurrentHashMap<Integer, Boolean>());
    private HandlerThread workerThread;
    private Handler worker;
    private ExecutorService networkPool;
    private ExecutorService immediateMapExecutor;
    private volatile Future<?> immediateMapFuture;
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
    static MapAircraftPayload latestMapAircraftPayload() {
        return latestMapAircraftPayload;
    }

    static final class MapAircraftPayload {
        final long revision;
        final String[] chunks;
        final int aircraftCount;

        MapAircraftPayload(long revision, String[] chunks, int aircraftCount) {
            this.revision = revision;
            this.chunks = chunks;
            this.aircraftCount = aircraftCount;
        }
    }
    static double latestOwnLatitude() { return latestOwnLatitude; }
    static double latestOwnLongitude() { return latestOwnLongitude; }
    static void setMapVisible(boolean visible) { mapVisible = visible; }
    static boolean isMapLoading() { return mapVisible && mapLoading; }
    static synchronized boolean setMapViewport(
            double latitude, double longitude, int radiusNm) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return false;
        int boundedRadius = boundedViewportRadiusNm(radiusNm);
        boolean changed = Double.isNaN(mapCenterLatitude)
                || DistanceCalculator.kilometers(mapCenterLatitude, mapCenterLongitude,
                latitude, longitude) > Math.max(.5d, boundedRadius * NAUTICAL_MILE_KM * .03d)
                || Math.abs(mapRadiusNm - boundedRadius) >= Math.max(1, boundedRadius / 20);
        if (changed) {
            mapCenterLatitude = latitude;
            mapCenterLongitude = longitude;
            mapRadiusNm = boundedRadius;
            mapViewportChangedAtMs = System.currentTimeMillis();
            mapViewportGeneration++;
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
        mapViewportGeneration++;
        return true;
    }

    private static synchronized long mapViewportGeneration() {
        return mapViewportGeneration;
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
        immediateMapExecutor = Executors.newFixedThreadPool(2);
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
                aircraftNotificationIds.remove(hex.hashCode());
                getSystemService(NotificationManager.class).cancel(hex.hashCode());
            }
        }
        boolean viewportChanged = intent != null
                && ACTION_VIEWPORT_CHANGED.equals(intent.getAction());
        if (viewportChanged && pollingScheduled && immediateMapExecutor != null) {
            requestImmediateMapAircraft();
        }
        if (intent != null && (ACTION_RADIUS_CHANGED.equals(intent.getAction())
                || ACTION_SOURCES_CHANGED.equals(intent.getAction()))
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

    private void requestImmediateMapAircraft() {
        final double latitude;
        final double longitude;
        final int radiusNm;
        final String isolatedHex;
        final long generation;
        synchronized (MonitorService.class) {
            if (!mapVisible || Double.isNaN(mapCenterLatitude)
                    || Double.isNaN(mapCenterLongitude)) return;
            latitude = mapCenterLatitude;
            longitude = mapCenterLongitude;
            radiusNm = mapRadiusNm;
            isolatedHex = isolatedMapHex;
            generation = mapViewportGeneration;
            mapLoading = true;
        }
        Future<?> previous = immediateMapFuture;
        if (previous != null && !previous.isDone()) previous.cancel(true);
        immediateMapFuture = immediateMapExecutor.submit(() -> {
            try {
                JSONArray response = fetchAircraft(mapAircraftEndpoint(
                        latitude, longitude, radiusNm, isolatedHex), null, "adsb.lol");
                if (generation != mapViewportGeneration()) return;
                long receivedAt = System.currentTimeMillis();
                JSONArray compact = compactMapAircraft(
                        taggedCopy(response, "ADSB.lol", 0d),
                        latitude, longitude, radiusNm);
                synchronized (MonitorService.class) {
                    if (generation != mapViewportGeneration) return;
                    updateMapAircraftCache(compact, receivedAt);
                    publishMapAircraft(visibleMapAircraftCacheJson(receivedAt));
                }
            } catch (Exception ignored) {
                // The normal one-second monitor poll remains the fallback.
            } finally {
                if (generation == mapViewportGeneration()) mapLoading = false;
            }
        });
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
        if (immediateMapExecutor != null) immediateMapExecutor.shutdownNow();
        immediateMapFuture = null;
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
        pollGlobalSquawkAlerts(System.currentTimeMillis());
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
        long requestMapGeneration = mapViewportGeneration();

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
            int supplementalRadiusNm = Math.min(radiusNm, 250);
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
                        queryLatitude, queryLongitude, supplementalRadiusNm);
                airplanesFuture = networkPool.submit(
                        () -> fetchAircraft(airplanesEndpoint, null, "airplanes.live"));
            }
            String adsbxKey = ProviderCredentials.adsbExchangeKey(this);
            if (querySupplemental && !adsbxKey.isEmpty()
                    && now - lastAdsbExchangeFetchMs >= ADSBX_REFRESH_MS) {
                String adsbxEndpoint = String.format(Locale.US,
                        "https://gateway.adsbexchange.com/api/aircraft/v2/lat/%.5f/lon/%.5f/dist/%d",
                        queryLatitude, queryLongitude, supplementalRadiusNm);
                adsbxFuture = networkPool.submit(
                        () -> fetchAircraft(adsbxEndpoint, adsbxKey, "adsbexchange"));
            }

            boolean receivedLiveFeed = false;
            JSONArray regional = awaitOptional(regionalFuture);
            if (regional != null) {
                cachedAdsbLolRegional = regional;
                lastAdsbLolRegionalFetchMs = now;
                receivedLiveFeed = true;
                if (expandedMap && requestMapGeneration == mapViewportGeneration()) {
                    long receivedAt = System.currentTimeMillis();
                    JSONArray immediateMapAircraft = compactMapAircraft(
                            taggedCopy(regional, "ADSB.lol", 0d),
                            queryLatitude, queryLongitude, radiusNm);
                    updateMapAircraftCache(immediateMapAircraft, receivedAt);
                    publishMapAircraft(visibleMapAircraftCacheJson(receivedAt));
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
                    boolean builtInTarget = isAlertTarget(plane);
                    if (!builtInTarget) continue;
                    alertTargetCount++;
                    currentlyInside.add(hex);
                    JSONObject compact = compactAircraft(plane, hex, callsign,
                            distanceKm, altitudeFt, aircraftLat, aircraftLon);
                    if (MilitaryClassifier.isMilitary(plane)
                            && AircraftData.isRotorcraft(plane)) {
                        compact.put("alert_reason", MapL10n.t(this, "military") + " · "
                                + MapL10n.t(this, "rotorcraft"));
                    } else if (AircraftData.isRotorcraft(plane)) {
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
                        boolean newEntry = markNotificationInside(
                                notifiedInsideLastSeen, hex, scanTime);
                        if (newEntry) showAircraftNotification(compact);
                    }
                }
            }

            markMissingAircraftOutOfRange(currentlyInside);
            pruneNotificationEntryCycles(scanTime);
            publishSessionHistory();
            if (!expandedMap || requestMapGeneration == mapViewportGeneration()) {
                updateMapAircraftCache(allAircraft, scanTime);
                publishMapAircraft(visibleMapAircraftCacheJson(scanTime));
            }
            publishTelemetry("connected", alertTargetCount, liveAircraft, nearestCallsign,
                    nearestDistanceKm, nearestAltitudeFt);
            updateStatus("LIVE // " + nowTime(), "", alertTargetCount);
        } catch (Exception e) {
            AppPreferences.get(this).edit().putString(AppPreferences.KEY_CONNECTION, "error").apply();
            updateStatus("SIGNAL LOST", "", 0);
        } finally {
            if (!expandedMap || requestMapGeneration == mapViewportGeneration()) {
                mapLoading = false;
            }
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
                JSONObject cached = mapAircraftCache.get(hex);
                mapAircraftCache.remove(hex);
                mapAircraftCache.put(hex, mergeMapAircraft(cached, aircraft));
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

    private static JSONObject mergeMapAircraft(JSONObject cached, JSONObject fresh) {
        if (cached == null) return fresh;
        try {
            JSONObject merged = new JSONObject(cached.toString());
            java.util.Iterator<String> keys = fresh.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object incoming = fresh.opt(key);
                if ("sources".equals(key)) {
                    merged.put(key, mergedSources(
                            merged.optJSONArray(key), fresh.optJSONArray(key)));
                } else if ("db_flags".equals(key)) {
                    merged.put(key, merged.optInt(key, 0) | fresh.optInt(key, 0));
                } else if ("military".equals(key) || "rotorcraft".equals(key)) {
                    merged.put(key, merged.optBoolean(key, false)
                            || fresh.optBoolean(key, false));
                } else if (!mapIdentityField(key)
                        || AircraftData.meaningful(incoming)
                        || !AircraftData.meaningful(merged.opt(key))) {
                    merged.put(key, incoming);
                }
            }
            return merged;
        } catch (Exception ignored) {
            return fresh;
        }
    }

    private static boolean mapIdentityField(String key) {
        return "callsign".equals(key) || "display_name".equals(key)
                || "registration".equals(key) || "country".equals(key)
                || "operator".equals(key) || "type".equals(key)
                || "description".equals(key) || "category".equals(key);
    }

    private static JSONArray mergedSources(JSONArray first, JSONArray second) {
        JSONArray result = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (JSONArray sources : new JSONArray[]{first, second}) {
            if (sources == null) continue;
            for (int i = 0; i < sources.length(); i++) {
                String source = sources.optString(i, "").trim();
                if (!source.isEmpty() && seen.add(source)) result.put(source);
            }
        }
        return result;
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
            result.put(entry.getValue());
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
            visible.put(aircraft);
        }
        for (String hex : expired) {
            mapAircraftCache.remove(hex);
            mapAircraftCacheTimes.remove(hex);
        }
        return visible;
    }

    private static synchronized void publishMapAircraft(JSONArray aircraft) {
        final int chunkSize = 300;
        int count = aircraft == null ? 0 : aircraft.length();
        if (count == 0) {
            latestMapAircraftPayload = new MapAircraftPayload(
                    ++mapAircraftPayloadRevision, new String[]{"[]"}, 0);
            return;
        }
        int chunkCount = (count + chunkSize - 1) / chunkSize;
        String[] chunks = new String[chunkCount];
        for (int chunk = 0; chunk < chunkCount; chunk++) {
            int start = chunk * chunkSize;
            int end = Math.min(count, start + chunkSize);
            StringBuilder json = new StringBuilder((end - start) * 320);
            json.append('[');
            for (int i = start; i < end; i++) {
                if (i > start) json.append(',');
                Object item = aircraft.opt(i);
                json.append(item == null ? "null" : item.toString());
            }
            json.append(']');
            chunks[chunk] = json.toString();
        }
        latestMapAircraftPayload = new MapAircraftPayload(
                ++mapAircraftPayloadRevision, chunks, count);
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
        publishMapAircraft(new JSONArray());
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
        long base = airplanesBaseRefreshMs(businessRate);
        return airplanesBackoff.delayMs(
                base, System.currentTimeMillis());
    }

    private void pollGlobalSquawkAlerts(long now) {
        String configuredSquawk = CustomAlertRules.querySquawk(this);
        if (configuredSquawk.isEmpty() || networkPool == null) return;
        int intervalMinutes = CustomAlertRules.intervalMinutes(this);
        long requestedInterval = CustomAlertRules.requestedIntervalMs(intervalMinutes);
        boolean businessRate = AppPreferences.get(this).getBoolean(
                AppPreferences.KEY_AIRPLANES_BUSINESS_RATE, false);
        long airplanesInterval = CustomAlertRules.airplanesIntervalMs(
                intervalMinutes, businessRate);
        android.content.SharedPreferences preferences = AppPreferences.get(this);
        long adsbLolLast = preferences.getLong(
                AppPreferences.KEY_SQUAWK_ADSB_LOL_LAST_ATTEMPT_MS, 0L);
        if (!providerDue(adsbLolLast, requestedInterval, now)) return;

        preferences.edit().putLong(
                AppPreferences.KEY_SQUAWK_ADSB_LOL_LAST_ATTEMPT_MS, now).apply();
        JSONArray received = awaitOptional(networkPool.submit(() -> fetchAircraft(
                globalSquawkEndpoint("adsb.lol", configuredSquawk),
                null, "adsb.lol")));

        // ADSB.lol provides the documented global exact-squawk endpoint and is
        // authoritative here. Only try another source when that request failed;
        // an empty successful response correctly means that no aircraft matched.
        String adsbxKey = ProviderCredentials.adsbExchangeKey(this);
        long adsbxLast = preferences.getLong(
                AppPreferences.KEY_SQUAWK_ADSBX_LAST_ATTEMPT_MS, 0L);
        if (received == null && !adsbxKey.isEmpty()
                && providerDue(adsbxLast, requestedInterval, now)) {
            preferences.edit().putLong(
                    AppPreferences.KEY_SQUAWK_ADSBX_LAST_ATTEMPT_MS, now).apply();
            received = awaitOptional(networkPool.submit(() -> fetchAircraft(
                    globalSquawkEndpoint("adsbexchange", configuredSquawk),
                    adsbxKey, "adsbexchange")));
        }
        long airplanesLast = preferences.getLong(
                AppPreferences.KEY_SQUAWK_AIRPLANES_LAST_ATTEMPT_MS, 0L);
        if (received == null && providerDue(airplanesLast, airplanesInterval, now)) {
            preferences.edit().putLong(
                    AppPreferences.KEY_SQUAWK_AIRPLANES_LAST_ATTEMPT_MS, now).apply();
            received = awaitOptional(networkPool.submit(() -> fetchAircraft(
                    globalSquawkEndpoint("airplanes.live", configuredSquawk),
                    null, "airplanes.live")));
        }
        if (received != null) {
            processGlobalSquawkAircraft(received, configuredSquawk, now,
                    requestedInterval);
        }
    }

    static boolean providerDue(long lastAttempt, long interval, long now) {
        return lastAttempt <= 0L || now - lastAttempt >= interval;
    }

    static String globalSquawkEndpoint(String provider, String squawks) {
        if ("airplanes.live".equals(provider)) {
            return "https://api.airplanes.live/v2/squawk/" + squawks;
        }
        if ("adsbexchange".equals(provider)) {
            return "https://gateway.adsbexchange.com/api/aircraft/v2/sqk/" + squawks;
        }
        return "https://api.adsb.lol/v2/sqk/" + squawks;
    }

    static String normalizeApiSquawk(Object rawValue) {
        if (rawValue instanceof Number) {
            double decimal = ((Number) rawValue).doubleValue();
            int integer = (int) decimal;
            if (decimal != integer || integer < 0 || integer > 7777) return "";
            String value = String.format(Locale.US, "%04d", integer);
            return CustomAlertRules.validSquawk(value) ? value : "";
        }
        String value = rawValue instanceof String ? ((String) rawValue).trim() : "";
        return CustomAlertRules.validSquawk(value) ? value : "";
    }

    static boolean exactSquawkMatch(JSONObject aircraft, String configuredSquawk) {
        return aircraft != null && CustomAlertRules.validSquawk(configuredSquawk)
                && configuredSquawk.equals(normalizeApiSquawk(aircraft.opt("squawk")));
    }

    static JSONArray exactSquawkAircraft(JSONArray aircraft, String configuredSquawk) {
        JSONArray matches = new JSONArray();
        if (aircraft == null) return matches;
        for (int i = 0; i < aircraft.length(); i++) {
            JSONObject item = aircraft.optJSONObject(i);
            if (exactSquawkMatch(item, configuredSquawk)) matches.put(item);
        }
        return matches;
    }

    private void processGlobalSquawkAircraft(JSONArray aircraft, String selectedSquawk,
                                             long now, long requestedInterval) {
        long expiry = Math.max(2 * requestedInterval, 2 * 60_000L);
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : activeGlobalSquawks.entrySet()) {
            if (now - entry.getValue() >= expiry) expired.add(entry.getKey());
        }
        for (String key : expired) activeGlobalSquawks.remove(key);
        JSONArray mapItems = new JSONArray();
        JSONArray matchingAircraft = exactSquawkAircraft(aircraft, selectedSquawk);
        for (int i = 0; i < matchingAircraft.length(); i++) {
            JSONObject plane = matchingAircraft.optJSONObject(i);
            String squawk = normalizeApiSquawk(plane.opt("squawk"));
            String hex = plane.optString("hex", "").replace("~", "")
                    .trim().toLowerCase(Locale.US);
            if (hex.isEmpty()) continue;
            String eventKey = squawk + ':' + hex;
            boolean newEvent = !activeGlobalSquawks.containsKey(eventKey);
            activeGlobalSquawks.put(eventKey, now);
            double[] position = AircraftData.recentPosition(
                    plane, MAP_MAX_POSITION_AGE_SECONDS);
            double latitude = position == null ? Double.NaN : position[0];
            double longitude = position == null ? Double.NaN : position[1];
            if (position != null) {
                try {
                    mapItems.put(compactAircraft(plane, hex, AircraftData.callsign(plane),
                            Double.NaN, altitudeFeet(plane.opt("alt_geom"),
                                    plane.opt("alt_baro")), latitude, longitude));
                } catch (Exception ignored) { }
            }
            if (newEvent) showGlobalSquawkNotification(
                    plane, hex, squawk, latitude, longitude);
        }
        if (mapItems.length() > 0) updateMapAircraftCache(mapItems, now);
    }

    private void showGlobalSquawkNotification(JSONObject aircraft, String hex,
                                               String squawk, double latitude,
                                               double longitude) {
        if (!running || !AppPreferences.get(this)
                .getBoolean(AppPreferences.KEY_RUNNING, false)) return;
        String callsign = AircraftData.displayName(aircraft);
        String details = globalSquawkNotificationDetails(aircraft);
        Intent openMap = new Intent(this, MainActivity.class)
                .setData(android.net.Uri.parse("mar://aircraft/"
                        + android.net.Uri.encode(hex)))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_map", true).putExtra("callsign", callsign)
                .putExtra("hex", hex).putExtra("lat", latitude).putExtra("lon", longitude);
        int notificationId = ("global-squawk:" + squawk + ':' + hex).hashCode();
        PendingIntent tracker = PendingIntent.getActivity(this,
                notificationId ^ 0x5351574b, openMap,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_radar)
                .setLargeIcon(radarBitmap(true))
                .setContentTitle("Aircraft squawked " + squawk)
                .setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details))
                .setColor(MARColors.RED)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tracker)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        aircraftNotificationIds.add(notificationId);
        getSystemService(NotificationManager.class).notify(notificationId, notification);
    }

    static String globalSquawkNotificationDetails(JSONObject aircraft) {
        String callsign = AircraftData.displayName(aircraft);
        String operator = aircraft.optString("ownOp",
                aircraft.optString("operator", "")).trim();
        if (operator.isEmpty()) operator = aircraft.optString("r",
                aircraft.optString("registration", "")).trim();
        if (operator.isEmpty() || operator.equalsIgnoreCase(callsign)) return callsign;
        return callsign + "  •  " + operator;
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

    private void showAircraftNotification(JSONObject aircraft) {
        if (!running || !AppPreferences.get(this)
                .getBoolean(AppPreferences.KEY_RUNNING, false)) return;
        String callsign = AircraftData.normalizeCallsign(aircraft.optString("callsign", ""));
        String displayName = callsign.isEmpty() ? "NO CALLSIGN" : callsign;
        String hex = aircraft.optString("hex", "");
        if (hex.isEmpty()) return;
        double aircraftLat = aircraft.optDouble("lat", Double.NaN);
        double aircraftLon = aircraft.optDouble("lon", Double.NaN);
        String details = notificationDetails(aircraft);
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
                .setColor(MARColors.ORANGE)
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

    static String notificationDetails(JSONObject aircraft) {
        List<String> details = new ArrayList<>();
        String reason = aircraft.optString("alert_reason", "").trim();
        if (!reason.isEmpty()) details.add(reason);
        String operator = aircraft.optString("operator", "").trim();
        String registration = aircraft.optString("registration", "").trim();
        String type = aircraft.optString("type", "").trim();
        String second = !operator.isEmpty() ? operator
                : !registration.isEmpty() ? registration : type;
        if (!second.isEmpty() && !second.equalsIgnoreCase(reason)) details.add(second);
        StringBuilder joined = new StringBuilder();
        for (String detail : details) {
            if (joined.length() > 0) joined.append("  •  ");
            joined.append(detail);
        }
        return joined.toString();
    }

    static boolean markNotificationInside(Map<String, Long> entries,
                                          String hex, long now) {
        boolean newEntry = !entries.containsKey(hex);
        entries.put(hex, now);
        return newEntry;
    }

    static List<String> expiredNotificationEntries(Map<String, Long> entries, long now) {
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : entries.entrySet()) {
            if (now - entry.getValue() >= NOTIFICATION_ENTRY_GRACE_MS) {
                expired.add(entry.getKey());
            }
        }
        return expired;
    }

    private void pruneNotificationEntryCycles(long now) {
        List<String> expired = expiredNotificationEntries(notifiedInsideLastSeen, now);
        NotificationManager manager = getSystemService(NotificationManager.class);
        for (String hex : expired) {
            notifiedInsideLastSeen.remove(hex);
            int notificationId = hex.hashCode();
            aircraftNotificationIds.remove(notificationId);
            manager.cancel(notificationId);
        }
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
        item.put("registration", firstAircraftText(
                plane, "r", "registration", "reg"));
        item.put("country", IcaoCountry.providerOrHex(plane.optString("country",
                plane.optString("country_name", "")), hex));
        item.put("operator", firstAircraftText(
                plane, "ownOp", "operator", "operator_name"));
        item.put("type", firstAircraftText(
                plane, "t", "typeCode", "icao_type", "aircraft_type"));
        item.put("data_source", plane.optString("type", ""));
        item.put("db_flags", plane.optInt("dbFlags", 0));
        item.put("description", bestAircraftDescription(plane));
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

    private static String firstAircraftText(JSONObject aircraft, String... keys) {
        for (String key : keys) {
            Object value = aircraft.opt(key);
            if (AircraftData.meaningful(value)) return String.valueOf(value).trim();
        }
        return "";
    }

    private static String bestAircraftDescription(JSONObject aircraft) {
        String best = "";
        for (String key : new String[]{"desc", "typeDescription", "description",
                "model", "model_name"}) {
            Object value = aircraft.opt(key);
            if (!AircraftData.meaningful(value)) continue;
            String text = String.valueOf(value).trim();
            if (text.length() > best.length()) best = text;
        }
        return best;
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
