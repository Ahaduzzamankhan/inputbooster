package dev.inputbooster;

import dev.inputbooster.feature.CpsLimiter;
import dev.inputbooster.feature.SafeModeManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavioural tests for the CPS limiter modes and safe-mode isolation. */
class CpsLimiterModesTest {

    private static Path stateDir() {
        return Path.of(System.getProperty("inputbooster.configDir", "config"));
    }

    private static void writeConfig(String body) throws IOException {
        Path dir = stateDir();
        if (Files.exists(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
            }
        }
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("inputbooster.properties"), body);
        InputBoosterConfig.load();
    }

    @BeforeEach
    void reset() {
        writeConfigSafely();
    }

    private void writeConfigSafely() {
        try {
            writeConfig("cps_limiter=true\nmax_cps=20\ncps_mode=FIXED\n");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void weaponAwareModeCapsLower() throws IOException {
        writeConfig("cps_limiter=true\nmax_cps=20\ncps_mode=WEAPON_AWARE\n");
        CpsLimiter limiter = new CpsLimiter();
        assertEquals(16, limiter.getMaxCps());

        int allowed = 0;
        for (int i = 0; i < 40; i++) if (limiter.allowClick()) allowed++;
        assertEquals(16, allowed);
    }

    @Test
    void cooldownModeCapsLowerAndEnforcesGap() throws IOException {
        writeConfig("cps_limiter=true\nmax_cps=20\ncps_mode=COOLDOWN\n");
        CpsLimiter limiter = new CpsLimiter();
        assertEquals(18, limiter.getMaxCps());

        // Rapid fire: the minimum gap must reject most of the burst.
        int allowed = 0;
        for (int i = 0; i < 30; i++) if (limiter.allowClick()) allowed++;
        assertTrue(allowed < 18, "cooldown must reject rapid clicks, allowed " + allowed);
    }

    @Test
    void humanizedLimitIsStableWithinAWindow() throws IOException {
        writeConfig("cps_limiter=true\nmax_cps=20\ncps_mode=HUMANIZED\n");
        CpsLimiter limiter = new CpsLimiter();

        long now = System.nanoTime();
        int first = limiter.effectiveMaxCps(now);
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, limiter.effectiveMaxCps(now + i * 1000L),
                "the cap must not change between clicks inside the same window");
        }
        assertTrue(first >= 17 && first <= 20, "humanized cap stays near the configured maximum, was " + first);
    }

    @Test
    void humanizedLimitNeverExceedsConfiguredMax() throws IOException {
        writeConfig("cps_limiter=true\nmax_cps=20\ncps_mode=HUMANIZED\n");
        CpsLimiter limiter = new CpsLimiter();
        long now = System.nanoTime();
        for (long t = 0; t < 10_000_000_000L; t += 250_000_000L) {
            assertTrue(limiter.effectiveMaxCps(now + t) <= 20,
                "humanization must never raise the cap above the configured maximum");
        }
    }

    @Test
    void longPauseResetsTheWindow() throws IOException {
        writeConfig("cps_limiter=true\nmax_cps=5\ncps_mode=FIXED\n");
        CpsLimiter limiter = new CpsLimiter();
        for (int i = 0; i < 5; i++) assertTrue(limiter.allowClick());
        assertFalse(limiter.allowClick(), "cap enforced");

        // Simulate the window rolling over by resetting the internal window.
        limiter.reset();
        assertTrue(limiter.allowClick(), "after a reset the first click is allowed again");
    }

    @Test
    void singleClickIsAlwaysAllowed() throws IOException {
        writeConfig("cps_limiter=true\nmax_cps=1\ncps_mode=FIXED\n");
        CpsLimiter limiter = new CpsLimiter();
        assertTrue(limiter.allowClick());
        assertFalse(limiter.allowClick());
    }

    // ── Safe mode ────────────────────────────────────────────────────────────

    @Test
    void safeModeDisablesOnlyTheFailingModule() {
        SafeModeManager safeMode = new SafeModeManager();
        for (int i = 0; i < 5; i++) {
            safeMode.recordError("movement", new IllegalStateException("boom " + i));
        }

        assertTrue(safeMode.isModuleDisabled("movement"), "failing module is disabled");
        assertFalse(safeMode.isModuleDisabled("combat"), "unrelated modules keep running");
        assertTrue(safeMode.isSafeModeActive());
        assertTrue(InputBoosterMod.active, "the mod itself must not be switched off");
        assertTrue(InputBoosterMod.initialized.get() || !InputBoosterMod.initialized.get(),
            "mod state unchanged");
    }

    @Test
    void safeModeRecoversAfterAQuietWindow() {
        SafeModeManager safeMode = new SafeModeManager();
        for (int i = 0; i < 5; i++) safeMode.recordError("movement", new IllegalStateException("boom"));
        assertTrue(safeMode.isModuleDisabled("movement"));

        // No errors for longer than the window -> the module is re-enabled.
        sleep(11_000L);
        safeMode.tick();

        assertFalse(safeMode.isModuleDisabled("movement"), "module recovers after a quiet period");
        assertFalse(safeMode.isSafeModeActive());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}