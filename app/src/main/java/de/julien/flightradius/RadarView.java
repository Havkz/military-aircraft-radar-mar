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
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class RadarView extends View {
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint target = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator animator;
    private final ValueAnimator revealAnimator;
    private float angle;
    private float reveal;
    private boolean scanning;
    private boolean attached;
    private boolean darkMode = true;

    public RadarView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(dp(1));
        grid.setColor(MARColors.RADAR_GRID);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(dp(2));
        glow.setColor(MARColors.GREEN);
        glow.setShadowLayer(dp(3), 0, 0, MARColors.GREEN);
        target.setColor(MARColors.ORANGE);
        target.setShadowLayer(dp(4), 0, 0, MARColors.ORANGE);

        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(4200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            angle = (float) animation.getAnimatedValue();
            invalidate();
        });
        revealAnimator = ValueAnimator.ofFloat(0f, 1f);
        revealAnimator.setDuration(720);
        revealAnimator.setInterpolator(new DecelerateInterpolator());
        revealAnimator.addUpdateListener(animation -> {
            reveal = (float) animation.getAnimatedValue();
            invalidate();
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (scanning && getWindowVisibility() == View.VISIBLE) startAnimations();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        animator.cancel();
        revealAnimator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(getWidth(), getHeight()) * 0.43f;

        Path clip = new Path();
        clip.addRoundRect(0, 0, getWidth(), getHeight(), dp(24), dp(24),
                Path.Direction.CW);
        int clipped = canvas.save();
        canvas.clipPath(clip);

        Paint panel = new Paint(Paint.ANTI_ALIAS_FLAG);
        panel.setColor(darkMode ? MARColors.DARK_PANEL : MARColors.LIGHT_PANEL);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(24), dp(24), panel);

        for (int i = 1; i <= 4; i++) canvas.drawCircle(cx, cy, radius * i / 4f, grid);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, grid);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, grid);
        canvas.drawCircle(cx, cy, radius, glow);

        if (scanning) {
            Path radarClip = new Path();
            radarClip.addCircle(cx, cy, radius - dp(2), Path.Direction.CW);
            int sweepClip = canvas.save();
            canvas.clipPath(radarClip);
            int revealed = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(),
                    Math.max(0, Math.min(255, (int) (255 * reveal))));
            float scale = 0.86f + 0.14f * reveal;
            canvas.scale(scale, scale, cx, cy);
            canvas.save();
            canvas.rotate(angle, cx, cy);
            sweep.setShader(new LinearGradient(cx, cy, cx + radius, cy,
                    new int[]{Color.TRANSPARENT, Color.argb(44, 110, 168, 255), MARColors.GREEN},
                    null, Shader.TileMode.CLAMP));
            Path beam = new Path();
            beam.moveTo(cx, cy);
            beam.lineTo(cx + radius * 0.96f, cy - radius * 0.12f);
            beam.lineTo(cx + radius * 0.96f, cy);
            beam.close();
            canvas.drawPath(beam, sweep);
            canvas.drawLine(cx, cy, cx + radius * 0.96f, cy, glow);
            canvas.restore();

            pulse(canvas, cx - radius * 0.42f, cy - radius * 0.18f, 5f);
            pulse(canvas, cx + radius * 0.25f, cy + radius * 0.38f, 4f);
            pulse(canvas, cx + radius * 0.48f, cy - radius * 0.47f, 3f);
            canvas.restoreToCount(revealed);
            canvas.restoreToCount(sweepClip);
        }
        canvas.restoreToCount(clipped);
    }

    public void setScanning(boolean active) {
        if (scanning == active) return;
        scanning = active;
        if (active) {
            reveal = 0f;
            if (attached && getWindowVisibility() == View.VISIBLE) startAnimations();
        } else {
            animator.cancel();
            revealAnimator.cancel();
            angle = 0f;
            reveal = 0f;
        }
        invalidate();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE && scanning && attached && !animator.isStarted()) {
            startAnimations();
        } else if (visibility != View.VISIBLE) {
            animator.cancel();
            revealAnimator.cancel();
        }
    }

    public void setDarkMode(boolean dark) {
        darkMode = dark;
        grid.setColor(dark ? MARColors.RADAR_GRID : Color.rgb(161, 190, 176));
        invalidate();
    }

    private void pulse(Canvas canvas, float x, float y, float size) {
        float wave = 0.7f + 0.3f * (float) Math.sin(Math.toRadians(angle * 2));
        target.setAlpha((int) (150 + 105 * wave));
        canvas.drawCircle(x, y, dp(size * wave), target);
    }

    private void startAnimations() {
        if (!animator.isStarted()) animator.start();
        if (reveal < 1f && !revealAnimator.isStarted()) revealAnimator.start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
