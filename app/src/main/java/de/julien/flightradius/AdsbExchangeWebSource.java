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

import java.util.Locale;

final class AdsbExchangeWebSource {
    private static final String BASE_URL = "https://globe.adsbexchange.com/";
    private static final String EXPORT_HOOK = "(function(){"
            + "if(window.__marAdsbxExportInstalled)return;"
            + "window.__marAdsbxExportInstalled=true;"
            + "function finite(v){return typeof v==='number'&&Number.isFinite(v)}"
            + "function exportVisible(){try{const planes=(typeof g!=='undefined'&&g"
            + "&&Array.isArray(g.planesOrdered))?g.planesOrdered:[];const flights=[];"
            + "for(const p of planes){if(flights.length>=10000)break;const pos=p&&p.position;"
            + "if(!p||!p.visible||!p.inView||!Array.isArray(pos)||!finite(pos[0])"
            + "||!finite(pos[1])||!String(p.icao||'').replace('~','')"
            + ".match(/^[0-9a-f]{6}$/i))continue;flights.push({icao:p.icao,"
            + "latitude:pos[1],longitude:pos[0],callsign:p.flight,"
            + "registration:p.registration,type:p.icaoType,description:p.typeLong,"
            + "operator:p.ownOp,altitude:null!=p.alt_baro?p.alt_baro:p.altitude,"
            + "geometricAltitude:p.alt_geom,speed:null!=p.gs?p.gs:p.speed,track:p.track,"
            + "vspeed:null!=p.baro_rate?p.baro_rate:p.vert_rate,squawk:p.squawk,"
            + "onGround:p.onGround,military:p.military===true||p.military===1,"
            + "seenPosition:p.seen_pos,timestamp:p.position_time});}if(window.MarAdsbx)"
            + "MarAdsbx.submitAircraft(JSON.stringify(flights));}catch(e){}}"
            + "setInterval(exportVisible,1500);})();";

    private final WebView webView;
    private boolean pageReady;
    private boolean visible;
    private boolean loaded;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private int zoom = 8;

    AdsbExchangeWebSource(Activity host) {
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
        webView.addJavascriptInterface(new ExportBridge(), "MarAdsbx");
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
                        || !("adsbexchange.com".equalsIgnoreCase(hostName)
                        || hostName.toLowerCase(Locale.US).endsWith(".adsbexchange.com"));
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
        webView.removeJavascriptInterface("MarAdsbx");
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
        String script = String.format(Locale.US,
                "(function(){try{const lat=%.8f,lon=%.8f,zoom=%d;"
                        + "history.replaceState(null,'','/?lat='+lat+'&lon='+lon+'&zoom='+zoom"
                        + "+'&embedded_webview=true');if(typeof OLMap==='undefined'||!OLMap"
                        + "||typeof ol==='undefined')return;const view=OLMap.getView();"
                        + "view.setCenter(ol.proj.fromLonLat([lon,lat]));view.setZoom(zoom);"
                        + "if(typeof checkMovement==='function')checkMovement();"
                        + "if(typeof fetchData==='function')fetchData({force:true});}catch(e){}})();",
                latitude, longitude, zoom);
        webView.evaluateJavascript(script, null);
    }

    private String viewportUrl() {
        return String.format(Locale.US,
                BASE_URL + "?lat=%.8f&lon=%.8f&zoom=%d&embedded_webview=true",
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
                MonitorService.acceptAdsbExchangeWebAircraft(
                        AdsbExchangeWebAircraftMapper.map(new JSONArray(json), receivedAt),
                        receivedAt);
            } catch (Exception ignored) { }
        }
    }
}
