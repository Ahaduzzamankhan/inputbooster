package dev.inputbooster.mixin;

import dev.inputbooster.access.MixinAccess;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes the vanilla pick-block action, which is {@code pickBlockOrEntity} and
 * private in 26.x.
 *
 * <p>Declared as an interface even though {@link Minecraft} is a class: Mixin
 * only marks a mixin as a loadable accessor when the mixin itself is an
 * interface with accessor-only methods ({@code MixinInfo.getVariant}). As an
 * abstract class it would be an ordinary, non-loadable mixin and casting to it
 * would abort the client with {@code IllegalClassLoadError}. Use
 * {@link MixinAccess#pickBlockOrEntity(Minecraft)} to call it.
 */
@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {

    /** Invokes the vanilla pick-block action. */
    @Invoker("pickBlockOrEntity")
    void inputbooster$pickBlockOrEntity();
}