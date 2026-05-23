package dev.inputbooster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

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
    private static final Path CONFIG_PATH = Paths.get("config", "inputbooster.properties");

    // ── Poll Rate ────────────────────────────────────────────────────────────
    private static int     pollRateHz        = 200;
    private static boolean pollRateAutoMode  = true;

    // ── Features ─────────────────────────────────────────────────────────────
    private static boolean sprintFixEnabled  = true;
    private static boolean autoSprintEnabled = true;
    private static boolean wTapAssistEnabled = true;
    private static boolean antiIdleEnabled   = true;
    private static boolean autoStrafeEnabled = true;
    private static boolean cpsLimiterEnabled = true;

    // ── New v3.0.0 features ──────────────────────────────────────────────────
    private static boolean burstModeEnabled  = true;   // Feature 1
    private static int     maxCps            = 20;     // Feature 3 (1–20)
    private static boolean comboKeysEnabled  = true;   // Feature 4

    // ── UI ───────────────────────────────────────────────────────────────────
    private static boolean showF3Info        = true;
    private static boolean showActionBar     = true;

    // ── Advanced ─────────────────────────────────────────────────────────────
    private static int     fpsCheckInterval  = 20;
    private static boolean debugMode         = false;

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

    public static void load() {
        try {
            Properties props = new Properties();
            if (Files.exists(CONFIG_PATH)) {
                LOGGER.info("Loading config from {}", CONFIG_PATH);
                try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                    props.load(in);
                }
                pollRateHz        = Math.max(60, Math.min(1000, parseInt(props, "poll_rate_hz",       200)));
                pollRateAutoMode  = parseBool(props, "poll_rate_auto",        true);
                sprintFixEnabled  = parseBool(props, "sprint_fix",            true);
                autoSprintEnabled = parseBool(props, "auto_sprint",           true);
                wTapAssistEnabled = parseBool(props, "wtap_assist",           true);
                antiIdleEnabled   = parseBool(props, "anti_idle",             true);
                autoStrafeEnabled = parseBool(props, "auto_strafe",           true);
                cpsLimiterEnabled = parseBool(props, "cps_limiter",           true);
                burstModeEnabled  = parseBool(props, "burst_mode",            true);
                maxCps            = Math.max(1, Math.min(20, parseInt(props, "max_cps", 20)));
                comboKeysEnabled  = parseBool(props, "combo_keys",            true);
                showF3Info        = parseBool(props, "show_f3_info",          true);
                showActionBar     = parseBool(props, "show_action_bar",       true);
                fpsCheckInterval  = Math.max(1, Math.min(100, parseInt(props, "fps_check_interval", 20)));
                debugMode         = parseBool(props, "debug_mode",            false);
                LOGGER.info("✓ Config loaded successfully");
            } else {
                LOGGER.info("No config found, creating defaults...");
                save();
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load config, using defaults", e);
            resetDefaults();
        }
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
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
            props.setProperty("show_f3_info",        String.valueOf(showF3Info));
            props.setProperty("show_action_bar",     String.valueOf(showActionBar));
            props.setProperty("fps_check_interval",  String.valueOf(fpsCheckInterval));
            props.setProperty("debug_mode",          String.valueOf(debugMode));
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "InputBooster v3.0.0 Configuration — by Ahaduzzaman Khan");
            }
            LOGGER.info("✓ Config saved to {}", CONFIG_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int parseInt(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static boolean parseBool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }

    private static void resetDefaults() {
        pollRateHz = 200; pollRateAutoMode = true;
        sprintFixEnabled = true; autoSprintEnabled = true; wTapAssistEnabled = true;
        antiIdleEnabled = true; autoStrafeEnabled = true; cpsLimiterEnabled = true;
        burstModeEnabled = true; maxCps = 20; comboKeysEnabled = true;
        showF3Info = true; showActionBar = true; fpsCheckInterval = 20; debugMode = false;
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
    public static boolean isShowF3Info()         { return showF3Info; }
    public static boolean isShowActionBar()      { return showActionBar; }
    public static int     getFpsCheckInterval()  { return fpsCheckInterval; }
    public static boolean isDebugMode()          { return debugMode; }

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
    public static void setShowF3Info(boolean v)       { showF3Info = v; }
    public static void setShowActionBar(boolean v)    { showActionBar = v; }
    public static void setFpsCheckInterval(int v)     { fpsCheckInterval = Math.max(1, Math.min(100, v)); }
    public static void setDebugMode(boolean v)        { debugMode = v; }
}
