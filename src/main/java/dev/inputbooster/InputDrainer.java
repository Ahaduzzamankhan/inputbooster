package dev.inputbooster;

import dev.inputbooster.compat.McVersion;
import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.feature.InputClickSoundManager;
import dev.inputbooster.access.MixinAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.atomic.AtomicInteger;

public class InputDrainer {

    /**
     * Duplicate-attack suppression is tick-scoped.
     *
     * Each token carries the tick id it was issued in and is discarded when
     * that tick ends, so suppression can never leak into a later tick.
     */
    private static final AtomicInteger TICK = new AtomicInteger(1);
    private static volatile int attackHandledTick = -1;
    private static volatile int useHandledTick = -1;

    private static volatile boolean PICK_BLOCK_MIXIN_WARNED = false;

    /** Called once per client tick, before draining. */
    public static void beginTick() {
        int tick = TICK.incrementAndGet();
        // Expire tokens from earlier ticks: vanilla never got to consume them.
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
        if (InputBoosterMod.moduleManager != null && !InputBoosterMod.moduleManager.enabled("input")) {
            // Safe Mode pulled the execution layer after repeated failures.
            beginTick();
            InputActionQueue.clear();
            return;
        }

        beginTick();

        // A key pressed while a screen is open belongs to that screen. Vanilla
        // stops feeding those events to the player, so the mod must stop too:
        // otherwise the space typed in chat comes out as a jump. The router is
        // fed by the game's own show-screen call, which is more reliable than
        // asking the HUD at tick time.
        if (dev.inputbooster.input.KeyboardRouter.isScreenOpen()
            || (mc.gui != null && mc.gui.screen() != null)) {
            InputActionQueue.clear();
            return;
        }

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

            try {
                apply(stamped.action(), mc);
            } catch (Throwable t) {
                // One bad action must not abort the tick or take the client
                // down with it. Report it, then keep the rest of the queue
                // moving so input does not stall behind a single failure.
                InputBoosterMod.LOGGER.warn("[InputBooster] {} failed: {}",
                    stamped.action(), t.toString());
                if (InputBoosterMod.safeMode != null) {
                    InputBoosterMod.safeMode.recordError("input", t);
                }
                continue;
            }

            // Replayed events are not the player's input: they must not tick
            // the session hit counter, the CPS graph or the click sound.
            if (!stamped.isReplay()) {
                InputClickSoundManager.playFor(stamped.action(), mc);
                if (stamped.action() == InputAction.ATTACK_PRESSED) {
                    InputBoosterMod.totalHits.incrementAndGet();
                    if (InputBoosterMod.cpsLimiter != null) {
                        InputBoosterMod.cpsLimiter.recordClick();
                    }
                }
            }
        }
    }

    private static void apply(InputAction action, Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        switch (action) {

            case ATTACK_PRESSED -> {
                HitResult hit = mc.hitResult;
                if (hit != null) {
                    switch (hit.getType()) {
                        case ENTITY -> {
                            if (hit instanceof EntityHitResult entityHit) {
                                Entity target = entityHit.getEntity();
                                // The hit result is one frame old at tick start.
                                // Only fire at a target that is still alive and
                                // within vanilla melee reach.
                                if (target != null && target.isAlive()
                                    && player.distanceToSqr(target) <= 36.0D) {
                                    mc.gameMode.attack(player, target);
                                    // 26.x requires an explicit swing animation component on 26.3
                                    // and a plain hand on 26.2; the compat shim hides the difference.
                                    McVersion.swingArm(player);
                                    if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Entity attack fired");
                                    // Signal vanilla's attack handling to back off: we already fired.
                                    markAttackHandled();
                                }
                            }
                        }
                        case BLOCK -> {
                            // Block breaking is a CONTINUOUS held-key mechanic. Vanilla's
                            // handleBlockBreaking() already calls attackBlock() every tick
                            // the button is held. Calling attackBlock() here as well would
                            // break two blocks per tick, so this case stays empty on purpose.
                        }
                        default -> {}
                    }
                }
            }

            case USE_PRESSED -> {
                // Vanilla owns block and entity interaction: opening a chest,
                // flipping a lever, trading, mounting. Only the "right-click at
                // nothing" case is executed here. For anything under the
                // crosshair the event is left to startUseItem(), so the mod no
                // longer turns every right-click into a held-item use.
                HitResult hit = mc.hitResult;
                if (hit == null || hit.getType() == HitResult.Type.MISS) {
                    mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                    markUseHandled();
                }
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
                // The old guard required the jump key to be *up*, which made
                // this branch unreachable: the event only exists because the
                // key went down. Jump a tick earlier than vanilla when the
                // player can actually leave the ground; vanilla then sees
                // onGround() == false and does not jump a second time.
                boolean canJump = player.onGround()
                    || McCompat.isInWater(player)
                    || player.isInLava()
                    || McCompat.isClimbing(player);
                if (canJump) {
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
                // The offhand swap belongs to vanilla's own keybind path. This
                // used to call useItem(OFF_HAND), so pressing the swap key ate
                // food or placed a block instead of swapping the shield in.
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
