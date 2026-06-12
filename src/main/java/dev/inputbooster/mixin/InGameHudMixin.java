package dev.inputbooster.mixin;

import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into InGameHud.render() at TAIL so InputBooster draws its panel
 * after all vanilla HUD elements are done.
 *
 * 1.21.1 uses render(DrawContext, float tickDelta) — no RenderTickCounter.
 * RenderTickCounter was introduced in 1.21.2+.
 *
 * require=0 → game still boots if the signature changes in a future patch.
 */
@Mixin(value = InGameHud.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void onRenderTail(DrawContext context, float tickDelta, CallbackInfo ci) {
        DebugOverlayManager.render(context);
    }
}
