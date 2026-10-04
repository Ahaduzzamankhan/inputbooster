package dev.inputbooster.platform;

import dev.inputbooster.InputBoosterMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

/**
 * Fabric client entrypoint.
 *
 * All mod logic lives in {@link InputBoosterMod}; this class only wires the
 * core into the Fabric lifecycle and key binding registry.
 */
public final class InputBoosterFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Create the mappings before registering them — the previous ordering
        // meant the bindings were never handed to the registry.
        for (KeyMapping mapping : InputBoosterMod.createKeyMappings()) {
            KeyMappingHelper.registerKeyMapping(mapping);
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> InputBoosterMod.onClientTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> InputBoosterMod.shutdown());

        InputBoosterMod.LOGGER.info("[{}] Fabric client entrypoint registered", InputBoosterMod.MOD_NAME);
    }
}