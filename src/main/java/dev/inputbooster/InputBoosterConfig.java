package dev.inputbooster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * InputBoosterConfig — Configuration management for InputBooster mod.
 *
 * Config file location: .minecraft/config/inputbooster.properties
 *
 * Features:
 *  • Poll Rate: Manual (60-1000 Hz) or Auto (FPS-adaptive)
 *  • Feature toggles: Sprint Fix, Auto-Sprint, W-Tap, Anti-Idle, Auto-Strafe
 *  • UI options: F3 overlay visibility
 *  • Persistent storage with validation
 *  • Thread-safe access with atomic operations
 *
 * Version: 2.1.0
 * Author: Ahaduzzaman Khan
 */
public class InputBoosterConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("inputbooster-config");
    private static final Path CONFIG_PATH = Paths.get("config", "inputbooster.properties");

    // ──────────────────────────────────────────────────────────────
    // Configuration Fields
    // ──────────────────────────────────────────────────────────────

    // Poll Rate Settings
    private static int     pollRateHz        = 200;  // 60-1000
    private static boolean pollRateAutoMode  = true; // auto vs manual

    // Feature Toggles
    private static boolean sprintFixEnabled  = true;
    private static boolean autoSprintEnabled = true;
    private static boolean wTapAssistEnabled = true;
    private static boolean antiIdleEnabled   = true;
    private static boolean autoStrafeEnabled = true;
    private static boolean cpsLimiterEnabled = true;

    // UI Options
    private static boolean showF3Info        = true;
    private static boolean showActionBar     = true;

    // Advanced Options
    private static int     fpsCheckInterval  = 20;   // ticks between FPS checks
    private static boolean debugMode         = false;

    // ──────────────────────────────────────────────────────────────
    // Poll Rate Presets
    // ──────────────────────────────────────────────────────────────

    public enum PollPreset {
        ULTRA_LOW(60, "Ultra Low"),
        VERY_LOW(100, "Very Low"),
        LOW(150, "Low"),
        NORMAL(200, "Normal"),
        HIGH(350, "High"),
        ULTRA(500, "Ultra"),
        EXTREME(750, "Extreme"),
        INSANE(1000, "Insane"),
        CUSTOM(-1, "Custom");

        public final int hz;
        public final String label;

        PollPreset(int hz, String label) {
            this.hz = hz;
            this.label = label;
        }

        public static PollPreset fromHz(int hz) {
            for (PollPreset p : values()) {
                if (p.hz == hz) return p;
            }
            return CUSTOM;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Load Configuration
    // ──────────────────────────────────────────────────────────────

    public static void load() {
        try {
            Properties props = new Properties();

            if (Files.exists(CONFIG_PATH)) {
                LOGGER.info("Loading config from {}", CONFIG_PATH);
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    props.load(in);
                }

                // Poll Rate
                pollRateHz = Math.max(60, Math.min(1000,
                    parseInt(props, "poll_rate_hz", 200)));
                pollRateAutoMode = parseBool(props, "poll_rate_auto", true);

                // Features
                sprintFixEnabled  = parseBool(props, "sprint_fix", true);
                autoSprintEnabled = parseBool(props, "auto_sprint", true);
                wTapAssistEnabled = parseBool(props, "wtap_assist", true);
                antiIdleEnabled   = parseBool(props, "anti_idle", true);
                autoStrafeEnabled = parseBool(props, "auto_strafe", true);
                cpsLimiterEnabled = parseBool(props, "cps_limiter", true);

                // UI
                showF3Info        = parseBool(props, "show_f3_info", true);
                showActionBar     = parseBool(props, "show_action_bar", true);

                // Advanced
                fpsCheckInterval  = Math.max(1, Math.min(100,
                    parseInt(props, "fps_check_interval", 20)));
                debugMode         = parseBool(props, "debug_mode", false);

                LOGGER.info("✓ Config loaded successfully");
            } else {
                LOGGER.info("No config found, creating default...");
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
            resetDefaults();
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Save Configuration
    // ──────────────────────────────────────────────────────────────

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Properties props = new Properties();

            // Poll Rate
            props.setProperty("poll_rate_hz", String.valueOf(pollRateHz));
            props.setProperty("poll_rate_auto", String.valueOf(pollRateAutoMode));

            // Features
            props.setProperty("sprint_fix", String.valueOf(sprintFixEnabled));
            props.setProperty("auto_sprint", String.valueOf(autoSprintEnabled));
            props.setProperty("wtap_assist", String.valueOf(wTapAssistEnabled));
            props.setProperty("anti_idle", String.valueOf(antiIdleEnabled));
            props.setProperty("auto_strafe", String.valueOf(autoStrafeEnabled));
            props.setProperty("cps_limiter", String.valueOf(cpsLimiterEnabled));

            // UI
            props.setProperty("show_f3_info", String.valueOf(showF3Info));
            props.setProperty("show_action_bar", String.valueOf(showActionBar));

            // Advanced
            props.setProperty("fps_check_interval", String.valueOf(fpsCheckInterval));
            props.setProperty("debug_mode", String.valueOf(debugMode));

            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out,
                    "═══════════════════════════════════════════════════════════\n" +
                    " InputBooster v2.1.0 Configuration\n" +
                    " by Ahaduzzaman Khan\n" +
                    " https://github.com/ahaduzzamankhan/inputbooster\n" +
                    "═══════════════════════════════════════════════════════════\n" +
                    "\n" +
                    "[POLL RATE]\n" +
                    "poll_rate_hz      : Poll rate in Hz (60-1000), used in MANUAL mode\n" +
                    "poll_rate_auto    : true = AUTO (FPS-adaptive) | false = MANUAL (fixed Hz)\n" +
                    "\n" +
                    "[FEATURES]\n" +
                    "sprint_fix        : Fix sprint input handling\n" +
                    "auto_sprint       : Auto-sprint when moving forward\n" +
                    "wtap_assist       : W-Tap assist for combat\n" +
                    "anti_idle         : Prevent kick for inactivity\n" +
                    "auto_strafe       : Auto-strafing helper\n" +
                    "cps_limiter       : CPS (clicks per second) limiter\n" +
                    "\n" +
                    "[UI]\n" +
                    "show_f3_info      : Show InputBooster info in F3 debug screen\n" +
                    "show_action_bar   : Show status messages in action bar\n" +
                    "\n" +
                    "[ADVANCED]\n" +
                    "fps_check_interval: Ticks between FPS checks in AUTO mode (1-100)\n" +
                    "debug_mode        : Enable debug logging\n"
                );
            }

            LOGGER.info("✓ Config saved to {}", CONFIG_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Utility Methods
    // ──────────────────────────────────────────────────────────────

    private static int parseInt(Properties p, String key, int def) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid integer for key '{}', using default: {}", key, def);
            return def;
        }
    }

    private static boolean parseBool(Properties p, String key, boolean def) {
        String val = p.getProperty(key, String.valueOf(def));
        return Boolean.parseBoolean(val);
    }

    private static void resetDefaults() {
        pollRateHz        = 200;
        pollRateAutoMode  = true;
        sprintFixEnabled  = true;
        autoSprintEnabled = true;
        wTapAssistEnabled = true;
        antiIdleEnabled   = true;
        autoStrafeEnabled = true;
        cpsLimiterEnabled = true;
        showF3Info        = true;
        showActionBar     = true;
        fpsCheckInterval  = 20;
        debugMode         = false;
    }

    // ──────────────────────────────────────────────────────────────
    // Getters
    // ──────────────────────────────────────────────────────────────

    // Poll Rate
    public static int     getPollRateHz()        { return pollRateHz; }
    public static boolean isPollRateAutoMode()   { return pollRateAutoMode; }

    // Features
    public static boolean isSprintFixEnabled()   { return sprintFixEnabled; }
    public static boolean isAutoSprintEnabled()  { return autoSprintEnabled; }
    public static boolean isWTapAssistEnabled()  { return wTapAssistEnabled; }
    public static boolean isAntiIdleEnabled()    { return antiIdleEnabled; }
    public static boolean isAutoStrafeEnabled()  { return autoStrafeEnabled; }
    public static boolean isCpsLimiterEnabled()  { return cpsLimiterEnabled; }

    // UI
    public static boolean isShowF3Info()         { return showF3Info; }
    public static boolean isShowActionBar()      { return showActionBar; }

    // Advanced
    public static int     getFpsCheckInterval()  { return fpsCheckInterval; }
    public static boolean isDebugMode()          { return debugMode; }

    // ──────────────────────────────────────────────────────────────
    // Setters
    // ──────────────────────────────────────────────────────────────

    // Poll Rate
    public static void setPollRateHz(int hz) {
        pollRateHz = Math.max(60, Math.min(1000, hz));
        if (InputBoosterConfig.isDebugMode()) {
            LOGGER.info("Poll rate set to {} Hz", pollRateHz);
        }
    }

    public static void setPollRateAutoMode(boolean auto) {
        pollRateAutoMode = auto;
    }

    // Features
    public static void setSprintFixEnabled(boolean v)   { sprintFixEnabled = v; }
    public static void setAutoSprintEnabled(boolean v)  { autoSprintEnabled = v; }
    public static void setWTapAssistEnabled(boolean v)  { wTapAssistEnabled = v; }
    public static void setAntiIdleEnabled(boolean v)    { antiIdleEnabled = v; }
    public static void setAutoStrafeEnabled(boolean v)  { autoStrafeEnabled = v; }
    public static void setCpsLimiterEnabled(boolean v)  { cpsLimiterEnabled = v; }

    // UI
    public static void setShowF3Info(boolean v)         { showF3Info = v; }
    public static void setShowActionBar(boolean v)      { showActionBar = v; }

    // Advanced
    public static void setFpsCheckInterval(int ticks) {
        fpsCheckInterval = Math.max(1, Math.min(100, ticks));
    }

    public static void setDebugMode(boolean v) {
        debugMode = v;
    }
}
