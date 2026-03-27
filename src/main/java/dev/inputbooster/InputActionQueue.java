package dev.inputbooster;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe lock-free queue bridging the polling thread → game tick.
 * Author: Ahaduzzaman Khan
 */
public class InputActionQueue {

    private static final int MAX_QUEUED = 128;
    private static final ConcurrentLinkedQueue<InputAction> QUEUE = new ConcurrentLinkedQueue<>();

    /** Returns true if the action was queued (false if queue is full). */
    public static boolean queue(InputAction action) {
        if (QUEUE.size() < MAX_QUEUED) {
            QUEUE.offer(action);
            return true;
        }
        return false;
    }

    public static InputAction poll() { return QUEUE.poll(); }
    public static boolean isEmpty()  { return QUEUE.isEmpty(); }
    public static void clear()       { QUEUE.clear(); }
    public static int size()         { return QUEUE.size(); }
}
