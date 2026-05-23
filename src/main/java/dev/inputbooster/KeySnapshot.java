package dev.inputbooster;

import net.minecraft.client.Options;

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

    public static final KeySnapshot EMPTY = new KeySnapshot();

    private KeySnapshot() {
        this.attack = this.use = this.sprint = this.sneak = false;
        this.jump = this.forward = this.back = this.left = this.right = false;
        this.drop = this.swap = this.pickBlock = false;
    }
}
