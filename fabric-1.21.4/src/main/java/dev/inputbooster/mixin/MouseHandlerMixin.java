package dev.inputbooster.mixin;

import dev.inputbooster.engine.MouseProcessor;
import net.minecraft.client.MouseHandler;
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
 * <p>1.21.x exposes {@code onPress(long, int, int, int)} and
 * {@code onScroll(long, double, double)}. Handler parameters must match the
 * target exactly, minus the trailing {@link CallbackInfo}; {@code
 * MixinTargetSignatureTest} enforces that against the resolved Minecraft jar.
 */
@Mixin(value = MouseHandler.class, priority = 1100)
public class MouseHandlerMixin {

    @Inject(method = "onPress", at = @At("HEAD"), require = 0)
    private void onButtonObserved(long window, int button, int action, int modifiers, CallbackInfo ci) {
        MouseProcessor.onButton(button, action);
    }

    @Inject(method = "onScroll", at = @At("HEAD"), require = 0)
    private void onScrollObserved(long window, double xOffset, double yOffset, CallbackInfo ci) {
        MouseProcessor.onScroll(yOffset);
    }
}
