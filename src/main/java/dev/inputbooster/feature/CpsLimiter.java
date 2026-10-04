package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Smart CPS limiter.
 *
 * The cap is enforced with a token bucket rather than a hard rolling-window
 * counter: a rolling window rejected bursty but legitimate input (e.g. a fast
 * 8-click burst followed by a pause) because every attempt inside the same
 * second counted against the cap regardless of when the previous ones happened.
 * The bucket refills at {@code maxCps} tokens per second and holds at most
 * {@code maxCps}, so the long-run average is still capped while short bursts are
 * allowed through.
 *
 * The effective cap is computed once per second and reused for that window, so
 * HUMANIZED mode cannot give two clicks in the same second different limits.
 * All timing uses {@link System#nanoTime()}, so a wall-clock change cannot
 * corrupt the limiter.
 */
public class CpsLimiter {

    private static final long WINDOW_NANOS = 1_000_000_000L;

    /** Accepted clicks in the last second (display only). */
    private final ConcurrentLinkedDeque<Long> accepted = new ConcurrentLinkedDeque<>();
    private volatile long lastAcceptedAt;

    private final Object bucketLock = new Object();
    private double tokens;
    private long lastRefillNanos;
    private boolean primed;

    private volatile long windowStartNanos = System.nanoTime();
    private volatile int windowLimit = -1;

    /**
     * Call before processing an ATTACK_PRESSED event.
     *
     * @return true if the click should be allowed, false if it must be dropped
     */
    public boolean allowClick() {
        if (!InputBoosterConfig.isCpsLimiterEnabled()) return true;

        long now = System.nanoTime();
        int maxCps = effectiveMaxCps(now);

        synchronized (bucketLock) {
            refill(now, maxCps);
            if ("COOLDOWN".equals(InputBoosterConfig.getCpsMode())) {
                long minGapNs = Math.max(35L, WINDOW_NANOS / Math.max(1, maxCps)) * 1_000_000L;
                if (lastAcceptedAt > 0 && now - lastAcceptedAt < minGapNs) return false;
            }
            if (tokens < 1.0d) {
                return false; // over the long-run cap — drop
            }
            tokens -= 1.0d;
            lastAcceptedAt = now;
            accepted.addLast(now);
            pruneAccepted(now);
            return true;
        }
    }

    /** Fills the bucket for elapsed time since the last refill. */
    private void refill(long nowNanos, int maxCps) {
        if (!primed) {
            // First use starts with a full bucket, so the first burst after
            // joining a world is not swallowed by the limiter.
            primed = true;
            lastRefillNanos = nowNanos;
            tokens = maxCps;
            return;
        }
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) return;
        lastRefillNanos = nowNanos;
        double refillRate = maxCps / (double) WINDOW_NANOS;
        tokens = Math.min(maxCps, tokens + elapsed * refillRate);
    }

    /** Tokens currently available — exposed for tests and the HUD. */
    public double availableTokens() {
        synchronized (bucketLock) {
            refill(System.nanoTime(), effectiveMaxCps(System.nanoTime()));
            return tokens;
        }
    }

    /**
     * Record a click that was accepted.
     *
     * Accepted clicks are already counted by {@link #allowClick()}, so this
     * only prunes the display window; it is kept so existing call sites keep
     * working without double counting.
     */
    public void recordClick() {
        pruneAccepted(System.nanoTime());
    }

    /** Current CPS (accepted clicks in the last second). */
    public int getCps() {
        long now = System.nanoTime();
        pruneAccepted(now);
        return accepted.size();
    }

    /** Max CPS from config (for HUD bar max scale). */
    public int getMaxCps() {
        return effectiveMaxCps(System.nanoTime());
    }

    /** Called every game tick — prune stale entries. */
    public void tick(Minecraft client) {
        long now = System.nanoTime();
        pruneAccepted(now);
        rollWindow(now);
    }

    /** Drops all recorded state, e.g. on world change or shutdown. */
    public void reset() {
        synchronized (bucketLock) {
            accepted.clear();
            lastAcceptedAt = 0L;
            lastRefillNanos = 0L;
            tokens = 0d;
            primed = false;
        }
        windowStartNanos = System.nanoTime();
        windowLimit = -1;
    }

    /**
     * Effective cap for the current window, computed once per second and then
     * reused so every click in a window sees the same limit.
     */
    public int effectiveMaxCps(long nowNanos) {
        rollWindow(nowNanos);
        int cached = windowLimit;
        if (cached > 0) return cached;

        int max = InputBoosterConfig.getMaxCps();
        long windowIndex = nowNanos / WINDOW_NANOS;
        // Deterministic jitter per window rather than a fresh random number per
        // click: the cap still varies naturally but stays consistent within the
        // second it applies to.
        int jitter = (int) Math.floorMod((windowIndex * 0x9E3779B97F4A7C15L) >>> 33, 3L);
        int limit = switch (InputBoosterConfig.getCpsMode()) {
            case "HUMANIZED" -> Math.max(1, max - jitter);
            case "WEAPON_AWARE" -> Math.min(max, 16);
            case "COOLDOWN" -> Math.min(max, 18);
            default -> max;
        };
        windowLimit = Math.max(1, limit);
        return windowLimit;
    }

    private void rollWindow(long nowNanos) {
        if (nowNanos - windowStartNanos < WINDOW_NANOS) return;
        windowStartNanos += WINDOW_NANOS;
        windowLimit = -1; // recompute once for the new window
    }

    private void pruneAccepted(long nowNanos) {
        while (!accepted.isEmpty() && nowNanos - accepted.peekFirst() > WINDOW_NANOS) {
            accepted.pollFirst();
        }
    }
}