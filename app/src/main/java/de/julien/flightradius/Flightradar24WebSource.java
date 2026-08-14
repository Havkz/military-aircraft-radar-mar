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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class Flightradar24WebSource {
    interface MetadataListener {
        void onMetadata(String hex, String registration, JSONObject metadata);
    }

    interface RouteListener {
        void onRoute(String hex, String callsign, JSONObject route);
    }

    private static final String BASE_URL = "https://www.flightradar24.com";
    private static final String EXPORT_HOOK = "(function(){"
            + "if(window.__marFr24ExportInstalled)return;"
            + "window.__marFr24ExportInstalled=true;"
            + "const originalEntries=Object.entries;let timer=0;const pending=new Map;"
            + "function finite(v){return typeof v==='number'&&Number.isFinite(v)}"
            + "function fallbackIcao(id){const text=String(id||'');if(!text)return'';"
            + "let hash=2166136261;for(let i=0;i<text.length;i++)"
            + "hash=Math.imul(hash^text.charCodeAt(i),16777619);"
            + "return'~'+((hash>>>0)&16777215).toString(16).padStart(6,'0')}"
            + "function snapshot(entries){const flights=[];for(const pair of entries){"
            + "const v=pair&&pair[1],a=Array.isArray(v),"
            + "flightId=a?pair[0]:(v&&(v.flightId||v.flight_id||v.id)||pair[0]),"
            + "rawIcao=a?v[0]:v&&(v.icao||v.hex),"
            + "icao=String(rawIcao||'').match(/^[0-9a-f]{6}$/i)"
            + "?rawIcao:(!a&&v&&v.id&&typeof v.onGround==='boolean'"
            + "?fallbackIcao(flightId):''),"
            + "latitude=a?v[1]:v&&v.latitude,longitude=a?v[2]:v&&v.longitude;"
            + "if(!v||!finite(latitude)||!finite(longitude)"
            + "||!String(icao||'').match(/^~?[0-9a-f]{6}$/i))continue;"
            + "flights.push({icao:icao,latitude:latitude,longitude:longitude,"
            + "flightId:flightId,"
            + "callsign:a?(v[16]||v[13]):v.callsign,"
            + "registration:a?v[9]:v.registration,"
            + "type:a?v[8]:(v.type||v.aircraftType||v.aircraft_type),"
            + "altitude:a?v[4]:v.altitude,speed:a?v[5]:v.speed,"
            + "track:a?v[3]:v.track,vspeed:a?v[15]:v.vspeed,"
            + "squawk:a?v[6]:v.squawk,onGround:a?v[14]===1:"
            + "(v.onGround||v.on_ground||v.ground),timestamp:a?v[10]:v.timestamp,"
            + "timestampMs:a?null:v.timestampMs});}return flights}"
            + "Object.entries=function(value){const entries=originalEntries(value);try{"
            + "const flights=snapshot(entries);if(flights.length){for(const flight of flights)"
            + "pending.set(String(flight.icao).toLowerCase(),flight);if(!timer)"
            + "timer=setTimeout(function(){const batch=Array.from(pending.values());"
            + "pending.clear();timer=0;if(batch.length&&window.MarFr24)"
            + "MarFr24.submitAircraft(JSON.stringify(batch));},500);}}catch(e){}"
            + "return entries};})();";

    private final WebView webView;
    private final MetadataListener metadataListener;
    private final RouteListener routeListener;
    private final Map<String, FlightReference> flightReferences = new LinkedHashMap<>();
    private boolean pageReady;
    private boolean visible;
    private boolean loaded;
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    private int zoom = 8;
    private String pendingMetadataHex = "";
    private String pendingMetadataRegistration = "";
    private String pendingRouteHex = "";
    private String pendingRouteCallsign = "";
    private double pendingRouteLatitude = Double.NaN;
    private double pendingRouteLongitude = Double.NaN;
    private double pendingRouteTrack = Double.NaN;
    private double pendingRouteSpeedKnots;

    Flightradar24WebSource(Activity host, MetadataListener metadataListener,
                           RouteListener routeListener) {
        this.metadataListener = metadataListener;
        this.routeListener = routeListener;
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
                view.evaluateJavascript(EXPORT_HOOK, ignored -> {
                    applyViewport();
                    fetchPendingMetadata();
                    fetchPendingRoute();
                });
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

    void requestMetadata(String rawHex, String rawRegistration) {
        String hex = normalizeHex(rawHex);
        String registration = rawRegistration == null ? "" : rawRegistration.trim()
                .toUpperCase(Locale.US);
        if (hex.isEmpty() || !registration.isEmpty()
                && !registration.matches("[A-Z0-9-]{2,12}")) return;
        pendingMetadataHex = hex;
        pendingMetadataRegistration = registration;
        fetchPendingMetadata();
    }

    void requestRoute(String rawHex, String rawCallsign,
                      double latitude, double longitude,
                      double track, double speedKnots) {
        String hex = normalizeHex(rawHex);
        if (hex.isEmpty() || !validPosition(latitude, longitude)) return;
        pendingRouteHex = hex;
        pendingRouteCallsign = rawCallsign == null ? "" : rawCallsign.trim();
        pendingRouteLatitude = latitude;
        pendingRouteLongitude = longitude;
        pendingRouteTrack = track;
        pendingRouteSpeedKnots = speedKnots;
        fetchPendingRoute();
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

    private void fetchPendingMetadata() {
        if (!pageReady || !visible || pendingMetadataHex.isEmpty()) return;
        String hex = pendingMetadataHex;
        String registration = pendingMetadataRegistration;
        String flightId = flightIdFor(hex);
        pendingMetadataHex = "";
        pendingMetadataRegistration = "";
        String script = "(function(){const h=" + JSONObject.quote(hex)
                + ",r=" + JSONObject.quote(registration)
                + ",f=" + JSONObject.quote(flightId)
                + ";const get=p=>p?fetch(p,{credentials:'include'})"
                + ".then(x=>x.ok?x.text():'').catch(()=>''):Promise.resolve('');"
                + "const norm=v=>String(v||'').toUpperCase().replace(/[^A-Z0-9]/g,'');"
                + "get('/v1/search/web/find?query='+encodeURIComponent(h)+'&limit=20')"
                + ".then(s=>{let rr=r;try{const j=JSON.parse(s),x=(j.results||[]).find(x=>"
                + "x&&x.type==='aircraft'&&x.detail&&norm(x.detail.hex)===norm(h));"
                + "if(x&&x.id)rr=String(x.id).toUpperCase()}catch(e){}"
                + "return get(rr?'/v1/search/web/find?query='+encodeURIComponent(rr)"
                + "+'&limit=20':'').then(sr=>{let id=f;try{const j=JSON.parse(sr),"
                + "x=(j.results||[]).find(x=>x&&x.type==='live'&&x.detail"
                + "&&norm(x.detail.reg)===norm(rr)&&/^[A-Za-z0-9_-]{4,32}$/.test(x.id));"
                + "if(x)id=x.id}catch(e){}return Promise.all(["
                + "get(id?'/clickhandler/?flight='+encodeURIComponent(id):''),"
                + "get(rr?'/data/aircraft/'+encodeURIComponent(rr.toLowerCase()):'')])"
                + ".then(v=>window.MarFr24&&MarFr24.submitSelectedMetadata("
                + "h,rr,id,s,sr,v[0],v[1]))})});})();";
        webView.evaluateJavascript(script, null);
    }

    private void fetchPendingRoute() {
        if (!pageReady || !visible || pendingRouteHex.isEmpty()) return;
        String flightId = flightIdFor(pendingRouteHex);
        if (flightId.isEmpty()) return;
        String hex = pendingRouteHex;
        String callsign = pendingRouteCallsign;
        double latitude = pendingRouteLatitude;
        double longitude = pendingRouteLongitude;
        double track = pendingRouteTrack;
        double speedKnots = pendingRouteSpeedKnots;
        pendingRouteHex = "";
        String script = "(function(){const h=" + JSONObject.quote(hex)
                + ",c=" + JSONObject.quote(callsign)
                + ",f=" + JSONObject.quote(flightId)
                + ",lat=" + Double.toString(latitude)
                + ",lon=" + Double.toString(longitude)
                + ",tr=" + Double.toString(track)
                + ",sp=" + Double.toString(speedKnots)
                + ";fetch('/clickhandler/?flight='+encodeURIComponent(f),"
                + "{credentials:'include'}).then(x=>x.ok?x.text():'')"
                + ".then(t=>window.MarFr24&&MarFr24.submitRoute(h,c,f,lat,lon,tr,sp,t))"
                + ".catch(()=>window.MarFr24&&MarFr24.submitRoute(h,c,f,lat,lon,tr,sp,''));})();";
        webView.evaluateJavascript(script, null);
    }

    private synchronized void rememberFlightReferences(JSONArray aircraft, long receivedAtMs) {
        for (int i = 0; i < aircraft.length(); i++) {
            JSONObject item = aircraft.optJSONObject(i);
            if (item == null) continue;
            String hex = normalizeHex(item.optString("icao", ""));
            String flightId = item.optString("flightId", "").trim();
            if (!hex.isEmpty() && flightId.matches("[A-Za-z0-9_-]{4,32}")) {
                flightReferences.remove(hex);
                flightReferences.put(hex, new FlightReference(flightId, receivedAtMs));
            }
        }
        Iterator<Map.Entry<String, FlightReference>> iterator =
                flightReferences.entrySet().iterator();
        while (iterator.hasNext()) {
            FlightReference reference = iterator.next().getValue();
            if (receivedAtMs - reference.receivedAtMs > 5 * 60_000L) iterator.remove();
        }
        while (flightReferences.size() > 12_000) {
            Iterator<String> keys = flightReferences.keySet().iterator();
            if (!keys.hasNext()) break;
            keys.next();
            keys.remove();
        }
    }

    private synchronized String flightIdFor(String hex) {
        FlightReference reference = flightReferences.get(hex);
        if (reference == null
                || System.currentTimeMillis() - reference.receivedAtMs > 5 * 60_000L) {
            flightReferences.remove(hex);
            return "";
        }
        return reference.flightId;
    }

    private synchronized void rememberFlightReference(
            String rawHex, String flightId, long receivedAtMs) {
        String hex = normalizeHex(rawHex);
        if (hex.isEmpty() || flightId == null
                || !flightId.matches("[A-Za-z0-9_-]{4,32}")) return;
        flightReferences.remove(hex);
        flightReferences.put(hex, new FlightReference(flightId, receivedAtMs));
    }

    private static String normalizeHex(String value) {
        String hex = value == null ? "" : value.replace("~", "").trim()
                .toLowerCase(Locale.US).replaceAll("[^0-9a-f]", "");
        return hex.length() == 6 ? hex : "";
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

    private final class ExportBridge {
        @JavascriptInterface public void submitAircraft(String json) {
            if (json == null || json.length() > 8_000_000) return;
            try {
                long receivedAt = System.currentTimeMillis();
                JSONArray exported = new JSONArray(json);
                rememberFlightReferences(exported, receivedAt);
                MonitorService.acceptFlightradar24Aircraft(
                        Flightradar24AircraftMapper.map(exported, receivedAt),
                        receivedAt);
                webView.post(Flightradar24WebSource.this::fetchPendingMetadata);
                webView.post(Flightradar24WebSource.this::fetchPendingRoute);
            } catch (Exception ignored) { }
        }

        @JavascriptInterface public void submitSelectedMetadata(
                String hex, String registration, String flightId, String searchJson,
                String registrationSearchJson, String flightJson, String html) {
            if (searchJson == null || searchJson.length() > 1_000_000
                    || registrationSearchJson == null
                    || registrationSearchJson.length() > 1_000_000
                    || flightJson == null || flightJson.length() > 2_000_000
                    || html == null || html.length() > 1_000_000) return;
            String verifiedFlightId = Flightradar24MetadataParser.liveFlightId(
                    registrationSearchJson, registration);
            if (verifiedFlightId.equals(flightId)) {
                rememberFlightReference(hex, verifiedFlightId, System.currentTimeMillis());
            }
            JSONObject metadata = Flightradar24MetadataParser.parseFlightDetails(
                    flightJson, hex);
            try {
                Flightradar24MetadataParser.mergeMissing(metadata,
                        Flightradar24MetadataParser.parseSearch(searchJson, hex));
                Flightradar24MetadataParser.mergeMissing(metadata,
                        Flightradar24MetadataParser.parseSearch(
                                registrationSearchJson, hex));
                Flightradar24MetadataParser.mergeMissing(metadata,
                        Flightradar24MetadataParser.parse(html, registration));
                Flightradar24MetadataParser.mergeMissing(metadata,
                        Flightradar24MetadataParser.parsePhoto(
                                html, registration, metadata.optString("type")));
            } catch (Exception ignored) { }
            if (metadataListener != null) {
                metadataListener.onMetadata(hex, registration, metadata);
            }
            webView.post(Flightradar24WebSource.this::fetchPendingRoute);
        }

        @JavascriptInterface public void submitRoute(
                String hex, String callsign, String flightId,
                double latitude, double longitude, double track,
                double speedKnots, String json) {
            if (json == null || json.length() > 2_000_000) return;
            JSONObject route = AircraftRouteLookup.parseFlightradar24(
                    json, flightId, hex, callsign, latitude, longitude,
                    track, speedKnots);
            if (routeListener != null) routeListener.onRoute(hex, callsign, route);
        }
    }

    private static final class FlightReference {
        final String flightId;
        final long receivedAtMs;

        FlightReference(String flightId, long receivedAtMs) {
            this.flightId = flightId;
            this.receivedAtMs = receivedAtMs;
        }
    }
}
