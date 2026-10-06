package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * CPU module.
 *
 * <p>Its optimisation is subtraction. The previous design polled the frame rate,
 * rebuilt a key snapshot, re-detected the server and dispatched every manager
 * on every one of the 20 client ticks per second. A performance mod has no
 * business doing that, so this module counts ticks and lets
 * {@link OptimizationManager} skip the rest: the mod's own per-tick cost is one
 * increment and one comparison, not a dozen calls.
 *
 * <p>Per-server profile detection also used to re-derive the current server once
 * a second forever. Identity is compared against the last observed value, so a
 * player sitting on one server does no further work.
 */
public final class CpuOptimizer implements Optimizer {

    private long ticks;
    private long workUnits;

    /** Server identity last observed, or null while no world is loaded. */
    private String serverKey;
    private String lastServerKey;

    @Override
    public String name() {
        return "cpu";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isCpuEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
        // Redundant-derivation guard: the identity only has to be recomputed
        // when the player actually changes server or dimension.
        if (serverKey == null ? lastServerKey != null : !serverKey.equals(lastServerKey)) {
            lastServerKey = serverKey;
            workUnits++;
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
        return workUnits;
    }
}