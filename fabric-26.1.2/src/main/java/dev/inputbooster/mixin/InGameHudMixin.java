package dev.inputbooster.mixin;

import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the HUD render-state extraction.
 *
 * <p>26.1 extracts the HUD through
 * {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}; 26.2 added a
 * third parameter. The handler signature has to match the target exactly, which
 * is why every supported line carries its own copy of this mixin.
 */
@Mixin(value = Gui.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onExtractRenderStateTail(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker,
                                          CallbackInfo ci) {
        DebugOverlayManager.extractRenderState(Minecraft.getInstance(), extractor);
    }
}
