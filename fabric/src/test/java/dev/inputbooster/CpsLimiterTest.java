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

/** Behavioural tests for the smart CPS limiter. */
class CpsLimiterTest {

    @BeforeEach
    void useKnownConfig() throws IOException {
        Path dir = Path.of(System.getProperty("inputbooster.configDir", "config"));
        if (Files.exists(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best effort cleanup
                    }
                });
            }
        }
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("inputbooster.properties"), """
            cps_limiter=true
            max_cps=10
            cps_mode=FIXED
            """);
        InputBoosterConfig.load();
    }

    @Test
    void enforcesTheConfiguredCap() {
        CpsLimiter limiter = new CpsLimiter();

        int allowed = 0;
        for (int i = 0; i < 40; i++) {
            if (limiter.allowClick()) allowed++;
        }

        assertEquals(10, allowed, "exactly max_cps clicks may pass inside one second");
        assertFalse(limiter.allowClick(), "still capped");
    }

    @Test
    void recordsAndReportsAcceptedClicks() {
        CpsLimiter limiter = new CpsLimiter();

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.allowClick());
            limiter.recordClick();
        }

        assertEquals(5, limiter.getCps());
        assertEquals(10, limiter.getMaxCps());
    }

    @Test
    void disabledLimiterLetsEveryClickThrough() throws IOException {
        Path dir = Path.of(System.getProperty("inputbooster.configDir", "config"));
        Files.writeString(dir.resolve("inputbooster.properties"), """
            cps_limiter=false
            max_cps=1
            cps_mode=FIXED
            """);
        InputBoosterConfig.load();

        CpsLimiter limiter = new CpsLimiter();
        for (int i = 0; i < 25; i++) {
            assertTrue(limiter.allowClick(), "limiter disabled must not block clicks");
        }
    }
}