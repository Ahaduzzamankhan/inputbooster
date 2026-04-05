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
 * Version : 2.0.1
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
    public static final String MOD_VERSION = "2.0.2";
    public static final String MOD_AUTHOR  = "Ahaduzzaman Khan";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Volatile flag updated from main thread — safe to read from polling thread
    public static volatile boolean gameReady  = false;
    public static volatile boolean gamePaused = false;
    public static volatile boolean active     = true;
    public static volatile KeySnapshot keySnapshot = null;
    public static final AtomicLong totalHits       = new AtomicLong(0);
    public static final AtomicLong recoveredInputs = new AtomicLong(0);
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
            // Always update thread-safe flags so the polling thread never reads MC state directly
            gameReady  = client.player != null;
            gamePaused = client.isPaused() || client.currentScreen != null;

            // Capture key state snapshot on main thread — polling thread reads this safely
            if (client.options != null) {
                keySnapshot = new KeySnapshot(client.options);
            }

            if (client.player == null) return;

            currentFps = client.getCurrentFps();

            // NOTE: InputDrainer.drainAll() is called at tick HEAD via GameTickMixin.
            // Do NOT call it again here — double-draining fires every queued input twice.

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
