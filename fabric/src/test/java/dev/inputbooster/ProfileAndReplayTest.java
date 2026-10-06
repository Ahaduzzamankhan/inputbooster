package dev.inputbooster;

import dev.inputbooster.feature.PerServerProfileManager;
import dev.inputbooster.feature.ProfileManager;
import dev.inputbooster.feature.ReplayRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the profile system, server profile normalisation, replay
 * overflow handling and per-module safe mode.
 */
class ProfileAndReplayTest {

    private static Path stateDir() {
        return Path.of(System.getProperty("inputbooster.configDir", "config"));
    }

    private static void clearStateDir() throws IOException {
        Path dir = stateDir();
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        }
        Files.createDirectories(dir);
    }

    @BeforeEach
    void reset() throws IOException {
        clearStateDir();
        InputBoosterConfig.setPollRateHz(200);
        InputBoosterConfig.setMaxCps(20);
        InputActionQueue.clear();
    }

    // ── Profiles ─────────────────────────────────────────────────────────────

    @Test
    void profileRoundTripsWithAwkwardNames() {
        ProfileManager pm = new ProfileManager();
        assertTrue(pm.saveProfile("PvP \"fast\""), "first profile must save");
        assertTrue(pm.saveProfile("brace}s,comma,and\\backslash"), "second profile must save");

        pm.load();

        assertEquals(2, pm.getCount());
        assertTrue(pm.loadProfile("pvp \"FAST\"", null), "lookup must be case-insensitive");
        assertEquals(0, pm.getActiveIndex());
    }

    @Test
    void deletingTheActiveProfileClearsTheActiveIndex() {
        ProfileManager pm = new ProfileManager();
        pm.saveProfile("A");
        pm.saveProfile("B");
        assertEquals(1, pm.getActiveIndex());

        assertTrue(pm.deleteProfile(1));
        assertEquals(-1, pm.getActiveIndex(), "deleting the active profile leaves nothing active");
        assertEquals(1, pm.getCount());
    }

    @Test
    void deletingBeforeTheActiveProfileKeepsTheSameProfileActive() {
        ProfileManager pm = new ProfileManager();
        pm.saveProfile("A");
        pm.saveProfile("B");
        pm.saveProfile("C");
        assertEquals(2, pm.getActiveIndex());

        assertTrue(pm.deleteProfile(0));
        assertEquals(1, pm.getActiveIndex(), "index must follow the profile that is still active");
        assertEquals("C", pm.getProfiles().get(pm.getActiveIndex()).name());
    }

    @Test
    void deletingAfterTheActiveProfileLeavesTheIndexAlone() {
        ProfileManager pm = new ProfileManager();
        pm.saveProfile("A");
        pm.saveProfile("B");
        pm.saveProfile("C");
        pm.loadProfile("A", null);
        assertEquals(0, pm.getActiveIndex());

        assertTrue(pm.deleteProfile(2));
        assertEquals(0, pm.getActiveIndex());
    }

    @Test
    void capacityIsEnforced() {
        ProfileManager pm = new ProfileManager();
        for (int i = 0; i < ProfileManager.MAX_PROFILES; i++) {
            assertTrue(pm.saveProfile("P" + i));
        }
        assertFalse(pm.saveProfile("one-too-many"), "max profiles must be enforced");
        assertTrue(pm.saveProfile("P0"), "existing names may still be overwritten");
    }

    @Test
    void corruptedProfileFileDoesNotCrashAndIsQuarantined() throws IOException {
        Path file = stateDir().resolve("inputbooster_profiles.json");
        Files.writeString(file, "{ this is not json at all", StandardCharsets.UTF_8);

        ProfileManager pm = new ProfileManager();
        pm.load();

        assertEquals(0, pm.getCount(), "corrupt file must not produce profiles");
        assertTrue(Files.exists(file.resolveSibling("inputbooster_profiles.json.corrupt")),
            "corrupt file is moved aside");
        // The manager must still be usable afterwards.
        assertTrue(pm.saveProfile("AfterRecovery"));
    }

    @Test
    void profileValuesAreClampedOnApply() throws IOException {
        Path file = stateDir().resolve("inputbooster_profiles.json");
        Files.createDirectories(stateDir());
        Files.writeString(file, "[{\"name\":\"evil\",\"pollRateHz\":999999,\"maxCps\":-4}]", StandardCharsets.UTF_8);

        ProfileManager pm = new ProfileManager();
        pm.load();
        assertEquals(1, pm.getCount());
        assertTrue(pm.loadProfile("evil", null));

        assertEquals(1000, InputBoosterConfig.getPollRateHz(), "poll rate clamped");
        assertEquals(1, InputBoosterConfig.getMaxCps(), "cps clamped");
    }

    // ── Server profiles ──────────────────────────────────────────────────────

    @Test
    void serverIdentifiersAreNormalised() {
        assertEquals("example.com", PerServerProfileManager.normalize("Example.Com"));
        assertEquals("example.com", PerServerProfileManager.normalize("example.com:25565"));
        assertEquals("example.com", PerServerProfileManager.normalize("  EXAMPLE.COM:25565  "));
        assertEquals("example.com", PerServerProfileManager.normalize("tcp://example.com:25565/"));
        assertEquals("unknown", PerServerProfileManager.normalize(null));
        assertEquals("unknown", PerServerProfileManager.normalize("   "));
    }

    // ── Replay ───────────────────────────────────────────────────────────────

    @Test
    void playbackOfAnEmptyRecordingDoesNothing() {
        ReplayRecorder replay = new ReplayRecorder();
        replay.startPlayback();
        replay.tick();
        assertFalse(replay.isPlaying());
    }

    @Test
    void playbackStopsWhenTheQueueStaysFullInsteadOfSkippingEvents() {
        ReplayRecorder replay = new ReplayRecorder();
        // Fill the queue completely; playback must not advance past its events.
        for (int i = 0; i < InputActionQueue.capacity(); i++) {
            assertTrue(InputActionQueue.queue(InputAction.JUMP_PRESSED));
        }

        replay.startRecording();
        replay.onQueued(InputAction.ATTACK_PRESSED);
        replay.stopRecording();
        assertEquals(1, replay.getRecordedCount(), "recorded events are queued input events");

        replay.startPlayback();
        for (int i = 0; i < 200; i++) {
            replay.tick();
        }

        assertEquals(InputActionQueue.capacity(), InputActionQueue.size(),
            "a full queue must not silently swallow replay events");
        assertEquals(1, replay.getRecordedCount(), "the recorded event is still pending");
        assertTrue(InputActionQueue.size() <= InputActionQueue.capacity(),
            "queue never exceeds its bound");
    }

    @Test
    void recordingClearsPreviousEventsAndStopsOnStop() {
        ReplayRecorder replay = new ReplayRecorder();
        replay.startRecording();
        assertTrue(replay.isRecording());
        replay.onQueued(InputAction.ATTACK_PRESSED);
        replay.stopRecording();
        assertFalse(replay.isRecording());
        assertEquals(1, replay.getRecordedCount());

        replay.startRecording();
        assertEquals(0, replay.getRecordedCount(), "a new recording starts empty");
    }

    @Test
    void recordingIsBounded() {
        ReplayRecorder replay = new ReplayRecorder();
        replay.startRecording();
        for (int i = 0; i < 5000; i++) replay.onQueued(InputAction.ATTACK_PRESSED);
        assertTrue(replay.getRecordedCount() <= 4000,
            "replay buffer must stay bounded, was " + replay.getRecordedCount());
        assertTrue(replay.getTruncatedEvents() > 0,
            "overflow must be reported instead of silently ignored");
        assertTrue(replay.statusLine().contains("dropped"), "status line must surface truncation");
    }

    @Test
    void notRecordingNothingIsStored() {
        ReplayRecorder replay = new ReplayRecorder();
        replay.onQueued(InputAction.ATTACK_PRESSED);
        assertEquals(0, replay.getRecordedCount());
        assertNotNull(replay.statusLine());
    }
}