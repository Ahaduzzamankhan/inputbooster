package dev.inputbooster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that Sodium can actually pick the configuration pages up.
 *
 * <p>Sodium finds a third-party configuration through the
 * {@code sodium:config_api_user} Fabric entrypoint, instantiates the class
 * reflectively through its no-argument constructor and only then looks at the
 * {@code ConfigEntryPoint} interface. A missing entrypoint in the metadata, a
 * constructor that takes arguments or a class that implements the wrong
 * interface are all silent failures: Sodium logs a warning and the pages
 * simply never appear.
 *
 * <p>The class file is read with ASM instead of being loaded, so the test does
 * not need Sodium itself on the classpath and never runs any of its code.
 */
class SodiumConfigEntryPointTest {

    private static final String ENTRY_POINT = "dev.inputbooster.compat.sodium.SodiumConfigEntryPoint";
    private static final String CONFIG_ENTRY_POINT_KEY = "sodium:config_api_user";
    private static final String CONFIG_ENTRY_POINT_INTERFACE =
            "net/caffeinemc/mods/sodium/api/config/ConfigEntryPoint";

    private static JsonObject metadata() throws Exception {
        Path generated = Path.of("build", "resources", "main", "fabric.mod.json");
        try (InputStream in = Files.newInputStream(generated)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    @Test
    void theSodiumEntrypointIsDeclaredInTheModMetadata() throws Exception {
        JsonObject entrypoints = metadata().getAsJsonObject("entrypoints");
        assertNotNull(entrypoints, "fabric.mod.json must declare entrypoints");

        JsonArray sodium = entrypoints.getAsJsonArray(CONFIG_ENTRY_POINT_KEY);
        assertNotNull(sodium, "fabric.mod.json must declare the \"" + CONFIG_ENTRY_POINT_KEY + "\" entrypoint");

        boolean found = false;
        for (JsonElement element : sodium) {
            if (ENTRY_POINT.equals(element.getAsString())) found = true;
        }
        assertTrue(found, "the \"" + CONFIG_ENTRY_POINT_KEY + "\" entrypoint must point at " + ENTRY_POINT);
    }

    @Test
    void sodiumDoesNotBecomeAHardDependency() throws Exception {
        JsonObject depends = metadata().getAsJsonObject("depends");
        assertTrue(depends == null || !depends.has("sodium"),
                "Sodium must stay optional: it may not be listed as a dependency");
        assertTrue(depends == null || !depends.has("modmenu"),
                "Mod Menu must stay optional: it may not be listed as a dependency");
    }

    @Test
    void theModMenuEntrypointIsDeclared() throws Exception {
        JsonObject entrypoints = metadata().getAsJsonObject("entrypoints");
        JsonArray modMenu = entrypoints.getAsJsonArray("modmenu");
        assertNotNull(modMenu, "fabric.mod.json must declare the \"modmenu\" entrypoint");
        assertEquals("dev.inputbooster.compat.modmenu.ModMenuIntegration",
                modMenu.get(0).getAsString());
    }

    @Test
    void theEntrypointImplementsTheSodiumInterfaceAndCanBeConstructed() throws Exception {
        ClassNode node = readClass(ENTRY_POINT.replace('.', '/'));
        assertNotNull(node, ENTRY_POINT + " must be compiled into the mod");

        assertTrue(node.interfaces.contains(CONFIG_ENTRY_POINT_INTERFACE),
                ENTRY_POINT + " must implement " + CONFIG_ENTRY_POINT_INTERFACE.replace('/', '.'));

        boolean noArgConstructor = false;
        for (MethodNode method : node.methods) {
            if (!"<init>".equals(method.name)) continue;
            if ("()V".equals(method.desc) && (method.access & Opcodes.ACC_PUBLIC) != 0) {
                noArgConstructor = true;
            }
        }
        assertTrue(noArgConstructor,
                ENTRY_POINT + " needs a public no-argument constructor: Sodium instantiates it reflectively");
    }

    private static ClassNode readClass(String internalName) throws IOException {
        try (InputStream in = SodiumConfigEntryPointTest.class
                .getResourceAsStream("/" + internalName + ".class")) {
            if (in == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                    | ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
