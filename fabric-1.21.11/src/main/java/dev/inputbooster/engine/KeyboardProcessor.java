package dev.inputbooster.engine;

import dev.inputbooster.InputBoosterMod;

/**
 * Receives keyboard events from {@code KeyboardHandlerMixin}.
 *
 * <p>The mixin only observes: it runs at HEAD, cancels nothing and lets vanilla
 * route the event exactly as before. That is the whole compatibility story. This
 * class turns the event into engine state, and the engine decides whether an
 * action follows.
 *
 * <p>GLFW's repeat action is counted but never turned into a second press. A
 * held key is one press and one release, which is what the rest of the pipeline
 * expects.
 */
public final class KeyboardProcessor {

    /** GLFW actions. */
    public static final int RELEASE = 0;
    public static final int PRESS = 1;
    public static final int REPEAT = 2;

    private KeyboardProcessor() {}

    public static void onKeyEvent(int key, int action) {
        InputEngine engine = InputBoosterMod.inputEngine;
        if (engine == null) return;

        DiagnosticsManager diagnostics = engine.diagnostics();
        diagnostics.keyboardEvent();

        if (action == REPEAT) {
            diagnostics.repeatIgnored();
            return;
        }
        if (action != PRESS && action != RELEASE) return;

        engine.onKeyState(key, action == PRESS);
    }

    /** Called from the engine when the event source goes away. */
    public static void reset() {
        // Nothing buffered here: the engine owns all state. Kept so the mixin
        // has a stable, explicit shutdown call site.
    }
}
