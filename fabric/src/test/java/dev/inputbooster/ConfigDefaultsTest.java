package dev.inputbooster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static dev.inputbooster.InputBoosterConfig.Defaults;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The documented defaults must be the defaults that are actually used.
 *
 * <p>Two user interfaces offer a "reset to default" action — the mod's own
 * settings screen and the Sodium configuration pages — and both read them from
 * {@link InputBoosterConfig.Defaults}. If a default there ever drifted away
 * from the value the loader writes, a reset would quietly change a setting the
 * user never touched, so the two are compared here.
 */
class ConfigDefaultsTest {

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
                        // best effort cleanup
                    }
                });
            }
        }
        Files.createDirectories(configDir());
    }

    @Test
    void documentedDefaultsAreTheValuesAFreshConfigurationUses() throws IOException {
        Files.writeString(configFile(), "config_version=" + InputBoosterConfig.CONFIG_VERSION + "\n");
        InputBoosterConfig.load();

        assertEquals(Defaults.POLL_RATE_HZ, InputBoosterConfig.getPollRateHz());
        assertEquals(Defaults.POLL_RATE_AUTO, InputBoosterConfig.isPollRateAutoMode());
        assertEquals(Defaults.SPRINT_FIX, InputBoosterConfig.isSprintFixEnabled());
        assertEquals(Defaults.AUTO_SPRINT, InputBoosterConfig.isAutoSprintEnabled());
        assertEquals(Defaults.WTAP_ASSIST, InputBoosterConfig.isWTapAssistEnabled());
        assertEquals(Defaults.ANTI_IDLE, InputBoosterConfig.isAntiIdleEnabled());
        assertEquals(Defaults.AUTO_STRAFE, InputBoosterConfig.isAutoStrafeEnabled());
        assertEquals(Defaults.CPS_LIMITER, InputBoosterConfig.isCpsLimiterEnabled());
        assertEquals(Defaults.BURST_MODE, InputBoosterConfig.isBurstModeEnabled());
        assertEquals(Defaults.MAX_CPS, InputBoosterConfig.getMaxCps());
        assertEquals(Defaults.COMBO_KEYS, InputBoosterConfig.isComboKeysEnabled());
        assertEquals(Defaults.CPS_MODE, InputBoosterConfig.getCpsMode());
        assertEquals(Defaults.CUSTOM_KEYBOARD_HANDLER, InputBoosterConfig.isCustomKeyboardHandlerEnabled());
        assertEquals(Defaults.ENGINE_MODE, InputBoosterConfig.getEngineMode());
        assertEquals(Defaults.KEYBOARD_REPEAT_GUARD, InputBoosterConfig.isKeyboardRepeatGuard());
        assertEquals(Defaults.MOUSE_SCROLL_DEBOUNCE_MS, InputBoosterConfig.getMouseScrollDebounceMs());
        assertEquals(Defaults.MOUSE_CLICK_DEDUPE_MS, InputBoosterConfig.getMouseClickDedupeMs());
        assertEquals(Defaults.COMPAT_FALLBACK, InputBoosterConfig.isCompatFallback());
        assertEquals(Defaults.OVERLAY_MODE, InputBoosterConfig.getOverlayMode());
        assertEquals(Defaults.OVERLAY_SHOW_ENGINE, InputBoosterConfig.isOverlayShowEngine());
        assertEquals(Defaults.OVERLAY_SHOW_EVENTS, InputBoosterConfig.isOverlayShowEvents());
        assertEquals(Defaults.OVERLAY_SHOW_OVERHEAD, InputBoosterConfig.isOverlayShowOverhead());
        assertEquals(Defaults.SHOW_F3_INFO, InputBoosterConfig.isShowF3Info());
        assertEquals(Defaults.SHOW_KEYSTROKES, InputBoosterConfig.isShowKeystrokes());
        assertEquals(Defaults.SHOW_ACTION_BAR, InputBoosterConfig.isShowActionBar());
        assertEquals(Defaults.OVERLAY_POSITION, InputBoosterConfig.getOverlayPosition());
        assertEquals(Defaults.OVERLAY_OPACITY, InputBoosterConfig.getOverlayOpacity(), 1e-6);
        assertEquals(Defaults.OVERLAY_SCALE, InputBoosterConfig.getOverlayScale(), 1e-6);
        assertEquals(Defaults.REPLAY_ENABLED, InputBoosterConfig.isReplayEnabled());
        assertEquals(Defaults.SAFE_MODE, InputBoosterConfig.isSafeModeEnabled());
        assertEquals(Defaults.EVENT_LOG, InputBoosterConfig.isEventLogEnabled());
        assertEquals(Defaults.KEY_CONFLICT_WARN, InputBoosterConfig.isKeyConflictWarn());
        assertEquals(Defaults.PER_SERVER_PROFILES, InputBoosterConfig.isPerServerProfiles());
        assertEquals(Defaults.CLICK_SOUNDS, InputBoosterConfig.isClickSoundsEnabled());
        assertEquals(Defaults.CLICK_SOUND_PITCH, InputBoosterConfig.getClickSoundPitch(), 1e-6);
        assertEquals(Defaults.CLICK_SOUND_VOLUME, InputBoosterConfig.getClickSoundVolume(), 1e-6);
        assertEquals(Defaults.FPS_CHECK_INTERVAL, InputBoosterConfig.getFpsCheckInterval());
        assertEquals(Defaults.DEBUG_MODE, InputBoosterConfig.isDebugMode());
    }

    @Test
    void ensureLoadedKeepsAConfigurationThatWasAlreadyRead() throws IOException {
        Files.writeString(configFile(), "poll_rate_hz=250\n");
        InputBoosterConfig.load();
        assertEquals(250, InputBoosterConfig.getPollRateHz());

        // A file that appeared after the configuration was read must not
        // replace the values that are already in use.
        Files.writeString(configFile(), "poll_rate_hz=900\n");
        InputBoosterConfig.ensureLoaded();
        assertEquals(250, InputBoosterConfig.getPollRateHz());

        // A deliberate reload still picks the file up.
        InputBoosterConfig.load();
        assertEquals(900, InputBoosterConfig.getPollRateHz());
    }
}
