package dev.inputbooster.mixin;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.feature.DebugOverlayManager;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = DebugHud.class, priority = 900)
public class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true, require = 0)
    private void onGetLeftText(CallbackInfoReturnable<List<String>> cir) {
        if (!InputBoosterConfig.isShowF3Info()) return;
        if (cir.getReturnValue() == null) return;

        List<String> lines = new ArrayList<>(cir.getReturnValue());
        lines.add("");
        lines.addAll(DebugOverlayManager.getDebugLines());
        cir.setReturnValue(lines);
    }
}
