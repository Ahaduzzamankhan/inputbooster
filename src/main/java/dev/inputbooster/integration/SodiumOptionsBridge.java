package dev.inputbooster.integration;

import dev.inputbooster.InputBoosterMod;
import dev.inputbooster.screen.InputBoosterScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Optional integration with the Sodium options screen.
 *
 * <p>Sodium replaces the vanilla options screen with one that lists every mod
 * page in a sidebar. This bridge adds the InputBooster entry there so the
 * navigation stays <em>Options &rarr; InputBooster &rarr; InputBooster GUI</em>
 * whichever options screen the player has.
 *
 * <h2>Sodium is not a dependency</h2>
 * Nothing here is referenced at compile time: every Sodium type is reached
 * through reflection and behind a {@link Proxy}, so the mod compiles and runs
 * unchanged when Sodium is absent, and every failure degrades to "the entry is
 * simply not in Sodium's sidebar" instead of a crash. Sodium is checked with
 * {@code FabricLoader.isModLoaded}, and the whole thing runs once at client
 * start-up.
 *
 * <p>The registration must happen before Sodium builds its config, which it
 * does from a {@code Minecraft} mixin during game load — that is after every
 * client entrypoint has run, so registering from
 * {@code ClientModInitializer#onInitializeClient} is always early enough.
 */
public final class SodiumOptionsBridge {

    /** Sodium's public configuration entry point interface. */
    private static final String ENTRY_POINT_CLASS =
        "net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint";

    /** Sodium's static registry of per-mod configuration entry points. */
    private static final String CONFIG_MANAGER_CLASS =
        "net.caffeinemc.mods.sodium.client.config.ConfigManager";

    /** {@code ExternalPageBuilder}, the sidebar entry that opens our own screen. */
    private static final String EXTERNAL_PAGE_BUILDER_CLASS =
        "net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder";

    /** {@code PageBuilder}, the common supertype of the page builders. */
    private static final String PAGE_BUILDER_CLASS =
        "net.caffeinemc.mods.sodium.api.config.structure.PageBuilder";

    private static final String MOD_ID = "inputbooster";

    private static boolean attempted = false;

    private SodiumOptionsBridge() {
    }

    /**
     * Registers the InputBooster page with Sodium if it is installed.
     *
     * <p>Idempotent, and never throws: a Sodium that changes its API only costs
     * the sidebar entry, because the vanilla options entry keeps working.
     */
    public static void register() {
        if (attempted) return;
        attempted = true;

        try {
            if (!FabricLoader.getInstance().isModLoaded("sodium")) return;
            doRegister();
            InputBoosterMod.LOGGER.info(
                "[Sodium] Added the InputBooster page to Sodium's options screen.");
        } catch (Throwable t) {
            // Reflection against another mod's API can fail for any number of
            // reasons; none of them may stop InputBooster from loading.
            InputBoosterMod.LOGGER.warn(
                "[Sodium] Could not add the InputBooster page to Sodium's options screen ({}). "
                    + "The entry in the vanilla Options screen is unaffected.",
                t.toString());
        }
    }

    private static void doRegister() throws Exception {
        Class<?> entryPoint = Class.forName(ENTRY_POINT_CLASS);

        Object proxy = Proxy.newProxyInstance(
            entryPoint.getClassLoader(), new Class<?>[]{entryPoint}, new EntryPointHandler());

        // ConfigManager#registerConfigEntryPoint(Supplier<ConfigEntryPoint>, String modId)
        @SuppressWarnings("unchecked")
        Supplier<Object> supplier = () -> proxy;
        Method register = Class.forName(CONFIG_MANAGER_CLASS)
            .getMethod("registerConfigEntryPoint", Supplier.class, String.class);
        register.invoke(null, supplier, MOD_ID);
    }

    /**
     * Implements Sodium's {@code ConfigEntryPoint}.
     *
     * <p>Only the late phase builds anything: early registration runs from
     * Sodium's own entrypoint, which may already have happened by the time this
     * mod starts, whereas the late phase runs during game load.
     */
    private static final class EntryPointHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "registerConfigLate" -> {
                    addPage(args[0]);
                    return null;
                }
                case "registerConfigEarly" -> {
                    return null;
                }
                case "toString" -> {
                    return "InputBooster " + MOD_ID + " Sodium config entry point";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return args != null && args.length == 1 && proxy == args[0];
                }
                default -> {
                    return null;
                }
            }
        }
    }

    /**
     * Builds the sidebar entry.
     *
     * <p>An <em>external</em> page is used rather than a page of Sodium-native
     * options: it simply opens the mod's own screen, so the settings keep their
     * real widgets and their own layout instead of being re-expressed through
     * Sodium's option model.
     */
    private static void addPage(Object configBuilder) throws Exception {
        Class<?> externalPageBuilder = Class.forName(EXTERNAL_PAGE_BUILDER_CLASS);
        Class<?> pageBuilder = Class.forName(PAGE_BUILDER_CLASS);

        Object modOptions = configBuilder.getClass()
            .getMethod("registerModOptions", String.class, String.class, String.class)
            .invoke(configBuilder, MOD_ID, InputBoosterMod.MOD_NAME, InputBoosterMod.MOD_VERSION);

        Object page = configBuilder.getClass()
            .getMethod("createExternalPage")
            .invoke(configBuilder);

        externalPageBuilder.getMethod("setName", Component.class)
            .invoke(page, Component.translatable("inputbooster.options.title"));

        @SuppressWarnings("unchecked")
        Consumer<Screen> openSettings = parent -> {
            Minecraft client = Minecraft.getInstance();
            if (parent == null && client.gui != null) parent = client.gui.screen();
            client.setScreenAndShow(new InputBoosterScreen(parent));
        };
        externalPageBuilder.getMethod("setScreenConsumer", Consumer.class)
            .invoke(page, openSettings);

        modOptions.getClass().getMethod("addPage", pageBuilder).invoke(modOptions, page);
    }
}