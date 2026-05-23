package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.InputDrainer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class GameTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        InputDrainer.drainAll(mc);
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true, require = 0)
    private void onDoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (!InputBoosterMod.active || !InputBoosterMod.initialized.get()) return;
        if (InputDrainer.attackHandledThisTick) {
            InputDrainer.attackHandledThisTick = false;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true, require = 0)
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
