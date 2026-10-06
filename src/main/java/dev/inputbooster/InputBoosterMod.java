package dev.inputbooster;

import dev.inputbooster.feature.*;
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
    public static final String MOD_VERSION = "4.0.0";

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

    // Key bindings
    private static volatile KeyMapping replayRecordKey;
    private static volatile KeyMapping replayPlayKey;
    private static final ComboKeyPresets COMBO_KEYS = new ComboKeyPresets();
    private static double smoothedFps = 60.0D;
    /** Client tick counter handed to the optimisation engine. */
    private static long perfTick;
    /** Reused by the game thread to compare bindings without allocating. */
    private static final int[] BINDING_SCRATCH = new int[dev.inputbooster.input.KeyBindingSet.SLOTS];
    /** Vanilla fallback codes; derived once, they never change mid-session. */
    private static volatile int[] vanillaDefaultCodes;
    /** Measured skips of the per-tick snapshot/binding rebuilds. Game thread writes. */
    public static volatile long keySnapshotRebuildsAvoided = 0;
    public static volatile long bindingRebuildsAvoided = 0;
    private static int stableFpsTicks = 0;
    private static int unstableFpsTicks = 0;
    private static boolean hadPlayer = false;

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
        if (initialized.get() || shuttingDown) return;
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

            int initialHz = InputBoosterConfig.isPollRateAutoMode() ? 200 : InputBoosterConfig.getPollRateHz();
            Minecraft client = Minecraft.getInstance();
            // Capture the window once, on the game thread, so the polling
            // thread never touches a Minecraft object.
            rawKeyState = dev.inputbooster.input.RawKeyState.of(
                client != null && client.getWindow() != null ? client.getWindow() : null);
            publishKeyBindings(client);
            pollingThread = new InputPollingThread(initialHz);
            pollingThread.start();
            currentPollHz = initialHz;

            DebugOverlayManager.register();
            // The optimisation engine runs alongside every feature above; it
            // adds no gameplay behaviour and no user-visible output.
            dev.inputbooster.perf.OptimizationManager.get()
                .setServerKeySource(InputBoosterMod::currentServerKey);
            dev.inputbooster.perf.OptimizationManager.get().start();
            initialized.set(true);
            eventLog.add("InputBooster initialized");
            LOGGER.info("[{}] Ready!", MOD_NAME);
        } catch (Exception e) {
            LOGGER.error("[{}] Fatal init error!", MOD_NAME, e);
            active = false;
        }
    }

    

    /** Called from the client tick event of the active loader. */
    public static void onClientTick() {
        if (shuttingDown) return;
        initialize();
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        handleKeybinds(client);
        if (!active || !initialized.get()) return;
        try {
            lastTickTime = System.nanoTime();
            gameReady = client.player != null;
            gamePaused = client.isPaused();
            dev.inputbooster.perf.OptimizationManager.get().tick(++perfTick);
            if (!gameReady && hadPlayer) {
                // Leaving a world (disconnect / world unload): drop transient
                // input state so nothing carries into the next session.
                InputActionQueue.clear();
                if (cpsLimiter != null) cpsLimiter.reset();
                if (replayRecorder != null) replayRecorder.stopPlayback();
                LatencyProfiler.reset();
            }
            hadPlayer = gameReady;
            if (client.options != null) {
                // Rebuild both snapshots only when their contents changed. On a
                // normal tick both are skipped, which removes roughly a dozen
                // short-lived allocations per tick that previously ran forever,
                // including while the player sat in the main menu.
                KeySnapshot previous = keySnapshot;
                if (previous == null || !previous.matches(client.options)) {
                    keySnapshot = new KeySnapshot(client.options);
                } else {
                    keySnapshotRebuildsAvoided++;
                }
                publishKeyBindings(client);
            }
            if (client.player == null) return;
            currentFps = McCompat.getFps(client);
            if (InputBoosterConfig.isPollRateAutoMode()) adjustPollRateAuto();
            else adjustPollRateManual();
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
            if (safeMode != null) safeMode.tick();
            sessionStats.tick(currentFps, cpsLimiter.getCps());
        } catch (Exception e) {
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

    /**
     * Identity of the world the player is in. The optimisation engine compares
     * this against the last observed value so server identity is re-derived on
     * change rather than every second forever. Null outside a world.
     */
    private static String currentServerKey() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return null;
        var connection = client.getConnection();
        var serverData = connection == null ? null : connection.getServerData();
        String host = serverData == null || serverData.ip == null ? "local" : serverData.ip;
        return host + '/' + client.level.dimension();
    }

    public static void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        LOGGER.info("[{}] Shutting down...", MOD_NAME);
        try {
            dev.inputbooster.perf.OptimizationManager.get().shutdown();
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
            active = false;
            initialized.set(false);
        } catch (Throwable t) {
            LOGGER.error("[{}] Shutdown error", MOD_NAME, t);
        }
    }

    /**
     * Republishes the key bindings the polling thread samples.
     *
     * <p>The bound codes are read straight into a reused scratch array and
     * compared against the currently published set; a new {@link KeyBindingSet}
     * is allocated only when a binding actually changed. The old path built a
     * map, a defaults array and a fresh set every tick — all thrown away
     * whenever the player had not touched their controls.
     */
    private static void publishKeyBindings(Minecraft client) {
        if (client == null || client.options == null) return;
        var options = client.options;
        int[] defaults = vanillaDefaults();
        int[] next = BINDING_SCRATCH;
        boolean complete = true;
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.ATTACK, options.keyAttack, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.USE, options.keyUse, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.SPRINT, options.keySprint, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.SNEAK, options.keyShift, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.JUMP, options.keyJump, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.FORWARD, options.keyUp, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.BACK, options.keyDown, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.LEFT, options.keyLeft, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.RIGHT, options.keyRight, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.DROP, options.keyDrop, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.SWAP, options.keySwapOffhand, defaults);
        complete &= slotCode(next, dev.inputbooster.input.KeyBindingSet.PICK_BLOCK, options.keyPickItem, defaults);

        var current = keyBindings;
        if (current != null && current.matches(next, complete)) {
            bindingRebuildsAvoided++;
            return;
        }
        keyBindings = dev.inputbooster.input.KeyBindingSet.of(next, complete);
        if (!complete) {
            LOGGER.warn("[Input] Could not read every key binding; using vanilla defaults for the rest.");
        }
    }

    /** Reads one binding into {@code out[slot]}; false when it had to fall back. */
    private static boolean slotCode(int[] out, int slot, KeyMapping mapping, int[] defaults) {
        int code;
        try {
            code = dev.inputbooster.access.MixinAccess.boundKeyCode(mapping);
        } catch (Throwable t) {
            // Same warn-once contract as KeyBindingSet.of: a swallowed failure
            // silently degrades the key to its vanilla default.
            code = -1;
        }
        if (code < 0) {
            out[slot] = defaults[slot];
            return false;
        }
        out[slot] = code;
        return true;
    }

    private static int[] vanillaDefaults() {
        int[] codes = vanillaDefaultCodes;
        if (codes == null) {
            codes = dev.inputbooster.input.KeyBindingSet.defaultCodes(
                mouseCode(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT),
                mouseCode(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT),
                mouseCode(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE));
            vanillaDefaultCodes = codes;
        }
        return codes;
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
                boolean found = false;
                for (var m : Minecraft.class.getDeclaredMethods()) {
                    if (m.getName().equals(name)) { found = true; break; }
                }
                if (!found) missing.add(name);
            } catch (Throwable t) {
                missing.add(name);
            }
        }
        if (missing.isEmpty()) {
            LOGGER.info("[Mixin] All client hooks present.");
        } else {
            LOGGER.error("[Mixin] Missing vanilla method(s): {} — input handling is degraded. "
                + "The mod may need an update for this Minecraft version.", String.join(", ", missing));
        }
    }
}