package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.InputDrainer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class GameTickMixin {

    /**
     * Drain our queued inputs at the very start of each tick.
     * Sets attackHandledThisTick / useHandledThisTick flags so that
     * doAttackMixin and doItemUseMixin below can suppress vanilla duplicates.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        InputDrainer.drainAll(mc);
    }

    /**
     * FIX — Double-click bug (attack side):
     * doAttack() is the exact internal method MC calls when the attack button
     * is held. Injecting HERE (not into the now-removed handleInputEvents) is
     * the correct 1.21.x hook point.
     *
     * If InputDrainer already fired an attack this tick we cancel doAttack()
     * so vanilla cannot fire a second hit on the same physical click.
     *
     * require=0 → if the method name ever changes the game still loads;
     * the fix simply won't apply rather than crashing.
     */
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) return;
        if (InputDrainer.attackHandledThisTick) {
            InputDrainer.attackHandledThisTick = false; // reset so next tick works normally
            cir.setReturnValue(false);
        }
    }

    /**
     * FIX — Double-click bug (use/right-click side):
     * Same pattern applied to item/block use.
     */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void onDoItemUse(CallbackInfo ci) {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) return;
        if (InputDrainer.useHandledThisTick) {
            InputDrainer.useHandledThisTick = false;
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        InputBoosterMod.shutdown();
    }
}
