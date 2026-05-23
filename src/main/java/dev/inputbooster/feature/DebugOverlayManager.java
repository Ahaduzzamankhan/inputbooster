package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class DebugOverlayManager {

    private static final int COLOR_AQUA   = 0xFF55FFFF;
    private static final int COLOR_RED    = 0xFFFF4444;
    private static final int COLOR_ORANGE = 0xFFFFAA00;
    private static final int COLOR_GREEN  = 0xFF55FF55;
    private static final int COLOR_YELLOW = 0xFFFFFF55;
    private static final int COLOR_GRAY   = 0xFFAAAAAA;

    public static void render(GuiGraphicsExtractor ctx) {
        if (!InputBoosterConfig.isShowF3Info()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !InputBoosterMod.gameReady) return;

        if (mc.gui.getDebugOverlay().showDebugScreen()) return;

        Font font = mc.font;
        boolean burst  = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        int hz         = InputBoosterMod.currentPollHz;
        int fps        = InputBoosterMod.currentFps;
        int cps        = InputBoosterMod.cpsLimiter != null ? InputBoosterMod.cpsLimiter.getCps() : 0;
        int maxCps     = InputBoosterConfig.getMaxCps();

        List<Line> lines = buildLines(burst, hz, fps, cps, maxCps);

        int   pos       = InputBoosterConfig.getOverlayPosition();
        float scale     = InputBoosterConfig.getOverlayScale();
        int   lineH     = (int)(10 * scale);
        int   padX      = 3, padY = 3, bgPad = 2;
        int   screenW   = mc.getWindow().getGuiScaledWidth();
        int   screenH   = mc.getWindow().getGuiScaledHeight();

        int maxTextW = 0;
        for (Line l : lines) maxTextW = Math.max(maxTextW, font.width(l.text));
        int panelW = (int)(maxTextW * scale) + padX * 2 + bgPad * 2;
        int panelH = lines.size() * lineH + padY * 2 + bgPad * 2;

        int originX, originY;
        switch (pos) {
            case 1  -> { originX = screenW - panelW; originY = 0; }
            case 2  -> { originX = 0;                originY = screenH - panelH; }
            case 3  -> { originX = screenW - panelW; originY = screenH - panelH; }
            default -> { originX = 0;                originY = 0; }
        }

        ctx.fill(
            originX,          originY,
            originX + panelW, originY + panelH,
            0x90000000
        );

        int textX = originX + padX + bgPad;
        int textY = originY + padY + bgPad;

        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            ctx.text(font, l.text, textX, textY + i * lineH, l.color, true);
        }
    }

    private static List<Line> buildLines(boolean burst, int hz, int fps, int cps, int maxCps) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(
            "[ InputBooster " + InputBoosterMod.MOD_VERSION + " ]" + (burst ? " \u26a1BURST" : ""),
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

    public static void register() {}
    public static List<String> getDebugLines() { return new ArrayList<>(); }
    public static boolean isInitialized() { return true; }
}
