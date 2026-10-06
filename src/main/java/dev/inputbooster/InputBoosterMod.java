package dev.inputbooster;

import dev.inputbooster.perf.OptimizationManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loader-neutral core of InputBooster.
 *
 * <p>InputBooster is a performance mod. It has no gameplay features, draws
 * nothing and sends the player no message of any kind: no HUD, no overlay, no
 * chat, no toast, no title. Settings live behind
 * <em>Options → InputBooster</em>, and everything the mod does happens silently
 * from the client tick.
 *
 * <p>The entire per-tick cost is one increment and one call into
 * {@link OptimizationManager#tick(long)}, which itself is a single branch for
 * almost every tick.
 *
 * <p>Author: Ahaduzzaman Khan
 */
public final class InputBoosterMod {

    public static final String MOD_ID = "inputbooster";
    public static final String MOD_NAME = "InputBooster";
    public static final String MOD_VERSION = "4.0.0-alpha-2";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final AtomicBoolean initialized = new AtomicBoolean();
    private static volatile boolean shuttingDown;

    private static long tick;

    private InputBoosterMod() {
    }

    /** Called by the platform entrypoint during mod construction. */
    public static void bootstrap() {
        // Nothing to register: the mod declares no key bindings, because it
        // handles no input.
    }

    public static void initialize() {
        if (initialized.get() || shuttingDown) return;
        try {
            InputBoosterConfig.load();
            OptimizationManager engine = OptimizationManager.get();
            engine.setServerKeySource(InputBoosterMod::currentServerKey);
            engine.start();
            initialized.set(true);
            LOGGER.info("{} {} ready", MOD_NAME, MOD_VERSION);
        } catch (Exception e) {
            LOGGER.error("{} failed to start; the game is unaffected", MOD_NAME, e);
        }
    }

    /** Called from the client tick event of the active loader. */
    public static void onClientTick(Minecraft client) {
        if (shuttingDown) return;
        initialize();
        if (!initialized.get()) return;
        tick++;
        OptimizationManager.get().tick(tick);
    }

    /**
     * Identity of the world the player is in, used by the CPU module to notice a
     * server change without re-deriving it every tick. Returns null outside a
     * world.
     */
    private static String currentServerKey() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return null;
        var serverData = client.getConnection() == null ? null : client.getConnection().getServerData();
        String host = serverData == null ? null : serverData.ip;
        return (host == null ? "local" : host) + '/' + client.level.dimension();
    }

    public static long currentTick() {
        return tick;
    }

    public static boolean isInitialized() {
        return initialized.get();
    }

    public static void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        try {
            OptimizationManager.get().shutdown();
            InputBoosterConfig.saveNow();
            initialized.set(false);
        } catch (Throwable t) {
            LOGGER.warn("{} shutdown problem", MOD_NAME, t);
        }
    }
}