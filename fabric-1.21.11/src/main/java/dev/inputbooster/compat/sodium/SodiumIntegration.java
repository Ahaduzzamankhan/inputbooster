package dev.inputbooster.compat.sodium;

import dev.inputbooster.InputBoosterMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/**
 * Everything the mod needs to know about Sodium without touching it.
 *
 * <p>This class deliberately contains no reference to any Sodium class. It only
 * asks the loader whether Sodium is installed and which version it is, so it
 * is safe to call from anywhere, including from the core, and it can never fail
 * with a {@link NoClassDefFoundError}.
 *
 * <p>The Sodium releases for 1.21.x do not ship the {@code sodium:config_api_user}
 * entrypoint, so this build has no Sodium configuration pages: the settings live
 * on the mod's own screen and {@link #isActive()} is always {@code false}.
 */
public final class SodiumIntegration {

    private static final String SODIUM_MOD_ID = "sodium";

    /** Set once Sodium has asked for the configuration pages. */
    private static volatile boolean active = false;

    private SodiumIntegration() {}

    /** True when Sodium is installed. */
    public static boolean isSodiumLoaded() {
        return FabricLoader.getInstance().isModLoaded(SODIUM_MOD_ID);
    }

    /** True once Sodium has loaded the InputBooster configuration pages. */
    public static boolean isActive() {
        return active;
    }

    /** Called by the entrypoint once the pages have been registered. */
    public static void markActive() {
        active = true;
    }

    /**
     * A one-line description of the Sodium situation.
     *
     * @return the Sodium version, or a short reason why there is none
     */
    public static String describe() {
        if (!isSodiumLoaded()) return "not installed";
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(SODIUM_MOD_ID);
        if (container.isEmpty()) return "installed, version unknown";
        String version = container.get().getMetadata().getVersion().getFriendlyString();
        return version + (active ? " (settings integrated)" : " (settings not integrated)");
    }

    /**
     * The integration path the mod is running on.
     *
     * @return {@code sodium} when the pages are live, {@code vanilla} otherwise
     */
    public static String integrationPath() {
        return active ? "sodium" : "vanilla";
    }

    /** Writes the startup line about Sodium. */
    public static void logStatus() {
        InputBoosterMod.LOGGER.info("[InputBooster] Sodium: {}", describe());
    }
}
