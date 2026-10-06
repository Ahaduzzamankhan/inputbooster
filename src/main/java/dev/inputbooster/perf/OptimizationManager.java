package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

import java.util.List;
import java.util.function.Supplier;

/**
 * The optimisation engine.
 *
 * <p>Owns the six modules and the adaptive engine, and is the only thing the
 * client tick adds. Its steady-state cost is a single branch: the adaptive
 * engine returns false for almost every tick, and on the ticks where it returns
 * true each module is asked once whether it is enabled.
 *
 * <p>Modules never allocate, never touch the file system from the tick and never
 * start a thread of their own. The one background thread in the engine belongs
 * to {@link DiskOptimizer} and is created only when a settings change actually
 * has to be written.
 *
 * <p>This engine is additive. It runs alongside the mod's existing features and
 * never replaces or suppresses them.
 */
public final class OptimizationManager {

    private static volatile OptimizationManager instance;

    private final CpuOptimizer cpu = new CpuOptimizer();
    private final MemoryOptimizer memory = new MemoryOptimizer();
    private final GpuOptimizer gpu = new GpuOptimizer();
    private final DiskOptimizer disk = new DiskOptimizer();
    private final ChunkOptimizer chunk = new ChunkOptimizer();
    private final InputOptimizer input = new InputOptimizer();

    private final List<Optimizer> modules = List.of(cpu, memory, gpu, disk, chunk, input);

    private AdaptiveEngine adaptive;
    private long ticks;
    private boolean started;
    private volatile Supplier<String> serverKeySource;

    private OptimizationManager() {
        this.adaptive = new AdaptiveEngine(InputBoosterConfig.getAdaptiveIntervalTicks());
    }

    public static OptimizationManager get() {
        OptimizationManager local = instance;
        if (local == null) {
            synchronized (OptimizationManager.class) {
                local = instance;
                if (local == null) {
                    local = new OptimizationManager();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void start() {
        if (started) return;
        started = true;
        adaptive = new AdaptiveEngine(InputBoosterConfig.getAdaptiveIntervalTicks());
        for (Optimizer module : modules) {
            if (module.enabled()) module.start();
        }
    }

    /**
     * The client tick entry point. Allocation free by construction: it iterates
     * a fixed list of live references and calls virtual methods on them.
     *
     * @param tick monotonically increasing client tick counter
     */
    public void tick(long tick) {
        ticks = tick;
        if (!InputBoosterConfig.isAdaptiveOptimizationEnabled()) return;
        if (!adaptive.shouldRunModules(tick)) return;

        Supplier<String> source = serverKeySource;
        cpu.observeServer(source == null ? null : source.get());

        for (int i = 0; i < modules.size(); i++) {
            Optimizer module = modules.get(i);
            if (module.enabled()) module.tick(tick);
        }
        adaptive.setIdling(!hasWork());
    }

    /** True when a module still owes work. Only the disk module ever does. */
    public boolean hasWork() {
        return disk.isDirty();
    }

    /** A settings change happened; the disk module decides when to write. */
    public void onSettingsChanged() {
        disk.requestFlush();
    }

    public void shutdown() {
        for (Optimizer module : modules) {
            if (module.enabled()) module.stop();
        }
        started = false;
    }

    public long ticks() {
        return ticks;
    }

    public CpuOptimizer cpu()        { return cpu; }
    public MemoryOptimizer memory()  { return memory; }
    public GpuOptimizer gpu()        { return gpu; }
    public DiskOptimizer disk()      { return disk; }
    public ChunkOptimizer chunk()    { return chunk; }
    public InputOptimizer input()    { return input; }
    public AdaptiveEngine adaptive() { return adaptive; }
    public List<Optimizer> modules() { return modules; }

    /** Supplies the current server identity, evaluated at most once per window. */
    public void setServerKeySource(Supplier<String> source) {
        this.serverKeySource = source;
    }

    /** Test seam: drops the singleton so a fresh engine can be built. */
    public static void reset() {
        instance = null;
    }
}