package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputAction;
import dev.inputbooster.InputActionQueue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * WTapAssist
 *
 * W-tapping is a PvP technique where you release W (forward) just before
 * hitting an enemy to reduce your own velocity, increasing knockback dealt.
 *
 * At low FPS, W-tap releases often span multiple frames and the velocity
 * cut doesn't happen cleanly. This feature monitors the action queue for
 * FORWARD_RELEASED events and applies a brief velocity dampen on the next tick.
 *
 * Author: Ahaduzzaman Khan
 */
public class WTapAssist {

    private volatile boolean pendingWRelease = false;
    private volatile int wReleaseTickDelay   = 0;

    public void tick(MinecraftClient mc) {
        if (!InputBoosterConfig.isWTapAssistEnabled()) return;

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        // Check if a W-release was queued by the polling thread
        // We scan the queue without consuming (peeking) for FORWARD_RELEASED
        // In practice, InputDrainer already cleared it; we track it via a flag
        // set by the polling thread count - handled via config stat

        // Apply pending W-tap dampen
        if (pendingWRelease && wReleaseTickDelay <= 0) {
            // Dampen forward velocity slightly on W-release for cleaner knockback
            if (!mc.options.forwardKey.isPressed()) {
                double vx = player.getVelocity().x;
                double vy = player.getVelocity().y;
                double vz = player.getVelocity().z;
                // Reduce horizontal velocity by 40% on W-release tick
                player.setVelocity(vx * 0.6, vy, vz * 0.6);
            }
            pendingWRelease = false;
        }

        if (wReleaseTickDelay > 0) wReleaseTickDelay--;
    }

    /** Called by the polling thread when a W-release is detected. */
    public void onWRelease() {
        pendingWRelease   = true;
        wReleaseTickDelay = 1;
    }
}
