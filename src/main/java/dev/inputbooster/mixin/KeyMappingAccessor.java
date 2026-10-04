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
 * <p>Declared as an abstract class because {@link KeyMapping} is a class: Mixin
 * only accepts an interface-form mixin for an interface target. The mixin must
 * contain nothing but the accessor, because Mixin merges every other method into
 * the target and rejects the ones it cannot merge. Call
 * {@link MixinAccess#boundKeyCode(KeyMapping)} instead of touching this class.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingAccessor {

    @Accessor("key")
    public abstract InputConstants.Key inputbooster$boundKey();
}