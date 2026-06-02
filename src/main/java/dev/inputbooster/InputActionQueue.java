package dev.inputbooster;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe lock-free queue bridging the polling thread → game tick.
 * Now queues InputAction.Stamped records for latency profiling.
 *
 * Version: 3.0.0
 * Author: Ahaduzzaman Khan
 */
public class InputActionQueue {

    private static final int MAX_QUEUED = 128;
    private static final ConcurrentLinkedQueue<InputAction.Stamped> QUEUE = new ConcurrentLinkedQueue<>();
    // Explicit counter: ConcurrentLinkedQueue.size() is O(n) — avoid on hot path
    private static final AtomicInteger COUNT = new AtomicInteger(0);

    /** Returns true if the action was queued (false if queue is full). */
    public static boolean queue(InputAction action) {
        int newCount = COUNT.incrementAndGet();
        if (newCount > MAX_QUEUED) {
            COUNT.decrementAndGet();
            return false;
        }
        QUEUE.offer(InputAction.Stamped.of(action));
        return true;
    }

    public static InputAction.Stamped poll() {
        InputAction.Stamped stamped = QUEUE.poll();
        if (stamped != null) COUNT.decrementAndGet();
        return stamped;
    }

    public static boolean isEmpty() { return COUNT.get() == 0; }

    public static void clear() {
        QUEUE.clear();
        COUNT.set(0);
    }

    public static int size() { return COUNT.get(); }
}
