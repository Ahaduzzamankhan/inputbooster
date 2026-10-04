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
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientAccessor {

    /** Invokes the vanilla pick-block action. */
    @Invoker("pickBlockOrEntity")
    public abstract void inputbooster$pickBlockOrEntity();

    /**
     * Runs the vanilla pick-block action.
     *
     * <p>The cast goes through {@link Object} because a class-form mixin is not
     * a compile-time supertype of its target.
     *
     * @return {@code false} when the mixin is not applied, so the caller can
     *         report that pick-block input is unavailable instead of crashing
     */
    public static boolean pickBlockOrEntity(Minecraft client) {
        if (client == null) return false;
        try {
            ((MinecraftClientAccessor) (Object) client).inputbooster$pickBlockOrEntity();
            return true;
        } catch (ClassCastException notApplied) {
            return false;
        }
    }
}