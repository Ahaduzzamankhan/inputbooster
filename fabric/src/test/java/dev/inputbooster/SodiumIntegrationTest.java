package dev.inputbooster;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the optional Sodium options integration.
 *
 * <p>The player asked for InputBooster to appear in the sidebar of Sodium's
 * options screen, next to the entries other mods add there. Sodium must stay
 * an <em>optional</em> integration: it is not a dependency, so the mod has to
 * load and run unchanged on a vanilla Minecraft install, and a Sodium version
 * that moves its API may only cost the sidebar entry, never a crash.
 *
 * <p>That is enforced three ways: the build must not declare Sodium, the shared
 * source set must not reference any Sodium type at compile time (everything is
 * reached by reflection behind a {@link java.lang.reflect.Proxy}), and the call
 * must be guarded by {@code FabricLoader.isModLoaded}.
 */
class SodiumIntegrationTest {

    private static final String BRIDGE = "src/main/java/dev/inputbooster/integration/SodiumOptionsBridge.java";
    private static final String ENTRYPOINT = "fabric/src/main/java/dev/inputbooster/platform/InputBoosterFabric.java";
    private static final String BUILD = "fabric/build.gradle";
    private static final String METADATA = "fabric/build/resources/main/fabric.mod.json";

    private static String read(String relative) throws Exception {
        Path path = Path.of("..", relative);
        assertTrue(Files.exists(path), "expected file at " + path.toAbsolutePath());
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void sodiumIsNotADependency() throws Exception {
        String build = stripComments(read(BUILD));
        assertFalse(build.contains("sodium"), "Sodium must not be declared in build.gradle");

        com.google.gson.JsonObject depends = metadata().getAsJsonObject("depends");
        for (String key : depends.keySet()) {
            assertFalse(key.contains("sodium"),
                "fabric.mod.json must not depend on Sodium; it declares " + key);
        }
    }

    @Test
    void noSodiumTypeIsReferencedAtCompileTime() throws Exception {
        // Every Sodium type is reached by name at runtime. A compile-time
        // import or a fully-qualified type would make the mod fail to load on a
        // vanilla install with a NoClassDefFoundError.
        String bridge = read(BRIDGE);
        StringBuilder offenders = new StringBuilder();
        for (String line : bridge.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) continue;
            if (trimmed.contains("net.caffeinemc") && !trimmed.contains("\"")) {
                offenders.append(trimmed).append('\n');
            }
        }
        assertEquals("", offenders.toString(),
            "Sodium classes may only appear as string literals inside the bridge, so they are "
                + "loaded reflectively:\n" + offenders);
    }

    @Test
    void theBridgeIsGuardedAndNeverThrows() throws Exception {
        String bridge = read(BRIDGE);
        assertTrue(bridge.contains("isModLoaded(\"sodium\")"),
            "the integration must be skipped entirely when Sodium is not installed");
        assertTrue(bridge.contains("catch (Throwable"),
            "reflection against another mod can fail for any reason; none may stop InputBooster "
                + "from loading");
        assertTrue(bridge.contains("if (attempted) return;"),
            "registration must be idempotent so it cannot add the page twice");
    }

    @Test
    void sodiumHooksAreResolvedOnThePublicApiType() throws Exception {
        // The 26.3 crash: "cannot access a member of class
        // ...ModOptionsBuilderImpl with modifiers public". Sodium returns a
        // package-private implementation class, so a Method read off the
        // runtime class is public and still uncallable from this package.
        String bridge = code(read(BRIDGE));
        assertFalse(bridge.contains(".getClass().getMethod("),
            "a hook must never be read off a runtime class; Sodium returns "
                + "package-private implementation classes");
        assertFalse(bridge.contains(".getClass().getDeclaredMethod("),
            "the same rule applies to declared lookups");
        for (String apiType : List.of("CONFIG_BUILDER_CLASS", "MOD_OPTIONS_BUILDER_CLASS",
            "EXTERNAL_PAGE_BUILDER_CLASS")) {
            assertTrue(bridge.contains(apiType),
                "the hook must be read from the public Sodium API type it is declared on: "
                    + apiType);
        }
    }

    @Test
    void theEntryPointHandlerCannotPropagate() throws Exception {
        // Sodium calls registerConfigLate inside a handler that ends the game
        // with crashWithMessage, so the proxy handler must absorb everything.
        String bridge = code(read(BRIDGE));
        assertFalse(bridge.contains("throws Throwable"),
            "the invocation handler must not declare a checked exception, or Sodium turns a "
                + "failed page build into a crash report");
        int guards = bridge.split("catch \\(Throwable", -1).length - 1;
        assertTrue(guards >= 2,
            "both register() and the invocation handler need a Throwable guard, found " + guards);
    }

    @Test
    void theEntryPointIsRegisteredBeforeSodiumBuildsItsConfig() throws Exception {
        String bridge = read(BRIDGE);
        assertTrue(bridge.contains("registerConfigEntryPoint"),
            "registration goes through Sodium's own ConfigManager, the same call its "
                + "ConfigLoaderFabric makes for mods that declare the entrypoint");
        assertTrue(bridge.contains("registerConfigLate"),
            "the page is built in the late phase: the early phase runs from Sodium's own "
                + "entrypoint, which may already have happened by the time this mod starts");
        assertFalse(bridge.contains("registerModOptions(String.class)"),
            "the three-argument form declares the display name and version explicitly");

        String entrypoint = read(ENTRYPOINT);
        assertTrue(entrypoint.contains("SodiumOptionsBridge.register()"),
            "the client entrypoint is where the registration happens, which is before the game load "
                + "mixin Sodium uses to finish its config");
    }

    @Test
    void theSidebarEntryOpensTheSameSettingsScreenAsTheOptionsEntry() throws Exception {
        String bridge = read(BRIDGE);
        assertTrue(bridge.contains("createExternalPage"),
            "an external page is a sidebar entry that opens the mod's own screen, so the settings "
                + "keep their real widgets instead of being re-expressed through Sodium's model");
        assertTrue(bridge.contains("new InputBoosterScreen("),
            "the sidebar entry must open the same InputBooster GUI as the vanilla Options entry");
        assertTrue(bridge.contains("setScreenAndShow"),
            "screens are opened through the client's setScreenAndShow");
    }

    @Test
    void theIntegrationDoesNotTouchSodiumsRendering() throws Exception {
        // The point of the previous release was renderer independence. Sodium
        // replaces rendering classes, so nothing here may reference one.
        String bridge = read(BRIDGE);
        for (String forbidden : List.of("net.caffeinemc.mods.sodium.client.render",
            "net.caffeinemc.mods.sodium.client.gui.options",
            "org.joml", "org.lwjgl")) {
            assertFalse(bridge.contains(forbidden),
                "the integration must only add a sidebar entry; it must not touch " + forbidden);
        }
    }

    /** Strips block and line comments, leaving only the statements. */
    private static String code(String source) {
        String withoutBlocks = source.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder out = new StringBuilder(withoutBlocks.length());
        for (String line : withoutBlocks.split("\\R")) {
            int comment = line.indexOf("//");
            out.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }
        return out.toString();
    }

    private static String stripComments(String script) {
        StringBuilder out = new StringBuilder(script.length());
        for (String line : script.split("\\R")) {
            int comment = line.indexOf("//");
            out.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }
        return out.toString();
    }

    private static com.google.gson.JsonObject metadata() throws Exception {
        Path generated = Path.of("build", "resources", "main", "fabric.mod.json");
        try (java.io.InputStream in = Files.newInputStream(generated)) {
            return com.google.gson.JsonParser.parseString(
                new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}