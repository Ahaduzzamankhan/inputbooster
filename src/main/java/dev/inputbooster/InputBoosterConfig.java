package dev.inputbooster;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * InputBoosterConfig — Config file: .minecraft/config/inputbooster.properties
 * Author: Ahaduzzaman Khan
 */
public class InputBoosterConfig {

    private static final Path CONFIG_PATH = Paths.get("config", "inputbooster.properties");

    private static int     pollRateHz         = 200;
    private static boolean sprintFixEnabled   = true;
    private static boolean autoSprintEnabled  = true;
    private static boolean wTapAssistEnabled  = true;
    private static boolean antiIdleEnabled    = true;
    private static boolean autoStrafeEnabled  = true;
    private static boolean showF3Info         = true;

    public static void load() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
                pollRateHz        = parseInt(props,  "poll_rate_hz",  200);
                sprintFixEnabled  = parseBool(props, "sprint_fix",    true);
                autoSprintEnabled = parseBool(props, "auto_sprint",   true);
                wTapAssistEnabled = parseBool(props, "wtap_assist",   true);
                antiIdleEnabled   = parseBool(props, "anti_idle",     true);
                autoStrafeEnabled = parseBool(props, "auto_strafe",   true);
                showF3Info        = parseBool(props, "show_f3_info",  true);
                InputBoosterMod.LOGGER.info("[InputBooster] Config loaded.");
            } catch (Exception e) {
                InputBoosterMod.LOGGER.warn("[InputBooster] Config error, using defaults.");
            }
        } else {
            save();
        }
        pollRateHz = Math.max(100, Math.min(500, pollRateHz));
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Properties props = new Properties();
            props.setProperty("poll_rate_hz",  String.valueOf(pollRateHz));
            props.setProperty("sprint_fix",    String.valueOf(sprintFixEnabled));
            props.setProperty("auto_sprint",   String.valueOf(autoSprintEnabled));
            props.setProperty("wtap_assist",   String.valueOf(wTapAssistEnabled));
            props.setProperty("anti_idle",     String.valueOf(antiIdleEnabled));
            props.setProperty("auto_strafe",   String.valueOf(autoStrafeEnabled));
            props.setProperty("show_f3_info",  String.valueOf(showF3Info));
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out,
                    "InputBooster v2.0.4 by Ahaduzzaman Khan (MC 26.1 / 26.1.1)\n" +
                    "# poll_rate_hz: 60-1000 (auto-scales with FPS)\n" +
                    "# All toggles : true / false"
                );
            }
        } catch (Exception e) {
            InputBoosterMod.LOGGER.warn("[InputBooster] Config save failed.");
        }
    }

    private static int parseInt(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
    private static boolean parseBool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }

    public static int     getPollRateHz()       { return pollRateHz; }
    public static boolean isSprintFixEnabled()  { return sprintFixEnabled; }
    public static boolean isAutoSprintEnabled() { return autoSprintEnabled; }
    public static boolean isWTapAssistEnabled() { return wTapAssistEnabled; }
    public static boolean isAntiIdleEnabled()   { return antiIdleEnabled; }
    public static boolean isAutoStrafeEnabled() { return autoStrafeEnabled; }
    public static boolean isShowF3Info()        { return showF3Info; }
}
