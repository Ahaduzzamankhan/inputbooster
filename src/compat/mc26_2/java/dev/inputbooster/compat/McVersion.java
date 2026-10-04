package dev.inputbooster.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Minecraft 26.2 bindings.
 *
 * The input, swing and drop APIs changed between 26.2 and 26.3, so the shared
 * core calls this shim and each supported version provides its own copy.
 */
public final class McVersion {

    private McVersion() {}

    /** Raw physical key state. */
    public static boolean isKeyDown(int keyCode) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), keyCode);
    }

    /** Plays the arm swing animation. */
    public static void swingArm(LocalPlayer player) {
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Drops the currently held stack, one item per press. */
    public static void dropHeldItem(LocalPlayer player) {
        player.drop(player.getMainHandItem(), false, false);
    }
}