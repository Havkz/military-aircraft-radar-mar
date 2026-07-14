package de.julien.flightradius;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        clearStaleNotificationsAfterUpdate();
        L10n.applyDirection(this);
        boolean dark = AppPreferences.isDark(this);
        int background = dark ? Color.rgb(0, 4, 8) : Color.rgb(244, 246, 243);
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(background);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_radar_logo);
        logo.setAlpha(0f);
        logo.setScaleX(0.55f); logo.setScaleY(0.55f);
        root.addView(logo, new LinearLayout.LayoutParams(dp(150), dp(150)));

        TextView mar = new TextView(this);
        mar.setText("MAR"); mar.setTextSize(42); mar.setTextColor(Color.rgb(79, 138, 101));
        mar.setTypeface(Typeface.DEFAULT_BOLD); mar.setLetterSpacing(0.22f); mar.setGravity(Gravity.CENTER);
        mar.setAlpha(0f);
        root.addView(mar);

        TextView name = new TextView(this);
        name.setText(L10n.t(this, "app_title").replace('\n', ' ')); name.setTextSize(11);
        name.setTextColor(dark ? Color.rgb(139, 149, 156) : Color.rgb(94, 105, 113));
        name.setTypeface(Typeface.DEFAULT_BOLD); name.setLetterSpacing(0.12f); name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(7), 0, 0); name.setAlpha(0f);
        root.addView(name);

        setContentView(root);
        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.55f, 1f),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.55f, 1f),
                ObjectAnimator.ofFloat(logo, "rotation", -35f, 0f),
                ObjectAnimator.ofFloat(mar, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(name, "alpha", 0f, 1f));
        animation.setDuration(850);
        animation.start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 1150L);
    }

    private void clearStaleNotificationsAfterUpdate() {
        int currentVersion;
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) {
            return;
        }
        android.content.SharedPreferences prefs = AppPreferences.get(this);
        if (prefs.getInt(AppPreferences.KEY_APP_VERSION, 0) == currentVersion) return;
        getSystemService(NotificationManager.class).cancelAll();
        stopService(new Intent(this, MonitorService.class));
        prefs.edit()
                .putInt(AppPreferences.KEY_APP_VERSION, currentVersion)
                .putBoolean(AppPreferences.KEY_RUNNING, false)
                .putBoolean(AppPreferences.KEY_MONITORING_ENABLED, false)
                .putString(AppPreferences.KEY_CONNECTION, "standby")
                .apply();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
