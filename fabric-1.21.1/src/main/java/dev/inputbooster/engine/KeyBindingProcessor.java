package dev.inputbooster.engine;

import dev.inputbooster.input.KeyBindingSet;

/**
 * Resolves a raw key code to the action slots it belongs to.
 *
 * <p>The bindings are republished once per client tick from vanilla
 * {@code KeyMapping}s. A flat 512-entry table of bit masks is rebuilt only when
 * the codes actually change, so the hot path is one array read.
 *
 * <p>The table maps a code to a <em>mask</em> rather than to a single slot. Two
 * actions can legitimately share a key (attack and use on the same mouse
 * button, a custom binding on the same letter as a movement key), and the
 * polling path has always applied every matching slot. Resolving only the first
 * match here made event-driven mode disagree with polling: one key press
 * produced one action instead of two depending on which source saw it first.
 *
 * <p>Codes bound to something the mod does not own resolve to an empty mask and
 * are ignored, which is what keeps other mods' key bindings untouched.
 */
public final class KeyBindingProcessor {

    private static final int CODE_SPACE = 512;

    private final long[] codeToMask = new long[CODE_SPACE];
    private int[] currentCodes;
    private boolean complete;

    public KeyBindingProcessor() {
        java.util.Arrays.fill(codeToMask, 0L);
    }

    /** @return true when the binding set changed and the table was rebuilt */
    public boolean update(KeyBindingSet bindings) {
        if (bindings == null) return false;
        int[] codes = bindings.copyCodes();
        if (currentCodes != null && java.util.Arrays.equals(currentCodes, codes)) return false;

        java.util.Arrays.fill(codeToMask, 0L);
        for (int slot = 0; slot < codes.length && slot < KeyStateManager.SLOTS; slot++) {
            int code = codes[slot];
            if (code >= 0 && code < CODE_SPACE) {
                // Every slot is recorded, so a shared key drives all of them.
                codeToMask[code] |= 1L << slot;
            }
        }
        currentCodes = codes;
        complete = bindings.complete;
        return true;
    }

    /** @return every slot bound to this code, as a bit mask (0 when none) */
    public long maskForCode(int code) {
        if (code < 0 || code >= CODE_SPACE) return 0L;
        return codeToMask[code];
    }

    /**
     * @return the first action slot for this raw code, or -1 when the mod does
     *         not own it. Kept for callers that only need one slot; dispatch
     *         uses {@link #maskForCode(int)}.
     */
    public int slotForCode(int code) {
        long mask = maskForCode(code);
        return mask == 0L ? -1 : Long.numberOfTrailingZeros(mask);
    }

    /** @return a copy of the codes in use, or null before the first publish */
    public int[] codes() {
        return currentCodes == null ? null : currentCodes.clone();
    }

    public boolean complete() {
        return complete;
    }

    /** Number of slots whose key could not be resolved. */
    public int unresolved() {
        int missing = 0;
        if (currentCodes == null) return KeyStateManager.SLOTS;
        for (int code : currentCodes) if (code < 0) missing++;
        return missing;
    }
}
