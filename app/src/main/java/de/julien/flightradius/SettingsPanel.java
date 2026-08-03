package de.julien.flightradius;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.Window;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
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
                new int[]{1}, new String[]{"ADSB.lol: 1 s"});
        addDropdown(root, L10n.t(host, "tracker_tap"), AppPreferences.KEY_TRACKER,
                new String[]{"flightradar", "adsbexchange"},
                new String[]{"Flightradar24", "ADS-B Exchange"}, false);
        addTrackerHint(root);
        addAdsbExchangeKey(root);
        addAirplanesRate(root);
        addSwitch(root, L10n.t(host, "vibration"));

        addMapSettings(root);

        section(root, L10n.t(host, "information"));
        LinearLayout info = card();
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label("Military Aircraft Radar - MAR", 16, text, Typeface.BOLD));
        TextView version = label("Version " + versionName()
                + "\nADSB.lol + Airplanes.live\nADS-B Exchange (optional API key)",
                12, muted, Typeface.NORMAL);
        version.setPadding(0, dp(8), 0, 0);
        version.setLineSpacing(0, 1.3f);
        info.addView(version);
        root.addView(info, cardParams());
        addLegal(root);
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

    private void addAdsbExchangeKey(LinearLayout root) {
        LinearLayout row = settingRow(MapL10n.t(host, "adsbx_key"));
        TextView value = (TextView) row.getChildAt(1);
        value.setText(ProviderCredentials.hasAdsbExchangeKey(host)
                ? MapL10n.t(host, "configured") + "  ✓"
                : MapL10n.t(host, "not_configured") + "  ›");
        row.setOnClickListener(view -> {
            EditText input = new EditText(host);
            input.setSingleLine(true);
            input.setHint(MapL10n.t(host, "adsbx_key"));
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setText(ProviderCredentials.adsbExchangeKey(host));
            input.setSelectAllOnFocus(true);
            AlertDialog dialog = new AlertDialog.Builder(host)
                    .setTitle(MapL10n.t(host, "adsbx_key"))
                    .setMessage(MapL10n.t(host, "key_private"))
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(MapL10n.t(host, "remove"), null)
                    .setPositiveButton(MapL10n.t(host, "save"), null)
                    .create();
            dialog.setOnShowListener(ignored -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    if (storeAdsbExchangeKey(input.getText().toString(), value)) dialog.dismiss();
                });
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button -> {
                    if (storeAdsbExchangeKey("", value)) dialog.dismiss();
                });
            });
            dialog.show();
        });
        root.addView(row, cardParams());
    }

    private void addAirplanesRate(LinearLayout root) {
        LinearLayout row = settingRow(MapL10n.t(host, "airplanes_rate"));
        TextView value = (TextView) row.getChildAt(1);
        Runnable refreshValue = () -> value.setText(prefs.getBoolean(
                AppPreferences.KEY_AIRPLANES_BUSINESS_RATE, false)
                ? MapL10n.t(host, "business_rate") + "  ✓"
                : MapL10n.t(host, "free_rate") + "  ›");
        refreshValue.run();
        row.setOnClickListener(view -> {
            if (prefs.getBoolean(AppPreferences.KEY_AIRPLANES_BUSINESS_RATE, false)) {
                setAirplanesBusinessRate(false);
                refreshValue.run();
                return;
            }
            new AlertDialog.Builder(host)
                    .setTitle(MapL10n.t(host, "business_rate"))
                    .setMessage(MapL10n.t(host, "business_rate_warning"))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(MapL10n.t(host, "enable"), (dialog, which) -> {
                        setAirplanesBusinessRate(true);
                        refreshValue.run();
                    })
                    .show();
        });
        root.addView(row, cardParams());
    }

    private void setAirplanesBusinessRate(boolean enabled) {
        prefs.edit().putBoolean(AppPreferences.KEY_AIRPLANES_BUSINESS_RATE, enabled).apply();
        if (prefs.getBoolean(AppPreferences.KEY_RUNNING, false)) {
            host.startService(new Intent(host, MonitorService.class)
                    .setAction(MonitorService.ACTION_SOURCES_CHANGED));
        }
    }

    private void addMapSettings(LinearLayout root) {
        section(root, MapL10n.t(host, "general"));
        addScale(root, MapL10n.t(host, "text_size"),
                MapPreferences.TEXT_SCALE, 75, 150, 100);
        addScale(root, MapL10n.t(host, "icon_size"),
                MapPreferences.ICON_SCALE, 70, 180, 100);

        section(root, MapL10n.t(host, "map_appearance"));
        addMapSwitch(root, MapL10n.t(host, "dark_map"),
                MapPreferences.DARK, false);
        addMapSwitch(root, MapL10n.t(host, "darker_colors"),
                MapPreferences.DARKER, false);
        addMapSwitch(root, MapL10n.t(host, "dim_map"),
                MapPreferences.DIM, false);
        addMapSwitch(root, MapL10n.t(host, "colored_aircraft"),
                MapPreferences.COLORED_PLANES, true);
        addMapSwitch(root, MapL10n.t(host, "colored_tracks"),
                MapPreferences.COLORED_TRAILS, true);
        addMapSwitch(root, MapL10n.t(host, "hardware_tracks"),
                MapPreferences.HARDWARE_TRACKS, true);

        section(root, MapL10n.t(host, "labels_altitude"));
        addMapSwitch(root, MapL10n.t(host, "show_labels"),
                MapPreferences.SHOW_LABELS, false);
        addMapSwitch(root, MapL10n.t(host, "label_units"),
                MapPreferences.LABEL_UNITS, true);
        addMapSwitch(root, MapL10n.t(host, "smaller_labels"),
                MapPreferences.SMALL_LABELS, true);
        addMapSwitch(root, MapL10n.t(host, "smaller_wind_labels"),
                MapPreferences.SMALL_WIND_LABELS, true);
        addMapSwitch(root, MapL10n.t(host, "geometric_alt"),
                MapPreferences.GEOMETRIC_ALTITUDE, false);
        addMapSwitch(root, MapL10n.t(host, "egm_conversion"),
                MapPreferences.EGM_CONVERSION, false);
        addMapSwitch(root, MapL10n.t(host, "qnh_correct"),
                MapPreferences.QNH_CORRECTION, false);
        addMapSwitch(root, MapL10n.t(host, "live_track_utc"),
                MapPreferences.LIVE_TRACK_UTC, false);
        addMapSwitch(root, MapL10n.t(host, "historic_track_utc"),
                MapPreferences.HISTORIC_TRACK_UTC, true);

        section(root, MapL10n.t(host, "tracks_history"));
        addMapSwitch(root, MapL10n.t(host, "last_leg"),
                MapPreferences.LAST_LEG_ONLY, true);
        addMapSwitch(root, MapL10n.t(host, "altitude_chart"),
                MapPreferences.ALTITUDE_CHART, true);
        addMapSwitch(root, MapL10n.t(host, "keep_faded"),
                MapPreferences.KEEP_FADED, false);

        section(root, MapL10n.t(host, "info_panel"));
        addMapSwitch(root, MapL10n.t(host, "enable_info"),
                MapPreferences.INFOBLOCK, true);
        addMapSwitch(root, MapL10n.t(host, "wide_info"),
                MapPreferences.WIDE_INFOBLOCK, false);
        addMapSwitch(root, MapL10n.t(host, "hover_info"),
                MapPreferences.HOVER_INFOBLOCK, false);
        addMapSwitch(root, MapL10n.t(host, "auto_select"),
                MapPreferences.AUTO_SELECT, false);
        addMapSwitch(root, MapL10n.t(host, "pictures_planespotters"),
                MapPreferences.PICTURES_PLANESPOTTERS, true);
        addMapSwitch(root, MapL10n.t(host, "pictures_planespotting"),
                MapPreferences.PICTURES_PLANESPOTTING, false);

        section(root, MapL10n.t(host, "traffic_privacy"));
        addMapSwitch(root, MapL10n.t(host, "ground_vehicles"),
                MapPreferences.GROUND_VEHICLES, true);
        addMapSwitch(root, MapL10n.t(host, "non_icao"),
                MapPreferences.NON_ICAO, true);
        addMapSwitch(root, MapL10n.t(host, "update_gps"),
                MapPreferences.UPDATE_GPS, true);
        addMapSwitch(root, MapL10n.t(host, "include_filters_url"),
                MapPreferences.INCLUDE_FILTERS_URL, false);

        section(root, MapL10n.t(host, "advanced"));
        addMapSwitch(root, MapL10n.t(host, "debug_tracks"),
                MapPreferences.DEBUG_TRACKS, false);
        addMapSwitch(root, MapL10n.t(host, "bypass_filters"),
                MapPreferences.DEBUG_SHOW_ALL, false);
        TextView reset = label(MapL10n.t(host, "reset_all").toUpperCase(),
                13, MARColors.ORANGE, Typeface.BOLD);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(14), dp(17), dp(14), dp(17));
        reset.setBackground(card().getBackground());
        reset.setOnClickListener(view -> {
            MapPreferences.reset(host);
            recreate.run();
        });
        root.addView(reset, cardParams());
    }

    private void addMapSwitch(LinearLayout root, String title, String key,
                              boolean defaultValue) {
        LinearLayout row = card();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title, 14, text, Typeface.BOLD),
                new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(host);
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(MARColors.BLUE));
        toggle.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(key, checked).apply());
        row.addView(toggle);
        root.addView(row, cardParams());
    }

    private void addScale(LinearLayout root, String title, String key,
                          int minimum, int maximum, int defaultValue) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.VERTICAL);
        TextView value = label(title, 14, text, Typeface.BOLD);
        row.addView(value);
        SeekBar seek = new SeekBar(host);
        seek.setMax(maximum - minimum);
        seek.setProgress(Math.round(prefs.getFloat(key, defaultValue / 100f) * 100) - minimum);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(MARColors.BLUE));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int percent = progress + minimum;
                value.setText(title + "  ·  " + percent + "%");
                if (fromUser) prefs.edit().putFloat(key, percent / 100f).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        value.setText(title + "  ·  " + (seek.getProgress() + minimum) + "%");
        row.addView(seek, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(row, cardParams());
    }


    private boolean storeAdsbExchangeKey(String key, TextView value) {
        try {
            ProviderCredentials.setAdsbExchangeKey(host, key);
            value.setText(ProviderCredentials.hasAdsbExchangeKey(host)
                    ? MapL10n.t(host, "configured") + "  ✓"
                    : MapL10n.t(host, "not_configured") + "  ›");
            if (prefs.getBoolean(AppPreferences.KEY_RUNNING, false)) {
                host.startService(new Intent(host, MonitorService.class)
                        .setAction(MonitorService.ACTION_SOURCES_CHANGED));
            }
            return true;
        } catch (Exception error) {
            android.widget.Toast.makeText(host, MapL10n.t(host, "key_error"),
                    android.widget.Toast.LENGTH_LONG).show();
            return false;
        }
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

    private void addTrackerHint(LinearLayout root) {
        TextView hint = label("ⓘ  " + L10n.t(host, "adsb_recommended"),
                11, MARColors.BLUE, Typeface.NORMAL);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(dp(8), 0, dp(8), dp(12));
        root.addView(hint);
    }

    private void addLegal(LinearLayout root) {
        section(root, L10n.t(host, "legal"));
        LinearLayout legal = card();
        legal.setOrientation(LinearLayout.VERTICAL);
        TextView data = label(L10n.t(host, "legal_data"), 12, text, Typeface.NORMAL);
        data.setLineSpacing(0, 1.25f);
        legal.addView(data);
        String providerNotice = MapL10n.t(host, "provider_notice");
        TextView trackers = label(providerNotice,
                12, muted, Typeface.NORMAL);
        trackers.setLineSpacing(0, 1.25f);
        trackers.setPadding(0, dp(10), 0, dp(6));
        legal.addView(trackers);
        addLegalLink(legal, "ADSB.lol API / ODbL 1.0",
                "https://www.adsb.lol/docs/open-data/api/");
        addLegalLink(legal, "Airplanes.live API",
                "https://airplanes.live/api-guide/");
        addLegalLink(legal, "Airplanes.live Terms of Use",
                "https://airplanes.live/terms-of-use/");
        addLegalLink(legal, "OpenStreetMap tile policy",
                "https://operations.osmfoundation.org/policies/tiles/");
        addLegalLink(legal, "Leaflet BSD 2-Clause License",
                "https://github.com/Leaflet/Leaflet/blob/main/LICENSE");
        addLegalLink(legal, "Planespotters.net",
                "https://www.planespotters.net/");
        addLegalLink(legal, "Planespotting.be",
                "https://www.planespotting.be/");
        addLegalLink(legal, "Flightradar24 Terms",
                "https://www.flightradar24.com/terms-of-service");
        addLegalLink(legal, "ADS-B Exchange Terms",
                "https://www.jetnet.com/legal/terms-of-use");
        root.addView(legal, cardParams());
    }

    private void addLegalLink(LinearLayout root, String title, String url) {
        TextView link = label(title + "  ↗", 12, MARColors.BLUE, Typeface.BOLD);
        link.setPadding(0, dp(8), 0, dp(4));
        link.setOnClickListener(view -> host.startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        root.addView(link);
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
