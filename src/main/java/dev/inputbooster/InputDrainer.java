package dev.inputbooster;

import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.mixin.MinecraftClientAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

public class InputDrainer {

    public static volatile boolean attackHandledThisTick = false;
    public static volatile boolean useHandledThisTick = false;

    public static void drainAll(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameMode == null) return;
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) {
            attackHandledThisTick = false;
            useHandledThisTick = false;
            InputActionQueue.clear();
            return;
        }

        attackHandledThisTick = false;
        useHandledThisTick    = false;

        InputAction.Stamped stamped;
        while ((stamped = InputActionQueue.poll()) != null) {
            LatencyProfiler.recordDrain(stamped.capturedAt());

            if (stamped.action() == InputAction.ATTACK_PRESSED) {
                if (InputBoosterMod.cpsLimiter != null &&
                    !InputBoosterMod.cpsLimiter.allowClick()) {
                    if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Attack blocked by CPS mode");
                    continue;
                }
            }

            apply(stamped.action(), mc);
            if (stamped.action() == InputAction.ATTACK_PRESSED) {
                InputBoosterMod.totalHits.incrementAndGet();
            }
            if (stamped.action() == InputAction.ATTACK_PRESSED &&
                InputBoosterMod.cpsLimiter != null) {
                InputBoosterMod.cpsLimiter.recordClick();
            }
        }
    }

    private static void apply(InputAction action, Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        switch (action) {

            case ATTACK_PRESSED -> {
                if (mc.hitResult != null) {
                    switch (mc.hitResult.getType()) {
                        case ENTITY -> {
                            if (mc.crosshairPickEntity != null) {
                                mc.gameMode.attack(player, mc.crosshairPickEntity);
                                player.swing(InteractionHand.MAIN_HAND);
                                if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Entity attack fired");
                                attackHandledThisTick = true;
                            }
                        }
                        case BLOCK -> {}
                        default -> {}
                    }
                }
            }

            case USE_PRESSED -> {}

            case SPRINT_PRESSED  -> player.setSprinting(true);
            case SPRINT_RELEASED -> {
                if (!mc.options.keySprint.isDown()) player.setSprinting(false);
            }

            case SNEAK_PRESSED  -> McCompat.setSneaking(player, true);
            case SNEAK_RELEASED -> {
                if (!mc.options.keyShift.isDown()) McCompat.setSneaking(player, false);
            }

            case JUMP_PRESSED -> {
                boolean canJump = player.onGround()
                    || McCompat.isInWater(player)
                    || player.isInLava()
                    || McCompat.isClimbing(player);
                if (!mc.options.keyJump.isDown() && canJump) {
                    player.jumpFromGround();
                }
            }

            case FORWARD_RELEASED -> {
                if (InputBoosterMod.wTapAssist != null) {
                    try { InputBoosterMod.wTapAssist.onWRelease(); }
                    catch (Exception e) {
                        InputBoosterMod.LOGGER.warn("[InputBooster] WTapAssist error: {}", e.getMessage());
                    }
                }
            }
            case LEFT_RELEASED  -> {}
            case RIGHT_RELEASED -> {}
            case BACK_RELEASED  -> {}

            case DROP_PRESSED -> player.drop(false);

            case SWAP_PRESSED -> {
                if (!mc.options.keySwapOffhand.isDown()) {
                    mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                }
            }

            case PICK_BLOCK_PRESSED -> {
                if (mc.hitResult != null &&
                    mc.hitResult.getType() == HitResult.Type.BLOCK &&
                    mc.level != null) {
                    ((MinecraftClientAccessor) mc).invokePickBlock();
                }
            }

            default -> {}
        }
    }
}
