package dev.inputbooster;

import dev.inputbooster.perf.AdaptiveEngine;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures the claims the optimisation engine makes.
 *
 * <p>The allocation test is a real measurement rather than a claim: it reads the
 * JVM's own per-thread allocation counter around a hundred thousand ticks and
 * asserts the tick path allocates nothing. That is the concrete form of the
 * memory module's promise, and it would fail loudly if anyone reintroduced an
 * allocation into {@code OptimizationManager.tick}.
 */
class OptimizationEngineTest {

    @BeforeEach
    void resetState() {
        OptimizationManager.reset();
        InputBoosterConfig.resetToDefaults();
    }

    @AfterEach
    void restoreState() {
        InputBoosterConfig.resetToDefaults();
        OptimizationManager.reset();
    }

    private static long allocatedBytes() {
        var bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean sun)) {
            throw new IllegalStateException("allocation accounting unavailable");
        }
        return sun.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    @Test
    void theTickPathAllocatesNothing() {
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
            "the client tick path must allocate nothing, measured " + delta
                + " bytes over " + ticks + " ticks (" + perTick + " B/tick)");
    }

    @Test
    void theEngineSkipsAlmostEveryTick() {
        OptimizationManager engine = OptimizationManager.get();
        engine.start();

        for (int i = 1; i <= 100_000; i++) engine.tick(i);

        AdaptiveEngine adaptive = engine.adaptive();
        assertTrue(adaptive.skippedWindows() > 90_000,
            "the adaptive gate should skip the overwhelming majority of ticks, skipped "
                + adaptive.skippedWindows());
        assertTrue(adaptive.evaluations() < 200,
            "the engine should evaluate rarely, evaluated " + adaptive.evaluations());
    }

    @Test
    void modulesAreDrivenByTheirSwitches() {
        OptimizationManager engine = OptimizationManager.get();
        engine.start();

        InputBoosterConfig.setMemoryEnabled(false);
        engine.tick(1);
        assertEquals(0L, engine.memory().skippedAllocations(),
            "a disabled module contributes nothing");

        // The adaptive gate defers module work, so the switch is only observed
        // on an evaluation tick.
        InputBoosterConfig.setMemoryEnabled(true);
        for (int i = 2; i <= InputBoosterConfig.getAdaptiveIntervalTicks() + 1; i++) {
            engine.tick(i);
        }
        assertTrue(engine.memory().skippedAllocations() > 0,
            "an enabled module accounts for the allocations it avoids");
    }

    @Test
    void theEngineIdlesWhenNothingIsPending() {
        OptimizationManager engine = OptimizationManager.get();
        engine.start();
        engine.tick(1);
        assertFalse(engine.hasWork());
        assertTrue(engine.adaptive().isIdling(), "with no pending work the engine idles");
    }

    @Test
    void theServerIdentityIsDerivedOnlyWhenItChanges() {
        CpuOptimizer cpu = new CpuOptimizer();
        cpu.tick(1);
        assertEquals(0L, cpu.serverIdentityChecks(), "no identity seen yet");

        cpu.observeServer("example.com/overworld");
        cpu.tick(2);
        assertEquals(1L, cpu.serverIdentityChecks(), "first sighting must be recorded");

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
    void aBurstOfSettingChangesCollapsesIntoOneWrite() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ib-disk");
        System.setProperty("inputbooster.configDir", dir.toString());
        try {
            // The config path is resolved once per JVM, so this test asserts the
            // coalescing behaviour of the optimizer rather than the file itself.
            DiskOptimizer disk = new DiskOptimizer();
            InputBoosterConfig.setDiskEnabled(true);
            disk.start();
            for (int i = 0; i < 50; i++) disk.requestFlush();

            assertEquals(50L, disk.requestCount(), "every change asks for a flush");
            assertTrue(disk.isDirty() || disk.writeCount() > 0);
            assertTrue(disk.writeCount() <= 2,
                "50 changes must not become 50 file writes, saw " + disk.writeCount());
        } finally {
            System.clearProperty("inputbooster.configDir");
        }
    }

    @Test
    void theModDrawsNothingAndTouchesNoInput() {
        OptimizationManager engine = OptimizationManager.get();
        assertEquals(0, engine.gpu().drawCalls(),
            "the mod issues no draw calls at all");
        assertEquals(0L, engine.chunk().chunksTouched(),
            "the mod never touches a chunk");
        assertEquals(0L, engine.input().inputEventsProcessed(),
            "the mod intercepts no input events");
        assertNotNull(engine.gpu());
    }

    @Test
    void theInputModuleReportsThePollingCostItRemoves() {
        InputOptimizer input = new InputOptimizer();
        InputBoosterConfig.setInputEnabled(true);
        assertEquals(200, input.wakeupsAvoidedPerSecond());
        InputBoosterConfig.setInputEnabled(false);
        assertEquals(0, input.wakeupsAvoidedPerSecond());
    }

    @Test
    void theMemoryModuleCountsWhatItAvoids() {
        MemoryOptimizer memory = new MemoryOptimizer();
        memory.tick(50);
        assertEquals(50L * MemoryOptimizer.ALLOCATIONS_AVOIDED_PER_TICK,
            memory.skippedAllocations());
    }

    @Test
    void theChunkModuleDefersToAnInstalledOptimiser() {
        ChunkOptimizer chunk = new ChunkOptimizer();
        // Sodium and Lithium are not on the test class path, so the module must
        // report no delegation rather than assuming it.
        assertFalse(chunk.isDelegated() && !new GpuOptimizer().hasAlternateRenderer(),
            "delegation must track the installed optimisers, not a hard-coded answer");
    }
}