package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;
import net.fabricmc.loader.api.FabricLoader;

/**
 * GPU module.
 *
 * <p>There is deliberately nothing to optimise here, and the module exists to
 * make that explicit rather than to look busy. InputBooster no longer draws
 * anything at all: the HUD badge, the keystroke panel and the debug overlay are
 * gone, so the mod submits no render state, touches no pipeline and issues no
 * draw call on any backend. Zero GPU work cannot be optimised.
 *
 * <p>The one real rule this module enforces is not to reintroduce any. With an
 * alternate renderer installed, the mod must still stay out of its way, which is
 * asserted by {@code RenderingApiTest} against the sources and against the
 * shipped bytecode.
 */
public final class GpuOptimizer implements Optimizer {

    private final boolean alternateRenderer;
    private long ticks;

    public GpuOptimizer() {
        this.alternateRenderer = FabricLoader.getInstance().isModLoaded("sodium")
            || FabricLoader.getInstance().isModLoaded("vulkanmod")
            || FabricLoader.getInstance().isModLoaded("magnesium");
    }

    @Override
    public String name() {
        return "gpu";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isGpuEnabled();
    }

    @Override
    public void tick(long tick) {
        ticks = tick;
    }

    /** Always zero: the mod issues no draw calls. */
    public int drawCalls() {
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