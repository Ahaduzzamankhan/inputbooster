package dev.inputbooster.mixin;

import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the HUD render-state extraction. The signature changed in 26.x:
 * {@code extractRenderState(DeltaTracker, boolean, boolean)} replaced the
 * graphics-extractor overload the mod used on 26.1.
 */
@Mixin(value = Gui.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onExtractRenderStateTail(DeltaTracker deltaTracker, boolean renderLevel, boolean renderHotbar, CallbackInfo ci) {
        DebugOverlayManager.extractRenderState(Minecraft.getInstance());
    }
}