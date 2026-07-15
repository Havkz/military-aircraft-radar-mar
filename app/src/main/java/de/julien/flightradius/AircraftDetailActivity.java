package de.julien.flightradius;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AircraftDetailActivity extends Activity {
    private static final int GREEN = MARColors.GREEN;
    private static final int BLUE = MARColors.BLUE;
    private static final int ORANGE = MARColors.ORANGE;
    private static final int RED = MARColors.RED;
    private boolean dark;
    private int background, surface, text, muted;
    private JSONObject aircraft;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = AppPreferences.isDark(this); L10n.applyDirection(this);
        background = dark ? MARColors.DARK_BACKGROUND : MARColors.LIGHT_BACKGROUND;
        surface = dark ? MARColors.DARK_SURFACE : MARColors.LIGHT_SURFACE;
        text = dark ? MARColors.DARK_TEXT : MARColors.LIGHT_TEXT;
        muted = dark ? MARColors.DARK_MUTED : MARColors.LIGHT_MUTED;
        try { aircraft = new JSONObject(getIntent().getStringExtra("aircraft")); }
        catch (Exception e) { aircraft = new JSONObject(); }
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(32)); scroll.addView(root);
        Button back = button("‹  " + L10n.t(this, "aircraft")); back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));

        RadarView hero = new RadarView(this); hero.setDarkMode(dark);
        hero.setScanning(AppPreferences.get(this).getBoolean(AppPreferences.KEY_RUNNING, false));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(190));
        heroParams.setMargins(0, dp(14), 0, 0); root.addView(hero, heroParams);

        String callsign = aircraft.optString("callsign", "");
        TextView title = label(callsign.isEmpty() ? L10n.t(this, "no_callsign") : callsign,
                32, BLUE, Typeface.BOLD); title.setPadding(0, dp(12), 0, 0); root.addView(title);
        TextView id = label(aircraft.optString("type", "—") + "  //  "
                + aircraft.optString("registration", "—") + "  //  "
                + aircraft.optString("hex", "—").toUpperCase(Locale.ROOT), 12, ORANGE, Typeface.BOLD);
        id.setLetterSpacing(0.07f); root.addView(id);

        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(18), 0, 0); root.addView(grid);
        boolean inRange = aircraft.optBoolean("in_range", false);
        row(grid, L10n.t(this, "status"),
                L10n.t(this, inRange ? "in_range" : "out_of_range"),
                inRange ? GREEN : RED);
        row(grid, L10n.t(this, "distance"), inRange
                ? AppPreferences.distance(this, aircraft.optDouble("distance_km", Double.NaN))
                : L10n.t(this, "out_of_range"), inRange ? text : RED);
        row(grid, L10n.t(this, "altitude"),
                AppPreferences.altitude(this, aircraft.optDouble("altitude_ft", Double.NaN)));
        row(grid, L10n.t(this, "first_in_range"),
                formatTime(aircraft.optLong("first_seen_ms", 0L)));
        row(grid, L10n.t(this, "last_in_range"),
                formatTime(aircraft.optLong("last_seen_ms", 0L)));
        row(grid, L10n.t(this, "ground_speed"),
                String.format(Locale.US, "%.0f kt", aircraft.optDouble("speed_knots", 0)));
        row(grid, L10n.t(this, "track"),
                String.format(Locale.US, "%.0f°", aircraft.optDouble("track", 0)));
        row(grid, "Squawk", aircraft.optString("squawk", "—"));
        row(grid, L10n.t(this, "last_signal"),
                String.format(Locale.US, "%.1f s", aircraft.optDouble("seen", 0)));
        row(grid, L10n.t(this, "position"), String.format(Locale.US, "%.5f, %.5f",
                aircraft.optDouble("lat", 0), aircraft.optDouble("lon", 0)));
        row(grid, "Emergency", aircraft.optString("emergency", "none"));

        Button tracker = neonButton(L10n.t(this, "open_in") + TrackerLinks.selectedName(this).toUpperCase(Locale.ROOT));
        tracker.setOnClickListener(v -> openTracker());
        LinearLayout.LayoutParams trackerParams = new LinearLayout.LayoutParams(-1, dp(60));
        trackerParams.setMargins(0, dp(10), 0, 0); root.addView(tracker, trackerParams);
        setContentView(scroll);
        SystemBars.apply(this, scroll, dark, background);
        root.setAlpha(0f); root.animate().alpha(1f).setDuration(380).start();
    }

    private void row(LinearLayout root, String name, String value) {
        row(root, name, value, text);
    }

    private void row(LinearLayout root, String name, String value, int valueColor) {
        LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(label(name, 13, muted, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(label(value, 14, valueColor, Typeface.BOLD));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8)); root.addView(row, params);
    }

    private void openTracker() {
        String callsign = aircraft.optString("callsign", "");
        String hex = aircraft.optString("hex", "");
        double lat = aircraft.optDouble("lat", Double.NaN), lon = aircraft.optDouble("lon", Double.NaN);
        startActivity(TrackerLinks.selectedIntent(this, callsign, hex, lat, lon));
    }

    private String formatTime(long timestamp) {
        if (timestamp <= 0) return "—";
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setPadding(dp(17), dp(15), dp(17), dp(15));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        v.setBackground(bg); return v; }
    private TextView label(String s, float z, int c, int style) { TextView v = new TextView(this); v.setText(s);
        v.setTextSize(z); v.setTextColor(c); v.setTypeface(Typeface.create("sans-serif", style)); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(BLUE); b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setStateListAnimator(null); b.setBackground(card().getBackground()); return b; }
    private Button neonButton(String s) { Button b = button(s); b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(BLUE);
        bg.setCornerRadius(dp(18)); b.setBackground(bg); b.setElevation(dp(2)); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
