package dev.inputbooster.mixin;

import dev.inputbooster.engine.MouseProcessor;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes mouse button and wheel events.
 *
 * <p>Same contract as {@link KeyboardHandlerMixin}: HEAD, non-cancelling, and
 * {@code require = 0} so a renamed target degrades to polling instead of
 * breaking the client. Scrolling is only counted; the mod never applies scroll
 * input.
 *
 * <p>26.2 and 26.3 expose {@code onButton(long, MouseButtonInfo, int)} and
 * {@code onScroll(long, double, double)}. Handler parameters must match the
 * target exactly, minus the trailing {@link CallbackInfo}; {@code
 * MixinTargetSignatureTest} enforces that against the resolved Minecraft jar.
 */
@Mixin(value = MouseHandler.class, priority = 1100)
public class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void onButtonObserved(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        MouseProcessor.onButton(button.button(), action);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), require = 0)
    private void onScrollObserved(long window, double xOffset, double yOffset, CallbackInfo ci) {
        MouseProcessor.onScroll(yOffset);
    }
}
