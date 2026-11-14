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
 * The new 4.1 settings must persist across restarts, which is a GUI requirement
 * and a regression risk: a control that writes memory but never reaches disk
 * looks fine until the game is restarted.
 */
class EngineConfigTest {

    private static Path configDir() {
        return Path.of(System.getProperty("inputbooster.configDir", "config"));
    }

    private static Path configFile() {
        return configDir().resolve("inputbooster.properties");
    }

    @BeforeEach
    void clean() throws IOException {
        if (Files.exists(configDir())) {
            try (Stream<Path> paths = Files.walk(configDir())) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // best effort
                    }
                });
            }
        }
        Files.createDirectories(configDir());
    }

    @Test
    void newEngineSettingsBecomeDefaultsWhenAbsent() throws IOException {
        Files.writeString(configFile(), "poll_rate_hz=200\n");
        InputBoosterConfig.load();

        assertEquals(0, InputBoosterConfig.getEngineMode());
        assertTrue(InputBoosterConfig.isKeyboardRepeatGuard());
        assertEquals(0, InputBoosterConfig.getMouseScrollDebounceMs());
        assertEquals(0, InputBoosterConfig.getMouseClickDedupeMs());
        assertTrue(InputBoosterConfig.isCompatFallback());
        assertEquals(0, InputBoosterConfig.getOverlayMode());
        assertTrue(InputBoosterConfig.isOverlayShowEngine());
        assertTrue(InputBoosterConfig.isOverlayShowEvents());
        assertTrue(InputBoosterConfig.isOverlayShowOverhead());
    }

    @Test
    void newEngineSettingsRoundTripThroughDisk() throws IOException {
        InputBoosterConfig.load();

        InputBoosterConfig.setEngineMode(2);
        InputBoosterConfig.setKeyboardRepeatGuard(false);
        InputBoosterConfig.setMouseScrollDebounceMs(25);
        InputBoosterConfig.setMouseClickDedupeMs(10);
        InputBoosterConfig.setCompatFallback(false);
        InputBoosterConfig.setOverlayMode(1);
        InputBoosterConfig.setOverlayShowEngine(false);
        InputBoosterConfig.setOverlayShowEvents(false);
        InputBoosterConfig.setOverlayShowOverhead(false);
        InputBoosterConfig.save();

        // Forget the in-memory values, then read them back from the file.
        InputBoosterConfig.setEngineMode(0);
        InputBoosterConfig.setKeyboardRepeatGuard(true);
        InputBoosterConfig.setMouseScrollDebounceMs(0);
        InputBoosterConfig.setMouseClickDedupeMs(0);
        InputBoosterConfig.setCompatFallback(true);
        InputBoosterConfig.setOverlayMode(0);
        InputBoosterConfig.setOverlayShowEngine(true);
        InputBoosterConfig.setOverlayShowEvents(true);
        InputBoosterConfig.setOverlayShowOverhead(true);
        InputBoosterConfig.load();

        assertEquals(2, InputBoosterConfig.getEngineMode());
        assertFalse(InputBoosterConfig.isKeyboardRepeatGuard());
        assertEquals(25, InputBoosterConfig.getMouseScrollDebounceMs());
        assertEquals(10, InputBoosterConfig.getMouseClickDedupeMs());
        assertFalse(InputBoosterConfig.isCompatFallback());
        assertEquals(1, InputBoosterConfig.getOverlayMode());
        assertFalse(InputBoosterConfig.isOverlayShowEngine());
        assertFalse(InputBoosterConfig.isOverlayShowEvents());
        assertFalse(InputBoosterConfig.isOverlayShowOverhead());
    }

    @Test
    void invalidValuesAreClampedNotAccepted() throws IOException {
        Files.writeString(configFile(), """
            engine_mode=9
            mouse_scroll_debounce_ms=9999
            mouse_click_dedupe_ms=-4
            overlay_mode=7
            """);
        InputBoosterConfig.load();

        assertEquals(2, InputBoosterConfig.getEngineMode());
        assertEquals(200, InputBoosterConfig.getMouseScrollDebounceMs());
        assertEquals(0, InputBoosterConfig.getMouseClickDedupeMs());
        assertEquals(1, InputBoosterConfig.getOverlayMode());
    }

    @Test
    void schemaVersionIsCurrentAfterLoad() throws IOException {
        Files.writeString(configFile(), "config_version=304\nengine_mode=1\n");
        InputBoosterConfig.load();

        assertEquals(InputBoosterConfig.CONFIG_VERSION, InputBoosterConfig.getConfigVersion());
        assertEquals(1, InputBoosterConfig.getEngineMode());
    }
}
