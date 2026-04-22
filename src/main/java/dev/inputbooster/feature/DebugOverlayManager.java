package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * DebugOverlayManager — F3 debug screen integration for InputBooster.
 *
 * Displays:
 *  • Current poll rate and mode (AUTO/MANUAL)
 *  • FPS and input queue depth
 *  • Total hits and recovered inputs
 *  • Performance metrics and thread status
 *  • Feature status indicators
 *
 * Integration:
 *  • Adds custom left-side text to F3 debug screen
 *  • Real-time stat updates every frame
 *  • Toggleable via options (default: enabled)
 *
 * Version: 2.1.0
 * Author: Ahaduzzaman Khan
 */
public class DebugOverlayManager {

    private static boolean initialized = false;

    public static void register() {
        if (initialized) return;

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (InputBoosterConfig.isShowF3Info()) {
                renderDebugOverlay(drawContext, tickCounter);
            }
        });

        initialized = true;
        InputBoosterMod.LOGGER.info("[DebugOverlayManager] F3 overlay registered");
    }

    private static void renderDebugOverlay(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!InputBoosterMod.gameReady) return;

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        int x = 2;
        int y = 2;
        int lineHeight = 10;
        int bgColor = 0x8B000000; // Transparent black

        // Get stats
        String modeName = InputBoosterConfig.isPollRateAutoMode() ? "AUTO" : "MANUAL";
        int hz = InputBoosterMod.currentPollHz;
        int fps = InputBoosterMod.currentFps;
        long hits = InputBoosterMod.totalHits.get();
        long recovered = InputBoosterMod.recoveredInputs.get();
        boolean active = InputBoosterMod.active;

        // ─────────────────────────────────────────────────────────────
        // Build debug lines
        // ─────────────────────────────────────────────────────────────

        String[] lines = {
            "§b§l══ InputBooster v" + InputBoosterMod.MOD_VERSION + " ══§r",
            "§7Mode: §" + (InputBoosterConfig.isPollRateAutoMode() ? "a" : "e") + modeName,
            "§7Poll Rate: §e" + hz + " Hz§7 | §7FPS: §e" + fps,
            "§7Status: §" + (active ? "a✓ ACTIVE" : "c✗ INACTIVE"),
            "",
            "§7Input Stats:",
            "§7  Hits: §e" + formatNumber(hits),
            "§7  Recovered: §e" + formatNumber(recovered),
            "",
            "§7Features:",
            (InputBoosterConfig.isSprintFixEnabled() ? "§a✓" : "§c✗") + " Sprint Fix",
            (InputBoosterConfig.isAutoSprintEnabled() ? "§a✓" : "§c✗") + " Auto-Sprint",
            (InputBoosterConfig.isWTapAssistEnabled() ? "§a✓" : "§c✗") + " W-Tap Assist",
            (InputBoosterConfig.isAntiIdleEnabled() ? "§a✓" : "§c✗") + " Anti-Idle",
            (InputBoosterConfig.isAutoStrafeEnabled() ? "§a✓" : "§c✗") + " Auto-Strafe",
            (InputBoosterConfig.isCpsLimiterEnabled() ? "§a✓" : "§c✗") + " CPS Limiter",
            "",
            "§7Thread: §" + (InputBoosterMod.pollingThread != null && InputBoosterMod.pollingThread.isAlive() ? "a" : "c") +
                   (InputBoosterMod.pollingThread != null && InputBoosterMod.pollingThread.isAlive() ? "RUNNING" : "STOPPED"),
            "§7Press [O] for options",
        };

        // ─────────────────────────────────────────────────────────────
        // Render
        // ─────────────────────────────────────────────────────────────

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int renderY = y + (i * lineHeight);

            // Semi-transparent background for better readability
            if (!line.isEmpty()) {
                int textWidth = textRenderer.getWidth(line);
                ctx.fill(x - 2, renderY - 1, x + textWidth + 2, renderY + lineHeight - 1, bgColor);
            }

            // Draw text
            ctx.drawText(textRenderer, Text.literal(line), x, renderY, 0xFFFFFF, false);
        }
    }

    /**
     * Returns debug lines for injection into the F3 left panel via DebugHudMixin.
     */
    public static List<String> getDebugLines() {
        List<String> lines = new ArrayList<>();
        if (!InputBoosterMod.gameReady) return lines;

        String modeName = InputBoosterConfig.isPollRateAutoMode() ? "AUTO" : "MANUAL";
        lines.add("[InputBooster v" + InputBoosterMod.MOD_VERSION + "]");
        lines.add("Mode: " + modeName + " | " + InputBoosterMod.currentPollHz + " Hz");
        lines.add("Hits: " + formatNumber(InputBoosterMod.totalHits.get()) +
                  " | Recovered: " + formatNumber(InputBoosterMod.recoveredInputs.get()));
        lines.add("Status: " + (InputBoosterMod.active ? "ACTIVE" : "INACTIVE"));
        return lines;
    }

    /**
     * Format large numbers with K/M suffix.
     * Example: 1234 → "1.2K", 1234567 → "1.2M"
     */
    private static String formatNumber(long num) {
        if (num >= 1_000_000) {
            return String.format("%.1fM", num / 1_000_000.0);
        } else if (num >= 1_000) {
            return String.format("%.1fK", num / 1_000.0);
        } else {
            return String.valueOf(num);
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
