package dev.inputbooster;

import dev.inputbooster.InputPollingThread;
import dev.inputbooster.engine.CompatibilityManager;
import dev.inputbooster.engine.InputEngine;
import dev.inputbooster.input.KeyBindingSet;
import dev.inputbooster.input.KeyboardRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the custom keyboard handler, the handler/source selection rules and
 * the key-mapping fallback.
 *
 * <p>The central property: exactly one input source owns input at a time, the
 * choice is deterministic, and switching between them never duplicates an action
 * or leaves a key held.
 */
class HandlerAndBindingTest {

    @BeforeEach
    void reset() {
        InputActionQueue.clear();
        InputBoosterConfig.setMouseClickDedupeMs(0);
        InputBoosterMod.compatibilityManager = null;
        InputBoosterConfig.setCustomKeyboardHandlerEnabled(false);
    }

    @AfterEach
    void cleanup() {
        InputActionQueue.clear();
        InputBoosterMod.compatibilityManager = null;
        InputBoosterConfig.setCustomKeyboardHandlerEnabled(false);
    }

    @Test
    void sourceSelectionIsDeterministic() {
        // The gate (pause, screen open, no world) does not change which source
        // is used: a gated loop still needs one to follow state with.
        for (boolean gated : new boolean[]{false, true}) {
            // A usable native sampler always wins.
            assertSource(InputPollingThread.Source.RAW, gated, true, true, true);
            assertSource(InputPollingThread.Source.RAW, gated, true, false, false);

            // No native sampler and no custom handler: tick snapshot.
            assertSource(InputPollingThread.Source.SNAPSHOT, gated, false, false, false);
            assertSource(InputPollingThread.Source.SNAPSHOT, gated, false, false, true);

            // Custom handler enabled and its hooks are live: it owns input.
            assertSource(InputPollingThread.Source.IDLE, gated, false, true, true);

            // Custom handler enabled but its hooks never fired: using nothing
            // would disable input entirely, so the snapshot is used instead.
            assertSource(InputPollingThread.Source.SNAPSHOT, gated, false, true, false);
        }
    }

    private static void assertSource(InputPollingThread.Source expected,
                                     boolean gated, boolean rawAvailable,
                                     boolean customHandler, boolean eventSourceLive) {
        assertEquals(expected,
            InputPollingThread.chooseSource(gated, rawAvailable, customHandler, eventSourceLive),
            "gated=" + gated + " raw=" + rawAvailable + " custom=" + customHandler + " live=" + eventSourceLive);
    }

    @Test
    void customHandlerIsOnlyLiveOnceItsHooksDeliverAnEvent() {
        InputBoosterConfig.setCustomKeyboardHandlerEnabled(true);
        InputBoosterMod.compatibilityManager = new CompatibilityManager();

        assertTrue(KeyboardRouter.customHandlerEnabled());
        assertFalse(KeyboardRouter.customHandlerLive(), "enabling the option alone must not claim the input path");

        InputBoosterMod.compatibilityManager.markEventSourceActive();
        assertTrue(KeyboardRouter.customHandlerLive());

        InputBoosterConfig.setCustomKeyboardHandlerEnabled(false);
        assertFalse(KeyboardRouter.customHandlerLive());
    }

    @Test
    void switchingHandlerModeDoesNotRepeatActions() {
        InputEngine engine = new InputEngine();
        KeyBindingSet set = KeyBindingSet.of(Map.of(), KeyBindingSet.defaultCodes(0, 1, 2));
        engine.publishBindings(set);

        engine.setMode(InputEngine.Mode.EVENT);
        engine.onKeyState(KeyBindingSet.ATTACK, true);
        assertEquals(1, InputActionQueue.size(), "one press, one action");

        // Switch mid-press: the state must not be re-emitted by the new mode.
        engine.setMode(InputEngine.Mode.POLL);
        engine.onKeyState(KeyBindingSet.ATTACK, true);
        assertEquals(1, InputActionQueue.size(), "a switch must not duplicate the press");

        // Release and switch back; the next press is a new edge.
        engine.onKeyState(KeyBindingSet.ATTACK, false);
        engine.setMode(InputEngine.Mode.EVENT);
        engine.onKeyState(KeyBindingSet.ATTACK, true);
        assertEquals(2, InputActionQueue.size(), "a fresh press after a switch is one action");
    }

    @Test
    void unresolvedSlotsAreNamedInsteadOfSilentlyGuessed() {
        KeyBindingSet set = KeyBindingSet.of(Map.of(), KeyBindingSet.defaultCodes(0, 1, 2));

        assertFalse(set.complete(), "no mappings were supplied, so every slot fell back");
        assertEquals((1 << KeyBindingSet.SLOTS) - 1, set.unresolvedMask());
        assertEquals(0, set.unreadableMask(),
            "a missing mapping is not an accessor failure and must be reported differently");
        List<String> names = set.unresolvedNames();
        assertEquals(KeyBindingSet.SLOTS, names.size());
        assertTrue(names.contains("attack") && names.contains("jump"), names.toString());
    }

    @Test
    void keyMappingRegistrationIsClaimedOnce() {
        // A second registration pass would add duplicate rows to the Controls
        // screen, so only the first caller may register.
        assertTrue(InputBoosterMod.claimKeyMappingRegistration());
        assertFalse(InputBoosterMod.claimKeyMappingRegistration());
        assertFalse(InputBoosterMod.claimKeyMappingRegistration());
    }
}
