package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded in-memory activity log.
 *
 * The deque plus the size counter are updated under one lock: the previous
 * counter-based trim could be raced by a second producer (the polling thread
 * logs "Attack queued"), which could either overshoot the limit or, worse, lose
 * count and let the log grow without bound.
 */
public class EventLog {
    private static final int MAX_EVENTS = 80;

    private final ConcurrentLinkedDeque<String> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger logSize = new AtomicInteger(0);
    private final Object trimLock = new Object();

    public void add(String message) {
        if (!InputBoosterConfig.isEventLogEnabled() || message == null || message.isBlank()) return;
        events.addLast(LocalTime.now().withNano(0) + " " + message);
        int size = logSize.incrementAndGet();
        if (size <= MAX_EVENTS) return;
        synchronized (trimLock) {
            while (logSize.get() > MAX_EVENTS) {
                if (events.pollFirst() == null) break;
                logSize.decrementAndGet();
            }
        }
    }

    public List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public String latest() {
        String latest = events.peekLast();
        return latest == null ? "Event: none" : latest;
    }

    public int size() {
        return logSize.get();
    }

    public void clear() {
        synchronized (trimLock) {
            events.clear();
            logSize.set(0);
        }
    }
}