package dev.inputbooster.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;

/**
 * DebugHudMixin — intentionally empty.
 *
 * We no longer hook into DebugHud's text methods (getLeftText / getRightText)
 * because their names differ across yarn 1.21.x patch versions, causing
 * "Cannot remap" build warnings and the injection silently never running.
 *
 * Instead, DebugOverlayManager uses HudRenderCallback to draw InputBooster
 * info directly via DrawContext in the top-right corner whenever F3 is open.
 * This approach works on every MC version with no mapping dependency.
 */
@Mixin(value = DebugScreenOverlay.class, priority = 900)
public class DebugHudMixin {
    // Intentionally empty — see DebugOverlayManager for the actual rendering.
}
