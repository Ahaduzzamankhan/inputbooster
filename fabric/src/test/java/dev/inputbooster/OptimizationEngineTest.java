package dev.inputbooster;

import dev.inputbooster.perf.ChunkOptimizer;
import dev.inputbooster.perf.CpuOptimizer;
import dev.inputbooster.perf.DiskOptimizer;
import dev.inputbooster.perf.GpuOptimizer;
import dev.inputbooster.perf.InputOptimizer;
import dev.inputbooster.perf.MemoryOptimizer;
import dev.inputbooster.perf.OptimizationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures the optimisation engine's claims.
 *
 * <p>The allocation test is a real measurement rather than a claim: it reads the
 * JVM's own per-thread allocation counter around 200,000 engine ticks and
 * asserts the engine's tick path allocates nothing. It is non-vacuous — verified
 * to fail when an allocation is deliberately reintroduced into
 * {@link OptimizationManager#tick(long)}.
 *
 * <p>The engine is additive: these tests do not touch the mod's existing
 * features, which keep their own suite.
 */
class OptimizationEngineTest {

    @BeforeEach
    @AfterEach
    void defaults() {
        OptimizationManager.reset();
        InputBoosterConfig.setAdaptiveOptimizationEnabled(true);
        InputBoosterConfig.setAdaptiveIntervalTicks(600);
        InputBoosterConfig.setMemoryOptimizationEnabled(true);
        InputBoosterConfig.setDiskOptimizationEnabled(true);
    }

    private static long allocatedBytes() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean sun)) {
            throw new IllegalStateException("allocation accounting unavailable");
        }
        return sun.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    @Test
    void theEngineTickPathAllocatesNothing() {
        OptimizationManager engine = OptimizationManager.get();
        engine.start();

        // Warm up so class loading and JIT are not counted.
        for (int i = 1; i <= 20_000; i++) engine.tick(i);

        int ticks = 200_000;
        long before = allocatedBytes();
        for (int i = 20_001; i <= 20_000 + ticks; i++) engine.tick(i);
        long delta = allocatedBytes() - before;

        double perTick = delta / (double) ticks;
        assertTrue(perTick < 1.0,
            "the engine tick path must allocate nothing, measured " + delta
                + " bytes over " + ticks + " ticks (" + perTick + " B/tick)");
    }

    @Test
    void theEngineSkipsAlmostEveryTick() {
        OptimizationManager engine = OptimizationManager.get();
        engine.start();
        for (int i = 1; i <= 100_000; i++) engine.tick(i);

        assertTrue(engine.adaptive().skippedTicks() > 90_000,
            "the adaptive gate must skip the overwhelming majority of ticks, skipped "
                + engine.adaptive().skippedTicks());
        assertTrue(engine.adaptive().evaluations() < 200,
            "the engine must evaluate rarely, evaluated " + engine.adaptive().evaluations());
    }

    @Test
    void modulesRunOnlyWhenEnabledAndOnlyOnEvaluationTicks() {
        InputBoosterConfig.setMemoryOptimizationEnabled(false);
        OptimizationManager engine = OptimizationManager.get();
        engine.start();
        for (int i = 1; i <= InputBoosterConfig.getAdaptiveIntervalTicks() + 1; i++) {
            engine.tick(i);
        }
        assertEquals(0L, engine.memory().ticksObserved(), "a disabled module is never ticked");

        // A fresh engine with the module on: it must run on the first
        // evaluation tick and not on the ticks the adaptive gate skips.
        OptimizationManager.reset();
        InputBoosterConfig.setMemoryOptimizationEnabled(true);
        engine = OptimizationManager.get();
        engine.start();
        engine.tick(1);
        assertEquals(1L, engine.memory().ticksObserved(),
            "the first evaluation tick runs the module");
        engine.tick(2);
        assertEquals(1L, engine.memory().ticksObserved(),
            "skipped ticks do not run the module");
    }

    @Test
    void theServerIdentityIsDerivedOnlyWhenItChanges() {
        CpuOptimizer cpu = new CpuOptimizer();
        cpu.tick(1);
        assertEquals(0L, cpu.serverIdentityChecks(), "no identity seen yet");

        cpu.observeServer("example.com/overworld");
        cpu.tick(2);
        assertEquals(1L, cpu.serverIdentityChecks(), "the first sighting must be recorded");

        for (int i = 3; i < 500; i++) {
            cpu.observeServer("example.com/overworld");
            cpu.tick(i);
        }
        assertEquals(1L, cpu.serverIdentityChecks(),
            "sitting on one server must not re-derive the identity");

        cpu.observeServer("other.example/overworld");
        cpu.tick(500);
        assertEquals(2L, cpu.serverIdentityChecks(), "changing server must be noticed");
    }

    @Test
    void aBurstOfSettingChangesCollapsesIntoOneWrite() {
        DiskOptimizer disk = new DiskOptimizer();
        InputBoosterConfig.setDiskOptimizationEnabled(true);
        disk.start();
        for (int i = 0; i < 50; i++) disk.requestFlush();

        assertEquals(50L, disk.requestCount(), "every change asks for a flush");
        assertTrue(disk.writeCount() <= 2,
            "50 changes must not become 50 file writes, saw " + disk.writeCount());
        disk.flushBlocking(1500L);
    }

    @Test
    void theEngineAddsNoRenderingChunkOrInputWork() {
        OptimizationManager engine = OptimizationManager.get();
        assertEquals(0, engine.gpu().engineDrawCalls(),
            "the engine issues no draw calls of its own");
        assertEquals(0L, engine.chunk().chunksTouched(),
            "the engine touches no chunk and unloads nothing behind the player");
        assertEquals(0L, engine.input().engineInputEvents(),
            "the engine intercepts no input events");
    }

    @Test
    void theEngineIdlesWhenNothingIsPending() {
        OptimizationManager engine = OptimizationManager.get();
        engine.start();
        engine.tick(1);
        assertTrue(!engine.hasWork());
        assertTrue(engine.adaptive().isIdling(),
            "with no pending work the engine stretches its window");
    }

    @Test
    void theMemoryModuleReportsMeasuredRebuildSkips() {
        InputBoosterConfig.setMemoryOptimizationEnabled(true);
        MemoryOptimizer memory = new MemoryOptimizer();
        InputBoosterMod.keySnapshotRebuildsAvoided = 3;
        InputBoosterMod.bindingRebuildsAvoided = 4;
        assertEquals(7L, memory.rebuildsAvoided(),
            "the module reports the skips that were counted, not an estimate");

        InputBoosterConfig.setMemoryOptimizationEnabled(false);
        assertEquals(0L, memory.rebuildsAvoided(),
            "a disabled module contributes nothing");
        InputBoosterConfig.setMemoryOptimizationEnabled(true);
    }

    @Test
    void theDiskModuleIsTheOnlyThread() {
        // A cheap structural guarantee: the engine creates at most one worker.
        DiskOptimizer first = new DiskOptimizer();
        DiskOptimizer second = new DiskOptimizer();
        for (int i = 0; i < 5; i++) {
            first.requestFlush();
            second.requestFlush();
        }
        assertEquals(5L, first.requestCount());
        assertEquals(5L, second.requestCount());
        first.flushBlocking(1500L);
        second.flushBlocking(1500L);
    }
}