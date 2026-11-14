package dev.inputbooster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the Ctrl+digit combo keys.
 *
 * Before the fix the latch for the digit that fired the preset was never
 * written, so holding Ctrl+1 re-applied the poll rate on every tick.
 */
class ComboKeyPresetsTest {

    @Test
    void firesOnlyOnTheRisingEdge() {
        ComboKeyPresets keys = new ComboKeyPresets();

        assertTrue(keys.press(0, true), "first press must fire");
        assertFalse(keys.press(0, true), "held key must not fire again");
        assertFalse(keys.press(0, true), "still held, still no fire");
        assertTrue(keys.press(0, false) == false, "release never fires");
        assertTrue(keys.press(0, true), "pressing again after release fires");
    }

    @Test
    void resetForgetsHeldDigits() {
        ComboKeyPresets keys = new ComboKeyPresets();
        assertTrue(keys.press(2, true));
        assertFalse(keys.press(2, true));

        keys.reset();

        assertTrue(keys.press(2, true), "after reset the digit counts as a fresh press");
    }

    @Test
    void presetsMatchDocumentedRates() {
        ComboKeyPresets keys = new ComboKeyPresets();
        assertEquals(5, keys.size());
        assertEquals(100, ComboKeyPresets.HZ[0]);
        assertEquals(200, ComboKeyPresets.HZ[1]);
        assertEquals(350, ComboKeyPresets.HZ[2]);
        assertEquals(500, ComboKeyPresets.HZ[3]);
        assertEquals(1000, ComboKeyPresets.HZ[4]);
    }

    @Test
    void outOfRangeIndexIsIgnored() {
        ComboKeyPresets keys = new ComboKeyPresets();
        assertFalse(keys.press(-1, true));
        assertFalse(keys.press(99, true));
    }
}