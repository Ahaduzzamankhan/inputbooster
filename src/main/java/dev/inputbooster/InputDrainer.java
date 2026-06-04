package dev.inputbooster;

import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.feature.InputClickSoundManager;
import dev.inputbooster.mixin.MinecraftClientAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

public class InputDrainer {

    /**
     * FIX: Track whether the mod handled an ATTACK_PRESSED this tick.
     * GameTickMixin reads this flag to suppress vanilla's own attack handling,
     * preventing the double-hit bug where one click triggers two attacks.
     */
    public static volatile boolean attackHandledThisTick = false;

    /**
     * FIX: Track whether the mod handled a USE_PRESSED this tick.
     * Same suppression pattern applied to right-click / use actions.
     */
    public static volatile boolean useHandledThisTick = false;

    public static void drainAll(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameMode == null) return;
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) {
            attackHandledThisTick = false;
            useHandledThisTick = false;
            InputActionQueue.clear();
            return;
        }

        // Reset per-tick flags before draining
        attackHandledThisTick = false;
        useHandledThisTick    = false;

        InputAction.Stamped stamped;
        while ((stamped = InputActionQueue.poll()) != null) {
            LatencyProfiler.recordDrain(stamped.capturedAt());

            if (stamped.action() == InputAction.ATTACK_PRESSED) {
                if (InputBoosterMod.cpsLimiter != null &&
                    !InputBoosterMod.cpsLimiter.allowClick()) {
                    if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Attack blocked by CPS mode");
                    attackHandledThisTick = true; // CRITICAL: Suppress vanilla attack for this blocked click!
                    continue;
                }
            }

            apply(stamped.action(), mc);
            InputClickSoundManager.playFor(stamped.action(), mc);
            if (stamped.action() == InputAction.ATTACK_PRESSED) {
                InputBoosterMod.totalHits.incrementAndGet();
            }
            // Record CPS for every accepted attack, regardless of target type
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
                            // Entity hits are one-shot events: fire from the drainer and
                            // suppress vanilla's doAttack() so it doesn't hit a second time.
                            if (mc.crosshairEntity != null) {
                                mc.gameMode.attack(player, mc.crosshairEntity);
                                player.swing(InteractionHand.MAIN_HAND);
                                if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Entity attack fired");
                                // Signal vanilla's doAttack() to back off — we already fired.
                                attackHandledThisTick = true;
                            }
                        }
                        case BLOCK -> {
                            // Block breaking is a CONTINUOUS held-key mechanic. Vanilla's
                            // handleBlockBreaking() already calls attackBlock() every tick
                            // the button is held. If we also call attackBlock() here we get
                            // TWO calls per tick — exactly the double-break bug the user sees.
                            //
                            // Fix: do NOT call attackBlock() from the drainer. Do NOT set
                            // attackHandledThisTick so vanilla's doAttack() / handleBlockBreaking()
                            // runs its normal single-call loop unobstructed.
                        }
                        default -> {}
                    }
                }
            }

            case USE_PRESSED -> {
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                useHandledThisTick = true;
            }

            case SPRINT_PRESSED  -> player.setSprinting(true);
            case SPRINT_RELEASED -> {
                if (!mc.options.keySprint.isPressed()) player.setSprinting(false);
            }

            case SNEAK_PRESSED  -> McCompat.setSneaking(player, true);
            case SNEAK_RELEASED -> {
                if (!mc.options.keyShift.isPressed()) McCompat.setSneaking(player, false);
            }

            case JUMP_PRESSED -> {
                boolean canJump = player.isOnGround()
                    || McCompat.isInWater(player)
                    || player.isInLava()
                    || McCompat.isClimbing(player);
                if (!mc.options.keyJump.isPressed() && canJump) {
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
                if (!mc.options.keySwapOffhand.isPressed()) {
                    mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                }
            }

            case PICK_BLOCK_PRESSED -> {
                if (mc.hitResult != null &&
                    mc.hitResult.getType() == HitResult.Type.BLOCK &&
                    mc.level != null) {
                    ((MinecraftClientAccessor) mc).invokeDoItemPick();
                }
            }

            default -> {}
        }
    }
}
