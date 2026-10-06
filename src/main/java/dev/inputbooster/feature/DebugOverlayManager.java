package dev.inputbooster.feature;

import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.access.MixinAccess;
import net.minecraft.client.DeltaTracker;
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
 * The corner HUD badge.
 *
 * Minecraft 26.x renders the HUD through a deferred {@link GuiRenderState}
 * rather than a graphics extractor, so the badge is enqueued as a
 * {@link GuiTextRenderState} while the GUI render state is being built by
 * {@link #extractRenderState(Minecraft, DeltaTracker)}.
 *
 * <p>All layout and colour maths lives in {@link OverlayLayout} so it can be
 * covered by the automated tests.
 */
public class DebugOverlayManager {

    /**
     * Adds the InputBooster poll-rate badge to the HUD render state.
     *
     * <p>FIX (badge never visible): the text colours were RGB constants with an
     * alpha byte of {@code 0x00} ({@code 0x55FFFF} / {@code 0xFFAA00}).
     * Minecraft drops text whose alpha is zero — {@code GuiGraphicsExtractor}
     * even short-circuits on {@code ARGB.alpha(color) == 0} — so the badge was
     * submitted every frame and blended away invisibly. Colours now carry an
     * explicit {@code 0xFF} alpha and the configured opacity is applied to it.
     *
     * <p>FIX (scale slider did nothing): the pose matrix was the identity, so
     * glyphs always rendered at 1x while the panel was sized as if scaled. The
     * pose now translates to the chosen corner and scales about that origin.
     *
     * <p>FIX (badge drawn over a hidden HUD): {@code GuiRenderState.isHudHidden}
     * (F1) was ignored, so the badge kept drawing over a HUD the player had
     * explicitly hidden.
     */
    public static void extractRenderState(Minecraft mc, DeltaTracker deltaTracker) {
        if (mc == null) return;
        // Opacity 0 must mean "do not draw at all".
        float opacity = OverlayLayout.clampOpacity(InputBoosterConfig.getOverlayOpacity());
        if (opacity <= 0.001f) return;

        GuiRenderState renderState = MixinAccess.renderState(mc.gui);
        if (renderState == null) return;
        if (renderState.isHudHidden) return;
        if (!InputBoosterConfig.isShowF3Info()) return;

        Font font = mc.font;
        if (font == null) return;

        boolean burst = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        int hz = burst ? 1000 : InputBoosterMod.currentPollHz;

        String text = hz + " Hz" + (burst ? " ⚡" : "");
        int baseColor = burst ? OverlayLayout.COLOR_ORANGE : OverlayLayout.COLOR_AQUA;
        int color = OverlayLayout.withOpacity(baseColor, opacity);

        float scale = OverlayLayout.clampScale(InputBoosterConfig.getOverlayScale());
        int textW = font.width(text);
        int lineH = font.lineHeight;
        int panelW = Math.round(textW * scale);
        int panelH = Math.round(lineH * scale);

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int pos = InputBoosterConfig.getOverlayPosition();
        int originX = OverlayLayout.originX(pos, screenW, panelW);
        int originY = OverlayLayout.originY(pos, screenH, panelH);

        // Translate to the corner first, then scale about it: a scaled badge
        // stays anchored to the corner it was positioned at.
        Matrix3x2f pose = new Matrix3x2f().translate(originX, originY).scale(scale, scale);

        renderState.addText(new GuiTextRenderState(
            font,
            Component.literal(text).getVisualOrderText(),
            pose,
            0,
            0,
            color,
            OverlayLayout.backgroundColor(opacity),
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
            + " @ " + OverlayLayout.positionName(InputBoosterConfig.getOverlayPosition()));
        return lines;
    }

    /** True when the overlay is configured to draw at all. */
    public static boolean isInitialized() {
        return InputBoosterConfig.isShowF3Info()
            && OverlayLayout.clampOpacity(InputBoosterConfig.getOverlayOpacity()) > 0.001f;
    }
}