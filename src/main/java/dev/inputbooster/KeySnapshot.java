package dev.inputbooster;

import net.minecraft.client.option.GameOptions;

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
