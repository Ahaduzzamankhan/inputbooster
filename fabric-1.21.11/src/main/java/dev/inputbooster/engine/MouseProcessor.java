package dev.inputbooster.engine;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

/**
 * Receives mouse button and scroll events from {@code MouseHandlerMixin}.
 *
 * <p>Buttons are treated exactly like keys: they update state and produce an
 * edge. Scrolling is not an action the mod applies, so it is only counted, with
 * an optional debounce for clients where one wheel notch arrives as several
 * events.
 */
public final class MouseProcessor {

    private static long lastScrollNanos;

    private MouseProcessor() {}

    public static void onButton(int button, int action) {
        InputEngine engine = InputBoosterMod.inputEngine;
        if (engine == null) return;
        engine.diagnostics().mouseEvent();
        if (action != KeyboardProcessor.PRESS && action != KeyboardProcessor.RELEASE) return;
        engine.onKeyState(button, action == KeyboardProcessor.PRESS);
    }

    public static void onScroll(double amount) {
        InputEngine engine = InputBoosterMod.inputEngine;
        if (engine == null) return;
        long now = System.nanoTime();
        int debounceMs = InputBoosterConfig.getMouseScrollDebounceMs();
        if (debounceMs > 0 && now - lastScrollNanos < debounceMs * 1_000_000L) {
            engine.diagnostics().duplicateSuppressed();
            return;
        }
        lastScrollNanos = now;
        engine.diagnostics().scrollEvent();
    }

    public static void reset() {
        lastScrollNanos = 0L;
    }
}
