package dev.inputbooster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bounds tests for the adaptive poll-rate algorithm.
 *
 * The rate has to stay inside the range the polling thread accepts and must
 * move gradually so the pipeline does not oscillate between rates.
 */
class AdaptivePollRateTest {

    @Test
    void staysWithinSupportedBoundsForEveryFps() {
        int[] fpsSamples = {0, 1, 5, 15, 20, 30, 45, 60, 90, 144, 240, 500};
        for (int fps : fpsSamples) {
            int hz = InputBoosterMod.calculateAutoHz(fps);
            assertTrue(hz >= 60 && hz <= 1000, "poll rate " + hz + " Hz out of bounds for fps " + fps);
        }
    }

    @Test
    void rampsDownGraduallyInsteadOfJumping() {
        InputBoosterMod.currentPollHz = 1000;
        int previous = 1000;
        for (int i = 0; i < 50; i++) {
            int hz = InputBoosterMod.calculateAutoHz(10);
            assertTrue(hz <= previous, "adaptive rate must not oscillate upwards while FPS stays low");
            assertTrue(previous - hz <= 50, "downward step must be gradual, was " + (previous - hz));
            assertTrue(hz >= 60, "never below the supported minimum");
            previous = hz;
        }
    }

    @Test
    void rampsUpGradually() {
        InputBoosterMod.currentPollHz = 60;
        int previous = 60;
        for (int i = 0; i < 80; i++) {
            int hz = InputBoosterMod.calculateAutoHz(144);
            assertTrue(hz >= previous, "rate should climb while FPS is healthy");
            assertTrue(hz - previous <= 100, "upward step must be gradual, was " + (hz - previous));
            assertTrue(hz <= 1000);
            previous = hz;
        }
    }
}