package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.InputDrainer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GameTickMixin
 *
 * Hooks into MinecraftClient.tick() HEAD to drain the InputActionQueue
 * before any vanilla input handling occurs, ensuring recovered inputs
 * fire at the very start of each tick.
 *
 * Author: Ahaduzzaman Khan
 */
@Mixin(MinecraftClient.class)
public class GameTickMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        InputDrainer.drainAll(mc);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        InputBoosterMod.shutdown();
    }
}
