package dev.inputbooster.platform;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.engine.InputEngine;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Optional Sodium Config API integration.
 *
 * <p>This class is only loaded by Sodium through the
 * {@code sodium:config_api_user} Fabric entrypoint. Sodium remains an optional
 * dependency; InputBooster's own settings screen and keybind continue to work
 * when Sodium is not installed.
 *
 * <p>The Sodium API is used strictly as a presentation layer. All values are
 * bound to InputBoosterConfig and are persisted by its existing atomic save
 * implementation.
 */
public final class SodiumConfigEntryPoint implements ConfigEntryPoint {
    private static final Runnable SAVE = InputBoosterConfig::save;

    private enum OverlayMode {
        F3_ONLY,
        ALWAYS
    }

    private enum OverlayPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var mod = builder.registerOwnModOptions();

        mod.addPage(createGeneralPage(builder));
        mod.addPage(createFeaturesPage(builder));
        mod.addPage(createOverlayPage(builder));
        mod.addPage(createAdvancedPage(builder));
    }

    private static OptionPageBuilder createGeneralPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
            .setName(Component.literal("General"));

        OptionGroupBuilder polling = builder.createOptionGroup()
            .setName(Component.literal("Input polling"));

        polling.addOption(bool(builder, "poll_rate_auto", "Automatic poll rate",
            "Adapt the polling rate to the current game state and performance.",
            InputBoosterConfig::isPollRateAutoMode, InputBoosterConfig::setPollRateAutoMode));

        polling.addOption(integer(builder, "poll_rate_hz", "Manual poll rate",
            "Polling frequency used when automatic mode is disabled.",
            60, 1000, 1, " Hz",
            InputBoosterConfig::getPollRateHz, InputBoosterConfig::setPollRateHz)
            .setEnabledProvider(state -> !state.readBooleanOption(id("poll_rate_auto")),
                id("poll_rate_auto")));

        page.addOptionGroup(polling);

        OptionGroupBuilder engine = builder.createOptionGroup()
            .setName(Component.literal("Input engine"));

        engine.addOption(enumOption(builder, "engine_mode", "Engine mode",
            "Choose how InputBooster observes keyboard and mouse state.",
            InputEngine.Mode.class,
            () -> InputEngine.Mode.fromConfig(InputBoosterConfig.getEngineMode()),
            value -> InputBoosterConfig.setEngineMode(value.ordinal()),
            value -> switch (value) {
                case AUTO -> "Automatic";
                case EVENT -> "Event-driven";
                case POLL -> "Polling";
            }));

        engine.addOption(bool(builder, "compat_fallback", "Compatibility fallback",
            "Fall back to a compatible input mode when another input mod conflicts.",
            InputBoosterConfig::isCompatFallback, InputBoosterConfig::setCompatFallback));

        page.addOptionGroup(engine);
        return page;
    }

    private static OptionPageBuilder createFeaturesPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
            .setName(Component.literal("Features"));

        OptionGroupBuilder movement = builder.createOptionGroup()
            .setName(Component.literal("Movement"));

        movement.addOption(bool(builder, "sprint_fix", "Sprint fix",
            "Keep sprint state consistent across short input interruptions.",
            InputBoosterConfig::isSprintFixEnabled, InputBoosterConfig::setSprintFixEnabled));
        movement.addOption(bool(builder, "auto_sprint", "Auto sprint",
            "Enable the configured automatic sprint assistance.",
            InputBoosterConfig::isAutoSprintEnabled, InputBoosterConfig::setAutoSprintEnabled));
        movement.addOption(bool(builder, "wtap_assist", "W-tap assistance",
            "Enable the W-tap movement helper.",
            InputBoosterConfig::isWTapAssistEnabled, InputBoosterConfig::setWTapAssistEnabled));
        movement.addOption(bool(builder, "anti_idle", "Anti-idle",
            "Enable the configured anti-idle helper.",
            InputBoosterConfig::isAntiIdleEnabled, InputBoosterConfig::setAntiIdleEnabled));
        movement.addOption(bool(builder, "auto_strafe", "Auto strafe",
            "Enable the configured strafing helper.",
            InputBoosterConfig::isAutoStrafeEnabled, InputBoosterConfig::setAutoStrafeEnabled));
        movement.addOption(bool(builder, "combo_keys", "Combo keys",
            "Enable poll-rate preset shortcuts.",
            InputBoosterConfig::isComboKeysEnabled, InputBoosterConfig::setComboKeysEnabled));
        page.addOptionGroup(movement);

        OptionGroupBuilder clickControl = builder.createOptionGroup()
            .setName(Component.literal("Click limiter"));

        clickControl.addOption(bool(builder, "cps_limiter", "CPS limiter",
            "Enable the click-rate limiter.",
            InputBoosterConfig::isCpsLimiterEnabled, InputBoosterConfig::setCpsLimiterEnabled));
        clickControl.addOption(integer(builder, "max_cps", "Maximum CPS",
            "Maximum accepted clicks per second.",
            1, 20, 1, " CPS",
            InputBoosterConfig::getMaxCps, InputBoosterConfig::setMaxCps)
            .setEnabledProvider(state -> state.readBooleanOption(id("cps_limiter")),
                id("cps_limiter")));
        clickControl.addOption(bool(builder, "burst_mode", "Adaptive burst mode",
            "Allow short input bursts while keeping the configured long-term limit.",
            InputBoosterConfig::isBurstModeEnabled, InputBoosterConfig::setBurstModeEnabled));
        page.addOptionGroup(clickControl);

        return page;
    }

    private static OptionPageBuilder createOverlayPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
            .setName(Component.literal("Overlay"));

        OptionGroupBuilder visibility = builder.createOptionGroup()
            .setName(Component.literal("Visibility"));

        visibility.addOption(bool(builder, "show_f3_info", "Show F3 information",
            "Show InputBooster diagnostics in the debug overlay.",
            InputBoosterConfig::isShowF3Info, InputBoosterConfig::setShowF3Info));
        visibility.addOption(bool(builder, "show_keystrokes", "Show keystrokes",
            "Display the keystroke overlay.",
            InputBoosterConfig::isShowKeystrokes, InputBoosterConfig::setShowKeystrokes));
        visibility.addOption(bool(builder, "show_action_bar", "Show action bar",
            "Display InputBooster action-bar messages.",
            InputBoosterConfig::isShowActionBar, InputBoosterConfig::setShowActionBar));
        visibility.addOption(enumOption(builder, "overlay_mode", "Overlay display mode",
            "Choose when the diagnostics overlay is visible.",
            OverlayMode.class,
            () -> InputBoosterConfig.getOverlayMode() == 1 ? OverlayMode.ALWAYS : OverlayMode.F3_ONLY,
            value -> InputBoosterConfig.setOverlayMode(value == OverlayMode.ALWAYS ? 1 : 0),
            value -> value == OverlayMode.ALWAYS ? "Always" : "While F3 is open"));
        page.addOptionGroup(visibility);

        OptionGroupBuilder appearance = builder.createOptionGroup()
            .setName(Component.literal("Appearance"));

        appearance.addOption(enumOption(builder, "overlay_position", "Overlay position",
            "Choose the corner used by the overlay.",
            OverlayPosition.class,
            () -> OverlayPosition.values()[InputBoosterConfig.getOverlayPosition()],
            value -> InputBoosterConfig.setOverlayPosition(value.ordinal()),
            value -> switch (value) {
                case TOP_LEFT -> "Top left";
                case TOP_RIGHT -> "Top right";
                case BOTTOM_LEFT -> "Bottom left";
                case BOTTOM_RIGHT -> "Bottom right";
            }));
        appearance.addOption(integer(builder, "overlay_opacity", "Overlay opacity",
            "Background opacity percentage.",
            0, 100, 1, "%",
            () -> Math.round(InputBoosterConfig.getOverlayOpacity() * 100.0f),
            value -> InputBoosterConfig.setOverlayOpacity(value / 100.0f)));
        appearance.addOption(integer(builder, "overlay_scale", "Overlay scale",
            "Overlay size percentage.",
            50, 300, 1, "%",
            () -> Math.round(InputBoosterConfig.getOverlayScale() * 100.0f),
            value -> InputBoosterConfig.setOverlayScale(value / 100.0f)));
        page.addOptionGroup(appearance);

        OptionGroupBuilder diagnostics = builder.createOptionGroup()
            .setName(Component.literal("Diagnostics"));

        diagnostics.addOption(bool(builder, "overlay_show_engine", "Show engine status",
            "Include the active input engine in the overlay.",
            InputBoosterConfig::isOverlayShowEngine, InputBoosterConfig::setOverlayShowEngine));
        diagnostics.addOption(bool(builder, "overlay_show_events", "Show recent events",
            "Include recent input events in the overlay.",
            InputBoosterConfig::isOverlayShowEvents, InputBoosterConfig::setOverlayShowEvents));
        diagnostics.addOption(bool(builder, "overlay_show_overhead", "Show overhead",
            "Include polling and processing overhead in the overlay.",
            InputBoosterConfig::isOverlayShowOverhead, InputBoosterConfig::setOverlayShowOverhead));
        page.addOptionGroup(diagnostics);

        return page;
    }

    private static OptionPageBuilder createAdvancedPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
            .setName(Component.literal("Advanced"));

        OptionGroupBuilder input = builder.createOptionGroup()
            .setName(Component.literal("Input handling"));

        input.addOption(bool(builder, "keyboard_repeat_guard", "Keyboard repeat guard",
            "Ignore repeated key-down events where a single press is expected.",
            InputBoosterConfig::isKeyboardRepeatGuard, InputBoosterConfig::setKeyboardRepeatGuard));
        input.addOption(integer(builder, "mouse_scroll_debounce_ms", "Scroll debounce",
            "Minimum debounce interval for mouse-wheel events.",
            0, 200, 1, " ms",
            InputBoosterConfig::getMouseScrollDebounceMs, InputBoosterConfig::setMouseScrollDebounceMs));
        input.addOption(integer(builder, "mouse_click_dedupe_ms", "Click deduplication",
            "Deduplication interval for duplicate mouse-click events.",
            0, 100, 1, " ms",
            InputBoosterConfig::getMouseClickDedupeMs, InputBoosterConfig::setMouseClickDedupeMs));
        input.addOption(bool(builder, "custom_keyboard_handler", "Custom keyboard handler (alpha)",
            "Experimental. Keep disabled unless you are testing the alternate keyboard handler.",
            InputBoosterConfig::isCustomKeyboardHandlerEnabled,
            InputBoosterConfig::setCustomKeyboardHandlerEnabled));
        page.addOptionGroup(input);

        OptionGroupBuilder safety = builder.createOptionGroup()
            .setName(Component.literal("Recovery and diagnostics"));

        safety.addOption(bool(builder, "safe_mode", "Safe mode",
            "Enable InputBooster's recovery safeguards.",
            InputBoosterConfig::isSafeModeEnabled, InputBoosterConfig::setSafeModeEnabled));
        safety.addOption(bool(builder, "event_log", "Event log",
            "Record recent InputBooster events for diagnostics.",
            InputBoosterConfig::isEventLogEnabled, InputBoosterConfig::setEventLogEnabled));
        safety.addOption(bool(builder, "key_conflict_warn", "Key conflict warnings",
            "Warn when configured key mappings conflict.",
            InputBoosterConfig::isKeyConflictWarn, InputBoosterConfig::setKeyConflictWarn));
        safety.addOption(bool(builder, "debug_mode", "Debug mode",
            "Enable additional diagnostic logging.",
            InputBoosterConfig::isDebugMode, InputBoosterConfig::setDebugMode));
        safety.addOption(integer(builder, "fps_check_interval", "FPS check interval",
            "Interval in ticks between FPS checks.",
            1, 100, 1, " ticks",
            InputBoosterConfig::getFpsCheckInterval, InputBoosterConfig::setFpsCheckInterval));
        page.addOptionGroup(safety);

        OptionGroupBuilder data = builder.createOptionGroup()
            .setName(Component.literal("Profiles and replay"));

        data.addOption(bool(builder, "replay_enabled", "Replay recording",
            "Enable input replay recording and playback.",
            InputBoosterConfig::isReplayEnabled, InputBoosterConfig::setReplayEnabled));
        data.addOption(bool(builder, "per_server_profiles", "Per-server profiles",
            "Automatically use profiles associated with the current server.",
            InputBoosterConfig::isPerServerProfiles, InputBoosterConfig::setPerServerProfiles));
        page.addOptionGroup(data);

        OptionGroupBuilder audio = builder.createOptionGroup()
            .setName(Component.literal("Audio feedback"));

        audio.addOption(bool(builder, "click_sounds", "Click sounds",
            "Play optional audio feedback for accepted clicks.",
            InputBoosterConfig::isClickSoundsEnabled, InputBoosterConfig::setClickSoundsEnabled));
        audio.addOption(integer(builder, "click_sound_pitch", "Click sound pitch",
            "Pitch percentage for click feedback.",
            50, 200, 1, "%",
            () -> Math.round(InputBoosterConfig.getClickSoundPitch() * 100.0f),
            value -> InputBoosterConfig.setClickSoundPitch(value / 100.0f))
            .setEnabledProvider(state -> state.readBooleanOption(id("click_sounds")),
                id("click_sounds")));
        audio.addOption(integer(builder, "click_sound_volume", "Click sound volume",
            "Volume percentage for click feedback.",
            0, 100, 1, "%",
            () -> Math.round(InputBoosterConfig.getClickSoundVolume() * 100.0f),
            value -> InputBoosterConfig.setClickSoundVolume(value / 100.0f))
            .setEnabledProvider(state -> state.readBooleanOption(id("click_sounds")),
                id("click_sounds")));
        page.addOptionGroup(audio);

        return page;
    }

    private static BooleanOptionBuilder bool(
        ConfigBuilder builder,
        String key,
        String name,
        String tooltip,
        Supplier<Boolean> getter,
        Consumer<Boolean> setter
    ) {
        return builder.createBooleanOption(id(key))
            .setName(Component.literal(name))
            .setTooltip(Component.literal(tooltip))
            .setStorageHandler(SAVE)
            .setDefaultValue(getter.get())
            .setBinding(setter, getter);
    }

    private static IntegerOptionBuilder integer(
        ConfigBuilder builder,
        String key,
        String name,
        String tooltip,
        int min,
        int max,
        int step,
        String suffix,
        Supplier<Integer> getter,
        Consumer<Integer> setter
    ) {
        return builder.createIntegerOption(id(key))
            .setName(Component.literal(name))
            .setTooltip(Component.literal(tooltip))
            .setStorageHandler(SAVE)
            .setDefaultValue(getter.get())
            .setRange(min, max, step)
            .setValueFormatter(value -> Component.literal(value + suffix))
            .setBinding(setter, getter);
    }

    private static <E extends Enum<E>> EnumOptionBuilder<E> enumOption(
        ConfigBuilder builder,
        String key,
        String name,
        String tooltip,
        Class<E> enumClass,
        Supplier<E> getter,
        Consumer<E> setter,
        Function<E, String> displayName
    ) {
        return builder.createEnumOption(id(key), enumClass)
            .setName(Component.literal(name))
            .setTooltip(Component.literal(tooltip))
            .setStorageHandler(SAVE)
            .setDefaultValue(getter.get())
            .setElementNameProvider(value -> Component.literal(displayName.apply(value)))
            .setBinding(setter, getter);
    }

    private static Identifier id(String path) {
        return Identifier.parse("inputbooster:" + path);
    }
}
