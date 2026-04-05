package dev.inputbooster;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InputPollingThread
 *
 * Runs a dedicated background thread polling keyboard/mouse state
 * at rates far exceeding the game's render FPS.
 *
 * At 30 FPS → Minecraft checks keys every ~33ms
 * At 500Hz  → This thread checks every ~2ms
 *
 * Detected edge events (key press / release) are queued to
 * InputActionQueue for consumption on the main game tick.
 *
 * Author: Ahaduzzaman Khan
 */
public class InputPollingThread extends Thread {

    private final AtomicBoolean running    = new AtomicBoolean(true);
    private final AtomicInteger pollRateHz = new AtomicInteger(200);

    // Previous key states for edge detection
    private boolean prevAttack, prevUse, prevSprint, prevSneak;
    private boolean prevJump, prevForward, prevBack, prevLeft, prevRight;
    private boolean prevDrop, prevSwap, prevPickBlock;

    public InputPollingThread(int initialHz) {
        setName("InputBooster-PollerThread");
        setDaemon(true);
        setPriority(Thread.MAX_PRIORITY - 1); // near-max OS scheduling priority
        this.pollRateHz.set(clampHz(initialHz));
    }

    public void setPollRateHz(int hz) {
        this.pollRateHz.set(clampHz(hz));
    }

    private int clampHz(int hz) {
        return Math.max(60, Math.min(1000, hz));
    }

    @Override
    public void run() {
        InputBoosterMod.LOGGER.info("[InputBooster] Polling thread started.");

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            long loopStart = System.nanoTime();

            try {
                poll();
            } catch (Exception e) {
                // Log errors but never crash the thread
                InputBoosterMod.LOGGER.warn("[InputBooster] Polling error: {}", e.getMessage());
            }

            // Sleep precisely to hit target Hz
            int hz        = pollRateHz.get();
            long targetNs = 1_000_000_000L / hz;
            long elapsed  = System.nanoTime() - loopStart;
            long sleepNs  = targetNs - elapsed;

            if (sleepNs > 0) {
                try {
                    // Use millisecond sleep for better battery efficiency and thread responsiveness
                    long sleepMs = sleepNs / 1_000_000L;
                    int nanos = (int)(sleepNs % 1_000_000L);
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs, nanos);
                    } else if (nanos > 0) {
                        Thread.sleep(0, nanos);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        InputBoosterMod.LOGGER.info("[InputBooster] Polling thread stopped.");
    }

    private void poll() {
        // Use main-thread-updated volatile flags — never read MC objects from this thread
        if (!InputBoosterMod.gameReady || InputBoosterMod.gamePaused) return;

        try {
            // Read key snapshot that was safely captured on the main thread
            KeySnapshot snap = InputBoosterMod.keySnapshot;
            if (snap == null) return;

            // ── Attack (LMB) ──────────────────────────────────────────────────────
            boolean attack = snap.attack;
            if (attack  && !prevAttack) queue(InputAction.ATTACK_PRESSED);
            if (!attack && prevAttack)  queue(InputAction.ATTACK_RELEASED);
            prevAttack = attack;

            // ── Use / Place (RMB) ─────────────────────────────────────────────────
            boolean use = snap.use;
            if (use  && !prevUse) queue(InputAction.USE_PRESSED);
            if (!use && prevUse)  queue(InputAction.USE_RELEASED);
            prevUse = use;

            // ── Sprint ────────────────────────────────────────────────────────────
            boolean sprint = snap.sprint;
            if (sprint && !prevSprint) queue(InputAction.SPRINT_PRESSED);
            if (!sprint && prevSprint) queue(InputAction.SPRINT_RELEASED);
            prevSprint = sprint;

            // ── Sneak ─────────────────────────────────────────────────────────────
            boolean sneak = snap.sneak;
            if (sneak  && !prevSneak) queue(InputAction.SNEAK_PRESSED);
            if (!sneak && prevSneak)  queue(InputAction.SNEAK_RELEASED);
            prevSneak = sneak;

            // ── Jump ──────────────────────────────────────────────────────────────
            boolean jump = snap.jump;
            if (jump && !prevJump) queue(InputAction.JUMP_PRESSED);
            prevJump = jump;

            // ── Movement ──────────────────────────────────────────────────────────
            boolean forward = snap.forward;
            boolean back    = snap.back;
            boolean left    = snap.left;
            boolean right   = snap.right;

            if (forward  && !prevForward) queue(InputAction.FORWARD_PRESSED);
            if (!forward && prevForward)  queue(InputAction.FORWARD_RELEASED);
            if (back     && !prevBack)    queue(InputAction.BACK_PRESSED);
            if (!back    && prevBack)     queue(InputAction.BACK_RELEASED);
            if (left     && !prevLeft)    queue(InputAction.LEFT_PRESSED);
            if (right    && !prevRight)   queue(InputAction.RIGHT_PRESSED);

            prevForward = forward;
            prevBack    = back;
            prevLeft    = left;
            prevRight   = right;

            // ── Drop / Swap / Pick-block ───────────────────────────────────────────
            boolean drop      = snap.drop;
            boolean swap      = snap.swap;
            boolean pickBlock = snap.pickBlock;

            if (drop      && !prevDrop)      queue(InputAction.DROP_PRESSED);
            if (swap      && !prevSwap)      queue(InputAction.SWAP_PRESSED);
            if (pickBlock && !prevPickBlock) queue(InputAction.PICK_BLOCK_PRESSED);

            prevDrop      = drop;
            prevSwap      = swap;
            prevPickBlock = pickBlock;
        } catch (Exception e) {
            InputBoosterMod.LOGGER.debug("[InputBooster] Poll cycle exception: {}", e.getMessage());
        }
    }

    private void queue(InputAction action) {
        if (InputActionQueue.queue(action)) {
            InputBoosterMod.recoveredInputs.incrementAndGet();
        }
    }

    public void stopPolling() {
        running.set(false);
        this.interrupt();
    }
}
