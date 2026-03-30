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

        // Colorful poll rate indicator with boost levels
        String hzStr = hz >= 450 ? "§c§l" + hz + " Hz §c(MAX BOOST)" + "§7 🔥"
                     : hz >= 400 ? "§c" + hz + " Hz (ultra boost)"
                     : hz >= 350 ? "§e" + hz + " Hz (high boost)"
                     : hz >= 250 ? "§a" + hz + " Hz (normal)"
                     : hz >= 150 ? "§6" + hz + " Hz (light)"
                     :             "§7" + hz + " Hz (minimum)";

        // FPS indicator with color gradient
        String fpsStr = fps >= 120 ? "§a" + fps : fps >= 60 ? "§e" + fps
                      : fps >= 30 ? "§6" + fps : "§c" + fps;

        // CPS indicator
        String cpsStr = cps >= 15 ? "§c§l" + cps : cps >= 10 ? "§a" + cps
                      : cps >= 5 ? "§e" + cps : "§7" + cps;

        // Recovery indicator
        String recovStr = recov >= 100000 ? "§c§l" : recov >= 10000 ? "§e" : "§a";

        return List.of(
            "",
            "§b§l[InputBooster " + InputBoosterMod.MOD_VERSION + "] §l§a✓ ACTIVE",
            "§r§70━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "§7Poll Rate   : " + hzStr,
            "§7Client FPS  : " + fpsStr + " §7(target: 60+)",
            recovStr + "§7Recovered   : §f" + String.format("%,d", recov) + " §7inputs",
            "§7CPS         : " + cpsStr,
            "§r§70━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "§7Sprint Fix  : " + f(InputBoosterConfig.isSprintFixEnabled())
                + "  §7Auto-Sprint: " + f(InputBoosterConfig.isAutoSprintEnabled()),
            "§7W-Tap Assist: " + f(InputBoosterConfig.isWTapAssistEnabled())
                + "  §7Auto-Strafe: " + f(InputBoosterConfig.isAutoStrafeEnabled()),
            "§7Anti-Idle   : " + f(InputBoosterConfig.isAntiIdleEnabled()),
            "§r§70━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━",
            "§8by Ahaduzzaman Khan"
        );
    }

    private static String f(boolean v) { return v ? "§a§l✓ ON" : "§c✗ OFF"; }
}
