package dev.inputbooster.perf;

import dev.inputbooster.InputBoosterConfig;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Disk module.
 *
 * <p>The one thing a performance mod genuinely does about I/O is get out of the
 * way. A settings change used to be written synchronously, so dragging a slider
 * wrote the whole configuration once per control change, on the render thread.
 *
 * <p>Now a change only marks the file dirty. A single daemon thread — created
 * lazily, and never more than one — waits out the debounce window and writes
 * once for however many changes arrived during it. Dragging a slider produces
 * one write, on another thread. The file is still written atomically through a
 * temporary file and a move.
 */
public final class DiskOptimizer implements Optimizer {

    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong writes = new AtomicLong();
    private final AtomicLong requests = new AtomicLong();

    private volatile int debounceMs;
    private volatile Thread worker;

    public DiskOptimizer() {
        this.debounceMs = InputBoosterConfig.getWriteDebounceMs();
    }

    @Override
    public String name() {
        return "disk";
    }

    @Override
    public boolean enabled() {
        return InputBoosterConfig.isDiskOptimizationEnabled();
    }

    @Override
    public void start() {
        debounceMs = InputBoosterConfig.getWriteDebounceMs();
    }

    @Override
    public void stop() {
        flushBlocking(250L);
    }

    /** Marks the configuration dirty; the worker coalesces and writes it later. */
    public void requestFlush() {
        requests.incrementAndGet();
        if (!enabled()) {
            InputBoosterConfig.save();
            writes.incrementAndGet();
            return;
        }
        dirty.set(true);
        startWorker();
    }

    private void startWorker() {
        if (worker != null) return;
        if (!running.compareAndSet(false, true)) return;
        Thread t = new Thread(this::drain, "InputBooster-Disk");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        worker = t;
        t.start();
    }

    /** One loop for the life of the worker: sleep, write, repeat. Not a poll storm. */
    private void drain() {
        try {
            while (running.get()) {
                if (!dirty.compareAndSet(true, false)) {
                    Thread.sleep(250L);
                    continue;
                }
                // A change arriving during the window is absorbed by the next
                // pass, which is what collapses a slider drag into one write.
                Thread.sleep(debounceMs);
                dirty.set(false);
                InputBoosterConfig.save();
                writes.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
            worker = null;
        }
    }

    /** Writes any pending change and waits briefly for the worker to go idle. */
    public void flushBlocking(long timeoutMs) {
        if (dirty.compareAndSet(true, false)) {
            InputBoosterConfig.save();
            writes.incrementAndGet();
        }
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline && running.get()) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (dirty.get() && !running.get()) {
            InputBoosterConfig.save();
            writes.incrementAndGet();
            dirty.set(false);
        }
    }

    /** File writes performed. A burst of changes must not grow this. */
    public long writeCount() {
        return writes.get();
    }

    /** Flush requests received, before coalescing. */
    public long requestCount() {
        return requests.get();
    }

    /** True while a flush is still owed. */
    public boolean isDirty() {
        return dirty.get();
    }
}