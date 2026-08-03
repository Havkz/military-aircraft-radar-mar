package de.julien.flightradius;

final class AdaptiveBackoff {
    private static final long STEP_MS = 500L;
    private static final long WINDOW_MS = 60_000L;

    private long extraDelayMs;
    private long activeUntilMs;

    synchronized void recordRateLimit(long nowMs) {
        if (nowMs >= activeUntilMs) extraDelayMs = 0L;
        extraDelayMs += STEP_MS;
        activeUntilMs = nowMs + WINDOW_MS;
    }

    synchronized long delayMs(long baseDelayMs, long nowMs) {
        if (nowMs >= activeUntilMs) extraDelayMs = 0L;
        return baseDelayMs + extraDelayMs;
    }
}
