package dev.inputbooster.mixin;

import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Gui.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void onRenderTail(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {
        DebugOverlayManager.render(context);
    }
}
