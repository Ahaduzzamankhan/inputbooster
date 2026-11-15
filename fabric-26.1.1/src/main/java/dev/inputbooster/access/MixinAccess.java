package dev.inputbooster.access;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

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
 * <p>That error happened while the game was loading {@code Minecraft}, so the
 * client died before the main menu and no amount of {@code require = 0} could
 * save it. Static methods are only allowed in a mixin when they are private, and
 * a private one cannot be called from here, so the helpers live in this plain
 * class instead.
 *
 * <p>The helpers cast to the duck interfaces of this package and never to the
 * mixin classes themselves. Mixin loads the classes of the mixin package with
 * its own class loader and refuses to let ordinary code reference them:
 *
 * <pre>
 * IllegalClassLoadError: Illegal classload request for dev.inputbooster.mixin.GuiAccessor.
 * Mixin is defined in inputbooster.mixins.json and cannot be referenced directly
 * </pre>
 *
 * <p>That error happened while the overlay was drawn, so the overlay never
 * appeared and the client reported a mixin transformation failure on every
 * frame.
 *
 * <p>Each helper reports the failure case ({@code -1}, {@code null},
 * {@code false}) when the mixin did not apply, so a missing hook degrades
 * instead of throwing.
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
            InputConstants.Key key = ((KeyMappingHolder) mapping).inputbooster$boundKey();
            return key == null ? -1 : key.getValue();
        } catch (ClassCastException | LinkageError notApplied) {
            return -1;
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
            ((MinecraftClientHolder) client).inputbooster$pickBlockOrEntity();
            return true;
        } catch (ClassCastException | LinkageError notApplied) {
            return false;
        }
    }
}
