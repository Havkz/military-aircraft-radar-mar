package de.julien.flightradius;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    private static final int GREEN = MARColors.GREEN;
    private static final int BLUE = MARColors.BLUE;
    private SharedPreferences prefs;
    private boolean dark;
    private int background;
    private int surface;
    private int text;
    private int muted;

    private interface ChoiceListener {
        void onChoice(int index);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = AppPreferences.get(this);
        L10n.applyDirection(this);
        dark = AppPreferences.isDark(this);
        background = dark ? MARColors.DARK_BACKGROUND : MARColors.LIGHT_BACKGROUND;
        surface = dark ? MARColors.DARK_SURFACE : MARColors.LIGHT_SURFACE;
        text = dark ? MARColors.DARK_TEXT : MARColors.LIGHT_TEXT;
        muted = dark ? MARColors.DARK_MUTED : MARColors.LIGHT_MUTED;
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

        Button back = button("‹  " + L10n.t(this, "back"));
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView title = label(L10n.t(this, "settings").toUpperCase(), 30, text, Typeface.BOLD);
        title.setPadding(0, dp(18), 0, dp(4));
        root.addView(title);
        TextView subtitle = label(L10n.t(this, "configuration"), 11, GREEN, Typeface.BOLD);
        subtitle.setLetterSpacing(0.14f);
        root.addView(subtitle);

        section(root, L10n.t(this, "appearance"));
        addDropdown(root, L10n.t(this, "theme"), AppPreferences.KEY_THEME,
                new String[]{"oled", "light", "system"},
                new String[]{L10n.t(this, "oled_dark"), L10n.t(this, "light"), L10n.t(this, "system")}, true);
        String[] languageNames = L10n.NAMES.clone();
        languageNames[0] = L10n.t(this, "system");
        addDropdown(root, L10n.t(this, "language"), AppPreferences.KEY_LANGUAGE,
                L10n.CODES, languageNames, true);
        addDropdown(root, L10n.t(this, "units"), AppPreferences.KEY_UNITS,
                new String[]{"aviation", "metric"},
                new String[]{L10n.t(this, "aviation_units"), L10n.t(this, "metric_units")}, false);

        section(root, L10n.t(this, "live_section"));
        addDropdown(root, L10n.t(this, "refresh_rate"), AppPreferences.KEY_REFRESH_SECONDS,
                new int[]{10, 30, 60}, new String[]{"10 s", "30 s", "60 s"});
        addDropdown(root, L10n.t(this, "tracker_tap"),
                AppPreferences.KEY_TRACKER,
                new String[]{"flightradar", "adsbexchange"},
                new String[]{"Flightradar24", "ADS-B Exchange"}, false);
        addSwitch(root, L10n.t(this, "vibration"));

        section(root, L10n.t(this, "information"));
        LinearLayout info = card();
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label("Military Aircraft Radar - MAR", 16, text, Typeface.BOLD));
        TextView version = label("Version " + versionName()
                        + "\nADSB.lol\nFlightradar24 / ADS-B Exchange",
                12, muted, Typeface.NORMAL);
        version.setPadding(0, dp(8), 0, 0);
        version.setLineSpacing(0, 1.3f);
        info.addView(version);
        root.addView(info, cardParams());
        setContentView(scroll);
        SystemBars.apply(this, scroll, dark, background);
    }

    private void addDropdown(LinearLayout root, String title, String key, String[] values,
                             String[] labels, boolean recreateAfter) {
        LinearLayout row = settingRow(title);
        TextView value = (TextView) row.getChildAt(1);
        String current = prefs.getString(key, values[0]);
        value.setText(labels[indexOf(values, current)] + "  ▾");
        row.setOnClickListener(v -> {
            int selected = indexOf(values, prefs.getString(key, values[0]));
            showDropdown(title, labels, selected, choice -> {
                prefs.edit().putString(key, values[choice]).apply();
                value.setText(labels[choice] + "  ▾");
                if (recreateAfter) recreate();
            });
        });
        root.addView(row, cardParams());
    }

    private void addDropdown(LinearLayout root, String title, String key,
                             int[] values, String[] labels) {
        LinearLayout row = settingRow(title);
        TextView value = (TextView) row.getChildAt(1);
        int current = prefs.getInt(key, values[0]);
        value.setText(labels[indexOf(values, current)] + "  ▾");
        row.setOnClickListener(v -> {
            int selected = indexOf(values, prefs.getInt(key, values[0]));
            showDropdown(title, labels, selected, choice -> {
                prefs.edit().putInt(key, values[choice]).apply();
                value.setText(labels[choice] + "  ▾");
            });
        });
        root.addView(row, cardParams());
    }

    private void showDropdown(String title, String[] labels, int selected,
                              ChoiceListener listener) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(14));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(surface);
        panelBackground.setCornerRadius(dp(24));
        panelBackground.setStroke(dp(1), dark
                ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        panel.setBackground(panelBackground);

        TextView heading = label(title.toUpperCase(), 12, text, Typeface.BOLD);
        heading.setLetterSpacing(0.1f);
        heading.setPadding(dp(4), 0, dp(4), dp(12));
        panel.addView(heading);

        for (int i = 0; i < labels.length; i++) {
            final int choice = i;
            boolean active = i == selected;
            TextView option = label((active ? "●  " : "○  ") + labels[i], 15,
                    active ? GREEN : text, active ? Typeface.BOLD : Typeface.NORMAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(15), dp(13), dp(15), dp(13));
            GradientDrawable optionBackground = new GradientDrawable();
            optionBackground.setColor(active
                    ? (dark ? MARColors.DARK_SELECTED : MARColors.LIGHT_SELECTED)
                    : Color.TRANSPARENT);
            optionBackground.setCornerRadius(dp(14));
            option.setBackground(optionBackground);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                listener.onChoice(choice);
            });
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(-1, -2);
            optionParams.setMargins(0, 0, 0, dp(4));
            panel.addView(option, optionParams);
        }

        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(0.58f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        dialog.show();
        if (window != null) window.setLayout(
                getResources().getDisplayMetrics().widthPixels - dp(44), -2);
        panel.setAlpha(0f);
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    private void addSwitch(LinearLayout root, String title) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = label(title, 14, text, Typeface.BOLD);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(prefs.getBoolean(AppPreferences.KEY_VIBRATION, true));
        toggle.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(AppPreferences.KEY_VIBRATION, checked).apply();
            getSystemService(NotificationManager.class).deleteNotificationChannel("military_alerts_v3");
        });
        row.addView(toggle);
        root.addView(row, cardParams());
    }

    private LinearLayout settingRow(String title) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.VERTICAL);
        TextView heading = label(title, 12, muted, Typeface.BOLD);
        heading.setLetterSpacing(0.04f);
        row.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView value = label("", 15, text, Typeface.BOLD);
        value.setPadding(0, dp(7), 0, 0);
        row.addView(value, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void section(LinearLayout root, String title) {
        TextView heading = label(title, 11, muted, Typeface.BOLD);
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
        bg.setStroke(dp(1), dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
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
        button.setText(value); button.setTextColor(BLUE); button.setTextSize(12);
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
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "—";
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
