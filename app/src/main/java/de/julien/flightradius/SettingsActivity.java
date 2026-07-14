package de.julien.flightradius;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int CYAN = Color.rgb(0, 245, 255);
    private static final int VIOLET = Color.rgb(176, 38, 255);
    private SharedPreferences prefs;
    private boolean de;
    private boolean dark;
    private int background;
    private int surface;
    private int text;
    private int muted;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AppPreferences.get(this);
        de = AppPreferences.isGerman(this);
        dark = AppPreferences.isDark(this);
        background = dark ? Color.BLACK : Color.rgb(239, 247, 250);
        surface = dark ? Color.rgb(5, 10, 15) : Color.WHITE;
        text = dark ? Color.rgb(230, 250, 255) : Color.rgb(7, 25, 34);
        muted = dark ? Color.rgb(119, 153, 164) : Color.rgb(70, 96, 107);
        getWindow().setStatusBarColor(background);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        Button back = button("‹  " + (de ? "ZURÜCK" : "BACK"));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = label(de ? "EINSTELLUNGEN" : "SETTINGS", 30, text, Typeface.BOLD);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = label("MAR // SYSTEM CONFIGURATION", 11, CYAN, Typeface.BOLD);
        subtitle.setLetterSpacing(0.14f);
        root.addView(subtitle);

        section(root, de ? "DARSTELLUNG" : "APPEARANCE");
        addCycle(root, de ? "Farbschema" : "Theme", AppPreferences.KEY_THEME,
                new String[]{"oled", "light", "system"},
                de ? new String[]{"OLED Dunkel", "Hell", "System"} : new String[]{"OLED Dark", "Light", "System"}, true);
        addCycle(root, de ? "Sprache" : "Language", AppPreferences.KEY_LANGUAGE,
                new String[]{"system", "de", "en"},
                new String[]{"System", "Deutsch", "English"}, true);
        addCycle(root, de ? "Einheiten" : "Units", AppPreferences.KEY_UNITS,
                new String[]{"aviation", "metric"},
                de ? new String[]{"Luftfahrt (NM / ft)", "Metrisch (km / m)"}
                        : new String[]{"Aviation (NM / ft)", "Metric (km / m)"}, false);

        section(root, de ? "LIVE-RADAR" : "LIVE RADAR");
        addCycle(root, de ? "Aktualisierung" : "Refresh rate", AppPreferences.KEY_REFRESH_SECONDS,
                new int[]{10, 30, 60}, new String[]{"10 s", "30 s", "60 s"});
        addCycle(root, de ? "Tracker beim Antippen" : "Tracker on notification tap",
                AppPreferences.KEY_TRACKER,
                new String[]{"flightradar", "adsbexchange"},
                new String[]{"Flightradar24", "ADS-B Exchange"}, false);
        addSwitch(root, de ? "Vibration bei neuem Kontakt" : "Vibrate for new contact");

        section(root, de ? "INFORMATION" : "INFORMATION");
        LinearLayout info = card();
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label("Military Aircraft Radar - MAR", 16, text, Typeface.BOLD));
        TextView version = label("Version 3.0\nLive data: ADSB.lol\nVisual tracking: Flightradar24 / ADS-B Exchange",
                12, muted, Typeface.NORMAL);
        version.setPadding(0, dp(8), 0, 0);
        version.setLineSpacing(0, 1.3f);
        info.addView(version);
        root.addView(info, cardParams());
        setContentView(scroll);
    }

    private void addCycle(LinearLayout root, String title, String key, String[] values,
                          String[] labels, boolean recreateAfter) {
        LinearLayout row = settingRow(title);
        TextView value = (TextView) row.getChildAt(1);
        String current = prefs.getString(key, values[0]);
        value.setText(labels[indexOf(values, current)] + "  ›");
        row.setOnClickListener(v -> {
            int next = (indexOf(values, prefs.getString(key, values[0])) + 1) % values.length;
            prefs.edit().putString(key, values[next]).apply();
            value.setText(labels[next] + "  ›");
            if (recreateAfter) recreate();
        });
        root.addView(row, cardParams());
    }

    private void addCycle(LinearLayout root, String title, String key, int[] values, String[] labels) {
        LinearLayout row = settingRow(title);
        TextView value = (TextView) row.getChildAt(1);
        int current = prefs.getInt(key, values[0]);
        value.setText(labels[indexOf(values, current)] + "  ›");
        row.setOnClickListener(v -> {
            int next = (indexOf(values, prefs.getInt(key, values[0])) + 1) % values.length;
            prefs.edit().putInt(key, values[next]).apply();
            value.setText(labels[next] + "  ›");
        });
        root.addView(row, cardParams());
    }

    private void addSwitch(LinearLayout root, String title) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 14, text, Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(AppPreferences.KEY_VIBRATION, true));
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(AppPreferences.KEY_VIBRATION, checked).apply();
            getSystemService(NotificationManager.class).deleteNotificationChannel("military_alerts_v3");
        });
        row.addView(toggle);
        root.addView(row, cardParams());
    }

    private LinearLayout settingRow(String title) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title, 14, text, Typeface.BOLD), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(label("", 13, CYAN, Typeface.BOLD));
        return row;
    }

    private void section(LinearLayout root, String title) {
        TextView heading = label(title, 11, VIOLET, Typeface.BOLD);
        heading.setLetterSpacing(0.13f);
        heading.setPadding(dp(4), dp(24), 0, dp(10));
        root.addView(heading);
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surface);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), dark ? Color.rgb(0, 71, 80) : Color.rgb(191, 221, 228));
        view.setBackground(bg);
        return view;
    }

    private TextView label(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value); button.setTextColor(CYAN); button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT_BOLD); button.setAllCaps(false);
        button.setBackground(card().getBackground()); button.setStateListAnimator(null);
        return button;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10)); return params;
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return 0;
    }
    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
