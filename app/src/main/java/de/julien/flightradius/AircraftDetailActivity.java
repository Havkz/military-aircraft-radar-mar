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
    private static final int CYAN = Color.rgb(0, 245, 255);
    private static final int VIOLET = Color.rgb(176, 38, 255);
    private boolean dark, de;
    private int background, surface, text, muted;
    private JSONObject aircraft;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dark = AppPreferences.isDark(this); de = AppPreferences.isGerman(this);
        background = dark ? Color.BLACK : Color.rgb(239, 247, 250);
        surface = dark ? Color.rgb(5, 10, 15) : Color.WHITE;
        text = dark ? Color.rgb(230, 250, 255) : Color.rgb(7, 25, 34);
        muted = dark ? Color.rgb(119, 153, 164) : Color.rgb(70, 96, 107);
        try { aircraft = new JSONObject(getIntent().getStringExtra("aircraft")); }
        catch (Exception e) { aircraft = new JSONObject(); }
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(32)); scroll.addView(root);
        Button back = button("‹  " + (de ? "FLUGZEUGE" : "AIRCRAFT")); back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));

        RadarView hero = new RadarView(this); hero.setDarkMode(dark);
        hero.setScanning(AppPreferences.get(this).getBoolean(AppPreferences.KEY_RUNNING, false));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(190));
        heroParams.setMargins(0, dp(14), 0, 0); root.addView(hero, heroParams);

        String callsign = aircraft.optString("callsign", "");
        TextView title = label(callsign.isEmpty() ? (de ? "OHNE CALLSIGN" : "NO CALLSIGN") : callsign,
                32, CYAN, Typeface.BOLD); title.setPadding(0, dp(12), 0, 0); root.addView(title);
        TextView id = label(aircraft.optString("type", "—") + "  //  "
                + aircraft.optString("registration", "—") + "  //  "
                + aircraft.optString("hex", "—").toUpperCase(Locale.ROOT), 12, VIOLET, Typeface.BOLD);
        id.setLetterSpacing(0.07f); root.addView(id);

        LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(18), 0, 0); root.addView(grid);
        row(grid, de ? "Entfernung" : "Distance",
                AppPreferences.distance(this, aircraft.optDouble("distance_km", Double.NaN)));
        row(grid, de ? "Höhe" : "Altitude",
                AppPreferences.altitude(this, aircraft.optDouble("altitude_ft", Double.NaN)));
        row(grid, de ? "Geschwindigkeit" : "Ground speed",
                String.format(Locale.US, "%.0f kt", aircraft.optDouble("speed_knots", 0)));
        row(grid, de ? "Kurs" : "Track",
                String.format(Locale.US, "%.0f°", aircraft.optDouble("track", 0)));
        row(grid, "Squawk", aircraft.optString("squawk", "—"));
        row(grid, de ? "Letztes Signal" : "Last signal",
                String.format(Locale.US, "%.1f s", aircraft.optDouble("seen", 0)));
        row(grid, de ? "Position" : "Position", String.format(Locale.US, "%.5f, %.5f",
                aircraft.optDouble("lat", 0), aircraft.optDouble("lon", 0)));
        row(grid, de ? "Status" : "Status", aircraft.optString("emergency", "none"));

        Button tracker = neonButton((de ? "IN " : "OPEN IN ") + TrackerLinks.selectedName(this).toUpperCase(Locale.ROOT));
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
        String hex = aircraft.optString("hex", "");
        double lat = aircraft.optDouble("lat", Double.NaN), lon = aircraft.optDouble("lon", Double.NaN);
        startActivity(new Intent(Intent.ACTION_VIEW, TrackerLinks.selected(this, hex, lat, lon)));
    }

    private LinearLayout card() { LinearLayout v = new LinearLayout(this); v.setPadding(dp(17), dp(15), dp(17), dp(15));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(surface); bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), dark ? Color.rgb(0, 72, 82) : Color.rgb(190, 220, 227)); v.setBackground(bg); return v; }
    private TextView label(String s, float z, int c, int style) { TextView v = new TextView(this); v.setText(s);
        v.setTextSize(z); v.setTextColor(c); v.setTypeface(Typeface.create("sans-serif", style)); return v; }
    private Button button(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(CYAN); b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD); b.setAllCaps(false); b.setStateListAnimator(null); b.setBackground(card().getBackground()); return b; }
    private Button neonButton(String s) { Button b = button(s); b.setTextColor(Color.BLACK);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{CYAN, Color.rgb(0, 155, 181)}); bg.setCornerRadius(dp(18)); b.setBackground(bg); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
