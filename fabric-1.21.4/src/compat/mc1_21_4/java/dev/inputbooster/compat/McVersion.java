package dev.inputbooster.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Minecraft 1.21.4 bindings.
 *
 * The input, swing and drop APIs differ between the supported game lines, so the
 * shared core calls this shim and each project provides its own copy.
 */
public final class McVersion {

    private McVersion() {}

    /**
     * Raw physical key state, read from the platform window rather than from
     * Minecraft's per-tick key bindings. Safe to call from the polling thread.
     */
    public static boolean isKeyDown(Window window, int keyCode) {
        return InputConstants.isKeyDown(window.getWindow(), keyCode);
    }

    /** Raw physical key state for code paths that run on the game thread. */
    public static boolean isKeyDown(int keyCode) {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null && InputConstants.isKeyDown(mc.getWindow().getWindow(), keyCode);
    }

    /** Plays the arm swing animation. */
    public static void swingArm(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Drops the currently held stack, one item per press. */
    public static void dropHeldItem(LocalPlayer player) {
        player.drop(false);
    }
}
