package dev.inputbooster;

import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class InputDrainer {

    public static void drainAll(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;

        InputAction.Stamped stamped;
        while ((stamped = InputActionQueue.poll()) != null) {
            LatencyProfiler.recordDrain(stamped.capturedAt());

            if (stamped.action() == InputAction.ATTACK_PRESSED) {
                if (InputBoosterMod.cpsLimiter != null &&
                    !InputBoosterMod.cpsLimiter.allowClick()) continue;
            }

            apply(stamped.action(), mc);
            InputBoosterMod.totalHits.incrementAndGet();
        }
    }

    private static void apply(InputAction action, MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        switch (action) {

            case ATTACK_PRESSED -> {
                if (mc.crosshairTarget != null) {
                    switch (mc.crosshairTarget.getType()) {
                        case ENTITY -> {
                            if (mc.targetedEntity != null) {
                                mc.interactionManager.attackEntity(player, mc.targetedEntity);
                            }
                        }
                        case BLOCK -> {
                            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
                            mc.interactionManager.attackBlock(blockHit.getBlockPos(), blockHit.getSide());
                        }
                        default -> {}
                    }
                    player.swingHand(Hand.MAIN_HAND);
                    if (InputBoosterMod.cpsLimiter != null) {
                        InputBoosterMod.cpsLimiter.recordClick();
                    }
                }
            }

            case USE_PRESSED -> {
                if (mc.crosshairTarget != null) {
                    switch (mc.crosshairTarget.getType()) {
                        case BLOCK -> {
                            BlockHitResult blockHit = (BlockHitResult) mc.crosshairTarget;
                            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, blockHit);
                        }
                        case ENTITY -> {
                            if (mc.targetedEntity != null) {
                                EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
                                mc.interactionManager.interactEntityAtLocation(
                                    player, mc.targetedEntity, entityHit, Hand.MAIN_HAND);
                            }
                        }
                        default -> mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                    }
                }
            }

            case SPRINT_PRESSED  -> player.setSprinting(true);
            case SPRINT_RELEASED -> {
                if (!mc.options.sprintKey.isPressed()) player.setSprinting(false);
            }

            case SNEAK_PRESSED  -> McCompat.setSneaking(player, true);
            case SNEAK_RELEASED -> {
                if (!mc.options.sneakKey.isPressed()) McCompat.setSneaking(player, false);
            }

            case JUMP_PRESSED -> {
                boolean canJump = player.isOnGround()
                    || McCompat.isInWater(player)
                    || player.isInLava()
                    || McCompat.isClimbing(player);
                if (!mc.options.jumpKey.isPressed() && canJump) {
                    player.jump();
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

            case DROP_PRESSED -> player.dropSelectedItem(false);

            case SWAP_PRESSED -> {
                if (!mc.options.swapHandsKey.isPressed()) {
                    mc.interactionManager.interactItem(player, Hand.OFF_HAND);
                }
            }

            case PICK_BLOCK_PRESSED -> {
                if (mc.crosshairTarget != null &&
                    mc.crosshairTarget.getType() == HitResult.Type.BLOCK &&
                    mc.world != null) {
                    ((MinecraftClientAccessor) mc).invokeDoItemPick();
                }
            }

            default -> {}
        }
    }
}
