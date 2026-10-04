package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Isolates failures so one broken feature does not take the whole mod down.
 *
 * Errors are counted per module inside a rolling time window. When a module
 * crosses the threshold only that module is disabled and the window is reset,
 * so the rest of InputBooster keeps working and the module can recover
 * (re-enabled on the next successful tick after a full quiet window).
 *
 * The mod-level {@code active} flag is never cleared here.
 */
public class SafeModeManager {

    private static final long WINDOW_MS = 10_000L;
    private static final int ERROR_THRESHOLD = 5;

    private final Map<String, AtomicInteger> errors = new ConcurrentHashMap<>();
    private final Map<String, Long> windowStart = new ConcurrentHashMap<>();
    private final Map<String, Boolean> disabled = new ConcurrentHashMap<>();

    /**
     * Records an error for one module.
     *
     * @param module short module id, e.g. "movement", "profiles", "client tick"
     */
    public void recordError(String module, Throwable error) {
        String key = module == null ? "unknown" : module;
        if (!InputBoosterConfig.isSafeModeEnabled()) return;

        long now = System.currentTimeMillis();
        long start = windowStart.computeIfAbsent(key, k -> now);
        if (now - start > WINDOW_MS) {
            windowStart.put(key, now);
            errors.computeIfAbsent(key, k -> new AtomicInteger()).set(0);
        }

        int count = errors.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
        if (InputBoosterMod.eventLog != null && error != null) {
            InputBoosterMod.eventLog.add("Error in " + key + ": " + error.getClass().getSimpleName());
        }

        if (count >= ERROR_THRESHOLD && !isModuleDisabled(key)) {
            disableModule(key, error);
        }
    }

    /** Disables a single module and tells the module manager about it. */
    private void disableModule(String key, Throwable error) {
        disabled.put(key, Boolean.TRUE);
        errors.computeIfAbsent(key, k -> new AtomicInteger()).set(0);
        // Only real modules can be toggled in the module manager; arbitrary
        // error sources (e.g. "client tick") must not grow its key set.
        if (InputBoosterMod.moduleManager != null && isKnownModule(key)) {
            InputBoosterMod.moduleManager.setRuntime(key, false);
        }
        if (InputBoosterMod.eventLog != null) {
            InputBoosterMod.eventLog.add("Safe Mode disabled module '" + key + "' after repeated errors");
        }
        InputBoosterMod.LOGGER.warn("[SafeMode] Module '{}' disabled after {} errors ({}).",
            key, ERROR_THRESHOLD, error == null ? "unknown" : error.toString());
    }

    /**
     * Called from the client tick: re-enables a disabled module once it has been
     * quiet for a full window, so a transient failure self-heals.
     */
    public void tick() {
        if (disabled.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Boolean> entry : disabled.entrySet()) {
            String key = entry.getKey();
            long start = windowStart.getOrDefault(key, now);
            AtomicInteger counter = errors.get(key);
            if (now - start < WINDOW_MS || (counter != null && counter.get() > 0)) continue;

            disabled.remove(key);
            errors.remove(key);
            windowStart.remove(key);
            if (InputBoosterMod.moduleManager != null && isKnownModule(key)) {
                InputBoosterMod.moduleManager.setRuntime(key, true);
            }
            if (InputBoosterMod.eventLog != null) {
                InputBoosterMod.eventLog.add("Safe Mode re-enabled module '" + key + "'");
            }
            InputBoosterMod.LOGGER.info("[SafeMode] Module '{}' re-enabled after a quiet period.", key);
        }
    }

    private static boolean isKnownModule(String key) {
        return switch (key) {
            case "combat", "movement", "debug", "profiles", "anti_idle", "replay" -> true;
            default -> false;
        };
    }

    public boolean isModuleDisabled(String module) {
        return Boolean.TRUE.equals(disabled.get(module));
    }

    public boolean isSafeModeActive() {
        return !disabled.isEmpty();
    }

    public String statusLine() {
        return disabled.isEmpty() ? "Safe Mode: OK" : "Safe Mode: " + disabled.keySet();
    }

    /** Clears all state; used on shutdown and world changes. */
    public void reset() {
        errors.clear();
        windowStart.clear();
        disabled.clear();
    }
}