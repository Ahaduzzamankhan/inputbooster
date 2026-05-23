package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public class DebugOverlayManager {

    private static boolean initialized = false;

    public static void register() {
        if (initialized) return;

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!InputBoosterConfig.isShowF3Info()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getDebugHud().shouldShowDebugHud()) return; // F3 open — DebugHudMixin handles it
            renderHudOverlay(drawContext, mc);
        });

        initialized = true;
    }

    private static void renderHudOverlay(DrawContext ctx, MinecraftClient mc) {
        if (!InputBoosterMod.gameReady) return;

        TextRenderer font = mc.textRenderer;
        int x = 2, y = 2, lineHeight = 10;


        boolean burst  = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        int hz         = InputBoosterMod.currentPollHz;
        int fps        = InputBoosterMod.currentFps;
        int cps        = InputBoosterMod.cpsLimiter != null ? InputBoosterMod.cpsLimiter.getCps() : 0;
        int maxCps     = InputBoosterConfig.getMaxCps();
        String mode    = InputBoosterConfig.isPollRateAutoMode() ? "AUTO" : "MANUAL";

        List<String> lines = new ArrayList<>();
        lines.add("§b§lInputBooster v" + InputBoosterMod.MOD_VERSION + (burst ? " §c⚡BURST" : ""));
        lines.add("§7Mode: §" + (InputBoosterConfig.isPollRateAutoMode() ? "a" : "e") + mode
            + "§7 | §e" + hz + " Hz§7 | FPS: §e" + fps);
        lines.add("§7" + LatencyProfiler.formatForOverlay());
        lines.add("§7CPS: §e" + cps + "§7/" + maxCps + buildCpsBar(cps, maxCps));
        lines.add("§7Status: §" + (InputBoosterMod.active ? "a✓ ACTIVE" : "c✗ INACTIVE"));

        for (int i = 0; i < lines.size(); i++) {
            int renderY = y + (i * lineHeight);
            int textWidth = font.getWidth(lines.get(i));
            ctx.drawText(font, lines.get(i), x, renderY, 0xFFFFFF, true);
        }
    }

    private static String buildCpsBar(int cps, int maxCps) {
        if (maxCps <= 0) return "";
        int filled = Math.min(cps * 10 / maxCps, 10);
        StringBuilder bar = new StringBuilder(" §8[");
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "§a|" : "§8|");
        bar.append("§8]");
        return bar.toString();
    }

    public static List<String> getDebugLines() {
        List<String> lines = new ArrayList<>();
        if (!InputBoosterMod.gameReady) return lines;

        boolean burst = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        lines.add("[InputBooster v" + InputBoosterMod.MOD_VERSION + "]" + (burst ? " ⚡BURST" : ""));
        lines.add("Mode: " + (InputBoosterConfig.isPollRateAutoMode() ? "AUTO" : "MANUAL")
            + " | " + InputBoosterMod.currentPollHz + " Hz");
        lines.add(LatencyProfiler.formatForOverlay());
        lines.add("Hits: " + fmt(InputBoosterMod.totalHits.get())
            + " | Recovered: " + fmt(InputBoosterMod.recoveredInputs.get()));
        lines.add("CPS: " + (InputBoosterMod.cpsLimiter != null
            ? InputBoosterMod.cpsLimiter.getCps() + "/" + InputBoosterConfig.getMaxCps() : "disabled"));
        lines.add("Status: " + (InputBoosterMod.active ? "ACTIVE" : "INACTIVE"));
        return lines;
    }

    private static String fmt(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    public static boolean isInitialized() { return initialized; }
}
