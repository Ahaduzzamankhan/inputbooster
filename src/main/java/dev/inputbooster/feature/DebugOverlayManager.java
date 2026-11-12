package dev.inputbooster.feature;

import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.access.MixinAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * F3 overlay entry point.
 *
 * <p>The drawing itself lives in {@link OverlayRenderer}. This class owns the
 * decision of whether to draw, and the text list used by the diagnostics tab.
 *
 * <p>Why the previous version appeared broken: the glyph colours were RGB values
 * with a zero alpha channel, which Minecraft treats as fully transparent, so the
 * panel drew and its text did not. It also only drew while the F3 screen was
 * open. Both are addressed: colours are ARGB, and {@code overlay_mode} can keep
 * the overlay on outside F3.
 */
public class DebugOverlayManager {

    /** Hooked from {@code InGameHudMixin} at the tail of GUI render-state extraction. */
    public static void extractRenderState(Minecraft mc) {
        if (!OverlayRenderer.visible(mc)) return;
        if (mc == null || mc.gui == null) return;

        GuiRenderState state = MixinAccess.renderState(mc.gui);
        if (state == null) return;

        OverlayRenderer.render(mc, state);
    }

    /**
     * Nothing to register: the overlay is driven by the GUI render-state mixin.
     * Kept as an explicit call site so the initialisation order documents that
     * there is no separate registration step.
     */
    public static void register() {}

    /** Human-readable status lines for the settings screen and debug output. */
    public static List<String> getDebugLines() {
        List<String> lines = new ArrayList<>(7);
        boolean burst = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        lines.add("Poll rate: " + (burst ? 1000 : InputBoosterMod.currentPollHz) + " Hz"
            + (burst ? " (burst)" : ""));
        lines.add("Input queue: " + InputActionQueue.size() + "/" + InputActionQueue.capacity());
        lines.add("Poller: " + (InputBoosterMod.pollingThread != null && InputBoosterMod.pollingThread.isAlive()
            ? "running" : "stopped"));
        if (InputBoosterMod.inputEngine != null) {
            lines.add(InputBoosterMod.inputEngine.statusLine());
            lines.add("Events: keys " + InputBoosterMod.inputEngine.diagnostics().keyboardEvents()
                + ", mouse " + InputBoosterMod.inputEngine.diagnostics().mouseEvents());
        }
        if (InputBoosterMod.compatibilityManager != null) {
            lines.add(InputBoosterMod.compatibilityManager.statusLine());
        }
        lines.add("Overlay: " + (InputBoosterConfig.isShowF3Info() ? "enabled" : "disabled")
            + " @ " + OverlayLayout.positionName(InputBoosterConfig.getOverlayPosition())
            + (InputBoosterConfig.getOverlayMode() == 1 ? " (always)" : " (F3)"));
        return lines;
    }

    /** True when the overlay can actually draw (mixin and settings permitting). */
    public static boolean isInitialized() {
        return InputBoosterConfig.isShowF3Info()
            && InputBoosterConfig.getOverlayOpacity() > 0.001f;
    }
}
