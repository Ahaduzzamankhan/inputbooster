package dev.inputbooster;

/**
 * InputAction — all trackable key events, stamped with a capture timestamp
 * for Input Latency Profiler (Feature 2).
 *
 * The timestamp field records System.nanoTime() at the moment the polling
 * thread captures the key state change. InputDrainer records drain time and
 * LatencyProfiler computes the delta.
 *
 * Version: 3.1.2
 * Author: Ahaduzzaman Khan
 */
public enum InputAction {
    ATTACK_PRESSED, ATTACK_RELEASED,
    USE_PRESSED, USE_RELEASED,
    SPRINT_PRESSED, SPRINT_RELEASED,
    SNEAK_PRESSED, SNEAK_RELEASED,
    JUMP_PRESSED,
    FORWARD_PRESSED,  FORWARD_RELEASED,
    BACK_PRESSED,     BACK_RELEASED,
    LEFT_PRESSED,     LEFT_RELEASED,
    RIGHT_PRESSED,    RIGHT_RELEASED,
    DROP_PRESSED,
    SWAP_PRESSED,
    PICK_BLOCK_PRESSED;

    /**
     * Where a queued event came from.
     *
     * Physical input and replayed input share one queue (so ordering and the
     * bound stay simple), but they are tagged: playback can yield to live
     * input, and statistics can tell them apart instead of blending them.
     */
    public enum Origin {
        /** Detected by the polling thread from real key state. */
        INPUT,
        /** Re-injected by the replay recorder. */
        REPLAY
    }

    // ── Timestamp stamping ────────────────────────────────────────────────────

    /**
     * Wraps an InputAction with a capture timestamp (nanos) and its origin.
     * Used by LatencyProfiler — see feature/LatencyProfiler.java.
     */
    public record Stamped(InputAction action, long capturedAt, Origin origin) {
        public static Stamped of(InputAction action) {
            return new Stamped(action, System.nanoTime(), Origin.INPUT);
        }

        public static Stamped of(InputAction action, Origin origin) {
            return new Stamped(action, System.nanoTime(), origin);
        }

        public boolean isReplay() {
            return origin == Origin.REPLAY;
        }
    }
}