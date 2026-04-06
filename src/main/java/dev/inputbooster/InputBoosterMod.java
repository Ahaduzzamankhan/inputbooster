package dev.inputbooster;

import dev.inputbooster.feature.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InputBooster — Ultra-fast input registration mod for low-FPS PvP players.
 *
 * Author  : Ahaduzzaman Khan
 * Version : 2.0.4
 * Loader  : Fabric
 * MC      : 26.1 / 26.1.1 "Tiny Takeover" (Java SE 25)
 */
public class InputBoosterMod implements ClientModInitializer {

    public static final String MOD_ID      = "inputbooster";
    public static final String MOD_NAME    = "InputBooster";
    public static final String MOD_VERSION = "2.0.4-26.1.1";
    public static final String MOD_AUTHOR  = "Ahaduzzaman Khan";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static volatile boolean     gameReady    = false;
    public static volatile boolean     gamePaused   = false;
    public static volatile boolean     active       = true;
    public static volatile KeySnapshot keySnapshot  = null;
    public static final AtomicLong     totalHits       = new AtomicLong(0);
    public static final AtomicLong     recoveredInputs = new AtomicLong(0);
    public static volatile int         currentPollHz   = 200;
    public static volatile int         currentFps      = 0;

    public static InputPollingThread pollingThread;
    public static SprintManager      sprintManager;
    public static WTapAssist         wTapAssist;
    public static AntiIdleManager    antiIdle;
    public static AutoStrafeManager  autoStrafe;
    public static CpsLimiter         cpsLimiter;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] v{} by {} — loading...", MOD_NAME, MOD_VERSION, MOD_AUTHOR);

        InputBoosterConfig.load();

        sprintManager = new SprintManager();
        wTapAssist    = new WTapAssist();
        antiIdle      = new AntiIdleManager();
        autoStrafe    = new AutoStrafeManager();
        cpsLimiter    = new CpsLimiter();

        pollingThread = new InputPollingThread(InputBoosterConfig.getPollRateHz());
        pollingThread.start();
        currentPollHz = InputBoosterConfig.getPollRateHz();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            gameReady  = client.player != null;
            // MC 26.1 Mojang names: screen (not currentScreen), isPaused() unchanged
            gamePaused = client.isPaused() || client.screen != null;

            if (client.options != null) {
                keySnapshot = new KeySnapshot(client.options);
            }

            if (client.player == null) return;

            // MC 26.1: getFps() is a static int field accessor
            currentFps = net.minecraft.client.Minecraft.getInstance().getFps();

            sprintManager.tick(client);
            wTapAssist.tick(client);
            antiIdle.tick(client);
            autoStrafe.tick(client);

            adjustPollRate();
        });

        DebugOverlayManager.register();

        LOGGER.info("[{}] Ready! Auto-active. Open F3 to see status.", MOD_NAME);
    }

    public static void adjustPollRate() {
        int fps = currentFps;
        int targetHz = fps <= 20 ? 500
                     : fps <= 30 ? 350
                     : fps <= 60 ? 200
                     : 100;

        if (targetHz != currentPollHz && pollingThread != null) {
            currentPollHz = targetHz;
            pollingThread.setPollRateHz(targetHz);
        }
    }

    public static void shutdown() {
        if (pollingThread != null) pollingThread.stopPolling();
        InputBoosterConfig.save();
        LOGGER.info("[{}] Shutdown complete.", MOD_NAME);
    }
}
