package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class AntiIdleManager {
    private int tickCounter = 0;
    private static final int NUDGE_INTERVAL = 200;

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isAntiIdleEnabled()) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        tickCounter++;
        if (tickCounter >= NUDGE_INTERVAL) {
            tickCounter = 0;
            player.setYaw(player.getYaw() + 0.0001f);
        }
    }
}
