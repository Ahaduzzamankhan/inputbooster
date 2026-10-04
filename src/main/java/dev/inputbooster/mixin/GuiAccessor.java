package dev.inputbooster.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Minecraft 26.x renders the HUD through a deferred render state instead of a
 * per-frame graphics extractor, so the overlay needs access to the Gui's
 * render state to enqueue its text.
 */
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("guiRenderState")
    GuiRenderState inputbooster$renderState();
}