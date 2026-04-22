package dev.inputbooster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * InputDrainer
 *
 * Drains queued input actions on the main thread once per tick.
 * v2.0.4 fixes:
 *  - USE_PRESSED: removed incorrect guard (was blocking right-click items)
 *  - JUMP_PRESSED: supports ladder / water climbing, not just ground
 *  - LEFT_RELEASED / RIGHT_RELEASED: handled (were silently ignored before)
 *  - BACK_RELEASED: handled
 *
 * Author: Ahaduzzaman Khan
 */
public class InputDrainer {

    public static void drainAll(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;

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

            // FIX: removed `!mc.options.useKey.isPressed()` guard — that was suppressing
            // right-click on items (bows, food, potions) when held continuously.
            case USE_PRESSED -> {
                if (mc.crosshairTarget != null) {
                    mc.interactionManager.interactItem(player, Hand.MAIN_HAND);
                }
            }

            // ── Sprint ───────────────────────────────────────────────────────
            case SPRINT_PRESSED  -> player.setSprinting(true);
            case SPRINT_RELEASED -> {
                if (!mc.options.sprintKey.isPressed()) player.setSprinting(false);
            }

            // ── Sneak ────────────────────────────────────────────────────────
            case SNEAK_PRESSED  -> player.setSneaking(true);
            case SNEAK_RELEASED -> {
                if (!mc.options.sneakKey.isPressed()) player.setSneaking(false);
            }

            // ── Jump — FIX: allow jump in water/lava/ladder, not just ground ─
            case JUMP_PRESSED -> {
                boolean canJump = player.isOnGround()
                    || player.isTouchingWater()
                    || player.isInLava()
                    || player.isClimbing();
                if (!mc.options.jumpKey.isPressed() && canJump) {
                    player.jump();
                }
            }

            // ── Movement releases ─────────────────────────────────────────────
            case FORWARD_RELEASED -> {
                if (InputBoosterMod.wTapAssist != null) {
                    try { InputBoosterMod.wTapAssist.onWRelease(); }
                    catch (Exception e) {
                        InputBoosterMod.LOGGER.warn("[InputBooster] WTapAssist error: {}", e.getMessage());
                    }
                }
            }
            // LEFT_RELEASED and RIGHT_RELEASED: no special action needed beyond
            // the polling thread having stopped sending LEFT_PRESSED/RIGHT_PRESSED.
            // These cases ensure the enum switch is exhaustive and won't warn.
            case LEFT_RELEASED  -> {}
            case RIGHT_RELEASED -> {}
            case BACK_RELEASED  -> {}

            // ── Drop / Swap ───────────────────────────────────────────────────
            case DROP_PRESSED -> {
                if (!mc.options.dropKey.isPressed()) player.dropSelectedItem(false);
            }
            case SWAP_PRESSED -> {
                if (!mc.options.swapHandsKey.isPressed()) {
                    mc.interactionManager.interactItem(player, Hand.OFF_HAND);
                }
            }

            default -> {}
        }
    }
}
