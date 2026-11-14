package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.InputDrainer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Core client hooks.
 *
 * {@code require = 1} on the tick/attack/use/close injections: these are the
 * mod's critical path. A mapping change must fail loudly instead of silently
 * disabling input handling (which is exactly what {@code require = 0} did for
 * {@code startAttack} / {@code startUseItem} on 26.x).
 */
@Mixin(Minecraft.class)
public class GameTickMixin {

    /**
     * Drain our queued inputs at the very start of each tick and expire any
     * suppression tokens issued for earlier ticks.
     */
    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void onTickHead(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InputDrainer.drainAll(mc);
    }

    /**
     * Double-click guard: if the mod already fired an attack this tick, vanilla
     * must not fire a second one for the same physical click. The token is
     * single-use and tick-scoped, so a missed or reordered call can never
     * suppress a legitimate attack on a later tick.
     */
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true, require = 1)
    private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) return;
        if (InputDrainer.consumeAttackSuppression()) {
            cir.setReturnValue(false);
        }
    }

    /** Same pattern for right-click / item use. */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void onStartUseItem(CallbackInfo ci) {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) return;
        if (InputDrainer.consumeUseSuppression()) {
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"), require = 1)
    private void onClose(CallbackInfo ci) {
        InputBoosterMod.shutdown();
    }
}