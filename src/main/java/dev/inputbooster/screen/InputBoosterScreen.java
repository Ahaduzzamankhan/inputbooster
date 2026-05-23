package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.feature.LatencyProfiler;
import dev.inputbooster.feature.ProfileManager;
import dev.inputbooster.feature.SessionStats;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.List;

public class InputBoosterScreen extends Screen {

    private final Screen parent;
    private boolean hasChanges = false;
    private int currentTab = 0;
    private static final String[] TAB_LABELS = {"§ePoll Rate","§aFeatures","§8Advanced","§bStats","§dProfiles"};
    private static final int TAB_COUNT = 5;

    private Button modeButton;
    private PollRateSlider pollSlider;
    private Button[] presetButtons;

    public InputBoosterScreen(Screen parent) {
        super(Component.literal("§b§lInputBooster§r §8v" + InputBoosterMod.MOD_VERSION));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2, bw = 200, bh = 20, gap = 24, top = 50;

        int tabW  = Math.min(60, (this.width - 20) / TAB_COUNT);
        int tabsW = tabW * TAB_COUNT + (TAB_COUNT - 1) * 4;
        int tabX0 = cx - tabsW / 2;

        for (int t = 0; t < TAB_COUNT; t++) {
            final int tab = t;
            int tx = tabX0 + t * (tabW + 4);
            Button btn = Button.builder(Component.literal(TAB_LABELS[t]), b -> {
                currentTab = tab;
                rebuildWidgets();
            }).bounds(tx, 20, tabW, 16).build();
            btn.active = (t != currentTab);
            addRenderableWidget(btn);
        }

        switch (currentTab) {
            case 0 -> initPollRateTab(cx, top, bw, bh, gap);
            case 1 -> initFeaturesTab(cx, top, bw, bh, gap);
            case 2 -> initAdvancedTab(cx, top, bw, bh, gap);
            case 3 -> initStatsTab(cx, top, bw, bh, gap);
            case 4 -> initProfilesTab(cx, top, bw, bh, gap);
        }

        addRenderableWidget(Button.builder(Component.literal("§a✓ Save & Close"), btn -> {
            InputBoosterConfig.save();
            onClose();
        }).bounds(cx - bw / 2, this.height - 28, bw, bh).build());
    }

    private void initPollRateTab(int cx, int top, int bw, int bh, int gap) {
        modeButton = Button.builder(modeLabel(), btn -> {
            InputBoosterConfig.setPollRateAutoMode(!InputBoosterConfig.isPollRateAutoMode());
            btn.setMessage(modeLabel());
            updateSliderActive();
            applyPollRate();
            hasChanges = true;
        }).bounds(cx - bw / 2, top, bw, bh).build();
        addRenderableWidget(modeButton);

        pollSlider = new PollRateSlider(cx - bw / 2, top + gap, bw, bh, InputBoosterConfig.getPollRateHz());
        addRenderableWidget(pollSlider);

        addRenderableWidget(toggleButton(cx, top + gap * 2, "Burst Mode", InputBoosterConfig.isBurstModeEnabled(), btn -> {
            InputBoosterConfig.setBurstModeEnabled(!InputBoosterConfig.isBurstModeEnabled());
            btn.setMessage(toggleLabel("Burst Mode", InputBoosterConfig.isBurstModeEnabled()));
            hasChanges = true;
        }));

        addRenderableWidget(toggleButton(cx, top + gap * 3, "Combo Keys (Ctrl+1-5)", InputBoosterConfig.isComboKeysEnabled(), btn -> {
            InputBoosterConfig.setComboKeysEnabled(!InputBoosterConfig.isComboKeysEnabled());
            btn.setMessage(toggleLabel("Combo Keys", InputBoosterConfig.isComboKeysEnabled()));
            hasChanges = true;
        }));

        int[] presets = {100, 200, 350, 500, 750, 1000};
        String[] names = {"100","200","350","500","750","1000"};
        int pbw = 32; presetButtons = new Button[6];
        for (int i = 0; i < 6; i++) {
            final int hz = presets[i];
            int col = i % 3, row = i / 3;
            int px = cx - (pbw * 3 + 4) / 2 + col * (pbw + 2);
            int py = top + gap * 4 + row * (bh + 2);
            presetButtons[i] = Button.builder(Component.literal(names[i] + "Hz"), btn -> {
                InputBoosterConfig.setPollRateHz(hz);
                pollSlider.updateValue(hz);
                applyPollRate();
                hasChanges = true;
            }).bounds(px, py, pbw, bh).build();
            addRenderableWidget(presetButtons[i]);
        }
        updateSliderActive();
    }

    private void initFeaturesTab(int cx, int top, int bw, int bh, int gap) {
        record Toggle(String label, boolean state, java.util.function.Consumer<Boolean> setter) {}
        Toggle[] toggles = {
            new Toggle("Sprint Fix",   InputBoosterConfig.isSprintFixEnabled(),  InputBoosterConfig::setSprintFixEnabled),
            new Toggle("Auto-Sprint",  InputBoosterConfig.isAutoSprintEnabled(), InputBoosterConfig::setAutoSprintEnabled),
            new Toggle("W-Tap Assist", InputBoosterConfig.isWTapAssistEnabled(), InputBoosterConfig::setWTapAssistEnabled),
            new Toggle("Anti-Idle",    InputBoosterConfig.isAntiIdleEnabled(),   InputBoosterConfig::setAntiIdleEnabled),
            new Toggle("Auto-Strafe",  InputBoosterConfig.isAutoStrafeEnabled(), InputBoosterConfig::setAutoStrafeEnabled),
            new Toggle("CPS Limiter",  InputBoosterConfig.isCpsLimiterEnabled(), InputBoosterConfig::setCpsLimiterEnabled),
        };
        for (int i = 0; i < toggles.length; i++) {
            Toggle t = toggles[i];
            addRenderableWidget(toggleButton(cx, top + gap * i, t.label(), t.state(), btn -> {
                boolean newVal = !btn.getMessage().getString().contains("ON");
                t.setter().accept(newVal);
                btn.setMessage(toggleLabel(t.label(), newVal));
                hasChanges = true;
            }));
        }
        addRenderableWidget(new MaxCpsSlider(cx - bw / 2, top + gap * toggles.length + 4, bw, bh, InputBoosterConfig.getMaxCps()));
    }

    private void initAdvancedTab(int cx, int top, int bw, int bh, int gap) {
        addRenderableWidget(toggleButton(cx, top, "HUD Overlay", InputBoosterConfig.isShowF3Info(), btn -> {
            InputBoosterConfig.setShowF3Info(!InputBoosterConfig.isShowF3Info());
            btn.setMessage(toggleLabel("HUD Overlay", InputBoosterConfig.isShowF3Info()));
            hasChanges = true;
        }));
        addRenderableWidget(Button.builder(overlayPosLabel(), btn -> {
            InputBoosterConfig.setOverlayPosition((InputBoosterConfig.getOverlayPosition() + 1) % 4);
            btn.setMessage(overlayPosLabel());
            hasChanges = true;
        }).bounds(cx - 100, top + gap, 200, bh).build());
        addRenderableWidget(new OverlayScaleSlider(cx - bw / 2, top + gap * 2, bw, bh, InputBoosterConfig.getOverlayScale()));
        addRenderableWidget(toggleButton(cx, top + gap * 3, "Action Bar Messages", InputBoosterConfig.isShowActionBar(), btn -> {
            InputBoosterConfig.setShowActionBar(!InputBoosterConfig.isShowActionBar());
            btn.setMessage(toggleLabel("Action Bar Messages", InputBoosterConfig.isShowActionBar()));
            hasChanges = true;
        }));
        addRenderableWidget(toggleButton(cx, top + gap * 4, "Debug Mode", InputBoosterConfig.isDebugMode(), btn -> {
            InputBoosterConfig.setDebugMode(!InputBoosterConfig.isDebugMode());
            btn.setMessage(toggleLabel("Debug Mode", InputBoosterConfig.isDebugMode()));
            hasChanges = true;
        }));
        addRenderableWidget(new FpsCheckSlider(cx - bw / 2, top + gap * 5 + 4, bw, bh, InputBoosterConfig.getFpsCheckInterval()));
    }

    private void initStatsTab(int cx, int top, int bw, int bh, int gap) {
        addRenderableWidget(Button.builder(Component.literal("§cReset Peak Latency"), btn ->
            LatencyProfiler.resetPeak()
        ).bounds(cx - bw / 2, top + gap * 8, bw, bh).build());
    }

    private void initProfilesTab(int cx, int top, int bw, int bh, int gap) {
        ProfileManager pm = InputBoosterMod.profileManager;
        List<ProfileManager.Profile> profiles = pm.getProfiles();

        for (int i = 0; i < profiles.size(); i++) {
            ProfileManager.Profile p = profiles.get(i);
            final int idx = i;
            int rowY = top + gap * i;
            addRenderableWidget(Button.builder(Component.literal("§a▶ " + p.name()), btn ->
                pm.loadProfile(p.name(), this.minecraft)
            ).bounds(cx - 110, rowY, 100, bh).build());
            addRenderableWidget(Button.builder(Component.literal("§c✕"), btn -> {
                pm.deleteProfile(idx);
                rebuildWidgets();
            }).bounds(cx + 5, rowY, 30, bh).build());
        }

        int saveY = top + gap * ProfileManager.MAX_PROFILES + 10;
        if (profiles.size() < ProfileManager.MAX_PROFILES) {
            String[] quickNames = {"PvP", "Mining", "Idle", "Hybrid", "Custom"};
            int qbw = 38;
            for (int i = 0; i < quickNames.length; i++) {
                String qn = quickNames[i];
                addRenderableWidget(Button.builder(Component.literal(qn), btn -> {
                    pm.saveProfile(qn);
                    rebuildWidgets();
                }).bounds(cx - 100 + i * (qbw + 2), saveY, qbw, bh).build());
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Title - centered
        int titleW = this.font.width(this.title);
        ctx.text(this.font, this.title.getString(), this.width / 2 - titleW / 2, 8, 0xFFFFFF, false);

        // Tab description - centered
        String tabLabel = switch (currentTab) {
            case 0 -> "Poll Rate Configuration";
            case 1 -> "Feature Toggles";
            case 2 -> "Advanced Settings";
            case 3 -> "Session Statistics";
            case 4 -> "Config Profiles";
            default -> "";
        };
        String tabStr = "§8" + tabLabel;
        int tabW = this.font.width(tabStr);
        ctx.text(this.font, tabStr, this.width / 2 - tabW / 2, 38, 0x888888, false);

        if (currentTab == 3) renderStatsContent(ctx);

        if (hasChanges) {
            String unsaved = "§e⚠ Unsaved changes";
            int unsavedW = this.font.width(unsaved);
            ctx.text(this.font, unsaved, this.width / 2 - unsavedW / 2, this.height - 56, 0xFFFF55, false);
        }
    }

    private void renderStatsContent(GuiGraphicsExtractor ctx) {
        SessionStats ss = InputBoosterMod.sessionStats;
        if (ss == null) return;
        int cx = this.width / 2, y = 50, lh = 11;
        String[] lines = {
            "§7Session: §e" + ss.getSessionStartTime() + " §7(up §e" + ss.getUptimeFormatted() + "§7)",
            "§7Inputs recovered: §a" + ss.getTotalRecovered(),
            "§7Est. missed without mod: §c" + ss.getEstimatedMissedInputs(),
            "§7" + LatencyProfiler.formatForOverlay(),
            "§7Poll thread: §" + (ss.isPollingThreadAlive() ? "aRUNNING" : "cSTOPPED"),
            "§7Poll rate: §e" + InputBoosterMod.currentPollHz + " Hz",
            "",
            "§7CPS sparkline (60s):",
        };
        for (String line : lines) {
            ctx.text(this.font, line, cx - 110, y, 0xFFFFFF, false);
            y += lh;
        }
        renderSparkline(ctx, cx - 110, y, 220, 20, ss.getCpsHistory());
    }

    private void renderSparkline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int[] data) {
        if (data.length == 0) return;
        int max = 1;
        for (int v : data) if (v > max) max = v;
        int barW = Math.max(1, w / data.length);
        for (int i = 0; i < data.length; i++) {
            int barH = (int)((double) data[i] / max * h);
            double ratio = (double) data[i] / Math.max(1, InputBoosterConfig.getMaxCps());
            int color = ratio < 0.6 ? 0xFF55FF55 : ratio < 0.85 ? 0xFFFFFF55 : 0xFFFF5555;
            ctx.fill(x + i * barW, y + h - barH, x + i * barW + barW - 1, y + h, color);
        }
        ctx.horizontalLine(x, x + w - 1, y, 0xFF888888);
        ctx.horizontalLine(x, x + w - 1, y + h - 1, 0xFF888888);
        ctx.verticalLine(x, y, y + h - 1, 0xFF888888);
        ctx.verticalLine(x + w - 1, y, y + h - 1, 0xFF888888);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private void applyPollRate() {
        if (!InputBoosterConfig.isPollRateAutoMode()) {
            int hz = InputBoosterConfig.getPollRateHz();
            InputBoosterMod.currentPollHz = hz;
            if (InputBoosterMod.pollingThread != null) InputBoosterMod.pollingThread.setPollRateHz(hz);
        }
    }

    private void updateSliderActive() {
        if (pollSlider != null) {
            boolean manual = !InputBoosterConfig.isPollRateAutoMode();
            pollSlider.active = manual;
            if (presetButtons != null) for (Button pb : presetButtons) pb.active = manual;
        }
    }

    private Component overlayPosLabel() {
        String[] names = {"Top-Left", "Top-Right", "Bottom-Left", "Bottom-Right"};
        return Component.literal("Overlay Position: §e" + names[InputBoosterConfig.getOverlayPosition()]);
    }

    private Component modeLabel() {
        return InputBoosterConfig.isPollRateAutoMode()
            ? Component.literal("§aMode: AUTO §r§7(FPS-adaptive)")
            : Component.literal("§eMode: MANUAL §r§7(fixed Hz)");
    }

    private Button toggleButton(int cx, int y, String label, boolean initial, Button.OnPress action) {
        return Button.builder(toggleLabel(label, initial), action)
            .bounds(cx - 100, y, 200, 20).build();
    }

    private Component toggleLabel(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "§a✓ ON" : "§c✗ OFF"));
    }

    private static class PollRateSlider extends AbstractSliderButton {
        private int hz;
        PollRateSlider(int x, int y, int w, int h, int currentHz) {
            super(x, y, w, h, Component.literal("Poll Rate: " + currentHz + " Hz"), (currentHz - 60) / 940.0);
            this.hz = currentHz;
        }
        void updateValue(int newHz) {
            this.hz = newHz;
            this.value = (newHz - 60) / 940.0;
            updateMessage();
        }
        @Override protected void updateMessage() { setMessage(Component.literal("Poll Rate: §e" + hz + " Hz")); }
        @Override protected void applyValue() {
            hz = 60 + (int)(value * 940);
            hz = (hz / 10) * 10;
            hz = Math.max(60, Math.min(1000, hz));
            InputBoosterConfig.setPollRateHz(hz);
            updateMessage();
        }
    }

    private static class MaxCpsSlider extends AbstractSliderButton {
        private int cps;
        MaxCpsSlider(int x, int y, int w, int h, int currentCps) {
            super(x, y, w, h, Component.literal("Max CPS: " + currentCps), (currentCps - 1) / 19.0);
            this.cps = currentCps;
        }
        @Override protected void updateMessage() { setMessage(Component.literal("Max CPS: §e" + cps)); }
        @Override protected void applyValue() {
            cps = 1 + (int)(value * 19);
            InputBoosterConfig.setMaxCps(cps);
            updateMessage();
        }
    }

    private static class OverlayScaleSlider extends AbstractSliderButton {
        private float scale;
        OverlayScaleSlider(int x, int y, int w, int h, float currentScale) {
            super(x, y, w, h, Component.literal("Overlay Scale: " + currentScale + "x"), (currentScale - 0.5f) / 2.5f);
            this.scale = currentScale;
        }
        @Override protected void updateMessage() {
            setMessage(Component.literal(String.format("Overlay Scale: §e%.1fx", scale)));
        }
        @Override protected void applyValue() {
            scale = 0.5f + (float)(value * 2.5f);
            scale = Math.round(scale * 10) / 10.0f;
            InputBoosterConfig.setOverlayScale(scale);
            updateMessage();
        }
    }

    private static class FpsCheckSlider extends AbstractSliderButton {
        private int ticks;
        FpsCheckSlider(int x, int y, int w, int h, int currentTicks) {
            super(x, y, w, h, Component.literal("FPS Check: " + currentTicks + " ticks"), (currentTicks - 1) / 99.0);
            this.ticks = currentTicks;
        }
        @Override protected void updateMessage() { setMessage(Component.literal("FPS Check: §e" + ticks + " ticks")); }
        @Override protected void applyValue() {
            ticks = 1 + (int)(value * 99);
            InputBoosterConfig.setFpsCheckInterval(ticks);
            updateMessage();
        }
    }
}
