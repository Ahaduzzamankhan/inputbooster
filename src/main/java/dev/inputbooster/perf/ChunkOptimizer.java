package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Chunk module.
 *
 * <p>This module deliberately does nothing to chunks, and the reason matters
 * more than the code.
 *
 * <p>Chunk scheduling, rebuild queues and mesh upload are already owned by
 * Sodium's {@code VanillaChunkRenderer}, by Lithium and by vanilla's own
 * priority queues. A second mod injecting into the same queues is the classic
 * way to produce the kind of crash this project has already shipped twice. The
 * brief also forbids unloading chunks behind the player, which is the change
 * most people assume a "chunk optimizer" makes and the one that reliably causes
 * pop-in and extra disk I/O.
 *
 * <p>So the module's contribution is negative by construction: it adds no chunk
 * work, schedules nothing, unloads nothing and holds no reference to a
 * {@code Chunk}. When a dedicated chunk optimiser is present it is recorded so
 * the overlap is visible in diagnostics instead of silently doubling the work.
 */
public final class ChunkOptimizer implements Optimizer {

    private final boolean delegated;
    private long ticks;
    private long chunksTouched;

    public ChunkOptimizer() {
        this.delegated = FabricLoader.getInstance().isModLoaded("sodium")
            || FabricLoader.getInstance().isModLoaded("lithium")
            || FabricLoader.getInstance().isModLoaded("magnesium");
    }

    @Override
    public String name() {
        return "chunk";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isChunkEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /** Always zero, and asserted to stay zero. */
    public long chunksTouched() {
        return chunksTouched;
    }

    /** True when a dedicated chunk optimiser owns this work. */
    public boolean isDelegated() {
        return delegated;
    }

    public long ticksObserved() {
        return ticks;
    }
}