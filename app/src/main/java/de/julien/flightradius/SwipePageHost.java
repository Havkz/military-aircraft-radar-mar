package de.julien.flightradius;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

final class SwipePageHost extends FrameLayout {
    interface Listener { void onSwipe(int direction); }

    private final int threshold;
    private float downX;
    private float downY;
    private boolean horizontal;
    private Listener listener;

    SwipePageHost(Context context) {
        super(context);
        threshold = Math.max(ViewConfiguration.get(context).getScaledTouchSlop() * 5,
                Math.round(64 * getResources().getDisplayMetrics().density));
    }

    void setListener(Listener listener) { this.listener = listener; }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                horizontal = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dx) > threshold / 3f && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                    horizontal = true;
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!horizontal) return true;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            if (Math.abs(dx) >= threshold && listener != null) {
                listener.onSwipe(dx < 0 ? 1 : -1);
            }
            horizontal = false;
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) horizontal = false;
        return true;
    }
}
