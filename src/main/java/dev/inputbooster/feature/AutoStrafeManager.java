package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * AutoStrafeManager
 * Author: Ahaduzzaman Khan
 */
public class AutoStrafeManager {

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isAutoStrafeEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!player.isSprinting()) return;

        if (Minecraft.getInstance().getFps() > 40) return;

        boolean left  = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        boolean fwd   = mc.options.keyUp.isDown();

        if (!fwd) return;
        if (left == right) return;

        int fps = Minecraft.getInstance().getFps();
        float yawDelta = left ? -0.4f : 0.4f;
        player.setYRot(player.getYRot() + yawDelta * (40f / Math.max(1, fps)));
    }
}
