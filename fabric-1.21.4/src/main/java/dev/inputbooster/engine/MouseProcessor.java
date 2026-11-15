package dev.inputbooster.engine;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Receives mouse button and scroll events from {@code MouseHandlerMixin}.
 *
 * <p>Buttons are treated exactly like keys: they update state and produce an
 * edge. Scrolling is not an action the mod applies, so it is only counted, with
 * an optional debounce for clients where one wheel notch arrives as several
 * events.
 */
public final class MouseProcessor {

    /** Touched from the game thread and the poller; claimed atomically. */
    private static final AtomicLong lastScrollNanos = new AtomicLong();

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
        if (debounceMs > 0) {
            long window = debounceMs * 1_000_000L;
            while (true) {
                long last = lastScrollNanos.get();
                if (last != 0L && now - last < window) {
                    engine.diagnostics().duplicateSuppressed();
                    return;
                }
                if (lastScrollNanos.compareAndSet(last, now)) break;
            }
        } else {
            lastScrollNanos.set(now);
        }
        engine.diagnostics().scrollEvent();
    }

    public static void reset() {
        lastScrollNanos.set(0L);
    }
}
