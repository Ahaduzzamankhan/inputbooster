package dev.inputbooster;

import dev.inputbooster.feature.LatencyProfiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for {@link LatencyProfiler}.
 *
 * The rolling average used to read the raw ring-buffer array as
 * {@code samples[0..count-1]}. Once the buffer wrapped, that mixed the newest
 * samples with the oldest ones and reported a meaningless average.
 */
class LatencyProfilerTest {

    @BeforeEach
    void reset() {
        LatencyProfiler.reset();
    }

    @Test
    void averageIsZeroWithoutSamples() {
        assertTrue(LatencyProfiler.getAverageMs() == 0.0);
    }

    @Test
    void averageIgnoresSamplesThatFellOutOfTheWindow() {
        // Fill one full window (128) with 1 ms samples, then push another full
        // window of 12 ms samples. Only the 12 ms window is live.
        for (int i = 0; i < 128; i++) {
            LatencyProfiler.recordDrain(System.nanoTime() - 1_000_000L);
        }
        for (int i = 0; i < 128; i++) {
            LatencyProfiler.recordDrain(System.nanoTime() - 12_000_000L);
        }

        double avg = LatencyProfiler.getAverageMs();
        assertTrue(avg >= 10.0 && avg < 30.0,
            "average must reflect the live 12 ms window, was " + avg + " ms");
    }

    @Test
    void averageTracksASmallWindow() {
        for (int i = 0; i < 4; i++) {
            LatencyProfiler.recordDrain(System.nanoTime() - 5_000_000L);
        }
        double avg = LatencyProfiler.getAverageMs();
        assertTrue(avg >= 3.0 && avg < 15.0, "unexpected average " + avg + " ms");
    }

    @Test
    void peakKeepsSessionMaximum() {
        LatencyProfiler.recordDrain(System.nanoTime() - 40_000_000L);
        LatencyProfiler.recordDrain(System.nanoTime() - 2_000_000L);
        assertTrue(LatencyProfiler.getPeakMs() >= 30.0, "peak was " + LatencyProfiler.getPeakMs() + " ms");
    }
}