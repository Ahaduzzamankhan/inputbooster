package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * AntiIdleManager
 *
 * At very low FPS (< 15), the game can stutter for 1-2 seconds causing
 * some anti-AFK systems to flag the player as idle.
 * This feature sends a tiny sub-threshold yaw nudge every N ticks
 * to keep the server connection alive without moving the player visibly.
 *
 * Author: Ahaduzzaman Khan
 */
public class AntiIdleManager {

    private int tickCounter = 0;
    private static final int NUDGE_INTERVAL = 200; // every ~10 seconds at 20 TPS

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isAntiIdleEnabled()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        tickCounter++;
        if (tickCounter >= NUDGE_INTERVAL) {
            tickCounter = 0;
            // Micro-rotate yaw by 0.0001 degrees — imperceptible, keeps connection alive
            float currentYaw = player.getYaw();
            player.setYaw(currentYaw + 0.0001f);
        }
    }
}
