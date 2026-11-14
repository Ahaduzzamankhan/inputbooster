package dev.inputbooster.mixin;

import dev.inputbooster.input.KeyboardRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records when the game shows or closes a screen.
 *
 * <p>1.21.x switches screens through {@code setScreen}; 26.x replaced it with
 * {@code setScreenAndShow} and a hook naming the wrong one would silently never
 * run. The injection is {@code require = 0}: this observer can never stop the
 * client from starting.
 *
 * <p>This mixin only reads the screen argument. It does not cancel the call,
 * does not touch key mappings and does not see other mods' input.
 */
@Mixin(Minecraft.class)
public class ScreenTrackingMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), require = 0)
    private void onScreenShown(Screen screen, CallbackInfo ci) {
        KeyboardRouter.recordScreen(screen);
    }
}
