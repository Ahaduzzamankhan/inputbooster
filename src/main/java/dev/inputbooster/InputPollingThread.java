package dev.inputbooster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

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

        while (running.get()) {
            long loopStart = System.nanoTime();

            try {
                poll();
            } catch (Exception ignored) {
                // Never crash the thread — silently continue
            }

            // Sleep precisely to hit target Hz
            int hz        = pollRateHz.get();
            long targetNs = 1_000_000_000L / hz;
            long elapsed  = System.nanoTime() - loopStart;
            long sleepNs  = targetNs - elapsed;

            if (sleepNs > 0) {
                try {
                    // Use nanosecond sleep for accuracy
                    Thread.sleep(sleepNs / 1_000_000L, (int)(sleepNs % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        InputBoosterMod.LOGGER.info("[InputBooster] Polling thread stopped.");
    }

    private void poll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getWindow() == null) return;
        if (mc.isPaused() || mc.currentScreen != null) return;

        GameOptions opt = mc.options;

        // ── Attack (LMB) ──────────────────────────────────────────────────────
        boolean attack = opt.attackKey.isPressed();
        if (attack  && !prevAttack) queue(InputAction.ATTACK_PRESSED);
        if (!attack && prevAttack)  queue(InputAction.ATTACK_RELEASED);
        prevAttack = attack;

        // ── Use / Place (RMB) ─────────────────────────────────────────────────
        boolean use = opt.useKey.isPressed();
        if (use  && !prevUse) queue(InputAction.USE_PRESSED);
        if (!use && prevUse)  queue(InputAction.USE_RELEASED);
        prevUse = use;

        // ── Sprint ────────────────────────────────────────────────────────────
        boolean sprint = opt.sprintKey.isPressed();
        if (sprint && !prevSprint) queue(InputAction.SPRINT_PRESSED);
        if (!sprint && prevSprint) queue(InputAction.SPRINT_RELEASED);
        prevSprint = sprint;

        // ── Sneak ─────────────────────────────────────────────────────────────
        boolean sneak = opt.sneakKey.isPressed();
        if (sneak  && !prevSneak) queue(InputAction.SNEAK_PRESSED);
        if (!sneak && prevSneak)  queue(InputAction.SNEAK_RELEASED);
        prevSneak = sneak;

        // ── Jump ──────────────────────────────────────────────────────────────
        boolean jump = opt.jumpKey.isPressed();
        if (jump && !prevJump) queue(InputAction.JUMP_PRESSED);
        prevJump = jump;

        // ── Movement ──────────────────────────────────────────────────────────
        boolean forward = opt.forwardKey.isPressed();
        boolean back    = opt.backKey.isPressed();
        boolean left    = opt.leftKey.isPressed();
        boolean right   = opt.rightKey.isPressed();

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
        boolean drop      = opt.dropKey.isPressed();
        boolean swap      = opt.swapHandsKey.isPressed();
        boolean pickBlock = opt.pickItemKey.isPressed();

        if (drop      && !prevDrop)      queue(InputAction.DROP_PRESSED);
        if (swap      && !prevSwap)      queue(InputAction.SWAP_PRESSED);
        if (pickBlock && !prevPickBlock) queue(InputAction.PICK_BLOCK_PRESSED);

        prevDrop      = drop;
        prevSwap      = swap;
        prevPickBlock = pickBlock;
    }

    private void queue(InputAction action) {
        if (InputActionQueue.queue(action)) {
            InputBoosterMod.recoveredInputs++;
        }
    }

    public void stopPolling() {
        running.set(false);
        this.interrupt();
    }
}
