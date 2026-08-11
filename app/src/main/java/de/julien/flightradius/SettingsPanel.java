package de.julien.flightradius;

import android.app.Activity;
import android.app.Dialog;
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
        addDropdown(root, L10n.t(host, "units"), AppPreferences.KEY_UNITS,
                new String[]{"aviation", "metric"},
                new String[]{L10n.t(host, "aviation_units"), L10n.t(host, "metric_units")}, true);

        section(root, L10n.t(host, "live_section"));
        addCustomAlerts(root);
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

    void openCustomAlerts() {
        showAlertRules(() -> { });
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
        styleSwitch(toggle, MARColors.GREEN);
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
        row.setOnClickListener(view -> showApiKeyEditor(value));
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
            showConfirmation(MapL10n.t(host, "business_rate"),
                    MapL10n.t(host, "business_rate_warning"),
                    MapL10n.t(host, "enable"), () -> {
                        setAirplanesBusinessRate(true);
                        refreshValue.run();
                    });
        });
        root.addView(row, cardParams());
    }

    private void addCustomAlerts(LinearLayout root) {
        LinearLayout row = settingRow(MapL10n.t(host, "custom_alerts"));
        TextView value = (TextView) row.getChildAt(1);
        Runnable refresh = () -> {
            int count = CustomAlertRules.customSquawks(host).size();
            String label = count == 0 ? MapL10n.t(host, "not_configured")
                    : count + (count == 1 ? " squawk code" : " squawk codes");
            value.setText(label + "  ›");
        };
        refresh.run();
        row.setOnClickListener(view -> showAlertRules(refresh));
        root.addView(row, cardParams());
    }

    private void showAlertRules(Runnable refresh) {
        java.util.List<String> squawks = CustomAlertRules.customSquawks(host);
        String[] entries = new String[squawks.size() + 1];
        entries[0] = "＋  " + MapL10n.t(host, "add_squawk");
        for (int i = 0; i < squawks.size(); i++) {
            entries[i + 1] = "×  Squawk " + squawks.get(i);
        }
        showDropdown(MapL10n.t(host, "custom_alerts"), entries, -1, which -> {
                    if (which == 0) editCustomSquawk(refresh);
                    else {
                        String value = squawks.get(which - 1);
                        showConfirmation(MapL10n.t(host, "remove_squawk"),
                                "Remove Squawk " + value + " notification?",
                                MapL10n.t(host, "remove"), () -> {
                                    CustomAlertRules.removeCustomSquawk(host, value);
                                    saveSquawkAlertSettings(refresh);
                                });
                    }
                });
    }

    private void editCustomSquawk(Runnable refresh) {
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        form.setPadding(pad, dp(4), pad, 0);
        EditText code = ruleField(form, MapL10n.t(host, "squawk_code"),
                "", true);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        code.setKeyListener(android.text.method.DigitsKeyListener.getInstance("01234567"));
        Dialog alert = new Dialog(host);
        LinearLayout panel = modalPanel(MapL10n.t(host, "custom_squawk"));
        panel.addView(form, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = new LinearLayout(host);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(8), 0, 0);
        TextView cancel = modalButton(host.getString(android.R.string.cancel), false);
        TextView save = modalButton(MapL10n.t(host, "save"), true);
        actions.addView(cancel);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-2, dp(46));
        saveParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(save, saveParams);
        panel.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        alert.setContentView(panel);
        cancel.setOnClickListener(button -> dismissAnimated(alert, panel));
        save.setOnClickListener(button -> {
                    try {
                        String value = code.getText().toString().trim();
                        if (!CustomAlertRules.addCustomSquawk(host, value)) {
                            android.widget.Toast.makeText(host,
                                    MapL10n.t(host, "squawk_exists"),
                                    android.widget.Toast.LENGTH_LONG).show();
                            return;
                        }
                        saveSquawkAlertSettings(refresh);
                        dismissAnimated(alert, panel);
                    } catch (Exception error) {
                        android.widget.Toast.makeText(host,
                                MapL10n.t(host, "invalid_rule"),
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
        showModal(alert, panel);
    }

    private EditText ruleField(LinearLayout form, String hint, String value,
                               boolean number) {
        EditText input = new EditText(host);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT);
        input.setTextColor(text);
        input.setHintTextColor(muted);
        input.setTextSize(14);
        input.setPadding(dp(15), dp(10), dp(15), dp(10));
        input.setBackground(fieldBackground(false));
        input.setSelectAllOnFocus(true);
        input.setOnFocusChangeListener((view, focused) ->
                input.setBackground(fieldBackground(focused)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.setMargins(0, 0, 0, dp(10));
        form.addView(input, params);
        return input;
    }

    private void saveSquawkAlertSettings(Runnable refresh) {
        prefs.edit()
                .remove(AppPreferences.KEY_SQUAWK_ADSB_LOL_LAST_ATTEMPT_MS)
                .remove(AppPreferences.KEY_SQUAWK_AIRPLANES_LAST_ATTEMPT_MS)
                .remove(AppPreferences.KEY_SQUAWK_ADSBX_LAST_ATTEMPT_MS)
                .apply();
        refresh.run();
        if (prefs.getBoolean(AppPreferences.KEY_RUNNING, false)) {
            host.startService(new Intent(host, MonitorService.class)
                    .setAction(MonitorService.ACTION_SOURCES_CHANGED));
        }
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
        addMapSwitch(root, MapL10n.t(host, "dim_map"),
                MapPreferences.DIM, true);
        addMapSwitch(root, MapL10n.t(host, "colored_aircraft"),
                MapPreferences.COLORED_PLANES, true);
        addMapSwitch(root, MapL10n.t(host, "colored_tracks"),
                MapPreferences.COLORED_TRAILS, true);

        section(root, MapL10n.t(host, "labels_altitude"));
        addMapSwitch(root, MapL10n.t(host, "show_labels"),
                MapPreferences.SHOW_LABELS, true);
        addScale(root, MapL10n.t(host, "label_transparency"),
                MapPreferences.LABEL_TRANSPARENCY, 0, 95, 60);
        addMapSwitch(root, MapL10n.t(host, "label_units"),
                MapPreferences.LABEL_UNITS, true);
        addMapSwitch(root, MapL10n.t(host, "smaller_labels"),
                MapPreferences.SMALL_LABELS, true);
        addMapSwitch(root, MapL10n.t(host, "geometric_alt"),
                MapPreferences.GEOMETRIC_ALTITUDE, false);
        addMapSwitch(root, MapL10n.t(host, "egm_conversion"),
                MapPreferences.EGM_CONVERSION, false);
        addMapSwitch(root, MapL10n.t(host, "qnh_correct"),
                MapPreferences.QNH_CORRECTION, false);
        section(root, MapL10n.t(host, "tracks_history"));
        addMapSwitch(root, MapL10n.t(host, "keep_faded"),
                MapPreferences.KEEP_FADED, false);

        section(root, MapL10n.t(host, "info_panel"));
        addMapSwitch(root, MapL10n.t(host, "auto_select"),
                MapPreferences.AUTO_SELECT, false);

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
        addMapSwitch(root, MapL10n.t(host, "debug_track_timestamps"),
                MapPreferences.DEBUG_TRACK_TIMESTAMPS, false);
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
        styleSwitch(toggle, MARColors.BLUE);
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

    private void styleSwitch(Switch toggle, int accent) {
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{accent, muted}));
        int activeTrack = Color.argb(105, Color.red(accent), Color.green(accent),
                Color.blue(accent));
        int inactiveTrack = dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER;
        toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{activeTrack, inactiveTrack}));
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

    private LinearLayout modalPanel(String title) {
        LinearLayout panel = new LinearLayout(host);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(17), dp(18), dp(15));
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        panel.setBackground(background);

        LinearLayout header = new LinearLayout(host);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView accent = label("", 1, MARColors.GREEN, Typeface.NORMAL);
        GradientDrawable accentBackground = new GradientDrawable();
        accentBackground.setColor(MARColors.GREEN);
        accentBackground.setCornerRadius(dp(4));
        accent.setBackground(accentBackground);
        header.addView(accent, new LinearLayout.LayoutParams(dp(5), dp(22)));
        TextView heading = label(title.toUpperCase(), 12, text, Typeface.BOLD);
        heading.setLetterSpacing(0.1f);
        heading.setPadding(dp(11), 0, dp(4), 0);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));
        return panel;
    }

    private void showApiKeyEditor(TextView value) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = modalPanel(MapL10n.t(host, "adsbx_key"));
        TextView note = label(MapL10n.t(host, "key_private"), 12, muted, Typeface.NORMAL);
        note.setLineSpacing(0, 1.2f);
        note.setPadding(dp(2), 0, dp(2), dp(13));
        panel.addView(note);
        LinearLayout form = new LinearLayout(host);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText input = ruleField(form, MapL10n.t(host, "adsbx_key"),
                ProviderCredentials.adsbExchangeKey(host), false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(form);
        LinearLayout actions = new LinearLayout(host);
        actions.setGravity(Gravity.END);
        TextView remove = modalButton(MapL10n.t(host, "remove"), false);
        TextView cancel = modalButton(host.getString(android.R.string.cancel), false);
        TextView save = modalButton(MapL10n.t(host, "save"), true);
        actions.addView(remove);
        actions.addView(cancel);
        actions.addView(save);
        panel.addView(actions);
        dialog.setContentView(panel);
        remove.setOnClickListener(button -> {
            if (storeAdsbExchangeKey("", value)) dismissAnimated(dialog, panel);
        });
        cancel.setOnClickListener(button -> dismissAnimated(dialog, panel));
        save.setOnClickListener(button -> {
            if (storeAdsbExchangeKey(input.getText().toString(), value)) {
                dismissAnimated(dialog, panel);
            }
        });
        showModal(dialog, panel);
    }

    private void showConfirmation(String title, String message,
                                  String positive, Runnable confirmed) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = modalPanel(title);
        TextView body = label(message, 13, muted, Typeface.NORMAL);
        body.setLineSpacing(0, 1.25f);
        body.setPadding(dp(2), dp(2), dp(2), dp(18));
        panel.addView(body);
        LinearLayout actions = new LinearLayout(host);
        actions.setGravity(Gravity.END);
        TextView cancel = modalButton(host.getString(android.R.string.cancel), false);
        TextView accept = modalButton(positive, true);
        actions.addView(cancel);
        LinearLayout.LayoutParams acceptParams = new LinearLayout.LayoutParams(-2, dp(46));
        acceptParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(accept, acceptParams);
        panel.addView(actions);
        dialog.setContentView(panel);
        cancel.setOnClickListener(button -> dismissAnimated(dialog, panel));
        accept.setOnClickListener(button -> {
            confirmed.run();
            dismissAnimated(dialog, panel);
        });
        showModal(dialog, panel);
    }

    private TextView modalButton(String title, boolean primary) {
        TextView button = label(title.toUpperCase(), 12,
                primary ? MARColors.INK : text, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setLetterSpacing(0.08f);
        button.setPadding(dp(17), dp(10), dp(17), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? MARColors.GREEN : Color.TRANSPARENT);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), primary ? MARColors.GREEN
                : (dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER));
        button.setBackground(background);
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(.96f).scaleY(.96f).setDuration(80).start();
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });
        return button;
    }

    private GradientDrawable fieldBackground(boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? MARColors.DARK_PANEL : MARColors.LIGHT_PANEL);
        background.setCornerRadius(dp(15));
        background.setStroke(dp(focused ? 2 : 1), focused ? MARColors.BLUE
                : (dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER));
        return background;
    }

    private void showModal(Dialog dialog, LinearLayout panel) {
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setDimAmount(.64f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(getResources().getDisplayMetrics().widthPixels - dp(36), -2);
        }
        panel.setAlpha(0f);
        panel.setScaleX(.96f);
        panel.setScaleY(.96f);
        panel.setTranslationY(dp(22));
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, .9f, .2f, 1f))
                .setDuration(220).start();
    }

    private void dismissAnimated(Dialog dialog, LinearLayout panel) {
        panel.animate().alpha(0f).scaleX(.98f).scaleY(.98f).translationY(dp(12))
                .setDuration(130).withEndAction(dialog::dismiss).start();
    }

    private void showDropdown(String title, String[] labels, int selected,
                              ChoiceListener listener) {
        Dialog dialog = new Dialog(host);
        LinearLayout panel = modalPanel(title);
        ScrollView optionsScroll = new ScrollView(host);
        optionsScroll.setFillViewport(true);
        optionsScroll.setVerticalScrollBarEnabled(false);
        LinearLayout options = new LinearLayout(host);
        options.setOrientation(LinearLayout.VERTICAL);
        optionsScroll.addView(options, new ScrollView.LayoutParams(-1, -2));
        for (int i = 0; i < labels.length; i++) {
            final int choice = i;
            boolean active = selected >= 0 && i == selected;
            TextView option = label((active ? "✓  " : "   ") + labels[i], 14,
                    active ? MARColors.GREEN : text,
                    active ? Typeface.BOLD : Typeface.NORMAL);
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(dp(15), dp(12), dp(15), dp(12));
            GradientDrawable optionBackground = new GradientDrawable();
            optionBackground.setColor(active
                    ? (dark ? MARColors.DARK_SELECTED : MARColors.LIGHT_SELECTED)
                    : (dark ? MARColors.DARK_PANEL : MARColors.LIGHT_PANEL));
            optionBackground.setCornerRadius(dp(16));
            optionBackground.setStroke(dp(1), active ? MARColors.GREEN
                    : (dark ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER));
            option.setBackground(optionBackground);
            option.setOnClickListener(view -> {
                view.animate().scaleX(.97f).scaleY(.97f).alpha(.72f).setDuration(90)
                        .withEndAction(() -> {
                            dialog.dismiss();
                            listener.onChoice(choice);
                        }).start();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, dp(7));
            options.addView(option, params);
        }
        int available = getResources().getDisplayMetrics().heightPixels - dp(190);
        int desired = labels.length * dp(57);
        panel.addView(optionsScroll, new LinearLayout.LayoutParams(
                -1, Math.min(Math.max(dp(57), desired), Math.max(dp(180), available))));
        dialog.setContentView(panel);
        showModal(dialog, panel);
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
