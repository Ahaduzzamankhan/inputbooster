package dev.inputbooster.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Minecraft 26.x renders the HUD through a deferred render state instead of a
 * per-frame graphics extractor, so the overlay needs access to the Gui's
 * render state to enqueue its text.
 *
 * <p>Declared as an abstract class because {@link Gui} is a class. The mixin
 * holds only the accessor; use {@link MixinAccess#renderState(Gui)} to read it.
 */
@Mixin(Gui.class)
public abstract class GuiAccessor {

    @Accessor("guiRenderState")
    public abstract GuiRenderState inputbooster$renderState();
}