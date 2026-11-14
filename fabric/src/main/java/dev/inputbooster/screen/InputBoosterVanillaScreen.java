package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.OverlayColor;
import dev.inputbooster.engine.InputEngine;
import dev.inputbooster.feature.OverlayLayout;
import dev.inputbooster.feature.OverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * The settings screen used when Sodium is not installed.
 *
 * <p>It is built from vanilla widgets only — buttons, cycle buttons, sliders
 * and string labels — so it behaves exactly like the rest of the options
 * screen: vanilla focus handling, vanilla narration and vanilla input. The
 * custom window with its own scrollbar and tab rail that this replaced had
 * none of that.
 *
 * <p>The screen is a presentation layer only. Every control reads and writes
 * {@link InputBoosterConfig} and saves immediately, so a change is applied
 * without a restart and survives a restart. When Sodium is installed the same
 * settings are offered on the Sodium pages, and both write to the same
 * configuration file, so there is only one source of truth.
 */
public class InputBoosterVanillaScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int HEADING_HEIGHT = 18;
    private static final int WIDGET_HEIGHT = 20;
    private static final int TOP_MARGIN = 34;
    private static final int BOTTOM_MARGIN = 28;

    private static final int HEADING_COLOR = 0xFF55D6E8;

    private final Screen parent;

    /** The rows of the screen, rebuilt whenever the list scrolls. */
    private final List<RowSpec> specs = new ArrayList<>();

    /** Headings and their position, redrawn on every frame. */
    private final List<Heading> headings = new ArrayList<>();

    /** A section heading and where it is drawn. */
    private record Heading(int x, int y, String text) {}

    private int scroll;
    private int contentHeight;
    private int viewportTop;
    private int viewportBottom;

    public InputBoosterVanillaScreen(Screen parent) {
        super(Component.literal("InputBooster"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        specs.clear();
        headings.clear();

        viewportTop = TOP_MARGIN;
        viewportBottom = this.height - BOTTOM_MARGIN;

        buildSpecs();
        rebuildRows();
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    private void rebuildRows() {
        super.rebuildWidgets();
        headings.clear();

        int contentWidth = this.width - 20;
        int labelWidth = Math.max(80, contentWidth / 2 - 10);
        int controlX = 10 + labelWidth + 10;
        int controlWidth = Math.max(60, contentWidth - labelWidth - 10);

        int y = viewportTop - scroll;
        for (RowSpec spec : specs) {
            Row row = spec.create(y, 10, labelWidth, controlX, controlWidth);
            if (row.heading() != null) {
                headings.add(new Heading(10, y, row.heading()));
            }
            if (row.widget() != null) {
                addRenderableWidget(row.widget());
            }
            y += row.height();
        }
        contentHeight = y + scroll - viewportTop;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, contentHeight - (viewportBottom - viewportTop));
        if (max <= 0) return false;
        int next = Math.max(0, Math.min(max, scroll - (int) Math.round(scrollY * 20)));
        if (next != scroll) {
            scroll = next;
            rebuildRows();
        }
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        for (Heading heading : headings) {
            ctx.text(this.font, heading.text(), heading.x(), heading.y(), HEADING_COLOR);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    // ── Rows ─────────────────────────────────────────────────────────────────

    /** One line of the screen: an optional control and an optional heading. */
    private record Row(AbstractWidget widget, int height, String heading) {}

    /** Creates the widget of a row at the given position. */
    private interface RowSpec {
        Row create(int y, int labelX, int labelWidth, int controlX, int controlWidth);
    }

    private void heading(String text) {
        specs.add((y, labelX, labelWidth, controlX, controlWidth) ->
                new Row(null, HEADING_HEIGHT, text));
    }

    private void toggle(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        specs.add((y, labelX, labelWidth, controlX, controlWidth) ->
                new Row(Button.builder(toggleText(label, getter.get()), button -> {
                    boolean value = !getter.get();
                    setter.accept(value);
                    button.setMessage(toggleText(label, value));
                    save();
                }).bounds(controlX, y, controlWidth, WIDGET_HEIGHT).build(), ROW_HEIGHT, null));
    }

    private void slider(String label, int min, int max, int step, int value,
                        IntFunction<String> format, IntConsumer setter) {
        specs.add((y, labelX, labelWidth, controlX, controlWidth) ->
                new Row(new SettingSlider(controlX, y, controlWidth, WIDGET_HEIGHT, label, min, max, step,
                        value, format, setter), ROW_HEIGHT, null));
    }

    private void slider(String label, int min, int max, int step, float fractionValue,
                        IntFunction<String> format, IntConsumer setter) {
        slider(label, min, max, step, (int) Math.round(fractionValue), format, setter);
    }

    private <T> void cycle(String label, List<T> values, Supplier<T> getter,
                           Function<T, String> display, Consumer<T> setter) {
        specs.add((y, labelX, labelWidth, controlX, controlWidth) -> {
            // The value overload of the builder is used on purpose: the
            // supplier overload cannot be told apart from it by the compiler.
            CycleButton<T> button = CycleButton
                    .builder(value -> Component.literal(display.apply(value)), getter.get())
                    .withValues(values)
                    .create(controlX, y, controlWidth, WIDGET_HEIGHT, Component.literal(label),
                            (btn, value) -> {
                                setter.accept(value);
                                save();
                            });
            return new Row(button, ROW_HEIGHT, null);
        });
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "§aON" : "§7OFF"));
    }

    /** Saves and applies a change immediately. */
    private static void save() {
        InputBoosterConfig.save();
        OverlayRenderer.invalidate();
    }

    // ── Content ──────────────────────────────────────────────────────────────

    private void buildSpecs() {
        heading("Polling");

        toggle("Automatic polling",
                InputBoosterConfig::isPollRateAutoMode, InputBoosterConfig::setPollRateAutoMode);
        slider("Manual polling frequency", 60, 1000, 10, InputBoosterConfig.getPollRateHz(),
                value -> value + " Hz", InputBoosterConfig::setPollRateHz);
        slider("FPS check interval", 1, 100, 1, InputBoosterConfig.getFpsCheckInterval(),
                value -> value + " ticks", InputBoosterConfig::setFpsCheckInterval);

        heading("Input engine");

        cycle("Engine mode", List.of(InputEngine.Mode.values()),
                () -> InputEngine.Mode.fromConfig(InputBoosterConfig.getEngineMode()),
                mode -> switch (mode) {
                    case AUTO -> "Automatic";
                    case EVENT -> "Event driven";
                    case POLL -> "Polled";
                },
                mode -> InputBoosterConfig.setEngineMode(mode.ordinal()));
        toggle("Compatibility fallback",
                InputBoosterConfig::isCompatFallback, InputBoosterConfig::setCompatFallback);
        toggle("Keyboard repeat guard",
                InputBoosterConfig::isKeyboardRepeatGuard, InputBoosterConfig::setKeyboardRepeatGuard);
        slider("Mouse scroll debounce", 0, 200, 5, InputBoosterConfig.getMouseScrollDebounceMs(),
                value -> value + " ms", InputBoosterConfig::setMouseScrollDebounceMs);
        slider("Click deduplication", 0, 100, 5, InputBoosterConfig.getMouseClickDedupeMs(),
                value -> value + " ms", InputBoosterConfig::setMouseClickDedupeMs);
        toggle("Experimental keyboard handler",
                InputBoosterConfig::isCustomKeyboardHandlerEnabled,
                InputBoosterConfig::setCustomKeyboardHandlerEnabled);

        heading("Movement assistance");

        toggle("Sprint fix", InputBoosterConfig::isSprintFixEnabled, InputBoosterConfig::setSprintFixEnabled);
        toggle("Auto sprint", InputBoosterConfig::isAutoSprintEnabled, InputBoosterConfig::setAutoSprintEnabled);
        toggle("W-tap assistance", InputBoosterConfig::isWTapAssistEnabled, InputBoosterConfig::setWTapAssistEnabled);
        toggle("Anti-idle", InputBoosterConfig::isAntiIdleEnabled, InputBoosterConfig::setAntiIdleEnabled);
        toggle("Auto strafe", InputBoosterConfig::isAutoStrafeEnabled, InputBoosterConfig::setAutoStrafeEnabled);
        toggle("Combo keys", InputBoosterConfig::isComboKeysEnabled, InputBoosterConfig::setComboKeysEnabled);

        heading("Click limiter");

        toggle("CPS limiter", InputBoosterConfig::isCpsLimiterEnabled, InputBoosterConfig::setCpsLimiterEnabled);
        slider("Maximum CPS", 1, 20, 1, InputBoosterConfig.getMaxCps(),
                value -> value + " CPS", InputBoosterConfig::setMaxCps);
        cycle("Limiter mode", List.of(CpsMode.values()),
                () -> CpsMode.valueOf(InputBoosterConfig.getCpsMode()),
                mode -> mode.name().charAt(0) + mode.name().substring(1).toLowerCase(Locale.ROOT),
                mode -> InputBoosterConfig.setCpsMode(mode.name()));
        toggle("Adaptive burst mode",
                InputBoosterConfig::isBurstModeEnabled, InputBoosterConfig::setBurstModeEnabled);

        heading("Overlay");

        toggle("Overlay information", InputBoosterConfig::isShowF3Info, InputBoosterConfig::setShowF3Info);
        cycle("Display mode", List.of(OverlayMode.values()),
                () -> InputBoosterConfig.getOverlayMode() == 1 ? OverlayMode.ALWAYS : OverlayMode.F3_ONLY,
                mode -> mode == OverlayMode.ALWAYS ? "Always" : "With F3 only",
                mode -> InputBoosterConfig.setOverlayMode(mode == OverlayMode.ALWAYS ? 1 : 0));
        cycle("Overlay position", List.of(OverlayPosition.values()),
                () -> OverlayPosition.values()[InputBoosterConfig.getOverlayPosition()],
                position -> switch (position) {
                    case TOP_LEFT -> "Top left";
                    case TOP_RIGHT -> "Top right";
                    case BOTTOM_LEFT -> "Bottom left";
                    case BOTTOM_RIGHT -> "Bottom right";
                    case TOP_CENTER -> "Top center";
                },
                position -> InputBoosterConfig.setOverlayPosition(position.ordinal()));
        slider("Overlay scale", 50, 300, 10, InputBoosterConfig.getOverlayScale() * 100.0f,
                value -> String.format(Locale.ROOT, "%.1fx", value / 100.0),
                value -> InputBoosterConfig.setOverlayScale(value / 100.0f));
        slider("Overlay opacity", 0, 100, 5, InputBoosterConfig.getOverlayOpacity() * 100.0f,
                value -> value + "%", value -> InputBoosterConfig.setOverlayOpacity(value / 100.0f));
        cycle("Overlay colour", List.of(OverlayColor.values()),
                () -> OverlayColor.closestTo(InputBoosterConfig.getOverlayColor()),
                color -> color.label,
                color -> InputBoosterConfig.setOverlayColor(color.argb));
        toggle("Test rectangle", InputBoosterConfig::isOverlayTestRect, InputBoosterConfig::setOverlayTestRect);
        toggle("Keystroke display", InputBoosterConfig::isShowKeystrokes, InputBoosterConfig::setShowKeystrokes);
        toggle("Action-bar messages", InputBoosterConfig::isShowActionBar, InputBoosterConfig::setShowActionBar);
        toggle("Engine diagnostics", InputBoosterConfig::isOverlayShowEngine, InputBoosterConfig::setOverlayShowEngine);
        toggle("Event diagnostics", InputBoosterConfig::isOverlayShowEvents, InputBoosterConfig::setOverlayShowEvents);
        toggle("Performance overhead", InputBoosterConfig::isOverlayShowOverhead, InputBoosterConfig::setOverlayShowOverhead);

        heading("Safety and feedback");

        toggle("Safe mode", InputBoosterConfig::isSafeModeEnabled, InputBoosterConfig::setSafeModeEnabled);
        toggle("Event logging", InputBoosterConfig::isEventLogEnabled, InputBoosterConfig::setEventLogEnabled);
        toggle("Key conflict warnings", InputBoosterConfig::isKeyConflictWarn, InputBoosterConfig::setKeyConflictWarn);
        toggle("Debug mode", InputBoosterConfig::isDebugMode, InputBoosterConfig::setDebugMode);
        toggle("Replay recording", InputBoosterConfig::isReplayEnabled, InputBoosterConfig::setReplayEnabled);
        toggle("Per-server profiles", InputBoosterConfig::isPerServerProfiles, InputBoosterConfig::setPerServerProfiles);
        toggle("Click sounds", InputBoosterConfig::isClickSoundsEnabled, InputBoosterConfig::setClickSoundsEnabled);
        slider("Click sound pitch", 50, 200, 5, InputBoosterConfig.getClickSoundPitch() * 100.0f,
                value -> String.format(Locale.ROOT, "%.2fx", value / 100.0),
                value -> InputBoosterConfig.setClickSoundPitch(value / 100.0f));
        slider("Click sound volume", 0, 100, 5, InputBoosterConfig.getClickSoundVolume() * 100.0f,
                value -> value + "%", value -> InputBoosterConfig.setClickSoundVolume(value / 100.0f));
    }

    /** When the diagnostics overlay is drawn. */
    private enum OverlayMode {
        F3_ONLY,
        ALWAYS
    }

    /** Corner the diagnostics overlay is drawn in. */
    private enum OverlayPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_CENTER
    }

    /** How the click limiter treats the accepted clicks. */
    private enum CpsMode {
        FIXED,
        HUMANIZED,
        WEAPON_AWARE,
        COOLDOWN
    }

    /** One integer-backed slider; floats are stored as hundredths. */
    private static final class SettingSlider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;
        private final int step;
        private final IntConsumer setter;
        private final IntFunction<String> format;
        private int current;

        SettingSlider(int x, int y, int width, int height, String label,
                      int min, int max, int step, int value,
                      IntFunction<String> format, IntConsumer setter) {
            super(x, y, width, height, Component.literal(label),
                    max == min ? 0.0 : (clamp(value, min, max) - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = Math.max(1, step);
            this.format = format;
            this.setter = setter;
            this.current = clamp(value, min, max);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": §e" + format.apply(current)));
        }

        @Override
        protected void applyValue() {
            int raw = min + (int) Math.round(value * (max - min));
            int snapped = min + Math.round((raw - min) / (float) step) * step;
            snapped = clamp(snapped, min, max);
            if (snapped != current) {
                current = snapped;
                setter.accept(current);
                save();
            }
            updateMessage();
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
