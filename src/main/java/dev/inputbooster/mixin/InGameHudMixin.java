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
 *
 * <p>{@code require = 1}: this is the only place the corner HUD is drawn. With
 * {@code require = 0} a signature change made the injection disappear at
 * runtime and the badge silently stopped rendering, which is exactly the
 * failure the previous releases shipped.
 */
@Mixin(value = Gui.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 1)
    private void onExtractRenderStateTail(DeltaTracker deltaTracker, boolean renderLevel, boolean renderHotbar, CallbackInfo ci) {
        DebugOverlayManager.extractRenderState(Minecraft.getInstance(), deltaTracker);
    }
}