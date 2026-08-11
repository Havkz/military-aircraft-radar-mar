package de.julien.flightradius;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.MotionEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AircraftMapPanel extends FrameLayout {
    private final Activity host;
    private final WebView webView;
    private final ExecutorService photoExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService traceExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService destinationExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService routeExecutor = Executors.newSingleThreadExecutor();
    private boolean ready;
    private boolean pendingRefresh;
    private boolean pageVisible;
    private long appliedPayloadRevision = -1L;
    private long sendingPayloadRevision = -1L;
    private String pendingFocusHex = "";
    private double pendingFocusLatitude = Double.NaN;
    private double pendingFocusLongitude = Double.NaN;

    AircraftMapPanel(Activity host) {
        super(host);
        this.host = host;
        setBackgroundColor(AppPreferences.isDark(host)
                ? MARColors.DARK_BACKGROUND : MARColors.LIGHT_BACKGROUND);
        webView = new WebView(host);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString()
                + " MilitaryAircraftRadar/1.2.44 (+https://github.com/Havkz/military-aircraft-radar-mar)");
        webView.addJavascriptInterface(new MapBridge(), "MarNative");
        webView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (event.getPointerCount() > 1 || action == MotionEvent.ACTION_POINTER_DOWN) {
                view.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                ready = true;
                view.evaluateJavascript("window.marResize&&window.marResize()", null);
                if (pendingRefresh) refresh();
                applyPendingFocus();
            }

            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme())) return false;
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        && !"http".equalsIgnoreCase(uri.getScheme())) return true;
                try { host.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) { }
                return true;
            }
        });
        addView(webView, new LayoutParams(-1, -1));
        webView.loadUrl("file:///android_asset/aircraft_map.html");
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (ready && width > 0 && height > 0) {
            webView.post(() -> webView.evaluateJavascript(
                    "window.marResize&&window.marResize()", null));
        }
    }

    void refresh() {
        if (!ready) {
            pendingRefresh = true;
            return;
        }
        pendingRefresh = false;
        double latitude = MonitorService.latestOwnLatitude();
        double longitude = MonitorService.latestOwnLongitude();
        if (Double.isNaN(latitude)) {
            latitude = Double.longBitsToDouble(AppPreferences.get(host).getLong(
                    AppPreferences.KEY_OWN_LATITUDE, Double.doubleToRawLongBits(Double.NaN)));
            longitude = Double.longBitsToDouble(AppPreferences.get(host).getLong(
                    AppPreferences.KEY_OWN_LONGITUDE, Double.doubleToRawLongBits(Double.NaN)));
        }
        int radius = AppPreferences.get(host).getInt(
                AppPreferences.KEY_RADIUS_KM, AppPreferences.DEFAULT_RADIUS_KM);
        MonitorService.MapAircraftPayload payload =
                MonitorService.latestMapAircraftPayload();
        JSONObject mapSettings = MapPreferences.json(host);
        try { mapSettings.put("loading", MonitorService.isMapLoading()); }
        catch (Exception ignored) { }
        String configuration = String.format(Locale.US, "%s,%s,%d,%s",
                Double.isNaN(latitude) ? "null" : Double.toString(latitude),
                Double.isNaN(longitude) ? "null" : Double.toString(longitude),
                radius, mapSettings.toString());
        if (payload.revision == appliedPayloadRevision
                || payload.revision == sendingPayloadRevision) {
            webView.evaluateJavascript(
                    "window.marConfigure&&window.marConfigure(" + configuration + ")", null);
            return;
        }
        sendingPayloadRevision = payload.revision;
        String begin = "window.marBeginUpdate&&window.marBeginUpdate("
                + payload.revision + "," + configuration + ","
                + payload.aircraftCount + ")";
        webView.evaluateJavascript(begin, ignored -> sendPayloadChunk(payload, 0));
    }

    private void sendPayloadChunk(MonitorService.MapAircraftPayload payload, int index) {
        if (!ready || payload.revision != sendingPayloadRevision
                || index < 0 || index >= payload.chunks.length) return;
        boolean last = index == payload.chunks.length - 1;
        String script = "window.marAppendUpdate&&window.marAppendUpdate("
                + payload.revision + "," + payload.chunks[index] + ","
                + (last ? "true" : "false") + ")";
        webView.evaluateJavascript(script, ignored -> {
            if (payload.revision != sendingPayloadRevision) return;
            if (last) {
                appliedPayloadRevision = payload.revision;
                sendingPayloadRevision = -1L;
            } else {
                webView.postDelayed(() -> sendPayloadChunk(payload, index + 1), 8L);
            }
        });
    }

    void setPageVisible(boolean visible) {
        pageVisible = visible;
        MonitorService.setMapVisible(pageVisible);
    }

    void focusAircraft(String hex, double latitude, double longitude) {
        pendingFocusHex = hex == null ? "" : hex;
        pendingFocusLatitude = latitude;
        pendingFocusLongitude = longitude;
        applyPendingFocus();
    }

    private void applyPendingFocus() {
        if (!ready || pendingFocusHex.isEmpty()) return;
        String script = String.format(Locale.US, "window.marFocusAircraft(%s,%s,%s)",
                JSONObject.quote(pendingFocusHex),
                Double.isNaN(pendingFocusLatitude) ? "null"
                        : Double.toString(pendingFocusLatitude),
                Double.isNaN(pendingFocusLongitude) ? "null"
                        : Double.toString(pendingFocusLongitude));
        webView.evaluateJavascript(script, null);
        pendingFocusHex = "";
    }

    private final class MapBridge {
        @JavascriptInterface public void setMapViewport(
                double latitude, double longitude, int radiusNm) {
            if (!MonitorService.setMapViewport(latitude, longitude, radiusNm)) return;
            host.runOnUiThread(() -> {
                if (pageVisible && MonitorService.isRunning()) {
                    host.startService(new Intent(host, MonitorService.class)
                            .setAction(MonitorService.ACTION_VIEWPORT_CHANGED));
                }
            });
        }

        @JavascriptInterface public void setIsolatedAircraft(String hex) {
            if (!MonitorService.setMapIsolatedAircraft(hex)) return;
            host.runOnUiThread(() -> {
                if (pageVisible && MonitorService.isRunning()) {
                    host.startService(new Intent(host, MonitorService.class)
                            .setAction(MonitorService.ACTION_VIEWPORT_CHANGED));
                }
            });
        }

        @JavascriptInterface public void copyAircraftInfo(String text) {
            host.runOnUiThread(() -> {
                ClipboardManager clipboard = (ClipboardManager) host.getSystemService(
                        Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText(
                            "Aircraft information", text == null ? "" : text));
                }
            });
        }

        @JavascriptInterface public void requestAircraftPhoto(
                String hex, String registration, String aircraftType) {
            photoExecutor.submit(() -> {
                JSONObject result = AircraftPhotoLookup.find(registration, aircraftType);
                String script = "window.marPhotoResult&&window.marPhotoResult("
                        + JSONObject.quote(hex == null ? "" : hex) + ","
                        + JSONObject.quote(registration == null ? "" : registration) + ","
                        + result.toString() + ")";
                host.runOnUiThread(() -> {
                    if (ready) webView.evaluateJavascript(script, null);
                });
            });
        }

        @JavascriptInterface public void requestAircraftTrace(String hex) {
            traceExecutor.submit(() -> {
                org.json.JSONArray result = AircraftTraceLookup.find(hex);
                String script = "window.marTraceResult&&window.marTraceResult("
                        + JSONObject.quote(hex == null ? "" : hex) + ","
                        + result.toString() + ")";
                host.runOnUiThread(() -> {
                    if (ready) webView.evaluateJavascript(script, null);
                });
            });
        }

        @JavascriptInterface public void requestFrequentDestinations(String hex) {
            destinationExecutor.submit(() -> {
                JSONObject result = AircraftFrequentDestinationsLookup.find(host, hex);
                String script = "window.marFrequentDestinationsResult"
                        + "&&window.marFrequentDestinationsResult("
                        + JSONObject.quote(hex == null ? "" : hex) + ","
                        + result.toString() + ")";
                host.runOnUiThread(() -> {
                    if (ready) webView.evaluateJavascript(script, null);
                });
            });
        }

        @JavascriptInterface public void requestAircraftRoute(
                String hex, String callsign, double latitude, double longitude) {
            routeExecutor.submit(() -> {
                JSONObject result = AircraftRouteLookup.find(
                        host, callsign, latitude, longitude);
                String script = "window.marRouteResult&&window.marRouteResult("
                        + JSONObject.quote(hex == null ? "" : hex) + ","
                        + JSONObject.quote(callsign == null ? "" : callsign) + ","
                        + result.toString() + ")";
                host.runOnUiThread(() -> {
                    if (ready) webView.evaluateJavascript(script, null);
                });
            });
        }
    }

    void destroy() {
        ready = false;
        sendingPayloadRevision = -1L;
        MonitorService.setMapIsolatedAircraft("");
        photoExecutor.shutdownNow();
        traceExecutor.shutdownNow();
        destinationExecutor.shutdownNow();
        routeExecutor.shutdownNow();
        webView.stopLoading();
        webView.destroy();
    }
}
