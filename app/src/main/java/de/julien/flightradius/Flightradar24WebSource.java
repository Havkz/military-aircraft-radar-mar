package de.julien.flightradius;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class Flightradar24WebSource {
    private static final String BASE_URL = "https://www.flightradar24.com";
    private static final String EXPORT_HOOK = "(function(){"
            + "if(window.__marFr24ExportInstalled)return;"
            + "window.__marFr24ExportInstalled=true;"
            + "const originalEntries=Object.entries;let timer=0;const pending=new Map;"
            + "function finite(v){return typeof v==='number'&&Number.isFinite(v)}"
            + "function snapshot(entries){const flights=[];for(const pair of entries){"
            + "const v=pair&&pair[1];if(!v||!finite(v.latitude)||!finite(v.longitude)"
            + "||!String(v.icao||'').match(/^[0-9a-f]{6}$/i))continue;"
            + "flights.push({icao:v.icao,latitude:v.latitude,longitude:v.longitude,"
            + "callsign:v.callsign,registration:v.registration,type:v.type,"
            + "altitude:v.altitude,speed:v.speed,track:v.track,vspeed:v.vspeed,"
            + "squawk:v.squawk,onGround:v.onGround,timestamp:v.timestamp,"
            + "timestampMs:v.timestampMs});}return flights}"
            + "Object.entries=function(value){const entries=originalEntries(value);try{"
            + "const flights=snapshot(entries);if(flights.length){for(const flight of flights)"
            + "pending.set(String(flight.icao).toLowerCase(),flight);if(!timer)"
            + "timer=setTimeout(function(){const batch=Array.from(pending.values());"
            + "pending.clear();timer=0;if(batch.length&&window.MarFr24)"
            + "MarFr24.submitAircraft(JSON.stringify(batch));},500);}}catch(e){}"
            + "return entries};})();";

    private final WebView webView;
    private boolean pageReady;
    private boolean visible;
    private boolean loaded;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private int zoom = 8;

    Flightradar24WebSource(Activity host) {
        webView = new WebView(host);
        webView.setAlpha(0f);
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setOnTouchListener((view, event) -> true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.addJavascriptInterface(new ExportBridge(), "MarFr24");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url,
                                                android.graphics.Bitmap favicon) {
                pageReady = false;
                view.evaluateJavascript(EXPORT_HOOK, null);
            }

            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                view.evaluateJavascript(EXPORT_HOOK, ignored -> applyViewport());
            }

            @Override public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String hostName = uri.getHost();
                return !"https".equalsIgnoreCase(uri.getScheme())
                        || hostName == null
                        || !("flightradar24.com".equalsIgnoreCase(hostName)
                        || hostName.toLowerCase(Locale.US).endsWith(".flightradar24.com"));
            }
        });
    }

    WebView view() {
        return webView;
    }

    void setVisible(boolean visible) {
        this.visible = visible;
        if (visible) {
            webView.onResume();
            loadIfReady();
        } else {
            webView.onPause();
        }
    }

    void updateViewport(double latitude, double longitude, int zoom) {
        if (!validPosition(latitude, longitude)) return;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoom = Math.max(2, Math.min(18, zoom));
        if (!loaded) loadIfReady();
        else if (pageReady) applyViewport();
    }

    void destroy() {
        webView.removeJavascriptInterface("MarFr24");
        webView.stopLoading();
        webView.destroy();
    }

    private void loadIfReady() {
        if (!visible || loaded || !validPosition(latitude, longitude)) return;
        loaded = true;
        webView.loadUrl(viewportUrl());
    }

    private void applyViewport() {
        if (!pageReady || !visible || !validPosition(latitude, longitude)) return;
        String path = String.format(Locale.US, "/%.5f,%.5f/%d", latitude, longitude, zoom);
        String script = "(function(){const path=" + JSONObject.quote(path)
                + ";if(location.pathname===path)return;history.replaceState(null,'',path);"
                + "window.dispatchEvent(new PopStateEvent('popstate'));})();";
        webView.evaluateJavascript(script, null);
    }

    private String viewportUrl() {
        return String.format(Locale.US, BASE_URL + "/%.5f,%.5f/%d",
                latitude, longitude, zoom);
    }

    private static boolean validPosition(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isInfinite(latitude)
                && !Double.isNaN(longitude) && !Double.isInfinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }

    private static final class ExportBridge {
        @JavascriptInterface public void submitAircraft(String json) {
            if (json == null || json.length() > 8_000_000) return;
            try {
                long receivedAt = System.currentTimeMillis();
                MonitorService.acceptFlightradar24Aircraft(
                        Flightradar24AircraftMapper.map(new JSONArray(json), receivedAt),
                        receivedAt);
            } catch (Exception ignored) { }
        }
    }
}
