package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class SprintManager {
    private int sprintHoldTicks = 0;

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isSprintFixEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean forward  = mc.options.keyUp.isDown();
        boolean sprint   = mc.options.keySprint.isDown();
        boolean sneaking = player.isShiftKeyDown();
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
