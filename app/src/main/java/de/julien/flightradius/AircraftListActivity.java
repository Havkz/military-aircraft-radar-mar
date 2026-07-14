package de.julien.flightradius;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class AircraftListActivity extends Activity {
    private static final int CYAN = Color.rgb(0, 245, 255);
    private static final int VIOLET = Color.rgb(176, 38, 255);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout contacts;
    private int background, surface, text, muted;
    private boolean dark, de;
    private String lastJson = "";

    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateList(); handler.postDelayed(this, 1500L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = AppPreferences.isDark(this); de = AppPreferences.isGerman(this);
        background = dark ? Color.BLACK : Color.rgb(239, 247, 250);
        surface = dark ? Color.rgb(5, 10, 15) : Color.WHITE;
        text = dark ? Color.rgb(230, 250, 255) : Color.rgb(7, 25, 34);
        muted = dark ? Color.rgb(119, 153, 164) : Color.rgb(70, 96, 107);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(30)); scroll.addView(root);
        Button back = button("‹  " + (de ? "RADAR" : "RADAR")); back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = label(de ? "LIVE-FLUGZEUGE" : "LIVE AIRCRAFT", 29, text, Typeface.BOLD);
        title.setPadding(0, dp(18), 0, 0); root.addView(title);
        TextView subtitle = label(de ? "MILITÄRKONTAKTE IM GEWÄHLTEN RADIUS" : "MILITARY CONTACTS IN SELECTED RADIUS",
                11, CYAN, Typeface.BOLD); subtitle.setLetterSpacing(0.1f); root.addView(subtitle);
        contacts = new LinearLayout(this); contacts.setOrientation(LinearLayout.VERTICAL);
        contacts.setPadding(0, dp(18), 0, 0); root.addView(contacts);
        setContentView(scroll);
    }

    private void updateList() {
        String json = AppPreferences.get(this).getString(AppPreferences.KEY_AIRCRAFT_JSON, "[]");
        if (json.equals(lastJson)) return; lastJson = json; contacts.removeAllViews();
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                TextView empty = label(de ? "Keine militärischen Kontakte erkannt.\nDer Live-Scan läuft weiter."
                        : "No military contacts detected.\nLive scan continues.", 15, muted, Typeface.NORMAL);
                empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(70), 0, 0); contacts.addView(empty); return;
            }
            for (int i = 0; i < array.length(); i++) addAircraft(array.getJSONObject(i), i);
        } catch (Exception ignored) { }
    }

    private void addAircraft(JSONObject plane, int index) {
        LinearLayout card = card(); card.setOrientation(LinearLayout.VERTICAL);
        String callsign = plane.optString("callsign", "");
        TextView name = label(callsign.isEmpty() ? (de ? "OHNE CALLSIGN" : "NO CALLSIGN") : callsign,
                21, CYAN, Typeface.BOLD); card.addView(name);
        String type = plane.optString("type", "—"); String reg = plane.optString("registration", "—");
        card.addView(label(type + "  •  " + reg, 12, muted, Typeface.BOLD));
        double distance = plane.optDouble("distance_km", Double.NaN);
        double altitude = plane.optDouble("altitude_ft", Double.NaN);
        TextView metrics = label(AppPreferences.distance(this, distance) + "   //   "
                + AppPreferences.altitude(this, altitude), 15, text, Typeface.BOLD);
        metrics.setPadding(0, dp(10), 0, 0); card.addView(metrics);
        card.setOnClickListener(v -> startActivity(new Intent(this, AircraftDetailActivity.class)
                .putExtra("aircraft", plane.toString())));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(11)); contacts.addView(card, params);
        card.setAlpha(0f); card.setTranslationY(dp(18));
        card.animate().alpha(1f).translationY(0f).setStartDelay(index * 55L).setDuration(320).start();
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this); v.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), dark ? Color.rgb(0, 80, 89) : Color.rgb(190, 220, 227)); v.setBackground(bg); return v;
    }
    private TextView label(String s, float z, int c, int style) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(c);
        v.setTypeface(Typeface.create("sans-serif", style)); return v;
    }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(CYAN);
        b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setStateListAnimator(null);
        b.setBackground(card().getBackground()); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
