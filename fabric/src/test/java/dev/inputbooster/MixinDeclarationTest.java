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
 * <p>Mixin refuses to load any class in a declared mixin package unless that
 * class is a loadable mixin. {@code MixinInfo.getVariant} only returns
 * {@code ACCESSOR} — the loadable variant — for an <em>interface</em> whose
 * methods are all accessors. A class-form accessor is an ordinary mixin, so
 * casting to it at runtime makes the client die on the first render frame with
 * {@code IllegalClassLoadError: ... is in a defined mixin package
 * dev.inputbooster.mixin.* and cannot be referenced directly}.
 *
 * <p>The inverse rule also holds: an interface mixin carrying a non-accessor
 * method is an interface mixin, and Mixin then requires the target to be an
 * interface too. So injection mixins ({@code @Inject}) must stay classes, and
 * accessor mixins must be interfaces.
 */
class MixinDeclarationTest {

    private static final String PACKAGE = "dev.inputbooster.mixin.";

    /** Injection mixins carry {@code @Inject} methods, so they cannot be interfaces. */
    private static final List<String> INJECTION_MIXINS =
        List.of("GameTickMixin", "InGameHudMixin", "OptionsScreenMixin");

    /** Accessor mixins are cast at runtime, so they must be loadable interfaces. */
    private static final List<String> RUNTIME_ACCESSORS =
        List.of("GuiAccessor", "KeyMappingAccessor", "MinecraftClientAccessor");

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
    void injectionMixinsAreClassForm() throws Exception {
        for (String name : INJECTION_MIXINS) {
            Class<?> mixin = load(name);
            assertFalse(mixin.isInterface(),
                name + " uses @Inject, which makes it an interface mixin; Mixin then demands an "
                    + "interface target and aborts with \"@Mixin target type mismatch ... is not an interface\"");
        }
    }

    @Test
    void runtimeAccessorsAreLoadableInterfaceMixins() throws Exception {
        // The accessors are cast at runtime. Mixin only treats an interface
        // mixin as loadable (MixinInfo.getVariant -> Variant.ACCESSOR); the
        // class form is registered as non-loadable and referencing it throws
        // IllegalClassLoadError on the first frame.
        for (String name : RUNTIME_ACCESSORS) {
            Class<?> mixin = load(name);
            assertTrue(mixin.isInterface(),
                name + " is cast at runtime and must therefore be declared as an interface. "
                    + "A class-form accessor is not loadable, so Mixin aborts with IllegalClassLoadError.");
        }
    }

    @Test
    void accessorMixinsDeclareOnlyAccessorsAndInvokers() throws Exception {
        // Mixin classifies an interface mixin as ACCESSOR (the loadable variant)
        // only when every one of its methods is an accessor or invoker. A single
        // plain method flips it to INTERFACE, which then demands an interface
        // target and aborts with "@Mixin target type mismatch".
        for (String name : RUNTIME_ACCESSORS) {
            Class<?> mixin = load(name);
            Method[] methods = mixin.getDeclaredMethods();
            assertTrue(methods.length > 0, name + " must declare at least one accessor");
            for (Method method : methods) {
                boolean accessor = method.getAnnotation(org.spongepowered.asm.mixin.gen.Accessor.class) != null;
                boolean invoker = method.getAnnotation(org.spongepowered.asm.mixin.gen.Invoker.class) != null;
                assertTrue(accessor || invoker,
                    name + "#" + method.getName() + " carries neither @Accessor nor @Invoker, so Mixin "
                        + "treats the mixin as an interface mixin instead of a loadable accessor");
            }
        }
    }

    @Test
    void mixinsContainNothingButMergeableAccessors() throws Exception {
        // Mixin merges every member of a class-form mixin into the target and
        // aborts the game for anything it cannot merge. "contains non-private
        // static method" is the observed crash, so helpers must live outside.
        for (String name : INJECTION_MIXINS) {
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
        for (String required : RUNTIME_ACCESSORS) {
            assertTrue(mixins.contains(required),
                required + " must be registered in inputbooster.mixins.json, found " + mixins);
        }
    }

    /**
     * Regression test for the crash shipped in 3.1.6: every class compiled into
     * the declared mixin package must be a registered mixin. A plain helper
     * there can never be loadable, so referencing it throws
     * {@code IllegalClassLoadError} the first time it is used.
     */
    @Test
    void everyClassInTheMixinPackageIsARegisteredMixin() throws Exception {
        List<String> mixins = clientMixins();
        List<String> unregistered = new ArrayList<>();
        for (Path classFile : mixinPackageClasses()) {
            String simpleName = classFile.getFileName().toString().replace(".class", "");
            // Nested and anonymous classes belong to their enclosing mixin.
            if (simpleName.contains("$")) continue;
            if (!mixins.contains(simpleName)) unregistered.add(simpleName);
        }
        assertTrue(unregistered.isEmpty(),
            "dev.inputbooster.mixin.* is a defined mixin package, so every class in it must be "
                + "listed in inputbooster.mixins.json; unregistered: " + unregistered
                + " (anything else is unloaded by Mixin with IllegalClassLoadError)");
    }

    /**
     * Regression test for the same crash: a class outside the mixin package may
     * only reference mixin classes that Mixin can actually load, i.e. the
     * interface-form accessors. This scans the compiled bytecode of every
     * shipped class for references into {@code dev.inputbooster.mixin} and
     * fails if any of them is not loadable.
     */
    @Test
    void runtimeCodeOnlyReferencesLoadableMixins() throws Exception {
        Path root = compiledClassesRoot();
        String internalPrefix = PACKAGE.replace('.', '/');
        List<String> illegal = new ArrayList<>();

        for (Path classFile : allClassFiles(root)) {
            String relative = root.relativize(classFile).toString().replace('\\', '/');
            if (relative.startsWith(internalPrefix)) continue;
            // Byte-preserving decode: class names appear verbatim as UTF-8
            // constant-pool entries, in descriptors and in checkcast operands.
            String pool = new String(Files.readAllBytes(classFile),
                java.nio.charset.StandardCharsets.ISO_8859_1);
            for (String referenced : referencesInto(pool, internalPrefix)) {
                String dotted = referenced.replace('/', '.');
                if (!mixinsContain(dotted)) {
                    illegal.add(dotted + " (referenced from " + relative
                        + ", not a registered mixin)");
                    continue;
                }
                Class<?> target = Class.forName(dotted, false, MixinDeclarationTest.class.getClassLoader());
                if (!target.isInterface()) {
                    illegal.add(dotted + " (referenced from " + relative
                        + ") is a class-form mixin, which Mixin cannot load at runtime");
                }
            }
        }
        assertTrue(illegal.isEmpty(),
            "these classes are referenced at runtime but live in the defined mixin package "
                + "dev.inputbooster.mixin.*, where Mixin refuses to load anything that is not a "
                + "loadable accessor interface: " + illegal);
    }

    private static boolean mixinsContain(String dottedName) throws Exception {
        return clientMixins().contains(dottedName.substring(PACKAGE.length()));
    }

    /** Every {@code dev/inputbooster/mixin/X} name mentioned in a constant pool. */
    private static List<String> referencesInto(String constantPool, String internalPrefix) {
        List<String> found = new ArrayList<>();
        int from = 0;
        while (true) {
            int start = constantPool.indexOf(internalPrefix, from);
            if (start < 0) break;
            int end = start;
            while (end < constantPool.length() && isClassNameChar(constantPool.charAt(end))) end++;
            if (end > start + internalPrefix.length()) {
                String name = constantPool.substring(start, end);
                // Descriptors such as Ldev/inputbooster/mixin/X; already stop at ';'.
                if (!found.contains(name)) found.add(name);
            }
            from = end > start ? end : start + 1;
        }
        return found;
    }

    private static boolean isClassNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
            || c == '_' || c == '$' || c == '/';
    }

    /**
     * Directory holding the compiled mod classes (not the test classes).
     *
     * <p>Resolved from a registered mixin so it does not depend on Gradle's
     * output layout.
     */
    private static Path compiledClassesRoot() throws Exception {
        Class<?> anyMixin = load(clientMixins().get(0));
        return Path.of(anyMixin.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static List<Path> mixinPackageClasses() throws Exception {
        Path dir = compiledClassesRoot().resolve(PACKAGE.replace('.', '/'));
        assertTrue(Files.isDirectory(dir), "compiled mixin package not found at " + dir);
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".class")).toList();
        }
    }

    private static List<Path> allClassFiles(Path root) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> p.toString().endsWith(".class")).toList();
        }
    }

    private static String sourceOf(String simpleName) throws Exception {
        return Files.readString(Path.of("..", "src", "main", "java",
            "dev", "inputbooster", "mixin", simpleName + ".java"));
    }

    @Test
    void theHudInjectionIsRegisteredAndRequired() throws Exception {
        // The corner HUD is drawn from this single injection. It used to be
        // declared with require = 0, so a signature change made it vanish at
        // runtime and the badge silently stopped rendering with no error.
        List<String> mixins = clientMixins();
        assertTrue(mixins.contains("InGameHudMixin"),
            "InGameHudMixin must be registered in inputbooster.mixins.json, found " + mixins);

        String source = Files.readString(Path.of("..", "src", "main", "java",
            "dev", "inputbooster", "mixin", "InGameHudMixin.java"));
        // Match the @Inject annotation itself, not prose elsewhere in the file:
        // searching the whole source also matches the javadoc that explains it.
        String annotation = null;
        for (String line : source.split("\\R")) {
            if (line.contains("@Inject")) annotation = line;
        }
        assertNotNull(annotation, "InGameHudMixin must declare an @Inject");
        assertTrue(annotation.contains("require = 1"),
            "the HUD injection must use require = 1 so a mapping change fails loudly "
                + "instead of silently dropping the badge; found: " + annotation.trim());
    }

    @Test
    void noMixinTargetsAVanillaTypeItDoesNotModify() throws Exception {
        // DebugHudMixin targeted DebugScreenOverlay and declared nothing at
        // all: it cost an entry in the mixin config and reloaded the vanilla
        // debug-screen class for nothing.
        for (String name : clientMixins()) {
            String source = sourceOf(name);
            assertTrue(source.contains("@Inject") || source.contains("@Accessor")
                    || source.contains("@Invoker") || source.contains("@Shadow"),
                name + " is registered but declares no accessor, invoker or injection, "
                    + "so it only forces an extra class load at runtime");
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