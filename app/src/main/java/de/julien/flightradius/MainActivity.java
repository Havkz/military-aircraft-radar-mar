package de.julien.flightradius;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int GREEN = MARColors.GREEN;
    private static final int BLUE = MARColors.BLUE;
    private static final int ORANGE = MARColors.ORANGE;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private SharedPreferences preferences;
    private RadarView radar;
    private Button toggle;
    private TextView status;
    private TextView radiusValue;
    private TextView countValue;
    private TextView connectionValue;
    private TextView lastScanValue;
    private TextView nearestValue;
    private ProgressBar startupProgress;
    private SwipePageHost pageHost;
    private View radarPage;
    private AircraftListPanel aircraftPanel;
    private SettingsPanel settingsPanel;
    private final ImageButton[] navigationButtons = new ImageButton[3];
    private int currentPage;
    private int restoredPage;
    private boolean pageAnimating;
    private int background;
    private int surface;
    private int text;
    private int muted;
    private String settingsSignature;

    private final Runnable liveUi = new Runnable() {
        @Override public void run() {
            refreshLiveData();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = AppPreferences.get(this);
        L10n.applyDirection(this);
        applyPalette();
        settingsSignature = signature();
        restoredPage = savedInstanceState == null ? 0 : savedInstanceState.getInt("page", 0);
        buildUi();
    }

    private void applyPalette() {
        boolean dark = AppPreferences.isDark(this);
        background = dark ? MARColors.DARK_BACKGROUND : MARColors.LIGHT_BACKGROUND;
        surface = dark ? MARColors.DARK_SURFACE : MARColors.LIGHT_SURFACE;
        text = dark ? MARColors.DARK_TEXT : MARColors.LIGHT_TEXT;
        muted = dark ? MARColors.DARK_MUTED : MARColors.LIGHT_MUTED;
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        if (!dark && Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView eyebrow = label(L10n.t(this, "live_radar"), 11, GREEN, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.16f);
        names.addView(eyebrow);
        TextView title = label(L10n.t(this, "app_title"), 28, text, Typeface.BOLD);
        title.setLetterSpacing(0.05f);
        names.addView(title);
        ImageButton settings = iconButton(R.drawable.ic_material_settings,
                L10n.t(this, "settings"));
        settings.setContentDescription(L10n.t(this, "settings"));
        settings.setOnClickListener(v -> switchPage(2));
        top.addView(settings, new LinearLayout.LayoutParams(dp(54), dp(54)));
        root.addView(top);

        radar = new RadarView(this);
        radar.setDarkMode(AppPreferences.isDark(this));
        LinearLayout.LayoutParams radarParams = new LinearLayout.LayoutParams(-1, dp(260));
        radarParams.setMargins(0, dp(16), 0, dp(14));
        root.addView(radar, radarParams);

        LinearLayout statusCard = card();
        statusCard.setOrientation(LinearLayout.VERTICAL);
        status = label("", 13, text, Typeface.BOLD);
        status.setLetterSpacing(0.08f);
        statusCard.addView(status);
        connectionValue = label("", 12, muted, Typeface.NORMAL);
        connectionValue.setPadding(0, dp(6), 0, 0);
        statusCard.addView(connectionValue);
        startupProgress = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        startupProgress.setIndeterminate(true);
        startupProgress.setIndeterminateTintList(ColorStateList.valueOf(GREEN));
        startupProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
        progressParams.setMargins(0, dp(10), 0, 0);
        statusCard.addView(startupProgress, progressParams);
        root.addView(statusCard, cardParams());

        LinearLayout liveGrid = new LinearLayout(this);
        liveGrid.setOrientation(LinearLayout.HORIZONTAL);
        countValue = addMetric(liveGrid, L10n.t(this, "contacts"));
        lastScanValue = addMetric(liveGrid, L10n.t(this, "last_scan"));
        root.addView(liveGrid, cardParams());

        LinearLayout nearestCard = card();
        nearestCard.setOrientation(LinearLayout.VERTICAL);
        TextView nearestTitle = label(L10n.t(this, "nearest"),
                11, muted, Typeface.BOLD);
        nearestTitle.setLetterSpacing(0.1f);
        nearestCard.addView(nearestTitle);
        nearestValue = label("—", 18, text, Typeface.BOLD);
        nearestValue.setPadding(0, dp(8), 0, 0);
        nearestCard.addView(nearestValue);
        root.addView(nearestCard, cardParams());

        LinearLayout rangeCard = card();
        rangeCard.setOrientation(LinearLayout.VERTICAL);
        TextView rangeTitle = label(L10n.t(this, "scan_radius"), 11, muted, Typeface.BOLD);
        rangeTitle.setLetterSpacing(0.12f);
        rangeCard.addView(rangeTitle);
        radiusValue = label("", 27, text, Typeface.BOLD);
        radiusValue.setPadding(0, dp(6), 0, 0);
        rangeCard.addView(radiusValue);
        RecommendedRangeControl rangeControl = new RecommendedRangeControl(this);
        SeekBar range = rangeControl.seekBar();
        int radius = preferences.getInt(AppPreferences.KEY_RADIUS_KM, AppPreferences.DEFAULT_RADIUS_KM);
        range.setProgress(radius - 10);
        range.setProgressTintList(ColorStateList.valueOf(GREEN));
        range.setThumbTintList(ColorStateList.valueOf(ORANGE));
        updateRadius(radius);
        range.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateRadius(progress + 10);
                if (fromUser) preferences.edit().putInt(AppPreferences.KEY_RADIUS_KM, progress + 10).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                requestRadiusRefresh();
            }
        });
        rangeCard.addView(rangeControl, new LinearLayout.LayoutParams(-1, dp(74)));
        root.addView(rangeCard, cardParams());

        toggle = neonButton("");
        toggle.setOnClickListener(v -> toggleMonitoring());
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(-1, dp(62));
        toggleParams.setMargins(0, dp(4), 0, dp(8));
        root.addView(toggle, toggleParams);

        Button battery = secondaryButton(L10n.t(this, "battery"));
        battery.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e) { Toast.makeText(this, L10n.t(this, "not_available"), Toast.LENGTH_SHORT).show(); }
        });
        root.addView(battery, new LinearLayout.LayoutParams(-1, dp(52)));

        radarPage = scroll;
        buildPageShell();
    }

    private TextView addMetric(LinearLayout parent, String title) {
        LinearLayout box = card();
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView heading = label(title, 10, muted, Typeface.BOLD);
        heading.setLetterSpacing(0.1f);
        box.addView(heading);
        TextView value = label("—", 22, text, Typeface.BOLD);
        value.setPadding(0, dp(6), 0, 0);
        box.addView(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(100), 1f);
        params.setMargins(0, 0, dp(7), 0);
        parent.addView(box, params);
        return value;
    }

    private void buildPageShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(background);

        pageHost = new SwipePageHost(this);
        pageHost.setListener(direction -> switchPage(currentPage + direction));
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView footer = label("ADSB.LOL  •  FLIGHTRADAR24  •  ADS-B EXCHANGE",
                9, muted, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.06f);
        footer.setPadding(dp(8), dp(5), dp(8), dp(5));
        shell.addView(footer, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(14), dp(4), dp(14), dp(6));
        navigationButtons[0] = navButton(R.drawable.ic_material_radar,
                L10n.t(this, "live_radar"), 0);
        navigationButtons[1] = navButton(R.drawable.ic_material_flight,
                L10n.t(this, "aircraft"), 1);
        navigationButtons[2] = navButton(R.drawable.ic_material_settings,
                L10n.t(this, "settings"), 2);
        for (ImageButton button : navigationButtons) navigation.addView(button, navParams());
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(62)));

        currentPage = Math.max(0, Math.min(2, restoredPage));
        pageHost.addView(pageFor(currentPage), new ViewGroup.LayoutParams(-1, -1));
        updateNavigation();
        setContentView(shell);
        SystemBars.apply(this, shell, AppPreferences.isDark(this), background);
    }

    private View pageFor(int page) {
        if (page == 1) {
            if (aircraftPanel == null) aircraftPanel = new AircraftListPanel(this);
            return aircraftPanel;
        }
        if (page == 2) {
            if (settingsPanel == null) settingsPanel = new SettingsPanel(this, this::recreate);
            return settingsPanel;
        }
        return radarPage;
    }

    private void switchPage(int target) {
        if (target < 0 || target > 2 || target == currentPage || pageAnimating) return;
        final int from = currentPage;
        final int direction = target > from ? 1 : -1;
        final View previous = pageFor(from);
        final View next = pageFor(target);
        currentPage = target;
        updateNavigation();
        if (next.getParent() != null) ((ViewGroup) next.getParent()).removeView(next);
        int distance = Math.max(pageHost.getWidth(), getResources().getDisplayMetrics().widthPixels);
        next.setTranslationX(direction * distance);
        next.setAlpha(0.82f);
        pageHost.addView(next, new ViewGroup.LayoutParams(-1, -1));
        pageAnimating = true;
        previous.animate().translationX(-direction * distance * 0.22f).alpha(0f)
                .setDuration(230).start();
        next.animate().translationX(0f).alpha(1f).setDuration(260).withEndAction(() -> {
            pageHost.removeView(previous);
            previous.setTranslationX(0f);
            previous.setAlpha(1f);
            pageAnimating = false;
            if (currentPage == 1 && aircraftPanel != null) aircraftPanel.refresh();
        }).start();
    }

    private ImageButton navButton(int icon, String description, int page) {
        ImageButton button = iconButton(icon, description);
        button.setOnClickListener(view -> switchPage(page));
        return button;
    }

    private void updateNavigation() {
        for (int i = 0; i < navigationButtons.length; i++) {
            ImageButton button = navigationButtons[i];
            if (button == null) continue;
            boolean selected = i == currentPage;
            button.setImageTintList(ColorStateList.valueOf(selected ? GREEN : muted));
            GradientDrawable backgroundDrawable = new GradientDrawable();
            backgroundDrawable.setColor(selected
                    ? (AppPreferences.isDark(this)
                    ? MARColors.DARK_SELECTED : MARColors.LIGHT_SELECTED)
                    : android.graphics.Color.TRANSPARENT);
            backgroundDrawable.setCornerRadius(dp(16));
            button.setBackground(backgroundDrawable);
            button.animate().scaleX(selected ? 1.08f : 1f)
                    .scaleY(selected ? 1.08f : 1f).setDuration(180).start();
        }
    }

    private void toggleMonitoring() {
        if (isRunning()) {
            stopService(new Intent(this, MonitorService.class));
            preferences.edit()
                    .putBoolean(AppPreferences.KEY_RUNNING, false)
                    .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                    .apply();
            refreshLiveData();
        } else requestAndStart();
        ObjectAnimator.ofFloat(toggle, "scaleX", 0.96f, 1f).setDuration(220).start();
        ObjectAnimator.ofFloat(toggle, "scaleY", 0.96f, 1f).setDuration(220).start();
    }

    private void requestAndStart() {
        boolean locationMissing = !hasLocationPermission();
        boolean notificationMissing = Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
        if (locationMissing || notificationMissing) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSIONS);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
            return;
        }
        startForegroundService(new Intent(this, MonitorService.class));
        preferences.edit()
                .putBoolean(AppPreferences.KEY_RUNNING, true)
                .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, true)
                .apply();
        refreshLiveData();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_PERMISSIONS && hasLocationPermission()) requestAndStart();
        else Toast.makeText(this, L10n.t(this, "location_required"), Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!signature().equals(settingsSignature)) {
            recreate();
            return;
        }
        uiHandler.removeCallbacks(liveUi);
        uiHandler.post(liveUi);
    }

    @Override protected void onPause() {
        uiHandler.removeCallbacks(liveUi);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("page", currentPage);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (currentPage != 0) {
            switchPage(0);
            return;
        }
        preferences.edit()
                .putBoolean(AppPreferences.KEY_RUNNING, false)
                .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                .putString(AppPreferences.KEY_CONNECTION, "standby")
                .putString(AppPreferences.KEY_AIRCRAFT_HISTORY_JSON, "[]")
                .apply();
        stopService(new Intent(this, MonitorService.class));
        getSystemService(android.app.NotificationManager.class).cancelAll();
        super.onBackPressed();
    }

    private boolean isRunning() {
        return preferences.getBoolean(AppPreferences.KEY_RUNNING, false);
    }

    private void refreshLiveData() {
        boolean running = isRunning();
        radar.setScanning(running);
        status.setText(L10n.t(this, running ? "radar_active" : "radar_standby"));
        status.setTextColor(running ? GREEN : muted);
        toggle.setText(L10n.t(this, running ? "stop_monitoring" : "start_monitoring"));
        styleToggle(running);

        int count = preferences.getInt(AppPreferences.KEY_LIVE_COUNT, 0);
        countValue.setText(String.valueOf(count));
        String connection = preferences.getString(AppPreferences.KEY_CONNECTION, "standby");
        boolean loading = running && !"connected".equals(connection);
        startupProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        connectionValue.setVisibility(loading ? View.GONE : View.VISIBLE);
        if (!loading) connectionValue.setText(running
                ? L10n.t(this, "connected") : L10n.t(this, "paused"));

        long lastScan = preferences.getLong(AppPreferences.KEY_LAST_SCAN, 0L);
        lastScanValue.setText(lastScan == 0 ? "—" : new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date(lastScan)));
        String callsign = preferences.getString(AppPreferences.KEY_NEAREST_CALLSIGN, "");
        if (callsign.isEmpty()) nearestValue.setText(L10n.t(this, "no_contact"));
        else {
            double distance = Double.longBitsToDouble(preferences.getLong(
                    AppPreferences.KEY_NEAREST_DISTANCE_KM, Double.doubleToLongBits(Double.NaN)));
            double altitude = Double.longBitsToDouble(preferences.getLong(
                    AppPreferences.KEY_NEAREST_ALTITUDE_FT, Double.doubleToLongBits(Double.NaN)));
            nearestValue.setText(callsign + "  •  " + AppPreferences.distance(this, distance)
                    + "  •  " + AppPreferences.altitude(this, altitude));
        }
        if (currentPage == 1 && aircraftPanel != null) aircraftPanel.refresh();
    }

    private void updateRadius(int km) { radiusValue.setText(AppPreferences.distance(this, km)); }

    private void requestRadiusRefresh() {
        if (!isRunning()) return;
        startService(new Intent(this, MonitorService.class)
                .setAction(MonitorService.ACTION_RADIUS_CHANGED));
    }

    private String signature() {
        return preferences.getString(AppPreferences.KEY_THEME, "oled") + "|"
                + preferences.getString(AppPreferences.KEY_LANGUAGE, "system") + "|"
                + preferences.getString(AppPreferences.KEY_UNITS, "aviation");
    }

    private TextView label(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        return view;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setPadding(dp(17), dp(14), dp(17), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surface);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), AppPreferences.isDark(this)
                ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        view.setBackground(bg);
        view.setElevation(dp(2));
        return view;
    }

    private ImageButton iconButton(int drawable, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawable);
        button.setImageTintList(ColorStateList.valueOf(BLUE));
        button.setContentDescription(description);
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surface);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), AppPreferences.isDark(this)
                ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        button.setBackground(bg);
        button.setStateListAnimator(null);
        return button;
    }

    private Button neonButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setLetterSpacing(0.08f);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        return button;
    }

    private void styleToggle(boolean running) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(running ? surface : GREEN);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(running ? 2 : 1), running ? ORANGE : GREEN);
        toggle.setBackground(bg);
        toggle.setTextColor(running ? ORANGE : MARColors.INK);
        toggle.setElevation(dp(running ? 1 : 3));
    }

    private Button secondaryButton(String value) {
        Button button = neonButton(value);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(surface);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), AppPreferences.isDark(this)
                ? MARColors.DARK_BORDER : MARColors.LIGHT_BORDER);
        button.setBackground(bg);
        button.setTextColor(muted);
        return button;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
