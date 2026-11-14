package dev.inputbooster.feature;

import dev.inputbooster.InputAction;
import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public final class InputClickSoundManager {
    private static long lastSoundNanos = 0L;
    private static final long MIN_INTERVAL_NANOS = 18_000_000L;

    private InputClickSoundManager() {}

    public static void playFor(InputAction action, Minecraft client) {
        if (!InputBoosterConfig.isClickSoundsEnabled() || client == null || client.player == null) return;
        if (!isPressedAction(action)) return;

        long now = System.nanoTime();
        if (now - lastSoundNanos < MIN_INTERVAL_NANOS) return;
        lastSoundNanos = now;

        client.getSoundManager().play(SimpleSoundInstance.forUI(
            SoundEvents.UI_BUTTON_CLICK.value(),
            InputBoosterConfig.getClickSoundPitch()
        ));
    }

    private static boolean isPressedAction(InputAction action) {
        return switch (action) {
            case ATTACK_PRESSED, USE_PRESSED, SPRINT_PRESSED, SNEAK_PRESSED, JUMP_PRESSED,
                 FORWARD_PRESSED, BACK_PRESSED, LEFT_PRESSED, RIGHT_PRESSED,
                 DROP_PRESSED, SWAP_PRESSED, PICK_BLOCK_PRESSED -> true;
            default -> false;
        };
    }
}
