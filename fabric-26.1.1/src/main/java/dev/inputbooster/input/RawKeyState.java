package dev.inputbooster.input;

import com.mojang.blaze3d.platform.Window;
import dev.inputbooster.compat.McVersion;
import dev.inputbooster.InputBoosterMod;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The native key state the high-frequency poller reads.
 *
 * <p><b>Thread ownership.</b> Platform key state is read only on the game
 * thread, by {@link #refresh(KeyBindingSet)}. GLFW's {@code glfwGetKey} is a
 * main-thread function, and SDL3's keyboard state is sampled the same way, so
 * asking either from {@link dev.inputbooster.InputPollingThread} was undefined
 * behaviour: it works often enough to look fine, then crashes or returns stale
 * data under load. The poller therefore never calls into the platform. It reads
 * the immutable snapshot published here.
 *
 * <p>The snapshot is a 512-bit {@code long[]} replaced as a whole by a volatile
 * write, so a reader either sees the previous frame's state or the current one,
 * never a half-updated mixture. No lock is taken on either side.
 *
 * <p>Sampling happens once per rendered frame (from the HUD hook) and once per
 * tick, so the poller still sees state changes at frame rate instead of tick
 * rate while staying on the safe thread.
 *
 * <p>If a native call fails the sampler degrades to "everything released", the
 * failure is reported once with the exception, and the engine falls back to the
 * tick-rate snapshot.
 */
public final class RawKeyState {

    private static final int CODE_WORDS = 8; // 8 * 64 = 512 codes

    private final Window window;

    /** Immutable once published; replaced wholesale. */
    private volatile long[] sample;

    private volatile boolean failed = false;
    private volatile boolean reported = false;
    private final AtomicLong refreshCount = new AtomicLong();
    private final AtomicLong failureCount = new AtomicLong();
    private volatile long lastRefreshNanos;
    private volatile String failureReason;

    private RawKeyState(Window window) {
        this.window = window;
    }

    /** @param window captured on the game thread; may be null (headless/tests). */
    public static RawKeyState of(Window window) {
        return new RawKeyState(window);
    }

    /** True when samples can be read (a window exists and no native failure). */
    public boolean isAvailable() {
        return window != null && !failed && sample != null;
    }

    /** True when the platform refuses to report key state at all. */
    public boolean failed() {
        return failed;
    }

    public String failureReason() {
        return failureReason;
    }

    public long refreshCount() {
        return refreshCount.get();
    }

    public long failureCount() {
        return failureCount.get();
    }

    public long lastRefreshNanos() {
        return lastRefreshNanos;
    }

    /**
     * Samples the bound keys. <b>Game thread only.</b>
     *
     * @param bindings the codes to sample; typically the twelve action slots
     */
    public void refresh(KeyBindingSet bindings) {
        if (window == null || failed || bindings == null) return;
        long[] next = new long[CODE_WORDS];
        try {
            for (int slot = 0; slot < KeyBindingSet.SLOTS; slot++) {
                int code = bindings.code(slot);
                if (code < 0 || code >= CODE_WORDS * 64) continue;
                if (McVersion.isKeyDown(window, code)) {
                    next[code >>> 6] |= 1L << (code & 63);
                }
            }
        } catch (Throwable t) {
            // Not swallowed: the reason is kept and reported once, and the
            // engine drops to the tick-rate snapshot for the rest of the
            // session instead of retrying a failing native call every frame.
            failed = true;
            failureReason = t.toString();
            failureCount.incrementAndGet();
            if (!reported) {
                reported = true;
                InputBoosterMod.LOGGER.warn(
                    "[Input] Raw key state unavailable ({}); falling back to tick-rate input sampling.",
                    t.toString());
            }
            return;
        }
        sample = next;
        lastRefreshNanos = System.nanoTime();
        refreshCount.incrementAndGet();
    }

    /**
     * @return true when the key was down at the last refresh
     *
     * <p>Safe from any thread: it only reads the published snapshot.
     */
    public boolean isDown(int keyCode) {
        if (keyCode < 0 || keyCode >= CODE_WORDS * 64) return false;
        long[] current = sample;
        if (current == null || failed) return false;
        return (current[keyCode >>> 6] & (1L << (keyCode & 63))) != 0L;
    }

    public Window window() {
        return window;
    }
}
