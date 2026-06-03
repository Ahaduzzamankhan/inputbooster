package dev.inputbooster;

import dev.inputbooster.feature.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class InputBoosterMod implements ClientModInitializer {

    public static final String MOD_ID      = "inputbooster";
    public static final String MOD_NAME    = "InputBooster";
    public static final String MOD_VERSION = "3.0.2-rl02";
    public static final String DISPLAY_VERSION = "3.0.2-rl02-mc26";

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
    public static EventLog            eventLog;
    public static ModuleManager       moduleManager;
    public static ReplayRecorder      replayRecorder;
    public static SafeModeManager     safeMode;
    public static KeybindConflictDetector keybindConflictDetector;
    public static PerServerProfileManager perServerProfileManager;
    public static ConfigTools         configTools;

    private static KeyBinding replayRecordKey;
    private static KeyBinding replayPlayKey;
    private static final int[] COMBO_PRESET_HZ = {100, 200, 350, 500, 1000};
    private static final boolean[] comboDigitHeld = new boolean[COMBO_PRESET_HZ.length];
    private static double smoothedFps = 60.0D;
    private static int stableFpsTicks = 0;
    private static int unstableFpsTicks = 0;

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
            eventLog       = new EventLog();
            moduleManager  = new ModuleManager();
            replayRecorder = new ReplayRecorder();
            safeMode       = new SafeModeManager();
            keybindConflictDetector = new KeybindConflictDetector();
            perServerProfileManager = new PerServerProfileManager();
            perServerProfileManager.load();
            configTools = new ConfigTools();

            int initialHz = InputBoosterConfig.isPollRateAutoMode()
                            ? 200 : InputBoosterConfig.getPollRateHz();
            pollingThread = new InputPollingThread(initialHz);
            pollingThread.start();
            currentPollHz = initialHz;

            replayRecordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputbooster.replay_record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, KeyBinding.Category.MISC));
            replayPlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.inputbooster.replay_play", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyBinding.Category.MISC));

            ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
            DebugOverlayManager.register();

            initialized.set(true);
            eventLog.add("InputBooster initialized");
            LOGGER.info("[{}] Ready!", MOD_NAME);
        } catch (Exception e) {
            LOGGER.error("[{}] Fatal init error!", MOD_NAME, e);
            active = false;
        }
    }

    private void onClientTick(MinecraftClient client) {
        // Replay keybinds are independent of movement/input processing.
        if (initialized.get()) handleKeybinds(client);

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

            handleComboKeys(client);

            if (moduleManager.enabled("profiles")) perServerProfileManager.tick(client);
            if (moduleManager.enabled("debug")) keybindConflictDetector.tick(client);
            if (moduleManager.enabled("replay")) replayRecorder.tick();
            if (moduleManager.enabled("movement")) {
                sprintManager.tick(client);
                wTapAssist.tick(client);
                autoStrafe.tick(client);
            }
            if (moduleManager.enabled("anti_idle")) antiIdle.tick(client);
            if (moduleManager.enabled("combat")) cpsLimiter.tick(client);
            if (InputBoosterConfig.isBurstModeEnabled()) burstMode.tick(client);
            sessionStats.tick(currentFps, cpsLimiter.getCps());
        } catch (Exception e) {
            LOGGER.warn("[{}] Tick error", MOD_NAME, e);
            if (safeMode != null) safeMode.recordError("client tick", e);
        }
    }

    private void handleKeybinds(MinecraftClient client) {
        if (replayRecordKey.wasPressed() && replayRecorder != null) {
            boolean recording = replayRecorder.toggleRecording();
            if (eventLog != null) eventLog.add("Replay recording " + (recording ? "started" : "stopped"));
            if (client.player != null) {
                client.player.sendMessage(Text.literal("InputBooster replay " + (recording ? "REC" : "STOP")), true);
            }
        }
        if (replayPlayKey.wasPressed() && replayRecorder != null) {
            replayRecorder.startPlayback();
            if (eventLog != null) eventLog.add("Replay playback started");
        }
    }

    private void handleComboKeys(MinecraftClient client) {
        if (!InputBoosterConfig.isComboKeysEnabled()) return;
        if (client.currentScreen != null) {
            resetComboKeyState();
            return;
        }

        long window = client.getWindow().getHandle();
        boolean ctrl = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (!ctrl) {
            resetComboKeyState();
            return;
        }

        int[] digits = {GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_5};
        for (int i = 0; i < digits.length; i++) {
            boolean pressed = GLFW.glfwGetKey(window, digits[i]) == GLFW.GLFW_PRESS;
            if (pressed && !comboDigitHeld[i]) {
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
            comboDigitHeld[i] = pressed;
        }
    }

    private void resetComboKeyState() {
        Arrays.fill(comboDigitHeld, false);
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
        if (fps <= 0) fps = (int)Math.round(smoothedFps);

        double previous = smoothedFps;
        smoothedFps = previous * 0.82D + fps * 0.18D;
        double dropRatio = previous <= 1.0D ? 0.0D : (previous - fps) / previous;
        boolean unstable = dropRatio > 0.18D || Math.abs(fps - smoothedFps) > 16.0D;

        if (unstable) {
            unstableFpsTicks = Math.min(40, unstableFpsTicks + 1);
            stableFpsTicks = 0;
        } else {
            stableFpsTicks = Math.min(40, stableFpsTicks + 1);
            if (stableFpsTicks >= 8) unstableFpsTicks = Math.max(0, unstableFpsTicks - 1);
        }

        int target;
        if (smoothedFps <= 16.0D)      target = 250;
        else if (smoothedFps <= 24.0D) target = 500;
        else if (smoothedFps <= 35.0D) target = 450;
        else if (smoothedFps <= 50.0D) target = 350;
        else if (smoothedFps <= 75.0D) target = 250;
        else if (smoothedFps <= 120.0D) target = 180;
        else                           target = 120;

        if (unstableFpsTicks >= 3) {
            target = Math.min(target, smoothedFps <= 24.0D ? 350 : 250);
        }

        int current = currentPollHz <= 0 ? target : currentPollHz;
        if (target > current) return Math.min(target, current + 100);
        if (target < current) return Math.max(target, current - 50);
        return target;
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
