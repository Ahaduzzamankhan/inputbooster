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
 * The 26.x API only exposes {@code setScreenAndShow}; {@code setScreen} was
 * removed, and a hook naming it would silently never run.
 * The injection is {@code require = 0}: a version without that method keeps
 * working, and this observer can never stop the client from starting.
 *
 * <p>This mixin only reads the screen argument. It does not cancel the call,
 * does not touch key mappings and does not see other mods' input.
 */
@Mixin(Minecraft.class)
public class ScreenTrackingMixin {

    @Inject(method = "setScreenAndShow", at = @At("HEAD"), require = 0)
    private void onScreenShown(Screen screen, CallbackInfo ci) {
        KeyboardRouter.recordScreen(screen);
    }
}
