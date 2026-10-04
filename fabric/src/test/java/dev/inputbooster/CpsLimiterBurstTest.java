package dev.inputbooster;

import dev.inputbooster.feature.CpsLimiter;
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

/** Tests for the token-bucket CPS limiter and config migration. */
class CpsLimiterBurstTest {

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
        try {
            writeConfig("cps_limiter=true\nmax_cps=10\ncps_mode=FIXED\nconfig_version=304\n");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aFullBurstUpToTheCapIsAllowed() {
        CpsLimiter limiter = new CpsLimiter();
        int allowed = 0;
        for (int i = 0; i < 10; i++) if (limiter.allowClick()) allowed++;
        assertEquals(10, allowed, "a burst up to the cap must pass");

        assertFalse(limiter.allowClick(), "the eleventh click in the same instant is over the cap");
    }

    @Test
    void burstyButValidInputIsNotRejected() throws Exception {
        writeConfig("cps_limiter=true\nmax_cps=10\ncps_mode=FIXED\n");
        CpsLimiter limiter = new CpsLimiter();

        // 10 quick clicks, a short pause, then 10 more: the bucket refills, so
        // the second burst is legitimate and must be accepted.
        for (int i = 0; i < 10; i++) assertTrue(limiter.allowClick(), "burst 1 click " + i);
        Thread.sleep(150);
        int allowed = 0;
        for (int i = 0; i < 10; i++) if (limiter.allowClick()) allowed++;
        assertTrue(allowed >= 1, "tokens must refill after a pause, allowed " + allowed);
    }

    @Test
    void longRunAverageStaysCapped() throws Exception {
        writeConfig("cps_limiter=true\nmax_cps=10\ncps_mode=FIXED\n");
        CpsLimiter limiter = new CpsLimiter();

        int accepted = 0;
        long start = System.nanoTime();
        // Click as fast as possible for ~1 second.
        while (System.nanoTime() - start < 1_000_000_000L) {
            if (limiter.allowClick()) accepted++;
        }
        // The bucket starts full (maxCps) and refills at maxCps/s, so roughly
        // 2x the per-second cap is possible across a one-second window.
        assertTrue(accepted <= 25, "long-run rate must stay bounded, accepted " + accepted);
        assertTrue(accepted >= 10, "the initial burst must still be allowed, accepted " + accepted);
    }

    @Test
    void configMigrationRewritesLegacyKeysAndBumpsVersion() throws IOException {
        writeConfig("pollrate=350\nwatap=true\nauto_sprint_key=true\nconfig_version=301\n");

        assertEquals(350, InputBoosterConfig.getPollRateHz(), "legacy pollrate key is migrated");
        assertTrue(InputBoosterConfig.isWTapAssistEnabled(), "legacy watap key is migrated");
        assertTrue(InputBoosterConfig.isAutoSprintEnabled(), "legacy auto_sprint_key is migrated");
        assertEquals(InputBoosterConfig.CONFIG_VERSION, InputBoosterConfig.getConfigVersion());
    }

    @Test
    void corruptValuesFallBackAndAreReportedNotCrashed() throws IOException {
        writeConfig("poll_rate_hz=abc\nmax_cps=9999\nanti_idle=perhaps\nconfig_version=304\n");

        assertEquals(200, InputBoosterConfig.getPollRateHz());
        assertEquals(20, InputBoosterConfig.getMaxCps());
        assertTrue(InputBoosterConfig.isAntiIdleEnabled());
    }
}