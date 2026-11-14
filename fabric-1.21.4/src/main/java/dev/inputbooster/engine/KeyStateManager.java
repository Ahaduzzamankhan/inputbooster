package dev.inputbooster.engine;

/**
 * Authoritative physical state of everything the engine tracks.
 *
 * <p>Two views are kept:
 * <ul>
 *   <li>a 512-bit {@code long[]} for raw key codes, so modifier and bound-key
 *       lookups are a shift and a mask with no allocation, and</li>
 *   <li>one boolean per action slot, which is what edge detection runs on.</li>
 * </ul>
 *
 * <p>Every source of truth (the raw poller, the keyboard handler events, the
 * mouse handler events) writes through {@link #updateSlot(int, boolean)}, which
 * reports whether the value actually changed. That single rule is what makes
 * duplicate suppression structural: the second source to report the same press
 * sees no change and therefore emits no second event. There is no timing window
 * to tune and no "already dispatched" flag to leak across ticks.
 *
 * <p>Not thread-safe by design. The game thread and the polling thread both use
 * it, so the engine hands each of them its own instance and merges edges rather
 * than adding a lock to the hot path.
 */
public final class KeyStateManager {

    /** Number of action slots; matches {@code KeyBindingSet.SLOTS}. */
    public static final int SLOTS = 12;

    private static final int CODE_WORDS = 8; // 8 * 64 = 512 codes

    private final long[] rawDown = new long[CODE_WORDS];
    private final boolean[] slotDown = new boolean[SLOTS];
    private final boolean[] slotPrev = new boolean[SLOTS];
    private final long[] slotChangeNanos = new long[SLOTS];

    /** Records a raw key/button code. */
    public void setRaw(int code, boolean down) {
        if (code < 0 || code >= CODE_WORDS * 64) return;
        int word = code >>> 6;
        long mask = 1L << (code & 63);
        if (down) rawDown[word] |= mask;
        else rawDown[word] &= ~mask;
    }

    public boolean isRawDown(int code) {
        if (code < 0 || code >= CODE_WORDS * 64) return false;
        return (rawDown[code >>> 6] & (1L << (code & 63))) != 0L;
    }

    /**
     * Updates one action slot.
     *
     * @return true when the value changed, i.e. when an edge should be emitted
     */
    public boolean updateSlot(int slot, boolean down) {
        if (slot < 0 || slot >= SLOTS) return false;
        if (slotDown[slot] == down) return false;
        slotDown[slot] = down;
        slotChangeNanos[slot] = System.nanoTime();
        return true;
    }

    public boolean isSlotDown(int slot) {
        return slot >= 0 && slot < SLOTS && slotDown[slot];
    }

    /** Sets the "previous" snapshot the next edge comparison uses. */
    public void markPrevious(int slot, boolean down) {
        if (slot >= 0 && slot < SLOTS) slotPrev[slot] = down;
    }

    public boolean previous(int slot) {
        return slot >= 0 && slot < SLOTS && slotPrev[slot];
    }

    public long lastChangeNanos(int slot) {
        return slot >= 0 && slot < SLOTS ? slotChangeNanos[slot] : 0L;
    }

    /** Releases every tracked key, e.g. on focus loss or a world change. */
    public void releaseAll() {
        java.util.Arrays.fill(rawDown, 0L);
        java.util.Arrays.fill(slotDown, false);
        java.util.Arrays.fill(slotPrev, false);
    }

    /** Copies the slot bits into {@code out}; returns {@code out}. */
    public boolean[] copySlots(boolean[] out) {
        System.arraycopy(slotDown, 0, out, 0, Math.min(out.length, SLOTS));
        System.arraycopy(slotDown, 0, slotPrev, 0, SLOTS);
        return out;
    }
}
