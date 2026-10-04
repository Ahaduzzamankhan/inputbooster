package dev.inputbooster.platform;

import dev.inputbooster.InputBoosterMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entrypoint.
 *
 * The mod logic itself is loader-neutral and lives in {@link InputBoosterMod};
 * this class only binds it to the NeoForge lifecycle.
 */
@Mod(value = InputBoosterMod.MOD_ID, dist = Dist.CLIENT)
public final class InputBoosterNeoForge {

    public InputBoosterNeoForge(IEventBus modBus) {
        // FIX: create the key mappings during construction so they already
        // exist when RegisterKeyMappingsEvent fires. Creating them in client
        // setup (which runs later) left the registration handler with nulls and
        // the R / K bindings were never added to the controls screen.
        InputBoosterMod.bootstrap();

        modBus.addListener(this::onRegisterKeyMappings);
        modBus.addListener(this::onClientSetup);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        for (var mapping : InputBoosterMod.createKeyMappings()) {
            event.register(mapping);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        InputBoosterMod.initialize();
    }

    private void onClientTick(ClientTickEvent.Post event) {
        InputBoosterMod.onClientTick();
    }
}