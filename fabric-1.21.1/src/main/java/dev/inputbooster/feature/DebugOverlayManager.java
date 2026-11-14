package dev.inputbooster.feature;

import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

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

    /** Set once the overlay has failed, so the error is only reported once. */
    private static volatile boolean failed = false;

    /** Hooked from {@code InGameHudMixin} at the tail of the HUD render pass. */
    public static void render(Minecraft mc, GuiGraphics graphics) {
        if (failed) return;
        try {
            draw(mc, graphics);
        } catch (Throwable t) {
            // The overlay is a diagnostic extra. It must never take the client
            // down, and it must not spam the log with one message per frame.
            failed = true;
            InputBoosterMod.LOGGER.error("[InputBooster] The overlay could not be drawn and has been disabled.", t);
        }
    }

    private static void draw(Minecraft mc, GuiGraphics graphics) {
        if (!OverlayRenderer.visible(mc)) return;
        if (mc == null || mc.options == null) return;
        // F1 hides the whole HUD, overlay included.
        if (mc.options.hideGui) return;

        OverlayRenderer.render(mc, graphics);
        drawn = true;
    }

    /**
     * True once the overlay has been submitted to the render state at least
     * once.
     *
     * <p>The client smoke test uses this to prove that the render pipeline
     * really works instead of only compiling.
     */
    public static boolean hasDrawn() {
        return drawn;
    }

    private static volatile boolean drawn = false;

    /** Nothing to register: the overlay is driven by the GUI render-state mixin.
     * Kept as an explicit call site so the initialisation order documents that
     * there is no separate registration step. */
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
