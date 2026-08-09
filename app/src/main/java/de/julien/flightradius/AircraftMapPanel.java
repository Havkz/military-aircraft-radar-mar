package de.julien.flightradius;

import android.app.Activity;
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
    private final ExecutorService lookupExecutor = Executors.newFixedThreadPool(2);
    private boolean ready;
    private boolean pendingRefresh;
    private boolean pageVisible;
    private boolean trafficPaused;
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
                + " MilitaryAircraftRadar/1.2.24 (+https://github.com/Havkz/military-aircraft-radar-mar)");
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
        String mapAircraftJson = MonitorService.latestAllAircraftJson();
        JSONObject mapSettings = MapPreferences.json(host);
        try { mapSettings.put("loading", MonitorService.isMapLoading()); }
        catch (Exception ignored) { }
        String script = String.format(Locale.US, "window.marUpdate(%s,%s,%s,%d,%s)",
                JSONObject.quote(mapAircraftJson),
                Double.isNaN(latitude) ? "null" : Double.toString(latitude),
                Double.isNaN(longitude) ? "null" : Double.toString(longitude),
                radius, mapSettings.toString());
        webView.evaluateJavascript(script, null);
    }

    void setPageVisible(boolean visible) {
        pageVisible = visible;
        MonitorService.setMapVisible(pageVisible && !trafficPaused);
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
        @JavascriptInterface public void setMapTrafficPaused(boolean paused) {
            host.runOnUiThread(() -> {
                trafficPaused = paused;
                MonitorService.setMapVisible(pageVisible && !trafficPaused);
                if (!paused && pageVisible && MonitorService.isRunning()) {
                    host.startService(new Intent(host, MonitorService.class)
                            .setAction(MonitorService.ACTION_VIEWPORT_CHANGED));
                }
            });
        }

        @JavascriptInterface public void setMapViewport(
                double latitude, double longitude, int radiusNm) {
            if (!MonitorService.setMapViewport(latitude, longitude, radiusNm)) return;
            host.runOnUiThread(() -> {
                if (pageVisible && !trafficPaused && MonitorService.isRunning()) {
                    host.startService(new Intent(host, MonitorService.class)
                            .setAction(MonitorService.ACTION_VIEWPORT_CHANGED));
                }
            });
        }

        @JavascriptInterface public void requestAircraftPhoto(
                String hex, String registration, String aircraftType) {
            lookupExecutor.submit(() -> {
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
            lookupExecutor.submit(() -> {
                org.json.JSONArray result = AircraftTraceLookup.find(hex);
                String script = "window.marTraceResult&&window.marTraceResult("
                        + JSONObject.quote(hex == null ? "" : hex) + ","
                        + result.toString() + ")";
                host.runOnUiThread(() -> {
                    if (ready) webView.evaluateJavascript(script, null);
                });
            });
        }
    }

    void destroy() {
        ready = false;
        lookupExecutor.shutdownNow();
        webView.stopLoading();
        webView.destroy();
    }
}
