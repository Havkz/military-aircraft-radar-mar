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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MonitorService extends Service implements LocationListener {
    private static final String CHANNEL_STATUS = "monitor_status_v4";
    private static final String CHANNEL_ALERTS = "military_alerts_v3";
    private static final String ACTION_STOP = "de.julien.flightradius.STOP";
    private static final String ACTION_RESEND_DISMISSED = "de.julien.flightradius.RESEND_DISMISSED";
    private static final long DISMISSED_RESEND_DELAY_MS = 5 * 60 * 1000L;
    private static final int STATUS_NOTIFICATION_ID = 1001;
    private static final int MILITARY_FLAG = 1;

    private final Set<String> aircraftAlreadyInside = new HashSet<>();
    private final Set<Integer> aircraftNotificationIds = new HashSet<>();
    private HandlerThread workerThread;
    private Handler worker;
    private LocationManager locationManager;
    private volatile Location latestLocation;
    private static volatile boolean running;
    private boolean pollingScheduled;

    static boolean isRunning() { return running; }

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            try { pollAircraft(); }
            finally {
                if (worker != null) worker.postDelayed(this,
                        AppPreferences.refreshSeconds(MonitorService.this) * 1000L);
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
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (hasLocationPermission()) {
            registerProvider(LocationManager.GPS_PROVIDER);
            registerProvider(LocationManager.NETWORK_PROVIDER);
        }
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
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
        if (intent != null && ACTION_RESEND_DISMISSED.equals(intent.getAction())) {
            String callsign = intent.getStringExtra("callsign");
            String hex = intent.getStringExtra("hex");
            double distanceKm = intent.getDoubleExtra("distance_km", Double.NaN);
            double altitudeFt = intent.getDoubleExtra("altitude_ft", Double.NaN);
            double lat = intent.getDoubleExtra("lat", Double.NaN);
            double lon = intent.getDoubleExtra("lon", Double.NaN);
            if (worker != null && callsign != null && !callsign.isEmpty()
                    && hex != null && !hex.isEmpty()) {
                worker.postDelayed(() -> showAircraftNotification(
                        callsign, hex, distanceKm, altitudeFt, lat, lon),
                        DISMISSED_RESEND_DELAY_MS);
            }
        }
        if (!pollingScheduled && worker != null) {
            pollingScheduled = true;
            worker.post(pollTask);
        }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.KEY_RUNNING, false)
                .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                .putString(AppPreferences.KEY_CONNECTION, "standby")
                .apply();
        getSystemService(NotificationManager.class).cancelAll();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        AppPreferences.get(this).edit()
                .putBoolean(AppPreferences.KEY_RUNNING, false)
                .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                .putString(AppPreferences.KEY_CONNECTION, "standby").apply();
        if (locationManager != null) locationManager.removeUpdates(this);
        if (worker != null) worker.removeCallbacksAndMessages(null);
        if (workerThread != null) workerThread.quitSafely();
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        for (Integer notificationId : aircraftNotificationIds) {
            notificationManager.cancel(notificationId);
        }
        aircraftNotificationIds.clear();
        notificationManager.cancel(STATUS_NOTIFICATION_ID);
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public void onLocationChanged(Location location) { latestLocation = location; }
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
                    || lastKnown.getTime() > latestLocation.getTime())) latestLocation = lastKnown;
        } catch (IllegalArgumentException | SecurityException ignored) { }
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
        String endpoint = String.format(Locale.US,
                "https://api.adsb.lol/v2/lat/%.5f/lon/%.5f/dist/%d",
                own.getLatitude(), own.getLongitude(), radiusNm);

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", "FlightRadiusMonitor/2.0");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code != 200) {
                updateStatus("NETWORK " + code, "", 0);
                return;
            }

            StringBuilder json = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) json.append(line);
            }

            JSONArray aircraft = new JSONObject(json.toString()).optJSONArray("ac");
            JSONArray liveAircraft = new JSONArray();
            Set<String> currentlyInside = new HashSet<>();
            int militaryCount = 0;
            String nearestCallsign = "";
            double nearestDistanceKm = Double.NaN;
            double nearestAltitudeFt = Double.NaN;
            if (aircraft != null) {
                for (int i = 0; i < aircraft.length(); i++) {
                    JSONObject plane = aircraft.optJSONObject(i);
                    if (plane == null || (plane.optInt("dbFlags", 0) & MILITARY_FLAG) == 0) continue;
                    double distanceNm = plane.optDouble("dst", Double.NaN);
                    double distanceKm = distanceNm * 1.852;
                    if (Double.isNaN(distanceKm) || distanceKm > radiusKm) continue;

                    String hex = plane.optString("hex", "unknown").replace("~", "");
                    String callsign = plane.optString("flight", "").trim();
                    double altitudeFt = altitudeFeet(plane.opt("alt_baro"));
                    militaryCount++;
                    currentlyInside.add(hex);
                    liveAircraft.put(compactAircraft(plane, hex, callsign, distanceKm, altitudeFt));
                    if (!callsign.isEmpty() && (Double.isNaN(nearestDistanceKm)
                            || distanceKm < nearestDistanceKm)) {
                        nearestCallsign = callsign;
                        nearestDistanceKm = distanceKm;
                        nearestAltitudeFt = altitudeFt;
                    }
                    if (!callsign.isEmpty() && !aircraftAlreadyInside.contains(hex))
                        notifyAircraft(plane, callsign, distanceKm, altitudeFt);
                }
            }

            aircraftAlreadyInside.clear();
            aircraftAlreadyInside.addAll(currentlyInside);
            publishTelemetry("connected", militaryCount, liveAircraft, nearestCallsign,
                    nearestDistanceKm, nearestAltitudeFt);
            updateStatus("LIVE // " + nowTime(), "", militaryCount);
        } catch (Exception e) {
            AppPreferences.get(this).edit().putString(AppPreferences.KEY_CONNECTION, "error").apply();
            updateStatus("SIGNAL LOST", "", 0);
        } finally {
            if (connection != null) connection.disconnect();
        }
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
        alerts.setLightColor(Color.rgb(217, 130, 69));
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
                .setColor(Color.rgb(96, 105, 110))
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

    private void notifyAircraft(JSONObject plane, String callsign, double distanceKm, double altitudeFt) {
        String hex = plane.optString("hex", "unknown").replace("~", "");
        showAircraftNotification(callsign, hex, distanceKm, altitudeFt,
                plane.optDouble("lat", Double.NaN), plane.optDouble("lon", Double.NaN));
    }

    private void showAircraftNotification(String callsign, String hex, double distanceKm,
                                          double altitudeFt, double aircraftLat,
                                          double aircraftLon) {
        String details = AppPreferences.distance(this, distanceKm) + "  •  "
                + AppPreferences.altitude(this, altitudeFt);
        Intent trackerIntent = new Intent(this, TrackerDispatchActivity.class)
                .setData(android.net.Uri.parse("mar://aircraft/" + android.net.Uri.encode(hex)))
                .putExtra("callsign", callsign)
                .putExtra("hex", hex)
                .putExtra("lat", aircraftLat)
                .putExtra("lon", aircraftLon);
        PendingIntent tracker = PendingIntent.getActivity(this, hex.hashCode() ^ 0x5f3759df,
                trackerIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent dismissedIntent = new Intent(this, MonitorService.class)
                .setAction(ACTION_RESEND_DISMISSED)
                .putExtra("callsign", callsign)
                .putExtra("hex", hex)
                .putExtra("distance_km", distanceKm)
                .putExtra("altitude_ft", altitudeFt)
                .putExtra("lat", aircraftLat)
                .putExtra("lon", aircraftLon);
        PendingIntent dismissed = PendingIntent.getService(this, hex.hashCode() ^ 0x4d4152,
                dismissedIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_radar)
                .setLargeIcon(radarBitmap(true))
                .setContentTitle(callsign)
                .setContentText(details)
                .setSubText(L10n.t(this, "found_via"))
                .setColor(Color.rgb(217, 130, 69))
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(tracker)
                .setDeleteIntent(dismissed)
                .setAutoCancel(false);
        Notification notification = builder.build();
        int notificationId = hex.hashCode();
        aircraftNotificationIds.add(notificationId);
        getSystemService(NotificationManager.class).notify(notificationId, notification);
    }

    private double altitudeFeet(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String && "ground".equalsIgnoreCase((String) value)) return 0;
        return Double.NaN;
    }

    private JSONObject compactAircraft(JSONObject plane, String hex, String callsign,
                                       double distanceKm, double altitudeFt) throws Exception {
        JSONObject item = new JSONObject();
        item.put("hex", hex);
        item.put("callsign", callsign);
        item.put("registration", plane.optString("r", ""));
        item.put("type", plane.optString("t", ""));
        item.put("distance_km", distanceKm);
        item.put("altitude_ft", Double.isNaN(altitudeFt) ? JSONObject.NULL : altitudeFt);
        item.put("speed_knots", plane.optDouble("gs", 0));
        item.put("track", plane.optDouble("track", 0));
        item.put("squawk", plane.optString("squawk", ""));
        item.put("lat", plane.optDouble("lat", 0));
        item.put("lon", plane.optDouble("lon", 0));
        item.put("seen", plane.optDouble("seen", 0));
        item.put("emergency", plane.optString("emergency", "none"));
        return item;
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
        paint.setColor(Color.BLACK);
        canvas.drawCircle(64, 64, 62, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(alert ? Color.rgb(217, 130, 69) : Color.rgb(79, 138, 101));
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
