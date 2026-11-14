package dev.inputbooster.input;

import dev.inputbooster.InputActionQueue;
import dev.inputbooster.InputBoosterConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * InputBooster's own view of who owns the keyboard right now.
 *
 * Minecraft routes key events to the open screen, so a space typed in chat
 * belongs to the chat box, not to the player. The mod reads raw platform key
 * state at up to 1000 Hz, which bypasses that routing completely; without a
 * reliable screen signal a chat space press comes out as a jump.
 *
 * Screen state is recorded by {@code ScreenTrackingMixin} from the method the
 * game itself uses to show a screen, so the decision does not depend on
 * polling the HUD at tick time. Nothing here changes vanilla key bindings,
 * cancels events or intercepts another mod's input.
 */
public final class KeyboardRouter {

    private static volatile boolean screenOpen;
    private static volatile String lastTransition = "none";

    private KeyboardRouter() {}

    /** Called on the game thread whenever the game shows or closes a screen. */
    public static void recordScreen(Screen screen) {
        boolean open = screen != null;
        if (open) {
            // Anything already queued was produced before the screen existed and
            // must not execute behind it.
            InputActionQueue.clear();
        }
        if (open != screenOpen) {
            screenOpen = open;
            lastTransition = open ? "screen opened" : "screen closed";
        }
    }

    public static boolean isScreenOpen() {
        return screenOpen;
    }

    /** True when the player, not a screen, owns the keyboard. Game thread only. */
    public static boolean worldInputAllowed(Minecraft mc) {
        if (screenOpen) return false;
        if (mc == null) return false;
        // 26.1 keeps the open screen on the client itself.
        return mc.screen == null;
    }

    /** The alpha handler is opt-in and lives in the central config. */
    public static boolean customHandlerEnabled() {
        return InputBoosterConfig.isCustomKeyboardHandlerEnabled();
    }

    public static String statusLine() {
        String mode = customHandlerEnabled() ? "custom (alpha)" : "vanilla bindings";
        return "Keyboard: " + mode + (screenOpen ? ", screen open" : "");
    }

    public static String lastTransition() {
        return lastTransition;
    }

    public static void reset() {
        screenOpen = false;
        lastTransition = "reset";
    }
}
