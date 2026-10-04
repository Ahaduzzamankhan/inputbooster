package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Smart CPS limiter.
 *
 * Caps accepted attacks to a rolling one second window. The effective cap is
 * computed once per window rather than once per click: recomputing it on every
 * call produced a different limit for each click inside the same second, which
 * made HUMANIZED mode inconsistent (and effectively raised the cap, because the
 * randomised value was subtracted per click but re-rolled each time).
 *
 * Timing uses {@link System#nanoTime()} so a wall clock change cannot corrupt
 * the rolling window.
 */
public class CpsLimiter {

    private static final long WINDOW_NANOS = 1_000_000_000L;

    /** Accepted clicks in the last second (display). */
    private final ConcurrentLinkedDeque<Long> accepted = new ConcurrentLinkedDeque<>();
    /** Attempted clicks in the last second (cap enforcement). */
    private final ConcurrentLinkedDeque<Long> attempted = new ConcurrentLinkedDeque<>();
    private volatile long lastAcceptedAt;

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
        pruneAttempted(now);

        if (attempted.size() >= maxCps) {
            return false; // over cap — drop
        }
        if ("COOLDOWN".equals(InputBoosterConfig.getCpsMode()) && lastAcceptedAt > 0) {
            long minGapNs = Math.max(35L, WINDOW_NANOS / Math.max(1, maxCps)) * 1_000_000L;
            if (now - lastAcceptedAt < minGapNs) return false;
        }
        attempted.addLast(now);
        lastAcceptedAt = now;
        return true;
    }

    /** Record a click that was accepted (for CPS display). */
    public void recordClick() {
        long now = System.nanoTime();
        accepted.addLast(now);
        pruneAccepted(now);
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
        pruneAttempted(now);
        rollWindow(now);
    }

    /** Drops all recorded state, e.g. on world change or shutdown. */
    public void reset() {
        accepted.clear();
        attempted.clear();
        lastAcceptedAt = 0L;
        windowStartNanos = System.nanoTime();
        windowLimit = -1;
    }

    /**
     * Effective cap for the current window. The value is recomputed once per
     * second and then reused, so every click in a window sees the same limit.
     */
    public int effectiveMaxCps(long nowNanos) {
        rollWindow(nowNanos);
        int cached = windowLimit;
        if (cached > 0) return cached;

        int max = InputBoosterConfig.getMaxCps();
        long windowIndex = nowNanos / WINDOW_NANOS;
        // Deterministic jitter per window instead of a fresh random number per
        // click: the cap still varies naturally but stays consistent within
        // the second it applies to.
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

    private void pruneAttempted(long nowNanos) {
        while (!attempted.isEmpty() && nowNanos - attempted.peekFirst() > WINDOW_NANOS) {
            attempted.pollFirst();
        }
    }

    }