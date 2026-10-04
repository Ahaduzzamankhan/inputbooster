package dev.inputbooster;

import dev.inputbooster.compat.McVersion;
import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.feature.InputClickSoundManager;
import dev.inputbooster.mixin.MixinAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.atomic.AtomicInteger;

public class InputDrainer {

    /**
     * Duplicate-attack suppression is tick-scoped.
     *
     * Previously two plain booleans were set by the drainer and cleared inside
     * the injected vanilla methods. That is fragile: if vanilla stops calling
     * the method (changed input order, a click handled elsewhere, a paused
     * game) the stale flag survived and silently cancelled a legitimate attack
     * on a later tick. Each token now carries the tick id it was issued in and
     * is discarded automatically when that tick ends, so suppression can never
     * leak across ticks.
     */
    private static final AtomicInteger TICK = new AtomicInteger(1);
    private static volatile int attackHandledTick = -1;
    private static volatile int useHandledTick = -1;

    private static volatile boolean PICK_BLOCK_MIXIN_WARNED = false;

    /** Called once per client tick, before draining. */
    public static void beginTick() {
        int tick = TICK.incrementAndGet();
        // Expire tokens from earlier ticks: vanilla never gets to consume them.
        if (attackHandledTick != tick) attackHandledTick = -1;
        if (useHandledTick != tick) useHandledTick = -1;
    }

    /** Records that the mod executed an attack for the current tick. */
    public static void markAttackHandled() {
        attackHandledTick = TICK.get();
    }

    /** Records that the mod executed a use action for the current tick. */
    public static void markUseHandled() {
        useHandledTick = TICK.get();
    }

    /**
     * @return true (once) if vanilla's own attack must be suppressed for this tick
     */
    public static boolean consumeAttackSuppression() {
        if (attackHandledTick == TICK.get()) {
            attackHandledTick = -1;
            return true;
        }
        return false;
    }

    /**
     * @return true (once) if vanilla's own use action must be suppressed
     */
    public static boolean consumeUseSuppression() {
        if (useHandledTick == TICK.get()) {
            useHandledTick = -1;
            return true;
        }
        return false;
    }

    /** Test/diagnostic helper: current tick id. */
    public static int currentTickId() {
        return TICK.get();
    }

    public static void drainAll(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameMode == null) {
            // Drop anything still queued and expire suppression tokens so they
            // can never cancel a vanilla action in a later session.
            beginTick();
            InputActionQueue.clear();
            return;
        }
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) {
            beginTick();
            InputActionQueue.clear();
            return;
        }

        beginTick();

        InputAction.Stamped stamped;
        while ((stamped = InputActionQueue.poll()) != null) {
            LatencyProfiler.recordDrain(stamped.capturedAt());

            if (stamped.action() == InputAction.ATTACK_PRESSED) {
                if (InputBoosterMod.cpsLimiter != null &&
                    !InputBoosterMod.cpsLimiter.allowClick()) {
                    if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Attack blocked by CPS mode");
                    markAttackHandled(); // suppress vanilla's attack for this blocked click
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
                            if (((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity() != null) {
                                mc.gameMode.attack(player, ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity());
                                // 26.x requires an explicit swing animation component on 26.3 and a plain
                                // hand on 26.2; the compat shim hides the difference.
                                McVersion.swingArm(player);
                                if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Entity attack fired");
                                // Signal vanilla's attack handling to back off — we already fired.
                                markAttackHandled();
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
                markUseHandled();
            }

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

            case DROP_PRESSED -> McVersion.dropHeldItem(player);

            case SWAP_PRESSED -> {
                if (!mc.options.keySwapOffhand.isDown()) {
                    mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                }
            }

            case PICK_BLOCK_PRESSED -> {
                if (mc.hitResult != null &&
                    mc.hitResult.getType() == HitResult.Type.BLOCK &&
                    mc.level != null) {
                    // 26.x exposes KeyMapping.click() as a static helper that takes
                    // an InputConstants.Key; the vanilla action is still the
                    // authoritative implementation, so invoke it directly.
                    // Guard the cast: if the accessor mixin did not apply this
                    // would otherwise be a ClassCastException on a key press.
                    if (MixinAccess.pickBlockOrEntity(mc)) {
                        // vanilla pick-block ran
                    } else if (!PICK_BLOCK_MIXIN_WARNED) {
                        PICK_BLOCK_MIXIN_WARNED = true;
                        InputBoosterMod.LOGGER.warn(
                            "[Mixin] pick-block hook unavailable; pick-block input is disabled.");
                    }
                }
            }

            default -> {}
        }
    }
}
