package dev.inputbooster.mixin;

import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the HUD render pass.
 *
 * <p>1.21 draws the HUD straight into a {@link GuiGraphics}, so the overlay is
 * submitted at the tail of {@code Gui.render} instead of into a deferred render
 * state.
 */
@Mixin(value = Gui.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void onRenderTail(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        DebugOverlayManager.render(Minecraft.getInstance(), graphics);
    }
}
