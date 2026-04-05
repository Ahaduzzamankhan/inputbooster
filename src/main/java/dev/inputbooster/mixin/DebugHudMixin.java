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
 *
 * FIX (v2.0.4): Added require=0 so a failed injection no longer crashes the game.
 * The method name changed between MC versions; require=0 makes it silently skip
 * if the target is not found instead of throwing InvalidInjectionException.
 *
 * Author: Ahaduzzaman Khan
 */
@Mixin(value = DebugHud.class, priority = 900)
public class DebugHudMixin {

    // require=0: if this target method doesn't exist in this MC version, skip silently.
    // This was the ROOT CAUSE of "Not Responding" — a failed mixin with require=1 (default)
    // crashes the render thread on startup, leaving a blank white window.
    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetLeftText(CallbackInfoReturnable<List<String>> cir) {
        if (!InputBoosterConfig.isShowF3Info()) return;
        if (cir.getReturnValue() == null) return;

        List<String> lines = new ArrayList<>(cir.getReturnValue());
        lines.addAll(DebugOverlayManager.getDebugLines());
        cir.setReturnValue(lines);
    }
}
