package dev.inputbooster;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe lock-free queue bridging the polling thread → game tick.
 * Author: Ahaduzzaman Khan
 */
public class InputActionQueue {

    private static final int MAX_QUEUED = 128;
    private static final ConcurrentLinkedQueue<InputAction> QUEUE = new ConcurrentLinkedQueue<>();
    // Explicit counter: ConcurrentLinkedQueue.size() is O(n) — avoid on hot path
    private static final AtomicInteger COUNT = new AtomicInteger(0);

    /** Returns true if the action was queued (false if queue is full). */
    public static boolean queue(InputAction action) {
        // Use compareAndSet loop to atomically check-and-increment
        int current;
        do {
            current = COUNT.get();
            if (current >= MAX_QUEUED) return false;
        } while (!COUNT.compareAndSet(current, current + 1));
        QUEUE.offer(action);
        return true;
    }

    public static InputAction poll() {
        InputAction action = QUEUE.poll();
        if (action != null) COUNT.decrementAndGet();
        return action;
    }

    public static boolean isEmpty() { return COUNT.get() == 0; }

    public static void clear() {
        QUEUE.clear();
        COUNT.set(0);
    }

    public static int size() { return COUNT.get(); }
}
