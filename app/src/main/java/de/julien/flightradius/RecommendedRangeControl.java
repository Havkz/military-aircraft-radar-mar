package de.julien.flightradius;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

final class RecommendedRangeControl extends FrameLayout {
    private static final int MIN_KM = 10;
    private static final int MAX_KM = 300;
    private static final int RECOMMENDED_MIN_KM = 20;
    private static final int RECOMMENDED_MAX_KM = 30;

    private final MarkedSeekBar seekBar;
    private final TextView marker;

    RecommendedRangeControl(Context context) {
        super(context);
        seekBar = new MarkedSeekBar(context);
        seekBar.setMax(MAX_KM - MIN_KM);
        LayoutParams seekParams = new LayoutParams(-1, dp(48));
        seekParams.gravity = Gravity.TOP;
        addView(seekBar, seekParams);

        marker = new TextView(context);
        marker.setText(L10n.t(context, "recommended_short"));
        marker.setTextSize(9);
        marker.setTextColor(AppPreferences.isDark(context)
                ? MARColors.DARK_MUTED : MARColors.LIGHT_MUTED);
        marker.setGravity(Gravity.CENTER);
        marker.setPadding(dp(3), 0, dp(3), 0);
        LayoutParams markerParams = new LayoutParams(-2, dp(18));
        markerParams.gravity = Gravity.TOP;
        markerParams.topMargin = dp(43);
        addView(marker, markerParams);
    }

    SeekBar seekBar() { return seekBar; }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int usable = Math.max(0, getWidth() - seekBar.getPaddingLeft() - seekBar.getPaddingRight());
        float midpoint = ((25f - MIN_KM) / (MAX_KM - MIN_KM));
        float center = seekBar.getPaddingLeft() + usable * midpoint;
        float x = Math.max(0, Math.min(getWidth() - marker.getWidth(),
                center - marker.getWidth() / 2f));
        marker.setX(x);
    }

    private final class MarkedSeekBar extends SeekBar {
        private final Paint recommended = new Paint(Paint.ANTI_ALIAS_FLAG);

        MarkedSeekBar(Context context) {
            super(context);
            recommended.setColor(MARColors.GREEN);
            recommended.setAlpha(115);
            recommended.setStrokeWidth(dp(9));
            recommended.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected synchronized void onDraw(Canvas canvas) {
            int usable = getWidth() - getPaddingLeft() - getPaddingRight();
            float start = getPaddingLeft() + usable
                    * ((RECOMMENDED_MIN_KM - MIN_KM) / (float) (MAX_KM - MIN_KM));
            float end = getPaddingLeft() + usable
                    * ((RECOMMENDED_MAX_KM - MIN_KM) / (float) (MAX_KM - MIN_KM));
            canvas.drawLine(start, getHeight() / 2f, end, getHeight() / 2f, recommended);
            super.onDraw(canvas);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
