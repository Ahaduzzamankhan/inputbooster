package dev.inputbooster.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The set of key codes the polling thread samples at high frequency.
 *
 * Published once per client tick from the vanilla {@link KeyMapping}s, so the
 * poll rate is independent of the tick rate while the sampled keys always
 * follow the player's own key bindings.
 */
public final class KeyBindingSet {

    public static final int SLOTS = 12;

    /** Order must match {@link dev.inputbooster.KeySnapshot}'s field order. */
    private static final String[] NAMES = {
        "attack", "use", "sprint", "sneak", "jump", "forward",
        "back", "left", "right", "drop", "swap", "pickBlock"
    };

    /** Slot indices, matching the constants used by KeySnapshot. */
    public static final int ATTACK = 0, USE = 1, SPRINT = 2, SNEAK = 3, JUMP = 4,
        FORWARD = 5, BACK = 6, LEFT = 7, RIGHT = 8, DROP = 9, SWAP = 10, PICK_BLOCK = 11;

    /**
     * Never handed out directly: the polling thread and the game thread both
     * read these codes, and an accidental write from either side would silently
     * rebind an action mid-fight. Use {@link #code(int)} or {@link #copyCodes()}.
     */
    private final int[] codes;
    /** True when at least one slot could not be resolved. */
    public final boolean complete;

    /** Bit per slot whose vanilla default was substituted. */
    private final int unresolvedMask;
    /** Bit per slot whose mapping existed but could not be read. */
    private final int unreadableMask;

    private KeyBindingSet(int[] codes, int unresolvedMask, int unreadableMask) {
        this.codes = codes;
        this.unresolvedMask = unresolvedMask;
        this.unreadableMask = unreadableMask;
        this.complete = unresolvedMask == 0;
    }

    /** True when every slot resolved to the player's own binding. */
    public boolean complete() {
        return complete;
    }

    /** Bit mask of the slots that fell back to a vanilla default. */
    public int unresolvedMask() {
        return unresolvedMask;
    }

    /**
     * Bit mask of the slots where a mapping existed but its bound key could not
     * be read, which is the only case that is a genuine accessor failure.
     */
    public int unreadableMask() {
        return unreadableMask;
    }

    /** Names of the slots that fell back, in slot order. */
    public java.util.List<String> unresolvedNames() {
        java.util.List<String> names = new java.util.ArrayList<>(Integer.bitCount(unresolvedMask));
        for (int slot = 0; slot < SLOTS; slot++) {
            if ((unresolvedMask & (1 << slot)) != 0) names.add(NAMES[slot]);
        }
        return names;
    }

    public int code(int slot) {
        return slot >= 0 && slot < codes.length ? codes[slot] : -1;
    }

    /** @return a copy the caller may keep or modify without touching this set */
    public int[] copyCodes() {
        return codes.clone();
    }

    /** True when every slot holds the same code as {@code other}. */
    public boolean sameCodes(KeyBindingSet other) {
        return other != null && java.util.Arrays.equals(codes, other.codes);
    }

    /** Builds the binding set, falling back to the vanilla defaults per slot. */
    public static KeyBindingSet of(Map<String, KeyMapping> bindings, int[] defaults) {
        int[] codes = new int[SLOTS];
        int unresolved = 0;
        int unreadable = 0;
        for (int i = 0; i < SLOTS; i++) {
            KeyMapping mapping = bindings.get(NAMES[i]);
            int code = -1;
            if (mapping != null) {
                try {
                    code = dev.inputbooster.access.MixinAccess.boundCode(mapping);
                } catch (Throwable t) {
                    code = -1;
                }
                // The mapping exists but its key could not be read. That is an
                // accessor failure, not an unbound key, so it is tracked apart
                // from a slot that simply had no mapping handed in.
                if (code < 0) unreadable |= 1 << i;
            }
            if (code < 0) {
                // A fallback is used so the mod still works, but the caller can
                // now say exactly which slots were guessed.
                code = defaults[i];
                unresolved |= 1 << i;
            }
            codes[i] = code;
        }
        return new KeyBindingSet(codes, unresolved, unreadable);
    }

    public static Map<String, KeyMapping> map(KeyMapping attack, KeyMapping use, KeyMapping sprint,
                                              KeyMapping sneak, KeyMapping jump, KeyMapping forward,
                                              KeyMapping back, KeyMapping left, KeyMapping right,
                                              KeyMapping drop, KeyMapping swap, KeyMapping pickBlock) {
        Map<String, KeyMapping> map = new LinkedHashMap<>();
        map.put(NAMES[0], attack);
        map.put(NAMES[1], use);
        map.put(NAMES[2], sprint);
        map.put(NAMES[3], sneak);
        map.put(NAMES[4], jump);
        map.put(NAMES[5], forward);
        map.put(NAMES[6], back);
        map.put(NAMES[7], left);
        map.put(NAMES[8], right);
        map.put(NAMES[9], drop);
        map.put(NAMES[10], swap);
        map.put(NAMES[11], pickBlock);
        return map;
    }

    /** Vanilla defaults, used when a binding cannot be read. */
    public static int[] defaultCodes(int mouseAttack, int mouseUse, int middleMouse) {
        return new int[]{
            // Slot order is sprint then sneak: vanilla binds sprint to left
            // control and sneak to left shift, the reverse of the old array.
            mouseAttack, mouseUse, InputConstants.KEY_LCONTROL, InputConstants.KEY_LSHIFT,
            InputConstants.KEY_SPACE, InputConstants.KEY_W, InputConstants.KEY_S,
            InputConstants.KEY_A, InputConstants.KEY_D, InputConstants.KEY_Q,
            InputConstants.KEY_F, middleMouse
        };
    }
}