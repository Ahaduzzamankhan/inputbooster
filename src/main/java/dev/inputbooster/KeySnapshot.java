package dev.inputbooster;

import net.minecraft.client.option.GameOptions;

/**
 * KeySnapshot
 *
 * An immutable snapshot of all tracked key states, captured on the main thread
 * once per tick and published to a volatile field for the polling thread to read.
 *
 * This eliminates the root cause of "game not responding": the polling thread was
 * reading mc.options key states directly, which is not thread-safe and caused
 * contention / deadlocks with Minecraft's main thread.
 *
 * Author: Ahaduzzaman Khan
 */
public final class KeySnapshot {
    public final boolean attack, use, sprint, sneak;
    public final boolean jump, forward, back, left, right;
    public final boolean drop, swap, pickBlock;

    public KeySnapshot(GameOptions opt) {
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
}
