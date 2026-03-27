package dev.inputbooster;

import dev.inputbooster.feature.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InputBooster — Ultra-fast input registration mod for low-FPS PvP players.
 *
 * Author  : Ahaduzzaman Khan
 * Version : 2.0.0
 * Loader  : Fabric
 * MC      : 1.21.x
 * Compat  : Sodium, Iris, Lithium, FerriteCore
 *
 * AUTO-ENABLED on launch. No toggle key.
 * Check status anytime with F3.
 */
public class InputBoosterMod implements ClientModInitializer {

    public static final String MOD_ID      = "inputbooster";
    public static final String MOD_NAME    = "InputBooster";
    public static final String MOD_VERSION = "2.0.0";
    public static final String MOD_AUTHOR  = "Ahaduzzaman Khan";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Global volatile state — read by mixins & features from any thread
    public static volatile boolean active         = true;
    public static volatile long    totalHits       = 0;
    public static volatile long    recoveredInputs = 0;
    public static volatile int     currentPollHz   = 200;
    public static volatile int     currentFps      = 0;

    // Feature managers (public for cross-feature access)
    public static InputPollingThread pollingThread;
    public static SprintManager      sprintManager;
    public static WTapAssist         wTapAssist;
    public static AntiIdleManager    antiIdle;
    public static AutoStrafeManager  autoStrafe;
    public static CpsLimiter         cpsLimiter;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] v{} by {} — loading...", MOD_NAME, MOD_VERSION, MOD_AUTHOR);

        // 1. Config
        InputBoosterConfig.load();

        // 2. Feature managers
        sprintManager = new SprintManager();
        wTapAssist    = new WTapAssist();
        antiIdle      = new AntiIdleManager();
        autoStrafe    = new AutoStrafeManager();
        cpsLimiter    = new CpsLimiter();

        // 3. Start high-frequency polling thread (auto, no user action needed)
        pollingThread = new InputPollingThread(InputBoosterConfig.getPollRateHz());
        pollingThread.start();
        currentPollHz = InputBoosterConfig.getPollRateHz();

        // 4. Per-tick work
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            currentFps = client.getCurrentFps();

            // Drain captured input queue → fire missed actions
            InputDrainer.drainAll(client);

            // Feature ticks
            sprintManager.tick(client);
            wTapAssist.tick(client);
            antiIdle.tick(client);
            autoStrafe.tick(client);

            // Dynamically scale poll rate to FPS
            adjustPollRate();
        });

        // 5. F3 debug screen lines
        DebugOverlayManager.register();

        LOGGER.info("[{}] Ready! Auto-active. Open F3 to see status.", MOD_NAME);
    }

    /**
     * Scales poll rate inversely with FPS.
     * The lower the FPS, the harder we compensate.
     *
     *  FPS ≤ 20  →  500 Hz  (~25x vs frame rate)
     *  FPS ≤ 30  →  350 Hz  (~12x vs frame rate)
     *  FPS ≤ 60  →  200 Hz  (~3-4x vs frame rate)
     *  FPS > 60  →  100 Hz  (minimal overhead)
     */
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
