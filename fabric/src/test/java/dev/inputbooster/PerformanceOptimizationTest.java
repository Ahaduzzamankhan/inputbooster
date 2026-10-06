package dev.inputbooster;

import dev.inputbooster.input.KeyBindingSet;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the real optimisations that shipped in 4.0.0 and the contracts they
 * depend on.
 *
 * <ul>
 *   <li>The polling thread's idle decision: a state in which nothing sampled
 *       can be kept must be the only one that drops to the idle rate.</li>
 *   <li>{@link KeyBindingSet#matches(int[], boolean)}: the change gate the
 *       client tick uses to skip rebuilding the published binding set.</li>
 * </ul>
 */
class PerformanceOptimizationTest {

    @Test
    void idlePollingOnlyRunsWhereNothingCanBeKept() {
        // Full rate needs all of: active, initialised, not shutting down,
        // in a world, not paused. Each line removes exactly one condition.
        assertTrue(InputPollingThread.shouldPollFast(true, true, false, true, false),
            "in-world and active: full rate");
        assertFalse(InputPollingThread.shouldPollFast(false, true, false, true, false),
            "mod inactive");
        assertFalse(InputPollingThread.shouldPollFast(true, false, false, true, false),
            "not initialised");
        assertFalse(InputPollingThread.shouldPollFast(true, true, true, true, false),
            "shutting down");
        assertFalse(InputPollingThread.shouldPollFast(true, true, false, false, false),
            "no world loaded");
        assertFalse(InputPollingThread.shouldPollFast(true, true, false, true, true),
            "game paused");
    }

    @Test
    void idleRateStaysLowButNoticesStateChanges() {
        assertTrue(InputPollingThread.IDLE_POLL_HZ <= 20,
            "the idle rate must be far below the configured one");
        assertTrue(InputPollingThread.IDLE_POLL_HZ >= 5,
            "but wake often enough to notice the player returning");
    }

    @Test
    void bindingSetsMatchOnlyOnIdenticalContents() {
        int[] defaults = KeyBindingSet.defaultCodes(100, 101, 102);
        Map<String, KeyMapping> bindings = KeyBindingSet.map(
            null, null, null, null, null, null, null, null, null, null, null, null);
        KeyBindingSet set = KeyBindingSet.of(bindings, defaults);

        assertFalse(set.complete, "null bindings fall back to the defaults");
        assertTrue(set.matches(defaults.clone(), false),
            "identical codes and completeness must match");
        assertFalse(set.matches(defaults.clone(), true),
            "a different completeness flag must not match");
        assertFalse(set.matches(new int[]{999, 101, 102, 0, 0, 0, 0, 0, 0, 0, 0, 0}, false),
            "a changed code must not match");
    }
}
