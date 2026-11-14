package dev.inputbooster;

import dev.inputbooster.engine.InputEngine;
import dev.inputbooster.input.KeyBindingSet;
import dev.inputbooster.input.KeyboardRouter;
import dev.inputbooster.input.RawKeyState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * InputPollingThread — high-frequency input sampler. Runs at up to 1000 Hz.
 *
 * <p>This thread no longer detects edges itself. It samples, and hands the sample
 * to {@link InputEngine}, which owns the state machine, duplicate suppression and
 * dispatch. That keeps the threading rule intact: the thread reads immutable data
 * captured on the game thread and a raw key state handle, and never touches a
 * Minecraft object.
 *
 * <p>No allocation happens per poll: the engine reads key codes directly instead
 * of a per-sample snapshot object.
 *
 * <p>Default thread priority on purpose. An aggressively prioritised poller
 * starves the render thread on low-end CPUs, which is the stutter the mod exists
 * to avoid.
 *
 * Author: Ahaduzzaman Khan
 */
public class InputPollingThread extends Thread {

    private final AtomicBoolean running    = new AtomicBoolean(true);
    private final AtomicInteger pollRateHz = new AtomicInteger(200);

    public InputPollingThread(int initialHz) {
        super("InputBooster-PollerThread");
        setDaemon(true);
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

            int hz = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting()
                     ? 1000
                     : pollRateHz.get();

            long targetNs = 1_000_000_000L / hz;
            long elapsed  = System.nanoTime() - loopStart;
            long sleepNs  = targetNs - elapsed;

            // parkNanos keeps sub-millisecond resolution (Thread.sleep is
            // millisecond-quantised on many platforms and drifts badly at high
            // rates); drift is absorbed by never sleeping when already behind.
            while (sleepNs > 0 && running.get() && !Thread.currentThread().isInterrupted()) {
                LockSupport.parkNanos(sleepNs);
                sleepNs = targetNs - (System.nanoTime() - loopStart);
            }
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        InputBoosterMod.LOGGER.info("[Input] Polling thread stopped.");
    }

    private void poll() {
        InputEngine engine = InputBoosterMod.inputEngine;
        if (engine == null) return;

        RawKeyState raw = InputBoosterMod.rawKeyState;
        KeyBindingSet bindings = InputBoosterMod.keyBindings;
        boolean rawReady = raw != null && raw.isAvailable() && bindings != null;
        boolean customHandler = KeyboardRouter.customHandlerEnabled();

        boolean gated = !InputBoosterMod.active || !InputBoosterMod.initialized.get()
            || InputBoosterMod.shuttingDown || !InputBoosterMod.gameReady
            || InputBoosterMod.gamePaused || !InputBoosterMod.inputAllowed;

        if (gated) {
            // Follow the state silently so a key held through a pause or an open
            // screen is not reported as a fresh press when play resumes.
            if (rawReady) {
                engine.syncOnlyRaw(raw, bindings);
            } else if (!customHandler && InputBoosterMod.keySnapshot != null) {
                engine.syncOnlySnapshot(InputBoosterMod.keySnapshot);
            }
            return;
        }

        if (rawReady) {
            engine.pollRaw(raw, bindings);
        } else if (!customHandler) {
            // With the custom handler enabled the poller refuses to borrow
            // Minecraft's key mappings: those are driven by the same events that
            // belong to an open screen, which is how a space typed in chat used
            // to become a jump.
            engine.pollSnapshot(InputBoosterMod.keySnapshot);
        }
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
