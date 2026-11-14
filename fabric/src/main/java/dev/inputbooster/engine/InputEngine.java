package dev.inputbooster.engine;

import dev.inputbooster.InputAction;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.KeySnapshot;
import dev.inputbooster.input.KeyBindingSet;
import dev.inputbooster.input.RawKeyState;

/**
 * The mod's own input processing engine.
 *
 * <p>What it replaces is the mod's previous ad-hoc edge detector, not vanilla's
 * input handling. Vanilla still receives every event through
 * {@code KeyboardHandler} and {@code MouseHandler}; the observing mixins run at
 * HEAD, cancel nothing and change no binding. The engine's job is to turn key
 * state into exactly one ordered action per physical edge, quickly.
 *
 * <p>Two sources feed the same state machine:
 * <ul>
 *   <li>the polling thread, which reads raw platform key state between ticks, and</li>
 *   <li>the keyboard and mouse handlers, which report the event itself.</li>
 * </ul>
 *
 * <p>Both go through {@link KeyStateManager#updateSlot}, and only a real change
 * emits an action. A press seen by the event hook first makes the poller's later
 * look a no-op, and the other way round. Duplicate suppression is therefore
 * structural rather than a timing window.
 *
 * <p>No allocation happens per poll: the raw path reads codes directly instead
 * of building a snapshot object every iteration.
 *
 * <p>Mode selection and fallback live in {@link CompatibilityManager}. In POLL
 * mode the event hooks still update state, so a switch is seamless, but they
 * never dispatch.
 */
public final class InputEngine {

    /** How the engine is allowed to observe input. */
    public enum Mode {
        /** Use event hooks when they applied and no known input mod conflicts. */
        AUTO("auto"),
        /** Use event hooks only. Falls back to poll if they are unavailable. */
        EVENT("event"),
        /** Poll raw state only; event hooks never dispatch. */
        POLL("poll");

        public final String label;

        Mode(String label) { this.label = label; }

        public static Mode fromConfig(int value) {
            return switch (value) {
                case 1 -> EVENT;
                case 2 -> POLL;
                default -> AUTO;
            };
        }
    }

    private static final InputAction[] PRESS = {
        InputAction.ATTACK_PRESSED, InputAction.USE_PRESSED, InputAction.SPRINT_PRESSED,
        InputAction.SNEAK_PRESSED, InputAction.JUMP_PRESSED, InputAction.FORWARD_PRESSED,
        InputAction.BACK_PRESSED, InputAction.LEFT_PRESSED, InputAction.RIGHT_PRESSED,
        InputAction.DROP_PRESSED, InputAction.SWAP_PRESSED, InputAction.PICK_BLOCK_PRESSED,
    };

    /** Jump, drop, swap and pick-block have no release handling in the pipeline. */
    private static final InputAction[] RELEASE = {
        InputAction.ATTACK_RELEASED, InputAction.USE_RELEASED, InputAction.SPRINT_RELEASED,
        InputAction.SNEAK_RELEASED, null, InputAction.FORWARD_RELEASED,
        InputAction.BACK_RELEASED, InputAction.LEFT_RELEASED, InputAction.RIGHT_RELEASED,
        null, null, null,
    };

    private final KeyStateManager state = new KeyStateManager();
    private final KeyBindingProcessor bindings = new KeyBindingProcessor();
    private final DiagnosticsManager diagnostics = new DiagnosticsManager();
    private final InputEventDispatcher dispatcher = new InputEventDispatcher(diagnostics);

    private volatile Mode mode = Mode.AUTO;
    private volatile boolean eventDriven;

    public DiagnosticsManager diagnostics() { return diagnostics; }
    public KeyBindingProcessor keyBindings() { return bindings; }
    public KeyStateManager state() { return state; }
    public Mode mode() { return mode; }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.AUTO : mode;
    }

    /** Publishes new bindings; returns true when they changed. */
    public boolean publishBindings(KeyBindingSet set) {
        return bindings.update(set);
    }

    /** Samples raw platform state and dispatches edges. Polling thread only. */
    public int pollRaw(RawKeyState raw, KeyBindingSet set) {
        return pollRaw(raw, set, true);
    }

    /** Follows raw state without emitting (screen open, paused, no player). */
    public void syncOnlyRaw(RawKeyState raw, KeyBindingSet set) {
        pollRaw(raw, set, false);
    }

    private int pollRaw(RawKeyState raw, KeyBindingSet set, boolean dispatch) {
        if (raw == null || set == null) return 0;
        int emitted = 0;
        for (int slot = 0; slot < KeyStateManager.SLOTS; slot++) {
            int code = set.codes[slot];
            boolean down = code >= 0 && raw.isDown(code);
            state.setRaw(code, down);
            emitted += apply(slot, down, dispatch);
        }
        return emitted;
    }

    /** Samples the tick-rate snapshot used when raw state is unavailable. */
    public int pollSnapshot(KeySnapshot snapshot) {
        return pollSnapshot(snapshot, true);
    }

    /** Follows the tick snapshot without emitting anything. */
    public void syncOnlySnapshot(KeySnapshot snapshot) {
        pollSnapshot(snapshot, false);
    }

    private int pollSnapshot(KeySnapshot snapshot, boolean dispatch) {
        if (snapshot == null) return 0;
        int emitted = 0;
        emitted += apply(0, snapshot.attack, dispatch);
        emitted += apply(1, snapshot.use, dispatch);
        emitted += apply(2, snapshot.sprint, dispatch);
        emitted += apply(3, snapshot.sneak, dispatch);
        emitted += apply(4, snapshot.jump, dispatch);
        emitted += apply(5, snapshot.forward, dispatch);
        emitted += apply(6, snapshot.back, dispatch);
        emitted += apply(7, snapshot.left, dispatch);
        emitted += apply(8, snapshot.right, dispatch);
        emitted += apply(9, snapshot.drop, dispatch);
        emitted += apply(10, snapshot.swap, dispatch);
        emitted += apply(11, snapshot.pickBlock, dispatch);
        return emitted;
    }

    /**
     * Keyboard or mouse handler reported a state change.
     *
     * <p>The event always updates state, even in POLL mode, so a mode switch does
     * not start from a stale picture. Dispatching is what POLL mode suppresses.
     */
    public void onKeyState(int code, boolean down) {
        CompatibilityManager compat = InputBoosterMod.compatibilityManager;
        if (compat != null) {
            boolean firstEvent = !compat.eventSourceActive();
            compat.markEventSourceActive();
            if (firstEvent) {
                eventDriven = true;
                // The hooks are proven live, so AUTO may now choose EVENT mode.
                setMode(compat.resolveMode());
            }
        }

        state.setRaw(code, down);
        if (mode == Mode.POLL) return;

        int slot = bindings.slotForCode(code);
        if (slot < 0) return;
        apply(slot, down, true);
    }

    /** @return 1 when an action was emitted, 0 otherwise */
    private int apply(int slot, boolean down, boolean dispatch) {
        // updateSlot is the single source of truth: an unchanged value means the
        // other input source already reported this edge, which is exactly the
        // duplicate case. No timing window, no per-tick flag.
        if (!state.updateSlot(slot, down)) return 0;
        if (!dispatch) return 0;

        InputAction action = down ? PRESS[slot] : RELEASE[slot];
        if (action == null) return 0;

        boolean queued = dispatcher.submit(action, clickDedupeMs(action));
        return queued ? 1 : 0;
    }

    private static int clickDedupeMs(InputAction action) {
        if (action != InputAction.ATTACK_PRESSED) return 0;
        return InputBoosterConfig.getMouseClickDedupeMs();
    }

    /**
     * Releases every held key. Called when the window loses focus, a world is
     * left, or the engine is shut down, so nothing carries over.
     */
    public void reset() {
        state.releaseAll();
        dispatcher.reset();
        MouseProcessor.reset();
    }

    public boolean eventDriven() {
        return eventDriven;
    }

    /** Human-readable state for the overlay and diagnostics tab. */
    public String statusLine() {
        return "Input: " + mode.label + (eventDriven ? " (event)" : " (poll)");
    }
}
