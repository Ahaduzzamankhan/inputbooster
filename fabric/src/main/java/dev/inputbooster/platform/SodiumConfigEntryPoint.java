package dev.inputbooster.platform;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.engine.InputEngine;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.EnumOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.IntegerOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionPageBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Optional Sodium Config API integration.
 *
 * <p>Sodium discovers this class through the {@code sodium:config_api_user}
 * Fabric entrypoint, so it is only ever loaded when Sodium is installed.
 * InputBooster's own settings screen and keybinds keep working without it.
 *
 * <p>The Sodium API is used strictly as a presentation layer:
 * <ul>
 *   <li>every option reads and writes the existing {@link InputBoosterConfig}
 *       accessors — no second copy of a setting exists anywhere;</li>
 *   <li>{@link InputBoosterConfig#save()} is invoked from Sodium's storage
 *       handler, which Sodium calls once the pending changes have been written
 *       through the option binding, so the existing atomic write to
 *       {@code config/inputbooster.properties} stays the only persistence
 *       path;</li>
 *   <li>the documented defaults come from
 *       {@link InputBoosterConfig.Defaults}, so Sodium's own "reset to
 *       default" action restores exactly what a fresh configuration file
 *       contains.</li>
 * </ul>
 *
 * <p>Verified against the Sodium Config API of Sodium 0.9.2 for Minecraft 26.2
 * and 26.3; the API is identical in both releases.
 */
public final class SodiumConfigEntryPoint implements ConfigEntryPoint {

    /** Namespace every option id lives in, so no other mod can collide. */
    private static final String NAMESPACE = "inputbooster";

    /**
     * The icon shown next to the mod name in Sodium's settings sidebar.
     *
     * <p>Sodium hands this identifier straight to the texture manager, which
     * resolves it relative to {@code assets/<namespace>/}, so the
     * {@code textures/} directory has to be part of the path.
     */
    private static final Identifier ICON = id("textures/gui/icon.png");

    /**
     * Called by Sodium after it wrote a pending change through the option
     * binding. Persisting the file here keeps Sodium's "Apply" button and
     * InputBooster's own "Save &amp; Close" button writing the same file
     * through the same atomic save.
     */
    private static final StorageEventHandler SAVE = InputBoosterConfig::save;

    /** When the diagnostics overlay is drawn. */
    private enum OverlayMode {
        /** Only while the F3 debug screen is open. */
        F3_ONLY,
        /** Whenever the overlay is enabled. */
        ALWAYS
    }

    /** Corner the diagnostics overlay is drawn in. */
    private enum OverlayPosition {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        // Sodium builds its configuration pages while the game finishes
        // loading, which can happen before the first client tick that normally
        // loads the configuration file. Reading it here makes the values shown
        // in the settings screen the values that are actually stored.
        InputBoosterConfig.ensureLoaded();

        ModOptionsBuilder mod = builder.registerOwnModOptions()
                .setNonTintedIcon(ICON);

        mod.addPage(createGeneralPage(builder));
        mod.addPage(createFeaturesPage(builder));
        mod.addPage(createOverlayPage(builder));
        mod.addPage(createAdvancedPage(builder));
    }

    // ── Pages ────────────────────────────────────────────────────────────────

    private static OptionPageBuilder createGeneralPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.literal("General"));

        OptionGroupBuilder polling = builder.createOptionGroup()
                .setName(Component.literal("Input polling"));

        polling.addOption(bool(builder, "general.poll_rate_auto",
                "Automatic polling",
                "Let InputBooster match the polling rate to the current frame rate. "
                        + "The manual rate is used again as soon as this is turned off.",
                InputBoosterConfig.Defaults.POLL_RATE_AUTO,
                InputBoosterConfig::isPollRateAutoMode, InputBoosterConfig::setPollRateAutoMode));

        polling.addOption(integer(builder, "general.poll_rate_hz",
                "Manual polling frequency",
                "How often the raw keyboard and mouse state is sampled while automatic "
                        + "polling is turned off.",
                InputBoosterConfig.Defaults.POLL_RATE_HZ, 60, 1000, " Hz",
                InputBoosterConfig::getPollRateHz, InputBoosterConfig::setPollRateHz)
                .setEnabledProvider(state -> !state.readBooleanOption(id("general.poll_rate_auto")),
                        id("general.poll_rate_auto"))
                .setControlHiddenWhenDisabled(false));

        page.addOptionGroup(polling);

        OptionGroupBuilder engine = builder.createOptionGroup()
                .setName(Component.literal("Input engine"));

        engine.addOption(enumOption(builder, "general.engine_mode",
                "Input engine mode",
                "How InputBooster observes the keyboard and the mouse.",
                InputEngine.Mode.class,
                InputEngine.Mode.fromConfig(InputBoosterConfig.Defaults.ENGINE_MODE),
                () -> InputEngine.Mode.fromConfig(InputBoosterConfig.getEngineMode()),
                mode -> InputBoosterConfig.setEngineMode(mode.ordinal()),
                mode -> switch (mode) {
                    case AUTO -> "Automatic";
                    case EVENT -> "Event driven";
                    case POLL -> "Polled";
                }));

        engine.addOption(bool(builder, "general.compat_fallback",
                "Compatibility fallback",
                "Fall back to a mode that other input mods cannot conflict with when one "
                        + "is detected.",
                InputBoosterConfig.Defaults.COMPAT_FALLBACK,
                InputBoosterConfig::isCompatFallback, InputBoosterConfig::setCompatFallback));

        page.addOptionGroup(engine);
        return page;
    }

    private static OptionPageBuilder createFeaturesPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.literal("Features"));

        OptionGroupBuilder movement = builder.createOptionGroup()
                .setName(Component.literal("Movement assistance"));

        movement.addOption(bool(builder, "features.sprint_fix",
                "Sprint fix",
                "Keep the sprint state consistent across short interruptions of the "
                        + "sprint input.",
                InputBoosterConfig.Defaults.SPRINT_FIX,
                InputBoosterConfig::isSprintFixEnabled, InputBoosterConfig::setSprintFixEnabled));

        movement.addOption(bool(builder, "features.auto_sprint",
                "Auto sprint",
                "Sprint automatically while moving forward.",
                InputBoosterConfig.Defaults.AUTO_SPRINT,
                InputBoosterConfig::isAutoSprintEnabled, InputBoosterConfig::setAutoSprintEnabled));

        movement.addOption(bool(builder, "features.wtap_assist",
                "W-tap assistance",
                "Time the sprint input so it lands shortly after the forward input.",
                InputBoosterConfig.Defaults.WTAP_ASSIST,
                InputBoosterConfig::isWTapAssistEnabled, InputBoosterConfig::setWTapAssistEnabled));

        movement.addOption(bool(builder, "features.anti_idle",
                "Anti-idle",
                "Send a harmless input when the player has been idle for a while.",
                InputBoosterConfig.Defaults.ANTI_IDLE,
                InputBoosterConfig::isAntiIdleEnabled, InputBoosterConfig::setAntiIdleEnabled));

        movement.addOption(bool(builder, "features.auto_strafe",
                "Auto strafe",
                "Keep strafing while the mouse is used for something else.",
                InputBoosterConfig.Defaults.AUTO_STRAFE,
                InputBoosterConfig::isAutoStrafeEnabled, InputBoosterConfig::setAutoStrafeEnabled));

        movement.addOption(bool(builder, "features.combo_keys",
                "Combo keys",
                "Use Ctrl and the number keys to switch to a polling preset.",
                InputBoosterConfig.Defaults.COMBO_KEYS,
                InputBoosterConfig::isComboKeysEnabled, InputBoosterConfig::setComboKeysEnabled));

        page.addOptionGroup(movement);

        OptionGroupBuilder clicks = builder.createOptionGroup()
                .setName(Component.literal("Click limiter"));

        clicks.addOption(bool(builder, "features.cps_limiter",
                "CPS limiter",
                "Limit how many clicks per second are accepted.",
                InputBoosterConfig.Defaults.CPS_LIMITER,
                InputBoosterConfig::isCpsLimiterEnabled, InputBoosterConfig::setCpsLimiterEnabled));

        clicks.addOption(integer(builder, "features.max_cps",
                "Maximum CPS",
                "The highest number of clicks per second that is accepted.",
                InputBoosterConfig.Defaults.MAX_CPS, 1, 20, " CPS",
                InputBoosterConfig::getMaxCps, InputBoosterConfig::setMaxCps)
                .setEnabledProvider(state -> state.readBooleanOption(id("features.cps_limiter")),
                        id("features.cps_limiter"))
                .setControlHiddenWhenDisabled(false));

        clicks.addOption(bool(builder, "features.burst_mode",
                "Adaptive burst mode",
                "Raise the polling rate for a short while when the frame rate drops.",
                InputBoosterConfig.Defaults.BURST_MODE,
                InputBoosterConfig::isBurstModeEnabled, InputBoosterConfig::setBurstModeEnabled));

        page.addOptionGroup(clicks);
        return page;
    }

    private static OptionPageBuilder createOverlayPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.literal("Overlay"));

        OptionGroupBuilder visibility = builder.createOptionGroup()
                .setName(Component.literal("Visibility"));

        visibility.addOption(bool(builder, "overlay.show_f3_info",
                "F3 information",
                "Draw the InputBooster diagnostics overlay.",
                InputBoosterConfig.Defaults.SHOW_F3_INFO,
                InputBoosterConfig::isShowF3Info, InputBoosterConfig::setShowF3Info));

        visibility.addOption(bool(builder, "overlay.show_keystrokes",
                "Keystroke display",
                "Show which keys are currently held.",
                InputBoosterConfig.Defaults.SHOW_KEYSTROKES,
                InputBoosterConfig::isShowKeystrokes, InputBoosterConfig::setShowKeystrokes));

        visibility.addOption(bool(builder, "overlay.show_action_bar",
                "Action-bar messages",
                "Show InputBooster messages above the hotbar.",
                InputBoosterConfig.Defaults.SHOW_ACTION_BAR,
                InputBoosterConfig::isShowActionBar, InputBoosterConfig::setShowActionBar));

        visibility.addOption(enumOption(builder, "overlay.mode",
                "Display mode",
                "Choose when the diagnostics overlay is visible.",
                OverlayMode.class,
                InputBoosterConfig.Defaults.OVERLAY_MODE == 1 ? OverlayMode.ALWAYS : OverlayMode.F3_ONLY,
                () -> InputBoosterConfig.getOverlayMode() == 1 ? OverlayMode.ALWAYS : OverlayMode.F3_ONLY,
                mode -> InputBoosterConfig.setOverlayMode(mode == OverlayMode.ALWAYS ? 1 : 0),
                mode -> mode == OverlayMode.ALWAYS ? "Always" : "With F3 only"));

        page.addOptionGroup(visibility);

        OptionGroupBuilder appearance = builder.createOptionGroup()
                .setName(Component.literal("Appearance"));

        appearance.addOption(enumOption(builder, "overlay.position",
                "Overlay position",
                "The corner the overlay is drawn in.",
                OverlayPosition.class,
                OverlayPosition.values()[InputBoosterConfig.Defaults.OVERLAY_POSITION],
                () -> OverlayPosition.values()[InputBoosterConfig.getOverlayPosition()],
                position -> InputBoosterConfig.setOverlayPosition(position.ordinal()),
                position -> switch (position) {
                    case TOP_LEFT -> "Top left";
                    case TOP_RIGHT -> "Top right";
                    case BOTTOM_LEFT -> "Bottom left";
                    case BOTTOM_RIGHT -> "Bottom right";
                }));

        appearance.addOption(percent(builder, "overlay.opacity",
                "Opacity",
                "How opaque the background of the overlay is drawn.",
                Math.round(InputBoosterConfig.Defaults.OVERLAY_OPACITY * 100.0f), 0, 100,
                InputBoosterConfig::getOverlayOpacity, InputBoosterConfig::setOverlayOpacity)
                .setEnabledProvider(state -> state.readBooleanOption(id("overlay.show_f3_info")),
                        id("overlay.show_f3_info"))
                .setControlHiddenWhenDisabled(false));

        appearance.addOption(percent(builder, "overlay.scale",
                "Scale",
                "How large the overlay is drawn.",
                Math.round(InputBoosterConfig.Defaults.OVERLAY_SCALE * 100.0f), 50, 300,
                InputBoosterConfig::getOverlayScale, InputBoosterConfig::setOverlayScale,
                value -> times(value, "%.1fx"))
                .setEnabledProvider(state -> state.readBooleanOption(id("overlay.show_f3_info")),
                        id("overlay.show_f3_info"))
                .setControlHiddenWhenDisabled(false));

        page.addOptionGroup(appearance);

        OptionGroupBuilder diagnostics = builder.createOptionGroup()
                .setName(Component.literal("Diagnostics"));

        diagnostics.addOption(bool(builder, "overlay.show_engine",
                "Engine diagnostics",
                "Show the active input engine and the current polling rate.",
                InputBoosterConfig.Defaults.OVERLAY_SHOW_ENGINE,
                InputBoosterConfig::isOverlayShowEngine, InputBoosterConfig::setOverlayShowEngine));

        diagnostics.addOption(bool(builder, "overlay.show_events",
                "Event diagnostics",
                "Show the number of keyboard and mouse events that were seen.",
                InputBoosterConfig.Defaults.OVERLAY_SHOW_EVENTS,
                InputBoosterConfig::isOverlayShowEvents, InputBoosterConfig::setOverlayShowEvents));

        diagnostics.addOption(bool(builder, "overlay.show_overhead",
                "Performance overhead",
                "Show how long dispatching an input takes.",
                InputBoosterConfig.Defaults.OVERLAY_SHOW_OVERHEAD,
                InputBoosterConfig::isOverlayShowOverhead, InputBoosterConfig::setOverlayShowOverhead));

        page.addOptionGroup(diagnostics);
        return page;
    }

    private static OptionPageBuilder createAdvancedPage(ConfigBuilder builder) {
        OptionPageBuilder page = builder.createOptionPage()
                .setName(Component.literal("Advanced"));

        OptionGroupBuilder input = builder.createOptionGroup()
                .setName(Component.literal("Input handling"));

        input.addOption(bool(builder, "advanced.keyboard_repeat_guard",
                "Keyboard repeat guard",
                "Ignore repeated key-down events where a single press is expected.",
                InputBoosterConfig.Defaults.KEYBOARD_REPEAT_GUARD,
                InputBoosterConfig::isKeyboardRepeatGuard, InputBoosterConfig::setKeyboardRepeatGuard));

        input.addOption(integer(builder, "advanced.mouse_scroll_debounce_ms",
                "Mouse scroll debounce",
                "The shortest time between two accepted scroll events.",
                InputBoosterConfig.Defaults.MOUSE_SCROLL_DEBOUNCE_MS, 0, 200, " ms",
                InputBoosterConfig::getMouseScrollDebounceMs,
                InputBoosterConfig::setMouseScrollDebounceMs));

        input.addOption(integer(builder, "advanced.mouse_click_dedupe_ms",
                "Click deduplication",
                "The shortest time between two accepted clicks, so a duplicated "
                        + "event cannot be counted twice.",
                InputBoosterConfig.Defaults.MOUSE_CLICK_DEDUPE_MS, 0, 100, " ms",
                InputBoosterConfig::getMouseClickDedupeMs,
                InputBoosterConfig::setMouseClickDedupeMs));

        input.addOption(bool(builder, "advanced.custom_keyboard_handler",
                "Experimental keyboard handler",
                "Alpha. InputBooster reads the keyboard itself instead of using "
                        + "Minecraft's key mappings. Leave this off unless you are "
                        + "testing the alternate handler.",
                InputBoosterConfig.Defaults.CUSTOM_KEYBOARD_HANDLER,
                InputBoosterConfig::isCustomKeyboardHandlerEnabled,
                InputBoosterConfig::setCustomKeyboardHandlerEnabled));

        page.addOptionGroup(input);

        OptionGroupBuilder safety = builder.createOptionGroup()
                .setName(Component.literal("Safety and diagnostics"));

        safety.addOption(bool(builder, "advanced.safe_mode",
                "Safe mode",
                "Stop InputBooster and report the error when it misbehaves.",
                InputBoosterConfig.Defaults.SAFE_MODE,
                InputBoosterConfig::isSafeModeEnabled, InputBoosterConfig::setSafeModeEnabled));

        safety.addOption(bool(builder, "advanced.event_log",
                "Event logging",
                "Record the most recent InputBooster events for diagnostics.",
                InputBoosterConfig.Defaults.EVENT_LOG,
                InputBoosterConfig::isEventLogEnabled, InputBoosterConfig::setEventLogEnabled));

        safety.addOption(bool(builder, "advanced.key_conflict_warn",
                "Key conflict warnings",
                "Warn when a key mapping of another mod conflicts with InputBooster.",
                InputBoosterConfig.Defaults.KEY_CONFLICT_WARN,
                InputBoosterConfig::isKeyConflictWarn, InputBoosterConfig::setKeyConflictWarn));

        safety.addOption(bool(builder, "advanced.debug_mode",
                "Debug mode",
                "Write additional information to the game log.",
                InputBoosterConfig.Defaults.DEBUG_MODE,
                InputBoosterConfig::isDebugMode, InputBoosterConfig::setDebugMode));

        safety.addOption(integer(builder, "advanced.fps_check_interval",
                "FPS check interval",
                "How many ticks pass between two frame-rate measurements.",
                InputBoosterConfig.Defaults.FPS_CHECK_INTERVAL, 1, 100, " ticks",
                InputBoosterConfig::getFpsCheckInterval, InputBoosterConfig::setFpsCheckInterval));

        page.addOptionGroup(safety);

        OptionGroupBuilder data = builder.createOptionGroup()
                .setName(Component.literal("Profiles and replay"));

        data.addOption(bool(builder, "advanced.replay_enabled",
                "Replay recording",
                "Record input and play it back later.",
                InputBoosterConfig.Defaults.REPLAY_ENABLED,
                InputBoosterConfig::isReplayEnabled, InputBoosterConfig::setReplayEnabled));

        data.addOption(bool(builder, "advanced.per_server_profiles",
                "Per-server profiles",
                "Use the profile that belongs to the server that is connected.",
                InputBoosterConfig.Defaults.PER_SERVER_PROFILES,
                InputBoosterConfig::isPerServerProfiles, InputBoosterConfig::setPerServerProfiles));

        page.addOptionGroup(data);

        OptionGroupBuilder audio = builder.createOptionGroup()
                .setName(Component.literal("Click feedback"));

        audio.addOption(bool(builder, "advanced.click_sounds",
                "Click sounds",
                "Play a sound for every accepted click.",
                InputBoosterConfig.Defaults.CLICK_SOUNDS,
                InputBoosterConfig::isClickSoundsEnabled, InputBoosterConfig::setClickSoundsEnabled));

        audio.addOption(percent(builder, "advanced.click_sound_pitch",
                "Click sound pitch",
                "How high the click feedback is pitched.",
                Math.round(InputBoosterConfig.Defaults.CLICK_SOUND_PITCH * 100.0f), 50, 200,
                InputBoosterConfig::getClickSoundPitch, InputBoosterConfig::setClickSoundPitch,
                value -> times(value, "%.2fx"))
                .setEnabledProvider(state -> state.readBooleanOption(id("advanced.click_sounds")),
                        id("advanced.click_sounds"))
                .setControlHiddenWhenDisabled(false));

        audio.addOption(percent(builder, "advanced.click_sound_volume",
                "Click sound volume",
                "How loud the click feedback is.",
                Math.round(InputBoosterConfig.Defaults.CLICK_SOUND_VOLUME * 100.0f), 0, 100,
                InputBoosterConfig::getClickSoundVolume, InputBoosterConfig::setClickSoundVolume)
                .setEnabledProvider(state -> state.readBooleanOption(id("advanced.click_sounds")),
                        id("advanced.click_sounds"))
                .setControlHiddenWhenDisabled(false));

        page.addOptionGroup(audio);
        return page;
    }

    // ── Option helpers ───────────────────────────────────────────────────────

    private static BooleanOptionBuilder bool(ConfigBuilder builder, String optionId, String name,
                                              String tooltip, boolean defaultValue,
                                              Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return builder.createBooleanOption(id(optionId))
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setStorageHandler(SAVE)
                .setDefaultValue(defaultValue)
                .setBinding(setter, getter);
    }

    private static IntegerOptionBuilder integer(ConfigBuilder builder, String optionId, String name,
                                                String tooltip, int defaultValue,
                                                int min, int max, String suffix,
                                                Supplier<Integer> getter, Consumer<Integer> setter) {
        return integer(builder, optionId, name, tooltip, defaultValue, min, max,
                value -> value + suffix, getter, setter);
    }

    private static IntegerOptionBuilder integer(ConfigBuilder builder, String optionId, String name,
                                                String tooltip, int defaultValue,
                                                int min, int max, Function<Integer, String> formatter,
                                                Supplier<Integer> getter, Consumer<Integer> setter) {
        return builder.createIntegerOption(id(optionId))
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setStorageHandler(SAVE)
                .setDefaultValue(defaultValue)
                // A step of one keeps every value that a hand-edited configuration
                // file can hold valid. A larger step would make Sodium reset an
                // unusual but perfectly usable value back to the default.
                .setRange(min, max, 1)
                .setValueFormatter(value -> Component.literal(formatter.apply(value)))
                .setBinding(setter, getter);
    }

    /**
     * An integer option that is stored as a fraction between 0 and 1 but shown
     * as a percentage.
     */
    private static IntegerOptionBuilder percent(ConfigBuilder builder, String optionId, String name,
                                                 String tooltip, int defaultPercent, int min, int max,
                                                 Supplier<Float> getter, Consumer<Float> setter) {
        return percent(builder, optionId, name, tooltip, defaultPercent, min, max, getter, setter,
                value -> value + "%");
    }

    private static IntegerOptionBuilder percent(ConfigBuilder builder, String optionId, String name,
                                                 String tooltip, int defaultPercent, int min, int max,
                                                 Supplier<Float> getter, Consumer<Float> setter,
                                                 Function<Integer, String> formatter) {
        return integer(builder, optionId, name, tooltip, defaultPercent, min, max, formatter,
                () -> Math.round(getter.get() * 100.0f),
                value -> setter.accept(value / 100.0f));
    }

    /** Formats a stored fraction the way the mod's own settings screen does. */
    private static String times(int percent, String format) {
        return String.format(Locale.ROOT, format, percent / 100.0);
    }

    private static <E extends Enum<E>> EnumOptionBuilder<E> enumOption(ConfigBuilder builder, String optionId,
                                                                        String name, String tooltip,
                                                                        Class<E> enumClass, E defaultValue,
                                                                        Supplier<E> getter, Consumer<E> setter,
                                                                        Function<E, String> displayName) {
        return builder.createEnumOption(id(optionId), enumClass)
                .setName(Component.literal(name))
                .setTooltip(Component.literal(tooltip))
                .setStorageHandler(SAVE)
                .setDefaultValue(defaultValue)
                .setElementNameProvider(value -> Component.literal(displayName.apply(value)))
                .setBinding(setter, getter);
    }

    private static Identifier id(String path) {
        return Identifier.parse(NAMESPACE + ":" + path);
    }
}
