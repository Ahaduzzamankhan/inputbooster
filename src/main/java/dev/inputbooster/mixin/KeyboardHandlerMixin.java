package dev.inputbooster.mixin;

import dev.inputbooster.engine.KeyboardProcessor;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes key events as the game receives them.
 *
 * <p>HEAD, non-cancelling, no local capture: vanilla still routes the event to
 * the open screen, the key bindings and every other mod exactly as before. The
 * only thing added is a state update for keys this mod already tracks.
 *
 * <p>{@code require = 0}. If a future version renames the method, the mod loses
 * the event source and keeps working on the poller; it must never fail to start
 * over an optimisation.
 */
@Mixin(value = KeyboardHandler.class, priority = 1100)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void onKeyPressObserved(long window, int key, int scancode, int action, int modifiers,
                                    CallbackInfo ci) {
        KeyboardProcessor.onKeyEvent(key, action);
    }
}
