package dev.inputbooster;

import dev.inputbooster.input.KeyBindingSet;
import dev.inputbooster.input.RawKeyState;
import net.minecraft.client.Options;

/**
 * Immutable snapshot of the key states the input pipeline reacts to.
 *
 * Two sources are supported:
 *  - {@link #KeySnapshot(Options)} — Minecraft's per-tick key bindings. Used as
 *    a fallback when raw key state cannot be read.
 *  - {@link #fromRaw(RawKeyState, KeyBindingSet)} — the platform key state,
 *    sampled by the polling thread between ticks. This is what gives the mod
 *    genuine sub-tick input resolution.
 */
public final class KeySnapshot {
    public final boolean attack, use, sprint, sneak;
    public final boolean jump, forward, back, left, right;
    public final boolean drop, swap, pickBlock;

    public KeySnapshot(Options opt) {
        // All reads happen on the game tick thread — safe, consistent snapshot.
        // The volatile write to InputBoosterMod.keySnapshot ensures the polling
        // thread sees the fully constructed object (Java memory model guarantee:
        // a volatile write happens-after all prior writes in the same thread).
        this.attack    = opt.keyAttack.isDown();
        this.use       = opt.keyUse.isDown();
        this.sprint    = opt.keySprint.isDown();
        this.sneak     = opt.keyShift.isDown();
        this.jump      = opt.keyJump.isDown();
        this.forward   = opt.keyUp.isDown();
        this.back      = opt.keyDown.isDown();
        this.left      = opt.keyLeft.isDown();
        this.right     = opt.keyRight.isDown();
        this.drop      = opt.keyDrop.isDown();
        this.swap      = opt.keySwapOffhand.isDown();
        this.pickBlock = opt.keyPickItem.isDown();
    }

    private KeySnapshot(boolean attack, boolean use, boolean sprint, boolean sneak, boolean jump,
                        boolean forward, boolean back, boolean left, boolean right,
                        boolean drop, boolean swap, boolean pickBlock) {
        this.attack = attack;
        this.use = use;
        this.sprint = sprint;
        this.sneak = sneak;
        this.jump = jump;
        this.forward = forward;
        this.back = back;
        this.left = left;
        this.right = right;
        this.drop = drop;
        this.swap = swap;
        this.pickBlock = pickBlock;
    }

    /** Samples the platform key state for the player's current key bindings. */
    public static KeySnapshot fromRaw(RawKeyState state, KeyBindingSet bindings) {
        return new KeySnapshot(
            state.isDown(bindings.code(KeyBindingSet.ATTACK)),
            state.isDown(bindings.code(KeyBindingSet.USE)),
            state.isDown(bindings.code(KeyBindingSet.SPRINT)),
            state.isDown(bindings.code(KeyBindingSet.SNEAK)),
            state.isDown(bindings.code(KeyBindingSet.JUMP)),
            state.isDown(bindings.code(KeyBindingSet.FORWARD)),
            state.isDown(bindings.code(KeyBindingSet.BACK)),
            state.isDown(bindings.code(KeyBindingSet.LEFT)),
            state.isDown(bindings.code(KeyBindingSet.RIGHT)),
            state.isDown(bindings.code(KeyBindingSet.DROP)),
            state.isDown(bindings.code(KeyBindingSet.SWAP)),
            state.isDown(bindings.code(KeyBindingSet.PICK_BLOCK))
        );
    }

    /** Returns an empty snapshot (all keys released). Used during init/pause. */
    public static final KeySnapshot EMPTY = new KeySnapshot();

    private KeySnapshot() {
        this.attack = this.use = this.sprint = this.sneak = false;
        this.jump = this.forward = this.back = this.left = this.right = false;
        this.drop = this.swap = this.pickBlock = false;
    }
}