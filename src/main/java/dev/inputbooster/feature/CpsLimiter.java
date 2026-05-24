package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CpsLimiter — Smart CPS Limiter with Real-Time Feedback (Feature 3).
 *
 * v3.0.0 upgrade: now an actual limiter.
 *  - Configurable max CPS (1–20, default 20).
 *  - allowClick() is called by InputDrainer before processing ATTACK_PRESSED.
 *    Returns false (drop the click) when the cap is exceeded.
 *  - recordClick() records clicks that passed the cap gate for CPS display.
 *  - getCps() returns the current 1-second rolling CPS for the HUD bar.
 *
 * Author: Ahaduzzaman Khan
 */
public class CpsLimiter {

    // All accepted clicks in the last 1 second (for CPS display)
    private final ConcurrentLinkedDeque<Long> accepted = new ConcurrentLinkedDeque<>();
    // All attempted clicks in the last 1 second (for cap enforcement)
    private final ConcurrentLinkedDeque<Long> attempted = new ConcurrentLinkedDeque<>();
    private volatile long lastAcceptedAt;

    /**
     * Call before processing an ATTACK_PRESSED event.
     * Returns true if the click should be allowed, false if it should be dropped.
     */
    public boolean allowClick() {
        if (!InputBoosterConfig.isCpsLimiterEnabled()) return true;
        int maxCps = effectiveMaxCps();
        long now = System.currentTimeMillis();

        // Prune stale attempts
        while (!attempted.isEmpty() && now - attempted.peekFirst() > 1000) {
            attempted.pollFirst();
        }

        if (attempted.size() >= maxCps) {
            return false; // over cap — drop
        }
        if ("COOLDOWN".equals(InputBoosterConfig.getCpsMode()) && lastAcceptedAt > 0) {
            long minGapMs = Math.max(35L, 1000L / Math.max(1, maxCps));
            if (now - lastAcceptedAt < minGapMs) return false;
        }
        attempted.addLast(now);
        lastAcceptedAt = now;
        return true;
    }

    /** Record a click that was accepted (for CPS display). */
    public void recordClick() {
        long now = System.currentTimeMillis();
        accepted.addLast(now);
        while (!accepted.isEmpty() && now - accepted.peekFirst() > 1000) {
            accepted.pollFirst();
        }
    }

    /** Current CPS (accepted clicks in last 1 second). */
    public int getCps() {
        long now = System.currentTimeMillis();
        while (!accepted.isEmpty() && now - accepted.peekFirst() > 1000) {
            accepted.pollFirst();
        }
        return accepted.size();
    }

    /** Max CPS from config (for HUD bar max scale). */
    public int getMaxCps() {
        return effectiveMaxCps();
    }

    /** Called every game tick — prune stale entries. */
    public void tick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        while (!accepted.isEmpty() && now - accepted.peekFirst() > 1000) accepted.pollFirst();
        while (!attempted.isEmpty() && now - attempted.peekFirst() > 1000) attempted.pollFirst();
    }

    private int effectiveMaxCps() {
        int max = InputBoosterConfig.getMaxCps();
        return switch (InputBoosterConfig.getCpsMode()) {
            case "HUMANIZED" -> Math.max(1, max - ThreadLocalRandom.current().nextInt(0, 3));
            case "WEAPON_AWARE" -> Math.min(max, 16);
            case "COOLDOWN" -> Math.min(max, 18);
            default -> max;
        };
    }
}
