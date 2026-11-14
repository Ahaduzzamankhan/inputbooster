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
 * <p>The target is {@code keyPress(long window, int key, int scancode, int
 * action, int modifiers)} on 1.21.x; 26.x replaced it with a
 * {@code KeyEvent} parameter. The handler parameters must match the target
 * exactly, minus the trailing {@link CallbackInfo}; a mismatch is a fatal
 * injection error even with {@code require = 0}, which only tolerates a missing
 * target. {@code MixinTargetSignatureTest} checks every hook against the
 * resolved Minecraft jar so a wrong descriptor fails the build instead of the
 * game.
 */
@Mixin(value = KeyboardHandler.class, priority = 1100)
public class KeyboardHandlerMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void onKeyPressObserved(long window, int key, int scancode, int action, int modifiers,
                                    CallbackInfo ci) {
        KeyboardProcessor.onKeyEvent(key, action);
    }
}
