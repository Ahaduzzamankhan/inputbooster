package dev.inputbooster;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class McCompat {
    private McCompat() {}

    public static int getFps(Minecraft mc) {
        return mc.getFps();
    }

    public static boolean isInWater(LocalPlayer player) {
        return player.isInWater();
    }

    public static boolean isClimbing(LocalPlayer player) {
        return player.onClimbable();
    }

    public static int getFoodLevel(LocalPlayer player) {
        return player.getFoodData().getFoodLevel();
    }

    public static void setSneaking(LocalPlayer player, boolean sneaking) {
        player.setShiftKeyDown(sneaking);
    }
}
