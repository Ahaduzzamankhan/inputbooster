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
 * Call sites for the mod's accessor and invoker mixins.
 *
 * <p>This class lives outside {@code dev.inputbooster.mixin} on purpose, for
 * two separate Mixin rules.
 *
 * <ol>
 *   <li>Mixin refuses to load any class that sits in a package declared by a
 *       mixin config unless it is itself a loadable mixin. A plain helper in
 *       that package crashes the client on the first render frame with
 *       {@code IllegalClassLoadError: ... is in a defined mixin package
 *       dev.inputbooster.mixin.* and cannot be referenced directly}.
 *   <li>Mixin copies every member of a class-form mixin into its target and
 *       rejects what it cannot merge: a {@code public static} helper inside a
 *       mixin aborts the game with "contains non-private static method ..."
 *       during the apply phase. Only private statics are tolerated, and those
 *       cannot be called from outside.
 * </ol>
 *
 * <p>A mixin is also not a compile-time supertype of its target, so the casts
 * below go through {@link Object}. The accessor mixins themselves are declared
 * as interfaces, which is what makes them loadable and safe to reference here.
 * Each helper degrades to a safe fallback when its mixin is not applied.
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