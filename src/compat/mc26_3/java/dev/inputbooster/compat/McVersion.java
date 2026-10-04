package dev.inputbooster.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Minecraft 26.3 bindings.
 *
 * The input, swing and drop APIs changed between 26.2 and 26.3, so the shared
 * core calls this shim and each supported version provides its own copy.
 */
public final class McVersion {

    private McVersion() {}

    /**
     * Raw physical key state, read from the platform window rather than from
     * Minecraft's per-tick key bindings. Safe to call from the polling thread.
     */
    public static boolean isKeyDown(Window window, int keyCode) {
        return InputConstants.isKeyDown(keyCode);
    }

    /** Raw physical key state for code paths that run on the game thread. */
    public static boolean isKeyDown(int keyCode) {
        return InputConstants.isKeyDown(keyCode);
    }

    /** Plays the arm swing animation. */
    public static void swingArm(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
    }

    /** Drops the currently held stack, one item per press. */
    public static void dropHeldItem(LocalPlayer player) {
        player.drop(player.getMainHandItem(), false, Prediction.PREDICTED);
    }
}