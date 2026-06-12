package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.McCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public class SprintManager {
    private int sprintHoldTicks = 0;

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isSprintFixEnabled()) return;
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        boolean forward  = mc.options.forwardKey.isPressed();
        boolean sprint   = mc.options.sprintKey.isPressed();
        boolean sneaking = player.isSneaking();
        int foodLevel    = McCompat.getFoodLevel(player);

        if (InputBoosterConfig.isAutoSprintEnabled()) {
            if (forward && !sneaking && foodLevel > 6) {
                player.setSprinting(true);
                sprintHoldTicks = 0;
                return;
            }
        }

        if (sprint && forward && !sneaking && foodLevel > 6) {
            if (!player.isSprinting()) player.setSprinting(true);
        }

        if (!forward) sprintHoldTicks = 0;
        else sprintHoldTicks++;
    }
}
