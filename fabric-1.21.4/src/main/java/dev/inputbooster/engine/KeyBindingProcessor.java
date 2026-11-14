package dev.inputbooster.engine;

import dev.inputbooster.input.KeyBindingSet;

/**
 * Resolves a raw key code to the action slot it belongs to.
 *
 * <p>The bindings are republished once per client tick from vanilla
 * {@code KeyMapping}s. A flat 512-entry lookup table is rebuilt only when the
 * codes actually change, so the hot path is one array read instead of a loop
 * over twelve bindings. Codes bound to something the mod does not own resolve to
 * -1 and are ignored, which is what keeps other mods' key bindings untouched.
 */
public final class KeyBindingProcessor {

    private final int[] codeToSlot = new int[512];
    private int[] currentCodes;
    private boolean complete;

    public KeyBindingProcessor() {
        java.util.Arrays.fill(codeToSlot, -1);
    }

    /** @return true when the binding set changed and the table was rebuilt */
    public boolean update(KeyBindingSet bindings) {
        if (bindings == null) return false;
        int[] codes = bindings.codes;
        if (currentCodes != null && java.util.Arrays.equals(currentCodes, codes)) return false;

        java.util.Arrays.fill(codeToSlot, -1);
        for (int slot = 0; slot < codes.length && slot < KeyStateManager.SLOTS; slot++) {
            int code = codes[slot];
            if (code >= 0 && code < codeToSlot.length) {
                // First binding wins if two actions share a key, matching the
                // order the poller has always used.
                if (codeToSlot[code] == -1) codeToSlot[code] = slot;
            }
        }
        currentCodes = codes.clone();
        complete = bindings.complete;
        return true;
    }

    /** @return the action slot for this raw code, or -1 when the mod does not own it */
    public int slotForCode(int code) {
        if (code < 0 || code >= codeToSlot.length) return -1;
        return codeToSlot[code];
    }

    public int[] codes() {
        return currentCodes;
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
