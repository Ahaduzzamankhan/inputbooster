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

    /** Immutable; the polling thread only ever reads it. */
    public final int[] codes;
    /** True when at least one slot could not be resolved. */
    public final boolean complete;

    private KeyBindingSet(int[] codes, boolean complete) {
        this.codes = codes;
        this.complete = complete;
    }

    public int code(int slot) {
        return codes[slot];
    }

    /** Builds the binding set, falling back to the vanilla defaults per slot. */
    public static KeyBindingSet of(Map<String, KeyMapping> bindings, int[] defaults) {
        int[] codes = new int[SLOTS];
        boolean complete = true;
        for (int i = 0; i < SLOTS; i++) {
            KeyMapping mapping = bindings.get(NAMES[i]);
            int code = -1;
            if (mapping != null) {
                try {
                    code = dev.inputbooster.mixin.KeyMappingAccessor.boundCode(mapping);
                } catch (Throwable t) {
                    code = -1;
                }
            }
            if (code < 0) {
                code = defaults[i];
                complete = false;
            }
            codes[i] = code;
        }
        return new KeyBindingSet(codes, complete);
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
            mouseAttack, mouseUse, InputConstants.KEY_LSHIFT, InputConstants.KEY_LCONTROL,
            InputConstants.KEY_SPACE, InputConstants.KEY_W, InputConstants.KEY_S,
            InputConstants.KEY_A, InputConstants.KEY_D, InputConstants.KEY_Q,
            InputConstants.KEY_F, middleMouse
        };
    }
}