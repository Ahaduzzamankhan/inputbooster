package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterMod;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * SessionStats — Session Statistics tracker (Feature 5).
 *
 * Tracks per-session metrics for display on the Stats tab of InputBoosterScreen:
 *  - Session start time
 *  - Total inputs recovered this session
 *  - CPS graph (last 60 seconds, 1-second buckets)
 *  - Poll thread uptime
 *  - Estimated inputs that would have been missed without the mod
 *    (based on FPS history × natural miss rate heuristic)
 *
 * Version: 3.0.0
 * Author: Ahaduzzaman Khan
 */
public class SessionStats {

    private static final int CPS_HISTORY_SECONDS = 60;
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    // Session metadata
    private final long sessionStartMs = System.currentTimeMillis();
    private final long sessionStartNs = System.nanoTime();

    // CPS sparkline: circular buffer of 60 one-second buckets
    private final int[] cpsBuckets    = new int[CPS_HISTORY_SECONDS];
    private int cpsBucketHead         = 0;
    private long lastBucketMs         = System.currentTimeMillis();
    private int currentBucketCount    = 0;

    // FPS history for missed-input estimation (last 60 ticks = ~3s)
    private final Deque<Integer> fpsHistory = new ArrayDeque<>();
    private static final int FPS_HISTORY_SIZE = 60;

    // Cumulative estimates
    private long estimatedMissedInputs = 0;
    private long inputsRecoveredAtLastTick = 0;
    private long hitsAtLastTick = 0;

    public void tick(int currentFps, int currentCps) {
        long nowMs = System.currentTimeMillis();

        // Advance CPS bucket if 1 second has passed
        if (nowMs - lastBucketMs >= 1000) {
            cpsBuckets[cpsBucketHead] = currentBucketCount;
            cpsBucketHead = (cpsBucketHead + 1) % CPS_HISTORY_SECONDS;
            currentBucketCount = 0;
            lastBucketMs = nowMs;
        }

        // Use hits delta instead of cumulative rolling CPS to avoid 20x multiplication bug
        long hits = InputBoosterMod.totalHits.get();
        long hitsDelta = hits - hitsAtLastTick;
        if (hitsDelta > 0) {
            currentBucketCount += hitsDelta;
        }
        hitsAtLastTick = hits;

        // FPS history for missed-input estimation
        fpsHistory.addLast(currentFps);
        if (fpsHistory.size() > FPS_HISTORY_SIZE) fpsHistory.pollFirst();

        // Estimate missed inputs: at low FPS, each frame that takes longer than
        // 1/pollRate seconds would miss a polling cycle in vanilla.
        // Heuristic: missed ≈ max(0, recoveredInputs_delta * (1 - fps/pollRate))
        long recovered = InputBoosterMod.recoveredInputs.get();
        long delta = recovered - inputsRecoveredAtLastTick;
        if (delta > 0 && currentFps > 0) {
            int pollHz = InputBoosterMod.currentPollHz;
            double missRate = Math.max(0.0, 1.0 - (double) currentFps / pollHz);
            estimatedMissedInputs += Math.round(delta * missRate);
        }
        inputsRecoveredAtLastTick = recovered;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public String getSessionStartTime() {
        return TIME_FMT.format(Instant.ofEpochMilli(sessionStartMs));
    }

    public long getSessionUptimeSeconds() {
        return (System.nanoTime() - sessionStartNs) / 1_000_000_000L;
    }

    public String getUptimeFormatted() {
        long secs  = getSessionUptimeSeconds();
        long hrs   = secs / 3600;
        long mins  = (secs % 3600) / 60;
        long s     = secs % 60;
        return String.format("%02d:%02d:%02d", hrs, mins, s);
    }

    public long getTotalRecovered() {
        return InputBoosterMod.recoveredInputs.get();
    }

    public long getEstimatedMissedInputs() {
        return estimatedMissedInputs;
    }

    /** Returns the CPS sparkline array (60 one-second buckets, oldest first). */
    public int[] getCpsHistory() {
        int[] out = new int[CPS_HISTORY_SECONDS];
        for (int i = 0; i < CPS_HISTORY_SECONDS; i++) {
            out[i] = cpsBuckets[(cpsBucketHead + i) % CPS_HISTORY_SECONDS];
        }
        return out;
    }

    public boolean isPollingThreadAlive() {
        return InputBoosterMod.pollingThread != null
            && InputBoosterMod.pollingThread.isAlive();
    }
}
