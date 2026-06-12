package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public class WTapAssist {
    private long releaseOnTick = -1;
    private long currentTick   = 0;

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isWTapAssistEnabled()) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) { currentTick++; return; }

        if (releaseOnTick >= 0 && currentTick == releaseOnTick + 1) {
            if (!mc.options.forwardKey.isPressed()) {
                Vec3d vel = player.getVelocity();
                player.setVelocity(vel.x * 0.6, vel.y, vel.z * 0.6);
            }
            releaseOnTick = -1;
        }
        currentTick++;
    }

    public void onWRelease() {
        releaseOnTick = currentTick;
    }
}
