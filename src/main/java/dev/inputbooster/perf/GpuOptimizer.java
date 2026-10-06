package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * GPU module.
 *
 * <p>There is deliberately nothing for this module to optimise on the rendering
 * side, and it says so rather than looking busy. The mod's existing HUD overlay
 * is the only thing it draws; the optimisation engine adds no rendering work of
 * its own, issues no draw calls, and constructs no pose matrix. New GPU work
 * would go straight through Minecraft's render state, never through LWJGL, so it
 * behaves identically on the OpenGL and Vulkan backends.
 *
 * <p>The module records whether an alternate renderer owns the backend so the
 * overlap is visible in diagnostics instead of silently doubling the work.
 */
public final class GpuOptimizer implements Optimizer {

    private final boolean alternateRenderer;
    private long ticks;

    public GpuOptimizer() {
        FabricLoader loader = FabricLoader.getInstance();
        this.alternateRenderer = loader.isModLoaded("sodium")
            || loader.isModLoaded("vulkanmod")
            || loader.isModLoaded("magnesium");
    }

    @Override
    public String name() {
        return "gpu";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isGpuOptimizationEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /** Draw calls issued by the optimisation engine. Always zero. */
    public int engineDrawCalls() {
        return 0;
    }

    /** True when Sodium, VulkanMod or Magnesium owns the render backend. */
    public boolean hasAlternateRenderer() {
        return alternateRenderer;
    }

    public long ticksObserved() {
        return ticks;
    }
}