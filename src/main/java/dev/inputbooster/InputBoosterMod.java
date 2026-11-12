package dev.inputbooster;

import dev.inputbooster.engine.CompatibilityManager;
import dev.inputbooster.engine.InputEngine;
import dev.inputbooster.feature.*;
import dev.inputbooster.input.KeyboardRouter;
import com.mojang.blaze3d.platform.InputConstants;
import dev.inputbooster.compat.McVersion;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Loader-neutral core of InputBooster.
 *
 * The class no longer carries any loader-specific entrypoint. Platform mods
 * (NeoForge {@code @Mod} class, Fabric {@code ClientModInitializer}) call
 * {@link #bootstrap()}, register the key mappings returned by
 * {@link #createKeyMappings()} with their own registry, and then forward
 * {@link #onClientTick()} from the client tick event.
 *
 * Author: Ahaduzzaman Khan
 */
public final class InputBoosterMod {
    public static final String MOD_ID = "inputbooster";
    public static final String MOD_NAME = "InputBooster";
    public static final String MOD_VERSION = "4.1.2-alpha";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Runtime state
    public static volatile boolean gameReady = false;
    public static volatile boolean gamePaused = false;
    public static volatile boolean active = true;
    /** Set once the client starts closing; blocks re-initialisation and polling. */
    public static volatile boolean shuttingDown = false;
    public static volatile KeySnapshot keySnapshot = null;
    /** Player key bindings, republished by the game thread for the poller. */
    public static volatile dev.inputbooster.input.KeyBindingSet keyBindings = null;
    /** Raw platform key state; captured once on the game thread. */
    public static volatile dev.inputbooster.input.RawKeyState rawKeyState = null;
    public static final AtomicLong totalHits = new AtomicLong(0);
    public static final AtomicLong recoveredInputs = new AtomicLong(0);
    public static final AtomicBoolean initialized = new AtomicBoolean(false);
    public static volatile int currentPollHz = 200;
    public static volatile int currentFps = 0;
    public static volatile long lastTickTime = 0;
    /**
     * True while the game thread allows world input: a player exists and no
     * screen is open. The polling thread reads this so a click in a container
     * is never turned into a world action.
     */
    public static volatile boolean inputAllowed = false;

    // Managers & utilities
    public static InputPollingThread pollingThread;
    public static SprintManager sprintManager;
    public static WTapAssist wTapAssist;
    public static AntiIdleManager antiIdle;
    public static AutoStrafeManager autoStrafe;
    public static CpsLimiter cpsLimiter;
    public static DebugOverlayManager debugOverlay;
    public static BurstModeManager burstMode;
    public static SessionStats sessionStats;
    public static ProfileManager profileManager;
    public static EventLog eventLog;
    public static ModuleManager moduleManager;
    public static ReplayRecorder replayRecorder;
    public static SafeModeManager safeMode;
    public static KeybindConflictDetector keybindConflictDetector;
    public static PerServerProfileManager perServerProfileManager;
    public static ConfigTools configTools;
    /** The mod's own input processing engine. Null until initialisation. */
    public static volatile InputEngine inputEngine;
    /** Input-mod detection and engine-mode fallback. */
    public static volatile CompatibilityManager compatibilityManager;
    private static int lastConfiguredEngineMode = -1;

    // Key bindings
    private static volatile KeyMapping replayRecordKey;
    private static volatile KeyMapping replayPlayKey;
    private static final ComboKeyPresets COMBO_KEYS = new ComboKeyPresets();
    private static double smoothedFps = 60.0D;
    private static int stableFpsTicks = 0;
    private static int unstableFpsTicks = 0;
    private static boolean hadPlayer = false;
    /** Set when initialisation failed for good; stops the per-tick retry loop. */
    private static volatile boolean initFailed = false;

    private InputBoosterMod() {}

    /**
     * Creates the mod's key mappings.
     *
     * FIX (keybinds never registered): the mappings used to be constructed in
     * the client-setup callback, which on NeoForge runs *after* the
     * key-mapping registration event. The registration handler therefore only
     * ever saw {@code null} and the R / K bindings were silently dropped from
     * the controls screen. Platforms must call this during their own bootstrap
     * — before they register the returned mappings.
     *
     * @return the two mod key mappings, in a stable order
     */
    public static KeyMapping[] createKeyMappings() {
        if (replayRecordKey == null) {
            replayRecordKey = new KeyMapping("key.inputbooster.replay_record", InputConstants.KEY_R, KeyMapping.Category.MISC);
        }
        if (replayPlayKey == null) {
            replayPlayKey = new KeyMapping("key.inputbooster.replay_play", InputConstants.KEY_K, KeyMapping.Category.MISC);
        }
        return new KeyMapping[]{replayRecordKey, replayPlayKey};
    }

    /** Called by the platform entrypoint during mod construction. */
    public static void bootstrap() {
        createKeyMappings();
    }

    public static void initialize() {
        if (initialized.get() || shuttingDown || initFailed) return;
        LOGGER.info("[{}] Starting v{}", MOD_NAME, MOD_VERSION);
        try {
            createKeyMappings();
            verifyClientHooks();
            InputBoosterConfig.load();
            // Initialise managers
            sprintManager = new SprintManager();
            wTapAssist = new WTapAssist();
            antiIdle = new AntiIdleManager();
            autoStrafe = new AutoStrafeManager();
            cpsLimiter = new CpsLimiter();
            debugOverlay = new DebugOverlayManager();
            burstMode = new BurstModeManager();
            sessionStats = new SessionStats();
            profileManager = new ProfileManager();
            profileManager.load();
            eventLog = new EventLog();
            moduleManager = new ModuleManager();
            replayRecorder = new ReplayRecorder();
            safeMode = new SafeModeManager();
            keybindConflictDetector = new KeybindConflictDetector();
            perServerProfileManager = new PerServerProfileManager();
            perServerProfileManager.load();
            configTools = new ConfigTools();
            compatibilityManager = new CompatibilityManager();
            compatibilityManager.detect();
            inputEngine = new InputEngine();
            lastConfiguredEngineMode = InputBoosterConfig.getEngineMode();
            inputEngine.setMode(compatibilityManager.resolveMode());

            int initialHz = InputBoosterConfig.isPollRateAutoMode() ? 200 : InputBoosterConfig.getPollRateHz();
            Minecraft client = Minecraft.getInstance();
            // Capture the window once, on the game thread, so the polling
            // thread never touches a Minecraft object.
            rawKeyState = dev.inputbooster.input.RawKeyState.of(
                client != null && client.getWindow() != null ? client.getWindow() : null);
            publishKeyBindings(client);
            inputEngine.publishBindings(keyBindings);
            KeyboardRouter.reset();
            // Mark initialised before the poller starts: the loop reads this
            // flag on its first iteration, and a start that fails after the
            // thread is created used to leave a running poller behind while
            // the mod still reported itself as uninitialised.
            initialized.set(true);
            DebugOverlayManager.register();
            pollingThread = new InputPollingThread(initialHz);
            pollingThread.start();
            currentPollHz = initialHz;
            eventLog.add("InputBooster initialized");
            LOGGER.info("[{}] Ready!", MOD_NAME);
        } catch (Throwable e) {
            // Retrying every tick logged the same failure forever and, when it
            // happened after the poller started, left another polling thread
            // behind on every attempt. Throwable, not Exception: a missing
            // class or a linkage error from a version mismatch would otherwise
            // escape into the tick handler on every single tick.
            initFailed = true;
            LOGGER.error("[{}] Fatal init error! InputBooster is disabled for this session.", MOD_NAME, e);
            active = false;
        }
    }

    

    /** Called from the client tick event of the active loader. */
    public static void onClientTick() {
        if (shuttingDown) return;
        initialize();
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        // Screens own the keyboard. Publishing this here keeps the decision on
        // the game thread while the poller only reads a volatile boolean. The
        // router records screen changes from the game's own show-screen call,
        // so this does not depend on catching the HUD mid-tick.
        inputAllowed = client.player != null && KeyboardRouter.worldInputAllowed(client);
        handleKeybinds(client);
        if (!active || !initialized.get()) return;
        try {
            lastTickTime = System.nanoTime();
            gameReady = client.player != null;
            gamePaused = client.isPaused();
            if (!gameReady && hadPlayer) {
                // Leaving a world (disconnect / world unload): drop transient
                // input state so nothing carries into the next session.
                InputActionQueue.clear();
                if (inputEngine != null) inputEngine.reset();
                if (cpsLimiter != null) cpsLimiter.reset();
                if (replayRecorder != null) replayRecorder.stopPlayback();
                LatencyProfiler.reset();
            }
            hadPlayer = gameReady;
            if (client.options != null) {
                keySnapshot = new KeySnapshot(client.options);
                publishKeyBindings(client);
                if (inputEngine != null && keyBindings != null) inputEngine.publishBindings(keyBindings);
            }
            // A focus change must not leave a key stuck down in the engine.
            if (inputEngine != null && (client.getWindow() == null || !client.getWindow().isFocused())) {
                inputEngine.reset();
            }
            if (inputEngine != null && compatibilityManager != null
                && lastConfiguredEngineMode != InputBoosterConfig.getEngineMode()) {
                lastConfiguredEngineMode = InputBoosterConfig.getEngineMode();
                inputEngine.setMode(compatibilityManager.resolveMode());
            }
            if (client.player == null) return;
            currentFps = McCompat.getFps(client);
            if (InputBoosterConfig.isPollRateAutoMode()) adjustPollRateAuto();
            else adjustPollRateManual();
            handleComboKeys(client);
            if (moduleManager.enabled("profiles")) perServerProfileManager.tick(client);
            if (moduleManager.enabled("debug")) keybindConflictDetector.tick(client);
            // Playback must not advance behind a screen. The drainer clears the
            // queue while a screen is open, so a replayed event queued there
            // would be consumed from the recording and never executed: the
            // replay would silently skip inputs. Wait until the player owns the
            // keyboard again.
            if (moduleManager.enabled("replay") && inputAllowed) replayRecorder.tick();
            if (moduleManager.enabled("movement")) {
                sprintManager.tick(client);
                wTapAssist.tick(client);
                autoStrafe.tick(client);
            }
            if (moduleManager.enabled("anti_idle")) antiIdle.tick(client);
            if (moduleManager.enabled("combat")) cpsLimiter.tick(client);
            if (InputBoosterConfig.isBurstModeEnabled()) burstMode.tick(client);
            if (safeMode != null) safeMode.tick();
            sessionStats.tick(currentFps, cpsLimiter.getCps());
        } catch (Throwable e) {
            LOGGER.warn("[{}] Tick error", MOD_NAME, e);
            if (safeMode != null) safeMode.recordError("client tick", e);
        }
    }

    private static void handleKeybinds(Minecraft client) {
        KeyMapping record = replayRecordKey;
        KeyMapping play = replayPlayKey;
        if (record != null && record.consumeClick() && replayRecorder != null) {
            boolean recording = replayRecorder.toggleRecording();
            if (eventLog != null) eventLog.add("Replay recording " + (recording ? "started" : "stopped"));
            if (client.player != null) {
                client.player.sendOverlayMessage(Component.literal("InputBooster replay " + (recording ? "REC" : "STOP")));
            }
        }
        if (play != null && play.consumeClick() && replayRecorder != null) {
            replayRecorder.startPlayback();
            if (eventLog != null) eventLog.add("Replay playback started");
        }
    }

    private static void handleComboKeys(Minecraft client) {
        if (!InputBoosterConfig.isComboKeysEnabled()) {
            resetComboKeyState();
            return;
        }
        if (client.gui != null && client.gui.screen() != null) {
            resetComboKeyState();
            return;
        }
        // Raw key state is read through the version compat shim: 26.2 still passes
        // the window handle to InputConstants, 26.3 does not.
        boolean ctrl = McVersion.isKeyDown(InputConstants.KEY_LCONTROL)
                || McVersion.isKeyDown(InputConstants.KEY_RCONTROL);
        if (!ctrl) {
            resetComboKeyState();
            return;
        }
        int[] digits = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
                        InputConstants.KEY_4, InputConstants.KEY_5};
        for (int i = 0; i < digits.length; i++) {
            boolean pressed = McVersion.isKeyDown(digits[i]);
            // FIX (combo keys re-triggered every tick): the latch used to be
            // written *after* the break, so the digit that fired the preset was
            // never marked as held and Ctrl+1 re-applied the poll rate on every
            // single tick while the key was kept down.
            if (COMBO_KEYS.press(i, pressed)) {
                int hz = ComboKeyPresets.HZ[i];
                InputBoosterConfig.setPollRateAutoMode(false);
                InputBoosterConfig.setPollRateHz(hz);
                adjustPollRateManual();
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("§b[InputBooster] §ePoll rate: §a" + hz + " Hz"));
                }
            }
        }
    }

    private static void resetComboKeyState() {
        COMBO_KEYS.reset();
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
        if (fps <= 0) fps = (int) Math.round(smoothedFps);
        double previous = smoothedFps;
        smoothedFps = previous * 0.82 + fps * 0.18;
        double dropRatio = previous <= 1.0 ? 0.0 : (previous - fps) / previous;
        boolean unstable = dropRatio > 0.18 || Math.abs(fps - smoothedFps) > 16.0;
        if (unstable) {
            unstableFpsTicks = Math.min(40, unstableFpsTicks + 1);
            stableFpsTicks = 0;
        } else {
            stableFpsTicks = Math.min(40, stableFpsTicks + 1);
            if (stableFpsTicks >= 8) unstableFpsTicks = Math.max(0, unstableFpsTicks - 1);
        }
        int target;
        if (smoothedFps <= 16.0) target = 250;
        else if (smoothedFps <= 24.0) target = 500;
        else if (smoothedFps <= 35.0) target = 450;
        else if (smoothedFps <= 50.0) target = 350;
        else if (smoothedFps <= 75.0) target = 250;
        else if (smoothedFps <= 120.0) target = 180;
        else target = 120;
        if (unstableFpsTicks >= 3) {
            target = Math.min(target, smoothedFps <= 24.0 ? 350 : 250);
        }
        int current = currentPollHz <= 0 ? target : currentPollHz;
        if (target > current) return Math.min(target, current + 100);
        if (target < current) return Math.max(target, current - 50);
        return target;
    }

    public static boolean debugMode() {
        return InputBoosterConfig.isDebugMode();
    }

    public static void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        LOGGER.info("[{}] Shutting down...", MOD_NAME);
        try {
            if (pollingThread != null) {
                // Wait for the poller to actually finish: Minecraft keeps
                // running ticks (and may still dispatch events) while close()
                // unwinds, and a surviving poller would touch the window.
                if (!pollingThread.stopPollingAndAwait(2_000L)) {
                    LOGGER.warn("[{}] Polling thread did not stop within 2s", MOD_NAME);
                }
                pollingThread = null;
            }
            InputActionQueue.clear();
            keySnapshot = null;
            rawKeyState = null;
            InputBoosterConfig.save();
            inputAllowed = false;
            KeyboardRouter.reset();
            if (inputEngine != null) inputEngine.reset();
            active = false;
            initialized.set(false);
        } catch (Throwable t) {
            LOGGER.error("[{}] Shutdown error", MOD_NAME, t);
        }
    }

    /**
     * Republishes the key bindings the polling thread samples. The codes are
     * rebuilt every tick (a dozen array slots), but the volatile set the poller
     * reads is only replaced when a binding actually changed.
     */
    private static void publishKeyBindings(Minecraft client) {
        if (client == null || client.options == null) return;
        var options = client.options;
        var defaults = dev.inputbooster.input.KeyBindingSet.defaultCodes(
            mouseCode(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT),
            mouseCode(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT),
            mouseCode(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE));
        var map = dev.inputbooster.input.KeyBindingSet.map(
            options.keyAttack, options.keyUse, options.keySprint, options.keyShift,
            options.keyJump, options.keyUp, options.keyDown, options.keyLeft,
            options.keyRight, options.keyDrop, options.keySwapOffhand, options.keyPickItem);
        var next = dev.inputbooster.input.KeyBindingSet.of(map, defaults);
        var current = keyBindings;
        if (current == null || !java.util.Arrays.equals(current.codes, next.codes)) {
            keyBindings = next;
            if (!next.complete) {
                LOGGER.warn("[Input] Could not read every key binding; using vanilla defaults for the rest.");
            }
        }
    }

    private static int mouseCode(int button) {
        var key = com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button);
        return key == null ? -1 : key.getValue();
    }

    /**
     * Verifies that the vanilla methods the mixins attach to still exist.
     *
     * The mixins use {@code require = 0} so an API change degrades instead of
     * crashing the game, which means a silent failure would otherwise disable
     * input handling with no explanation. This check logs exactly what is
     * missing instead.
     */
    private static void verifyClientHooks() {
        String[] required = {"tick", "startAttack", "startUseItem", "close"};
        java.util.List<String> missing = new java.util.ArrayList<>();
        for (String name : required) {
            try {
                if (!hasMethod(name)) missing.add(name);
            } catch (Throwable t) {
                missing.add(name);
            }
        }
        // The screen router has two candidate hooks and only needs one. Report
        // which one this client really has, so a router that silently failed to
        // apply is visible in the log instead of looking like a jump bug.
        boolean showHook = false, setHook = false;
        try {
            showHook = hasMethod("setScreenAndShow");
            setHook = hasMethod("setScreen");
        } catch (Throwable ignored) {
            // Reflection unavailable: the missing-hook path below covers it.
        }
        if (showHook || setHook) {
            LOGGER.info("[Mixin] Screen hooks: setScreenAndShow={}, setScreen={}", showHook, setHook);
        } else {
            LOGGER.warn("[Mixin] No screen hook found; screen state falls back to the HUD check each tick.");
        }
        if (missing.isEmpty()) {
            LOGGER.info("[Mixin] All client hooks present.");
        } else {
            LOGGER.error("[Mixin] Missing vanilla method(s): {} — input handling is degraded. "
                + "The mod may need an update for this Minecraft version.", String.join(", ", missing));
        }
    }

    private static boolean hasMethod(String name) {
        for (var m : Minecraft.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }
}