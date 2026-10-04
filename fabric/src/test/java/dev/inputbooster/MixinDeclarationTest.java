package dev.inputbooster;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the declaration form of the mixins in
 * {@code inputbooster.mixins.json}.
 *
 * Mixin only accepts an interface-form mixin (a mixin declared as an
 * {@code interface}) when its target is itself an interface. Declaring
 * {@code KeyMappingAccessor}, {@code GuiAccessor} or
 * {@code MinecraftClientAccessor} as interfaces while their targets
 * ({@code KeyMapping}, {@code Gui}, {@code Minecraft}) are classes aborts mixin
 * preparation with "@Mixin target type mismatch ... is not an interface", which
 * crashed the client while another mod (Sodium) triggered class loading.
 */
class MixinDeclarationTest {

    private static final String PACKAGE = "dev.inputbooster.mixin.";

    private static JsonObject config() throws Exception {
        Path generated = Path.of("build", "resources", "main", "inputbooster.mixins.json");
        assertTrue(Files.exists(generated), "processed mixin config not found at " + generated.toAbsolutePath());
        try (InputStream in = Files.newInputStream(generated)) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    private static List<String> clientMixins() throws Exception {
        JsonArray array = config().getAsJsonArray("client");
        assertNotNull(array, "mixin config must declare a client list");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            names.add(array.get(i).getAsString());
        }
        return names;
    }

    private static Class<?> load(String simpleName) throws Exception {
        return Class.forName(PACKAGE + simpleName, false, MixinDeclarationTest.class.getClassLoader());
    }

    @Test
    void noMixinIsDeclaredAsAnInterface() throws Exception {
        for (String name : clientMixins()) {
            Class<?> mixin = load(name);
            assertFalse(mixin.isInterface(),
                name + " must be declared as a class: Minecraft 26.x targets are classes, "
                    + "and an interface-form mixin fails target validation with "
                    + "\"@Mixin target type mismatch ... is not an interface\"");
        }
    }

    @Test
    void mixinsContainNothingButMergeableAccessors() throws Exception {
        // Mixin merges every member of a class-form mixin into the target and
        // aborts the game for anything it cannot merge. "contains non-private
        // static method" is the observed crash, so helpers must live outside.
        for (String name : clientMixins()) {
            Class<?> mixin = load(name);
            for (Method method : mixin.getDeclaredMethods()) {
                assertFalse(Modifier.isStatic(method.getModifiers()),
                    name + "#" + method.getName()
                        + " is static: a class-form mixin may not declare static methods, "
                        + "Mixin fails with \"contains non-private static method\"");
            }
            // A mixin may extend its target to reach protected members, but a
            // non-private constructor would be copied into that target.
            java.lang.reflect.Constructor<?>[] constructors = mixin.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                // javac emits one implicit no-argument constructor; anything else
                // was written by hand and would be merged into the target.
                boolean implicitDefault = constructors.length == 1 && constructor.getParameterCount() == 0;
                assertTrue(implicitDefault || Modifier.isPrivate(constructor.getModifiers()),
                    name + " must not declare a non-private constructor to copy into the target");
            }
        }
    }

    @Test
    void accessorsAndInvokersAreAbstractPublicMembers() throws Exception {
        for (String name : clientMixins()) {
            Class<?> mixin = load(name);
            for (Method method : mixin.getDeclaredMethods()) {
                if (!method.getName().startsWith("inputbooster$")) continue;
                int modifiers = method.getModifiers();
                assertTrue(Modifier.isAbstract(modifiers),
                    name + "#" + method.getName() + " must be abstract so Mixin can wire it to the target");
                assertTrue(Modifier.isPublic(modifiers),
                    name + "#" + method.getName() + " must be public so callers in other packages can use it");
            }
        }
    }

    @Test
    void theAccessorsTheModNeedsAreRegistered() throws Exception {
        List<String> mixins = clientMixins();
        for (String required : List.of("GuiAccessor", "KeyMappingAccessor", "MinecraftClientAccessor")) {
            assertTrue(mixins.contains(required),
                required + " must be registered in inputbooster.mixins.json, found " + mixins);
        }
    }

    @Test
    void injectedMembersFailLoudlyByDefault() throws Exception {
        JsonObject injectors = config().getAsJsonObject("injectors");
        assertNotNull(injectors, "injectors block must be declared");
        assertEquals(1, injectors.get("defaultRequire").getAsInt(),
            "a missing injection must fail the game instead of silently disabling the feature");
        assertTrue(config().get("required").getAsBoolean(),
            "the mixin config itself must stay required");
    }
}