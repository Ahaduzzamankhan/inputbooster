package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.McCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * DebugOverlayManager — draws the InputBooster HUD panel.
 *
 * Position and scale are configurable via InputBoosterConfig:
 *   overlayPosition: 0=Top-Left  1=Top-Right  2=Bottom-Left  3=Bottom-Right
 *   overlayScale:    0.5 – 3.0
 */
public class DebugOverlayManager {

    private static final int COLOR_AQUA   = 0xFF55FFFF;
    private static final int COLOR_RED    = 0xFFFF4444;
    private static final int COLOR_ORANGE = 0xFFFFAA00;
    private static final int COLOR_GREEN  = 0xFF55FF55;
    private static final int COLOR_YELLOW = 0xFFFFFF55;
    private static final int COLOR_GRAY   = 0xFFAAAAAA;

    /** Called from InGameHudMixin every frame. */
    public static void render(GuiGraphics ctx) {
        if (!InputBoosterConfig.isShowF3Info()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        // Hide overlay while F3 is open — it clutters the debug screen
        if (mc.gui.getDebugOverlay().showDebugScreen()) return;

        Font font = mc.font;
        boolean burst  = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        int hz         = InputBoosterMod.currentPollHz;
        int fps        = InputBoosterMod.currentFps > 0 ? InputBoosterMod.currentFps : McCompat.getFps(mc);
        int cps        = InputBoosterMod.cpsLimiter != null ? InputBoosterMod.cpsLimiter.getCps() : 0;
        int maxCps     = InputBoosterConfig.getMaxCps();

        List<Line> lines = buildLines(burst, hz, fps, cps, maxCps);

        int   pos       = InputBoosterConfig.getOverlayPosition();
        float scale     = InputBoosterConfig.getOverlayScale();
        int   lineH     = (int)(10 * scale);
        int   padX      = 3, padY = 3, bgPad = 2;
        int   screenW   = mc.getWindow().getGuiScaledWidth();
        int   screenH   = mc.getWindow().getGuiScaledHeight();

        // Measure panel
        int maxTextW = 0;
        for (Line l : lines) maxTextW = Math.max(maxTextW, font.width(l.text));
        
        boolean showKeystrokes = InputBoosterConfig.isShowKeystrokes();
        if (showKeystrokes) {
            maxTextW = Math.max(60, maxTextW); // Fit keystrokes grid
        }

        int panelW = (int)(maxTextW * scale) + padX * 2 + bgPad * 2;
        int panelH = lines.size() * lineH + padY * 2 + bgPad * 2;
        if (showKeystrokes) {
            panelH += (int)((72 + 6) * scale); // 72px for keystrokes grid + 6px spacing
        }

        // Anchor
        int originX, originY;
        switch (pos) {
            case 1  -> { originX = screenW - panelW;          originY = 0; }           // Top-Right
            case 2  -> { originX = 0;                          originY = screenH - panelH; } // Bottom-Left
            case 3  -> { originX = screenW - panelW;          originY = screenH - panelH; } // Bottom-Right
            default -> { originX = 0;                          originY = 0; }           // Top-Left
        }

        // Background
        // Apply configurable opacity to background
        float opacity = InputBoosterConfig.getOverlayOpacity();
        int bgAlpha = (int) (0x90 * opacity); // original alpha 0x90 (~56% opacity)
        int bgColor = (bgAlpha << 24) | 0x000000;
        ctx.fill(
            originX, originY,
            originX + panelW, originY + panelH,
            bgColor
        );

        // Draw each line manually at pre-computed scaled positions.
        int textX = originX + padX + bgPad;
        int textY = originY + padY + bgPad;
        int scaledTextX = Math.round(textX / scale);
        int scaledTextY = Math.round(textY / scale);
        int scaledLineH = Math.max(10, Math.round(lineH / scale));

        ctx.pose().pushMatrix();
        ctx.pose().scale(scale, scale);
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            ctx.drawString(font, l.text, scaledTextX, scaledTextY + i * scaledLineH, l.color, true);
        }

        if (showKeystrokes) {
            int gridX = scaledTextX + (maxTextW - 58) / 2;
            int gridY = scaledTextY + lines.size() * scaledLineH + 4;
            drawKeystrokesGrid(ctx, font, gridX, gridY, mc);
        }

        ctx.pose().popMatrix();
    }

    private static List<Line> buildLines(boolean burst, int hz, int fps, int cps, int maxCps) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(
            "[ InputBooster " + InputBoosterMod.DISPLAY_VERSION + " ]" + (burst ? " \u26a1BURST" : ""),
            burst ? COLOR_ORANGE : COLOR_AQUA
        ));
        lines.add(new Line(
            "Poll Rate: " + hz + " Hz" + (burst ? " (\u26a1->1000)" : ""),
            burst ? COLOR_ORANGE : COLOR_RED
        ));
        lines.add(new Line(
            "FPS: " + fps,
            fps >= 60 ? COLOR_GREEN : fps >= 30 ? COLOR_YELLOW : COLOR_RED
        ));
        boolean auto = InputBoosterConfig.isPollRateAutoMode();
        lines.add(new Line("Mode: " + (auto ? "AUTO" : "MANUAL"), auto ? COLOR_GREEN : COLOR_YELLOW));
        lines.add(new Line(LatencyProfiler.formatForOverlay(), COLOR_GRAY));
        lines.add(new Line(
            "Hits: " + fmt(InputBoosterMod.totalHits.get())
            + "  Rec: " + fmt(InputBoosterMod.recoveredInputs.get()), COLOR_GRAY
        ));
        lines.add(new Line("CPS: " + cps + " / " + maxCps, COLOR_YELLOW));
        if (InputBoosterMod.moduleManager != null) {
            lines.add(new Line(InputBoosterMod.moduleManager.statusLine(), COLOR_GRAY));
        }
        if (InputBoosterMod.replayRecorder != null) {
            lines.add(new Line(InputBoosterMod.replayRecorder.statusLine(), COLOR_GRAY));
        }
        if (InputBoosterMod.safeMode != null && InputBoosterMod.safeMode.isSafeModeActive()) {
            lines.add(new Line("Safe Mode: ACTIVE", COLOR_RED));
        }
        if (InputBoosterMod.eventLog != null && InputBoosterConfig.isDebugMode()) {
            lines.add(new Line(InputBoosterMod.eventLog.latest(), COLOR_GRAY));
        }
        lines.add(new Line(
            "Status: " + (InputBoosterMod.active ? "ACTIVE" : "INACTIVE"),
            InputBoosterMod.active ? COLOR_GREEN : COLOR_RED
        ));
        return lines;
    }

    private record Line(String text, int color) {}

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private static void drawKeystrokesGrid(GuiGraphics ctx, Font font, int x, int y, Minecraft mc) {
        boolean w = mc.options.keyUp.isDown();
        boolean a = mc.options.keyLeft.isDown();
        boolean s = mc.options.keyDown.isDown();
        boolean d = mc.options.keyRight.isDown();
        boolean lmb = mc.options.keyAttack.isDown();
        boolean rmb = mc.options.keyUse.isDown();
        boolean space = mc.options.keyJump.isDown();

        // Row 1: W
        drawKey(ctx, font, x + 20, y, 18, 18, "W", w);

        // Row 2: A, S, D
        drawKey(ctx, font, x, y + 20, 18, 18, "A", a);
        drawKey(ctx, font, x + 20, y + 20, 18, 18, "S", s);
        drawKey(ctx, font, x + 40, y + 20, 18, 18, "D", d);

        // Row 3: LMB, RMB
        drawKey(ctx, font, x, y + 40, 28, 18, "LMB", lmb);
        drawKey(ctx, font, x + 30, y + 40, 28, 18, "RMB", rmb);

        // Row 4: Space
        drawKey(ctx, font, x, y + 60, 58, 12, "SPACE", space);
    }

    private static void drawKey(GuiGraphics ctx, Font font, int x, int y, int w, int h, String text, boolean pressed) {
        int bgColor = pressed ? 0x8055FFFF : 0x40000000; // Aqua highlight if pressed, dark transparent if not
        int textColor = pressed ? 0xFFFFFFFF : 0xFFAAAAAA;
        ctx.fill(x, y, x + w, y + h, bgColor);
        ctx.drawCenteredString(font, text, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static void register() {}
    public static List<String> getDebugLines() { return new ArrayList<>(); }
    public static boolean isInitialized() { return true; }
}
