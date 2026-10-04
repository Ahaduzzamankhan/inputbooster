package dev.inputbooster.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the key a {@link KeyMapping} is currently bound to.
 *
 * The polling thread samples raw key state, so it needs the numeric code the
 * player actually bound (not the default) for every tracked action.
 *
 * <p>This is an abstract class, not an interface: {@link KeyMapping} is a class,
 * and Mixin rejects an interface-form mixin whose target is a class
 * ("@Mixin target type mismatch ... is not an interface"), which aborted the
 * client during mixin preparation.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingAccessor {

    @Accessor("key")
    public abstract InputConstants.Key inputbooster$boundKey();

    /**
     * Reads the currently bound key code.
     *
     * <p>The cast goes through {@link Object} because a class-form mixin is not
     * a compile-time supertype of its target.
     *
     * @return the bound key code, or -1 when the accessor is unavailable
     *         (for example if the mixin failed to apply)
     */
    public static int boundCode(KeyMapping mapping) {
        if (mapping == null) return -1;
        try {
            InputConstants.Key key = ((KeyMappingAccessor) (Object) mapping).inputbooster$boundKey();
            if (key != null) return key.getValue();
        } catch (ClassCastException notApplied) {
            return -1;
        }
        return -1;
    }
}