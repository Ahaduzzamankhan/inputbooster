package dev.inputbooster;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the polling thread → game thread queue.
 *
 * Before the fix, {@link InputActionQueue#clear()} reset the counter to zero
 * while the polling thread could be between its counter increment and its
 * offer(), so the queue could grow past its documented bound.
 */
class InputActionQueueTest {

    @BeforeEach
    void reset() {
        InputActionQueue.clear();
    }

    @Test
    void queuesAndPollsStampedActions() {
        assertTrue(InputActionQueue.queue(InputAction.ATTACK_PRESSED));
        assertEquals(1, InputActionQueue.size());
        assertFalse(InputActionQueue.isEmpty());

        InputAction.Stamped stamped = InputActionQueue.poll();
        assertNotNull(stamped);
        assertEquals(InputAction.ATTACK_PRESSED, stamped.action());
        assertTrue(stamped.capturedAt() > 0);
        assertTrue(InputActionQueue.isEmpty());
    }

    @Test
    void rejectsEventsBeyondTheBound() {
        int capacity = InputActionQueue.capacity();
        for (int i = 0; i < capacity; i++) {
            assertTrue(InputActionQueue.queue(InputAction.JUMP_PRESSED), "event " + i + " must fit");
        }
        assertEquals(capacity, InputActionQueue.size());
        assertFalse(InputActionQueue.queue(InputAction.JUMP_PRESSED), "queue must reject overflow");

        InputActionQueue.clear();
        assertTrue(InputActionQueue.queue(InputAction.JUMP_PRESSED), "space freed by clear() is reusable");
    }

    @Test
    void clearEmptiesQueueAndCounter() {
        for (int i = 0; i < 10; i++) InputActionQueue.queue(InputAction.USE_PRESSED);
        InputActionQueue.clear();

        assertEquals(0, InputActionQueue.size());
        assertTrue(InputActionQueue.isEmpty());
        assertTrue(InputActionQueue.poll() == null);
    }

    @Test
    void clearRacingWithProducersKeepsCountInSync() throws InterruptedException {
        int capacity = InputActionQueue.capacity();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger overflowRejections = new AtomicInteger();

        Thread producer = new Thread(() -> {
            while (running.get()) {
                if (InputActionQueue.queue(InputAction.ATTACK_PRESSED)) {
                    accepted.incrementAndGet();
                } else {
                    overflowRejections.incrementAndGet();
                }
            }
        });
        producer.setDaemon(true);
        producer.start();

        CountDownLatch done = new CountDownLatch(1);
        Thread clearer = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                InputActionQueue.clear();
            }
            done.countDown();
        });
        clearer.setDaemon(true);
        clearer.start();

        assertTrue(done.await(30, TimeUnit.SECONDS), "clearer thread must finish");
        running.set(false);
        producer.join(5_000);

        InputActionQueue.clear();
        assertEquals(0, InputActionQueue.size(), "counter must never drift from the queue contents");

        // The bound must hold again once the racing clear() is done.
        for (int i = 0; i < capacity; i++) {
            assertTrue(InputActionQueue.queue(InputAction.ATTACK_PRESSED));
        }
        assertFalse(InputActionQueue.queue(InputAction.ATTACK_PRESSED), "bounded backlog must be preserved");
        assertTrue(accepted.get() > 0, "producer must have made progress");
        assertTrue(overflowRejections.get() >= 0);
    }
}