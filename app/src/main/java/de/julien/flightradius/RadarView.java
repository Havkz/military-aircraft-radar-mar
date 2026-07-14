package de.julien.flightradius;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class RadarView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator;
    private float angle;
    private boolean scanning;
    private boolean attached;
    private boolean darkMode = true;

    public RadarView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        grid.setColor(Color.rgb(0, 67, 74));
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(dp(2));
        glow.setColor(Color.rgb(0, 245, 255));
        glow.setShadowLayer(dp(10), 0, 0, Color.rgb(0, 245, 255));
        target.setColor(Color.rgb(176, 38, 255));
        target.setShadowLayer(dp(12), 0, 0, Color.rgb(176, 38, 255));

        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(4200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            angle = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (scanning) animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.43f;

        Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        panel.setColor(darkMode ? Color.rgb(2, 8, 12) : Color.rgb(226, 241, 245));
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(24), dp(24), panel);

        for (int i = 1; i <= 4; i++) canvas.drawCircle(cx, cy, radius * i / 4f, grid);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, grid);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, grid);
        canvas.drawCircle(cx, cy, radius, glow);

        canvas.save();
        canvas.rotate(angle, cx, cy);
        sweep.setShader(new LinearGradient(cx, cy, cx + radius, cy,
                new int[]{Color.TRANSPARENT, Color.argb(65, 0, 245, 255), Color.rgb(0, 245, 255)},
                null, Shader.TileMode.CLAMP));
        Path beam = new Path();
        beam.moveTo(cx, cy);
        beam.lineTo(cx + radius, cy - radius * 0.24f);
        beam.lineTo(cx + radius, cy);
        beam.close();
        canvas.drawPath(beam, sweep);
        canvas.drawLine(cx, cy, cx + radius, cy, glow);
        canvas.restore();

        if (scanning) {
            pulse(canvas, cx - radius * 0.42f, cy - radius * 0.18f, 5f);
            pulse(canvas, cx + radius * 0.25f, cy + radius * 0.38f, 4f);
            pulse(canvas, cx + radius * 0.48f, cy - radius * 0.47f, 3f);
        }
    }

    public void setScanning(boolean active) {
        if (scanning == active) return;
        scanning = active;
        if (active && attached && !animator.isStarted()) animator.start();
        else if (!active) animator.cancel();
        invalidate();
    }

    public void setDarkMode(boolean dark) {
        darkMode = dark;
        invalidate();
    }

    private void pulse(Canvas canvas, float x, float y, float size) {
        float wave = 0.7f + 0.3f * (float) Math.sin(Math.toRadians(angle * 2));
        target.setAlpha((int) (150 + 105 * wave));
        canvas.drawCircle(x, y, dp(size * wave), target);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
