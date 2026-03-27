package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * SprintManager
 *
 * At low FPS, Minecraft can fail to maintain sprint state because the
 * key-press check happens infrequently. This feature re-asserts sprint
 * every tick when the forward key is held and conditions are met.
 *
 * Also handles auto-sprint: if enabled, holding W automatically sprints
 * without needing to double-tap or hold Ctrl.
 *
 * Author: Ahaduzzaman Khan
 */
public class SprintManager {

    private int sprintHoldTicks = 0;

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isSprintFixEnabled()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        boolean forward = mc.options.forwardKey.isPressed();
        boolean sprint  = mc.options.sprintKey.isPressed();
        boolean sneaking = player.isSneaking();
        boolean onGround = player.isOnGround();
        float hunger = player.getHungerManager().getFoodLevel();

        // Auto-sprint: moving forward + not sneaking + enough food → sprint
        if (InputBoosterConfig.isAutoSprintEnabled()) {
            if (forward && !sneaking && hunger > 6.0f) {
                player.setSprinting(true);
                sprintHoldTicks = 0;
                return;
            }
        }

        // Sprint-fix: if sprint key held but player lost sprint state, restore it
        if (sprint && forward && !sneaking && hunger > 6.0f) {
            if (!player.isSprinting()) {
                player.setSprinting(true);
            }
        }

        // Reset sprint cleanly when forward released
        if (!forward) {
            sprintHoldTicks = 0;
        } else {
            sprintHoldTicks++;
        }
    }
}
