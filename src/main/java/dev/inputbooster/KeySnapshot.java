package dev.inputbooster;

import net.minecraft.client.Options;

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

    public KeySnapshot(Options opt) {
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
}
