package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the input overlay.
 *
 * <p>1.21 renders the HUD into a {@link GuiGraphics}, so the panel is submitted
 * with its own fill and draw-string calls. The layout, the text and the rebuild
 * throttle are the same as on every other build.
 *
 * <p>Colours are ARGB with a full alpha byte: an RGB value whose alpha byte is
 * zero makes the GUI skip the draw.
 */
public final class OverlayRenderer {

    private static final int COLOR_ACCENT = 0xFF55FFFF;
    private static final int COLOR_WARN = 0xFFFFAA00;

    /** Colour of the bright rectangle the debug toggle draws. */
    private static final int COLOR_TEST_RECT = 0xFFFF00FF;

    private static final long REBUILD_INTERVAL_NANOS = 250_000_000L;
    private static final int LINE_HEIGHT = 10;

    /**
     * Rough height of the vanilla F3 text block, in lines. Vanilla has no API
     * for it, and the number only has to keep our panel clear of the text.
     */
    private static final int DEBUG_SCREEN_LINES = 12;

    private static final String[] LINES = new String[6];
    private static int lineCount;
    private static long lastBuildNanos;

    private OverlayRenderer() {}

    /** True when the overlay should be drawn at all right now. */
    public static boolean visible(Minecraft mc) {
        if (mc == null || mc.player == null) return false;
        if (!InputBoosterConfig.isShowF3Info()) return false;
        if (InputBoosterConfig.getOverlayOpacity() <= 0.001f
                && !InputBoosterConfig.isOverlayTestRect()) {
            return false;
        }
        if (InputBoosterConfig.getOverlayMode() == 1) return true;
        return mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen();
    }

    /** Draws the overlay into the HUD. Game thread only. */
    public static void render(Minecraft mc, GuiGraphics graphics) {
        Font font = mc.font;
        if (font == null) return;

        boolean testRect = InputBoosterConfig.isOverlayTestRect();
        rebuildIfStale(mc);

        float scale = InputBoosterConfig.getOverlayScale();
        if (scale <= 0.01f) scale = 1.0f;

        int textWidth = 0;
        for (int i = 0; i < lineCount; i++) textWidth = Math.max(textWidth, font.width(LINES[i]));

        int panelWidth = (int) ((textWidth + 4) * scale);
        int panelHeight = (int) ((lineCount * LINE_HEIGHT + 2) * scale);
        if (testRect) {
            panelWidth = Math.max(panelWidth, (int) (40 * scale));
            panelHeight = Math.max(panelHeight, (int) (20 * scale));
        }
        if (panelWidth <= 0 || panelHeight <= 0) return;

        // The vanilla F3 screen owns the top of the window: its columns of text
        // start at the top and span the full width. Drawing the panel over them
        // erased the debug text, so with F3 open the panel moves below that block
        // and drops its background.
        boolean debugScreenOpen = mc.getDebugOverlay() != null
                && mc.getDebugOverlay().showDebugScreen();
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int position = InputBoosterConfig.getOverlayPosition();
        int reservedTop = debugScreenOpen
                ? Math.min(font.lineHeight * DEBUG_SCREEN_LINES + 4, Math.max(0, guiHeight / 2))
                : 0;
        boolean anchoredToBottom = position == OverlayLayout.BOTTOM_LEFT
                || position == OverlayLayout.BOTTOM_RIGHT;
        OverlayLayout.Pos pos = OverlayLayout.compute(
                guiWidth,
                anchoredToBottom ? guiHeight : Math.max(1, guiHeight - reservedTop),
                panelWidth, panelHeight, position);
        int panelX = pos.x();
        int panelY = pos.y() + (anchoredToBottom ? 0 : reservedTop);

        float opacity = InputBoosterConfig.getOverlayOpacity();
        int background = debugScreenOpen ? 0 : (((int) (0xA0 * opacity)) & 0xFF) << 24;
        float invScale = 1.0f / scale;

        var pose = graphics.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1.0f);
        try {
            int x = (int) (panelX * invScale);
            int y = (int) (panelY * invScale);
            int width = textWidth + 4;
            int height = lineCount * LINE_HEIGHT + 2;

            if (background != 0) graphics.fill(x, y, x + width, y + height, background);
            if (testRect) graphics.fill(x, y, x + 40, y + 20, COLOR_TEST_RECT);

            int bodyColor = InputBoosterConfig.getOverlayColor();
            for (int i = 0; i < lineCount; i++) {
                boolean warning = LINES[i].startsWith("!");
                int color = warning ? COLOR_WARN : (i == 0 ? COLOR_ACCENT : bodyColor);
                String line = warning ? LINES[i].substring(1) : LINES[i];
                graphics.drawString(font, line, x + 2, y + 1 + i * LINE_HEIGHT, color, false);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void rebuildIfStale(Minecraft mc) {
        long now = System.nanoTime();
        if (now - lastBuildNanos < REBUILD_INTERVAL_NANOS) return;
        lastBuildNanos = now;
        lineCount = 0;

        var engine = dev.inputbooster.InputBoosterMod.inputEngine;
        var diagnostics = engine == null ? null : engine.diagnostics();

        add("InputBooster " + dev.inputbooster.InputBoosterMod.MOD_VERSION);
        if (engine != null && InputBoosterConfig.isOverlayShowEngine()) {
            add("Engine: " + engine.mode().label.toLowerCase(java.util.Locale.ROOT)
                    + "  " + dev.inputbooster.InputBoosterMod.currentPollHz + " Hz");
        }
        if (diagnostics != null && InputBoosterConfig.isOverlayShowEvents()) {
            add("Keys " + compact(diagnostics.keyboardEvents())
                    + "  Mouse " + compact(diagnostics.mouseEvents())
                    + "  Actions " + compact(diagnostics.actionsSubmitted()));
        }
        if (diagnostics != null && InputBoosterConfig.isOverlayShowOverhead()) {
            double micros = diagnostics.dispatchMicros();
            if (diagnostics.dispatchSamples() > 0) {
                add(String.format(java.util.Locale.ROOT, "Dispatch %.2f \u00b5s  Queue %d/%d",
                        micros, dev.inputbooster.InputActionQueue.size(),
                        dev.inputbooster.InputActionQueue.capacity()));
            } else {
                add("Dispatch: no samples  Queue " + dev.inputbooster.InputActionQueue.size());
            }
        }
        if (InputBoosterConfig.isCustomKeyboardHandlerEnabled()) {
            add("!Custom keyboard handler is ALPHA");
        }
    }

    private static void add(String line) {
        if (lineCount < LINES.length) LINES[lineCount++] = line;
    }

    private static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        if (value < 1_000_000) return (value / 1_000) + "k";
        return (value / 1_000_000) + "m";
    }

    /** Forces a rebuild on the next frame, e.g. after a config change. */
    public static void invalidate() {
        lastBuildNanos = 0L;
    }
}
