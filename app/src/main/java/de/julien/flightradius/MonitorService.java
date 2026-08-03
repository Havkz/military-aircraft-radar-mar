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
    private static final long DISMISSED_RESEND_DELAY_MS = 5 * 60 * 1000L;
    private static final long ADSB_LOL_MILITARY_REFRESH_MS = 60_000L;
    private static final long ADSB_LOL_BASE_REFRESH_MS = 1_000L;
    private static final long AIRPLANES_REFRESH_MS = 180_000L;
    private static final long ADSBX_REFRESH_MS = 30_000L;
    private static final double MAP_MAX_POSITION_AGE_SECONDS = 210d;
    private static final int STATUS_NOTIFICATION_ID = 1001;
    private static final String MILITARY_ENDPOINT = "https://api.adsb.lol/v2/mil";
    private static volatile String latestAllAircraftJson = "[]";
    private static volatile double latestOwnLatitude = Double.NaN;
    private static volatile double latestOwnLongitude = Double.NaN;

    private final Map<String, JSONObject> lastKnownAlerts = new HashMap<>();
    private final Map<String, JSONObject> sessionHistory = new LinkedHashMap<>();
    private final Map<String, Long> notificationSuppressedUntil = new ConcurrentHashMap<>();
    private final Set<Integer> aircraftNotificationIds = Collections.newSetFromMap(
            new ConcurrentHashMap<Integer, Boolean>());
    private HandlerThread workerThread;
    private Handler worker;
    private ExecutorService networkPool;
    private LocationManager locationManager;
    private volatile Location latestLocation;
    private static volatile boolean running;
    private boolean pollingScheduled;
    private boolean explicitlyStopped;
    private JSONArray cachedAdsbLolMilitary = new JSONArray();
    private JSONArray cachedAdsbLolRegional = new JSONArray();
    private JSONArray cachedAirplanes = new JSONArray();
    private JSONArray cachedAdsbExchange = new JSONArray();
    private long lastAdsbLolMilitaryFetchMs;
    private long lastAdsbLolRegionalFetchMs;
    private long lastAirplanesFetchMs;
    private long lastAdsbExchangeFetchMs;
    private long adsbLolBackoffMs;
    private long adsbLolBackoffUntilMs;
    private long airplanesBackoffMs;
    private long airplanesBackoffUntilMs;

    static boolean isRunning() { return running; }
    static String latestAllAircraftJson() { return latestAllAircraftJson; }
    static double latestOwnLatitude() { return latestOwnLatitude; }
    static double latestOwnLongitude() { return latestOwnLongitude; }

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            PowerManager.WakeLock wakeLock = null;
            try {
                PowerManager power = getSystemService(PowerManager.class);
                if (power != null) {
                    wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "MAR:live-poll");
                    wakeLock.acquire(45_000L);
                }
                pollAircraft();
            }
            finally {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                if (worker != null) worker.postDelayed(this, nextAdsbLolDelayMs());
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        startForeground(STATUS_NOTIFICATION_ID,
                statusNotification("INITIALIZING",
                        L10n.t(this, "waiting_location"), 0));

        workerThread = new HandlerThread("military-live-monitor");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        networkPool = Executors.newFixedThreadPool(4);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
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
            latestAllAircraftJson = "[]";
        }
        if (locationManager != null) locationManager.removeUpdates(this);
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (workerThread != null) workerThread.quitSafely();
        if (networkPool != null) networkPool.shutdownNow();
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

        int radiusNm = Math.max(1, Math.min(250, (int) Math.ceil(radiusKm / 1.852)));
        String localEndpoint = String.format(Locale.US,
                "https://api.adsb.lol/v2/lat/%.5f/lon/%.5f/dist/%d",
                own.getLatitude(), own.getLongitude(), radiusNm);

        try {
            long now = System.currentTimeMillis();
            Future<JSONArray> regionalFuture = networkPool.submit(
                    () -> fetchAircraft(localEndpoint, null, "adsb.lol"));
            Future<JSONArray> militaryFuture = null;
            Future<JSONArray> airplanesFuture = null;
            Future<JSONArray> adsbxFuture = null;
            if (now - lastAdsbLolMilitaryFetchMs >= ADSB_LOL_MILITARY_REFRESH_MS) {
                militaryFuture = networkPool.submit(
                        () -> fetchAircraft(MILITARY_ENDPOINT, null, "adsb.lol"));
            }
            long persistedAirplanesAttempt = AppPreferences.get(this).getLong(
                    AppPreferences.KEY_AIRPLANES_LAST_ATTEMPT_MS, 0L);
            if (now - Math.max(lastAirplanesFetchMs, persistedAirplanesAttempt)
                    >= nextAirplanesDelayMs()) {
                lastAirplanesFetchMs = now;
                AppPreferences.get(this).edit()
                        .putLong(AppPreferences.KEY_AIRPLANES_LAST_ATTEMPT_MS, now).apply();
                String airplanesEndpoint = String.format(Locale.US,
                        "https://api.airplanes.live/v2/point/%.5f/%.5f/%d",
                        own.getLatitude(), own.getLongitude(), radiusNm);
                airplanesFuture = networkPool.submit(
                        () -> fetchAircraft(airplanesEndpoint, null, "airplanes.live"));
            }
            String adsbxKey = ProviderCredentials.adsbExchangeKey(this);
            if (!adsbxKey.isEmpty()
                    && now - lastAdsbExchangeFetchMs >= ADSBX_REFRESH_MS) {
                String adsbxEndpoint = String.format(Locale.US,
                        "https://api.adsbexchange.com/v2/lat/%.5f/lon/%.5f/dist/%d/",
                        own.getLatitude(), own.getLongitude(), radiusNm);
                adsbxFuture = networkPool.submit(
                        () -> fetchAircraft(adsbxEndpoint, adsbxKey, "adsbexchange"));
            }

            boolean receivedLiveFeed = false;
            JSONArray regional = awaitOptional(regionalFuture);
            if (regional != null) {
                cachedAdsbLolRegional = regional;
                lastAdsbLolRegionalFetchMs = now;
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
                    && cachedAirplanes.length() == 0 && cachedAdsbExchange.length() == 0) {
                throw new IllegalStateException("No aircraft feed available");
            }

            JSONArray aircraft = AircraftData.mergeByHex(
                    taggedCopy(cachedAdsbLolRegional, "ADSB.lol",
                            ageSeconds(now, lastAdsbLolRegionalFetchMs)),
                    taggedCopy(cachedAdsbLolMilitary, "ADSB.lol military",
                            ageSeconds(now, lastAdsbLolMilitaryFetchMs)),
                    taggedCopy(cachedAirplanes, "Airplanes.live",
                            ageSeconds(now, lastAirplanesFetchMs)),
                    taggedCopy(cachedAdsbExchange, "ADS-B Exchange",
                            ageSeconds(now, lastAdsbExchangeFetchMs)));
            JSONArray liveAircraft = new JSONArray();
            JSONArray allAircraft = new JSONArray();
            Set<String> currentlyInside = new HashSet<>();
            long scanTime = System.currentTimeMillis();
            int militaryCount = 0;
            String nearestCallsign = "";
            double nearestDistanceKm = Double.NaN;
            double nearestAltitudeFt = Double.NaN;
            if (aircraft != null) {
                for (int i = 0; i < aircraft.length(); i++) {
                    JSONObject plane = aircraft.optJSONObject(i);
                    double[] mapPosition = AircraftData.recentPosition(
                            plane, MAP_MAX_POSITION_AGE_SECONDS);
                    if (mapPosition != null) {
                        double mapDistance = DistanceCalculator.kilometers(
                                own.getLatitude(), own.getLongitude(),
                                mapPosition[0], mapPosition[1]);
                        if (!Double.isNaN(mapDistance) && mapDistance <= radiusKm) {
                            allAircraft.put(compactAircraft(plane,
                                    plane.optString("hex", "unknown").replace("~", ""),
                                    plane.optString("flight", "").trim(), mapDistance,
                                    altitudeFeet(plane.opt("alt_geom"), plane.opt("alt_baro")),
                                    mapPosition[0], mapPosition[1]));
                        }
                    }
                    if (!MilitaryClassifier.isMilitary(plane)) continue;
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
                    String callsign = plane.optString("flight", "").trim();
                    double altitudeFt = altitudeFeet(
                            plane.opt("alt_geom"), plane.opt("alt_baro"));
                    militaryCount++;
                    currentlyInside.add(hex);
                    JSONObject compact = compactAircraft(plane, hex, callsign,
                            distanceKm, altitudeFt, aircraftLat, aircraftLon);
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
            latestAllAircraftJson = allAircraft.toString();
            publishTelemetry("connected", militaryCount, liveAircraft, nearestCallsign,
                    nearestDistanceKm, nearestAltitudeFt);
            updateStatus("LIVE // " + nowTime(), "", militaryCount);
        } catch (Exception e) {
            AppPreferences.get(this).edit().putString(AppPreferences.KEY_CONNECTION, "error").apply();
            updateStatus("SIGNAL LOST", "", 0);
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

    private synchronized void increaseRateLimitBackoff(String provider) {
        long now = System.currentTimeMillis();
        if ("adsb.lol".equals(provider)) {
            if (now >= adsbLolBackoffUntilMs) adsbLolBackoffMs = 0L;
            adsbLolBackoffMs += 500L;
            adsbLolBackoffUntilMs = now + 60_000L;
        } else if ("airplanes.live".equals(provider)) {
            if (now >= airplanesBackoffUntilMs) airplanesBackoffMs = 0L;
            airplanesBackoffMs += 500L;
            airplanesBackoffUntilMs = now + 60_000L;
        }
    }

    private synchronized long nextAdsbLolDelayMs() {
        if (System.currentTimeMillis() >= adsbLolBackoffUntilMs) {
            adsbLolBackoffMs = 0L;
        }
        return ADSB_LOL_BASE_REFRESH_MS + adsbLolBackoffMs;
    }

    private synchronized long nextAirplanesDelayMs() {
        if (System.currentTimeMillis() >= airplanesBackoffUntilMs) {
            airplanesBackoffMs = 0L;
        }
        return AIRPLANES_REFRESH_MS + airplanesBackoffMs;
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
        String callsign = aircraft.optString("callsign", "");
        String displayName = aircraft.optString("display_name", callsign);
        String hex = aircraft.optString("hex", "");
        if (hex.isEmpty()) return;
        if (displayName.isEmpty()) displayName = hex.toUpperCase(Locale.US);
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
        if (inRange) {
            details = AppPreferences.distance(this, distanceKm) + "  •  "
                    + AppPreferences.altitude(this, altitudeFt);
        } else {
            String state = L10n.t(this, "out_of_range");
            SpannableString redState = new SpannableString(state + "  •  "
                    + AppPreferences.altitude(this, altitudeFt));
            redState.setSpan(new ForegroundColorSpan(MARColors.RED),
                    0, state.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            details = redState;
        }
        Intent trackerIntent = new Intent(this, TrackerDispatchActivity.class)
                .setData(android.net.Uri.parse("mar://aircraft/" + android.net.Uri.encode(hex)))
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
                .setSubText(displayName)
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
        double geometricFeet = altitudeValueFeet(geometricValue);
        if (!Double.isNaN(geometricFeet)) return geometricFeet;
        return altitudeValueFeet(barometricValue);
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
        item.put("callsign", callsign);
        item.put("display_name", AircraftData.displayName(plane));
        item.put("registration", plane.optString("r", ""));
        item.put("type", plane.optString("t", ""));
        item.put("description", plane.optString("desc",
                plane.optString("typeDescription", "")));
        item.put("category", plane.optString("category", ""));
        item.put("military", MilitaryClassifier.isMilitary(plane));
        item.put("rotorcraft", AircraftData.isRotorcraft(plane));
        item.put("sources", plane.optJSONArray("sources") == null
                ? new JSONArray() : plane.optJSONArray("sources"));
        item.put("distance_km", distanceKm);
        item.put("altitude_ft", Double.isNaN(altitudeFt) ? JSONObject.NULL : altitudeFt);
        item.put("speed_knots", plane.optDouble("gs", 0));
        item.put("track", plane.optDouble("track", 0));
        item.put("squawk", plane.optString("squawk", ""));
        item.put("lat", latitude);
        item.put("lon", longitude);
        item.put("seen", plane.optDouble("seen", 0));
        item.put("emergency", plane.optString("emergency", "none"));
        return item;
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
