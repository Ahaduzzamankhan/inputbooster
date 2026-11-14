package dev.inputbooster.mixin;

import dev.inputbooster.compat.sodium.SodiumIntegration;
import dev.inputbooster.screen.InputBoosterVanillaScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the InputBooster entry to the vanilla options screen.
 *
 * <p>The button is only skipped when Sodium has registered its own configuration
 * pages for this mod, which a 1.21.x Sodium release cannot do. A second entry
 * point would otherwise show the same settings twice.
 *
 * <p>{@code require = 0} is deliberate: this is a convenience button, so a
 * mapping change should remove the button rather than stop Minecraft from
 * starting.
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void onInitTail(CallbackInfo ci) {
        if (SodiumIntegration.isActive()) return;

        addRenderableWidget(Button.builder(
                Component.translatable("options.inputbooster.button"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new InputBoosterVanillaScreen(this));
                    }
                }
        ).bounds(this.width - 110, 6, 100, 20).build());
    }
}
