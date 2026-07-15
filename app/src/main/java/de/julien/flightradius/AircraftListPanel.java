package de.julien.flightradius;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AircraftListPanel extends ScrollView {
    private final Activity host;
    private final LinearLayout contacts;
    private final boolean dark;
    private final int surface;
    private final int text;
    private final int muted;
    private String lastJson = "";

    AircraftListPanel(Activity host) {
        super(host);
        this.host = host;
        dark = AppPreferences.isDark(host);
        int background = dark ? MARColors.DARK_BACKGROUND : MARColors.LIGHT_BACKGROUND;
        surface = dark ? MARColors.DARK_SURFACE : MARColors.LIGHT_SURFACE;
        text = dark ? MARColors.DARK_TEXT : MARColors.LIGHT_TEXT;
        muted = dark ? MARColors.DARK_MUTED : MARColors.LIGHT_MUTED;
        setFillViewport(true);
        setBackgroundColor(background);

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(28));
        addView(root, new LayoutParams(-1, -2));

        TextView title = label(L10n.t(host, "session_aircraft"), 29, text, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = label(L10n.t(host, "since_app_start"),
                11, MARColors.GREEN, Typeface.BOLD);
        subtitle.setLetterSpacing(0.1f);
        subtitle.setPadding(0, dp(5), 0, 0);
        root.addView(subtitle);
        contacts = new LinearLayout(host);
        contacts.setOrientation(LinearLayout.VERTICAL);
        contacts.setPadding(0, dp(18), 0, 0);
        root.addView(contacts);
        refresh();
    }

    void refresh() {
        String json = AppPreferences.get(host).getString(
                AppPreferences.KEY_AIRCRAFT_HISTORY_JSON, "[]");
        if (json.equals(lastJson)) return;
        lastJson = json;
        contacts.removeAllViews();
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                TextView empty = label(L10n.t(host, "empty_list"), 15, muted, Typeface.NORMAL);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(70), 0, 0);
                contacts.addView(empty);
                return;
            }
            for (int i = 0; i < array.length(); i++) addAircraft(array.getJSONObject(i), i);
        } catch (Exception ignored) { }
    }

    private void addAircraft(JSONObject plane, int index) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        String callsign = plane.optString("callsign", "");
        card.addView(label(callsign.isEmpty() ? L10n.t(host, "no_callsign") : callsign,
                21, text, Typeface.BOLD));
        card.addView(label(plane.optString("type", "—") + "  •  "
                + plane.optString("registration", "—"), 12, muted, Typeface.BOLD));
        boolean inRange = plane.optBoolean("in_range", false);
        double distance = plane.optDouble("distance_km", Double.NaN);
        double altitude = plane.optDouble("altitude_ft", Double.NaN);
        String state = inRange ? AppPreferences.distance(host, distance)
                : L10n.t(host, "out_of_range");
        TextView metrics = label(state + "   //   " + AppPreferences.altitude(host, altitude),
                15, inRange ? text : MARColors.RED, Typeface.BOLD);
        metrics.setPadding(0, dp(10), 0, 0);
        card.addView(metrics);
        TextView time = label(L10n.t(host, "in_range_time") + "  "
                        + formatTime(plane.optLong("first_seen_ms", 0L)) + " – "
                        + formatTime(plane.optLong("last_seen_ms", 0L)),
                12, muted, Typeface.NORMAL);
        time.setPadding(0, dp(7), 0, 0);
        card.addView(time);
        card.setOnClickListener(view -> host.startActivity(
                new Intent(host, AircraftDetailActivity.class)
                        .putExtra("aircraft", plane.toString())));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(11));
        contacts.addView(card, params);
        card.setAlpha(0f);
        card.setTranslationY(dp(14));
        card.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(index, 8) * 45L).setDuration(260).start();
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(host);
        view.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        view.setBackground(background);
        return view;
    }

    private TextView label(String value, float size, int color, int style) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private String formatTime(long timestamp) {
        return timestamp <= 0 ? "—" : new SimpleDateFormat(
                "HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
