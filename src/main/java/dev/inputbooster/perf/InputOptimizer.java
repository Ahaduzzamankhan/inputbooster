package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.InputPollingThread;

/**
 * Input module.
 *
 * <p>Its optimisation is real and lossless: the polling thread used to wake at
 * the full configured rate — two hundred times a second by default — even in
 * the states where {@code InputBoosterMod.onClientTick} guarantees nothing it
 * samples can be kept: main menu, singleplayer pause, or the mod switched
 * inactive. In those states {@code poll()} resets its edge state and discards
 * everything, so sampling at {@link InputPollingThread#IDLE_POLL_HZ} instead
 * changes no behaviour; it only stops the thread burning wakeups on work it
 * throws away.
 *
 * <p>While the player is in a world with the mod active, the configured rate is
 * untouched: sub-tick resolution is the point of the poller, and the engine
 * never trades it away. The input pipeline itself is unchanged — no key is
 * sampled, queued or dispatched twice because of this module, and the engine
 * adds no second consumer for key events.
 *
 * <p>The saving is counted, not estimated: {@link #idlePollCycles()} reports
 * how many poll-loop iterations ran at the idle rate.
 */
public final class InputOptimizer implements Optimizer {

    private long ticks;

    @Override
    public String name() {
        return "input";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isInputOptimizationEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /**
     * Additional key events consumed by the engine. Always zero: the engine
     * never intercepts input, it only avoids work elsewhere.
     */
    public long engineInputEvents() {
        return 0L;
    }

    /**
     * Poll-loop iterations spent at the idle rate since the thread started.
     * Each one would previously have run at the full configured rate.
     */
    public long idlePollCycles() {
        InputPollingThread poller = InputBoosterMod.pollingThread;
        return poller == null ? 0L : poller.idleCycles();
    }

    public long ticksObserved() {
        return ticks;
    }
}
