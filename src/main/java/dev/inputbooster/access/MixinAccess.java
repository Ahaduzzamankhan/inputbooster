package dev.inputbooster.access;

import com.mojang.blaze3d.platform.InputConstants;
import dev.inputbooster.mixin.GuiAccessor;
import dev.inputbooster.mixin.KeyMappingAccessor;
import dev.inputbooster.mixin.MinecraftClientAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Calls the accessor mixins from normal code.
 *
 * <p>These helpers used to be static methods on the mixin classes themselves,
 * which is a fatal mistake: Mixin refuses a mixin that contains a non-private
 * static method and aborts the transformation of the target class.
 *
 * <pre>
 * InvalidMixinException: Mixin inputbooster.mixins.json:KeyMappingAccessor
 * contains non-private static method boundCode(Lnet/minecraft/client/KeyMapping;)I
 * </pre>
 *
 * That error happened while the game was loading {@code Minecraft}, so the
 * client died before the main menu and no amount of {@code require = 0} could
 * save it. Static methods are only allowed in a mixin when they are private, and
 * a private one cannot be called from here, so the helpers live in this plain
 * class instead.
 *
 * <p>The casts go through {@link Object} because a class-form mixin is not a
 * compile-time supertype of its target. Each helper reports the failure case
 * ({@code -1}, {@code null}, {@code false}) when the mixin did not apply, so a
 * missing hook degrades instead of throwing.
 */
public final class MixinAccess {

    private MixinAccess() {}

    /**
     * Reads the key code a mapping is currently bound to.
     *
     * @return the bound key code, or -1 when the accessor is unavailable
     */
    public static int boundCode(KeyMapping mapping) {
        if (mapping == null) return -1;
        try {
            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).inputbooster$boundKey();
            return key == null ? -1 : key.getValue();
        } catch (ClassCastException notApplied) {
            return -1;
        }
    }

    /**
     * Reads the Gui's deferred render state.
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

    /**
     * Runs vanilla's pick-block action.
     *
     * @return {@code false} when the mixin is not applied, so the caller can
     *         report that pick-block input is unavailable instead of crashing
     */
    public static boolean pickBlockOrEntity(Minecraft client) {
        if (client == null) return false;
        try {
            ((MinecraftClientAccessor) (Object) client).inputbooster$pickBlockOrEntity();
            return true;
        } catch (ClassCastException notApplied) {
            return false;
        }
    }
}
