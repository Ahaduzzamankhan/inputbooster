package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleManager {
    private final Map<String, Boolean> runtime = new LinkedHashMap<>();

    public ModuleManager() {
        runtime.put("combat", true);
        runtime.put("movement", true);
        runtime.put("debug", true);
        runtime.put("profiles", true);
        runtime.put("anti_idle", true);
        runtime.put("replay", true);
    }

    public boolean enabled(String module) {
        return runtime.getOrDefault(module, true) && switch (module) {
            case "combat" -> InputBoosterConfig.isCpsLimiterEnabled();
            case "movement" -> InputBoosterConfig.isSprintFixEnabled()
                || InputBoosterConfig.isAutoSprintEnabled()
                || InputBoosterConfig.isWTapAssistEnabled()
                || InputBoosterConfig.isAutoStrafeEnabled();
            case "debug" -> InputBoosterConfig.isShowF3Info() || InputBoosterConfig.isEventLogEnabled();
            case "profiles" -> InputBoosterConfig.isPerServerProfiles();
            case "anti_idle" -> InputBoosterConfig.isAntiIdleEnabled();
            case "replay" -> InputBoosterConfig.isReplayEnabled();
            default -> true;
        };
    }

    public void setRuntime(String module, boolean enabled) {
        runtime.put(module, enabled);
    }

    public String statusLine() {
        int enabled = 0;
        for (String module : runtime.keySet()) if (enabled(module)) enabled++;
        return "Modules: " + enabled + "/" + runtime.size();
    }
}
