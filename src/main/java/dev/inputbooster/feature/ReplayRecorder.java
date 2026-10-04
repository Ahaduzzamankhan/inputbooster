package dev.inputbooster.feature;

import dev.inputbooster.InputAction;
import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records the input events the mod queued and replays them later.
 *
 * Recording captures events that were <em>successfully queued</em> by the
 * polling thread (not raw hardware presses and not events the drainer later
 * rejected), so a replay reproduces exactly what the mod itself would have
 * executed.
 *
 * Playback only advances when the event actually made it into the queue. A full
 * queue pauses playback instead of silently skipping events, and the number of
 * dropped events is tracked and reported.
 */
public class ReplayRecorder {
    private static final int MAX_EVENTS = 400;
    /** Consecutive full-queue ticks before playback gives up. */
    private static final int MAX_STALLED_TICKS = 20; // ~1 second

    private final List<Frame> frames = new ArrayList<>();
    private volatile boolean recording;
    private volatile boolean playing;
    private volatile long recordStart;
    private volatile long playStart;
    private volatile int playIndex;
    private final AtomicLong droppedEvents = new AtomicLong(0);

    public void startRecording() {
        if (!InputBoosterConfig.isReplayEnabled()) return;
        synchronized (frames) {
            frames.clear();
        }
        droppedEvents.set(0);
        recording = true;
        playing = false;
        recordStart = System.nanoTime();
    }

    public void stopRecording() {
        recording = false;
    }

    public boolean toggleRecording() {
        if (recording) {
            stopRecording();
            return false;
        }
        startRecording();
        return true;
    }

    public void startPlayback() {
        synchronized (frames) {
            if (!InputBoosterConfig.isReplayEnabled() || frames.isEmpty()) {
                if (frames.isEmpty() && InputBoosterMod.LOGGER != null) {
                    InputBoosterMod.LOGGER.info("[Replay] Nothing recorded yet.");
                }
                return;
            }
            playing = true;
            recording = false;
            playIndex = 0;
            playStart = System.nanoTime();
            stalledTicks = 0;
        }
    }

    public void onQueued(InputAction action) {
        if (!recording || playing || action == null) return;
        synchronized (frames) {
            if (frames.size() >= MAX_EVENTS) return;
            frames.add(new Frame(action, System.nanoTime() - recordStart));
        }
    }

    private int stalledTicks;

    public void tick() {
        if (!playing) return;
        long elapsed = System.nanoTime() - playStart;
        synchronized (frames) {
            while (playIndex < frames.size() && frames.get(playIndex).offsetNanos <= elapsed) {
                if (!InputActionQueue.queue(frames.get(playIndex).action)) {
                    // The queue is full. Do NOT advance the index: the event is
                    // retried next tick instead of being silently skipped.
                    if (++stalledTicks >= MAX_STALLED_TICKS) {
                        long dropped = droppedEvents.incrementAndGet();
                        playIndex++;
                        stalledTicks = 0;
                        if (InputBoosterMod.LOGGER != null) {
                            InputBoosterMod.LOGGER.warn(
                                "[Replay] Input queue stayed full; dropped {} event(s) from playback.",
                                dropped);
                        }
                    }
                    return;
                }
                playIndex++;
                stalledTicks = 0;
            }
            if (playIndex >= frames.size()) {
                playing = false;
                if (InputBoosterMod.LOGGER != null && droppedEvents.get() > 0) {
                    InputBoosterMod.LOGGER.warn("[Replay] Playback finished with {} dropped event(s).",
                        droppedEvents.get());
                }
            }
        }
    }

    public void stopPlayback() {
        playing = false;
        playIndex = 0;
        stalledTicks = 0;
    }

    public long getDroppedEvents() {
        return droppedEvents.get();
    }

    public boolean isRecording() {
        return recording;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getRecordedCount() {
        synchronized (frames) {
            return frames.size();
        }
    }

    public String statusLine() {
        int size = getRecordedCount();
        return "Replay: " + (recording ? "REC " : playing ? "PLAY " : "IDLE ") + size;
    }

    private record Frame(InputAction action, long offsetNanos) {}
}