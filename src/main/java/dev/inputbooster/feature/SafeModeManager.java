package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

import java.util.concurrent.atomic.AtomicInteger;

public class SafeModeManager {
    private final AtomicInteger recentErrors = new AtomicInteger();
    private long windowStart = System.currentTimeMillis();
    private volatile boolean safeModeActive;

    public void recordError(String source, Exception error) {
        if (!InputBoosterConfig.isSafeModeEnabled()) return;
        long now = System.currentTimeMillis();
        if (now - windowStart > 10_000L) {
            windowStart = now;
            recentErrors.set(0);
        }
        int count = recentErrors.incrementAndGet();
        if (InputBoosterMod.eventLog != null) {
            InputBoosterMod.eventLog.add("Error in " + source + ": " + error.getClass().getSimpleName());
        }
        if (count >= 5) {
            safeModeActive = true;
            InputBoosterMod.active = false;
            if (InputBoosterMod.eventLog != null) {
                InputBoosterMod.eventLog.add("Safe mode disabled active modules after repeated errors");
            }
        }
    }

    public boolean isSafeModeActive() {
        return safeModeActive;
    }
}
