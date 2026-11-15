package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class AntiIdleManager {
    private int tickCounter = 0;
    private static final int NUDGE_INTERVAL = 200;

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isAntiIdleEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        tickCounter++;
        if (tickCounter >= NUDGE_INTERVAL) {
            tickCounter = 0;
            player.setYRot(player.getYRot() + 0.0001f);
        }
    }
}
