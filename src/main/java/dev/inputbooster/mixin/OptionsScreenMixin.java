package dev.inputbooster.mixin;

import dev.inputbooster.screen.InputBoosterScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds an InputBooster entry to the vanilla options screen.
 *
 * {@code require = 0} is deliberate: this is a convenience button, so a mapping
 * change should remove the button rather than stop Minecraft from starting.
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void onInitTail(CallbackInfo ci) {
        addRenderableWidget(Button.builder(
            Component.translatable("options.inputbooster.button"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new InputBoosterScreen(this));
                }
            }
        ).bounds(this.width - 110, 6, 100, 20).build());
    }
}