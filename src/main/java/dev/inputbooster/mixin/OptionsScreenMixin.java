package dev.inputbooster.mixin;

import dev.inputbooster.screen.InputBoosterScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {

    protected OptionsScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        // Add a beautiful custom button in the top-right corner of the Options screen.
        // It aligns perfectly next to the title without colliding with any option layout grid.
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("§bInputBooster..."),
            button -> {
                if (this.client != null) {
                    this.client.setScreen(new InputBoosterScreen(this));
                }
            }
        ).dimensions(this.width - 110, 6, 100, 20).build());
    }
}
