package dev.inputbooster;

import java.util.Arrays;

/**
 * Ctrl + 1..5 poll-rate combo keys.
 *
 * Extracted from {@link InputBoosterMod} so the edge-detection latch can be
 * unit tested without a GLFW window or a running game.
 */
public final class ComboKeyPresets {

    /** Poll rate (Hz) applied by each combo key. */
    public static final int[] HZ = {100, 200, 350, 500, 1000};

    private final boolean[] held = new boolean[HZ.length];

    /**
     * Feeds the physical key state for one digit.
     *
     * @return true only on the tick where the digit transitioned to pressed.
     */
    public boolean press(int index, boolean pressed) {
        if (index < 0 || index >= held.length) return false;
        boolean justPressed = pressed && !held[index];
        held[index] = pressed;
        return justPressed;
    }

    /** Forgets all held digits (used when Ctrl is released or a screen opens). */
    public void reset() {
        Arrays.fill(held, false);
    }

    public int size() {
        return HZ.length;
    }
}