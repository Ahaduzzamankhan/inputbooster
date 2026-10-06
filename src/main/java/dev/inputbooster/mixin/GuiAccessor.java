package dev.inputbooster.mixin;

import dev.inputbooster.access.MixinAccess;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Minecraft 26.x renders the HUD through a deferred render state instead of a
 * per-frame graphics extractor, so the overlay needs access to the Gui's
 * render state to enqueue its text.
 *
 * <p>Declared as an interface on purpose. {@link Gui} is a class, and Mixin
 * classifies a mixin as a loadable accessor only when it is an interface whose
 * methods are all accessors ({@code MixinInfo.getVariant}). A class-form
 * accessor is registered as a non-loadable mixin, so casting to it at runtime
 * makes Mixin throw {@code IllegalClassLoadError} and kills the client. Use
 * {@link MixinAccess#renderState(Gui)} to read the value.
 */
@Mixin(Gui.class)
public interface GuiAccessor {

    @Accessor("guiRenderState")
    GuiRenderState inputbooster$renderState();
}