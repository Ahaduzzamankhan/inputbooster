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
    // FIX (clear() corrupted the queue bound): clear() used to do
    // QUEUE.clear() + COUNT.set(0) while the polling thread could be between
    // its counter increment and its offer(). The polled event was then lost but
    // the count still said "one less", so the queue could grow past MAX_QUEUED
    // and defeat the bounded-backlog guarantee the architecture depends on.
    // Guarding the counter mutations with one lock keeps count and contents in
    // agreement; the lock is uncontended apart from microseconds per event.
    private static final Object LOCK = new Object();

    /** Returns true if the action was queued (false if queue is full). */
    public static boolean queue(InputAction action) {
        synchronized (LOCK) {
            if (COUNT.get() >= MAX_QUEUED) return false;
            QUEUE.offer(InputAction.Stamped.of(action));
            COUNT.incrementAndGet();
            return true;
        }
    }

    public static InputAction.Stamped poll() {
        synchronized (LOCK) {
            InputAction.Stamped stamped = QUEUE.poll();
            if (stamped != null) COUNT.decrementAndGet();
            return stamped;
        }
    }

    public static boolean isEmpty() { return size() == 0; }

    public static void clear() {
        synchronized (LOCK) {
            QUEUE.clear();
            COUNT.set(0);
        }
    }

    /** Maximum number of queued events before new ones are rejected. */
    public static int capacity() { return MAX_QUEUED; }

    public static int size() { return COUNT.get(); }
}
