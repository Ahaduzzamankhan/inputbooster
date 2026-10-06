package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

/**
 * Adaptive engine.
 *
 * <p>Decides how often the optimisation engine does anything at all. It is the
 * reason the engine's steady-state cost is one {@code long} increment and one
 * comparison per client tick: module work runs once every
 * {@link InputBoosterConfig#getAdaptiveIntervalTicks()} ticks, and when nothing
 * is pending the window stretches from roughly ten seconds to roughly a minute.
 *
 * <p>There is no sampling thread, no frame-time histogram and no heap polling.
 * The only state is the tick counter the client tick already maintains.
 */
public final class AdaptiveEngine {

    /** Long interval the engine uses once it has nothing to contribute. */
    public static final long IDLE_INTERVAL = 1800L;

    private final long interval;
    private long nextEvaluation;
    private long evaluations;
    private long skippedTicks;
    private boolean idling;

    public AdaptiveEngine(long intervalTicks) {
        this.interval = Math.max(1L, intervalTicks);
    }

    /**
     * Advances the engine by one client tick.
     *
     * @param tick the client tick counter
     * @return true when the modules should run this tick
     */
    public boolean shouldRunModules(long tick) {
        if (tick < nextEvaluation) {
            skippedTicks++;
            return false;
        }
        nextEvaluation = tick + (idling ? IDLE_INTERVAL : interval);
        evaluations++;
        return true;
    }

    /**
     * Marks the engine idle when no module has outstanding work, which stretches
     * the evaluation window.
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
    public long skippedTicks() {
        return skippedTicks;
    }
}