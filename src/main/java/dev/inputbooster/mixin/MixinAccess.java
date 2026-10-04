package dev.inputbooster.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Call sites for the mod's accessor and invoker mixins.
 *
 * <p>These helpers live outside the mixin classes on purpose. Mixin copies every
 * method of a class-form mixin into its target, and it rejects any method it
 * cannot merge: a {@code public static} helper inside the mixin aborts the game
 * with "contains non-private static method ..." during the apply phase. Only
 * private statics are tolerated, and those cannot be called from outside.
 *
 * <p>A class-form mixin is also not a compile-time supertype of its target, so
 * the casts below go through {@link Object}. Each helper degrades to a safe
 * fallback when its mixin is not applied.
 */
public final class MixinAccess {

    private MixinAccess() {
    }

    /**
     * @return the key code the player bound to {@code mapping}, or -1 when the
     *         accessor mixin is not applied
     */
    public static int boundKeyCode(KeyMapping mapping) {
        if (mapping == null) return -1;
        try {
            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).inputbooster$boundKey();
            if (key != null) return key.getValue();
        } catch (ClassCastException notApplied) {
            return -1;
        }
        return -1;
    }

    /**
     * @return the Gui's deferred render state, or {@code null} when the
     *         accessor mixin is not applied
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
     * Runs the vanilla pick-block action.
     *
     * @return {@code false} when the invoker mixin is not applied, so the
     *         caller can report the missing hook instead of crashing
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