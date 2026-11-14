package dev.inputbooster.input;

import com.mojang.blaze3d.platform.Window;
import dev.inputbooster.compat.McVersion;
import dev.inputbooster.InputBoosterMod;

/**
 * Reads raw key state between Minecraft ticks.
 *
 * This is what makes the polling thread more than a fast loop over a snapshot
 * that only changes once per tick: the platform key state changes the moment a
 * key is pressed, so a short tap is still observed even when it starts and ends
 * inside a single game tick.
 *
 * The window reference is captured once on the game thread. If any native call
 * fails the sampler degrades to "everything released" and the polling thread
 * falls back to the tick-rate snapshot, so a windowing failure can never take
 * the client down.
 */
public final class RawKeyState {

    private final Window window;
    private volatile boolean failed = false;
    private volatile boolean reported = false;

    private RawKeyState(Window window) {
        this.window = window;
    }

    /** @param window captured on the game thread; may be null (headless/tests). */
    public static RawKeyState of(Window window) {
        return new RawKeyState(window);
    }

    public boolean isAvailable() {
        return window != null && !failed;
    }

    /** @return true when the key is physically down right now. */
    public boolean isDown(int keyCode) {
        if (keyCode < 0) return false;
        if (window == null || failed) return false;
        try {
            return McVersion.isKeyDown(window, keyCode);
        } catch (Throwable t) {
            if (!failed) {
                failed = true;
                if (!reported) {
                    reported = true;
                    InputBoosterMod.LOGGER.warn(
                        "[Input] Raw key state unavailable ({}); falling back to tick-rate input sampling.",
                        t.toString());
                }
            }
            return false;
        }
    }

    public Window window() {
        return window;
    }
}