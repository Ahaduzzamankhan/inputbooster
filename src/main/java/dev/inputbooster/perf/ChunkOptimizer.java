package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Chunk module.
 *
 * <p>This module deliberately does nothing to chunks, and the reason matters
 * more than the code.
 *
 * <p>Chunk scheduling, rebuild queues and mesh upload are owned by Sodium's
 * {@code VanillaChunkRenderer}, by Lithium and by vanilla's own priority queues.
 * A second mod injecting into those queues is the classic way to produce the
 * kind of crash this project has already shipped twice. The brief also forbids
 * unloading chunks behind the player, which is the change most people assume a
 * "chunk optimiser" makes and the one that reliably causes pop-in and extra disk
 * I/O.
 *
 * <p>So the contribution is negative by construction: the engine adds no chunk
 * work, schedules nothing, unloads nothing and holds no reference to a
 * {@code Chunk}. When a dedicated chunk optimiser is present that is recorded so
 * the overlap is visible instead of silent.
 */
public final class ChunkOptimizer implements Optimizer {

    private final boolean delegated;
    private long ticks;

    public ChunkOptimizer() {
        FabricLoader loader = FabricLoader.getInstance();
        this.delegated = loader.isModLoaded("sodium")
            || loader.isModLoaded("lithium")
            || loader.isModLoaded("magnesium");
    }

    @Override
    public String name() {
        return "chunk";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isChunkOptimizationEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /** Chunks the engine touched. Always zero, and asserted to stay zero. */
    public long chunksTouched() {
        return 0L;
    }

    /** True when a dedicated chunk optimiser owns this work. */
    public boolean isDelegated() {
        return delegated;
    }

    public long ticksObserved() {
        return ticks;
    }
}