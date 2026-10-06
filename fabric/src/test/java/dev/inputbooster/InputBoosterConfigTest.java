package dev.inputbooster;

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

/**
 * Regression tests for {@link InputBoosterConfig}.
 *
 * Two real bugs are covered here:
 *  1. {@code load()} kept in-memory values for keys missing from the file, so
 *     reloading a partial config leaked the previous session's settings.
 *  2. {@code Boolean.parseBoolean} silently turned hand-written values such as
 *     "True" or "1" into {@code false}.
 */
class InputBoosterConfigTest {

    private static Path configDir() {
        return Path.of(System.getProperty("inputbooster.configDir", "config"));
    }

    private static Path configFile() {
        return configDir().resolve("inputbooster.properties");
    }

    private static void write(String contents) throws IOException {
        Files.createDirectories(configDir());
        Files.writeString(configFile(), contents);
    }

    @BeforeEach
    void clean() throws IOException {
        if (Files.exists(configDir())) {
            try (Stream<Path> paths = Files.walk(configDir())) {
                paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best effort cleanup
                    }
                });
            }
        }
        Files.createDirectories(configDir());
    }

    @Test
    void reloadResetsKeysMissingFromTheFile() throws IOException {
        write("anti_idle=false\npoll_rate_hz=350\n");
        InputBoosterConfig.load();
        assertFalse(InputBoosterConfig.isAntiIdleEnabled());
        assertEquals(350, InputBoosterConfig.getPollRateHz());

        // A config that no longer mentions anti_idle must fall back to the
        // default instead of silently keeping "false" from the previous file.
        write("poll_rate_hz=200\n");
        InputBoosterConfig.load();
        assertTrue(InputBoosterConfig.isAntiIdleEnabled(), "missing keys must return to defaults");
        assertEquals(200, InputBoosterConfig.getPollRateHz());
    }

    @Test
    void acceptsCommonBooleanSpellings() throws IOException {
        write("""
            anti_idle=True
            auto_sprint=yes
            wtap_assist=1
            auto_strafe=on
            cps_limiter=OFF
            burst_mode=No
            """);
        InputBoosterConfig.load();

        assertTrue(InputBoosterConfig.isAntiIdleEnabled());
        assertTrue(InputBoosterConfig.isAutoSprintEnabled());
        assertTrue(InputBoosterConfig.isWTapAssistEnabled());
        assertTrue(InputBoosterConfig.isAutoStrafeEnabled());
        assertFalse(InputBoosterConfig.isCpsLimiterEnabled());
        assertFalse(InputBoosterConfig.isBurstModeEnabled());
    }

    @Test
    void fallsBackToDefaultForUnparsableBoolean() throws IOException {
        write("anti_idle=maybe\n");
        InputBoosterConfig.load();
        assertTrue(InputBoosterConfig.isAntiIdleEnabled(), "garbage value must not disable a feature");
    }

    @Test
    void clampsOutOfRangeValues() throws IOException {
        write("""
            poll_rate_hz=99999
            max_cps=500
            overlay_position=42
            overlay_opacity=7.5
            overlay_scale=0.01
            fps_check_interval=0
            """);
        InputBoosterConfig.load();

        assertEquals(1000, InputBoosterConfig.getPollRateHz());
        assertEquals(20, InputBoosterConfig.getMaxCps());
        assertEquals(3, InputBoosterConfig.getOverlayPosition());
        assertEquals(1.0f, InputBoosterConfig.getOverlayOpacity(), 1e-6);
        assertEquals(0.5f, InputBoosterConfig.getOverlayScale(), 1e-6);
        assertEquals(1, InputBoosterConfig.getFpsCheckInterval());
    }

    @Test
    void settersClampAndSaveRoundTrips() throws IOException {
        InputBoosterConfig.setPollRateHz(10);
        InputBoosterConfig.setMaxCps(99);
        assertEquals(60, InputBoosterConfig.getPollRateHz());
        assertEquals(20, InputBoosterConfig.getMaxCps());

        InputBoosterConfig.setPollRateHz(600);
        InputBoosterConfig.setMaxCps(12);
        InputBoosterConfig.save();
        assertTrue(Files.exists(configFile()));

        InputBoosterConfig.setPollRateHz(60);
        InputBoosterConfig.load();
        assertEquals(600, InputBoosterConfig.getPollRateHz());
        assertEquals(12, InputBoosterConfig.getMaxCps());
    }
}