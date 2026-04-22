package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * CpsLimiter
 *
 * Tracks clicks per second (CPS) using a sliding 1-second window.
 * This is purely informational — it logs your CPS to the F3 screen
 * so you know your recovered inputs are not exceeding natural human CPS.
 *
 * InputBooster does NOT auto-click. This just measures real input frequency.
 *
 * Author: Ahaduzzaman Khan
 */
public class CpsLimiter {

    private final ConcurrentLinkedDeque<Long> clickTimestamps = new ConcurrentLinkedDeque<>();
    private int lastCps = 0;

    /** Call this whenever an attack click is registered. */
    public void recordClick() {
        long now = System.currentTimeMillis();
        clickTimestamps.addLast(now);
        // Remove clicks older than 1 second
        while (!clickTimestamps.isEmpty() && now - clickTimestamps.peekFirst() > 1000) {
            clickTimestamps.pollFirst();
        }
        lastCps = clickTimestamps.size();
    }

    public int getCps() {
        // Purge stale entries
        long now = System.currentTimeMillis();
        while (!clickTimestamps.isEmpty() && now - clickTimestamps.peekFirst() > 1000) {
            clickTimestamps.pollFirst();
        }
        return clickTimestamps.size();
    }

    /** Called every game tick to update CPS tracking. */
    public void tick(MinecraftClient client) {
        // Purge stale entries each tick to keep getCps() accurate
        long now = System.currentTimeMillis();
        while (!clickTimestamps.isEmpty() && now - clickTimestamps.peekFirst() > 1000) {
            clickTimestamps.pollFirst();
        }
        lastCps = clickTimestamps.size();
    }
}
