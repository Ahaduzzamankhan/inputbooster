package dev.inputbooster.feature;

import java.util.concurrent.atomic.AtomicLong;

/**
 * LatencyProfiler — Input Latency Profiler (Feature 2).
 *
 * Measures the actual latency between a key being physically pressed
 * (captured and stamped in the polling thread) and being drained to the
 * game tick in InputDrainer. Exposes a rolling average and peak value
 * for display in the F3 overlay and HUD.
 *
 * Architecture:
 *  1. InputActionQueue stamps each action with System.nanoTime() at capture.
 *  2. InputDrainer calls recordDrain(capturedAt) when it processes the action.
 *  3. LatencyProfiler computes delta in milliseconds and maintains ring buffer.
 *
 * Version: 3.0.0
 * Author: Ahaduzzaman Khan
 */
public class LatencyProfiler {

    private static final int WINDOW = 128; // rolling window size

    private static final long[] samples = new long[WINDOW];
    private static int head = 0;
    private static int count = 0;

    private static final AtomicLong peakNs = new AtomicLong(0);

    /** Guards the ring buffer: recordDrain runs on the game thread, readers may not. */
    private static final Object LOCK = new Object();

    /**
     * Called by InputDrainer each time a stamped action is drained.
     *
     * @param capturedAtNs System.nanoTime() from when the action was polled
     */
    public static void recordDrain(long capturedAtNs) {
        long latencyNs = System.nanoTime() - capturedAtNs;
        if (latencyNs < 0) return; // clock skew guard

        peakNs.accumulateAndGet(latencyNs, Math::max);

        synchronized (LOCK) {
            samples[head] = latencyNs;
            head = (head + 1) % WINDOW;
            if (count < WINDOW) count++;
        }
    }

    /** Rolling average latency in milliseconds. */
    public static double getAverageMs() {
        int n;
        int oldest;
        long sum = 0;
        synchronized (LOCK) {
            n = count;
            if (n == 0) return 0.0;
            // Read the live window in chronological order — the buffer wraps.
            oldest = (n < WINDOW) ? 0 : head;
            for (int i = 0; i < n; i++) sum += samples[(oldest + i) % WINDOW];
        }
        return (sum / (double) n) / 1_000_000.0;
    }

    /** Peak latency in milliseconds (session-wide). */
    public static double getPeakMs() {
        return peakNs.get() / 1_000_000.0;
    }

    /** Reset peak (called on session reset or profile switch). */
    public static void resetPeak() {
        peakNs.set(0);
    }

    /** Reset all samples. */
    public static void reset() {
        synchronized (LOCK) {
            head = 0;
            count = 0;
        }
        peakNs.set(0);
    }

    /** Format for F3 overlay: "Input Latency: avg 4.2ms / peak 18ms" */
    public static String formatForOverlay() {
        return String.format("Input Latency: avg %.1fms / peak %.0fms",
            getAverageMs(), getPeakMs());
    }
}
