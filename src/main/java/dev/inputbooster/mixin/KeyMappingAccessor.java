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
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key inputbooster$boundKey();

    /**
     * @return the bound key code, or -1 when the accessor is unavailable
     *         (for example if the mixin failed to apply).
     */
    static int boundCode(KeyMapping mapping) {
        if (mapping instanceof KeyMappingAccessor accessor) {
            InputConstants.Key key = accessor.inputbooster$boundKey();
            if (key != null) return key.getValue();
        }
        return -1;
    }
}