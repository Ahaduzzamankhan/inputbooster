package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * Input module.
 *
 * <p>The mod's input pipeline is unchanged — the existing features still depend
 * on it. What this module guarantees is that the optimisation engine stays off
 * that path entirely: no key is sampled, queued or dispatched twice because of
 * it, and no engine work happens between a key press and Minecraft receiving
 * it.
 *
 * <p>The input thread is not slowed down to make room for the engine, and the
 * engine does not add a second consumer for key events, so a keystroke costs
 * exactly what it cost before the engine existed.
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

    public long ticksObserved() {
        return ticks;
    }
}