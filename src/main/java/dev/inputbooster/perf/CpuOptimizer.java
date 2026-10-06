package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * CPU module.
 *
 * <p>Its optimisation is subtraction, and it says exactly what is subtracted.
 * Per-server identity used to be re-derived on a fixed cadence forever; the
 * optimisation engine now asks the supplier at most once per adaptive window
 * (every {@link InputBoosterConfig#getAdaptiveIntervalTicks()} ticks, stretched
 * to {@link AdaptiveEngine#IDLE_INTERVAL} when idle) instead of every tick.
 * This module keeps the last observed identity and reports when it actually
 * changed, so a player sitting on one server does no further work while a
 * change of server or dimension is still noticed on the next window.
 *
 * <p>The rest of the mod's per-tick dispatch is unchanged: the input pipeline
 * still runs, because the mod's existing features still exist. The engine's
 * own steady-state cost is one increment and one comparison per tick.
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