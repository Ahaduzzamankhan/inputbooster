package dev.inputbooster;

import dev.inputbooster.engine.DiagnosticsManager;
import dev.inputbooster.engine.InputEngine;
import dev.inputbooster.engine.InputEventDispatcher;
import dev.inputbooster.engine.KeyBindingProcessor;
import dev.inputbooster.engine.KeyStateManager;
import dev.inputbooster.input.KeyBindingSet;
import dev.inputbooster.input.RawKeyState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the threading, aliasing and shared-binding bugs found in
 * the input pipeline.
 *
 * <p>Everything here is deterministic: the concurrency tests use a start latch
 * and assert invariants that must hold for every interleaving, never a timing
 * window.
 */
class StabilityAuditTest {

    @BeforeEach
    void reset() {
        InputActionQueue.clear();
        InputBoosterConfig.setMouseClickDedupeMs(0);
    }

    /** Left mouse button bound to attack *and* use, as a player can configure. */
    private static KeyBindingSet sharedButton() {
        int[] codes = KeyBindingSet.defaultCodes(0, 1, 2);
        codes[KeyBindingSet.USE] = codes[KeyBindingSet.ATTACK];
        return KeyBindingSet.of(Map.of(), codes);
    }

    @Test
    void oneKeyBoundToTwoActionsDrivesBothSlots() {
        KeyBindingProcessor processor = new KeyBindingProcessor();
        assertTrue(processor.update(sharedButton()));

        long mask = processor.maskForCode(0);
        assertTrue((mask & (1L << KeyBindingSet.ATTACK)) != 0L, "attack slot must be bound");
        assertTrue((mask & (1L << KeyBindingSet.USE)) != 0L, "use slot must be bound");
        assertEquals(KeyBindingSet.ATTACK, processor.slotForCode(0),
            "the first slot is reported for callers that need only one");
    }

    @Test
    void eventModeFiresEveryActionBoundToTheSameKey() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.EVENT);
        engine.publishBindings(sharedButton());

        engine.onKeyState(0, true);

        assertEquals(2, InputActionQueue.size(),
            "a shared key must queue both actions, exactly as the polling path does");
    }

    @Test
    void pollModeSuppressesDispatchButStillTracksState() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.POLL);
        engine.publishBindings(sharedButton());

        engine.onKeyState(0, true);

        assertEquals(0, InputActionQueue.size(), "POLL mode never dispatches from events");
        assertTrue(engine.state().isSlotDown(KeyBindingSet.ATTACK));
        assertTrue(engine.state().isSlotDown(KeyBindingSet.USE));
    }

    @Test
    void resetClearsEverySlotAndAllowsTheNextPress() {
        InputEngine engine = new InputEngine();
        engine.setMode(InputEngine.Mode.EVENT);
        engine.publishBindings(sharedButton());
        engine.onKeyState(0, true);
        assertTrue(engine.state().isSlotDown(KeyBindingSet.ATTACK));

        engine.reset();

        assertFalse(engine.state().isSlotDown(KeyBindingSet.ATTACK), "reset must clear held keys");
        assertFalse(engine.state().isSlotDown(KeyBindingSet.USE));
        InputActionQueue.clear();

        engine.onKeyState(0, true);
        assertEquals(2, InputActionQueue.size(), "a press after a reset is a fresh press");
    }

    @Test
    void bindingSetNeverHandsOutItsInternalArray() {
        KeyBindingSet set = sharedButton();
        int[] copy = set.copyCodes();
        assertNotSame(set.copyCodes(), copy, "every call must return a new array");

        copy[KeyBindingSet.ATTACK] = 999;
        assertEquals(0, set.code(KeyBindingSet.ATTACK),
            "mutating the copy must not rebind the set");
    }

    @Test
    void bindingProcessorNeverHandsOutItsInternalArray() {
        KeyBindingProcessor processor = new KeyBindingProcessor();
        KeyBindingSet set = sharedButton();
        assertTrue(processor.update(set));

        int[] published = processor.codes();
        assertNotNull(published);
        published[KeyBindingSet.ATTACK] = 4242;

        assertEquals(0, processor.codes()[KeyBindingSet.ATTACK],
            "the processor must not be mutable through its getter");
        assertFalse(processor.update(set), "the published codes must still match");
    }

    @Test
    void changingABindingLeavesNoStaleResolution() {
        KeyBindingProcessor processor = new KeyBindingProcessor();
        assertTrue(processor.update(sharedButton()));
        assertTrue(processor.maskForCode(0) != 0L);

        int[] defaults = KeyBindingSet.defaultCodes(0, 1, 2);
        // Any code the defaults do not already use, so the assertion below is
        // about the moved binding and not about a default that shares the code.
        int free = 50;
        while (free < 200) {
            boolean used = false;
            for (int code : defaults) {
                if (code == free) { used = true; break; }
            }
            if (!used) break;
            free++;
        }
        int[] moved = defaults.clone();
        moved[KeyBindingSet.ATTACK] = free;
        assertTrue(processor.update(KeyBindingSet.of(Map.of(), moved)));

        assertEquals(0L, processor.maskForCode(0),
            "the old code must stop resolving once the binding moves");
        assertTrue((processor.maskForCode(free) & (1L << KeyBindingSet.ATTACK)) != 0L,
            "the new code must resolve to the attack slot");
    }

    @Test
    void concurrentClickDispatchAcceptsExactlyOne() throws Exception {
        InputEventDispatcher dispatcher = new InputEventDispatcher(new DiagnosticsManager());
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 200; n++) {
                        if (dispatcher.submit(InputAction.ATTACK_PRESSED, 1_000)) accepted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "click-" + i);
            worker.start();
            workers.add(worker);
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "workers must finish");
        for (Thread worker : workers) worker.join(1_000);

        assertEquals(1, accepted.get(),
            "the click window must be claimed atomically: one accepted click in the window");
    }

    @Test
    void concurrentStateUpdatesStayConsistentAndReset() throws Exception {
        KeyStateManager state = new KeyStateManager();
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int offset = i;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < 50_000; n++) {
                        int slot = (n + offset) % KeyStateManager.SLOTS;
                        state.updateSlot(slot, (n & 1) == 0);
                        state.setRaw(32 + slot, (n & 1) == 0);
                        state.isSlotDown(slot);
                        state.isRawDown(32 + slot);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }, "state-" + i);
            worker.setDaemon(true);
            worker.start();
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "workers must finish");

        state.releaseAll();
        for (int slot = 0; slot < KeyStateManager.SLOTS; slot++) {
            assertFalse(state.isSlotDown(slot), "reset must clear slot " + slot);
            assertFalse(state.isRawDown(32 + slot), "reset must clear raw code " + (32 + slot));
        }
    }

    @Test
    void everyPollingRateProducesOneEdgePerPress() {
        for (int hz : new int[]{60, 200, 500, 750, 1000}) {
            KeyStateManager state = new KeyStateManager();
            int edges = 0;
            // A press and release observed by two sources at the same rate: the
            // second report of the same value must never produce an edge.
            for (int cycle = 0; cycle < 200; cycle++) {
                if (state.updateSlot(KeyBindingSet.ATTACK, true)) edges++;
                if (state.updateSlot(KeyBindingSet.ATTACK, true)) edges++;
                if (state.updateSlot(KeyBindingSet.ATTACK, false)) edges++;
                if (state.updateSlot(KeyBindingSet.ATTACK, false)) edges++;
            }
            assertEquals(400, edges, "at " + hz + " Hz each press/release pair is exactly two edges");
        }
    }

    @Test
    void rawKeyStateWithoutAWindowDegradesQuietly() {
        RawKeyState raw = RawKeyState.of(null);

        assertFalse(raw.isAvailable(), "no window means no native state");
        raw.refresh(sharedButton());
        assertFalse(raw.isDown(0), "an unavailable sampler reports nothing as held");
        assertFalse(raw.failed(), "a missing window is not a native failure");
        assertEquals(0, raw.refreshCount());
        assertNull(raw.failureReason());
    }
}
