package dev.inputbooster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * InputBoosterConfig — Configuration management for InputBooster mod.
 *
 * Config file: .minecraft/config/inputbooster.properties
 *
 * v3.0.0 additions:
 *  - burstModeEnabled   — Adaptive Burst Mode (Feature 1)
 *  - maxCps             — Smart CPS Limiter cap (Feature 3, default 20)
 *  - comboKeysEnabled   — Combo Key System for poll rate presets (Feature 4)
 *
 * Version: 3.0.0
 * Author: Ahaduzzaman Khan
 */
public class InputBoosterConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("inputbooster-config");
    /**
     * Config location. Defaults to {@code config/inputbooster.properties} next to the
     * game; the {@code inputbooster.configDir} system property overrides the parent
     * directory so the automated tests can use a throwaway location.
     */
    private static final Path CONFIG_PATH = configDir().resolve("inputbooster.properties");

    /**
     * Directory holding every InputBooster file (config, profiles, server
     * bindings). The {@code inputbooster.configDir} system property overrides
     * the default {@code config} directory next to the game, which is what the
     * automated tests use.
     */
    public static Path configDir() {
        return Paths.get(System.getProperty("inputbooster.configDir", "config"));
    }

    /**
     * The documented default value of every setting.
     *
     * <p>These are the values {@link #resetDefaults()} restores and the values a
     * fresh configuration file starts with. User interfaces that offer a "reset to
     * default" action — the mod's own screen and the Sodium configuration pages —
     * read them from here instead of keeping a second copy of every number, so a
     * default can never drift away from the value that is actually written.
     */
    public static final class Defaults {
        private Defaults() {}

        public static final int     POLL_RATE_HZ             = 200;
        public static final boolean POLL_RATE_AUTO           = true;

        public static final boolean SPRINT_FIX               = true;
        public static final boolean AUTO_SPRINT              = true;
        public static final boolean WTAP_ASSIST              = true;
        public static final boolean ANTI_IDLE                = true;
        public static final boolean AUTO_STRAFE              = true;
        public static final boolean CPS_LIMITER              = true;
        public static final boolean BURST_MODE               = true;
        public static final int     MAX_CPS                  = 20;
        public static final boolean COMBO_KEYS               = true;
        public static final String  CPS_MODE                 = "FIXED";
        public static final boolean CUSTOM_KEYBOARD_HANDLER  = false;

        public static final int     ENGINE_MODE              = 0;
        public static final boolean KEYBOARD_REPEAT_GUARD    = true;
        public static final int     MOUSE_SCROLL_DEBOUNCE_MS = 0;
        public static final int     MOUSE_CLICK_DEDUPE_MS    = 0;
        public static final boolean COMPAT_FALLBACK          = true;

        public static final int     OVERLAY_MODE             = 0;
        public static final boolean OVERLAY_SHOW_ENGINE      = true;
        public static final boolean OVERLAY_SHOW_EVENTS      = true;
        public static final boolean OVERLAY_SHOW_OVERHEAD    = true;
        public static final boolean SHOW_F3_INFO             = true;
        public static final boolean SHOW_KEYSTROKES          = true;
        public static final boolean SHOW_ACTION_BAR          = true;
        public static final int     OVERLAY_POSITION         = 0;
        public static final float   OVERLAY_OPACITY          = 0.8f;
        public static final float   OVERLAY_SCALE            = 1.0f;
        /** ARGB colour of the overlay text. */
        public static final int     OVERLAY_COLOR            = 0xFFE6EDF3;
        /** Draws a bright test rectangle at the overlay anchor. */
        public static final boolean OVERLAY_TEST_RECT        = false;

        public static final boolean REPLAY_ENABLED           = true;
        public static final boolean SAFE_MODE                = true;
        public static final boolean EVENT_LOG                = true;
        public static final boolean KEY_CONFLICT_WARN        = true;
        public static final boolean PER_SERVER_PROFILES      = true;
        public static final boolean CLICK_SOUNDS             = false;
        public static final float   CLICK_SOUND_PITCH        = 1.35f;
        public static final float   CLICK_SOUND_VOLUME       = 0.35f;

        public static final int     FPS_CHECK_INTERVAL       = 20;
        public static final boolean DEBUG_MODE               = false;
    }

    // ── Poll Rate ────────────────────────────────────────────────────────────
    // Fields are volatile: the polling thread reads several of them while the
    // game/UI thread writes them.
    private static volatile int     pollRateHz        = Defaults.POLL_RATE_HZ;
    private static volatile boolean pollRateAutoMode  = Defaults.POLL_RATE_AUTO;

    // ── Features ─────────────────────────────────────────────────────────────
    private static volatile boolean sprintFixEnabled  = Defaults.SPRINT_FIX;
    private static volatile boolean autoSprintEnabled = Defaults.AUTO_SPRINT;
    private static volatile boolean wTapAssistEnabled = Defaults.WTAP_ASSIST;
    private static volatile boolean antiIdleEnabled   = Defaults.ANTI_IDLE;
    private static volatile boolean autoStrafeEnabled = Defaults.AUTO_STRAFE;
    private static volatile boolean cpsLimiterEnabled = Defaults.CPS_LIMITER;

    // ── New v3.0.0 features ──────────────────────────────────────────────────
    private static volatile boolean burstModeEnabled  = Defaults.BURST_MODE;   // Feature 1
    private static volatile int     maxCps            = Defaults.MAX_CPS;     // Feature 3 (1–20)
    private static volatile boolean comboKeysEnabled  = Defaults.COMBO_KEYS;   // Feature 4
    /**
     * Alpha, off by default: InputBooster uses its own keyboard handler instead
     * of borrowing Minecraft's key mappings. See KeyboardRouter.
     */
    private static volatile boolean customKeyboardHandler = Defaults.CUSTOM_KEYBOARD_HANDLER;

    // ── Input engine (4.1) ──────────────────────────────────────────────────
    /** 0 = automatic, 1 = event driven, 2 = polled. */
    private static volatile int     engineMode           = Defaults.ENGINE_MODE;
    private static volatile boolean keyboardRepeatGuard  = Defaults.KEYBOARD_REPEAT_GUARD;
    private static volatile int     mouseScrollDebounceMs = Defaults.MOUSE_SCROLL_DEBOUNCE_MS;
    private static volatile int     mouseClickDedupeMs    = Defaults.MOUSE_CLICK_DEDUPE_MS;
    private static volatile boolean compatFallback        = Defaults.COMPAT_FALLBACK;

    // ── Overlay (4.1) ────────────────────────────────────────────────────────
    /** 0 = only while the F3 debug screen is open, 1 = always. */
    private static volatile int     overlayMode          = Defaults.OVERLAY_MODE;
    private static volatile boolean overlayShowEngine    = Defaults.OVERLAY_SHOW_ENGINE;
    private static volatile boolean overlayShowEvents    = Defaults.OVERLAY_SHOW_EVENTS;
    private static volatile boolean overlayShowOverhead  = Defaults.OVERLAY_SHOW_OVERHEAD;
    private static volatile String  cpsMode           = Defaults.CPS_MODE;
    private static volatile boolean replayEnabled     = Defaults.REPLAY_ENABLED;
    private static volatile boolean safeModeEnabled   = Defaults.SAFE_MODE;
    private static volatile boolean eventLogEnabled   = Defaults.EVENT_LOG;
    private static volatile boolean keyConflictWarn   = Defaults.KEY_CONFLICT_WARN;
    private static volatile boolean perServerProfiles = Defaults.PER_SERVER_PROFILES;
    private static volatile boolean clickSoundsEnabled = Defaults.CLICK_SOUNDS;
    private static volatile float   clickSoundPitch   = Defaults.CLICK_SOUND_PITCH;
    private static volatile float   clickSoundVolume  = Defaults.CLICK_SOUND_VOLUME;
    private static volatile int     configVersion     = 306;

    // ── UI ───────────────────────────────────────────────────────────────────
    private static volatile boolean showF3Info        = Defaults.SHOW_F3_INFO;
    private static volatile boolean showKeystrokes    = Defaults.SHOW_KEYSTROKES;
    private static volatile boolean showActionBar     = Defaults.SHOW_ACTION_BAR;
    // 0=Top-Left 1=Top-Right 2=Bottom-Left 3=Bottom-Right 4=Top-Centre
    private static volatile int     overlayPosition   = Defaults.OVERLAY_POSITION;
    private static volatile float   overlayOpacity   = Defaults.OVERLAY_OPACITY;
    private static volatile float   overlayScale      = Defaults.OVERLAY_SCALE;
    private static volatile int     overlayColor      = Defaults.OVERLAY_COLOR;
    private static volatile boolean overlayTestRect   = Defaults.OVERLAY_TEST_RECT;

    // ── Advanced ─────────────────────────────────────────────────────────────
    private static volatile int     fpsCheckInterval  = Defaults.FPS_CHECK_INTERVAL;
    private static volatile boolean debugMode         = Defaults.DEBUG_MODE;

    // ── Poll Rate Presets ────────────────────────────────────────────────────

    public enum PollPreset {
        ULTRA_LOW(60,   "Ultra Low"),
        VERY_LOW(100,   "Very Low"),
        LOW(150,        "Low"),
        NORMAL(200,     "Normal"),
        HIGH(350,       "High"),
        ULTRA(500,      "Ultra"),
        EXTREME(750,    "Extreme"),
        INSANE(1000,    "Insane"),
        CUSTOM(-1,      "Custom");

        public final int    hz;
        public final String label;

        PollPreset(int hz, String label) { this.hz = hz; this.label = label; }

        public static PollPreset fromHz(int hz) {
            for (PollPreset p : values()) if (p.hz == hz) return p;
            return CUSTOM;
        }
    }

    // ── Load ─────────────────────────────────────────────────────────────────

    /**
     * True once the configuration file has been read.
     *
     * <p>Only {@link #ensureLoaded()} consults it; {@link #load()} always reads
     * the file so that a deliberate reload still happens.
     */
    private static volatile boolean loaded;

    /**
     * Reads the configuration file if it has not been read yet.
     *
     * <p>The configuration is normally loaded from
     * {@link InputBoosterMod#initialize()}, which runs on the first client tick.
     * Other entry points may need the stored values earlier — the Sodium
     * configuration pages, for instance, are built while the game finishes
     * loading and before that first tick. Reading the file a second time would
     * only risk discarding a value that was changed in between, so the second
     * read is skipped.
     */
    public static void ensureLoaded() {
        if (loaded) return;
        synchronized (InputBoosterConfig.class) {
            if (loaded) return;
            load();
        }
    }

    public static void load() {
        // FIX (stale settings after reload): load() only assigned the keys that
        // were present in the file, so every option missing from the file kept
        // whatever value it had in memory — importing a partial config or
        // switching profiles leaked the previous session's settings. Start from
        // the documented defaults every time before applying the file.
        resetDefaults();
        try {
            Properties props = new Properties();
            if (Files.exists(CONFIG_PATH)) {
                LOGGER.info("Loading config from {}", CONFIG_PATH);
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    props.load(in);
                }
                migrate(props);
                pollRateHz        = Math.max(60, Math.min(1000, parseInt(props, "poll_rate_hz",       Defaults.POLL_RATE_HZ)));
                pollRateAutoMode  = parseBool(props, "poll_rate_auto",        Defaults.POLL_RATE_AUTO);
                sprintFixEnabled  = parseBool(props, "sprint_fix",            Defaults.SPRINT_FIX);
                autoSprintEnabled = parseBool(props, "auto_sprint",           Defaults.AUTO_SPRINT);
                wTapAssistEnabled = parseBool(props, "wtap_assist",           Defaults.WTAP_ASSIST);
                antiIdleEnabled   = parseBool(props, "anti_idle",             Defaults.ANTI_IDLE);
                autoStrafeEnabled = parseBool(props, "auto_strafe",           Defaults.AUTO_STRAFE);
                cpsLimiterEnabled = parseBool(props, "cps_limiter",           Defaults.CPS_LIMITER);
                burstModeEnabled  = parseBool(props, "burst_mode",            Defaults.BURST_MODE);
                maxCps            = Math.max(1, Math.min(20, parseInt(props, "max_cps", Defaults.MAX_CPS)));
                comboKeysEnabled  = parseBool(props, "combo_keys",            Defaults.COMBO_KEYS);
                cpsMode           = props.getProperty("cps_mode", Defaults.CPS_MODE).toUpperCase(Locale.ROOT);
                replayEnabled     = parseBool(props, "replay_enabled",        Defaults.REPLAY_ENABLED);
                safeModeEnabled   = parseBool(props, "safe_mode",             Defaults.SAFE_MODE);
                eventLogEnabled   = parseBool(props, "event_log",             Defaults.EVENT_LOG);
                keyConflictWarn   = parseBool(props, "key_conflict_warn",     Defaults.KEY_CONFLICT_WARN);
                perServerProfiles = parseBool(props, "per_server_profiles",   Defaults.PER_SERVER_PROFILES);
                clickSoundsEnabled = parseBool(props, "click_sounds",         Defaults.CLICK_SOUNDS);
                clickSoundPitch   = Math.max(0.5f, Math.min(2.0f, parseFloat(props, "click_sound_pitch", Defaults.CLICK_SOUND_PITCH)));
                clickSoundVolume  = Math.max(0.0f, Math.min(1.0f, parseFloat(props, "click_sound_volume", Defaults.CLICK_SOUND_VOLUME)));
                configVersion     = Math.max(1, parseInt(props, "config_version", CONFIG_VERSION));
                showF3Info        = parseBool(props, "show_f3_info",          Defaults.SHOW_F3_INFO);
                showKeystrokes    = parseBool(props, "show_keystrokes",       Defaults.SHOW_KEYSTROKES);
                overlayPosition   = Math.max(0, Math.min(4, parseInt(props, "overlay_position", Defaults.OVERLAY_POSITION)));
                overlayOpacity   = Math.max(0.0f, Math.min(1.0f, parseFloat(props, "overlay_opacity", Defaults.OVERLAY_OPACITY)));
                overlayScale      = Math.max(0.5f, Math.min(3.0f, parseFloat(props, "overlay_scale", Defaults.OVERLAY_SCALE)));
                overlayColor      = parseInt(props, "overlay_color", Defaults.OVERLAY_COLOR);
                overlayTestRect   = parseBool(props, "overlay_test_rect", Defaults.OVERLAY_TEST_RECT);
                showActionBar     = parseBool(props, "show_action_bar",       Defaults.SHOW_ACTION_BAR);
                fpsCheckInterval  = Math.max(1, Math.min(100, parseInt(props, "fps_check_interval", Defaults.FPS_CHECK_INTERVAL)));
                debugMode         = parseBool(props, "debug_mode",            Defaults.DEBUG_MODE);
                customKeyboardHandler = parseBool(props, "custom_keyboard_handler", Defaults.CUSTOM_KEYBOARD_HANDLER);
                engineMode         = Math.max(0, Math.min(2, parseInt(props, "engine_mode", Defaults.ENGINE_MODE)));
                keyboardRepeatGuard = parseBool(props, "keyboard_repeat_guard", Defaults.KEYBOARD_REPEAT_GUARD);
                mouseScrollDebounceMs = Math.max(0, Math.min(200, parseInt(props, "mouse_scroll_debounce_ms", Defaults.MOUSE_SCROLL_DEBOUNCE_MS)));
                mouseClickDedupeMs = Math.max(0, Math.min(100, parseInt(props, "mouse_click_dedupe_ms", Defaults.MOUSE_CLICK_DEDUPE_MS)));
                compatFallback     = parseBool(props, "compat_fallback", Defaults.COMPAT_FALLBACK);
                overlayMode        = Math.max(0, Math.min(1, parseInt(props, "overlay_mode", Defaults.OVERLAY_MODE)));
                overlayShowEngine  = parseBool(props, "overlay_show_engine", Defaults.OVERLAY_SHOW_ENGINE);
                overlayShowEvents  = parseBool(props, "overlay_show_events", Defaults.OVERLAY_SHOW_EVENTS);
                overlayShowOverhead = parseBool(props, "overlay_show_overhead", Defaults.OVERLAY_SHOW_OVERHEAD);
                warnAboutDefaults(props);
                LOGGER.info("✓ Config loaded successfully (config version {})", configVersion);
            } else {
                LOGGER.info("No config found, creating defaults...");
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
            resetDefaults();
        } finally {
            loaded = true;
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    public static void save() {
        // Synchronised + written through a temp file: the UI thread, the game
        // thread and shutdown can all save, and a truncated file used to wipe
        // the user's settings.
        synchronized (InputBoosterConfig.class) {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            Properties props = new Properties();
            props.setProperty("poll_rate_hz",        String.valueOf(pollRateHz));
            props.setProperty("poll_rate_auto",      String.valueOf(pollRateAutoMode));
            props.setProperty("sprint_fix",          String.valueOf(sprintFixEnabled));
            props.setProperty("auto_sprint",         String.valueOf(autoSprintEnabled));
            props.setProperty("wtap_assist",         String.valueOf(wTapAssistEnabled));
            props.setProperty("anti_idle",           String.valueOf(antiIdleEnabled));
            props.setProperty("auto_strafe",         String.valueOf(autoStrafeEnabled));
            props.setProperty("cps_limiter",         String.valueOf(cpsLimiterEnabled));
            props.setProperty("burst_mode",          String.valueOf(burstModeEnabled));
            props.setProperty("max_cps",             String.valueOf(maxCps));
            props.setProperty("combo_keys",          String.valueOf(comboKeysEnabled));
            props.setProperty("cps_mode",            cpsMode);
            props.setProperty("replay_enabled",      String.valueOf(replayEnabled));
            props.setProperty("safe_mode",           String.valueOf(safeModeEnabled));
            props.setProperty("event_log",           String.valueOf(eventLogEnabled));
            props.setProperty("key_conflict_warn",   String.valueOf(keyConflictWarn));
            props.setProperty("per_server_profiles", String.valueOf(perServerProfiles));
            props.setProperty("click_sounds",        String.valueOf(clickSoundsEnabled));
            props.setProperty("click_sound_pitch",   String.valueOf(clickSoundPitch));
            props.setProperty("click_sound_volume",  String.valueOf(clickSoundVolume));
            props.setProperty("config_version",      String.valueOf(configVersion));
            props.setProperty("show_f3_info",        String.valueOf(showF3Info));
            props.setProperty("show_keystrokes",     String.valueOf(showKeystrokes));
            props.setProperty("overlay_position",    String.valueOf(overlayPosition));
            props.setProperty("overlay_opacity",   String.valueOf(overlayOpacity));
            props.setProperty("overlay_scale",       String.valueOf(overlayScale));
            props.setProperty("overlay_color",       String.valueOf(overlayColor));
            props.setProperty("overlay_test_rect",   String.valueOf(overlayTestRect));
            props.setProperty("show_action_bar",     String.valueOf(showActionBar));
            props.setProperty("fps_check_interval",  String.valueOf(fpsCheckInterval));
            props.setProperty("debug_mode",          String.valueOf(debugMode));
            props.setProperty("custom_keyboard_handler", String.valueOf(customKeyboardHandler));
            props.setProperty("engine_mode",             String.valueOf(engineMode));
            props.setProperty("keyboard_repeat_guard",    String.valueOf(keyboardRepeatGuard));
            props.setProperty("mouse_scroll_debounce_ms", String.valueOf(mouseScrollDebounceMs));
            props.setProperty("mouse_click_dedupe_ms",    String.valueOf(mouseClickDedupeMs));
            props.setProperty("compat_fallback",          String.valueOf(compatFallback));
            props.setProperty("overlay_mode",             String.valueOf(overlayMode));
            props.setProperty("overlay_show_engine",      String.valueOf(overlayShowEngine));
            props.setProperty("overlay_show_events",      String.valueOf(overlayShowEvents));
            props.setProperty("overlay_show_overhead",    String.valueOf(overlayShowOverhead));
            Path temp = Files.createTempFile(parent, "inputbooster", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "InputBooster " + dev.inputbooster.InputBoosterMod.MOD_VERSION
                    + " configuration - by Ahaduzzaman Khan");
            }
            try {
                Files.move(temp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("✓ Config saved to {}", CONFIG_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // ── Schema migration ───────────────────────────────────────────────────────

    /** Current on-disk schema version. */
    public static final int CONFIG_VERSION = 306;

    /**
     * Migrates older config files to the current schema in place.
     *
     * v1–v303 used the same key names but stored {@code config_version} without
     * any upgrade path, and some early files carried unbounded values. Anything
     * unreadable falls back to its default and is reported once, so a partially
     * broken file produces a clean, explained reset instead of a mix of
     * silently-defaulted fields.
     */
    private static void migrate(Properties props) {
        int version = Math.max(1, parseInt(props, "config_version", 1));
        if (version < CONFIG_VERSION) {
            LOGGER.info("Migrating config from version {} to {}", version, CONFIG_VERSION);
            // Legacy aliases kept by very old builds.
            rename(props, "auto_sprint_key", "auto_sprint");
            rename(props, "watap", "wtap_assist");
            rename(props, "pollrate", "poll_rate_hz");
            version = CONFIG_VERSION;
        }
        configVersion = version;
        props.setProperty("config_version", String.valueOf(version));
    }

    private static void rename(Properties props, String from, String to) {
        String value = props.getProperty(from);
        if (value != null && props.getProperty(to) == null) {
            props.setProperty(to, value);
        }
        props.remove(from);
    }

    /** Reports every key whose stored value could not be used. */
    private static void warnAboutDefaults(Properties props) {
        List<String> bad = new ArrayList<>();
        checkBool(props, bad, "poll_rate_auto");
        checkBool(props, bad, "sprint_fix");
        checkBool(props, bad, "auto_sprint");
        checkBool(props, bad, "wtap_assist");
        checkBool(props, bad, "anti_idle");
        checkBool(props, bad, "auto_strafe");
        checkBool(props, bad, "cps_limiter");
        checkBool(props, bad, "burst_mode");
        checkBool(props, bad, "combo_keys");
        checkBool(props, bad, "custom_keyboard_handler");
        checkBool(props, bad, "keyboard_repeat_guard");
        checkBool(props, bad, "compat_fallback");
        checkBool(props, bad, "overlay_show_engine");
        checkBool(props, bad, "overlay_show_events");
        checkBool(props, bad, "overlay_show_overhead");
        checkInt(props, bad, "engine_mode", 0, 2);
        checkInt(props, bad, "mouse_scroll_debounce_ms", 0, 200);
        checkInt(props, bad, "mouse_click_dedupe_ms", 0, 100);
        checkInt(props, bad, "overlay_mode", 0, 1);
        checkInt(props, bad, "poll_rate_hz", 60, 1000);
        checkInt(props, bad, "max_cps", 1, 20);
        checkInt(props, bad, "overlay_position", 0, 4);
        checkInt(props, bad, "fps_check_interval", 1, 100);
        if (!bad.isEmpty()) {
            LOGGER.warn("Config entries reset to defaults (invalid or unparsable): {}", String.join(", ", bad));
        }
    }

    private static void checkBool(Properties props, List<String> bad, String key) {
        String raw = props.getProperty(key);
        if (raw == null) return;
        String v = raw.trim();
        boolean ok = v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")
            || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("no")
            || v.equals("1") || v.equals("0")
            || v.equalsIgnoreCase("on") || v.equalsIgnoreCase("off");
        if (!ok) bad.add(key);
    }

    private static void checkInt(Properties props, List<String> bad, String key, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null) return;
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) bad.add(key + "=" + raw.trim());
        } catch (NumberFormatException e) {
            bad.add(key + "=" + raw.trim());
        }
    }

    private static float parseFloat(java.util.Properties p, String k, float def) {
        try { return Float.parseFloat(p.getProperty(k, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    private static int parseInt(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean parseBool(Properties p, String key, boolean def) {
        // FIX (hand-edited configs silently disabling features):
        // Boolean.parseBoolean only accepts a lowercase "true", so "True",
        // "yes" or "1" all resolved to false. Accept the common spellings.
        String raw = p.getProperty(key);
        if (raw == null) return def;
        String v = raw.trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1") || v.equalsIgnoreCase("on")) return true;
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equals("0") || v.equalsIgnoreCase("off")) return false;
        return def;
    }

    private static void resetDefaults() {
        pollRateHz = Defaults.POLL_RATE_HZ; pollRateAutoMode = Defaults.POLL_RATE_AUTO;
        sprintFixEnabled = Defaults.SPRINT_FIX; autoSprintEnabled = Defaults.AUTO_SPRINT; wTapAssistEnabled = Defaults.WTAP_ASSIST;
        antiIdleEnabled = Defaults.ANTI_IDLE; autoStrafeEnabled = Defaults.AUTO_STRAFE; cpsLimiterEnabled = Defaults.CPS_LIMITER;
        burstModeEnabled = Defaults.BURST_MODE; maxCps = Defaults.MAX_CPS; comboKeysEnabled = Defaults.COMBO_KEYS;
        cpsMode = Defaults.CPS_MODE; replayEnabled = Defaults.REPLAY_ENABLED; safeModeEnabled = Defaults.SAFE_MODE;
        eventLogEnabled = Defaults.EVENT_LOG; keyConflictWarn = Defaults.KEY_CONFLICT_WARN; perServerProfiles = Defaults.PER_SERVER_PROFILES;
        clickSoundsEnabled = Defaults.CLICK_SOUNDS; clickSoundPitch = Defaults.CLICK_SOUND_PITCH; clickSoundVolume = Defaults.CLICK_SOUND_VOLUME;
        // Start at the current schema version. Writing 303 here made every
        // fresh config advertise an old version and migrate itself on the next
        // load.
        configVersion = CONFIG_VERSION;
        showF3Info = Defaults.SHOW_F3_INFO; showKeystrokes = Defaults.SHOW_KEYSTROKES; showActionBar = Defaults.SHOW_ACTION_BAR;
        fpsCheckInterval = Defaults.FPS_CHECK_INTERVAL; debugMode = Defaults.DEBUG_MODE;
        customKeyboardHandler = Defaults.CUSTOM_KEYBOARD_HANDLER;
        engineMode = Defaults.ENGINE_MODE; keyboardRepeatGuard = Defaults.KEYBOARD_REPEAT_GUARD;
        mouseScrollDebounceMs = Defaults.MOUSE_SCROLL_DEBOUNCE_MS; mouseClickDedupeMs = Defaults.MOUSE_CLICK_DEDUPE_MS;
        compatFallback = Defaults.COMPAT_FALLBACK;
        overlayMode = Defaults.OVERLAY_MODE; overlayShowEngine = Defaults.OVERLAY_SHOW_ENGINE;
        overlayShowEvents = Defaults.OVERLAY_SHOW_EVENTS; overlayShowOverhead = Defaults.OVERLAY_SHOW_OVERHEAD;
        overlayPosition = Defaults.OVERLAY_POSITION; overlayScale = Defaults.OVERLAY_SCALE; overlayOpacity = Defaults.OVERLAY_OPACITY;
        overlayColor = Defaults.OVERLAY_COLOR; overlayTestRect = Defaults.OVERLAY_TEST_RECT;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public static int     getPollRateHz()        { return pollRateHz; }
    public static boolean isPollRateAutoMode()   { return pollRateAutoMode; }
    public static boolean isSprintFixEnabled()   { return sprintFixEnabled; }
    public static boolean isAutoSprintEnabled()  { return autoSprintEnabled; }
    public static boolean isWTapAssistEnabled()  { return wTapAssistEnabled; }
    public static boolean isAntiIdleEnabled()    { return antiIdleEnabled; }
    public static boolean isAutoStrafeEnabled()  { return autoStrafeEnabled; }
    public static boolean isCpsLimiterEnabled()  { return cpsLimiterEnabled; }
    public static boolean isBurstModeEnabled()   { return burstModeEnabled; }
    public static int     getMaxCps()            { return maxCps; }
    public static boolean isComboKeysEnabled()   { return comboKeysEnabled; }
    public static String  getCpsMode()           { return cpsMode; }
    public static boolean isReplayEnabled()      { return replayEnabled; }
    public static boolean isSafeModeEnabled()    { return safeModeEnabled; }
    public static boolean isEventLogEnabled()    { return eventLogEnabled; }
    public static boolean isKeyConflictWarn()    { return keyConflictWarn; }
    public static boolean isPerServerProfiles()  { return perServerProfiles; }
    public static boolean isClickSoundsEnabled() { return clickSoundsEnabled; }
    public static float   getClickSoundPitch()   { return clickSoundPitch; }
    public static float   getClickSoundVolume()  { return clickSoundVolume; }
    public static int     getConfigVersion()     { return configVersion; }
    public static boolean isShowF3Info()         { return showF3Info; }
    public static boolean isShowKeystrokes()     { return showKeystrokes; }
    public static int     getOverlayPosition()   { return overlayPosition; }
    public static float   getOverlayOpacity()   { return overlayOpacity; }
    public static float   getOverlayScale()      { return overlayScale; }
    public static int     getOverlayColor()      { return overlayColor; }
    public static boolean isOverlayTestRect()    { return overlayTestRect; }
    public static boolean isShowActionBar()      { return showActionBar; }
    public static int     getFpsCheckInterval()  { return fpsCheckInterval; }
    public static boolean isDebugMode()          { return debugMode; }
    public static boolean isCustomKeyboardHandlerEnabled() { return customKeyboardHandler; }
    public static int     getEngineMode()        { return engineMode; }
    public static boolean isKeyboardRepeatGuard() { return keyboardRepeatGuard; }
    public static int     getMouseScrollDebounceMs() { return mouseScrollDebounceMs; }
    public static int     getMouseClickDedupeMs() { return mouseClickDedupeMs; }
    public static boolean isCompatFallback()     { return compatFallback; }
    public static int     getOverlayMode()       { return overlayMode; }
    public static boolean isOverlayShowEngine()  { return overlayShowEngine; }
    public static boolean isOverlayShowEvents()  { return overlayShowEvents; }
    public static boolean isOverlayShowOverhead() { return overlayShowOverhead; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public static void setPollRateHz(int hz)         { pollRateHz = Math.max(60, Math.min(1000, hz)); }
    public static void setPollRateAutoMode(boolean v){ pollRateAutoMode = v; }
    public static void setSprintFixEnabled(boolean v){ sprintFixEnabled = v; }
    public static void setAutoSprintEnabled(boolean v){ autoSprintEnabled = v; }
    public static void setWTapAssistEnabled(boolean v){ wTapAssistEnabled = v; }
    public static void setAntiIdleEnabled(boolean v) { antiIdleEnabled = v; }
    public static void setAutoStrafeEnabled(boolean v){ autoStrafeEnabled = v; }
    public static void setCpsLimiterEnabled(boolean v){ cpsLimiterEnabled = v; }
    public static void setBurstModeEnabled(boolean v) { burstModeEnabled = v; }
    public static void setMaxCps(int v)               { maxCps = Math.max(1, Math.min(20, v)); }
    public static void setComboKeysEnabled(boolean v) { comboKeysEnabled = v; }
    public static void setCpsMode(String v)            { cpsMode = (v == null ? "FIXED" : v.toUpperCase(Locale.ROOT)); }
    public static void setReplayEnabled(boolean v)     { replayEnabled = v; }
    public static void setSafeModeEnabled(boolean v)   { safeModeEnabled = v; }
    public static void setEventLogEnabled(boolean v)   { eventLogEnabled = v; }
    public static void setKeyConflictWarn(boolean v)   { keyConflictWarn = v; }
    public static void setPerServerProfiles(boolean v) { perServerProfiles = v; }
    public static void setClickSoundsEnabled(boolean v) { clickSoundsEnabled = v; }
    public static void setClickSoundPitch(float v)      { clickSoundPitch = Math.max(0.5f, Math.min(2.0f, v)); }
    public static void setClickSoundVolume(float v)     { clickSoundVolume = Math.max(0.0f, Math.min(1.0f, v)); }
    public static void setShowF3Info(boolean v)       { showF3Info = v; }
    public static void setShowKeystrokes(boolean v)   { showKeystrokes = v; }
    public static void setOverlayPosition(int v)      { overlayPosition = Math.max(0, Math.min(4, v)); }
    public static void setOverlayOpacity(float v)    { overlayOpacity = Math.max(0.0f, Math.min(1.0f, v)); }
    public static void setOverlayScale(float v)       { overlayScale = Math.max(0.5f, Math.min(3.0f, v)); }
    public static void setOverlayColor(int v)        { overlayColor = v; }
    public static void setOverlayTestRect(boolean v) { overlayTestRect = v; }
    public static void setShowActionBar(boolean v)    { showActionBar = v; }
    public static void setFpsCheckInterval(int v)     { fpsCheckInterval = Math.max(1, Math.min(100, v)); }
    public static void setDebugMode(boolean v)        { debugMode = v; }
    public static void setCustomKeyboardHandlerEnabled(boolean v) { customKeyboardHandler = v; }
    public static void setEngineMode(int v)        { engineMode = Math.max(0, Math.min(2, v)); }
    public static void setKeyboardRepeatGuard(boolean v) { keyboardRepeatGuard = v; }
    public static void setMouseScrollDebounceMs(int v)   { mouseScrollDebounceMs = Math.max(0, Math.min(200, v)); }
    public static void setMouseClickDedupeMs(int v)      { mouseClickDedupeMs = Math.max(0, Math.min(100, v)); }
    public static void setCompatFallback(boolean v)      { compatFallback = v; }
    public static void setOverlayMode(int v)             { overlayMode = Math.max(0, Math.min(1, v)); }
    public static void setOverlayShowEngine(boolean v)   { overlayShowEngine = v; }
    public static void setOverlayShowEvents(boolean v)   { overlayShowEvents = v; }
    public static void setOverlayShowOverhead(boolean v) { overlayShowOverhead = v; }
}
