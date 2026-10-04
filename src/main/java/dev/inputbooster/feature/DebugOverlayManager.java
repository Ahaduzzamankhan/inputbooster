package dev.inputbooster.feature;

import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.mixin.GuiAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.List;

/**
 * F3 debug-screen overlay.
 *
 * Minecraft 26.x renders the HUD through a deferred {@link GuiRenderState}
 * rather than a graphics extractor, so the overlay enqueues a text state while
 * the GUI render state is being built.
 */
public class DebugOverlayManager {

    private static final int COLOR_AQUA   = 0x55FFFF;
    private static final int COLOR_ORANGE = 0xFFAA00;

    /**
     * Adds the InputBooster poll-rate badge to the HUD render state.
     *
     * FIX: the visibility check used to be inverted — it returned early exactly
     * when the F3 debug screen was open, which is the only situation this
     * overlay is meant to decorate.
     */
    public static void extractRenderState(Minecraft mc) {
        if (!InputBoosterConfig.isShowF3Info()) return;
        // Opacity 0 must mean "do not draw at all" — rendering a fully
        // transparent panel still costs extraction work every frame.
        if (InputBoosterConfig.getOverlayOpacity() <= 0.001f) return;
        if (mc == null || mc.player == null) return;
        if (mc.getDebugOverlay() == null || !mc.getDebugOverlay().showDebugScreen()) return;

        Gui gui = mc.gui;
        if (gui == null || !(gui instanceof GuiAccessor accessor)) return;

        GuiRenderState renderState = accessor.inputbooster$renderState();
        if (renderState == null) return;

        Font font = mc.font;
        boolean burst = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        int hz = burst ? 1000 : InputBoosterMod.currentPollHz;

        String text = hz + " Hz" + (burst ? " ⚡" : "");
        int color = burst ? COLOR_ORANGE : COLOR_AQUA;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int textW = font.width(text);
        float scale = InputBoosterConfig.getOverlayScale();
        int panelW = (int) (textW * scale);
        int panelH = (int) (9 * scale);

        int pos = InputBoosterConfig.getOverlayPosition();
        int originX;
        int originY;
        switch (pos) {
            case 1  -> { originX = screenW - panelW; originY = 0; }
            case 2  -> { originX = 0;               originY = screenH - panelH; }
            case 3  -> { originX = screenW - panelW; originY = screenH - panelH; }
            default -> { originX = 0;               originY = 0; }
        }

        float opacity = InputBoosterConfig.getOverlayOpacity();
        int backgroundColor = ((int) (0x90 * opacity) & 0xFF) << 24;

        renderState.addText(new GuiTextRenderState(
            font,
            Component.literal(text).getVisualOrderText(),
            new Matrix3x2f(),
            originX,
            originY,
            color,
            backgroundColor,
            false,
            false,
            null
        ));
    }

    /**
     * Nothing to register: the overlay is driven by the Gui render-state
     * mixin. Kept as an explicit no-op call site so the initialisation
     * sequence documents that there is no separate registration step.
     */
    public static void register() {}

    /** Human-readable status lines for the settings screen and debug output. */
    public static List<String> getDebugLines() {
        List<String> lines = new ArrayList<>(4);
        boolean burst = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        lines.add("Poll rate: " + (burst ? 1000 : InputBoosterMod.currentPollHz) + " Hz" + (burst ? " (burst)" : ""));
        lines.add("Input queue: " + InputActionQueue.size() + "/" + InputActionQueue.capacity());
        lines.add("Poller: " + (InputBoosterMod.pollingThread != null && InputBoosterMod.pollingThread.isAlive()
            ? "running" : "stopped"));
        lines.add("Overlay: " + (InputBoosterConfig.isShowF3Info() ? "enabled" : "disabled")
            + " @ " + positionName());
        return lines;
    }

    private static String positionName() {
        return switch (InputBoosterConfig.getOverlayPosition()) {
            case 1 -> "top-right";
            case 2 -> "bottom-left";
            case 3 -> "bottom-right";
            default -> "top-left";
        };
    }

    /** True when the overlay can actually draw (mixin + settings permitting). */
    public static boolean isInitialized() {
        return InputBoosterConfig.isShowF3Info()
            && InputBoosterConfig.getOverlayOpacity() > 0.001f;
    }
}