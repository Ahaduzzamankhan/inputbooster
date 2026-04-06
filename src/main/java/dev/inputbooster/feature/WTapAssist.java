package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * WTapAssist
 * Author: Ahaduzzaman Khan
 */
public class WTapAssist {

    private volatile boolean pendingWRelease  = false;
    private volatile int     wReleaseTickDelay = 0;

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isWTapAssistEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (pendingWRelease && wReleaseTickDelay <= 0) {
            if (!mc.options.keyUp.isDown()) {
                Vec3 vel = player.getDeltaMovement();
                player.setDeltaMovement(vel.x * 0.6, vel.y, vel.z * 0.6);
            }
            pendingWRelease = false;
        }

        if (wReleaseTickDelay > 0) wReleaseTickDelay--;
    }

    public void onWRelease() {
        pendingWRelease   = true;
        wReleaseTickDelay = 1;
    }
}
