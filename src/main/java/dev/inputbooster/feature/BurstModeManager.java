package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class BurstModeManager {

    private static final double DROP_THRESHOLD = 0.20;
    private static final int    BURST_TICKS    = 60;

    private int     lastFps        = 0;
    private int     burstTicksLeft = 0;
    private boolean bursting       = false;
    private boolean endMsgSent     = false;

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isBurstModeEnabled()) {
            bursting = false;
            return;
        }

        int fps = InputBoosterMod.currentFps;

        if (lastFps > 10 && fps > 0) {
            double drop = (double)(lastFps - fps) / lastFps;
            if (drop > DROP_THRESHOLD && !bursting) {
                bursting = true;
                burstTicksLeft = BURST_TICKS;
                endMsgSent = false;
                InputBoosterMod.LOGGER.info(
                    "[BurstMode] FPS dropped from {} to {} ({}%), bursting to 1000 Hz for 3s",
                    lastFps, fps, String.format("%.0f", drop * 100));
                if (mc.player != null && InputBoosterConfig.isShowActionBar()) {
                    mc.player.sendSystemMessage(
                        Component.literal("§c⚡ InputBooster Burst Mode ACTIVE (1000 Hz)"));
                }
            }
        }

        if (bursting) {
            burstTicksLeft--;
            if (burstTicksLeft <= 0) {
                bursting = false;
                InputBoosterMod.LOGGER.info("[BurstMode] Burst mode ended, returning to normal rate.");
                if (!endMsgSent && mc.player != null && InputBoosterConfig.isShowActionBar()) {
                    mc.player.sendSystemMessage(
                        Component.literal("§a⚡ InputBooster Burst Mode ended"));
                    endMsgSent = true;
                }
            }
        }

        if (fps > 0) lastFps = fps;
    }

    public boolean isBursting() {
        return bursting && InputBoosterConfig.isBurstModeEnabled();
    }

    public int getBurstTicksLeft() { return burstTicksLeft; }
}
