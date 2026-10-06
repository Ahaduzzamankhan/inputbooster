package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

/**
 * Memory module.
 *
 * <p>Its optimisation is real and measured: the client tick used to rebuild the
 * key snapshot and the published key-binding set on every tick — roughly a
 * dozen short-lived objects per tick, forever, including on the main menu.
 * Both rebuilds are now change-gated, so the steady-state cost of a tick that
 * changes nothing is a handful of primitive comparisons and no allocation at
 * all.
 *
 * <p>Counters on {@link InputBoosterMod} record how many rebuilds were skipped;
 * {@link #rebuildsAvoided()} reports the total. There is no estimated figure
 * anywhere in this module: everything it reports was counted when it happened.
 */
public final class MemoryOptimizer implements Optimizer {

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

    /**
     * Per-tick snapshot and binding rebuilds skipped since the client started.
     * Zero while the module is switched off.
     */
    public long rebuildsAvoided() {
        if (!enabled()) return 0L;
        return InputBoosterMod.keySnapshotRebuildsAvoided + InputBoosterMod.bindingRebuildsAvoided;
    }
}
