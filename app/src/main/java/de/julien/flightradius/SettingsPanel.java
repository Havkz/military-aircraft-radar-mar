package de.julien.flightradius;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

final class SettingsPanel extends ScrollView {
    private final Activity host;
    private final SharedPreferences prefs;
    private final Runnable recreate;
    private final boolean dark;
    private final int surface;
    private final int text;
    private final int muted;

    private interface ChoiceListener { void onChoice(int index); }

    SettingsPanel(Activity host, Runnable recreate) {
        super(host);
        this.host = host;
        this.recreate = recreate;
        prefs = AppPreferences.get(host);
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
        TextView title = label(L10n.t(host, "settings").toUpperCase(),
                29, text, Typeface.BOLD);
        root.addView(title);
        TextView subtitle = label(L10n.t(host, "configuration"),
                11, MARColors.BLUE, Typeface.BOLD);
        subtitle.setLetterSpacing(0.12f);
        subtitle.setPadding(0, dp(5), 0, 0);
        root.addView(subtitle);

        section(root, L10n.t(host, "appearance"));
        addDropdown(root, L10n.t(host, "theme"), AppPreferences.KEY_THEME,
                new String[]{"oled", "light", "system"},
                new String[]{L10n.t(host, "oled_dark"), L10n.t(host, "light"),
                        L10n.t(host, "system")}, true);
        String[] languages = L10n.NAMES.clone();
        languages[0] = L10n.t(host, "system");
        addDropdown(root, L10n.t(host, "language"), AppPreferences.KEY_LANGUAGE,
                L10n.CODES, languages, true);
        addDropdown(root, L10n.t(host, "units"), AppPreferences.KEY_UNITS,
                new String[]{"aviation", "metric"},
                new String[]{L10n.t(host, "aviation_units"), L10n.t(host, "metric_units")}, true);

        section(root, L10n.t(host, "live_section"));
        addDropdown(root, L10n.t(host, "refresh_rate"), AppPreferences.KEY_REFRESH_SECONDS,
                new int[]{10, 30, 60}, new String[]{"10 s", "30 s", "60 s"});
        addDropdown(root, L10n.t(host, "tracker_tap"), AppPreferences.KEY_TRACKER,
                new String[]{"flightradar", "adsbexchange"},
                new String[]{"Flightradar24", "ADS-B Exchange"}, false);
        addSwitch(root, L10n.t(host, "vibration"));

        section(root, L10n.t(host, "information"));
        LinearLayout info = card();
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label("Military Aircraft Radar - MAR", 16, text, Typeface.BOLD));
        TextView version = label("Version " + versionName()
                + "\nADSB.lol\nFlightradar24 / ADS-B Exchange", 12, muted, Typeface.NORMAL);
        version.setPadding(0, dp(8), 0, 0);
        version.setLineSpacing(0, 1.3f);
        info.addView(version);
        root.addView(info, cardParams());
    }

    private void addDropdown(LinearLayout root, String title, String key,
                             String[] values, String[] labels, boolean rebuild) {
        LinearLayout row = settingRow(title);
        TextView selectedValue = (TextView) row.getChildAt(1);
        selectedValue.setText(labels[indexOf(values, prefs.getString(key, values[0]))] + "  ▾");
        row.setOnClickListener(view -> showDropdown(title, labels,
                indexOf(values, prefs.getString(key, values[0])), choice -> {
                    prefs.edit().putString(key, values[choice]).apply();
                    selectedValue.setText(labels[choice] + "  ▾");
                    if (rebuild) recreate.run();
                }));
        root.addView(row, cardParams());
    }

    private void addDropdown(LinearLayout root, String title, String key,
                             int[] values, String[] labels) {
        LinearLayout row = settingRow(title);
        TextView selectedValue = (TextView) row.getChildAt(1);
        selectedValue.setText(labels[indexOf(values, prefs.getInt(key, values[0]))] + "  ▾");
        row.setOnClickListener(view -> showDropdown(title, labels,
                indexOf(values, prefs.getInt(key, values[0])), choice -> {
                    prefs.edit().putInt(key, values[choice]).apply();
                    selectedValue.setText(labels[choice] + "  ▾");
                }));
        root.addView(row, cardParams());
    }

    private void addSwitch(LinearLayout root, String title) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title, 14, text, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(host);
        toggle.setChecked(prefs.getBoolean(AppPreferences.KEY_VIBRATION, true));
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(MARColors.GREEN));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(AppPreferences.KEY_VIBRATION, checked).apply();
            host.getSystemService(NotificationManager.class)
                    .deleteNotificationChannel("military_alerts_v3");
        });
        row.addView(toggle);
        root.addView(row, cardParams());
    }

    private void showDropdown(String title, String[] labels, int selected,
                              ChoiceListener listener) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(24));
        background.setStroke(dp(1), dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        panel.setBackground(background);
        TextView heading = label(title.toUpperCase(), 12, text, Typeface.BOLD);
        heading.setLetterSpacing(0.1f);
        heading.setPadding(dp(4), 0, dp(4), dp(12));
        panel.addView(heading);
        for (int i = 0; i < labels.length; i++) {
            final int choice = i;
            boolean active = i == selected;
            TextView option = label((active ? "●  " : "○  ") + labels[i], 15,
                    text,
                    active ? Typeface.BOLD : Typeface.NORMAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(15), dp(13), dp(15), dp(13));
            GradientDrawable optionBackground = new GradientDrawable();
            optionBackground.setColor(active
                    ? (dark ? MARColors.DARK_SELECTED : MARColors.LIGHT_SELECTED)
                    : Color.TRANSPARENT);
            optionBackground.setCornerRadius(dp(14));
            option.setBackground(optionBackground);
            option.setOnClickListener(view -> {
                dialog.dismiss();
                listener.onChoice(choice);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(4));
            panel.addView(option, params);
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
        panel.setScaleX(0.97f);
        panel.setScaleY(0.97f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();
    }

    private LinearLayout settingRow(String title) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.VERTICAL);
        TextView heading = label(title, 12, muted, Typeface.BOLD);
        row.addView(heading);
        TextView value = label("", 15, text, Typeface.BOLD);
        value.setPadding(0, dp(7), 0, 0);
        row.addView(value);
        return row;
    }

    private void section(LinearLayout root, String title) {
        TextView heading = label(title, 11, MARColors.BLUE, Typeface.BOLD);
        heading.setLetterSpacing(0.12f);
        heading.setPadding(dp(4), dp(24), 0, dp(10));
        root.addView(heading);
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(host);
        view.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        view.setBackground(background);
        return view;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private TextView label(String value, float size, int color, int style) {
        TextView view = new TextView(host);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
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
            return host.getPackageManager().getPackageInfo(host.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "—";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
