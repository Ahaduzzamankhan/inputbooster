package dev.inputbooster;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InputPollingThread
 *
 * High-frequency polling thread. Runs at up to 1000 Hz.
 * v2.0.4 fixes:
 *  - LEFT_RELEASED and RIGHT_RELEASED now properly queued (were missing)
 *  - BACK_RELEASED properly queued (was already present, retained)
 *  - Thread priority set to MAX_PRIORITY - 1 for reliable timing
 *
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
                InputBoosterMod.LOGGER.warn("[InputBooster] Polling error: {}", e.getMessage());
            }

            int hz       = pollRateHz.get();
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
        if (!InputBoosterMod.gameReady || InputBoosterMod.gamePaused) return;

        KeySnapshot snap = InputBoosterMod.keySnapshot;
        if (snap == null) return;

        // Attack
        boolean attack = snap.attack;
        if ( attack && !prevAttack) queue(InputAction.ATTACK_PRESSED);
        if (!attack &&  prevAttack) queue(InputAction.ATTACK_RELEASED);
        prevAttack = attack;

        // Use
        boolean use = snap.use;
        if ( use && !prevUse) queue(InputAction.USE_PRESSED);
        if (!use &&  prevUse) queue(InputAction.USE_RELEASED);
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

        // Left — FIX: LEFT_RELEASED was never queued in v2.0.3
        boolean left = snap.left;
        if ( left && !prevLeft) queue(InputAction.LEFT_PRESSED);
        if (!left &&  prevLeft) queue(InputAction.LEFT_RELEASED);
        prevLeft = left;

        // Right — FIX: RIGHT_RELEASED was never queued in v2.0.3
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
        }
    }

    public void stopPolling() {
        running.set(false);
        this.interrupt();
    }
}
