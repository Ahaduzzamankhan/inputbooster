package dev.inputbooster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * InputDrainer
 *
 * Called once per game tick from the main thread.
 * Drains every queued input action and applies it to the game.
 *
 * Author: Ahaduzzaman Khan
 */
public class InputDrainer {

    public static void drainAll(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null) return;

        InputAction action;
        while ((action = InputActionQueue.poll()) != null) {
            apply(action, mc);
            InputBoosterMod.totalHits.incrementAndGet();
        }
    }

    private static void apply(InputAction action, MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        switch (action) {

            // ── Combat ──────────────────────────────────────────────────────
            case ATTACK_PRESSED -> {
                if (mc.crosshairTarget != null) {
                    HitResult target = mc.crosshairTarget;

                    switch (target.getType()) {
                        case ENTITY -> {
                            if (mc.targetedEntity != null) {
                                mc.interactionManager.attackEntity(player, mc.targetedEntity);
                            }
                        }
                        case BLOCK -> {
                            BlockHitResult blockHit = (BlockHitResult) target;
                            mc.interactionManager.attackBlock(
                                    blockHit.getBlockPos(),
                                    blockHit.getSide()
                            );
                        }
                        default -> {}
                    }

                    player.swingHand(Hand.MAIN_HAND);
                }
            }

            case USE_PRESSED -> {
                if (!mc.options.useKey.isPressed()) {
                    mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                }
            }

            // ── Sprint ───────────────────────────────────────────────────────
            case SPRINT_PRESSED -> player.setSprinting(true);

            case SPRINT_RELEASED -> {
                if (!mc.options.sprintKey.isPressed()) {
                    player.setSprinting(false);
                }
            }

            // ── Sneak ────────────────────────────────────────────────────────
            case SNEAK_PRESSED -> player.setSneaking(true);

            case SNEAK_RELEASED -> {
                if (!mc.options.sneakKey.isPressed()) {
                    player.setSneaking(false);
                }
            }

            // ── Jump ─────────────────────────────────────────────────────────
            case JUMP_PRESSED -> {
                if (!mc.options.jumpKey.isPressed() && player.isOnGround()) {
                    player.jump();
                }
            }

            // ── Drop / Swap ───────────────────────────────────────────────────
            case DROP_PRESSED -> {
                if (!mc.options.dropKey.isPressed()) {
                    player.dropSelectedItem(false);
                }
            }

            case SWAP_PRESSED -> {
                if (!mc.options.swapHandsKey.isPressed()) {
                    mc.interactionManager.interactItem(player, Hand.OFF_HAND);
                }
            }

            // Movement releases handled elsewhere
            case FORWARD_RELEASED -> {
                // Notify WTapAssist so it can apply velocity dampen for clean knockback
                if (InputBoosterMod.wTapAssist != null) {
                    InputBoosterMod.wTapAssist.onWRelease();
                }
            }
            default -> {}
        }
    }
}