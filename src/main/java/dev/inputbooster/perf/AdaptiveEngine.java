package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * Adaptive engine.
 *
 * <p>Decides how often the engine does anything at all. It is the reason the
 * mod's steady-state cost is one {@code long} increment and one comparison per
 * client tick: the per-module work runs once every
 * {@link InputBoosterConfig#getAdaptiveIntervalTicks()} ticks, and if every
 * module reports that it has nothing to do the engine stops ticking them at all.
 *
 * <p>There is no sampling thread, no frame-time histogram and no heap polling.
 * The only state is the tick counter, which the client tick already maintains.
 */
public final class AdaptiveEngine {

    private final long interval;
    private long nextEvaluation;
    private long evaluations;
    private long skippedWindows;
    private boolean idling;

    public AdaptiveEngine(long intervalTicks) {
        this.interval = Math.max(1L, intervalTicks);
    }

    /** Long interval the engine uses when it has nothing to contribute. */
    public static final long IDLE_INTERVAL = 1800L;

    /**
     * Advances the engine by one client tick.
     *
     * @param tick the client tick counter
     * @return true when the modules should run this tick
     */
    public boolean shouldRunModules(long tick) {
        if (tick < nextEvaluation) {
            skippedWindows++;
            return false;
        }
        nextEvaluation = tick + currentInterval();
        evaluations++;
        return true;
    }

    private long currentInterval() {
        return idling ? IDLE_INTERVAL : interval;
    }

    /**
     * Marks the engine idle when no module has any work, which stretches the
     * evaluation window from roughly ten seconds to roughly a minute.
     */
    public void setIdling(boolean value) {
        this.idling = value;
    }

    public boolean isIdling() {
        return idling;
    }

    public long evaluations() {
        return evaluations;
    }

    /** Ticks that passed without any module running. */
    public long skippedWindows() {
        return skippedWindows;
    }
}