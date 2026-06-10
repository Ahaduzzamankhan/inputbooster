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
    private static final int COLOR_ORANGE = 0xFFFFAA00;

    public static void render(GuiGraphicsExtractor ctx) {
        if (!InputBoosterConfig.isShowF3Info()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.getDebugOverlay().showDebugScreen()) return;

        Font font = mc.font;
        boolean burst = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        int hz = burst ? 1000 : InputBoosterMod.currentPollHz;

        String text = hz + " Hz" + (burst ? " \u26a1" : "");
        int color = burst ? COLOR_ORANGE : COLOR_AQUA;

        float scale = InputBoosterConfig.getOverlayScale();
        int padX = 4, padY = 3;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int textW = font.width(text);
        int panelW = (int)(textW * scale) + padX * 2;
        int panelH = (int)(9 * scale) + padY * 2;

        int pos = InputBoosterConfig.getOverlayPosition();
        int originX, originY;
        switch (pos) {
            case 1  -> { originX = screenW - panelW; originY = 0; }
            case 2  -> { originX = 0;                originY = screenH - panelH; }
            case 3  -> { originX = screenW - panelW; originY = screenH - panelH; }
            default -> { originX = 0;                originY = 0; }
        }

        float opacity = InputBoosterConfig.getOverlayOpacity();
        int bgAlpha = (int)(0x90 * opacity);
        ctx.fill(originX, originY, originX + panelW, originY + panelH, (bgAlpha << 24));

        ctx.pose().pushMatrix();
        ctx.pose().scale(scale, scale);
        int tx = Math.round((originX + padX) / scale);
        int ty = Math.round((originY + padY) / scale);
        ctx.text(font, text, tx, ty, color);
        ctx.pose().popMatrix();
    }

    public static void register() {}
    public static List<String> getDebugLines() { return new ArrayList<>(); }
    public static boolean isInitialized() { return true; }
}
