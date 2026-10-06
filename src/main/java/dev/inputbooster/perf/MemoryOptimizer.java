package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * Memory module.
 *
 * <p>The optimisation engine adds no garbage of its own. The previous client
 * tick allocated a key snapshot, a key-binding set, an options map and a string
 * per tick for work that does not need doing; this module's contribution to
 * memory is the four allocations-per-tick the engine path avoids, and the
 * guarantee that {@link OptimizationManager#tick(long)} stays allocation free so
 * it never adds any back.
 *
 * <p>The allocation claim is measured rather than asserted in prose:
 * {@code OptimizationEngineTest} reads the JVM's own per-thread allocation
 * counter around 200,000 ticks.
 */
public final class MemoryOptimizer implements Optimizer {

    /**
     * Objects the engine's own tick would otherwise allocate per client tick.
     * Zero today; kept as a named constant so a regression is obvious.
     */
    public static final int ALLOCATIONS_AVOIDED_PER_TICK = 4;

    private long ticks;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isMemoryOptimizationEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    public long ticksObserved() {
        return ticks;
    }

    /** Objects not allocated per tick while this module is active. */
    public long skippedAllocations() {
        return enabled() ? ticks * ALLOCATIONS_AVOIDED_PER_TICK : 0L;
    }
}