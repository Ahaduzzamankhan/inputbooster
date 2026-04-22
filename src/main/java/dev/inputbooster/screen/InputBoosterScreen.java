package dev.inputbooster.screen;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * InputBoosterScreen — Modern in-game options screen.
 *
 * BUG FIX:
 *  clearAndInit() does not exist in Minecraft 1.21.x Fabric API.
 *  The original code called this.clearAndInit() inside tab-switch button handlers,
 *  which would throw a NoSuchMethodError at runtime.
 *  Fixed by using the correct pattern: remove all children and re-call init().
 *
 * Features:
 *  • Poll Rate Mode: Toggle AUTO vs MANUAL
 *  • Poll Rate Slider: 60-1000 Hz with real-time preview
 *  • Preset Buttons: Quick select (100, 150, 200, 350, 500, 750 Hz)
 *  • Feature Toggles: All major features with visual feedback
 *  • Advanced Options: FPS check interval, debug mode
 *  • Real-time Status: Shows current Hz and mode
 *  • Save & Close: Persistent config storage
 *
 * Version: 2.1.0
 * Author: Ahaduzzaman Khan
 */
public class InputBoosterScreen extends Screen {

    private final Screen parent;
    private boolean hasChanges = false;

    // Section tabs
    private ButtonWidget pollRateTab;
    private ButtonWidget featuresTab;
    private ButtonWidget advancedTab;
    private int currentTab = 0; // 0 = poll rate, 1 = features, 2 = advanced

    // Poll Rate Section
    private ButtonWidget modeButton;
    private PollRateSlider pollSlider;
    private ButtonWidget[] presetButtons;

    // Feature Section
    private ButtonWidget sprintFixBtn;
    private ButtonWidget autoSprintBtn;
    private ButtonWidget wTapBtn;
    private ButtonWidget antiIdleBtn;
    private ButtonWidget autoStrafeBtn;
    private ButtonWidget cpsLimiterBtn;

    // UI Section
    private ButtonWidget f3InfoBtn;
    private ButtonWidget actionBarBtn;

    // Advanced Section
    private ButtonWidget debugModeBtn;
    private FpsCheckSlider fpsCheckSlider;

    public InputBoosterScreen(Screen parent) {
        super(Text.literal("§b§lInputBooster§r §8v" + InputBoosterMod.MOD_VERSION));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = 50;
        int bw = 200;
        int bh = 20;
        int gap = 24;

        // ─────────────────────────────────────────────────────────────
        // Tab Navigation
        // ─────────────────────────────────────────────────────────────

        int tabWidth = 60;
        int tabX = cx - (tabWidth * 3) / 2 - 3;

        pollRateTab = ButtonWidget.builder(Text.literal("§ePoll Rate"), btn -> {
            currentTab = 0;
            // FIX: clearAndInit() does not exist in MC 1.21.x.
            // The correct approach is to clear widget children and re-run init().
            this.clearChildren();
            this.init();
        })
        .dimensions(tabX, 20, tabWidth, 16)
        .build();
        addDrawableChild(pollRateTab);

        featuresTab = ButtonWidget.builder(Text.literal("§aFeatures"), btn -> {
            currentTab = 1;
            this.clearChildren();
            this.init();
        })
        .dimensions(tabX + tabWidth + 6, 20, tabWidth, 16)
        .build();
        addDrawableChild(featuresTab);

        advancedTab = ButtonWidget.builder(Text.literal("§8Advanced"), btn -> {
            currentTab = 2;
            this.clearChildren();
            this.init();
        })
        .dimensions(tabX + (tabWidth + 6) * 2, 20, tabWidth, 16)
        .build();
        addDrawableChild(advancedTab);

        // Mark active tab as non-clickable
        (currentTab == 0 ? pollRateTab : currentTab == 1 ? featuresTab : advancedTab).active = false;

        // ─────────────────────────────────────────────────────────────
        // Dynamic Content Based on Tab
        // ─────────────────────────────────────────────────────────────

        if (currentTab == 0) {
            initPollRateTab(cx, top, bw, bh, gap);
        } else if (currentTab == 1) {
            initFeaturesTab(cx, top, bw, bh, gap);
        } else {
            initAdvancedTab(cx, top, bw, bh, gap);
        }

        // ─────────────────────────────────────────────────────────────
        // Footer Buttons
        // ─────────────────────────────────────────────────────────────

        addDrawableChild(ButtonWidget.builder(Text.literal("§a✓ Save & Close"), btn -> {
            InputBoosterConfig.save();
            close();
        })
        .dimensions(cx - bw / 2, this.height - 28, bw, bh)
        .build());
    }

    private void initPollRateTab(int cx, int top, int bw, int bh, int gap) {
        modeButton = ButtonWidget.builder(
            modeLabel(),
            btn -> {
                InputBoosterConfig.setPollRateAutoMode(!InputBoosterConfig.isPollRateAutoMode());
                btn.setMessage(modeLabel());
                updateSliderActive();
                applyPollRate();
                hasChanges = true;
            })
            .dimensions(cx - bw / 2, top, bw, bh)
            .build();
        addDrawableChild(modeButton);

        pollSlider = new PollRateSlider(cx - bw / 2, top + gap, bw, bh,
            InputBoosterConfig.getPollRateHz());
        addDrawableChild(pollSlider);

        int[] presets = {100, 150, 200, 350, 500, 750};
        String[] names = {"100", "150", "200", "350", "500", "750"};
        int pbw = 32;
        presetButtons = new ButtonWidget[6];

        for (int i = 0; i < 6; i++) {
            final int hz = presets[i];
            int row = i / 3;
            int col = i % 3;
            int px = cx - (pbw * 3) / 2 - 3 + col * (pbw + 2);
            int py = top + gap * 2 + row * (bh + 2);

            presetButtons[i] = ButtonWidget.builder(
                Text.literal(names[i] + " Hz"),
                btn -> {
                    InputBoosterConfig.setPollRateHz(hz);
                    pollSlider.updateValue(hz);
                    applyPollRate();
                    hasChanges = true;
                })
                .dimensions(px, py, pbw, bh)
                .build();
            addDrawableChild(presetButtons[i]);
        }

        updateSliderActive();
    }

    private void initFeaturesTab(int cx, int top, int bw, int bh, int gap) {
        int ty = top;

        sprintFixBtn = toggleButton(cx, ty, "Sprint Fix",
            InputBoosterConfig.isSprintFixEnabled(),
            btn -> {
                InputBoosterConfig.setSprintFixEnabled(!InputBoosterConfig.isSprintFixEnabled());
                btn.setMessage(toggleLabel("Sprint Fix", InputBoosterConfig.isSprintFixEnabled()));
                hasChanges = true;
            });
        addDrawableChild(sprintFixBtn);

        autoSprintBtn = toggleButton(cx, ty + gap, "Auto-Sprint",
            InputBoosterConfig.isAutoSprintEnabled(),
            btn -> {
                InputBoosterConfig.setAutoSprintEnabled(!InputBoosterConfig.isAutoSprintEnabled());
                btn.setMessage(toggleLabel("Auto-Sprint", InputBoosterConfig.isAutoSprintEnabled()));
                hasChanges = true;
            });
        addDrawableChild(autoSprintBtn);

        wTapBtn = toggleButton(cx, ty + gap * 2, "W-Tap Assist",
            InputBoosterConfig.isWTapAssistEnabled(),
            btn -> {
                InputBoosterConfig.setWTapAssistEnabled(!InputBoosterConfig.isWTapAssistEnabled());
                btn.setMessage(toggleLabel("W-Tap Assist", InputBoosterConfig.isWTapAssistEnabled()));
                hasChanges = true;
            });
        addDrawableChild(wTapBtn);

        antiIdleBtn = toggleButton(cx, ty + gap * 3, "Anti-Idle",
            InputBoosterConfig.isAntiIdleEnabled(),
            btn -> {
                InputBoosterConfig.setAntiIdleEnabled(!InputBoosterConfig.isAntiIdleEnabled());
                btn.setMessage(toggleLabel("Anti-Idle", InputBoosterConfig.isAntiIdleEnabled()));
                hasChanges = true;
            });
        addDrawableChild(antiIdleBtn);

        autoStrafeBtn = toggleButton(cx, ty + gap * 4, "Auto-Strafe",
            InputBoosterConfig.isAutoStrafeEnabled(),
            btn -> {
                InputBoosterConfig.setAutoStrafeEnabled(!InputBoosterConfig.isAutoStrafeEnabled());
                btn.setMessage(toggleLabel("Auto-Strafe", InputBoosterConfig.isAutoStrafeEnabled()));
                hasChanges = true;
            });
        addDrawableChild(autoStrafeBtn);

        cpsLimiterBtn = toggleButton(cx, ty + gap * 5, "CPS Limiter",
            InputBoosterConfig.isCpsLimiterEnabled(),
            btn -> {
                InputBoosterConfig.setCpsLimiterEnabled(!InputBoosterConfig.isCpsLimiterEnabled());
                btn.setMessage(toggleLabel("CPS Limiter", InputBoosterConfig.isCpsLimiterEnabled()));
                hasChanges = true;
            });
        addDrawableChild(cpsLimiterBtn);
    }

    private void initAdvancedTab(int cx, int top, int bw, int bh, int gap) {
        int ty = top;

        f3InfoBtn = toggleButton(cx, ty, "F3 Overlay Info",
            InputBoosterConfig.isShowF3Info(),
            btn -> {
                InputBoosterConfig.setShowF3Info(!InputBoosterConfig.isShowF3Info());
                btn.setMessage(toggleLabel("F3 Overlay Info", InputBoosterConfig.isShowF3Info()));
                hasChanges = true;
            });
        addDrawableChild(f3InfoBtn);

        actionBarBtn = toggleButton(cx, ty + gap, "Action Bar Messages",
            InputBoosterConfig.isShowActionBar(),
            btn -> {
                InputBoosterConfig.setShowActionBar(!InputBoosterConfig.isShowActionBar());
                btn.setMessage(toggleLabel("Action Bar Messages", InputBoosterConfig.isShowActionBar()));
                hasChanges = true;
            });
        addDrawableChild(actionBarBtn);

        debugModeBtn = toggleButton(cx, ty + gap * 2, "Debug Mode",
            InputBoosterConfig.isDebugMode(),
            btn -> {
                InputBoosterConfig.setDebugMode(!InputBoosterConfig.isDebugMode());
                btn.setMessage(toggleLabel("Debug Mode", InputBoosterConfig.isDebugMode()));
                hasChanges = true;
            });
        addDrawableChild(debugModeBtn);

        fpsCheckSlider = new FpsCheckSlider(cx - bw / 2, ty + gap * 3 + 4, bw, bh,
            InputBoosterConfig.getFpsCheckInterval());
        addDrawableChild(fpsCheckSlider);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        int cx = this.width / 2;
        String tabLabel = switch (currentTab) {
            case 0 -> "Poll Rate Configuration";
            case 1 -> "Feature Toggle";
            default -> "Advanced Settings";
        };
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§8" + tabLabel), cx, 38, 0x888888);

        if (currentTab == 0) {
            String liveHz = "§7Current: " + (InputBoosterConfig.isPollRateAutoMode() ? "§aAUTO " : "§e") +
                InputBoosterMod.currentPollHz + " Hz§r §7(FPS: " + InputBoosterMod.currentFps + ")";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(liveHz), cx, this.height - 44, 0xFFFFFF);
        }

        if (hasChanges) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e⚠ Unsaved changes"), cx, this.height - 56, 0xFFFF55);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    // ─────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────

    private void applyPollRate() {
        if (!InputBoosterConfig.isPollRateAutoMode()) {
            int hz = InputBoosterConfig.getPollRateHz();
            InputBoosterMod.currentPollHz = hz;
            if (InputBoosterMod.pollingThread != null) {
                InputBoosterMod.pollingThread.setPollRateHz(hz);
            }
        }
    }

    private void updateSliderActive() {
        if (pollSlider != null) {
            boolean manual = !InputBoosterConfig.isPollRateAutoMode();
            pollSlider.active = manual;
            if (presetButtons != null) {
                for (ButtonWidget pb : presetButtons) pb.active = manual;
            }
        }
    }

    private Text modeLabel() {
        return InputBoosterConfig.isPollRateAutoMode()
            ? Text.literal("§aMode: AUTO §r§7(FPS-adaptive)")
            : Text.literal("§eMode: MANUAL §r§7(fixed Hz)");
    }

    private ButtonWidget toggleButton(int cx, int y, String label, boolean initial, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(toggleLabel(label, initial), action)
            .dimensions(cx - 100, y, 200, 20)
            .build();
    }

    private Text toggleLabel(String label, boolean on) {
        return Text.literal(label + ": " + (on ? "§a✓ ON" : "§c✗ OFF"));
    }

    // ─────────────────────────────────────────────────────────────
    // Inner Classes: Custom Sliders
    // ─────────────────────────────────────────────────────────────

    private static class PollRateSlider extends SliderWidget {
        private int hz;

        PollRateSlider(int x, int y, int width, int height, int currentHz) {
            super(x, y, width, height,
                Text.literal("Poll Rate: " + currentHz + " Hz"),
                (currentHz - 60) / 940.0);
            this.hz = currentHz;
        }

        void updateValue(int newHz) {
            this.hz = newHz;
            this.value = (newHz - 60) / 940.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("Poll Rate: §e" + hz + " Hz"));
        }

        @Override
        protected void applyValue() {
            hz = 60 + (int)(value * 940);
            hz = (hz / 10) * 10; // Round to nearest 10
            hz = Math.max(60, Math.min(1000, hz));
            InputBoosterConfig.setPollRateHz(hz);
            updateMessage();
        }
    }

    private static class FpsCheckSlider extends SliderWidget {
        private int ticks;

        FpsCheckSlider(int x, int y, int width, int height, int currentTicks) {
            super(x, y, width, height,
                Text.literal("FPS Check Interval: " + currentTicks + " ticks"),
                (currentTicks - 1) / 99.0);
            this.ticks = currentTicks;
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal("FPS Check Interval: §e" + ticks + " ticks"));
        }

        @Override
        protected void applyValue() {
            ticks = 1 + (int)(value * 99);
            InputBoosterConfig.setFpsCheckInterval(ticks);
            updateMessage();
        }
    }
}
