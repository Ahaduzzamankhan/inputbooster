package dev.inputbooster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

public final class McCompat {
    private McCompat() {}

    public static int getFps(MinecraftClient mc) {
        return mc.getCurrentFps();
    }

    public static boolean isInWater(ClientPlayerEntity player) {
        return player.isTouchingWater();
    }

    public static boolean isClimbing(ClientPlayerEntity player) {
        return player.isClimbing();
    }

    public static int getFoodLevel(ClientPlayerEntity player) {
        return player.getHungerManager().getFoodLevel();
    }

    public static void setSneaking(ClientPlayerEntity player, boolean sneaking) {
        player.setSneaking(sneaking);
    }
}
