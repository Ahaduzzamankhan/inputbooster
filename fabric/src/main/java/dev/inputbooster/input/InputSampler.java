package dev.inputbooster.input;

import dev.inputbooster.InputBoosterMod;

/**
 * Publishes a fresh native key sample. <b>Game thread only.</b>
 *
 * <p>Called once per rendered frame from the HUD hook and once per tick from the
 * client tick, which is the whole reason {@link RawKeyState} no longer touches
 * the platform from the polling thread: GLFW's key query is a main-thread
 * function.
 */
public final class InputSampler {

    private InputSampler() {}

    /** Samples the bound keys into the snapshot the poller reads. */
    public static void sampleOnGameThread() {
        RawKeyState raw = InputBoosterMod.rawKeyState;
        KeyBindingSet bindings = InputBoosterMod.keyBindings;
        if (raw == null || bindings == null) return;
        raw.refresh(bindings);
    }
}
