package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class EventLog {
    private static final int MAX_EVENTS = 80;
    private final ConcurrentLinkedDeque<String> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger logSize = new AtomicInteger(0);

    public void add(String message) {
        if (!InputBoosterConfig.isEventLogEnabled() || message == null || message.isBlank()) return;
        events.addLast(LocalTime.now().withNano(0) + " " + message);
        int currentSize = logSize.incrementAndGet();
        while (currentSize > MAX_EVENTS) {
            if (events.pollFirst() != null) {
                currentSize = logSize.decrementAndGet();
            } else {
                break;
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
}
