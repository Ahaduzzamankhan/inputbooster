package dev.inputbooster.engine;

import dev.inputbooster.InputBoosterConfig;

/**
 * Typed access to the engine and overlay options added in 4.1.
 *
 * <p>The values themselves live in {@link InputBoosterConfig}, which already
 * owns loading, clamping and atomic saving. This class exists so the GUI, the
 * engine and the overlay all read and write those settings through one named
 * place instead of scattering integer magic numbers, and so a reset has one
 * implementation.
 */
public final class ConfigurationManager {

    public static final int ENGINE_AUTO = 0;
    public static final int ENGINE_EVENT = 1;
    public static final int ENGINE_POLL = 2;

    public static final int OVERLAY_F3_ONLY = 0;
    public static final int OVERLAY_ALWAYS = 1;

    private ConfigurationManager() {}

    public static int engineMode() {
        return InputBoosterConfig.getEngineMode();
    }

    public static void setEngineMode(int mode) {
        InputBoosterConfig.setEngineMode(Math.max(ENGINE_AUTO, Math.min(ENGINE_POLL, mode)));
    }

    public static String engineModeLabel() {
        return switch (engineMode()) {
            case ENGINE_EVENT -> "Event driven";
            case ENGINE_POLL -> "Polled";
            default -> "Automatic";
        };
    }

    public static boolean repeatGuard() {
        return InputBoosterConfig.isKeyboardRepeatGuard();
    }

    public static void setRepeatGuard(boolean value) {
        InputBoosterConfig.setKeyboardRepeatGuard(value);
    }

    public static int scrollDebounceMs() {
        return InputBoosterConfig.getMouseScrollDebounceMs();
    }

    public static void setScrollDebounceMs(int ms) {
        InputBoosterConfig.setMouseScrollDebounceMs(ms);
    }

    public static int clickDedupeMs() {
        return InputBoosterConfig.getMouseClickDedupeMs();
    }

    public static void setClickDedupeMs(int ms) {
        InputBoosterConfig.setMouseClickDedupeMs(ms);
    }

    public static boolean compatFallback() {
        return InputBoosterConfig.isCompatFallback();
    }

    public static void setCompatFallback(boolean value) {
        InputBoosterConfig.setCompatFallback(value);
    }

    public static int overlayMode() {
        return InputBoosterConfig.getOverlayMode();
    }

    public static void setOverlayMode(int mode) {
        InputBoosterConfig.setOverlayMode(mode >= OVERLAY_ALWAYS ? OVERLAY_ALWAYS : OVERLAY_F3_ONLY);
    }

    public static String overlayModeLabel() {
        return overlayMode() == OVERLAY_ALWAYS ? "Always" : "With F3 only";
    }

    /** Restores every engine and overlay option to its documented default. */
    public static void resetEngineAndOverlay() {
        setEngineMode(InputBoosterConfig.Defaults.ENGINE_MODE);
        setRepeatGuard(InputBoosterConfig.Defaults.KEYBOARD_REPEAT_GUARD);
        setScrollDebounceMs(InputBoosterConfig.Defaults.MOUSE_SCROLL_DEBOUNCE_MS);
        setClickDedupeMs(InputBoosterConfig.Defaults.MOUSE_CLICK_DEDUPE_MS);
        setCompatFallback(InputBoosterConfig.Defaults.COMPAT_FALLBACK);
        setOverlayMode(InputBoosterConfig.Defaults.OVERLAY_MODE);
        InputBoosterConfig.setOverlayShowEngine(InputBoosterConfig.Defaults.OVERLAY_SHOW_ENGINE);
        InputBoosterConfig.setOverlayShowEvents(InputBoosterConfig.Defaults.OVERLAY_SHOW_EVENTS);
        InputBoosterConfig.setOverlayShowOverhead(InputBoosterConfig.Defaults.OVERLAY_SHOW_OVERHEAD);
        InputBoosterConfig.save();
    }
}
