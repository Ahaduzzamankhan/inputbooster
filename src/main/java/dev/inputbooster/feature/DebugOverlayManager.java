package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class DebugOverlayManager {

    public static void render(GuiGraphicsExtractor ctx) {
        if (!InputBoosterConfig.isShowF3Info()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (mc.gui.getDebugOverlay().showDebugScreen()) return;

        Font font = mc.font;
        int hz = InputBoosterMod.currentPollHz;

        String text = hz + " Hz";
        int textW = font.width(text);
        int screenW = mc.getWindow().getGuiScaledWidth();

        int x = screenW - textW - 4;
        int y = 4;

        ctx.fill(x - 2, y - 1, x + textW + 2, y + 9, 0x90000000);
        ctx.text(font, text, x, y, 0xFF55FFFF, true);
    }

    public static void register() {}
    public static List<String> getDebugLines() { return new ArrayList<>(); }
    public static boolean isInitialized() { return true; }
}
