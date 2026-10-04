package dev.inputbooster;

import dev.inputbooster.feature.ReplayRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the duplicate-suppression tokens and for keeping replay
 * separate from physical input.
 */
class SuppressionAndOriginTest {

    @BeforeEach
    void reset() {
        InputActionQueue.clear();
    }

    @Test
    void suppressionTokenOnlyAppliesToTheTickThatIssuedIt() {
        InputDrainer.beginTick();
        InputDrainer.markAttackHandled();

        assertTrue(InputDrainer.consumeAttackSuppression(), "vanilla must be suppressed this tick");
        assertFalse(InputDrainer.consumeAttackSuppression(), "token is single use");
    }

    @Test
    void staleTokenCannotSuppressALaterTick() {
        InputDrainer.beginTick();
        InputDrainer.markAttackHandled();
        // Vanilla never consumed it this tick.
        InputDrainer.beginTick();
        assertFalse(InputDrainer.consumeAttackSuppression(),
            "a token from a previous tick must never cancel a real attack");
    }

    @Test
    void useTokenBehavesTheSameWay() {
        InputDrainer.beginTick();
        InputDrainer.markUseHandled();
        assertTrue(InputDrainer.consumeUseSuppression());

        InputDrainer.beginTick();
        InputDrainer.markUseHandled();
        InputDrainer.beginTick();
        assertFalse(InputDrainer.consumeUseSuppression());
    }

    @Test
    void tickIdAdvancesMonotonically() {
        int before = InputDrainer.currentTickId();
        InputDrainer.beginTick();
        InputDrainer.beginTick();
        assertEquals(before + 2, InputDrainer.currentTickId());
    }

    @Test
    void replayEventsAreTaggedSeparatelyFromPhysicalInput() {
        assertTrue(InputActionQueue.queue(InputAction.ATTACK_PRESSED, InputAction.Origin.INPUT));
        assertTrue(InputActionQueue.queue(InputAction.ATTACK_PRESSED, InputAction.Origin.REPLAY));
        assertTrue(InputActionQueue.queue(InputAction.JUMP_PRESSED, InputAction.Origin.INPUT));

        assertEquals(2, InputActionQueue.pendingPhysical(), "replay events must not count as live input");
        assertEquals(3, InputActionQueue.size());
    }

    @Test
    void playbackYieldsWhileRealInputIsQueued() {
        ReplayRecorder replay = new ReplayRecorder();
        replay.startRecording();
        replay.onQueued(InputAction.ATTACK_PRESSED);
        replay.stopRecording();
        assertEquals(1, replay.getRecordedCount());

        // A real click is waiting in the queue: playback must not inject on top.
        InputActionQueue.queue(InputAction.ATTACK_PRESSED, InputAction.Origin.INPUT);
        replay.startPlayback();
        for (int i = 0; i < 50; i++) replay.tick();

        assertEquals(1, InputActionQueue.size(), "no replayed event may be injected while live input is pending");
    }
}