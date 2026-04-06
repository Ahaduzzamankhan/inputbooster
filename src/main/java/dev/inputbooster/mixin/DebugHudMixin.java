package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ArrayList;

/**
 * DebugHudMixin
 *
 * Injects InputBooster status lines into the F3 debug screen left panel.
 * We inject into both possible method names with require=0 so it works
 * regardless of which one MC 26.1.x actually uses.
 *
 * getGameInformation() = left panel in most MC versions
 * getSystemInformation() = right panel in most MC versions
 *
 * Author: Ahaduzzaman Khan
 */
@Mixin(value = DebugScreenOverlay.class, priority = 900)
public class DebugHudMixin {

    // Try left panel - require=0 means silently skip if method not found
    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetGameInformation(CallbackInfoReturnable<List<String>> cir) {
        appendLines(cir);
    }

    // Also try right panel as fallback - require=0 means silently skip if method not found
    @Inject(method = "getSystemInformation", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        appendLines(cir);
    }

    private static void appendLines(CallbackInfoReturnable<List<String>> cir) {
        if (!InputBoosterConfig.isShowF3Info()) return;
        if (cir.getReturnValue() == null) return;
        List<String> lines = new ArrayList<>(cir.getReturnValue());
        lines.addAll(DebugOverlayManager.getDebugLines());
        cir.setReturnValue(lines);
    }
}
