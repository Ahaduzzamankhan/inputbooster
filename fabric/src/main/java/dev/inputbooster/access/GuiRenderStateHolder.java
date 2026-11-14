package dev.inputbooster.access;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Duck interface for the deferred render state of the HUD.
 *
 * <p>Mixin refuses to let ordinary code reference a class that lives in the
 * mixin package:
 *
 * <pre>
 * IllegalClassLoadError: Illegal classload request for dev.inputbooster.mixin.GuiAccessor.
 * Mixin is defined in inputbooster.mixins.json and cannot be referenced directly
 * </pre>
 *
 * <p>The accessor therefore lives in this package, and the mixin class
 * implements it. The interface method is merged into the target, so the HUD
 * really is an instance of this interface once the mixin has been applied, and
 * ordinary code can cast to it.
 *
 * <p>The method name is prefixed so it cannot collide with anything the target
 * already declares.
 */
public interface GuiRenderStateHolder {

    /**
     * The render state the HUD collects its draw commands into.
     *
     * @return the HUD render state, never {@code null}
     */
    GuiRenderState inputbooster$renderState();
}
