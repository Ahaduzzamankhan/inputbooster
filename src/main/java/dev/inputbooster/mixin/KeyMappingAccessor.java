package dev.inputbooster.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.inputbooster.access.MixinAccess;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the key a {@link KeyMapping} is currently bound to.
 *
 * The polling thread samples raw key state, so it needs the numeric code the
 * player actually bound (not the default) for every tracked action.
 *
 * <p>Declared as an interface even though {@link KeyMapping} is a class: Mixin
 * only marks a mixin as a loadable accessor when the mixin itself is an
 * interface with accessor-only methods ({@code MixinInfo.getVariant}). As an
 * abstract class it would be an ordinary, non-loadable mixin and casting to it
 * would abort the client with {@code IllegalClassLoadError}. Call
 * {@link MixinAccess#boundKeyCode(KeyMapping)} instead of touching this class.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key inputbooster$boundKey();
}