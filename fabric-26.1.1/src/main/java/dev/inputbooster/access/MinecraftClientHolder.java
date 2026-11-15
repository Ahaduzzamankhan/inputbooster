package dev.inputbooster.access;

/**
 * Duck interface for the vanilla pick-block action.
 *
 * <p>See {@link GuiRenderStateHolder} for why the accessor lives outside the
 * mixin package.
 */
public interface MinecraftClientHolder {

    /**
     * Runs vanilla's pick-block action.
     *
     * <p>The action is private on the client in 26.x, so it is reached through
     * an invoker.
     */
    void inputbooster$pickBlockOrEntity();
}
