package dev.inputbooster.mixin;

import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into InGameHud.render() at TAIL so InputBooster draws its panel
 * after all vanilla HUD elements are done.
 *
 * Uses zero Fabric API rendering classes — no HudRenderCallback,
 * no HudElementRegistry, no version-specific package paths.
 * Works on any MC 1.21.x regardless of Fabric API version.
 *
 * require=0 → game still boots if the signature changes in a future patch.
 */
@Mixin(value = InGameHud.class, priority = 900)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void onRenderTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        DebugOverlayManager.render(context);
    }
}
