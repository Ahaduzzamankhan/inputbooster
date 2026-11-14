package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;

/**
 * Draws the input overlay.
 *
 * <p>The old implementation passed {@code 0x55FFFF} and {@code 0xFFAA00} as text
 * colours. Those are RGB values with an alpha byte of zero, and the GUI treats a
 * zero alpha as "do not draw", so the panel drew and its text did not. That is
 * the whole reason the overlay looked broken: it was rendering, completely
 * transparently. Colours here are ARGB with {@code 0xFF} alpha.
 *
 * <p>The text is rebuilt at most four times a second and kept in a reused array,
 * so the per-frame cost is one state submission per line instead of string
 * formatting every frame.
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
     * for it, and the number only has to keep our panel clear of the text: the
     * block is between ten and twelve lines on every 26.x client.
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
        if (InputBoosterConfig.getOverlayMode() == ConfigurationManagerOverlayMode.ALWAYS) return true;
        return mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen();
    }

    /** Kept as a nested constant holder so the renderer has no config import cycle. */
    private static final class ConfigurationManagerOverlayMode {
        static final int ALWAYS = 1;
    }

    /** Enqueues the overlay into the GUI render state. Game thread only. */
    public static void render(Minecraft mc, GuiRenderState state) {
        Font font = mc.font;
        if (font == null) return;

        boolean testRect = InputBoosterConfig.isOverlayTestRect();
        rebuildIfStale(mc);

        float scale = InputBoosterConfig.getOverlayScale();
        int textWidth = 0;
        for (int i = 0; i < lineCount; i++) textWidth = Math.max(textWidth, font.width(LINES[i]));

        int panelWidth = (int) ((textWidth + 4) * scale);
        int panelHeight = (int) ((lineCount * LINE_HEIGHT + 2) * scale);
        if (testRect) {
            panelWidth = Math.max(panelWidth, (int) (40 * scale));
            panelHeight = Math.max(panelHeight, (int) (20 * scale));
        }
        if (panelWidth <= 0 || panelHeight <= 0) return;

        // The vanilla F3 screen owns the top of the window: its two columns of
        // text start at y=2 and span the full width. Drawing the panel over them
        // erased the debug text, which is what "the overlay overrides the F3
        // text" looked like. With the debug screen open the panel moves below
        // that block and drops its background, so nothing is covered either way.
        boolean debugScreenOpen = mc.getDebugOverlay() != null
            && mc.getDebugOverlay().showDebugScreen();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        int position = InputBoosterConfig.getOverlayPosition();
        int reservedTop = debugScreenOpen
            ? Math.min(font.lineHeight * DEBUG_SCREEN_LINES + 4, Math.max(0, guiHeight / 2))
            : 0;
        boolean anchoredToBottom = position == OverlayLayout.BOTTOM_LEFT
            || position == OverlayLayout.BOTTOM_RIGHT;
        OverlayLayout.Pos pos = OverlayLayout.compute(
            guiWidth,
            anchoredToBottom ? guiHeight : Math.max(1, guiHeight - reservedTop),
            panelWidth, panelHeight,
            position);
        int panelX = pos.x();
        int panelY = pos.y() + (anchoredToBottom ? 0 : reservedTop);

        float opacity = InputBoosterConfig.getOverlayOpacity();
        int background = debugScreenOpen
            ? 0
            : (((int) (0xA0 * opacity)) & 0xFF) << 24;
        float invScale = scale > 0.0f ? 1.0f / scale : 1.0f;
        Matrix3x2f pose = new Matrix3x2f().scaling(scale);

        if (testRect) {
            // The rectangle is drawn through the text pipeline: a run of spaces
            // with an opaque background colour fills the box the text would
            // occupy. The element pipeline is not used because its render
            // pipeline type differs between 26.2 and 26.3.
            int spaceWidth = Math.max(1, font.width(" "));
            int spaces = Math.max(1, (int) Math.ceil(40.0 / spaceWidth));
            state.addText(new GuiTextRenderState(
                font,
                Component.literal(" ".repeat(spaces)).getVisualOrderText(),
                pose,
                (int) (panelX * invScale),
                (int) (panelY * invScale),
                COLOR_TEST_RECT,
                COLOR_TEST_RECT,
                false,
                false,
                null
            ));
        }

        int bodyColor = InputBoosterConfig.getOverlayColor();
        for (int i = 0; i < lineCount; i++) {
            boolean warning = LINES[i].startsWith("!");
            int color = warning ? COLOR_WARN : (i == 0 ? COLOR_ACCENT : bodyColor);
            String line = warning ? LINES[i].substring(1) : LINES[i];
            state.addText(new GuiTextRenderState(
                font,
                Component.literal(line).getVisualOrderText(),
                pose,
                (int) ((panelX + 2) * invScale),
                (int) ((panelY + 1 + i * LINE_HEIGHT) * invScale),
                color,
                background,
                false,
                false,
                null
            ));
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
                add(String.format(java.util.Locale.ROOT, "Dispatch %.2f µs  Queue %d/%d",
                    micros, dev.inputbooster.InputActionQueue.size(), dev.inputbooster.InputActionQueue.capacity()));
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
