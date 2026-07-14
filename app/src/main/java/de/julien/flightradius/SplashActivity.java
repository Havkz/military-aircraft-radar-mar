package de.julien.flightradius;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
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
        boolean dark = AppPreferences.isDark(this);
        int background = dark ? Color.BLACK : Color.rgb(239, 247, 250);
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
        mar.setText("MAR"); mar.setTextSize(42); mar.setTextColor(Color.rgb(0, 245, 255));
        mar.setTypeface(Typeface.DEFAULT_BOLD); mar.setLetterSpacing(0.22f); mar.setGravity(Gravity.CENTER);
        mar.setAlpha(0f);
        root.addView(mar);

        TextView name = new TextView(this);
        name.setText("MILITARY AIRCRAFT RADAR"); name.setTextSize(11);
        name.setTextColor(dark ? Color.rgb(150, 177, 185) : Color.rgb(65, 91, 101));
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
