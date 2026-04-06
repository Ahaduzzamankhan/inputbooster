package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * SprintManager
 * Author: Ahaduzzaman Khan
 */
public class SprintManager {

    private int sprintHoldTicks = 0;

    public void tick(Minecraft mc) {
        if (!InputBoosterConfig.isSprintFixEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean forward  = mc.options.keyUp.isDown();
        boolean sprint   = mc.options.keySprint.isDown();
        boolean sneaking = player.isShiftKeyDown();
        float   hunger   = player.getFoodData().getFoodLevel();

        if (InputBoosterConfig.isAutoSprintEnabled()) {
            if (forward && !sneaking && hunger > 6.0f) {
                player.setSprinting(true);
                sprintHoldTicks = 0;
                return;
            }
        }

        if (sprint && forward && !sneaking && hunger > 6.0f) {
            if (!player.isSprinting()) {
                player.setSprinting(true);
            }
        }

        if (!forward) {
            sprintHoldTicks = 0;
        } else {
            sprintHoldTicks++;
        }
    }
}
