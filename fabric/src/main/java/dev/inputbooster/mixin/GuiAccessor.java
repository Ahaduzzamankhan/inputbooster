package dev.inputbooster.mixin;

import dev.inputbooster.access.GuiRenderStateHolder;
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
 *
 * <p>It implements {@link GuiRenderStateHolder} instead of being referenced
 * directly. Mixin loads the classes of this package with its own class loader
 * and refuses to let ordinary code touch them, so a direct reference from
 * {@code dev.inputbooster.access} fails with an
 * {@code IllegalClassLoadError} the moment the accessor is used.
 */
@Mixin(Gui.class)
public abstract class GuiAccessor implements GuiRenderStateHolder {

    @Accessor("guiRenderState")
    @Override
    public abstract GuiRenderState inputbooster$renderState();
}
