package dev.inputbooster;

import dev.inputbooster.feature.*;
import dev.inputbooster.screen.InputBoosterScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * InputBooster — Ultra-fast input registration mod for low-FPS PvP players.
 *
 * BUG FIXES in this revision:
 *  1. Added missing debugMode() static method that was called from
 *     InputBoosterConfig.setPollRateHz() — caused a compile error previously.
 *
 * Version  : 2.1.0
 * MC       : 1.21.0 - 1.21.11
 * Java     : 21+
 * Gradle   : 9.4.1
 * Loader   : Fabric
 */
public class InputBoosterMod implements ClientModInitializer {

    public static final String MOD_ID      = "inputbooster";
    public static final String MOD_NAME    = "InputBooster";
    public static final String MOD_VERSION = "2.1.0";
    public static final String MC_VERSION  = "1.21.0-1.21.11";
    public static final String MOD_AUTHOR  = "Ahaduzzaman Khan";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // ─────────────────────────────────────────────────────────────────
    // Runtime State (thread-safe)
    // ─────────────────────────────────────────────────────────────────

    public static volatile boolean     gameReady    = false;
    public static volatile boolean     gamePaused   = false;
    public static volatile boolean     active       = true;
    public static volatile KeySnapshot keySnapshot  = null;

    public static final AtomicLong totalHits       = new AtomicLong(0);
    public static final AtomicLong recoveredInputs = new AtomicLong(0);
    public static final AtomicBoolean initialized  = new AtomicBoolean(false);

    public static volatile int     currentPollHz    = 200;
    public static volatile int     currentFps       = 0;
    public static volatile long    lastTickTime     = 0;

    // ─────────────────────────────────────────────────────────────────
    // Feature Managers
    // ─────────────────────────────────────────────────────────────────

    public static InputPollingThread pollingThread;
    public static SprintManager      sprintManager;
    public static WTapAssist         wTapAssist;
    public static AntiIdleManager    antiIdle;
    public static AutoStrafeManager  autoStrafe;
    public static CpsLimiter         cpsLimiter;
    public static DebugOverlayManager debugOverlay;

    // ─────────────────────────────────────────────────────────────────
    // Keybindings
    // ─────────────────────────────────────────────────────────────────

    private static KeyBinding openScreenKey;
    private static KeyBinding toggleModKey;

    // ─────────────────────────────────────────────────────────────────
    // Initialization
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void onInitializeClient() {
        LOGGER.info("╔════════════════════════════════════════════════════════╗");
        LOGGER.info("║        {} v{} by {}        ║", MOD_NAME, MOD_VERSION, MOD_AUTHOR);
        LOGGER.info("║     MC: {} | Java: 21+ | Gradle: 9.4.1    ║", MC_VERSION);
        LOGGER.info("╚════════════════════════════════════════════════════════╝");

        try {
            // 1. Load Configuration
            LOGGER.info("[{}] Loading configuration...", MOD_NAME);
            InputBoosterConfig.load();

            // 2. Initialize Feature Managers
            LOGGER.info("[{}] Initializing feature managers...", MOD_NAME);
            sprintManager = new SprintManager();
            wTapAssist    = new WTapAssist();
            antiIdle      = new AntiIdleManager();
            autoStrafe    = new AutoStrafeManager();
            cpsLimiter    = new CpsLimiter();
            debugOverlay  = new DebugOverlayManager();

            // 3. Start Polling Thread
            LOGGER.info("[{}] Starting input polling thread...", MOD_NAME);
            int initialHz = InputBoosterConfig.isPollRateAutoMode()
                            ? 200
                            : InputBoosterConfig.getPollRateHz();
            pollingThread = new InputPollingThread(initialHz);
            pollingThread.start();
            currentPollHz = initialHz;
            LOGGER.info("[{}] Polling thread started at {} Hz", MOD_NAME, initialHz);

            // 4. Register Keybindings
            LOGGER.info("[{}] Registering keybindings...", MOD_NAME);
            openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputbooster.options",
                GLFW.GLFW_KEY_O,
                "category.inputbooster"
            ));

            toggleModKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputbooster.toggle",
                GLFW.GLFW_KEY_P,
                "category.inputbooster"
            ));

            // 5. Register Tick Events
            LOGGER.info("[{}] Registering tick events...", MOD_NAME);
            ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

            // 6. Register F3 Debug Overlay
            LOGGER.info("[{}] Registering F3 debug overlay...", MOD_NAME);
            debugOverlay.register();

            // Mark as initialized
            initialized.set(true);

            LOGGER.info("╔════════════════════════════════════════════════════════╗");
            LOGGER.info("║  ✓ {} Ready!                                    ║", MOD_NAME);
            LOGGER.info("║  Press [O] to open options                             ║");
            LOGGER.info("║  Press [P] to toggle mod on/off                        ║");
            LOGGER.info("║  Press [F3] to see debug info                          ║");
            LOGGER.info("╚════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            LOGGER.error("[{}] Fatal error during initialization!", MOD_NAME, e);
            active = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Per-Tick Logic
    // ─────────────────────────────────────────────────────────────────

    private void onClientTick(MinecraftClient client) {
        if (!active || !initialized.get()) {
            return;
        }

        try {
            lastTickTime = System.nanoTime();

            // Update game state
            gameReady  = client.player != null;
            gamePaused = client.isPaused() || client.currentScreen != null;

            if (client.options != null) {
                keySnapshot = new KeySnapshot(client.options);
            }

            if (client.player == null) {
                return;
            }

            // Update FPS
            currentFps = client.getCurrentFps();

            // ── Poll Rate Scaling ──
            if (InputBoosterConfig.isPollRateAutoMode()) {
                adjustPollRateAuto();
            } else {
                adjustPollRateManual();
            }

            // ── Handle Keybinds ──
            handleKeybinds(client);

            // ── Feature Ticks ──
            sprintManager.tick(client);
            wTapAssist.tick(client);
            antiIdle.tick(client);
            autoStrafe.tick(client);
            cpsLimiter.tick(client);

        } catch (Exception e) {
            LOGGER.warn("[{}] Error in tick handler", MOD_NAME, e);
        }
    }

    private void handleKeybinds(MinecraftClient client) {
        // Open settings screen
        if (openScreenKey.wasPressed()) {
            if (client.currentScreen == null || !(client.currentScreen instanceof InputBoosterScreen)) {
                client.setScreen(new InputBoosterScreen(client.currentScreen));
            }
        }

        // Toggle mod on/off
        if (toggleModKey.wasPressed()) {
            active = !active;
            String status = active ? "§a§lON" : "§c§lOFF";
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§7InputBooster " + status),
                    true // Action bar
                );
            }
            LOGGER.info("[{}] Mod toggled: {}", MOD_NAME, active ? "ON" : "OFF");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Poll Rate Scaling
    // ─────────────────────────────────────────────────────────────────

    public static void adjustPollRateAuto() {
        int fps = currentFps;
        int targetHz = calculateAutoHz(fps);

        if (targetHz != currentPollHz && pollingThread != null) {
            currentPollHz = targetHz;
            pollingThread.setPollRateHz(targetHz);
        }
    }

    public static void adjustPollRateManual() {
        int manualHz = InputBoosterConfig.getPollRateHz();
        if (manualHz != currentPollHz && pollingThread != null) {
            currentPollHz = manualHz;
            pollingThread.setPollRateHz(manualHz);
        }
    }

    /**
     * Calculate target Hz based on FPS.
     *
     * FPS ≤ 20  →  500 Hz  (critical: very low FPS)
     * FPS ≤ 30  →  400 Hz  (severe: low FPS)
     * FPS ≤ 60  →  200 Hz  (normal: moderate FPS)
     * FPS ≤ 120 →  150 Hz  (good: high FPS)
     * FPS > 120 →  100 Hz  (optimal: very high FPS)
     */
    public static int calculateAutoHz(int fps) {
        if (fps <= 20)  return 500;
        if (fps <= 30)  return 400;
        if (fps <= 60)  return 200;
        if (fps <= 120) return 150;
        return 100;
    }

    // ─────────────────────────────────────────────────────────────────
    // Utility Methods
    // ─────────────────────────────────────────────────────────────────

    /**
     * FIX: This method was called from InputBoosterConfig.setPollRateHz() but did not
     * exist in the original source, causing a compile error.
     * Returns true if debug mode is currently enabled in config.
     */
    public static boolean debugMode() {
        return InputBoosterConfig.isDebugMode();
    }

    public static String getModeSuffix() {
        return InputBoosterConfig.isPollRateAutoMode() ? " (AUTO)" : " (MANUAL)";
    }

    public static String getPerformanceMetrics() {
        long elapsed = (System.nanoTime() - lastTickTime) / 1_000_000L;
        return String.format("Tick: %dms | Hits: %d | Recovered: %d",
            elapsed, totalHits.get(), recoveredInputs.get());
    }

    // ─────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────

    public static void shutdown() {
        LOGGER.info("[{}] Shutting down...", MOD_NAME);

        try {
            if (pollingThread != null) {
                pollingThread.stopPolling();
                pollingThread = null;
            }

            InputBoosterConfig.save();
            active = false;
            initialized.set(false);

            LOGGER.info("[{}] Shutdown complete.", MOD_NAME);
        } catch (Exception e) {
            LOGGER.error("[{}] Error during shutdown", MOD_NAME, e);
        }
    }
}
