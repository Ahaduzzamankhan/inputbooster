package dev.inputbooster;

import dev.inputbooster.engine.DiagnosticsManager;
import dev.inputbooster.engine.InputEngine;
import dev.inputbooster.engine.KeyBindingProcessor;
import dev.inputbooster.engine.KeyStateManager;
import dev.inputbooster.feature.OverlayLayout;
import dev.inputbooster.input.KeyBindingSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the input engine, which is where duplicate and missing events used
 * to come from.
 *
 * <p>The central invariant: one physical edge produces exactly one action, no
 * matter how many sources report it. Everything else here supports that.
 */
class InputEngineTest {

    private static KeyBindingSet vanillaDefaults() {
        return KeyBindingSet.of(Map.of(), KeyBindingSet.defaultCodes(0, 1, 2));
    }

    private static int code(KeyBindingSet set, int slot) {
        return set.codes[slot];
    }

    @BeforeEach
    void reset() {
        InputActionQueue.clear();
        InputBoosterConfig.setMouseClickDedupeMs(0);
    }

    @Test
    void keyStateReportsOnlyRealEdges() {
        KeyStateManager state = new KeyStateManager();

        assertTrue(state.updateSlot(0, true), "first press is an edge");
        assertFalse(state.updateSlot(0, true), "a repeated press is not an edge");
        assertTrue(state.updateSlot(0, false), "release is an edge");
        assertFalse(state.updateSlot(0, false), "a repeated release is not an edge");
        assertTrue(state.isSlotDown(0) == false);
    }

    @Test
    void rawBitsAreAddressableWithoutAllocation() {
        KeyStateManager state = new KeyStateManager();
        state.setRaw(340, true);
        state.setRaw(7, true);

        assertTrue(state.isRawDown(340), "high key codes must be stored");
        assertTrue(state.isRawDown(7));
        assertFalse(state.isRawDown(341));

        state.setRaw(340, false);
        assertFalse(state.isRawDown(340));
        assertTrue(state.isRawDown(7), "other bits must be untouched");
    }

    @Test
    void releaseAllClearsEverything() {
        KeyStateManager state = new KeyStateManager();
        state.updateSlot(3, true);
        state.setRaw(87, true);

        state.releaseAll();

        assertFalse(state.isSlotDown(3));
        assertFalse(state.isRawDown(87));
    }

    @Test
    void bindingProcessorMapsCodesToSlots() {
        KeyBindingProcessor processor = new KeyBindingProcessor();
        KeyBindingSet set = vanillaDefaults();
        assertTrue(processor.update(set), "first publication always rebuilds");
        assertFalse(processor.update(set), "an unchanged set must not rebuild");

        for (int slot = 0; slot < KeyStateManager.SLOTS; slot++) {
            assertEquals(slot, processor.slotForCode(code(set, slot)),
                "slot " + slot + " must resolve to itself");
        }
        assertEquals(-1, processor.slotForCode(999), "unknown codes belong to someone else");
        assertEquals(-1, processor.slotForCode(-5));
    }

    @Test
    void eventSourceProducesOneActionPerEdge() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.EVENT);
        KeyBindingSet set = vanillaDefaults();
        engine.publishBindings(set);
        int attack = code(set, KeyBindingSet.ATTACK);

        engine.onKeyState(attack, true);
        assertEquals(1, InputActionQueue.size(), "one press, one action");

        engine.onKeyState(attack, true);
        assertEquals(1, InputActionQueue.size(), "a repeated report must not duplicate the action");

        engine.onKeyState(attack, false);
        assertEquals(2, InputActionQueue.size(), "release produces its own action");

        assertEquals(InputAction.ATTACK_PRESSED, InputActionQueue.poll().action());
        assertEquals(InputAction.ATTACK_RELEASED, InputActionQueue.poll().action());
    }

    @Test
    void bothSourcesReportingTheSamePressProduceOneAction() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.EVENT);
        KeyBindingSet set = vanillaDefaults();
        engine.publishBindings(set);
        int attack = code(set, KeyBindingSet.ATTACK);

        engine.onKeyState(attack, true);              // keyboard hook
        engine.pollSnapshot(KeySnapshot.EMPTY);       // poller, all released
        engine.pollSnapshot(KeySnapshot.EMPTY);

        // The empty snapshots report "released" for every slot, so the press is
        // released once and nothing is duplicated on repeat polls.
        assertEquals(2, InputActionQueue.size(), "press and one release");
        assertEquals(InputAction.ATTACK_PRESSED, InputActionQueue.poll().action());
        assertEquals(InputAction.ATTACK_RELEASED, InputActionQueue.poll().action());
    }

    @Test
    void pollModeNeverDispatchesEventHooks() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.POLL);
        KeyBindingSet set = vanillaDefaults();
        engine.publishBindings(set);

        engine.onKeyState(code(set, KeyBindingSet.ATTACK), true);

        assertEquals(0, InputActionQueue.size(),
            "POLL mode leaves dispatch to the poller so nothing is applied twice");
    }

    @Test
    void keysTheModDoesNotOwnAreIgnored() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.EVENT);
        engine.publishBindings(vanillaDefaults());

        engine.onKeyState(999, true);

        assertEquals(0, InputActionQueue.size(), "another mod's key must not become an action");
    }

    @Test
    void diagnosticsCountAndReset() {
        DiagnosticsManager diagnostics = new DiagnosticsManager();
        diagnostics.keyboardEvent();
        diagnostics.mouseEvent();
        diagnostics.scrollEvent();
        diagnostics.actionSubmitted();
        diagnostics.actionDropped();
        diagnostics.repeatIgnored();
        diagnostics.duplicateSuppressed();
        diagnostics.recordDispatchNanos(2_000);

        assertEquals(1, diagnostics.keyboardEvents());
        assertEquals(1, diagnostics.mouseEvents());
        assertEquals(1, diagnostics.scrollEvents());
        assertEquals(1, diagnostics.actionsSubmitted());
        assertEquals(1, diagnostics.actionsDropped());
        assertEquals(1, diagnostics.repeatsIgnored());
        assertEquals(1, diagnostics.duplicatesSuppressed());
        assertEquals(2.0, diagnostics.dispatchMicros(), 1e-9);

        diagnostics.reset();
        assertEquals(0, diagnostics.keyboardEvents());
        assertEquals(0.0, diagnostics.dispatchMicros(), 1e-9);
    }

    @Test
    void overlayLayoutUsesCornersAndStaysOnScreen() {
        assertEquals(new OverlayLayout.Pos(0, 0),
            OverlayLayout.compute(800, 600, 120, 60, OverlayLayout.TOP_LEFT));
        assertEquals(new OverlayLayout.Pos(680, 0),
            OverlayLayout.compute(800, 600, 120, 60, OverlayLayout.TOP_RIGHT));
        assertEquals(new OverlayLayout.Pos(0, 540),
            OverlayLayout.compute(800, 600, 120, 60, OverlayLayout.BOTTOM_LEFT));
        assertEquals(new OverlayLayout.Pos(680, 540),
            OverlayLayout.compute(800, 600, 120, 60, OverlayLayout.BOTTOM_RIGHT));

        // A panel larger than the screen must not produce negative coordinates:
        // the old overlay was drawn off-screen after a resize.
        assertEquals(new OverlayLayout.Pos(0, 0),
            OverlayLayout.compute(100, 40, 300, 200, OverlayLayout.BOTTOM_RIGHT));
        assertEquals(new OverlayLayout.Pos(0, 0),
            OverlayLayout.compute(100, 40, 300, 200, OverlayLayout.TOP_RIGHT));
        assertNotEquals(0, OverlayLayout.compute(800, 600, 120, 60, OverlayLayout.TOP_RIGHT).x());
    }

    @Test
    void overlayPositionNamesCoverEveryCorner() {
        String[] seen = new String[4];
        for (int i = 0; i < 4; i++) seen[i] = OverlayLayout.positionName(i);
        assertEquals("top-left", seen[0]);
        assertEquals("top-right", seen[1]);
        assertEquals("bottom-left", seen[2]);
        assertEquals("bottom-right", seen[3]);
    }
}
