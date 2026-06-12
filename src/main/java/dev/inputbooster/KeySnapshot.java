package dev.inputbooster;

import net.minecraft.client.option.GameOptions;

/**
 * KeySnapshot — immutable snapshot of key states for a single polling cycle.
 *
 * FIX: All fields are final and the object is constructed atomically on the
 * game tick thread, then published via a volatile reference in InputBoosterMod.
 * The polling thread always reads a complete, consistent snapshot rather than
 * individual volatile booleans that could be seen partially written.
 *
 * This eliminates the race condition where the polling thread could read a
 * mix of old and new key states from a snapshot being concurrently written.
 */
public final class KeySnapshot {
    public final boolean attack, use, sprint, sneak;
    public final boolean jump, forward, back, left, right;
    public final boolean drop, swap, pickBlock;

    public KeySnapshot(GameOptions opt) {
        // All reads happen on the game tick thread — safe, consistent snapshot.
        // The volatile write to InputBoosterMod.keySnapshot ensures the polling
        // thread sees the fully constructed object (Java memory model guarantee:
        // a volatile write happens-after all prior writes in the same thread).
        this.attack    = opt.attackKey.isPressed();
        this.use       = opt.useKey.isPressed();
        this.sprint    = opt.sprintKey.isPressed();
        this.sneak     = opt.sneakKey.isPressed();
        this.jump      = opt.jumpKey.isPressed();
        this.forward   = opt.forwardKey.isPressed();
        this.back      = opt.backKey.isPressed();
        this.left      = opt.leftKey.isPressed();
        this.right     = opt.rightKey.isPressed();
        this.drop      = opt.dropKey.isPressed();
        this.swap      = opt.swapHandsKey.isPressed();
        this.pickBlock = opt.pickItemKey.isPressed();
    }

    /** Returns an empty snapshot (all keys released). Used during init/pause. */
    public static final KeySnapshot EMPTY = new KeySnapshot();

    private KeySnapshot() {
        this.attack = this.use = this.sprint = this.sneak = false;
        this.jump = this.forward = this.back = this.left = this.right = false;
        this.drop = this.swap = this.pickBlock = false;
    }
}
