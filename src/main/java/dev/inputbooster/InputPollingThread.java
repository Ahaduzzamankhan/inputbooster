package dev.inputbooster;

import dev.inputbooster.input.KeyBindingSet;
import dev.inputbooster.input.RawKeyState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InputPollingThread — high-frequency input observer. Runs at up to 1000 Hz.
 *
 * Architecture: the polling thread samples the platform key state itself
 * (through {@link RawKeyState}) rather than re-reading a snapshot that only
 * changes once per Minecraft tick. That is what makes short taps observable:
 * a key pressed and released inside a single tick still produces a
 * pressed/released pair here. The tick-rate snapshot published by the game
 * thread stays available as a fallback if raw sampling is unavailable.
 *
 * Threading: the polling thread is an observer and event producer only. It
 * never touches Minecraft objects — it reads the immutable
 * {@link KeyBindingSet} and the key state window that were captured on the
 * game thread, and it only writes to the bounded {@link InputActionQueue}.
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
        super("InputBooster-PollerThread");
        setDaemon(true);
        try {
            // Requesting near-max priority is best effort only; some systems
            // refuse it, and a SecurityException here would previously abort
            // mod initialisation entirely.
            setPriority(Thread.MAX_PRIORITY - 1);
        } catch (SecurityException ignored) {
            // Default priority is fine.
        }
        this.pollRateHz.set(clampHz(initialHz));
    }

    public void setPollRateHz(int hz) {
        this.pollRateHz.set(clampHz(hz));
    }

    private static int clampHz(int hz) {
        return Math.max(60, Math.min(1000, hz));
    }

    @Override
    public void run() {
        InputBoosterMod.LOGGER.info("[Input] Polling thread started at {} Hz.", pollRateHz.get());

        long errors = 0;
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long loopStart = System.nanoTime();

            try {
                poll();
            } catch (Throwable t) {
                // A failure here must never kill the thread: a dead poller
                // silently disables the whole mod.
                if (++errors <= 5) {
                    InputBoosterMod.LOGGER.warn("[Input] Polling error", t);
                }
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

        InputBoosterMod.LOGGER.info("[Input] Polling thread stopped.");
    }

    private void poll() {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()
            || InputBoosterMod.shuttingDown || !InputBoosterMod.gameReady || InputBoosterMod.gamePaused) {
            resetPreviousStates();
            return;
        }

        KeySnapshot snap = sample();
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

    /**
     * Samples the current key state. Raw platform state is preferred; the
     * tick-rate snapshot is the fallback so the mod still works if the raw
     * path is unavailable.
     */
    private KeySnapshot sample() {
        RawKeyState raw = InputBoosterMod.rawKeyState;
        KeyBindingSet bindings = InputBoosterMod.keyBindings;
        if (raw != null && raw.isAvailable() && bindings != null) {
            try {
                return KeySnapshot.fromRaw(raw, bindings);
            } catch (Throwable ignored) {
                // Fall through to the tick-rate snapshot.
            }
        }
        return InputBoosterMod.keySnapshot;
    }

    private void queue(InputAction action) {
        if (InputActionQueue.queue(action)) {
            InputBoosterMod.recoveredInputs.incrementAndGet();
            if (InputBoosterMod.replayRecorder != null) InputBoosterMod.replayRecorder.onQueued(action);
            if (InputBoosterMod.eventLog != null && action == InputAction.ATTACK_PRESSED) {
                InputBoosterMod.eventLog.add("Attack queued");
            }
        } else if (InputBoosterMod.eventLog != null && action == InputAction.ATTACK_PRESSED) {
            // Record the failure instead of dropping the click silently.
            InputBoosterMod.eventLog.add("Attack dropped: input queue full");
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

    /**
     * Stops the thread and waits for it to actually terminate so no background
     * thread survives the client shutdown.
     *
     * @return true if the thread finished within the timeout
     */
    public boolean stopPollingAndAwait(long timeoutMillis) {
        stopPolling();
        if (Thread.currentThread() == this) return true;
        try {
            join(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !isAlive();
    }
}