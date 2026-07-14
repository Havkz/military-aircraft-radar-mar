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

import java.util.Locale;

public class AircraftDetailActivity extends Activity {
    private static final int GREEN = Color.rgb(79, 138, 101);
    private static final int BLUE = Color.rgb(82, 122, 163);
    private static final int ORANGE = Color.rgb(217, 130, 69);
    private boolean dark;
    private int background, surface, text, muted;
    private JSONObject aircraft;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = AppPreferences.isDark(this); L10n.applyDirection(this);
        background = dark ? Color.rgb(0, 4, 8) : Color.rgb(244, 246, 243);
        surface = dark ? Color.rgb(10, 14, 19) : Color.WHITE;
        text = dark ? Color.rgb(228, 232, 235) : Color.rgb(21, 30, 39);
        muted = dark ? Color.rgb(139, 149, 156) : Color.rgb(94, 105, 113);
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
        row(grid, L10n.t(this, "distance"),
                AppPreferences.distance(this, aircraft.optDouble("distance_km", Double.NaN)));
        row(grid, L10n.t(this, "altitude"),
                AppPreferences.altitude(this, aircraft.optDouble("altitude_ft", Double.NaN)));
        row(grid, L10n.t(this, "ground_speed"),
                String.format(Locale.US, "%.0f kt", aircraft.optDouble("speed_knots", 0)));
        row(grid, L10n.t(this, "track"),
                String.format(Locale.US, "%.0f°", aircraft.optDouble("track", 0)));
        row(grid, "Squawk", aircraft.optString("squawk", "—"));
        row(grid, L10n.t(this, "last_signal"),
                String.format(Locale.US, "%.1f s", aircraft.optDouble("seen", 0)));
        row(grid, L10n.t(this, "position"), String.format(Locale.US, "%.5f, %.5f",
                aircraft.optDouble("lat", 0), aircraft.optDouble("lon", 0)));
        row(grid, L10n.t(this, "status"), aircraft.optString("emergency", "none"));

        Button tracker = neonButton(L10n.t(this, "open_in") + TrackerLinks.selectedName(this).toUpperCase(Locale.ROOT));
        tracker.setOnClickListener(v -> openTracker());
        LinearLayout.LayoutParams trackerParams = new LinearLayout.LayoutParams(-1, dp(60));
        trackerParams.setMargins(0, dp(10), 0, 0); root.addView(tracker, trackerParams);
        setContentView(scroll);
        root.setAlpha(0f); root.animate().alpha(1f).setDuration(380).start();
    }

    private void row(LinearLayout root, String name, String value) {
        LinearLayout row = card(); row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(label(name, 13, muted, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(label(value, 14, text, Typeface.BOLD));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8)); root.addView(row, params);
    }

    private void openTracker() {
        String callsign = aircraft.optString("callsign", "");
        String hex = aircraft.optString("hex", "");
        double lat = aircraft.optDouble("lat", Double.NaN), lon = aircraft.optDouble("lon", Double.NaN);
        startActivity(TrackerLinks.selectedIntent(this, callsign, hex, lat, lon));
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setPadding(dp(17), dp(15), dp(17), dp(15));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), dark ? Color.rgb(38, 53, 65) : Color.rgb(208, 215, 211)); v.setBackground(bg); return v; }
    private TextView label(String s, float z, int c, int style) { TextView v = new TextView(this); v.setText(s);
        v.setTextSize(z); v.setTextColor(c); v.setTypeface(Typeface.create("sans-serif", style)); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(BLUE); b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setStateListAnimator(null); b.setBackground(card().getBackground()); return b; }
    private Button neonButton(String s) { Button b = button(s); b.setTextColor(Color.BLACK);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GREEN, BLUE}); bg.setCornerRadius(dp(18)); b.setBackground(bg); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
