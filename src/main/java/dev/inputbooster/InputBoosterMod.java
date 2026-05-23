package dev.inputbooster;

import dev.inputbooster.feature.*;
import dev.inputbooster.screen.InputBoosterScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InputBoosterMod implements ClientModInitializer {

    public static final String MOD_ID      = "inputbooster";
    public static final String MOD_NAME    = "InputBooster";
    public static final String MOD_VERSION = "3.0.0";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static volatile boolean    gameReady    = false;
    public static volatile boolean    gamePaused   = false;
    public static volatile boolean    active       = true;
    public static volatile KeySnapshot keySnapshot = null;

    public static final AtomicLong  totalHits       = new AtomicLong(0);
    public static final AtomicLong  recoveredInputs = new AtomicLong(0);
    public static final AtomicBoolean initialized   = new AtomicBoolean(false);

    public static volatile int  currentPollHz = 200;
    public static volatile int  currentFps    = 0;
    public static volatile long lastTickTime  = 0;

    public static InputPollingThread  pollingThread;
    public static SprintManager       sprintManager;
    public static WTapAssist          wTapAssist;
    public static AntiIdleManager     antiIdle;
    public static AutoStrafeManager   autoStrafe;
    public static CpsLimiter          cpsLimiter;
    public static DebugOverlayManager debugOverlay;
    public static BurstModeManager    burstMode;
    public static SessionStats        sessionStats;
    public static ProfileManager      profileManager;

    private static KeyBinding openScreenKey;
    private static KeyBinding toggleModKey;
    private static final int[] COMBO_PRESET_HZ = {100, 200, 350, 500, 1000};

    @Override
    public void onInitializeClient() {
        LOGGER.info("[{}] Starting v{}", MOD_NAME, MOD_VERSION);
        try {
            InputBoosterConfig.load();

            sprintManager  = new SprintManager();
            wTapAssist     = new WTapAssist();
            antiIdle       = new AntiIdleManager();
            autoStrafe     = new AutoStrafeManager();
            cpsLimiter     = new CpsLimiter();
            debugOverlay   = new DebugOverlayManager();
            burstMode      = new BurstModeManager();
            sessionStats   = new SessionStats();
            profileManager = new ProfileManager();
            profileManager.load();

            int initialHz = InputBoosterConfig.isPollRateAutoMode()
                            ? 200 : InputBoosterConfig.getPollRateHz();
            pollingThread = new InputPollingThread(initialHz);
            pollingThread.start();
            currentPollHz = initialHz;

            openScreenKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputbooster.options", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, KeyBinding.Category.MISC));
            toggleModKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputbooster.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, KeyBinding.Category.MISC));

            ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
            DebugOverlayManager.register();

            initialized.set(true);
            LOGGER.info("[{}] Ready!", MOD_NAME);
        } catch (Exception e) {
            LOGGER.error("[{}] Fatal init error!", MOD_NAME, e);
            active = false;
        }
    }

    private void onClientTick(MinecraftClient client) {
        if (!active || !initialized.get()) return;
        try {
            lastTickTime = System.nanoTime();
            gameReady  = client.player != null;
            gamePaused = client.isPaused();

            if (client.options != null) {
                keySnapshot = new KeySnapshot(client.options);
            }
            if (client.player == null) return;

            currentFps = McCompat.getFps(client);

            if (InputBoosterConfig.isPollRateAutoMode()) adjustPollRateAuto();
            else                                          adjustPollRateManual();

            handleKeybinds(client);
            handleComboKeys(client);

            sprintManager.tick(client);
            wTapAssist.tick(client);
            antiIdle.tick(client);
            autoStrafe.tick(client);
            cpsLimiter.tick(client);
            burstMode.tick(client);
            sessionStats.tick(currentFps, cpsLimiter.getCps());
        } catch (Exception e) {
            LOGGER.warn("[{}] Tick error", MOD_NAME, e);
        }
    }

    private void handleKeybinds(MinecraftClient client) {
        if (openScreenKey.wasPressed()) {
            if (client.currentScreen == null || !(client.currentScreen instanceof InputBoosterScreen)) {
                client.setScreen(new InputBoosterScreen(client.currentScreen));
            }
        }
        if (toggleModKey.wasPressed()) {
            active = !active;
            String status = active ? "§a§lON" : "§c§lOFF";
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§7InputBooster " + status), true);
            }
        }
    }

    private void handleComboKeys(MinecraftClient client) {
        if (!InputBoosterConfig.isComboKeysEnabled()) return;
        if (client.currentScreen != null) return;

        long window = client.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (!ctrl) return;

        int[] digits = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5};
        for (int i = 0; i < digits.length; i++) {
            if (GLFW.glfwGetKey(window, digits[i]) == GLFW.GLFW_PRESS) {
                int hz = COMBO_PRESET_HZ[i];
                InputBoosterConfig.setPollRateAutoMode(false);
                InputBoosterConfig.setPollRateHz(hz);
                adjustPollRateManual();
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§b[InputBooster] §ePoll rate: §a" + hz + " Hz"), true);
                }
                break;
            }
        }
    }

    public static void adjustPollRateAuto() {
        int targetHz = calculateAutoHz(currentFps);
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

    public static int calculateAutoHz(int fps) {
        if (fps <= 20)  return 500;
        if (fps <= 30)  return 400;
        if (fps <= 60)  return 200;
        if (fps <= 120) return 150;
        return 100;
    }

    public static boolean debugMode() { return InputBoosterConfig.isDebugMode(); }

    public static void shutdown() {
        LOGGER.info("[{}] Shutting down...", MOD_NAME);
        try {
            if (pollingThread != null) { pollingThread.stopPolling(); pollingThread = null; }
            InputBoosterConfig.save();
            active = false;
            initialized.set(false);
        } catch (Exception e) {
            LOGGER.error("[{}] Shutdown error", MOD_NAME, e);
        }
    }
}
