package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * CPU module.
 *
 * <p>Its optimisation is subtraction. Per-server identity used to be re-derived
 * once a second forever; it is now compared against the last observed value, so
 * a player sitting on one server does no further work, and a change of server or
 * dimension is still noticed immediately.
 *
 * <p>The rest of the mod's per-tick dispatch is unchanged: the input pipeline
 * still runs, because the mod's existing features still exist. What changed is
 * that the optimisation engine itself is gated, so the performance side costs
 * one increment and one comparison per tick.
 */
public final class CpuOptimizer implements Optimizer {

    private long ticks;
    private long identityChecks;

    /** Server identity last observed, or null while no world is loaded. */
    private String serverKey;
    private String lastServerKey;

    @Override
    public String name() {
        return "cpu";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isCpuOptimizationEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
        // Redundant-derivation guard: the identity only has to be recomputed
        // when the player actually changes server or dimension.
        if (serverKey == null ? lastServerKey != null : !serverKey.equals(lastServerKey)) {
            lastServerKey = serverKey;
            identityChecks++;
        }
    }

    /** Supplies the current server identity from the game thread. */
    public void observeServer(String key) {
        serverKey = key;
    }

    public long ticksObserved() {
        return ticks;
    }

    /** Times the identity actually had to be re-derived. */
    public long serverIdentityChecks() {
        return identityChecks;
    }
}