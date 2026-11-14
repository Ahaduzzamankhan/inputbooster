package dev.inputbooster.engine;

import dev.inputbooster.InputAction;
import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterMod;

/**
 * Single exit point for every action the engine produces.
 *
 * <p>Ordering is preserved because there is exactly one dispatcher and it is
 * called in the order the edges were detected. Duplicates are already impossible
 * upstream (see {@link KeyStateManager}); the click guard here covers the one
 * case upstream cannot: two physical clicks so close together that a replay or a
 * double event would otherwise fire twice.
 *
 * <p>The bounded queue is the existing, tested one. A rejected action is counted
 * and reported instead of silently disappearing.
 */
public final class InputEventDispatcher {

    private final DiagnosticsManager diagnostics;
    private long lastClickNanos;

    public InputEventDispatcher(DiagnosticsManager diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Submits one action.
     *
     * @param action the action to apply on the next client tick
     * @param dedupeWindowMs minimum gap between two accepted clicks, 0 to disable
     * @return true when the action was queued
     */
    public boolean submit(InputAction action, int dedupeWindowMs) {
        if (action == null) return false;
        long start = System.nanoTime();

        if (dedupeWindowMs > 0 && isClick(action)) {
            long window = dedupeWindowMs * 1_000_000L;
            if (lastClickNanos != 0L && start - lastClickNanos < window) {
                diagnostics.duplicateSuppressed();
                return false;
            }
        }

        boolean queued = InputActionQueue.queue(action, InputAction.Origin.INPUT);
        long elapsed = System.nanoTime() - start;
        diagnostics.recordDispatchNanos(elapsed);

        if (!queued) {
            diagnostics.actionDropped();
            return false;
        }
        if (isClick(action)) lastClickNanos = start;
        diagnostics.actionSubmitted();
        InputBoosterMod.recoveredInputs.incrementAndGet();
        if (InputBoosterMod.replayRecorder != null) InputBoosterMod.replayRecorder.onQueued(action);
        if (InputBoosterMod.eventLog != null && action == InputAction.ATTACK_PRESSED) {
            InputBoosterMod.eventLog.add("Attack queued");
        }
        return true;
    }

    /** Clears the click guard, e.g. after a screen closed or a world changed. */
    public void reset() {
        lastClickNanos = 0L;
    }

    private static boolean isClick(InputAction action) {
        return action == InputAction.ATTACK_PRESSED || action == InputAction.USE_PRESSED;
    }
}
