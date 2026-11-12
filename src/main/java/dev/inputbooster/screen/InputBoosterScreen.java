package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.engine.ConfigurationManager;
import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.feature.OverlayLayout;
import dev.inputbooster.feature.SessionStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * InputBooster settings.
 *
 * <p>Seven sections in a left rail, one at a time, with a two-column control
 * grid. The window itself behaves like a window: drag the header to move it,
 * drag the bottom-right corner to resize it, and scroll the content with the
 * wheel or the scrollbar. Without that, anything below the fold on a small
 * window was simply unreachable.
 *
 * <p>Vanilla buttons and sliders are used deliberately: they keep Minecraft's
 * own input handling, narration and focus behaviour instead of a bespoke widget
 * that would need its own focus and accessibility handling.
 *
 * <p>Every control reads and writes {@link InputBoosterConfig}; nothing is
 * display-only. {@code Save & Close} writes atomically.
 */
public class InputBoosterScreen extends Screen {

    private enum Tab {
        OVERVIEW("Overview"),
        KEYBOARD("Keyboard"),
        MOUSE("Mouse"),
        OVERLAY("Overlay"),
        PERFORMANCE("Performance"),
        COMPATIBILITY("Compatibility"),
        ADVANCED("Advanced");

        final String label;
        Tab(String label) { this.label = label; }
    }

    private static final int PANEL_BG      = 0xF0141820;
    private static final int RAIL_BG       = 0xE00F1319;
    private static final int BORDER        = 0xFF2A3540;
    private static final int ACCENT        = 0xFF55D6E8;
    private static final int TEXT          = 0xFFE6EDF3;
    private static final int MUTED         = 0xFF93A1AE;
    private static final int GOOD          = 0xFF64D98B;
    private static final int WARN          = 0xFFFFB454;
    private static final int DANGER        = 0xFFFF6B6B;

    private static final int RAIL_WIDTH = 100;
    private static final int HEADER_H = 44;
    private static final int FOOTER_H = 34;
    private static final int MIN_PANEL_W = 380;
    private static final int MIN_PANEL_H = 260;
    private static final int SCROLL_STEP = 24;
    private static final int RESIZE_GRIP = 12;

    private final Screen parent;
    private Tab tab = Tab.OVERVIEW;
    private boolean hasChanges;

    private final List<TextLine> textLines = new ArrayList<>();
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private int contentX, contentRight, contentTop, rowGap, panelX, panelY, panelW, panelH;

    /**
     * Window geometry survives closing the screen, but not a game restart: it is
     * a view preference, not configuration, and putting it in the config file
     * would add four keys that need migration for no functional gain.
     */
    private static int savedPanelW, savedPanelH;
    private static int savedPanelX = Integer.MIN_VALUE;
    private static int savedPanelY = Integer.MIN_VALUE;

    private int scrollOffset;
    private int maxScroll;
    private boolean settlingScroll;

    private boolean draggingWindow;
    private boolean resizingWindow;
    private boolean draggingScrollbar;
    private int grabX, grabY;

    public InputBoosterScreen(Screen parent) {
        super(Component.literal("InputBooster"));
        this.parent = parent;
    }

    // ── Layout ───────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        textLines.clear();
        contentWidgets.clear();

        int maxW = Math.max(MIN_PANEL_W, this.width - 16);
        int maxH = Math.max(MIN_PANEL_H, this.height - 8);
        panelW = savedPanelW == 0 ? Math.min(560, maxW) : clamp(savedPanelW, MIN_PANEL_W, maxW);
        panelH = savedPanelH == 0 ? Math.min(460, maxH) : clamp(savedPanelH, MIN_PANEL_H, maxH);
        panelW = Math.min(panelW, maxW);
        panelH = Math.min(panelH, maxH);
        panelX = savedPanelX == Integer.MIN_VALUE
            ? (this.width - panelW) / 2
            : clamp(savedPanelX, 0, Math.max(0, this.width - panelW));
        panelY = savedPanelY == Integer.MIN_VALUE
            ? (this.height - panelH) / 2
            : clamp(savedPanelY, 0, Math.max(0, this.height - panelH));
        rememberGeometry();

        contentX = panelX + RAIL_WIDTH + 14;
        contentRight = panelX + panelW - 16;
        contentTop = panelY + HEADER_H + 12;
        rowGap = panelH < 300 ? 2 : 4;

        switch (tab) {
            case OVERVIEW -> buildOverview();
            case KEYBOARD -> buildKeyboard();
            case MOUSE -> buildMouse();
            case OVERLAY -> buildOverlay();
            case PERFORMANCE -> buildPerformance();
            case COMPATIBILITY -> buildCompatibility();
            case ADVANCED -> buildAdvanced();
        }

        buildRail();
        buildFooter();
        measureContent();

        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
            if (!settlingScroll) {
                // The extent shrinks while scrolling or resizing: rebuild once
                // with the corrected offset rather than drawing stale positions.
                settlingScroll = true;
                rebuildWidgets();
                settlingScroll = false;
                return;
            }
        }
        cullContent();
    }

    private void rememberGeometry() {
        savedPanelW = panelW;
        savedPanelH = panelH;
        savedPanelX = panelX;
        savedPanelY = panelY;
    }

    private int contentBottom() {
        return panelY + panelH - FOOTER_H - 4;
    }

    private int viewportHeight() {
        return Math.max(1, contentBottom() - contentTop);
    }

    /** Content height is measured with the scroll offset added back, so it does not depend on scroll. */
    private void measureContent() {
        int lowest = contentTop;
        for (AbstractWidget widget : contentWidgets) {
            lowest = Math.max(lowest, widget.getY() + widget.getHeight() + scrollOffset);
        }
        for (TextLine line : textLines) {
            lowest = Math.max(lowest, line.y + scrollOffset + 10);
        }
        int contentHeight = lowest - contentTop + 8;
        maxScroll = Math.max(0, contentHeight - viewportHeight());
        scrollOffset = clamp(scrollOffset, 0, maxScroll);
    }

    /** Widgets are culled rather than clipped: no partial rows bleeding into the header. */
    private void cullContent() {
        for (AbstractWidget widget : contentWidgets) {
            widget.visible = widget.getY() >= contentTop - 1
                && widget.getY() + widget.getHeight() <= contentBottom() + 1;
        }
    }

    private void buildFooter() {
        addRenderableWidget(Button.builder(Component.literal("§aSave & Close"), b -> {
            InputBoosterConfig.save();
            hasChanges = false;
            if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
        }).bounds(panelX + 14, panelY + panelH - 26, 120, 20).build());

        addRenderableWidget(Button.builder(Component.literal("§7Close"), b -> onClose())
            .bounds(panelX + panelW - 94, panelY + panelH - 26, 80, 20).build());
    }

    private <T extends AbstractWidget> T addContent(T widget) {
        addRenderableWidget(widget);
        contentWidgets.add(widget);
        return widget;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void buildRail() {
        int y = panelY + 48;
        for (Tab value : Tab.values()) {
            final Tab target = value;
            Button b = Button.builder(Component.literal((target == tab ? "§b" : "§7") + target.label), btn -> {
                tab = target;
                scrollOffset = 0;
                rebuildWidgets();
            }).bounds(panelX + 8, y, RAIL_WIDTH - 16, 20).build();
            b.active = target != tab;
            addRenderableWidget(b);
            y += 24;
        }
    }

    private int cellWidth() {
        return (contentRight - contentX - 8) / 2;
    }

    private int cellX(int column) {
        return column == 0 ? contentX : contentX + cellWidth() + 8;
    }

    private int rowHeight() {
        return this.height < 300 ? 18 : 21;
    }

    private int rowY(int row) {
        return contentTop + row * (rowHeight() + rowGap) - scrollOffset;
    }

    private void section(int row, String title) {
        textLines.add(new TextLine(contentX, rowY(row) - 10, title.toUpperCase(java.util.Locale.ROOT), MUTED));
    }

    private void note(int row, int column, String text, int color) {
        textLines.add(new TextLine(cellX(column), rowY(row) + 6, text, color));
    }

    // ── Overview ─────────────────────────────────────────────────────────────

    private void buildOverview() {
        int row = 0;
        section(row++, "Status");
        int statusLines = drawStatus();
        row += Math.max(2, statusLines / 2 + 1);

        section(row++, "Active features");
        row += drawFeatureSummary();

        section(row++, "Session");
        note(row, 0, "Recovered: §a" + InputBoosterMod.recoveredInputs.get(), TEXT);
        note(row++, 1, "Max CPS: §e" + InputBoosterConfig.getMaxCps(), TEXT);

        int y = rowY(row);
        addContent(Button.builder(Component.literal("§bProfiles..."), b -> {
            if (this.minecraft != null) this.minecraft.setScreenAndShow(new ProfilesScreen(this));
        }).bounds(contentX, y, cellWidth(), 20).build());

        addContent(Button.builder(Component.literal("§b" + ConfigurationManager.engineModeLabel() + " engine"),
            b -> {
                ConfigurationManager.setEngineMode((ConfigurationManager.engineMode() + 1) % 3);
                applyEngineMode();
                b.setMessage(Component.literal("§b" + ConfigurationManager.engineModeLabel() + " engine"));
                hasChanges = true;
            }).bounds(cellX(1), y, cellWidth(), 20).build());
    }

    private int drawStatus() {
        int y = rowY(1);
        int lineHeight = 10;
        int column = 0;
        int x = contentX;
        int lines = 0;
        List<String> status = new ArrayList<>();
        var engine = InputBoosterMod.inputEngine;
        status.add("Engine " + (engine == null ? "starting" : engine.mode().label));
        status.add("Poll thread " + (InputBoosterMod.pollingThread != null && InputBoosterMod.pollingThread.isAlive()
            ? "running" : "stopped"));
        status.add("Rate " + InputBoosterMod.currentPollHz + " Hz");
        status.add("Queue " + dev.inputbooster.InputActionQueue.size()
            + "/" + dev.inputbooster.InputActionQueue.capacity());
        status.add("Overlay " + (InputBoosterConfig.isShowF3Info() ? "on" : "off")
            + " (" + ConfigurationManager.overlayModeLabel().toLowerCase(java.util.Locale.ROOT) + ")");
        if (engine != null) {
            status.add("Keys " + engine.diagnostics().keyboardEvents()
                + " / Mouse " + engine.diagnostics().mouseEvents());
        }
        if (InputBoosterMod.compatibilityManager != null) {
            var mods = InputBoosterMod.compatibilityManager.detectedMods();
            status.add(mods.isEmpty() ? "No known input mods" : "With " + String.join(", ", mods));
        }
        for (String line : status) {
            textLines.add(new TextLine(x, y + lines * lineHeight, line, TEXT));
            lines++;
            if (lines == 4) { column = 1; x = cellX(1); lines = 0; y = rowY(1); }
            if (column == 1 && lines == 3) break;
        }
        return 7;
    }

    private int drawFeatureSummary() {
        int y = rowY(3);
        int lineHeight = 10;
        String[] features = {
            "Sprint " + onOff(InputBoosterConfig.isSprintFixEnabled()),
            "Auto-sprint " + onOff(InputBoosterConfig.isAutoSprintEnabled()),
            "W-tap " + onOff(InputBoosterConfig.isWTapAssistEnabled()),
            "Strafe " + onOff(InputBoosterConfig.isAutoStrafeEnabled()),
            "CPS cap " + onOff(InputBoosterConfig.isCpsLimiterEnabled()),
            "Anti-idle " + onOff(InputBoosterConfig.isAntiIdleEnabled()),
            "Replay " + onOff(InputBoosterConfig.isReplayEnabled()),
            "Combo keys " + onOff(InputBoosterConfig.isComboKeysEnabled()),
        };
        for (int i = 0; i < features.length; i++) {
            int column = i % 2;
            int line = i / 2;
            textLines.add(new TextLine(cellX(column), y + line * lineHeight, features[i], TEXT));
        }
        return 2;
    }

    private static String onOff(boolean value) {
        return value ? "§aon" : "§7off";
    }

    // ── Keyboard ─────────────────────────────────────────────────────────────

    private void buildKeyboard() {
        int row = 0;
        section(row++, "Processing");
        toggle(row++, 0, "Repeat guard", InputBoosterConfig.isKeyboardRepeatGuard(),
            InputBoosterConfig::setKeyboardRepeatGuard);
        toggle(row++, 1, "Combo keys (Ctrl+1-5)", InputBoosterConfig.isComboKeysEnabled(),
            InputBoosterConfig::setComboKeysEnabled);
        action(row++, 0, "Engine: " + ConfigurationManager.engineModeLabel(), () -> {
            ConfigurationManager.setEngineMode((ConfigurationManager.engineMode() + 1) % 3);
            applyEngineMode();
            rebuildWidgets();
        });
        action(row++, 1, "Resync held keys", () -> {
            if (InputBoosterMod.inputEngine != null) InputBoosterMod.inputEngine.reset();
            if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Manual key resync");
            rebuildWidgets();
        });

        section(row++, "Bindings");
        var processor = InputBoosterMod.inputEngine == null ? null : InputBoosterMod.inputEngine.keyBindings();
        int unresolved = processor == null ? 0 : processor.unresolved();
        note(row, 0, unresolved == 0 ? "§aAll 12 bindings resolved" : "§e" + unresolved + " binding(s) using defaults", TEXT);
        note(row++, 1, "§7Read from vanilla KeyMapping", MUTED);

        section(row++, "Routing");
        note(row, 0, "§7Vanilla still receives every event", MUTED);
        note(row, 1, "§7Only tracked keys produce actions", MUTED);
        row++;
        action(row, 0, "Mouse settings", () -> { tab = Tab.MOUSE; rebuildWidgets(); });
        action(row, 1, "Compatibility", () -> { tab = Tab.COMPATIBILITY; rebuildWidgets(); });
    }

    // ── Mouse ────────────────────────────────────────────────────────────────

    private void buildMouse() {
        int row = 0;
        section(row++, "Clicks");
        toggle(row++, 0, "CPS limiter", InputBoosterConfig.isCpsLimiterEnabled(),
            InputBoosterConfig::setCpsLimiterEnabled);
        slider(row++, 1, "Max CPS", 1, 20, 1, InputBoosterConfig.getMaxCps(),
            value -> String.valueOf(value), InputBoosterConfig::setMaxCps);
        slider(row++, 0, "Click dedupe", 0, 100, 5, InputBoosterConfig.getMouseClickDedupeMs(),
            value -> value + " ms", InputBoosterConfig::setMouseClickDedupeMs);
        slider(row++, 1, "Scroll debounce", 0, 200, 5, InputBoosterConfig.getMouseScrollDebounceMs(),
            value -> value + " ms", InputBoosterConfig::setMouseScrollDebounceMs);

        section(row++, "Mode");
        note(row, 0, "§7Mode: §e" + InputBoosterConfig.getCpsMode(), TEXT);
        note(row++, 1, "§7Set cps_mode in the config", MUTED);

        section(row++, "Click feedback");
        toggle(row++, 0, "Click sounds", InputBoosterConfig.isClickSoundsEnabled(),
            InputBoosterConfig::setClickSoundsEnabled);
        slider(row++, 1, "Volume", 0, 100, 5, Math.round(InputBoosterConfig.getClickSoundVolume() * 100),
            value -> value + "%", value -> InputBoosterConfig.setClickSoundVolume(value / 100f));
        slider(row, 0, "Pitch", 50, 200, 5, Math.round(InputBoosterConfig.getClickSoundPitch() * 100),
            value -> String.format(java.util.Locale.ROOT, "%.2fx", value / 100f),
            value -> InputBoosterConfig.setClickSoundPitch(value / 100f));
    }

    // ── Overlay ──────────────────────────────────────────────────────────────

    private void buildOverlay() {
        int row = 0;
        section(row++, "Visibility");
        toggle(row++, 0, "Overlay enabled", InputBoosterConfig.isShowF3Info(),
            InputBoosterConfig::setShowF3Info);
        action(row++, 1, "Mode: " + ConfigurationManager.overlayModeLabel(), () -> {
            ConfigurationManager.setOverlayMode(ConfigurationManager.overlayMode() == 0 ? 1 : 0);
            rebuildWidgets();
        });
        action(row++, 0, "Position: " + prettyPosition(), () -> {
            InputBoosterConfig.setOverlayPosition((InputBoosterConfig.getOverlayPosition() + 1) % 4);
            rebuildWidgets();
        });
        toggle(row++, 1, "Keystrokes overlay", InputBoosterConfig.isShowKeystrokes(),
            InputBoosterConfig::setShowKeystrokes);

        section(row++, "Appearance");
        slider(row++, 0, "Scale", 50, 300, 10, Math.round(InputBoosterConfig.getOverlayScale() * 100),
            value -> String.format(java.util.Locale.ROOT, "%.1fx", value / 100f),
            value -> InputBoosterConfig.setOverlayScale(value / 100f));
        slider(row++, 1, "Opacity", 0, 100, 5, Math.round(InputBoosterConfig.getOverlayOpacity() * 100),
            value -> value + "%", value -> InputBoosterConfig.setOverlayOpacity(value / 100f));

        section(row++, "Content");
        toggle(row++, 0, "Engine line", InputBoosterConfig.isOverlayShowEngine(),
            InputBoosterConfig::setOverlayShowEngine);
        toggle(row++, 1, "Event counters", InputBoosterConfig.isOverlayShowEvents(),
            InputBoosterConfig::setOverlayShowEvents);
        toggle(row++, 0, "Dispatch timing", InputBoosterConfig.isOverlayShowOverhead(),
            InputBoosterConfig::setOverlayShowOverhead);
        action(row, 1, "Preview note", () -> {
            if (InputBoosterMod.eventLog != null) {
                InputBoosterMod.eventLog.add("Overlay mode: " + ConfigurationManager.overlayModeLabel());
            }
            rebuildWidgets();
        });
    }

    private String prettyPosition() {
        int pos = InputBoosterConfig.getOverlayPosition();
        return switch (pos) {
            case 1 -> "Top right";
            case 2 -> "Bottom left";
            case 3 -> "Bottom right";
            default -> "Top left";
        };
    }

    // ── Performance ──────────────────────────────────────────────────────────

    private void buildPerformance() {
        int row = 0;
        section(row++, "Polling");
        action(row++, 0, "Mode: " + (InputBoosterConfig.isPollRateAutoMode() ? "Automatic" : "Manual"), () -> {
            InputBoosterConfig.setPollRateAutoMode(!InputBoosterConfig.isPollRateAutoMode());
            applyPollRate();
            rebuildWidgets();
        });
        toggle(row++, 1, "Burst mode", InputBoosterConfig.isBurstModeEnabled(),
            InputBoosterConfig::setBurstModeEnabled);
        slider(row++, 0, "Poll rate", 60, 1000, 10, InputBoosterConfig.getPollRateHz(),
            value -> value + " Hz", value -> {
                InputBoosterConfig.setPollRateHz(value);
                applyPollRate();
            });
        slider(row++, 1, "FPS check", 1, 100, 1, InputBoosterConfig.getFpsCheckInterval(),
            value -> value + " ticks", InputBoosterConfig::setFpsCheckInterval);

        section(row++, "Presets");
        int[] presets = {100, 200, 350, 500, 750, 1000};
        int presetWidth = (contentRight - contentX - 10) / 6;
        for (int i = 0; i < presets.length; i++) {
            final int hz = presets[i];
            addContent(Button.builder(Component.literal(hz + ""), b -> {
                InputBoosterConfig.setPollRateAutoMode(false);
                InputBoosterConfig.setPollRateHz(hz);
                applyPollRate();
                hasChanges = true;
                rebuildWidgets();
            }).bounds(contentX + i * (presetWidth + 2), rowY(row), presetWidth, 20).build());
        }
        row++;
        note(row++, 0, "§7Presets switch to manual mode", MUTED);

        section(row++, "Session counters");
        var diagnostics = InputBoosterMod.inputEngine == null ? null : InputBoosterMod.inputEngine.diagnostics();
        note(row, 0, "Actions: §a" + (diagnostics == null ? 0 : diagnostics.actionsSubmitted()), TEXT);
        note(row++, 1, "Dropped: §e" + (diagnostics == null ? 0 : diagnostics.actionsDropped()), TEXT);
        note(row, 0, String.format(java.util.Locale.ROOT, "Dispatch: §e%.2f µs",
            diagnostics == null ? 0.0 : diagnostics.dispatchMicros()), TEXT);
        note(row++, 1, "Peak drain: §e" + LatencyProfiler.formatForOverlay().replace("§7", ""), MUTED);
        action(row, 0, "Reset counters", () -> {
            if (diagnostics != null) diagnostics.reset();
            LatencyProfiler.resetPeak();
            rebuildWidgets();
        });
        action(row, 1, "Force sync", () -> {
            if (InputBoosterMod.inputEngine != null) InputBoosterMod.inputEngine.reset();
            rebuildWidgets();
        });
    }

    // ── Compatibility ────────────────────────────────────────────────────────

    private void buildCompatibility() {
        int row = 0;
        section(row++, "Input mods");
        var compat = InputBoosterMod.compatibilityManager;
        List<String> mods = compat == null ? List.of() : compat.detectedMods();
        if (mods.isEmpty()) {
            note(row++, 0, "§aNo known input mods detected", TEXT);
        } else {
            for (String mod : mods) {
                note(row++, 0, "§e" + mod, TEXT);
            }
        }
        note(row++, 0, compat != null && compat.eventSourceActive()
            ? "§aEvent hooks active" : "§7Event hooks not seen yet", TEXT);

        section(row++, "Behaviour");
        toggle(row++, 0, "Fallback with input mods", InputBoosterConfig.isCompatFallback(),
            InputBoosterConfig::setCompatFallback);
        action(row++, 1, "Engine: " + ConfigurationManager.engineModeLabel(), () -> {
            ConfigurationManager.setEngineMode((ConfigurationManager.engineMode() + 1) % 3);
            applyEngineMode();
            rebuildWidgets();
        });
        note(row++, 0, "§7Fallback keeps polling only", MUTED);
        note(row++, 1, "§7Vanilla handlers always run", MUTED);

        section(row++, "Alpha keyboard handler");
        note(row, 0, InputBoosterConfig.isCustomKeyboardHandlerEnabled() ? "§cEnabled" : "§7Disabled", TEXT);
        action(row++, 1, InputBoosterConfig.isCustomKeyboardHandlerEnabled() ? "Turn off" : "Review warning",
            () -> {
                if (InputBoosterConfig.isCustomKeyboardHandlerEnabled()) {
                    InputBoosterConfig.setCustomKeyboardHandlerEnabled(false);
                    hasChanges = true;
                    rebuildWidgets();
                } else if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(new KeyboardHandlerWarningScreen(this, () -> {
                        InputBoosterConfig.setCustomKeyboardHandlerEnabled(true);
                        hasChanges = true;
                    }));
                }
            });
        action(row, 0, "Write status to log", () -> {
            if (compat != null) InputBoosterMod.LOGGER.info("[Compat] {}", compat.statusLine());
            if (InputBoosterMod.eventLog != null) InputBoosterMod.eventLog.add("Compatibility status logged");
            rebuildWidgets();
        });
    }

    // ── Advanced ─────────────────────────────────────────────────────────────

    private void buildAdvanced() {
        int row = 0;
        section(row++, "Safety");
        toggle(row++, 0, "Safe mode", InputBoosterConfig.isSafeModeEnabled(),
            InputBoosterConfig::setSafeModeEnabled);
        toggle(row++, 1, "Event log", InputBoosterConfig.isEventLogEnabled(),
            InputBoosterConfig::setEventLogEnabled);
        toggle(row++, 0, "Debug mode", InputBoosterConfig.isDebugMode(),
            InputBoosterConfig::setDebugMode);
        toggle(row++, 1, "Key conflict warning", InputBoosterConfig.isKeyConflictWarn(),
            InputBoosterConfig::setKeyConflictWarn);

        section(row++, "Profiles");
        toggle(row++, 0, "Per-server profiles", InputBoosterConfig.isPerServerProfiles(),
            InputBoosterConfig::setPerServerProfiles);
        action(row++, 1, "Manage profiles", () -> {
            if (this.minecraft != null) this.minecraft.setScreenAndShow(new ProfilesScreen(this));
        });

        section(row++, "Diagnostics");
        var diagnostics = InputBoosterMod.inputEngine == null ? null : InputBoosterMod.inputEngine.diagnostics();
        note(row, 0, "Keys: §a" + (diagnostics == null ? 0 : diagnostics.keyboardEvents()), TEXT);
        note(row++, 1, "Mouse: §a" + (diagnostics == null ? 0 : diagnostics.mouseEvents()), TEXT);
        note(row, 0, "Duplicates: §e" + (diagnostics == null ? 0 : diagnostics.duplicatesSuppressed()), TEXT);
        note(row++, 1, "Repeats: §e" + (diagnostics == null ? 0 : diagnostics.repeatsIgnored()), TEXT);

        action(row++, 0, "§cReset engine & overlay", () -> {
            ConfigurationManager.resetEngineAndOverlay();
            applyEngineMode();
            hasChanges = true;
            rebuildWidgets();
        });
        action(row, 1, "Reset latency peak", () -> {
            LatencyProfiler.resetPeak();
            rebuildWidgets();
        });
    }

    // ── Widgets ──────────────────────────────────────────────────────────────

    private void toggle(int row, int column, String label, boolean initial, Consumer<Boolean> setter) {
        addContent(Button.builder(toggleText(label, initial), b -> {
            // The button's own message is the state: no shadow copy that can
            // drift from the config after a reset or a profile load.
            boolean value = !b.getMessage().getString().contains("§aON");
            setter.accept(value);
            b.setMessage(toggleText(label, value));
            hasChanges = true;
        }).bounds(cellX(column), rowY(row), cellWidth(), 20).build());
    }

    private void action(int row, int column, String label, Runnable action) {
        addContent(Button.builder(Component.literal(label), b -> action.run())
            .bounds(cellX(column), rowY(row), cellWidth(), 20).build());
    }

    private void slider(int row, int column, String label, int min, int max, int step, int value,
                        IntFunction<String> format, IntConsumer setter) {
        addContent(new SettingSlider(cellX(column), rowY(row), cellWidth(), 20,
            label, min, max, step, value, format, setter));
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "§aON" : "§7OFF"));
    }

    private void applyPollRate() {
        if (InputBoosterConfig.isPollRateAutoMode()) return;
        int hz = InputBoosterConfig.getPollRateHz();
        InputBoosterMod.currentPollHz = hz;
        if (InputBoosterMod.pollingThread != null) InputBoosterMod.pollingThread.setPollRateHz(hz);
    }

    private void applyEngineMode() {
        if (InputBoosterMod.inputEngine == null || InputBoosterMod.compatibilityManager == null) return;
        InputBoosterMod.inputEngine.setMode(InputBoosterMod.compatibilityManager.resolveMode());
        hasChanges = true;
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // The screen background is already drawn by Screen.extractRenderState;
        // this dims it without a second blur pass.
        ctx.fill(0, 0, this.width, this.height, 0xC00A0D11);

        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + 1, BORDER);
        ctx.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, BORDER);
        ctx.fill(panelX, panelY, panelX + 1, panelY + panelH, BORDER);
        ctx.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, BORDER);

        ctx.text(this.font, "§b§lInputBooster", panelX + 14, panelY + 10, TEXT);
        ctx.text(this.font, "§7v" + InputBoosterMod.MOD_VERSION, panelX + 14 + this.font.width("§b§lInputBooster") + 8,
            panelY + 10, MUTED);
        ctx.text(this.font, "§7" + tabSubtitle(), panelX + 14, panelY + 24, MUTED);

        ctx.fill(panelX + 1, panelY + 40, panelX + panelW - 1, panelY + 42, 0x3355D6E8);
        ctx.fill(panelX + 1, panelY + 42, panelX + RAIL_WIDTH, panelY + panelH - 34, RAIL_BG);

        int railY = panelY + 48 + tab.ordinal() * 24;
        ctx.fill(panelX + 2, railY, panelX + 5, railY + 20, ACCENT);

        for (TextLine line : textLines) {
            // Lines are culled to the viewport rather than clipped. The scissor
            // stack is private in the NeoForge Minecraft artifact, and drawing
            // a line only when it fits keeps both loaders on the same code.
            if (line.y < contentTop - 1 || line.y + 9 > contentBottom() + 1) continue;
            ctx.text(this.font, line.text, line.x, line.y, line.color);
        }

        if (tab == Tab.OVERVIEW) drawSparkline(ctx);
        drawScrollbar(ctx);
        drawResizeGrip(ctx);

        ctx.fill(panelX + 1, panelY + panelH - 34, panelX + panelW - 1, panelY + panelH - 33, BORDER);
        if (hasChanges) {
            ctx.text(this.font, "§eUnsaved changes", panelX + 140, panelY + panelH - 20, WARN);
        }
        if (InputBoosterConfig.isCustomKeyboardHandlerEnabled()) {
            ctx.text(this.font, "§cCustom keyboard handler is ALPHA", panelX + 140, panelY + panelH - 32, DANGER);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    // ── Window manipulation ──────────────────────────────────────────────────

    private int scrollbarX() {
        return panelX + panelW - 9;
    }

    private int scrollbarTop() {
        return contentTop;
    }

    private int scrollbarHeight() {
        return viewportHeight();
    }

    private int scrollbarThumbHeight() {
        if (maxScroll == 0) return scrollbarHeight();
        int total = viewportHeight() + maxScroll;
        return Math.max(24, (int) (scrollbarHeight() * (viewportHeight() / (double) total)));
    }

    private int scrollbarThumbTop() {
        if (maxScroll == 0) return scrollbarTop();
        int travel = Math.max(0, scrollbarHeight() - scrollbarThumbHeight());
        return scrollbarTop() + (int) Math.round(travel * (scrollOffset / (double) maxScroll));
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return maxScroll > 0
            && mouseX >= scrollbarX() - 3 && mouseX <= scrollbarX() + 5
            && mouseY >= scrollbarTop() && mouseY <= scrollbarTop() + scrollbarHeight();
    }

    private boolean inResizeGrip(double mouseX, double mouseY) {
        return mouseX >= panelX + panelW - RESIZE_GRIP && mouseX <= panelX + panelW
            && mouseY >= panelY + panelH - RESIZE_GRIP && mouseY <= panelY + panelH;
    }

    private boolean inHeader(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX < panelX + panelW
            && mouseY >= panelY && mouseY < panelY + HEADER_H;
    }

    private void scrollToPointer(double mouseY) {
        int travel = scrollbarHeight() - scrollbarThumbHeight();
        if (travel <= 0) {
            scrollOffset = 0;
        } else {
            double ratio = (mouseY - scrollbarTop() - scrollbarThumbHeight() / 2.0) / travel;
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            scrollOffset = clamp((int) Math.round(ratio * maxScroll), 0, maxScroll);
        }
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0) {
            if (inResizeGrip(mouseX, mouseY)) {
                resizingWindow = true;
                return true;
            }
            if (overScrollbar(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollToPointer(mouseY);
                return true;
            }
            if (inHeader(mouseX, mouseY)) {
                draggingWindow = true;
                grabX = (int) mouseX - panelX;
                grabY = (int) mouseY - panelY;
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (resizingWindow) {
            panelW = clamp((int) mouseX - panelX, MIN_PANEL_W,
                Math.max(MIN_PANEL_W, this.width - panelX));
            panelH = clamp((int) mouseY - panelY, MIN_PANEL_H,
                Math.max(MIN_PANEL_H, this.height - panelY));
            rememberGeometry();
            rebuildWidgets();
            return true;
        }
        if (draggingWindow) {
            panelX = clamp((int) mouseX - grabX, 0, Math.max(0, this.width - panelW));
            panelY = clamp((int) mouseY - grabY, 0, Math.max(0, this.height - panelH));
            rememberGeometry();
            rebuildWidgets();
            return true;
        }
        if (draggingScrollbar) {
            scrollToPointer(mouseY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && (resizingWindow || draggingWindow || draggingScrollbar)) {
            resizingWindow = false;
            draggingWindow = false;
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0
            && mouseX >= panelX && mouseX <= panelX + panelW
            && mouseY >= contentTop && mouseY <= contentBottom()) {
            int next = clamp(scrollOffset - (int) Math.round(scrollY * SCROLL_STEP), 0, maxScroll);
            if (next != scrollOffset) {
                scrollOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawScrollbar(GuiGraphicsExtractor ctx) {
        if (maxScroll <= 0) return;
        int x = scrollbarX();
        int top = scrollbarTop();
        int height = scrollbarHeight();
        ctx.fill(x, top, x + 3, top + height, 0x40000000);
        int thumbTop = scrollbarThumbTop();
        int thumbHeight = scrollbarThumbHeight();
        ctx.fill(x, thumbTop, x + 3, thumbTop + thumbHeight,
            draggingScrollbar ? ACCENT : 0xCC55D6E8);
    }

    private void drawResizeGrip(GuiGraphicsExtractor ctx) {
        int right = panelX + panelW;
        int bottom = panelY + panelH;
        for (int i = 0; i < 3; i++) {
            int offset = 4 + i * 4;
            ctx.fill(right - offset - 3, bottom - 4, right - offset, bottom - 3, BORDER);
            ctx.fill(right - 4, bottom - offset - 3, right - 3, bottom - offset, BORDER);
        }
    }

    private void drawSparkline(GuiGraphicsExtractor ctx) {
        SessionStats stats = InputBoosterMod.sessionStats;
        if (stats == null) return;
        int[] history = stats.getCpsHistory();
        int w = contentRight - contentX;
        int h = 16;
        int y = panelY + panelH - 60;
        int max = 1;
        for (int value : history) if (value > max) max = value;
        int barWidth = Math.max(1, w / history.length);
        int color = InputBoosterConfig.isCpsLimiterEnabled() ? GOOD : MUTED;
        for (int i = 0; i < history.length; i++) {
            int barHeight = (int) ((double) history[i] / max * h);
            if (barHeight <= 0) continue;
            int x = contentX + i * barWidth;
            ctx.fill(x, y + h - barHeight, x + barWidth - 1, y + h, color);
        }
        ctx.text(this.font, "§7CPS, last 60 s", contentX, y - 10, MUTED);
    }

    private String tabSubtitle() {
        return switch (tab) {
            case OVERVIEW -> "Live state, active features, session summary";
            case KEYBOARD -> "Key processing, bindings and routing";
            case MOUSE -> "Clicks, the CPS cap and click feedback";
            case OVERLAY -> "Visibility, placement and content";
            case PERFORMANCE -> "Polling, burst mode and counters";
            case COMPATIBILITY -> "Other input mods and fallback behaviour";
            case ADVANCED -> "Safety, profiles, diagnostics and reset";
        };
    }

    @Override
    public void onClose() {
        if (hasChanges) InputBoosterConfig.save();
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    private record TextLine(int x, int y, String text, int color) {}

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
            }
            updateMessage();
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
