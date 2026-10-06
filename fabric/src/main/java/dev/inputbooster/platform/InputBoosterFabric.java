package dev.inputbooster.platform;

import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.integration.SodiumOptionsBridge;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric client entrypoint.
 *
 * <p>All mod logic lives in {@link InputBoosterMod}; this class only wires the
 * core into the Fabric lifecycle. No key mapping is registered, because the mod
 * binds no key: it handles no input and draws nothing.
 */
public final class InputBoosterFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Optional, reflective, and a no-op when Sodium is absent. Registered
        // first because Sodium builds its config during game load, which is
        // after every client entrypoint has run.
        SodiumOptionsBridge.register();

        InputBoosterMod.bootstrap();

        ClientTickEvents.END_CLIENT_TICK.register(
            client -> InputBoosterMod.onClientTick(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(
            client -> InputBoosterMod.shutdown());
    }
}