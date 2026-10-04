package dev.inputbooster.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    /** Invokes the vanilla pick-block action (renamed to pickBlockOrEntity in 26.x). */
    @Invoker("pickBlockOrEntity")
    void invokeDoItemPick();
}
