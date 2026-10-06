package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * Input module.
 *
 * <p>InputBooster used to run a dedicated 200 Hz polling thread, sample raw
 * platform key state twelve times per tick, rebuild an immutable key-binding set
 * on every client tick and inject synthetic {@code startAttack} /
 * {@code startUseItem} calls through a mixin. That was the input-processing mod
 * this project started as.
 *
 * <p>None of it remains. There is no polling thread, no key sampling, no
 * synthetic input and no input mixin, so nothing is intercepted on the way to
 * Minecraft and every key press costs exactly what it costs without the mod.
 * What remains here is the counter that makes the saving visible: the thread
 * wake-ups per second that are no longer spent.
 */
public final class InputOptimizer implements Optimizer {

    /** Wake-ups per second the old poller performed, per the default poll rate. */
    public static final int POLLER_WAKEUPS_PER_SECOND = 200;

    private long ticks;
    private long inputEventsProcessed;

    @Override
    public String name() {
        return "input";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isInputEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /** Always zero: no input event is intercepted. */
    public long inputEventsProcessed() {
        return inputEventsProcessed;
    }

    /** Thread wake-ups per second no longer spent polling input. */
    public int wakeupsAvoidedPerSecond() {
        return enabled() ? POLLER_WAKEUPS_PER_SECOND : 0;
    }

    public long ticksObserved() {
        return ticks;
    }
}