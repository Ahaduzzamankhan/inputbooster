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
 *
 * <h2>Two rules the reflection must follow</h2>
 * <ol>
 *   <li><b>Look methods up on the public API type, never on the object's
 *       runtime class.</b> Sodium returns {@code ModOptionsBuilderImpl}, which
 *       is package-private, so {@code value.getClass().getMethod(...)} produces
 *       a method that is public but unreachable, and invoking it throws
 *       {@link IllegalAccessException}. The same member read from the public
 *       {@code ModOptionsBuilder} interface it implements is accessible.</li>
 *   <li><b>Never let an exception out of the entry point.</b> Sodium calls
 *       {@code registerConfigLate} inside a try/catch that ends the game with
 *       {@code crashWithMessage}, so a thrown exception is a crash report, not
 *       a missing sidebar entry.</li>
 * </ol>
 */
public final class SodiumOptionsBridge {

    /** Sodium's public configuration entry point interface. */
    private static final String ENTRY_POINT_CLASS =
        "net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint";

    /** Sodium's static registry of per-mod configuration entry points. */
    private static final String CONFIG_MANAGER_CLASS =
        "net.caffeinemc.mods.sodium.client.config.ConfigManager";

    /** {@code ConfigBuilder}, the public interface handed to the entry point. */
    private static final String CONFIG_BUILDER_CLASS =
        "net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder";

    /** {@code ModOptionsBuilder}, the public type {@code registerModOptions} returns. */
    private static final String MOD_OPTIONS_BUILDER_CLASS =
        "net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder";

    /** {@code ExternalPageBuilder}, the sidebar entry that opens our own screen. */
    private static final String EXTERNAL_PAGE_BUILDER_CLASS =
        "net.caffeinemc.mods.sodium.api.config.structure.ExternalPageBuilder";

    /** {@code PageBuilder}, the common supertype of the page builders. */
    private static final String PAGE_BUILDER_CLASS =
        "net.caffeinemc.mods.sodium.api.config.structure.PageBuilder";

    private static final String MOD_ID = "inputbooster";

    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

    private static final Class<?>[] THREE_STRINGS =
        {String.class, String.class, String.class};

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
                "[Sodium] Registered the InputBooster config entry point.");
        } catch (Throwable t) {
            // Reflection against another mod's API can fail for any number of
            // reasons; none of them may stop InputBooster from loading.
            InputBoosterMod.LOGGER.warn(
                "[Sodium] Could not register the InputBooster config entry point. "
                    + "The entry in the vanilla Options screen is unaffected.", t);
        }
    }

    private static void doRegister() throws ReflectiveOperationException {
        Class<?> entryPoint = Class.forName(ENTRY_POINT_CLASS);

        Object proxy = Proxy.newProxyInstance(
            entryPoint.getClassLoader(), new Class<?>[]{entryPoint}, new EntryPointHandler());

        // ConfigManager#registerConfigEntryPoint(Supplier<ConfigEntryPoint>, String modId)
        @SuppressWarnings("unchecked")
        Supplier<Object> supplier = () -> proxy;
        call(null, Class.forName(CONFIG_MANAGER_CLASS),
            "registerConfigEntryPoint", new Class<?>[]{Supplier.class, String.class},
            new Object[]{supplier, MOD_ID});
    }

    /**
     * Implements Sodium's {@code ConfigEntryPoint}.
     *
     * <p>Only the late phase builds anything: early registration runs from
     * Sodium's own entrypoint, which may already have happened by the time this
     * mod starts, whereas the late phase runs during game load.
     *
     * <p>Every branch swallows {@link Throwable}. Sodium wraps this call in a
     * handler that ends the game, so an exception here would crash Minecraft
     * over a cosmetic sidebar entry.
     */
    static final class EntryPointHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            try {
                switch (method.getName()) {
                    case "registerConfigLate" -> {
                        addPage(args[0]);
                        InputBoosterMod.LOGGER.info(
                            "[Sodium] Added the InputBooster page to Sodium's options screen.");
                    }
                    case "registerConfigEarly" -> {
                        // Built late instead; see above.
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
                        // A new hook in a future Sodium: nothing to do, and
                        // definitely not a reason to end the game.
                    }
                }
            } catch (Throwable t) {
                InputBoosterMod.LOGGER.warn(
                    "[Sodium] Could not add the InputBooster page to Sodium's options screen. "
                        + "The entry in the vanilla Options screen is unaffected.", t);
            }
            return null;
        }
    }

    /**
     * Builds the sidebar entry.
     *
     * <p>An <em>external</em> page is used rather than a page of Sodium-native
     * options: it simply opens the mod's own screen, so the settings keep their
     * real widgets and their own layout instead of being re-expressed through
     * Sodium's option model.
     *
     * <p>Each call goes through {@link #call} with the <em>public</em> Sodium
     * API type that declares the method, never {@code target.getClass()}: the
     * objects Sodium hands back are package-private implementation classes, and
     * a method read off those is public but not accessible from here.
     */
    private static void addPage(Object configBuilder) throws ReflectiveOperationException {
        Class<?> configBuilderType = Class.forName(CONFIG_BUILDER_CLASS);
        Class<?> modOptionsBuilderType = Class.forName(MOD_OPTIONS_BUILDER_CLASS);
        Class<?> externalPageBuilderType = Class.forName(EXTERNAL_PAGE_BUILDER_CLASS);
        Class<?> pageBuilderType = Class.forName(PAGE_BUILDER_CLASS);

        Object modOptions = call(configBuilder, configBuilderType,
            "registerModOptions", THREE_STRINGS,
            new Object[]{MOD_ID, InputBoosterMod.MOD_NAME, InputBoosterMod.MOD_VERSION});

        Object page = call(configBuilder, configBuilderType,
            "createExternalPage", NO_PARAMETERS, new Object[0]);

        call(page, externalPageBuilderType, "setName", new Class<?>[]{Component.class},
            new Object[]{Component.translatable("inputbooster.options.title")});

        call(page, externalPageBuilderType, "setScreenConsumer", new Class<?>[]{Consumer.class},
            new Object[]{openSettingsScreen()});

        call(modOptions, modOptionsBuilderType, "addPage", new Class<?>[]{pageBuilderType},
            new Object[]{page});
    }

    /** The action behind the sidebar entry: open the InputBooster GUI. */
    private static Consumer<Screen> openSettingsScreen() {
        return parent -> {
            Minecraft client = Minecraft.getInstance();
            if (parent == null && client.gui != null) parent = client.gui.screen();
            client.setScreenAndShow(new InputBoosterScreen(parent));
        };
    }

    /**
     * Invokes {@code apiType}'s {@code name} on {@code target}, without ever
     * consulting the runtime class of {@code target}.
     *
     * @param apiType the public interface that declares the method; must be a
     *                supertype of {@code target}'s class
     */
    private static Object call(Object target, Class<?> apiType, String name,
        Class<?>[] parameterTypes, Object[] args) throws ReflectiveOperationException {
        return method(apiType, name, parameterTypes).invoke(target, args);
    }

    /**
     * Reads a method from a public type and makes it callable.
     *
     * <p>{@code setAccessible} is redundant for a public member of a public
     * type, and deliberately tolerated when refused: it is only there so a
     * future Sodium that moves a hook onto a package-private class still works
     * instead of crashing the game.
     */
    private static Method method(Class<?> apiType, String name, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Method method = apiType.getMethod(name, parameterTypes);
        try {
            method.setAccessible(true);
        } catch (RuntimeException ignored) {
            // The member is already accessible.
        }
        return method;
    }
}