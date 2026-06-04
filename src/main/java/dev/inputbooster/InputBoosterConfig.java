package dev.inputbooster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
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
    private static String  cpsMode           = "FIXED";
    private static boolean replayEnabled     = true;
    private static boolean safeModeEnabled   = true;
    private static boolean eventLogEnabled   = true;
    private static boolean keyConflictWarn   = true;
    private static boolean perServerProfiles = true;
    private static boolean clickSoundsEnabled = true;
    private static float   clickSoundPitch   = 1.35f;
    private static float   clickSoundVolume  = 0.35f;
    private static int     configVersion     = 303;

    // ── UI ───────────────────────────────────────────────────────────────────
    private static boolean showF3Info        = true;
    private static boolean showKeystrokes    = true;
    private static boolean showActionBar     = true;
    // 0=Top-Left 1=Top-Right 2=Bottom-Left 3=Bottom-Right
    private static int     overlayPosition   = 0;
    private static float   overlayOpacity   = 0.8f;
    private static float   overlayScale      = 1.0f;

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
                cpsMode           = props.getProperty("cps_mode", "FIXED").toUpperCase(Locale.ROOT);
                replayEnabled     = parseBool(props, "replay_enabled",        true);
                safeModeEnabled   = parseBool(props, "safe_mode",             true);
                eventLogEnabled   = parseBool(props, "event_log",             true);
                keyConflictWarn   = parseBool(props, "key_conflict_warn",     true);
                perServerProfiles = parseBool(props, "per_server_profiles",   true);
                clickSoundsEnabled = parseBool(props, "click_sounds",         true);
                clickSoundPitch   = Math.max(0.5f, Math.min(2.0f, parseFloat(props, "click_sound_pitch", 1.35f)));
                clickSoundVolume  = Math.max(0.0f, Math.min(1.0f, parseFloat(props, "click_sound_volume", 0.35f)));
                configVersion     = Math.max(1, parseInt(props, "config_version", 303));
                showF3Info        = parseBool(props, "show_f3_info",          true);
                showKeystrokes    = parseBool(props, "show_keystrokes",       true);
                overlayPosition   = Math.max(0, Math.min(3, parseInt(props, "overlay_position", 0)));
                overlayOpacity   = Math.max(0.0f, Math.min(1.0f, parseFloat(props, "overlay_opacity", 0.8f)));
                overlayScale      = Math.max(0.5f, Math.min(3.0f, parseFloat(props, "overlay_scale", 1.0f)));
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
            props.setProperty("show_action_bar",     String.valueOf(showActionBar));
            props.setProperty("fps_check_interval",  String.valueOf(fpsCheckInterval));
            props.setProperty("debug_mode",          String.valueOf(debugMode));
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "InputBooster v3.0.3nf-beta01 Configuration - by Ahaduzzaman Khan");
            }
            LOGGER.info("✓ Config saved to {}", CONFIG_PATH);
        } catch (Exception e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static float parseFloat(java.util.Properties p, String k, float def) {
        try { return Float.parseFloat(p.getProperty(k, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

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
        cpsMode = "FIXED"; replayEnabled = true; safeModeEnabled = true;
        eventLogEnabled = true; keyConflictWarn = true; perServerProfiles = true;
        clickSoundsEnabled = true; clickSoundPitch = 1.35f; clickSoundVolume = 0.35f; configVersion = 303;
        showF3Info = true; showKeystrokes = true; showActionBar = true; fpsCheckInterval = 20; debugMode = false;
        overlayPosition = 0; overlayScale = 1.0f; overlayOpacity = 0.8f;
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
    public static void setOverlayPosition(int v)      { overlayPosition = Math.max(0, Math.min(3, v)); }
    public static void setOverlayOpacity(float v)    { overlayOpacity = Math.max(0.0f, Math.min(1.0f, v)); }
    public static void setOverlayScale(float v)       { overlayScale = Math.max(0.5f, Math.min(3.0f, v)); }
    public static void setShowActionBar(boolean v)    { showActionBar = v; }
    public static void setFpsCheckInterval(int v)     { fpsCheckInterval = Math.max(1, Math.min(100, v)); }
    public static void setDebugMode(boolean v)        { debugMode = v; }
}
