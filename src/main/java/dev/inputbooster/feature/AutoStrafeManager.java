package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class AutoStrafeManager {
    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isAutoStrafeEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (!player.isSprinting()) return;
        if (McCompat.getFps(mc) > 40) return;

        boolean left  = mc.options.keyLeft.isPressed();
        boolean right = mc.options.keyRight.isPressed();
        boolean fwd   = mc.options.keyUp.isPressed();

        if (!fwd) return;
        if (left == right) return;

        float yawDelta = left ? -0.4f : 0.4f;
        player.setYRot(player.getYaw() + yawDelta * (40f / Math.max(1, McCompat.getFps(mc))));
    }
}
