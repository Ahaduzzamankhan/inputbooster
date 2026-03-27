package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * AutoStrafeManager
 *
 * At low FPS, strafing while sprinting loses speed due to infrequent
 * movement updates. This subtly corrects the yaw each tick when the
 * player is strafing to maintain optimal sprint speed.
 *
 * Only active when FPS < 40 and player is sprinting + strafing.
 *
 * Author: Ahaduzzaman Khan
 */
public class AutoStrafeManager {

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isAutoStrafeEnabled()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        if (!player.isSprinting()) return;

        // Only compensate at low FPS
        if (mc.getCurrentFps() > 40) return;

        boolean left  = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();
        boolean fwd   = mc.options.forwardKey.isPressed();

        if (!fwd) return;
        if (left == right) return; // neither or both — no pure strafe

        // Apply a tiny yaw correction to keep sprint-strafe speed optimal
        // (Vanilla loses ~15% speed when strafing diagonally at low FPS)
        float yawDelta = left ? -0.4f : 0.4f;
        player.setYaw(player.getYaw() + yawDelta * (40f / Math.max(1, mc.getCurrentFps())));
    }
}
