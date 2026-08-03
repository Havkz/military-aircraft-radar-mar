package de.julien.flightradius;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
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
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString()
                + " MilitaryAircraftRadar/1.2.4 (+https://github.com/Havkz/military-aircraft-radar-mar)");
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
        String script = String.format(Locale.US, "window.marUpdate(%s,%s,%s,%d,%s)",
                JSONObject.quote(MonitorService.latestAllAircraftJson()),
                Double.isNaN(latitude) ? "null" : Double.toString(latitude),
                Double.isNaN(longitude) ? "null" : Double.toString(longitude),
                radius, MapPreferences.json(host).toString());
        webView.evaluateJavascript("window.marResize&&window.marResize();" + script, null);
    }

    void destroy() {
        webView.stopLoading();
        webView.destroy();
    }
}
