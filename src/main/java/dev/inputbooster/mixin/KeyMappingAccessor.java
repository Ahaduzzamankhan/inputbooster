package dev.inputbooster.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the key a {@link KeyMapping} is currently bound to.
 *
 * <p>The polling thread samples raw key state, so it needs the numeric code the
 * player actually bound (not the default) for every tracked action.
 *
 * <p>This is an abstract class, not an interface: {@link KeyMapping} is a class,
 * and Mixin rejects an interface-form mixin whose target is a class
 * ("@Mixin target type mismatch ... is not an interface"), which aborted the
 * client during mixin preparation.
 *
 * <p>It contains no static helpers on purpose. A mixin may only hold private
 * static methods, and a non-private one fails the whole transformation with
 * "contains non-private static method", which crashed the client at launch in
 * 4.0.2-stable. Callers use {@code dev.inputbooster.access.MixinAccess} instead.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingAccessor {

    @Accessor("key")
    public abstract InputConstants.Key inputbooster$boundKey();
}
