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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AircraftListActivity extends Activity {
    private static final int GREEN = Color.rgb(79, 138, 101);
    private static final int BLUE = Color.rgb(82, 122, 163);
    private static final int RED = Color.rgb(211, 75, 75);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout contacts;
    private int background, surface, text, muted;
    private boolean dark;
    private String lastJson = "";

    private final Runnable refresh = new Runnable() {
        @Override public void run() { updateList(); handler.postDelayed(this, 1500L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = AppPreferences.isDark(this); L10n.applyDirection(this);
        background = dark ? Color.rgb(0, 4, 8) : Color.rgb(244, 246, 243);
        surface = dark ? Color.rgb(10, 14, 19) : Color.WHITE;
        text = dark ? Color.rgb(228, 232, 235) : Color.rgb(21, 30, 39);
        muted = dark ? Color.rgb(139, 149, 156) : Color.rgb(94, 105, 113);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(30)); scroll.addView(root);
        Button back = button("‹  RADAR"); back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = label(L10n.t(this, "session_aircraft"), 29, text, Typeface.BOLD);
        title.setPadding(0, dp(18), 0, 0); root.addView(title);
        TextView subtitle = label(L10n.t(this, "since_app_start"),
                11, GREEN, Typeface.BOLD); subtitle.setLetterSpacing(0.1f); root.addView(subtitle);
        contacts = new LinearLayout(this); contacts.setOrientation(LinearLayout.VERTICAL);
        contacts.setPadding(0, dp(18), 0, 0); root.addView(contacts);
        setContentView(scroll);
    }

    private void updateList() {
        String json = AppPreferences.get(this).getString(
                AppPreferences.KEY_AIRCRAFT_HISTORY_JSON, "[]");
        if (json.equals(lastJson)) return; lastJson = json; contacts.removeAllViews();
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                TextView empty = label(L10n.t(this, "empty_list"), 15, muted, Typeface.NORMAL);
                empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(70), 0, 0); contacts.addView(empty); return;
            }
            for (int i = 0; i < array.length(); i++) addAircraft(array.getJSONObject(i), i);
        } catch (Exception ignored) { }
    }

    private void addAircraft(JSONObject plane, int index) {
        LinearLayout card = card(); card.setOrientation(LinearLayout.VERTICAL);
        String callsign = plane.optString("callsign", "");
        TextView name = label(callsign.isEmpty() ? L10n.t(this, "no_callsign") : callsign,
                21, BLUE, Typeface.BOLD); card.addView(name);
        String type = plane.optString("type", "—"); String reg = plane.optString("registration", "—");
        card.addView(label(type + "  •  " + reg, 12, muted, Typeface.BOLD));
        double distance = plane.optDouble("distance_km", Double.NaN);
        double altitude = plane.optDouble("altitude_ft", Double.NaN);
        boolean inRange = plane.optBoolean("in_range", false);
        String state = inRange ? AppPreferences.distance(this, distance)
                : L10n.t(this, "out_of_range");
        TextView metrics = label(state + "   //   "
                + AppPreferences.altitude(this, altitude), 15,
                inRange ? text : RED, Typeface.BOLD);
        metrics.setPadding(0, dp(10), 0, 0); card.addView(metrics);
        long firstSeen = plane.optLong("first_seen_ms", 0L);
        long lastSeen = plane.optLong("last_seen_ms", 0L);
        TextView time = label(L10n.t(this, "in_range_time") + "  "
                + formatTime(firstSeen) + " – " + formatTime(lastSeen),
                12, muted, Typeface.NORMAL);
        time.setPadding(0, dp(7), 0, 0); card.addView(time);
        card.setOnClickListener(v -> startActivity(new Intent(this, AircraftDetailActivity.class)
                .putExtra("aircraft", plane.toString())));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(11)); contacts.addView(card, params);
        card.setAlpha(0f); card.setTranslationY(dp(18));
        card.animate().alpha(1f).translationY(0f).setStartDelay(index * 55L).setDuration(320).start();
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "—";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this); v.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), dark ? Color.rgb(38, 53, 65) : Color.rgb(208, 215, 211)); v.setBackground(bg); return v;
    }
    private TextView label(String s, float z, int c, int style) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(c);
        v.setTypeface(Typeface.create("sans-serif", style)); return v;
    }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(BLUE);
        b.setTextSize(12); b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setStateListAnimator(null);
        b.setBackground(card().getBackground()); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
