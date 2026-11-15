package dev.inputbooster.engine;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters and honest timing for the input engine.
 *
 * <p>Everything here is a measurement of work the mod actually did:
 * <ul>
 *   <li>events seen per source,</li>
 *   <li>actions submitted, rejected by a full queue, suppressed as duplicates,</li>
 *   <li>time spent inside the dispatch call, averaged with a decaying mean.</li>
 * </ul>
 *
 * <p>The overhead figure is the cost of submitting an already-decided action to
 * the queue. It is not a frame-time saving, it is not a comparison against
 * vanilla, and the overlay labels it that way. Nothing is estimated.
 */
public final class DiagnosticsManager {

    private final AtomicLong keyboardEvents = new AtomicLong();
    private final AtomicLong mouseEvents = new AtomicLong();
    private final AtomicLong scrollEvents = new AtomicLong();
    private final AtomicLong actionsSubmitted = new AtomicLong();
    private final AtomicLong actionsDropped = new AtomicLong();
    private final AtomicLong duplicatesSuppressed = new AtomicLong();
    private final AtomicLong repeatsIgnored = new AtomicLong();
    private final AtomicLong dispatchSamples = new AtomicLong();

    /** Volatile so the render thread can read the latest value without a lock. */
    private volatile double dispatchNanosMean;

    public void keyboardEvent() { keyboardEvents.incrementAndGet(); }
    public void mouseEvent() { mouseEvents.incrementAndGet(); }
    public void scrollEvent() { scrollEvents.incrementAndGet(); }
    public void duplicateSuppressed() { duplicatesSuppressed.incrementAndGet(); }
    public void repeatIgnored() { repeatsIgnored.incrementAndGet(); }
    public void actionDropped() { actionsDropped.incrementAndGet(); }

    public void actionSubmitted() { actionsSubmitted.incrementAndGet(); }

    /** Records how long the dispatch path took, in nanoseconds. */
    public void recordDispatchNanos(long nanos) {
        if (nanos <= 0) return;
        double mean = dispatchNanosMean;
        dispatchNanosMean = mean <= 0.0 ? nanos : mean * 0.98 + nanos * 0.02;
        dispatchSamples.incrementAndGet();
    }

    public long keyboardEvents() { return keyboardEvents.get(); }
    public long mouseEvents() { return mouseEvents.get(); }
    public long scrollEvents() { return scrollEvents.get(); }
    public long actionsSubmitted() { return actionsSubmitted.get(); }
    public long actionsDropped() { return actionsDropped.get(); }
    public long duplicatesSuppressed() { return duplicatesSuppressed.get(); }
    public long repeatsIgnored() { return repeatsIgnored.get(); }
    public long dispatchSamples() { return dispatchSamples.get(); }

    /** Mean dispatch cost in microseconds; 0 until at least one action ran. */
    public double dispatchMicros() {
        return dispatchNanosMean / 1_000.0;
    }

    public void reset() {
        keyboardEvents.set(0);
        mouseEvents.set(0);
        scrollEvents.set(0);
        actionsSubmitted.set(0);
        actionsDropped.set(0);
        duplicatesSuppressed.set(0);
        repeatsIgnored.set(0);
        dispatchSamples.set(0);
        dispatchNanosMean = 0.0;
    }
}
