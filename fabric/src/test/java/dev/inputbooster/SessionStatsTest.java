package dev.inputbooster;

import dev.inputbooster.feature.SessionStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for {@link SessionStats}.
 *
 * The CPS sparkline used to close at most one bucket per tick and to use the
 * wall clock, so any gap longer than a second (lag spike, alt-tab) left the
 * whole graph stale. Every elapsed second must now be closed out.
 */
class SessionStatsTest {

    @BeforeEach
    void resetCounters() {
        InputBoosterMod.totalHits.set(0);
        InputBoosterMod.recoveredInputs.set(0);
        InputBoosterMod.currentPollHz = 200;
    }

    @Test
    void closesEveryElapsedSecondAfterAStall() throws InterruptedException {
        SessionStats stats = new SessionStats();

        stats.tick(60, 0);
        InputBoosterMod.totalHits.set(3);
        for (int i = 0; i < 3; i++) stats.tick(60, 3);

        // A three second stall: three one-second buckets must be closed.
        Thread.sleep(3_100L);
        stats.tick(60, 0);

        int[] history = stats.getCpsHistory();
        assertEquals(60, history.length);
        assertEquals(3, history[history.length - 3], "the three hits belong to the first closed bucket");
        assertEquals(0, history[history.length - 2], "the second closed second is empty");
        assertEquals(0, history[history.length - 1], "the third closed second is empty");
    }

    @Test
    void countsHitsInTheCurrentBucket() {
        SessionStats stats = new SessionStats();

        stats.tick(144, 0);
        InputBoosterMod.totalHits.addAndGet(7);
        stats.tick(144, 7);

        int[] history = stats.getCpsHistory();
        // The current (not yet closed) second is not part of the closed history,
        // so nothing has been recorded yet.
        for (int value : history) {
            assertEquals(0, value);
        }
        assertTrue(stats.getSessionUptimeSeconds() >= 0);
        assertTrue(stats.getUptimeFormatted().matches("\\d{2}:\\d{2}:\\d{2}"));
    }
}