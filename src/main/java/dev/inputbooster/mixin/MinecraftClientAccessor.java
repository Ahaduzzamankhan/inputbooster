package dev.inputbooster.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes the vanilla pick-block action, which is {@code pickBlockOrEntity} and
 * private in 26.x.
 *
 * <p>Declared as an abstract class because {@link Minecraft} is a class. The
 * mixin holds only the invoker; use
 * {@link MixinAccess#pickBlockOrEntity(Minecraft)} to call it.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientAccessor {

    /** Invokes the vanilla pick-block action. */
    @Invoker("pickBlockOrEntity")
    public abstract void inputbooster$pickBlockOrEntity();
}