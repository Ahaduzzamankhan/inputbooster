package dev.inputbooster.mixin;

import dev.inputbooster.screen.InputBoosterScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        // Add a beautiful custom button in the top-right corner of the Options screen.
        // It aligns perfectly next to the title without colliding with any option layout grid.
        this.addRenderableWidget(Button.builder(
            Component.literal("§bInputBooster..."),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new InputBoosterScreen(this));
                }
            }
        ).bounds(this.width - 110, 6, 100, 20).build());
    }
}
