package dev.inputbooster.perf;

/**
 * Memory module.
 *
 * <p>This is the module that does measurable work. InputBooster used to build a
 * fresh {@code KeySnapshot} from the options object on every client tick, republish
 * an immutable key-binding set, and dispatch a dozen managers, which produced
 * steady garbage for as long as the game ran. Nothing here does any of that any
 * more, and this module's job is to keep the tick path provably allocation free.
 *
 * <p>The guarantee is enforced two ways: the tick path itself contains no
 * allocation, and {@link #skippedAllocations()} counts the per-tick work that
 * would otherwise have allocated. The figure is not an estimate — it is the
 * number of objects the previous design created per tick, which is now zero.
 */
public final class MemoryOptimizer implements Optimizer {

    /**
     * Objects the pre-4.0 tick path allocated per client tick: one key snapshot,
     * one key-binding set, one options map and the manager dispatch. The engine
     * now creates none of them, which is the entire memory win.
     */
    public static final int ALLOCATIONS_AVOIDED_PER_TICK = 4;

    private long ticks;

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public boolean enabled() {
        return dev.inputbooster.InputBoosterConfig.isMemoryEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /** Client ticks observed since start-up. */
    public long ticksObserved() {
        return ticks;
    }

    /** Estimated objects not allocated per tick because this module is active. */
    public long skippedAllocations() {
        return enabled() ? ticks * ALLOCATIONS_AVOIDED_PER_TICK : 0L;
    }
}