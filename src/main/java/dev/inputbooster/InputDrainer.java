package dev.inputbooster;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * InputDrainer
 *
 * Called once per game tick from the main thread.
 * Drains every queued input action and applies it to the game.
 *
 * Author: Ahaduzzaman Khan
 */
public class InputDrainer {

    public static void drainAll(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameMode == null) return;

        InputAction action;
        while ((action = InputActionQueue.poll()) != null) {
            apply(action, mc);
            InputBoosterMod.totalHits.incrementAndGet();
        }
    }

    private static void apply(InputAction action, Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        switch (action) {

            // ── Combat ──────────────────────────────────────────────────────
            case ATTACK_PRESSED -> {
                if (mc.hitResult != null) {
                    HitResult target = mc.hitResult;

                    switch (target.getType()) {
                        case ENTITY -> {
                            // Cast to EntityHitResult to get the entity safely
                            Entity entity = ((EntityHitResult) target).getEntity();
                            if (entity != null) {
                                mc.gameMode.attack(player, entity);
                            }
                        }
                        case BLOCK -> {
                            BlockHitResult blockHit = (BlockHitResult) target;
                            mc.gameMode.startDestroyBlock(
                                    blockHit.getBlockPos(),
                                    blockHit.getDirection()
                            );
                        }
                        default -> {}
                    }

                    player.swing(InteractionHand.MAIN_HAND);
                    if (InputBoosterMod.cpsLimiter != null) {
                        InputBoosterMod.cpsLimiter.recordClick();
                    }
                }
            }

            case USE_PRESSED -> {
                if (!mc.options.keyUse.isDown()) {
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                }
            }

            // ── Sprint ───────────────────────────────────────────────────────
            case SPRINT_PRESSED  -> player.setSprinting(true);

            case SPRINT_RELEASED -> {
                if (!mc.options.keySprint.isDown()) {
                    player.setSprinting(false);
                }
            }

            // ── Sneak ────────────────────────────────────────────────────────
            case SNEAK_PRESSED  -> player.setShiftKeyDown(true);

            case SNEAK_RELEASED -> {
                if (!mc.options.keyShift.isDown()) {
                    player.setShiftKeyDown(false);
                }
            }

            // ── Jump ─────────────────────────────────────────────────────────
            case JUMP_PRESSED -> {
                if (!mc.options.keyJump.isDown() && player.onGround()) {
                    player.jumpFromGround();
                }
            }

            // ── Drop / Swap ───────────────────────────────────────────────────
            case DROP_PRESSED -> {
                if (!mc.options.keyDrop.isDown()) {
                    player.drop(false);
                }
            }

            case SWAP_PRESSED -> {
                if (!mc.options.keySwapOffhand.isDown()) {
                    mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                }
            }

            case FORWARD_RELEASED -> {
                if (InputBoosterMod.wTapAssist != null) {
                    try {
                        InputBoosterMod.wTapAssist.onWRelease();
                    } catch (Exception e) {
                        InputBoosterMod.LOGGER.warn("[InputBooster] Error in WTapAssist: {}", e.getMessage());
                    }
                }
            }
            default -> {}
        }
    }
}
