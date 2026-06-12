package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.McCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AutoStrafeManager {
    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isAutoStrafeEnabled()) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        if (!player.isSprinting()) return;
        if (McCompat.getFps(mc) > 40) return;

        boolean left  = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean fwd   = mc.options.forwardKey.isPressed();

        if (!fwd) return;
        if (left == right) return;

        float yawDelta = left ? -0.4f : 0.4f;
        player.setYaw(player.getYaw() + yawDelta * (40f / Math.max(1, McCompat.getFps(mc))));
    }
}
