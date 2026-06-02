package dev.inputbooster;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InputPollingThread — High-frequency polling thread. Runs at up to 1000 Hz.
 *
 * FIX: poll() now reads keySnapshot exactly once per cycle into a local
 * variable. Previously the volatile reference could be replaced mid-poll
 * by the game tick thread, causing some keys to be read from the old snapshot
 * and others from the new one — a torn read that could manufacture phantom
 * transitions (key appears pressed→released in the same cycle with no real
 * change). Capturing once makes every cycle operate on a single consistent
 * snapshot.
 *
 * v3.0.0 additions:
 *  - Adaptive Burst Mode (Feature 1): when FPS drops >20% in one second,
 *    automatically spikes poll rate to 1000 Hz for 3 seconds, then returns
 *    to normal. Controlled by BurstModeManager.
 *
 * Version: 3.0.0
 * Author: Ahaduzzaman Khan
 */
public class InputPollingThread extends Thread {

    private final AtomicBoolean running    = new AtomicBoolean(true);
    private final AtomicInteger pollRateHz = new AtomicInteger(200);

    private boolean prevAttack, prevUse, prevSprint, prevSneak;
    private boolean prevJump, prevForward, prevBack, prevLeft, prevRight;
    private boolean prevDrop, prevSwap, prevPickBlock;

    public InputPollingThread(int initialHz) {
        setName("InputBooster-PollerThread");
        setDaemon(true);
        setPriority(Thread.MAX_PRIORITY - 1);
        this.pollRateHz.set(clampHz(initialHz));
    }

    public void setPollRateHz(int hz) {
        this.pollRateHz.set(clampHz(hz));
    }

    private int clampHz(int hz) {
        return Math.max(60, Math.min(1000, hz));
    }

    @Override
    public void run() {
        InputBoosterMod.LOGGER.info("[InputBooster] Polling thread started at {} Hz.",
            pollRateHz.get());

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long loopStart = System.nanoTime();

            try {
                poll();
            } catch (Exception e) {
                InputBoosterMod.LOGGER.warn("[InputBooster] Polling error", e);
            }

            // Burst mode may override the configured poll rate
            int hz = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting()
                     ? 1000
                     : pollRateHz.get();

            long targetNs = 1_000_000_000L / hz;
            long elapsed  = System.nanoTime() - loopStart;
            long sleepNs  = targetNs - elapsed;

            if (sleepNs > 0) {
                try {
                    long sleepMs = sleepNs / 1_000_000L;
                    int  nanos   = (int)(sleepNs % 1_000_000L);
                    if (sleepMs > 0) Thread.sleep(sleepMs, nanos);
                    else if (nanos > 0) Thread.sleep(0, nanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        InputBoosterMod.LOGGER.info("[InputBooster] Polling thread stopped.");
    }

    private void poll() {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()
            || !InputBoosterMod.gameReady || InputBoosterMod.gamePaused) {
            resetPreviousStates();
            return;
        }

        // FIX: Capture the snapshot reference exactly once per poll cycle.
        // The game tick thread may replace InputBoosterMod.keySnapshot at any
        // moment. Reading the volatile field once and working from the local
        // variable guarantees all key reads in this cycle come from the same
        // consistent snapshot, eliminating torn reads and phantom transitions.
        KeySnapshot snap = InputBoosterMod.keySnapshot;
        if (snap == null) return;

        // Attack
        boolean attack = snap.attack;
        if ( attack && !prevAttack) queue(InputAction.ATTACK_PRESSED);
        if (!attack &&  prevAttack) queue(InputAction.ATTACK_RELEASED);
        prevAttack = attack;

        // Use/right-click
        boolean use = snap.use;
        if (use && !prevUse) queue(InputAction.USE_PRESSED);
        if (!use && prevUse) queue(InputAction.USE_RELEASED);
        prevUse = use;

        // Sprint
        boolean sprint = snap.sprint;
        if ( sprint && !prevSprint) queue(InputAction.SPRINT_PRESSED);
        if (!sprint &&  prevSprint) queue(InputAction.SPRINT_RELEASED);
        prevSprint = sprint;

        // Sneak
        boolean sneak = snap.sneak;
        if ( sneak && !prevSneak) queue(InputAction.SNEAK_PRESSED);
        if (!sneak &&  prevSneak) queue(InputAction.SNEAK_RELEASED);
        prevSneak = sneak;

        // Jump — only PRESSED (no RELEASED needed for vanilla jump)
        boolean jump = snap.jump;
        if (jump && !prevJump) queue(InputAction.JUMP_PRESSED);
        prevJump = jump;

        // Forward
        boolean forward = snap.forward;
        if ( forward && !prevForward) queue(InputAction.FORWARD_PRESSED);
        if (!forward &&  prevForward) queue(InputAction.FORWARD_RELEASED);
        prevForward = forward;

        // Back
        boolean back = snap.back;
        if ( back && !prevBack) queue(InputAction.BACK_PRESSED);
        if (!back &&  prevBack) queue(InputAction.BACK_RELEASED);
        prevBack = back;

        // Left
        boolean left = snap.left;
        if ( left && !prevLeft) queue(InputAction.LEFT_PRESSED);
        if (!left &&  prevLeft) queue(InputAction.LEFT_RELEASED);
        prevLeft = left;

        // Right
        boolean right = snap.right;
        if ( right && !prevRight) queue(InputAction.RIGHT_PRESSED);
        if (!right &&  prevRight) queue(InputAction.RIGHT_RELEASED);
        prevRight = right;

        // Drop / Swap / Pick-block
        boolean drop = snap.drop;
        if (drop && !prevDrop) queue(InputAction.DROP_PRESSED);
        prevDrop = drop;

        boolean swap = snap.swap;
        if (swap && !prevSwap) queue(InputAction.SWAP_PRESSED);
        prevSwap = swap;

        boolean pickBlock = snap.pickBlock;
        if (pickBlock && !prevPickBlock) queue(InputAction.PICK_BLOCK_PRESSED);
        prevPickBlock = pickBlock;
    }

    private void queue(InputAction action) {
        if (InputActionQueue.queue(action)) {
            InputBoosterMod.recoveredInputs.incrementAndGet();
            if (InputBoosterMod.replayRecorder != null) InputBoosterMod.replayRecorder.onQueued(action);
            if (InputBoosterMod.eventLog != null && action == InputAction.ATTACK_PRESSED) {
                InputBoosterMod.eventLog.add("Attack queued");
            }
        }
    }

    private void resetPreviousStates() {
        prevAttack = prevUse = prevSprint = prevSneak = false;
        prevJump = prevForward = prevBack = prevLeft = prevRight = false;
        prevDrop = prevSwap = prevPickBlock = false;
    }

    public void stopPolling() {
        running.set(false);
        this.interrupt();
    }
}
