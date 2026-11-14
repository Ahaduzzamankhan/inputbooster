package dev.inputbooster.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ProfileManager — configuration profile system.
 *
 * Profiles are persisted as JSON using Gson, which Minecraft already ships, so
 * escaping and malformed input are handled by a real parser instead of regular
 * expressions. Writes are atomic (temp file + move) so a crash or kill during a
 * save cannot leave a truncated profile file behind; an unreadable file is moved
 * aside and the mod continues with no profiles rather than failing to start.
 *
 * Author: Ahaduzzaman Khan
 */
public class ProfileManager {

    public static final int MAX_PROFILES = 5;

    private static final Path PROFILES_PATH = InputBoosterConfig.configDir().resolve("inputbooster_profiles.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
        boolean showKeystrokes,
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
                InputBoosterConfig.isShowKeystrokes(),
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

        /**
         * Applies this profile. Values are re-clamped through the config setters
         * so a hand-edited profile file cannot put the runtime into a bad state.
         */
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
            InputBoosterConfig.setShowKeystrokes(showKeystrokes);
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

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", name == null ? "Unnamed" : name);
            o.addProperty("pollRateHz", pollRateHz);
            o.addProperty("pollRateAutoMode", pollRateAutoMode);
            o.addProperty("sprintFixEnabled", sprintFixEnabled);
            o.addProperty("autoSprintEnabled", autoSprintEnabled);
            o.addProperty("wTapAssistEnabled", wTapAssistEnabled);
            o.addProperty("antiIdleEnabled", antiIdleEnabled);
            o.addProperty("autoStrafeEnabled", autoStrafeEnabled);
            o.addProperty("cpsLimiterEnabled", cpsLimiterEnabled);
            o.addProperty("showF3Info", showF3Info);
            o.addProperty("showKeystrokes", showKeystrokes);
            o.addProperty("showActionBar", showActionBar);
            o.addProperty("burstModeEnabled", burstModeEnabled);
            o.addProperty("maxCps", maxCps);
            o.addProperty("comboKeysEnabled", comboKeysEnabled);
            o.addProperty("fpsCheckInterval", fpsCheckInterval);
            o.addProperty("debugMode", debugMode);
            o.addProperty("cpsMode", cpsMode == null ? "FIXED" : cpsMode);
            o.addProperty("replayEnabled", replayEnabled);
            o.addProperty("safeModeEnabled", safeModeEnabled);
            o.addProperty("eventLogEnabled", eventLogEnabled);
            o.addProperty("keyConflictWarn", keyConflictWarn);
            o.addProperty("perServerProfiles", perServerProfiles);
            return o;
        }

        static Profile fromJson(JsonObject o) {
            if (o == null) return null;
            String name = optString(o, "name", "Unnamed");
            if (name.isBlank()) name = "Unnamed";
            String cpsMode = optString(o, "cpsMode", "FIXED");
            if (cpsMode.isBlank()) cpsMode = "FIXED";
            return new Profile(
                name,
                optInt(o, "pollRateHz", 200),
                optBool(o, "pollRateAutoMode", true),
                optBool(o, "sprintFixEnabled", true),
                optBool(o, "autoSprintEnabled", true),
                optBool(o, "wTapAssistEnabled", true),
                optBool(o, "antiIdleEnabled", true),
                optBool(o, "autoStrafeEnabled", true),
                optBool(o, "cpsLimiterEnabled", true),
                optBool(o, "showF3Info", true),
                optBool(o, "showKeystrokes", true),
                optBool(o, "showActionBar", true),
                optBool(o, "burstModeEnabled", true),
                optInt(o, "maxCps", 20),
                optBool(o, "comboKeysEnabled", true),
                optInt(o, "fpsCheckInterval", 20),
                optBool(o, "debugMode", false),
                cpsMode,
                optBool(o, "replayEnabled", true),
                optBool(o, "safeModeEnabled", true),
                optBool(o, "eventLogEnabled", true),
                optBool(o, "keyConflictWarn", true),
                optBool(o, "perServerProfiles", true)
            );
        }

        private static String optString(JsonObject o, String key, String def) {
            JsonElement e = o.get(key);
            return e == null || e.isJsonNull() ? def : e.getAsString();
        }

        private static int optInt(JsonObject o, String key, int def) {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return def;
            try {
                return e.getAsInt();
            } catch (NumberFormatException ex) {
                return def;
            }
        }

        private static boolean optBool(JsonObject o, String key, boolean def) {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return def;
            try {
                return e.getAsBoolean();
            } catch (RuntimeException ex) {
                return def;
            }
        }
    }

    // ── In-memory profile list (game thread only) ──────────────────────────

    private final List<Profile> profiles = new ArrayList<>();
    private int activeIndex = -1;

    public List<Profile> getProfiles() { return Collections.unmodifiableList(profiles); }
    public int getActiveIndex() { return activeIndex; }
    public int getCount() { return profiles.size(); }

    /** Save a new profile or overwrite one by name. Returns false if at capacity and name is new. */
    public boolean saveProfile(String name) {
        if (name == null || name.isBlank()) return false;
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
    public boolean loadProfile(String name, Minecraft mc) {
        if (name == null) return false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name().equalsIgnoreCase(name)) {
                profiles.get(i).apply();
                activeIndex = i;
                InputBoosterConfig.save();
                InputBoosterMod.LOGGER.info("[Profile] Loaded profile: {}", name);
                if (mc != null && mc.player != null) {
                    mc.player.sendOverlayMessage(
                        Component.literal("§b[InputBooster] §aProfile loaded: §e" + name));
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Delete a profile by index, keeping {@link #activeIndex} pointing at the
     * same profile it did before.
     */
    public boolean deleteProfile(int index) {
        if (index < 0 || index >= profiles.size()) return false;
        profiles.remove(index);
        if (activeIndex == index) {
            // The active profile itself was removed — nothing is active now.
            activeIndex = -1;
        } else if (activeIndex > index) {
            // Everything after the removed entry shifted left.
            activeIndex--;
        }
        if (activeIndex >= profiles.size()) activeIndex = profiles.size() - 1;
        persist();
        return true;
    }

    // ── Persistence ────────────────────────────────────────────────────────

    public void load() {
        profiles.clear();
        activeIndex = -1;
        if (!Files.exists(PROFILES_PATH)) return;
        try {
            String json = Files.readString(PROFILES_PATH, StandardCharsets.UTF_8);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) {
                quarantineCorruptFile("not a JSON array");
                return;
            }
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (profiles.size() >= MAX_PROFILES) break;
                if (!element.isJsonObject()) continue;
                Profile profile = Profile.fromJson(element.getAsJsonObject());
                if (profile != null) profiles.add(profile);
            }
            InputBoosterMod.LOGGER.info("[Profile] Loaded {} profile(s)", profiles.size());
        } catch (JsonSyntaxException | IllegalStateException e) {
            InputBoosterMod.LOGGER.warn("[Profile] Profile file is not valid JSON ({}); starting with no profiles.",
                e.getMessage());
            quarantineCorruptFile(e.getClass().getSimpleName());
        } catch (IOException e) {
            InputBoosterMod.LOGGER.warn("[Profile] Failed to read profiles: {}", e.getMessage());
        }
    }

    /** Moves an unreadable profile file aside so the game still starts. */
    private void quarantineCorruptFile(String reason) {
        try {
            Path backup = PROFILES_PATH.resolveSibling(PROFILES_PATH.getFileName() + ".corrupt");
            Files.move(PROFILES_PATH, backup, StandardCopyOption.REPLACE_EXISTING);
            InputBoosterMod.LOGGER.warn("[Profile] Moved unreadable profile file to {} ({})",
                backup.getFileName(), reason);
        } catch (IOException e) {
            InputBoosterMod.LOGGER.warn("[Profile] Could not move unreadable profile file aside: {}", e.getMessage());
        }
    }

    private void persist() {
        JsonArray array = new JsonArray();
        for (Profile profile : profiles) {
            array.add(profile.toJson());
        }
        String json = GSON.toJson(array);
        Path temp = null;
        try {
            Path parent = PROFILES_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            temp = Files.createTempFile(parent, "inputbooster_profiles", ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, PROFILES_PATH, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, PROFILES_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException e) {
            InputBoosterMod.LOGGER.warn("[Profile] Failed to save profiles: {}", e.getMessage());
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Nothing else to do; a stray temp file is harmless.
                }
            }
        }
    }
}