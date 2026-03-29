package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import java.util.List;

/**
 * Injects InputBooster status into the F3 left panel via DebugHudMixin.
 * Author: Ahaduzzaman Khan
 */
public class DebugOverlayManager {

    public static void register() { /* injection done via mixin */ }

    public static List<String> getDebugLines() {
        if (!InputBoosterConfig.isShowF3Info()) return List.of();

        int  fps   = InputBoosterMod.currentFps;
        int  hz    = InputBoosterMod.currentPollHz;
        long recov = InputBoosterMod.recoveredInputs.get();
        int  cps   = InputBoosterMod.cpsLimiter != null ? InputBoosterMod.cpsLimiter.getCps() : 0;

        String hzStr = hz >= 400 ? "§c" + hz + " Hz (max boost)"
                     : hz >= 300 ? "§e" + hz + " Hz (high boost)"
                     : hz >= 200 ? "§a" + hz + " Hz (normal)"
                     :             "§7" + hz + " Hz (light)";

        return List.of(
            "",
            "§b§l[InputBooster " + InputBoosterMod.MOD_VERSION + "] §aACTIVE",
            "§7Poll Rate   : " + hzStr,
            "§7Client FPS  : §f" + fps,
            "§7Recovered   : §f" + String.format("%,d", recov) + " inputs",
            "§7CPS         : §f" + cps,
            "§7Sprint Fix  : " + f(InputBoosterConfig.isSprintFixEnabled())
                + "  §7Auto-Sprint: " + f(InputBoosterConfig.isAutoSprintEnabled()),
            "§7W-Tap       : " + f(InputBoosterConfig.isWTapAssistEnabled())
                + "  §7Auto-Strafe: " + f(InputBoosterConfig.isAutoStrafeEnabled()),
            "§7Anti-Idle   : " + f(InputBoosterConfig.isAntiIdleEnabled()),
            "§8by Ahaduzzaman Khan"
        );
    }

    private static String f(boolean v) { return v ? "§aON" : "§cOFF"; }
}
