package dev.inputbooster.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes the vanilla pick-block action (renamed to {@code pickBlockOrEntity}
 * in 26.x, where it is private).
 *
 * <p>Declared as an abstract class because {@link Minecraft} is a class; an
 * interface-form mixin is only valid for interface targets.
 *
 * <p>No static helpers here: Mixin rejects a non-private static method in a
 * mixin and aborts the transformation of {@code Minecraft}, which killed the
 * client before the main menu in 4.0.2-stable. Callers use
 * {@code dev.inputbooster.access.MixinAccess}.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientAccessor {

    /** Invokes the vanilla pick-block action. */
    @Invoker("pickBlockOrEntity")
    public abstract void inputbooster$pickBlockOrEntity();
}
