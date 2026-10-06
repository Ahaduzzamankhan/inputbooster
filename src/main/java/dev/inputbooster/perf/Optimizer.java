package dev.inputbooster.perf;

/**
 * One optimisation module.
 *
 * <p>The contract is deliberately narrow because the whole point is cost: a
 * module that does nothing must be able to say so by overriding nothing. The
 * default {@link #tick(long)} allocates nothing, and callers skip it entirely
 * when {@link #enabled()} is false.
 *
 * <p>Implementations must not touch the file system, allocate in {@code tick},
 * spawn threads of their own, or read render state. Anything that needs to
 * happen off the render thread belongs on the single worker owned by
 * {@link DiskOptimizer}.
 */
public interface Optimizer {

    /** Stable identifier, used in diagnostics and by the tests. */
    String name();

    /** Whether the user has this module switched on. */
    boolean enabled();

    /** Called once when the client is ready. Must not allocate or do I/O. */
    default void start() {
    }

    /** Called once when the client shuts down. Must not block. */
    default void stop() {
    }

    /**
     * Called from the client tick.
     *
     * @param tick monotonically increasing client tick counter
     */
    default void tick(long tick) {
    }
}