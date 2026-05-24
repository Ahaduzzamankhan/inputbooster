package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * ProfileManager — Config Profile System (Feature 6).
 *
 * Stores up to 5 named configuration profiles (e.g. "PvP", "Mining", "Idle").
 * Each profile captures all InputBoosterConfig values and is persisted to
 * config/inputbooster_profiles.json.
 *
 * Access: O key → "Profiles" tab in InputBoosterScreen.
 * Command: /inputbooster profile <name>  (to be registered in InputBoosterMod)
 *
 * Version: 3.0.0
 * Author: Ahaduzzaman Khan
 */
public class ProfileManager {

    public static final int MAX_PROFILES = 5;
    private static final Path PROFILES_PATH = Paths.get("config", "inputbooster_profiles.json");

    /** Immutable snapshot of all config values for one profile. */
    public record Profile(
        String name,
        int pollRateHz,
        boolean pollRateAutoMode,
        boolean sprintFixEnabled,
        boolean autoSprintEnabled,
        boolean wTapAssistEnabled,
        boolean antiIdleEnabled,
        boolean autoStrafeEnabled,
        boolean cpsLimiterEnabled,
        boolean showF3Info,
        boolean showActionBar,
        boolean burstModeEnabled,
        int maxCps,
        boolean comboKeysEnabled,
        int fpsCheckInterval,
        boolean debugMode,
        String cpsMode,
        boolean replayEnabled,
        boolean safeModeEnabled,
        boolean eventLogEnabled,
        boolean keyConflictWarn,
        boolean perServerProfiles
    ) {
        /** Capture current config state into a new profile snapshot. */
        public static Profile capture(String name) {
            return new Profile(
                name,
                InputBoosterConfig.getPollRateHz(),
                InputBoosterConfig.isPollRateAutoMode(),
                InputBoosterConfig.isSprintFixEnabled(),
                InputBoosterConfig.isAutoSprintEnabled(),
                InputBoosterConfig.isWTapAssistEnabled(),
                InputBoosterConfig.isAntiIdleEnabled(),
                InputBoosterConfig.isAutoStrafeEnabled(),
                InputBoosterConfig.isCpsLimiterEnabled(),
                InputBoosterConfig.isShowF3Info(),
                InputBoosterConfig.isShowActionBar(),
                InputBoosterConfig.isBurstModeEnabled(),
                InputBoosterConfig.getMaxCps(),
                InputBoosterConfig.isComboKeysEnabled(),
                InputBoosterConfig.getFpsCheckInterval(),
                InputBoosterConfig.isDebugMode(),
                InputBoosterConfig.getCpsMode(),
                InputBoosterConfig.isReplayEnabled(),
                InputBoosterConfig.isSafeModeEnabled(),
                InputBoosterConfig.isEventLogEnabled(),
                InputBoosterConfig.isKeyConflictWarn(),
                InputBoosterConfig.isPerServerProfiles()
            );
        }

        /** Apply this profile's values to InputBoosterConfig (does not save to disk). */
        public void apply() {
            InputBoosterConfig.setPollRateHz(pollRateHz);
            InputBoosterConfig.setPollRateAutoMode(pollRateAutoMode);
            InputBoosterConfig.setSprintFixEnabled(sprintFixEnabled);
            InputBoosterConfig.setAutoSprintEnabled(autoSprintEnabled);
            InputBoosterConfig.setWTapAssistEnabled(wTapAssistEnabled);
            InputBoosterConfig.setAntiIdleEnabled(antiIdleEnabled);
            InputBoosterConfig.setAutoStrafeEnabled(autoStrafeEnabled);
            InputBoosterConfig.setCpsLimiterEnabled(cpsLimiterEnabled);
            InputBoosterConfig.setShowF3Info(showF3Info);
            InputBoosterConfig.setShowActionBar(showActionBar);
            InputBoosterConfig.setBurstModeEnabled(burstModeEnabled);
            InputBoosterConfig.setMaxCps(maxCps);
            InputBoosterConfig.setComboKeysEnabled(comboKeysEnabled);
            InputBoosterConfig.setFpsCheckInterval(fpsCheckInterval);
            InputBoosterConfig.setDebugMode(debugMode);
            InputBoosterConfig.setCpsMode(cpsMode);
            InputBoosterConfig.setReplayEnabled(replayEnabled);
            InputBoosterConfig.setSafeModeEnabled(safeModeEnabled);
            InputBoosterConfig.setEventLogEnabled(eventLogEnabled);
            InputBoosterConfig.setKeyConflictWarn(keyConflictWarn);
            InputBoosterConfig.setPerServerProfiles(perServerProfiles);
        }

        /** Serialize to a simple properties-style JSON object (manual, no Gson dep). */
        public String toJson() {
            return "{"
                + "\"name\":\"" + name + "\","
                + "\"pollRateHz\":" + pollRateHz + ","
                + "\"pollRateAutoMode\":" + pollRateAutoMode + ","
                + "\"sprintFixEnabled\":" + sprintFixEnabled + ","
                + "\"autoSprintEnabled\":" + autoSprintEnabled + ","
                + "\"wTapAssistEnabled\":" + wTapAssistEnabled + ","
                + "\"antiIdleEnabled\":" + antiIdleEnabled + ","
                + "\"autoStrafeEnabled\":" + autoStrafeEnabled + ","
                + "\"cpsLimiterEnabled\":" + cpsLimiterEnabled + ","
                + "\"showF3Info\":" + showF3Info + ","
                + "\"showActionBar\":" + showActionBar + ","
                + "\"burstModeEnabled\":" + burstModeEnabled + ","
                + "\"maxCps\":" + maxCps + ","
                + "\"comboKeysEnabled\":" + comboKeysEnabled + ","
                + "\"fpsCheckInterval\":" + fpsCheckInterval + ","
                + "\"debugMode\":" + debugMode + ","
                + "\"cpsMode\":\"" + cpsMode + "\","
                + "\"replayEnabled\":" + replayEnabled + ","
                + "\"safeModeEnabled\":" + safeModeEnabled + ","
                + "\"eventLogEnabled\":" + eventLogEnabled + ","
                + "\"keyConflictWarn\":" + keyConflictWarn + ","
                + "\"perServerProfiles\":" + perServerProfiles
                + "}";
        }

        /** Parse from a JSON object string (minimal parser — no Gson dep). */
        public static Profile fromJson(String json) {
            try {
                String name           = strField(json, "name");
                int pollRateHz        = intField(json, "pollRateHz", 200);
                boolean autoMode      = boolField(json, "pollRateAutoMode", true);
                boolean sprintFix     = boolField(json, "sprintFixEnabled", true);
                boolean autoSprint    = boolField(json, "autoSprintEnabled", true);
                boolean wTap          = boolField(json, "wTapAssistEnabled", true);
                boolean antiIdle      = boolField(json, "antiIdleEnabled", true);
                boolean autoStrafe    = boolField(json, "autoStrafeEnabled", true);
                boolean cpsLimiter    = boolField(json, "cpsLimiterEnabled", true);
                boolean f3Info        = boolField(json, "showF3Info", true);
                boolean actionBar     = boolField(json, "showActionBar", true);
                boolean burstMode     = boolField(json, "burstModeEnabled", true);
                int maxCps            = intField(json, "maxCps", 20);
                boolean comboKeys     = boolField(json, "comboKeysEnabled", true);
                int fpsCheckInterval  = intField(json, "fpsCheckInterval", 20);
                boolean debugMode     = boolField(json, "debugMode", false);
                String cpsMode         = strField(json, "cpsMode");
                if ("Unnamed".equals(cpsMode)) cpsMode = "FIXED";
                boolean replayEnabled  = boolField(json, "replayEnabled", true);
                boolean safeMode       = boolField(json, "safeModeEnabled", true);
                boolean eventLog       = boolField(json, "eventLogEnabled", true);
                boolean keyConflict    = boolField(json, "keyConflictWarn", true);
                boolean perServer      = boolField(json, "perServerProfiles", true);
                return new Profile(name, pollRateHz, autoMode, sprintFix, autoSprint, wTap,
                    antiIdle, autoStrafe, cpsLimiter, f3Info, actionBar, burstMode, maxCps,
                    comboKeys, fpsCheckInterval, debugMode, cpsMode, replayEnabled, safeMode,
                    eventLog, keyConflict, perServer);
            } catch (Exception e) {
                return null;
            }
        }

        private static String strField(String json, String key) {
            String marker = "\"" + key + "\":\"";
            int start = json.indexOf(marker);
            if (start < 0) return "Unnamed";
            start += marker.length();
            int end = json.indexOf('"', start);
            return end < 0 ? "Unnamed" : json.substring(start, end);
        }

        private static int intField(String json, String key, int def) {
            String marker = "\"" + key + "\":";
            int start = json.indexOf(marker);
            if (start < 0) return def;
            start += marker.length();
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
            try { return Integer.parseInt(json.substring(start, end)); } catch (Exception e) { return def; }
        }

        private static boolean boolField(String json, String key, boolean def) {
            String marker = "\"" + key + "\":";
            int start = json.indexOf(marker);
            if (start < 0) return def;
            start += marker.length();
            return json.startsWith("true", start);
        }
    }

    // ── In-memory profile list ──────────────────────────────────────────────

    private final List<Profile> profiles = new ArrayList<>();
    private int activeIndex = -1;

    public List<Profile> getProfiles() { return Collections.unmodifiableList(profiles); }
    public int getActiveIndex() { return activeIndex; }
    public int getCount() { return profiles.size(); }

    /** Save a new profile or overwrite one by name. Returns false if at capacity and name is new. */
    public boolean saveProfile(String name) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name().equalsIgnoreCase(name)) {
                profiles.set(i, Profile.capture(name));
                activeIndex = i;
                persist();
                return true;
            }
        }
        if (profiles.size() >= MAX_PROFILES) return false;
        profiles.add(Profile.capture(name));
        activeIndex = profiles.size() - 1;
        persist();
        return true;
    }

    /** Load a profile by name. Returns false if not found. */
    public boolean loadProfile(String name, net.minecraft.client.Minecraft mc) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name().equalsIgnoreCase(name)) {
                profiles.get(i).apply();
                activeIndex = i;
                InputBoosterConfig.save();
                InputBoosterMod.LOGGER.info("[ProfileManager] Loaded profile: {}", name);
                if (mc != null && mc.player != null) {
                    mc.player.sendSystemMessage(
                        Component.literal("§b[InputBooster] §aProfile loaded: §e" + name));
                }
                return true;
            }
        }
        return false;
    }

    /** Delete a profile by index. */
    public boolean deleteProfile(int index) {
        if (index < 0 || index >= profiles.size()) return false;
        profiles.remove(index);
        if (activeIndex >= profiles.size()) activeIndex = profiles.size() - 1;
        persist();
        return true;
    }

    // ── Persistence ────────────────────────────────────────────────────────

    public void load() {
        try {
            if (!Files.exists(PROFILES_PATH)) return;
            String json = Files.readString(PROFILES_PATH);
            profiles.clear();
            // Simple array parser: find each {...} block
            int start = 0;
            while (true) {
                int open = json.indexOf('{', start);
                if (open < 0) break;
                int close = json.indexOf('}', open);
                if (close < 0) break;
                String obj = json.substring(open, close + 1);
                Profile p = Profile.fromJson(obj);
                if (p != null && profiles.size() < MAX_PROFILES) profiles.add(p);
                start = close + 1;
            }
            InputBoosterMod.LOGGER.info("[ProfileManager] Loaded {} profile(s)", profiles.size());
        } catch (Exception e) {
            InputBoosterMod.LOGGER.warn("[ProfileManager] Failed to load profiles: {}", e.getMessage());
        }
    }

    private void persist() {
        try {
            Files.createDirectories(PROFILES_PATH.getParent());
            StringBuilder sb = new StringBuilder("[\n");
            for (int i = 0; i < profiles.size(); i++) {
                sb.append("  ").append(profiles.get(i).toJson());
                if (i < profiles.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("]");
            Files.writeString(PROFILES_PATH, sb.toString());
        } catch (Exception e) {
            InputBoosterMod.LOGGER.warn("[ProfileManager] Failed to save profiles: {}", e.getMessage());
        }
    }
}
