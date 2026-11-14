package dev.inputbooster.engine;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides how much of the input path the mod is allowed to own.
 *
 * <p>Two facts drive the decision:
 * <ul>
 *   <li>whether the observing event mixins actually applied, and</li>
 *   <li>whether another mod is known to rewrite input handling.</li>
 * </ul>
 *
 * <p>When a known input mod is present and fallback is enabled, the engine
 * chooses POLL: it keeps the high-rate sampling it has always done and leaves
 * event handling entirely to the other mod. Nothing is intercepted, nothing is
 * cancelled, and the vanilla handlers keep running untouched in every mode.
 *
 * <p>Detection is a class lookup, not a mod-list dependency, so it works
 * without touching loader APIs.
 */
public final class CompatibilityManager {

    /**
     * Mods whose presence means another component owns input dispatch.
     *
     * <p>Only mods that really take over keyboard or mouse handling belong in
     * this list. Sodium and Iris were listed here and are rendering mods: with
     * the compatibility fallback on (the default) their mere presence forced
     * the engine into POLL mode on the most common client stack, which silently
     * disabled the event path for no reason at all. Zoomify is an input mod — it
     * holds the middle mouse button — so it stays.
     */
    private static final String[] KNOWN_INPUT_MODS = {
        "controlify", "midnightcontrols", "controllable", "zoomify"
    };

    private final List<String> detected = new ArrayList<>();
    private boolean eventSourceActive;
    private String fallbackReason = "";

    /**
     * Asks the loader once during initialisation.
     *
     * <p>Presence is a loader question, so this is a map lookup per mod id. The
     * previous version called {@code Class.forName} on each mod's main class,
     * which made the game load another mod's classes — and everything those
     * reference — while this mod was initialising. That is real startup work for
     * a decision the loader can answer from its metadata.
     */
    public void detect() {
        detected.clear();
        for (String modId : KNOWN_INPUT_MODS) {
            try {
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId)) {
                    detected.add(modId);
                }
            } catch (Throwable ignored) {
                // Loader unavailable (unit tests): no mod is detected.
            }
        }
    }

    /** Called by the processors the first time an event actually arrives. */
    public void markEventSourceActive() {
        if (!eventSourceActive) {
            eventSourceActive = true;
            InputBoosterMod.LOGGER.info("[Compat] Keyboard/mouse event hooks are live.");
        }
    }

    public boolean eventSourceActive() {
        return eventSourceActive;
    }

    public List<String> detectedMods() {
        return List.copyOf(detected);
    }

    /**
     * Resolves the engine mode for this session.
     *
     * @return the mode the engine should run in
     */
    public InputEngine.Mode resolveMode() {
        InputEngine.Mode configured = InputEngine.Mode.fromConfig(InputBoosterConfig.getEngineMode());
        if (configured == InputEngine.Mode.EVENT && !eventSourceActive) {
            fallbackReason = "event hooks unavailable";
            return InputEngine.Mode.POLL;
        }
        if (configured == InputEngine.Mode.AUTO) {
            if (!detected.isEmpty() && InputBoosterConfig.isCompatFallback()) {
                fallbackReason = "compatibility fallback: " + String.join(", ", detected);
                return InputEngine.Mode.POLL;
            }
            return eventSourceActive ? InputEngine.Mode.EVENT : InputEngine.Mode.POLL;
        }
        return configured;
    }

    public String fallbackReason() {
        return fallbackReason;
    }

    public String statusLine() {
        String mode = InputBoosterMod.inputEngine == null ? "starting" : InputBoosterMod.inputEngine.mode().label;
        String compat = detected.isEmpty() ? "no known input mods" : String.join(", ", detected);
        return "Engine: " + mode + " | " + compat;
    }
}
