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
 * <p>This thread never calls into the platform. {@link RawKeyState} holds a
 * snapshot that the game thread refreshes once per frame (HUD hook) and once
 * per tick; the poller only reads that snapshot, which is why it can run at
 * 1000 Hz without touching GLFW or SDL from a background thread. Edges are
 * detected by {@link InputEngine}, which owns the state machine, duplicate
 * suppression and dispatch.
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

    /** Which source the loop used on the previous iteration. */
    private volatile Source activeSource = Source.IDLE;
    private volatile String sourceNote = "starting";
    private volatile boolean fallbackWarned = false;

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

    /**
     * The input source the loop is allowed to use.
     *
     * <p>The custom handler is opt-in, and it only owns input when its event
     * hooks are actually live. Before this was a single {@code !customHandler}
     * test, so enabling the alpha handler with no working raw sampler left the
     * mod with no input source at all — and nothing said why.
     */
    public enum Source {
        /** Native state sampled on the game thread; the normal high-rate path. */
        RAW,
        /** Tick-rate snapshot of vanilla key mappings. */
        SNAPSHOT,
        /** Nothing to sample: the custom handler owns input. */
        IDLE
    }

    /**
     * Pure decision so the fallback rules can be tested without a client.
     *
     * @param gated            play is paused, a screen is open, no world, ...
     * @param rawAvailable     the native sampler is usable
     * @param customHandler    the alpha handler is enabled in the config
     * @param eventSourceLive  a keyboard or mouse event has actually been seen
     * @return the source to read this iteration
     */
    public static Source chooseSource(boolean gated, boolean rawAvailable,
                                      boolean customHandler, boolean eventSourceLive) {
        if (rawAvailable) return Source.RAW;
        if (!customHandler) return Source.SNAPSHOT;
        // The custom handler is enabled but its hooks have never fired, so it
        // cannot be the only source: preferring silence would disable input
        // entirely. Fall back to the tick snapshot instead.
        return eventSourceLive ? Source.IDLE : Source.SNAPSHOT;
    }

    private void poll() {
        InputEngine engine = InputBoosterMod.inputEngine;
        if (engine == null) return;

        RawKeyState raw = InputBoosterMod.rawKeyState;
        KeyBindingSet bindings = InputBoosterMod.keyBindings;
        boolean rawReady = raw != null && raw.isAvailable() && bindings != null;
        boolean customHandler = KeyboardRouter.customHandlerEnabled();
        var compat = InputBoosterMod.compatibilityManager;
        boolean eventSourceLive = compat != null && compat.eventSourceActive();

        boolean gated = !InputBoosterMod.active || !InputBoosterMod.initialized.get()
            || InputBoosterMod.shuttingDown || !InputBoosterMod.gameReady
            || InputBoosterMod.gamePaused || !InputBoosterMod.inputAllowed;

        Source source = chooseSource(gated, rawReady, customHandler, eventSourceLive);

        if (source == Source.SNAPSHOT && customHandler && !fallbackWarned) {
            fallbackWarned = true;
            InputBoosterMod.LOGGER.warn(
                "[Input] Custom keyboard handler is enabled but no keyboard/mouse event has been seen; "
                + "using the tick-rate key snapshot so input keeps working.");
        }

        Source previous = activeSource;
        if (source != previous) {
            activeSource = source;
            sourceNote = switch (source) {
                case RAW -> "raw (frame-rate sampled)";
                case SNAPSHOT -> customHandler
                    ? "snapshot (custom handler has no events)"
                    : "snapshot (tick-rate)";
                case IDLE -> "custom handler (no polling)";
            };
            InputBoosterMod.LOGGER.info("[Input] Input source: {} ({})", source, sourceNote);
            // A source switch must not inherit held keys from the old one.
            if (previous != Source.IDLE && source != Source.IDLE) engine.reset();
        }

        if (gated) {
            // Follow the state silently so a key held through a pause or an open
            // screen is not reported as a fresh press when play resumes.
            if (source == Source.RAW) {
                engine.syncOnlyRaw(raw, bindings);
            } else if (source == Source.SNAPSHOT && InputBoosterMod.keySnapshot != null) {
                engine.syncOnlySnapshot(InputBoosterMod.keySnapshot);
            }
            return;
        }

        switch (source) {
            case RAW -> engine.pollRaw(raw, bindings);
            case SNAPSHOT -> engine.pollSnapshot(InputBoosterMod.keySnapshot);
            case IDLE -> { }
        }
    }

    /** Which source the loop is reading; for the overlay and diagnostics. */
    public Source activeSource() {
        return activeSource;
    }

    public String sourceNote() {
        return sourceNote;
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
