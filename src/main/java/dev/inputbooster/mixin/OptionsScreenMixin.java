package dev.inputbooster.mixin;

import dev.inputbooster.screen.InputBoosterScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the InputBooster entry to the vanilla options screen, giving the
 * navigation chain <em>Options → InputBooster → InputBooster settings</em>.
 *
 * <h2>Why the button is no longer free-floating</h2>
 * 3.1.x drew the button at a hard-coded {@code width - 110, 6}, which is on
 * top of the options header and reads as an overlay bolted onto the screen. The
 * entry is now handed to the screen's own {@link HeaderAndFooterLayout} and
 * laid out by it, so it is positioned, focused and re-positioned on resize
 * exactly like the vanilla entries around it.
 *
 * <p>The injection runs at the {@code RETURN} of {@code init}, after vanilla
 * has already called {@code layout.visitWidgets(this::addRenderableWidget)} and
 * {@code repositionElements()}, so this method re-runs the layout pass itself
 * to pick up the new child.
 *
 * <p>{@code require = 0} is deliberate: the entry is a convenience button, so a
 * mapping change should remove it rather than stop Minecraft from starting.
 */
@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {

    /**
     * Vanilla's own screen layout. Shadowed rather than guessed at, so the
     * entry is positioned by Minecraft instead of by hard-coded coordinates.
     */
    @Shadow
    private HeaderAndFooterLayout layout;

    // Private so Mixin cannot copy the constructor into OptionsScreen; only the
    // injected method below is merged.
    private OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void addInputBoosterEntry(CallbackInfo ci) {
        if (this.minecraft == null) return;

        OptionsScreen self = (OptionsScreen) (Object) this;
        HeaderAndFooterLayout screenLayout = this.layout;
        if (screenLayout == null) return;

        // Built with the vanilla default width so the entry lines up with the
        // buttons above it instead of being a differently sized special case.
        Button entry = Button.builder(
            Component.translatable("options.inputbooster.button"),
            button -> this.minecraft.setScreenAndShow(new InputBoosterScreen(self)))
            .width(Button.DEFAULT_WIDTH)
            .build();

        // Anchored to the bottom of the content area: the options grid is
        // centred in that area and is shorter than it, so the entry always has
        // room and never overlaps the "Done" button in the footer.
        //
        // The settings are applied by a method on InputBoosterScreen rather
        // than by a lambda here: Mixin merges every member of a class-form
        // mixin into its target and rejects what it cannot merge, and a
        // lambda that captures nothing compiles to a *static* synthetic
        // method, which Mixin aborts on with "contains non-private static
        // method". A method reference to a static method on another class
        // produces no synthetic member at all.
        screenLayout.addToContents(entry, InputBoosterScreen::anchorEntryToContentBottom);

        addRenderableWidget(entry);
        // Lays out the whole screen again so the new child gets its final
        // rectangle; virtual dispatch reaches OptionsScreen#repositionElements.
        this.repositionElements();
    }
}