package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ArrayList;

/**
 * DebugHudMixin
 *
 * Injects InputBooster status lines into the LEFT panel of the F3 debug screen.
 * This is the standard Fabric way to add mod info to F3 — no overlay, no HUD.
 *
 * Author: Ahaduzzaman Khan
 */
@Mixin(DebugHud.class)
public class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void onGetLeftText(CallbackInfoReturnable<List<String>> cir) {
        if (!InputBoosterConfig.isShowF3Info()) return;

        List<String> lines = new ArrayList<>(cir.getReturnValue());
        lines.addAll(DebugOverlayManager.getDebugLines());
        cir.setReturnValue(lines);
    }
}
