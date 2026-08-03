package de.julien.flightradius;

import android.app.Activity;
import android.graphics.Color;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.util.Locale;

final class AircraftMapPanel extends FrameLayout {
    private final Activity host;
    private final WebView webView;
    private boolean ready;
    private boolean pendingRefresh;

    AircraftMapPanel(Activity host) {
        super(host);
        this.host = host;
        setBackgroundColor(AppPreferences.isDark(host)
                ? MARColors.DARK_BACKGROUND : MARColors.LIGHT_BACKGROUND);
        webView = new WebView(host);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString()
                + " MilitaryAircraftRadar/1.1 (+https://github.com/Havkz/military-aircraft-radar-mar)");
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                ready = true;
                if (pendingRefresh) refresh();
            }
        });
        addView(webView, new LayoutParams(-1, -1));
        webView.loadUrl("file:///android_asset/aircraft_map.html");
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
        String script = String.format(Locale.US, "window.marUpdate(%s,%s,%s,%d,%s)",
                JSONObject.quote(MonitorService.latestAllAircraftJson()),
                Double.isNaN(latitude) ? "null" : Double.toString(latitude),
                Double.isNaN(longitude) ? "null" : Double.toString(longitude),
                radius, AppPreferences.isDark(host) ? "true" : "false");
        webView.evaluateJavascript(script, null);
    }
}
