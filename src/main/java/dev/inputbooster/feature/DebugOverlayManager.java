package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

public class DebugOverlayManager {

    public static void render(GuiGraphics ctx) {
        if (!InputBoosterConfig.isShowF3Info()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.getDebugOverlay().showDebugScreen()) return;

        Font font = mc.font;
        int hz = InputBoosterMod.currentPollHz;
        String text = hz + " Hz";
        int textW = font.width(text);

        int pos = InputBoosterConfig.getOverlayPosition();
        float scale = InputBoosterConfig.getOverlayScale();
        int padX = 4;
        int padY = 3;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int scaledW = (int) (textW * scale) + padX * 2;
        int scaledH = (int) (9 * scale) + padY * 2;

        int originX, originY;
        switch (pos) {
            case 1  -> { originX = screenW - scaledW; originY = 0; }
            case 2  -> { originX = 0;                 originY = screenH - scaledH; }
            case 3  -> { originX = screenW - scaledW; originY = screenH - scaledH; }
            default -> { originX = 0;                 originY = 0; }
        }

        float opacity = InputBoosterConfig.getOverlayOpacity();
        int bgAlpha = (int)(0x90 * opacity);
        ctx.fill(originX, originY, originX + scaledW, originY + scaledH, (bgAlpha << 24));

        ctx.pose().pushMatrix();
        ctx.pose().translate(originX, originY);
        ctx.pose().scale(scale, scale);

        ctx.drawString(font, text, padX, padY, 0xFF55FFFF, true);

        ctx.pose().popMatrix();
    }

    public static void register() {}
    public static List<String> getDebugLines() { return new ArrayList<>(); }
    public static boolean isInitialized() { return true; }
}