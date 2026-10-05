package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.feature.OverlayLayout;
import dev.inputbooster.feature.ProfileManager;
import dev.inputbooster.feature.SessionStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * The InputBooster settings screen.
 *
 * <h2>Navigation</h2>
 * Vanilla {@code Options} → <em>InputBooster</em> button → this screen. The
 * button is added by {@code OptionsScreenMixin}, which places it inside the
 * options layout so it reads as one more entry rather than a floating overlay.
 *
 * <h2>Why it is built on {@link OptionsSubScreen}</h2>
 * This is the base class every vanilla options page uses (Video Settings,
 * Controls, Sounds, Chat, Accessibility, …). Inheriting it means the screen
 * automatically gets the vanilla title header, the "Done" footer, the
 * scrollable {@link net.minecraft.client.gui.components.OptionsList} that
 * reflows with the window, keyboard navigation and narration. Nothing here
 * computes pixel rectangles by hand, so the layout keeps working when the
 * vanilla metrics change.
 *
 * <h2>Renderer independence</h2>
 * Everything is drawn through {@link GuiGraphicsExtractor}, Minecraft's
 * deferred GUI submission API. That is renderer agnostic: it records draw
 * commands into a render state that the backend (OpenGL <em>or</em> Vulkan)
 * replays, so the screen behaves identically on the Vulkan renderer and needs
 * no Sodium, Iris or other rendering mod. See
 * {@link dev.inputbooster.feature.OverlayLayout} for the same guarantee on the
 * in-game HUD badge.
 */
public class InputBoosterScreen extends OptionsSubScreen {

    /** One-click poll rate presets, matching the values the HUD badge can show. */
    private static final int[] POLL_PRESETS = {100, 200, 350, 500, 750, 1000};

    /** CPS limiter shapes offered by {@code CpsLimiter}. */
    private static final String[] CPS_MODES = {"FIXED", "HUMANIZED", "WEAPON_AWARE", "COOLDOWN"};

    /** Display names for {@link #CPS_MODES}, in the same order. */
    private static final String[] CPS_MODE_KEYS = {
        "inputbooster.value.cps_mode.fixed",
        "inputbooster.value.cps_mode.humanized",
        "inputbooster.value.cps_mode.weapon_aware",
        "inputbooster.value.cps_mode.cooldown"
    };

    /** Display names for the four overlay corners, indexed by their config value. */
    private static final String[] CORNER_KEYS = {
        "inputbooster.value.corner.0",
        "inputbooster.value.corner.1",
        "inputbooster.value.corner.2",
        "inputbooster.value.corner.3"
    };

    /** Names offered by the quick-save profile row. */
    private static final String[] QUICK_PROFILES = {"PvP", "Mining", "Idle", "Hybrid", "Custom"};

    /** Ticks between two refreshes of the live statistics block. */
    private static final int STATS_REFRESH_TICKS = 20;

    /**
     * Usable height of one row in the vanilla options list.
     *
     * <p>{@code OptionsList} is constructed with a fixed row height of 25 and
     * {@code getContentHeight()} reports 21 of it; {@code Entry#extractContent}
     * then places every widget at the row's top-left and never looks at the
     * widget's own height. Anything taller than this therefore draws straight
     * over the rows below it, which is why the statistics are one single-line
     * widget per row and the sparkline is exactly this tall.
     */
    private static final int ROW_CONTENT_HEIGHT = 20;

    /** Number of single-line rows the session statistics block occupies. */
    private static final int STAT_LINE_COUNT = 6;

    /** Widgets that only make sense while the poll rate is pinned manually. */
    private final List<AbstractWidget> manualPollWidgets = new ArrayList<>();

    /** One single-line widget per statistics row, refreshed once a second. */
    private final List<StringWidget> statLines = new ArrayList<>();

    private CpsSparklineWidget sparkline;
    private SettingSlider pollRateSlider;
    private int ticksSinceStatsRefresh;

    public InputBoosterScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options,
            Component.translatable("inputbooster.options.title"));
    }

    // ── Content ─────────────────────────────────────────────────────────────

    @Override
    protected void addOptions() {
        manualPollWidgets.clear();
        addPollingSection();
        addMovementSection();
        addClickSection();
        addOverlaySection();
        addDiagnosticsSection();
        addStatsSection();
        addProfilesSection();
        updateManualPollAvailability();
    }

    private void addPollingSection() {
        section("inputbooster.section.polling");

        big(cycle(
            Component.translatable("inputbooster.option.poll_mode"),
            value -> Component.translatable(value
                ? "inputbooster.value.poll_mode.auto"
                : "inputbooster.value.poll_mode.manual"),
            InputBoosterConfig::isPollRateAutoMode,
            List.of(Boolean.TRUE, Boolean.FALSE),
            (button, value) -> {
                InputBoosterConfig.setPollRateAutoMode(value);
                updateManualPollAvailability();
                applyManualPollRate();
            }));

        SettingSlider pollRate = slider(
            "inputbooster.option.poll_rate", "inputbooster.tip.poll_rate",
            60.0, 1000.0, 10.0,
            v -> Component.translatable("inputbooster.value.hz", (int) Math.round(v)),
            InputBoosterConfig.getPollRateHz(), v -> InputBoosterConfig.setPollRateHz((int) Math.round(v)),
            this::applyManualPollRate);
        pollRateSlider = pollRate;
        manualPollWidgets.add(pollRate);
        big(pollRate);

        for (int i = 0; i + 1 < POLL_PRESETS.length; i += 2) {
            Button first = presetButton(POLL_PRESETS[i]);
            Button second = presetButton(POLL_PRESETS[i + 1]);
            manualPollWidgets.add(first);
            manualPollWidgets.add(second);
            small(first, second);
        }

        small(
            checkbox("inputbooster.option.burst_mode", "inputbooster.tip.burst_mode",
                InputBoosterConfig.isBurstModeEnabled(), InputBoosterConfig::setBurstModeEnabled),
            checkbox("inputbooster.option.combo_keys", "inputbooster.tip.combo_keys",
                InputBoosterConfig.isComboKeysEnabled(), InputBoosterConfig::setComboKeysEnabled));
    }

    private void addMovementSection() {
        section("inputbooster.section.movement");

        small(
            checkbox("inputbooster.option.sprint_fix", "inputbooster.tip.sprint_fix",
                InputBoosterConfig.isSprintFixEnabled(), InputBoosterConfig::setSprintFixEnabled),
            checkbox("inputbooster.option.auto_sprint", "inputbooster.tip.auto_sprint",
                InputBoosterConfig.isAutoSprintEnabled(), InputBoosterConfig::setAutoSprintEnabled));

        small(
            checkbox("inputbooster.option.wtap_assist", "inputbooster.tip.wtap_assist",
                InputBoosterConfig.isWTapAssistEnabled(), InputBoosterConfig::setWTapAssistEnabled),
            checkbox("inputbooster.option.anti_idle", "inputbooster.tip.anti_idle",
                InputBoosterConfig.isAntiIdleEnabled(), InputBoosterConfig::setAntiIdleEnabled));

        small(
            checkbox("inputbooster.option.auto_strafe", "inputbooster.tip.auto_strafe",
                InputBoosterConfig.isAutoStrafeEnabled(), InputBoosterConfig::setAutoStrafeEnabled));
    }

    private void addClickSection() {
        section("inputbooster.section.click");

        small(
            checkbox("inputbooster.option.cps_limiter", "inputbooster.tip.cps_limiter",
                InputBoosterConfig.isCpsLimiterEnabled(), InputBoosterConfig::setCpsLimiterEnabled),
            checkbox("inputbooster.option.click_sounds", "inputbooster.tip.click_sounds",
                InputBoosterConfig.isClickSoundsEnabled(), InputBoosterConfig::setClickSoundsEnabled));

        big(cycle(
            Component.translatable("inputbooster.option.cps_mode"),
            index -> Component.translatable(CPS_MODE_KEYS[index]),
            InputBoosterScreen::cpsModeIndex,
            List.of(0, 1, 2, 3),
            (button, index) -> InputBoosterConfig.setCpsMode(CPS_MODES[index])));

        big(slider(
            "inputbooster.option.max_cps", "inputbooster.tip.max_cps",
            1.0, 20.0, 1.0,
            v -> Component.translatable("inputbooster.value.cps", (int) Math.round(v)),
            InputBoosterConfig.getMaxCps(), v -> InputBoosterConfig.setMaxCps((int) Math.round(v))));

        big(slider(
            "inputbooster.option.click_pitch", "inputbooster.tip.click_pitch",
            0.5, 2.0, 0.05,
            v -> Component.translatable("inputbooster.value.multiplier", round(v, 2)),
            InputBoosterConfig.getClickSoundPitch(), v -> InputBoosterConfig.setClickSoundPitch((float) v)));

        big(slider(
            "inputbooster.option.click_volume", "inputbooster.tip.click_volume",
            0.0, 1.0, 0.05,
            v -> Component.translatable("inputbooster.value.percent", (int) Math.round(v * 100)),
            InputBoosterConfig.getClickSoundVolume(), v -> InputBoosterConfig.setClickSoundVolume((float) v)));
    }

    private void addOverlaySection() {
        section("inputbooster.section.overlay");

        small(
            checkbox("inputbooster.option.hud_overlay", "inputbooster.tip.hud_overlay",
                InputBoosterConfig.isShowF3Info(), InputBoosterConfig::setShowF3Info),
            checkbox("inputbooster.option.keystrokes", "inputbooster.tip.keystrokes",
                InputBoosterConfig.isShowKeystrokes(), InputBoosterConfig::setShowKeystrokes));

        small(
            checkbox("inputbooster.option.action_bar", "inputbooster.tip.action_bar",
                InputBoosterConfig.isShowActionBar(), InputBoosterConfig::setShowActionBar));

        big(cycle(
            Component.translatable("inputbooster.option.overlay_position"),
            corner -> Component.translatable(CORNER_KEYS[corner]),
            InputBoosterConfig::getOverlayPosition,
            List.of(OverlayLayout.TOP_LEFT, OverlayLayout.TOP_RIGHT,
                OverlayLayout.BOTTOM_LEFT, OverlayLayout.BOTTOM_RIGHT),
            (button, corner) -> InputBoosterConfig.setOverlayPosition(corner)));

        big(slider(
            "inputbooster.option.overlay_scale", "inputbooster.tip.overlay_scale",
            0.5, 3.0, 0.1,
            v -> Component.translatable("inputbooster.value.multiplier", round(v, 1)),
            InputBoosterConfig.getOverlayScale(), v -> InputBoosterConfig.setOverlayScale((float) v)));

        big(slider(
            "inputbooster.option.overlay_opacity", "inputbooster.tip.overlay_opacity",
            0.0, 1.0, 0.05,
            v -> Component.translatable("inputbooster.value.percent", (int) Math.round(v * 100)),
            InputBoosterConfig.getOverlayOpacity(), v -> InputBoosterConfig.setOverlayOpacity((float) v)));
    }

    private void addDiagnosticsSection() {
        section("inputbooster.section.diagnostics");

        small(
            checkbox("inputbooster.option.safe_mode", "inputbooster.tip.safe_mode",
                InputBoosterConfig.isSafeModeEnabled(), InputBoosterConfig::setSafeModeEnabled),
            checkbox("inputbooster.option.debug_mode", "inputbooster.tip.debug_mode",
                InputBoosterConfig.isDebugMode(), InputBoosterConfig::setDebugMode));

        small(
            checkbox("inputbooster.option.event_log", "inputbooster.tip.event_log",
                InputBoosterConfig.isEventLogEnabled(), InputBoosterConfig::setEventLogEnabled),
            checkbox("inputbooster.option.key_conflict", "inputbooster.tip.key_conflict",
                InputBoosterConfig.isKeyConflictWarn(), InputBoosterConfig::setKeyConflictWarn));

        small(
            checkbox("inputbooster.option.replay", "inputbooster.tip.replay",
                InputBoosterConfig.isReplayEnabled(), InputBoosterConfig::setReplayEnabled),
            checkbox("inputbooster.option.per_server_profiles", "inputbooster.tip.per_server_profiles",
                InputBoosterConfig.isPerServerProfiles(), InputBoosterConfig::setPerServerProfiles));

        big(slider(
            "inputbooster.option.fps_check", "inputbooster.tip.fps_check",
            1.0, 100.0, 1.0,
            v -> Component.translatable("inputbooster.value.ticks", (int) Math.round(v)),
            (double) InputBoosterConfig.getFpsCheckInterval(), v -> InputBoosterConfig.setFpsCheckInterval((int) Math.round(v))));
    }

    private void addStatsSection() {
        section("inputbooster.section.stats");

        // One single-line widget per row: an options row is a fixed 25px tall,
        // so a multi-line block would be drawn over the rows underneath it.
        statLines.clear();
        for (int i = 0; i < STAT_LINE_COUNT; i++) {
            StringWidget line = new StringWidget(Component.empty(), font);
            line.setMaxWidth(Button.BIG_WIDTH);
            statLines.add(line);
            big(line);
        }

        sparkline = new CpsSparklineWidget(
            Component.translatable("inputbooster.option.cps_graph"), InputBoosterMod.sessionStats);
        big(sparkline);

        big(Button.builder(
            Component.translatable("inputbooster.button.reset_peak"),
            button -> {
                LatencyProfiler.resetPeak();
                refreshStats();
            })
            .width(Button.BIG_WIDTH)
            .tooltip(Tooltip.create(Component.translatable("inputbooster.tip.reset_peak")))
            .build());

        refreshStats();
    }

    private void addProfilesSection() {
        section("inputbooster.section.profiles");

        ProfileManager manager = InputBoosterMod.profileManager;
        if (manager == null) {
            // Mod initialisation has not finished (or failed); the old screen
            // threw a NullPointerException here.
            big(Button.builder(
                Component.translatable("inputbooster.profiles.unavailable"), button -> { })
                .width(Button.BIG_WIDTH)
                .build());
            return;
        }

        List<ProfileManager.Profile> profiles = manager.getProfiles();
        if (profiles.isEmpty()) {
            StringWidget empty = new StringWidget(
                Component.translatable("inputbooster.profiles.empty"), font);
            empty.setMaxWidth(Button.BIG_WIDTH);
            big(empty);
        }
        for (int i = 0; i < profiles.size(); i++) {
            ProfileManager.Profile profile = profiles.get(i);
            int index = i;
            boolean active = manager.getActiveIndex() == i;
            small(
                Button.builder(
                    Component.translatable(active
                        ? "inputbooster.button.load_active_profile"
                        : "inputbooster.button.load_profile", profile.name()),
                    button -> {
                        manager.loadProfile(profile.name(), this.minecraft);
                        rebuildWidgets();
                    })
                    .width(Button.SMALL_WIDTH)
                    .tooltip(Tooltip.create(Component.translatable("inputbooster.tip.load_profile")))
                    .build(),
                Button.builder(
                    Component.translatable("inputbooster.button.delete_profile"),
                    button -> {
                        manager.deleteProfile(index);
                        rebuildWidgets();
                    })
                    .width(Button.SMALL_WIDTH)
                    .tooltip(Tooltip.create(Component.translatable("inputbooster.tip.delete_profile")))
                    .build());
        }

        if (profiles.size() < ProfileManager.MAX_PROFILES) {
            for (int i = 0; i < QUICK_PROFILES.length; i += 2) {
                List<AbstractWidget> row = new ArrayList<>(2);
                for (int j = i; j < Math.min(i + 2, QUICK_PROFILES.length); j++) {
                    String name = QUICK_PROFILES[j];
                    row.add(Button.builder(
                        Component.translatable("inputbooster.button.quick_save", name),
                        button -> {
                            manager.saveProfile(name);
                            rebuildWidgets();
                        })
                        .width(Button.SMALL_WIDTH)
                        .tooltip(Tooltip.create(Component.translatable("inputbooster.tip.quick_save")))
                        .build());
                }
                list.addSmall(row);
            }
        }
    }

    // ── Live updates ────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (++ticksSinceStatsRefresh < STATS_REFRESH_TICKS) return;
        ticksSinceStatsRefresh = 0;
        refreshStats();
    }

    private void refreshStats() {
        if (statLines.isEmpty()) return;
        if (sparkline != null) sparkline.stats = InputBoosterMod.sessionStats;

        List<Component> lines = statsLines();
        for (int i = 0; i < statLines.size(); i++) {
            statLines.get(i).setMessage(i < lines.size() ? lines.get(i) : Component.empty());
        }
    }

    /** The session statistics, one component per options-list row. */
    private List<Component> statsLines() {
        List<Component> lines = new ArrayList<>(STAT_LINE_COUNT);
        SessionStats stats = InputBoosterMod.sessionStats;
        if (stats == null) {
            lines.add(Component.translatable("inputbooster.stats.unavailable"));
            return lines;
        }
        boolean bursting = InputBoosterMod.burstMode != null && InputBoosterMod.burstMode.isBursting();
        lines.add(Component.translatable("inputbooster.stats.started",
            stats.getSessionStartTime(), stats.getUptimeFormatted()));
        lines.add(Component.translatable("inputbooster.stats.recovered", stats.getTotalRecovered()));
        lines.add(Component.translatable("inputbooster.stats.missed", stats.getEstimatedMissedInputs()));
        lines.add(Component.literal(LatencyProfiler.formatForOverlay()));
        lines.add(Component.translatable("inputbooster.stats.poller",
            Component.translatable(stats.isPollingThreadAlive()
                ? "inputbooster.value.running"
                : "inputbooster.value.stopped")));
        lines.add(Component.translatable("inputbooster.stats.poll_rate",
            bursting ? 1000 : InputBoosterMod.currentPollHz));
        return lines;
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    /**
     * {@code removed()} runs on every path that leaves this screen — the Done
     * button, Escape, or another screen replacing it — so it is the one place
     * the configuration has to be flushed to disk. {@code super.removed()} is
     * still called first so vanilla keeps saving its own options.
     */
    @Override
    public void removed() {
        InputBoosterConfig.save();
        super.removed();
    }

    // ── Widget helpers ──────────────────────────────────────────────────────

    private void section(String key) {
        list.addHeader(Component.translatable(key));
    }

    private void big(AbstractWidget widget) {
        list.addBig(widget);
    }

    private void small(AbstractWidget... widgets) {
        list.addSmall(List.of(widgets));
    }

    private Checkbox checkbox(String labelKey, String tipKey, boolean value, Consumer<Boolean> setter) {
        return Checkbox.builder(Component.translatable(labelKey), font)
            .pos(0, 0)
            .maxWidth(Button.DEFAULT_WIDTH)
            .selected(value)
            .tooltip(Tooltip.create(Component.translatable(tipKey)))
            .onValueChange((box, selected) -> setter.accept(selected))
            .build();
    }

    private <T> CycleButton<T> cycle(
        Component label,
        Function<T, Component> valueName,
        java.util.function.Supplier<T> current,
        List<T> values,
        CycleButton.OnValueChange<T> onChange) {
        return CycleButton.<T>builder(valueName, current)
            .withValues(values)
            .create(0, 0, Button.BIG_WIDTH, Button.DEFAULT_HEIGHT, label, onChange);
    }

    private SettingSlider slider(
        String labelKey,
        String tipKey,
        double min,
        double max,
        double step,
        Function<Double, Component> format,
        double current,
        DoubleConsumer apply) {
        return slider(labelKey, tipKey, min, max, step, format, current, apply, null);
    }

    private SettingSlider slider(
        String labelKey,
        String tipKey,
        double min,
        double max,
        double step,
        Function<Double, Component> format,
        double current,
        DoubleConsumer apply,
        Runnable afterChange) {
        return new SettingSlider(
            Component.translatable(labelKey),
            Tooltip.create(Component.translatable(tipKey)),
            min, max, step, format, current, apply, afterChange);
    }

    private Button presetButton(int hz) {
        return Button.builder(
            Component.translatable("inputbooster.button.preset", hz),
            button -> {
                InputBoosterConfig.setPollRateHz(hz);
                applyManualPollRate();
                // Update the slider in place; rebuilding the whole screen would
                // throw away the player's scroll position in the options list.
                if (pollRateSlider != null) pollRateSlider.setCurrentValue(hz);
            })
            .width(Button.SMALL_WIDTH)
            .tooltip(Tooltip.create(Component.translatable("inputbooster.tip.preset")))
            .build();
    }

    private void updateManualPollAvailability() {
        boolean manual = !InputBoosterConfig.isPollRateAutoMode();
        for (AbstractWidget widget : manualPollWidgets) {
            widget.active = manual;
        }
    }

    private void applyManualPollRate() {
        if (InputBoosterConfig.isPollRateAutoMode()) return;
        int hz = InputBoosterConfig.getPollRateHz();
        InputBoosterMod.currentPollHz = hz;
        if (InputBoosterMod.pollingThread != null) {
            InputBoosterMod.pollingThread.setPollRateHz(hz);
        }
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    /** Index of the configured CPS limiter shape, defaulting to {@code FIXED}. */
    private static int cpsModeIndex() {
        String configured = InputBoosterConfig.getCpsMode();
        for (int i = 0; i < CPS_MODES.length; i++) {
            if (CPS_MODES[i].equals(configured)) return i;
        }
        return 0;
    }

    /**
     * Finds the vanilla options grid inside a screen's header/contents/footer
     * layout.
     *
     * <p>Public and static so {@code OptionsScreenMixin} can reach it: a lambda
     * there would compile to a <em>static</em> synthetic method inside the
     * mixin, which Mixin refuses to merge into its target ("contains non-private
     * static method"), while a method reference to this method generates no
     * synthetic member at all.
     *
     * @return the grid, or {@code null} when the screen does not use one
     */
    public static GridLayout findOptionsGrid(HeaderAndFooterLayout screenLayout) {
        GridLayout[] found = new GridLayout[1];
        screenLayout.visitChildren(element -> {
            if (element instanceof GridLayout grid) found[0] = grid;
        });
        return found[0];
    }

    /**
     * Adds {@code entry} to the options grid as its next free cell.
     *
     * <p>Being a real grid cell is what keeps the entry from colliding with the
     * vanilla buttons: the grid grows by one row and re-centres itself, so
     * there is nothing left underneath for the new button to overlap. Anchoring
     * it to the bottom of the content area instead drew it straight over the
     * last vanilla row on short windows.
     *
     * <p>The cell is derived from the grid's own contents rather than hard
     * coded: the entries sit in equally wide columns, so the number of distinct
     * left edges is the column count and the next free cell follows from the
     * entry count. That keeps working when a Minecraft version adds or removes
     * an options button.
     *
     * @return {@code false} when the grid has no children to measure, so the
     *         caller can fall back to a plain layout child
     */
    public static boolean addToOptionsGrid(GridLayout grid, AbstractWidget entry) {
        List<LayoutElement> existing = new ArrayList<>();
        grid.visitChildren(existing::add);
        if (existing.isEmpty()) return false;

        TreeSet<Integer> leftEdges = new TreeSet<>();
        for (LayoutElement element : existing) {
            leftEdges.add(element.getX());
        }
        int columns = leftEdges.size();
        int row = existing.size() / columns;
        int column = existing.size() % columns;

        grid.addChild(entry, row, column, grid.defaultCellSetting());
        return true;
    }

    /**
     * Fallback placement for the options entry, used only when the screen has
     * no options grid to join.
     *
     * <p>See {@link #addToOptionsGrid} for why the entry belongs in the grid
     * rather than at a fixed offset.
     */
    public static void anchorEntryToContentBottom(LayoutSettings settings) {
        settings.alignHorizontallyCenter().alignVerticallyBottom().paddingBottom(4);
    }

    // ── Custom widgets ──────────────────────────────────────────────────────

    /**
     * A single generic slider for every numeric setting.
     *
     * <p>The range is expressed in real units rather than a raw 0..1 fraction,
     * and the value is quantised to {@code step} on every change so dragging
     * can never write a value the configuration setter would have to clamp.
     */
    private static final class SettingSlider extends AbstractSliderButton {

        private final Component label;
        private final double min;
        private final double max;
        private final double step;
        private final Function<Double, Component> format;
        private final DoubleConsumer apply;
        private final Runnable afterChange;
        private double current;

        SettingSlider(
            Component label,
            Tooltip tooltip,
            double min,
            double max,
            double step,
            Function<Double, Component> format,
            double current,
            DoubleConsumer apply,
            Runnable afterChange) {
            super(0, 0, Button.BIG_WIDTH, Button.DEFAULT_HEIGHT, label, 0.0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.apply = apply;
            this.afterChange = afterChange;
            this.current = clampToStep(current);
            this.value = toFraction(this.current);
            setTooltip(tooltip);
            // The superclass constructor does not call updateMessage(), so the
            // label has to be rendered once here or the slider shows its raw
            // default until the player first drags it.
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("inputbooster.value.combined", label, format.apply(current)));
        }

        @Override
        protected void applyValue() {
            double raw = min + (max - min) * value;
            double next = clampToStep(raw);
            if (next == current) return;
            current = next;
            apply.accept(next);
            if (afterChange != null) afterChange.run();
            updateMessage();
        }

        /**
         * Moves the handle and the label without writing to the configuration.
         * Used by the preset buttons, which own the value themselves.
         */
        void setCurrentValue(double newValue) {
            this.current = clampToStep(newValue);
            this.value = toFraction(this.current);
            updateMessage();
        }

        private double clampToStep(double raw) {
            double stepped = Math.round((raw - min) / step) * step + min;
            stepped = Math.max(min, Math.min(max, stepped));
            // Kill the binary-floating-point dust introduced by the stepping so
            // the stored value stays stable across repeated drags.
            return Math.round(stepped * 1_000_000.0) / 1_000_000.0;
        }

        private double toFraction(double raw) {
            if (max <= min) return 0.0;
            return Math.max(0.0, Math.min(1.0, (raw - min) / (max - min)));
        }
    }

    /**
     * The 60-second CPS sparkline.
     *
     * <p>Draws with {@link GuiGraphicsExtractor#fill} so it goes through
     * Minecraft's deferred GUI render state and is therefore identical on the
     * OpenGL and Vulkan backends. The bar colour maths lives in
     * {@link OverlayLayout#cpsBarColor} so it can be unit tested without a
     * running game.
     */
    private static final class CpsSparklineWidget extends AbstractWidget {

        private static final int[] NO_HISTORY = new int[0];

        private SessionStats stats;

        CpsSparklineWidget(Component label, SessionStats stats) {
            super(0, 0, Button.BIG_WIDTH, ROW_CONTENT_HEIGHT, label);
            this.stats = stats;
            setTooltip(Tooltip.create(Component.translatable("inputbooster.tip.cps_graph")));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            int[] history = stats == null ? NO_HISTORY : stats.getCpsHistory();
            if (history.length == 0) return;

            int max = 1;
            for (int value : history) max = Math.max(max, value);

            int barWidth = Math.max(1, this.width / history.length);
            int graphHeight = this.height - 2;
            for (int i = 0; i < history.length; i++) {
                int barHeight = Math.round((float) history[i] / max * graphHeight);
                if (barHeight <= 0) continue;
                int x = this.getX() + i * barWidth;
                graphics.fill(x, this.getY() + this.height - 1 - barHeight,
                    x + barWidth - 1, this.getY() + this.height - 1,
                    OverlayLayout.cpsBarColor(history[i], InputBoosterConfig.getMaxCps()));
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}