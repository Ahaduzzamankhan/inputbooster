package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.perf.OptimizationManager;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

/**
 * InputBooster settings.
 *
 * <p>Reached through <em>Options → InputBooster</em> (and, when Sodium is
 * installed, through Sodium's own options sidebar). This is the only place the
 * mod is ever visible: in game it draws nothing, says nothing and sends no
 * message, so these switches are the entire user surface.
 *
 * <p>Extends {@code OptionsSubScreen}, the base class behind Video Settings,
 * Controls and Accessibility, so the header, the Done footer, keyboard
 * navigation and the reflowing option list all come from vanilla.
 */
public final class InputBoosterScreen extends OptionsSubScreen {

    private static final int ROW_CONTENT_HEIGHT = 20;

    public InputBoosterScreen(Screen parent) {
        super(parent, net.minecraft.client.Minecraft.getInstance().options,
            Component.translatable("inputbooster.options.title"));
    }

    @Override
    protected void addOptions() {
        list.addHeader(Component.translatable("inputbooster.section.modules"));
        list.addSmall(
            checkbox("inputbooster.option.cpu", InputBoosterConfig.isCpuEnabled(), InputBoosterConfig::setCpuEnabled),
            checkbox("inputbooster.option.memory", InputBoosterConfig.isMemoryEnabled(), InputBoosterConfig::setMemoryEnabled));
        list.addSmall(
            checkbox("inputbooster.option.gpu", InputBoosterConfig.isGpuEnabled(), InputBoosterConfig::setGpuEnabled),
            checkbox("inputbooster.option.disk", InputBoosterConfig.isDiskEnabled(), InputBoosterConfig::setDiskEnabled));
        list.addSmall(
            checkbox("inputbooster.option.chunk", InputBoosterConfig.isChunkEnabled(), InputBoosterConfig::setChunkEnabled),
            checkbox("inputbooster.option.input", InputBoosterConfig.isInputEnabled(), InputBoosterConfig::setInputEnabled));

        list.addHeader(Component.translatable("inputbooster.section.engine"));
        list.addSmall(
            checkbox("inputbooster.option.adaptive", InputBoosterConfig.isAdaptiveEnabled(), InputBoosterConfig::setAdaptiveEnabled),
            checkbox("inputbooster.option.diagnostics", InputBoosterConfig.isDiagnosticsEnabled(), InputBoosterConfig::setDiagnosticsEnabled));

        list.addBig(new IntSlider(0, 0, 200, ROW_CONTENT_HEIGHT,
            Component.translatable("inputbooster.option.adaptive_interval"),
            InputBoosterConfig.getAdaptiveIntervalTicks(),
            InputBoosterConfig.clampInterval(20), InputBoosterConfig.clampInterval(6000),
            v -> Component.translatable("inputbooster.value.ticks", v),
            InputBoosterConfig::setAdaptiveIntervalTicks));

        list.addBig(new IntSlider(0, 0, 200, ROW_CONTENT_HEIGHT,
            Component.translatable("inputbooster.option.write_debounce"),
            InputBoosterConfig.getWriteDebounceMs(),
            InputBoosterConfig.clampDebounce(100), InputBoosterConfig.clampDebounce(10_000),
            v -> Component.translatable("inputbooster.value.milliseconds", v),
            InputBoosterConfig::setWriteDebounceMs));
    }

    @Override
    public void removed() {
        super.removed();
        OptimizationManager.get().onSettingsChanged();
    }

    private Checkbox checkbox(String key, boolean selected, java.util.function.Consumer<Boolean> onChange) {
        return Checkbox.builder(Component.translatable(key), font)
            .selected(selected)
            .onValueChange((button, value) -> {
                onChange.accept(value);
                OptimizationManager.get().onSettingsChanged();
            })
            .build();
    }

    /** Integer slider drawn inside one vanilla options row. */
    private static final class IntSlider extends AbstractSliderButton {

        private final int min;
        private final int max;
        private final java.util.function.IntFunction<Component> label;
        private final java.util.function.IntConsumer sink;

        private IntSlider(int x, int y, int width, int height, Component title,
                          int value, int min, int max,
                          java.util.function.IntFunction<Component> label,
                          java.util.function.IntConsumer sink) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.min = min;
            this.max = max;
            this.label = label;
            this.sink = sink;
            setValue((value - (double) min) / (max - min));
            updateMessage();
        }

        private int current() {
            return (int) Math.round(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(current()));
        }

        @Override
        protected void applyValue() {
            sink.accept(current());
            OptimizationManager.get().onSettingsChanged();
        }
    }

    // ---- helpers used by OptionsScreenMixin to place the entry ----

    /** Finds the vanilla options grid so the entry can join it as a real cell. */
    public static GridLayout findOptionsGrid(HeaderAndFooterLayout screenLayout) {
        final GridLayout[] found = new GridLayout[1];
        screenLayout.visitChildren(element -> {
            if (found[0] == null && element instanceof GridLayout grid) found[0] = grid;
        });
        return found[0];
    }

    /**
     * Adds the entry as the grid's next free cell, so the grid grows by a row
     * and re-centres instead of the entry overlapping the last vanilla row.
     */
    public static boolean addToOptionsGrid(GridLayout grid, Button entry) {
        // Column count comes from the grid's own contents (distinct left
        // edges), not a hard-coded 2, so this survives a Minecraft version that
        // adds or removes an options button.
        java.util.List<net.minecraft.client.gui.layouts.LayoutElement> children = new java.util.ArrayList<>();
        grid.visitChildren(children::add);
        if (children.isEmpty()) return false;
        java.util.List<Integer> leftEdges = new java.util.ArrayList<>();
        for (var element : children) {
            if (!leftEdges.contains(element.getX())) leftEdges.add(element.getX());
        }
        int row = children.size() / leftEdges.size();
        // Mirrors the metrics OptionsScreen applies to every vanilla entry, so
        // the added cell is spaced like its neighbours instead of sitting flush.
        LayoutSettings settings = grid.defaultCellSetting().paddingHorizontal(4).paddingBottom(4);
        grid.addChild(entry, row, 0, settings);
        return true;
    }

    /** Fallback placement when the options screen has no grid to join. */
    public static void anchorEntryToContentBottom(LayoutSettings settings) {
        settings.alignHorizontallyCenter().paddingBottom(4);
    }
}