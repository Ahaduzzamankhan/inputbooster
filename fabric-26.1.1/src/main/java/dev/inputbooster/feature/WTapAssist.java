package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class WTapAssist {
    private long releaseOnTick = -1;
    private long currentTick   = 0;

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isWTapAssistEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null) { currentTick++; return; }

        if (releaseOnTick >= 0 && currentTick == releaseOnTick + 1) {
            if (!mc.options.keyUp.isDown()) {
                Vec3 vel = player.getDeltaMovement();
                player.setDeltaMovement(vel.x * 0.6, vel.y, vel.z * 0.6);
            }
            releaseOnTick = -1;
        }
        currentTick++;
    }

    public void onWRelease() {
        releaseOnTick = currentTick;
    }
}
