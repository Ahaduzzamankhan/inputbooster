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
 * <p>Declared as an abstract class because {@link Gui} is a class; Mixin only
 * allows interface-form mixins for interface targets.
 */
@Mixin(Gui.class)
public abstract class GuiAccessor {

    @Accessor("guiRenderState")
    public abstract GuiRenderState inputbooster$renderState();

    /**
     * Reads the Gui's render state.
     *
     * <p>The cast goes through {@link Object} because a class-form mixin is not
     * a compile-time supertype of its target.
     *
     * @return the render state, or {@code null} when the mixin is not applied
     */
    public static GuiRenderState renderState(Gui gui) {
        if (gui == null) return null;
        try {
            return ((GuiAccessor) (Object) gui).inputbooster$renderState();
        } catch (ClassCastException notApplied) {
            return null;
        }
    }
}