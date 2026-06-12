package dev.inputbooster.feature;

import dev.inputbooster.InputAction;
import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;

import java.util.ArrayList;
import java.util.List;

public class ReplayRecorder {
    private static final int MAX_EVENTS = 400;
    private final List<Frame> frames = new ArrayList<>();
    private volatile boolean recording;
    private volatile boolean playing;
    private volatile long recordStart;
    private volatile long playStart;
    private volatile int playIndex;

    public void startRecording() {
        if (!InputBoosterConfig.isReplayEnabled()) return;
        synchronized (frames) {
            frames.clear();
        }
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
            if (!InputBoosterConfig.isReplayEnabled() || frames.isEmpty()) return;
            playing = true;
            recording = false;
            playIndex = 0;
            playStart = System.nanoTime();
        }
    }

    public void onQueued(InputAction action) {
        if (!recording || playing || action == null) return;
        synchronized (frames) {
            if (frames.size() >= MAX_EVENTS) return;
            frames.add(new Frame(action, System.nanoTime() - recordStart));
        }
    }

    public void tick() {
        if (!playing) return;
        long elapsed = System.nanoTime() - playStart;
        synchronized (frames) {
            while (playIndex < frames.size() && frames.get(playIndex).offsetNanos <= elapsed) {
                InputActionQueue.queue(frames.get(playIndex).action);
                playIndex++;
            }
            if (playIndex >= frames.size()) playing = false;
        }
    }

    public String statusLine() {
        int size;
        synchronized (frames) {
            size = frames.size();
        }
        return "Replay: " + (recording ? "REC " : playing ? "PLAY " : "IDLE ") + size;
    }

    private record Frame(InputAction action, long offsetNanos) {}
}
