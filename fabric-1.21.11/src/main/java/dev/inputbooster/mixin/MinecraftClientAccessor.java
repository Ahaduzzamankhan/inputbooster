package dev.inputbooster.mixin;

import dev.inputbooster.access.MinecraftClientHolder;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes the vanilla pick-block action. 1.21 names it {@code pickBlock};
 * 26.x renamed it to {@code pickBlockOrEntity}.
 *
 * <p>Declared as an abstract class because {@link Minecraft} is a class; an
 * interface-form mixin is only valid for interface targets.
 *
 * <p>No static helpers here: Mixin rejects a non-private static method in a
 * mixin and aborts the transformation of {@code Minecraft}, which killed the
 * client before the main menu in 4.0.2-stable.
 *
 * <p>It implements {@link MinecraftClientHolder} rather than being referenced
 * directly: ordinary code must not reference a class from the mixin package.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientAccessor implements MinecraftClientHolder {

    /** Invokes the vanilla pick-block action. */
    @Invoker("pickBlock")
    @Override
    public abstract void inputbooster$pickBlockOrEntity();
}
