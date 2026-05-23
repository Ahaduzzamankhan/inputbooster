package dev.inputbooster.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DebugScreenOverlay.class, priority = 900)
public class DebugHudMixin {
}
