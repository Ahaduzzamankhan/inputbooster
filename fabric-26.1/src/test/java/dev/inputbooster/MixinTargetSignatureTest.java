package dev.inputbooster;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks every mixin hook against the Minecraft it will actually run on.
 *
 * <p>A wrong handler signature is a fatal injection error at class-transform
 * time: the client dies during startup before any of this mod's own code runs.
 * {@code require = 0} does not help, because it only tolerates a missing target,
 * not a handler whose parameters disagree with the target. That is exactly how
 * 4.1.0-alpha shipped a {@code keyPress} hook written against the old 1.21
 * signature and crashed on launch, so the signature is now a build-time check.
 *
 * <p>{@code @Mixin} has CLASS retention and is invisible to reflection, so the
 * mixin side is read with ASM from the compiled class file. Targets are loaded
 * without initialisation, since only their declared members are needed.
 */
class MixinTargetSignatureTest {

    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
    private static final String CALLBACK_RETURNABLE =
            "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";

    @Test
    void everyHookMatchesTheMethodItNames() throws Exception {
        List<String> problems = new ArrayList<>();

        for (String simpleName : configuredMixins()) {
            String internalName = "dev/inputbooster/mixin/" + simpleName;
            ClassNode mixin = readClass(internalName);
            if (mixin == null) {
                problems.add(simpleName + ": mixin class not found on the test classpath");
                continue;
            }

            List<String> targets = mixinTargets(mixin);
            if (targets.isEmpty()) {
                problems.add(simpleName + ": no @Mixin target");
                continue;
            }

            for (String targetName : targets) {
                Class<?> target;
                try {
                    target = Class.forName(targetName.replace('/', '.'), false,
                            MixinTargetSignatureTest.class.getClassLoader());
                } catch (ClassNotFoundException e) {
                    problems.add(simpleName + ": target " + targetName
                            + " is not on the test classpath, so its hooks cannot be checked");
                    continue;
                }
                checkHooks(simpleName, target, mixin, problems);
            }
        }

        assertTrue(problems.isEmpty(),
                () -> "Mixin hooks do not match Minecraft. The client would crash during startup:\n  "
                        + String.join("\n  ", problems));
    }

    private static void checkHooks(String mixinName, Class<?> target, ClassNode mixin,
                                   List<String> problems) {
        for (MethodNode method : mixin.methods) {
            AnnotationNode inject = annotation(method, INJECT);
            if (inject != null) {
                checkInject(mixinName, target, method, inject, problems);
                continue;
            }
            AnnotationNode accessor = annotation(method, ACCESSOR);
            if (accessor != null) {
                checkAccessor(mixinName, target, accessor, problems);
                continue;
            }
            AnnotationNode invoker = annotation(method, INVOKER);
            if (invoker != null) {
                checkInvoker(mixinName, target, method, invoker, problems);
            }
        }
    }

    private static void checkInject(String mixinName, Class<?> target, MethodNode handler,
                                    AnnotationNode inject, List<String> problems) {
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        String where = mixinName + "#" + handler.name;

        if (handlerArgs.length == 0) {
            problems.add(where + ": a handler needs the target parameters plus a callback parameter");
            return;
        }
        String last = handlerArgs[handlerArgs.length - 1].getInternalName();
        if (!last.equals(CALLBACK) && !last.equals(CALLBACK_RETURNABLE)) {
            problems.add(where + ": last parameter is " + last
                    + ", not CallbackInfo or CallbackInfoReturnable");
            return;
        }

        String[] targetParams = new String[handlerArgs.length - 1];
        for (int i = 0; i < targetParams.length; i++) {
            targetParams[i] = handlerArgs[i].getClassName();
        }

        List<String> names = annotationStrings(inject, "method");
        if (names.isEmpty()) {
            problems.add(where + ": @Inject names no method");
            return;
        }

        for (String name : names) {
            if (name.contains("(")) {
                // Descriptors are remapped by Fabric; validating the parameter
                // types is enough and keeps this test readable.
                name = name.substring(0, name.indexOf('('));
            }
            Method match = findMethod(target, name, targetParams);
            if (match == null) {
                problems.add(where + ": " + target.getName() + " has no " + name
                        + " with parameters " + String.join(", ", targetParams)
                        + "; target declares: " + describe(target, name));
            }
        }
    }

    private static void checkAccessor(String mixinName, Class<?> target, AnnotationNode accessor,
                                      List<String> problems) {
        List<String> names = annotationStrings(accessor, "value");
        if (names.isEmpty()) {
            return;
        }
        String field = names.get(0);
        for (Class<?> type = target; type != null; type = type.getSuperclass()) {
            for (var declared : type.getDeclaredFields()) {
                if (declared.getName().equals(field)) {
                    return;
                }
            }
        }
        problems.add(mixinName + ": @Accessor(\"" + field + "\") but " + target.getName()
                + " has no such field");
    }

    private static void checkInvoker(String mixinName, Class<?> target, MethodNode handler,
                                     AnnotationNode invoker, List<String> problems) {
        List<String> names = annotationStrings(invoker, "value");
        if (names.isEmpty()) {
            return;
        }
        Type[] handlerArgs = Type.getArgumentTypes(handler.desc);
        String[] params = new String[handlerArgs.length];
        for (int i = 0; i < params.length; i++) {
            params[i] = handlerArgs[i].getClassName();
        }
        Method match = findMethod(target, names.get(0), params);
        if (match == null) {
            problems.add(mixinName + ": @Invoker(\"" + names.get(0) + "\") but " + target.getName()
                    + " has no matching method; declares: " + describe(target, names.get(0)));
            return;
        }
        if (!match.getReturnType().getName().equals(Type.getReturnType(handler.desc).getClassName())) {
            problems.add(mixinName + ": @Invoker(\"" + names.get(0) + "\") returns "
                    + match.getReturnType().getName() + " but the mixin method returns "
                    + Type.getReturnType(handler.desc).getClassName());
        }
    }

    private static Method findMethod(Class<?> target, String name, String[] params) {
        for (Class<?> type = target; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] actual = method.getParameterTypes();
                if (actual.length != params.length) continue;
                boolean same = true;
                for (int i = 0; i < actual.length; i++) {
                    if (!actual[i].getName().equals(params[i])) {
                        same = false;
                        break;
                    }
                }
                if (same) return method;
            }
        }
        return null;
    }

    /** Every overload of {@code name} on the target, for a readable failure. */
    private static String describe(Class<?> target, String name) {
        List<String> found = new ArrayList<>();
        for (Class<?> type = target; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                found.add(name + Type.getMethodDescriptor(method));
            }
        }
        return found.isEmpty() ? "<none>" : String.join(", ", found);
    }

    private static List<String> configuredMixins() throws IOException {
        try (InputStream in = MixinTargetSignatureTest.class
                .getResourceAsStream("/inputbooster.mixins.json")) {
            if (in == null) {
                throw new IOException("inputbooster.mixins.json is not on the test classpath");
            }
            JsonObject config = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray client = config.getAsJsonArray("client");
            List<String> names = new ArrayList<>();
            for (JsonElement element : client) {
                names.add(element.getAsString());
            }
            return names;
        }
    }

    private static ClassNode readClass(String internalName) throws IOException {
        try (InputStream in = MixinTargetSignatureTest.class
                .getResourceAsStream("/" + internalName + ".class")) {
            if (in == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                    | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<String> mixinTargets(ClassNode mixin) {
        AnnotationNode annotation = mixin.visibleAnnotations == null ? null
                : mixin.visibleAnnotations.stream()
                        .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                        .findFirst().orElse(null);
        if (annotation == null && mixin.invisibleAnnotations != null) {
            annotation = mixin.invisibleAnnotations.stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                    .findFirst().orElse(null);
        }
        if (annotation == null) return List.of();

        List<String> targets = new ArrayList<>();
        List<String> keys = List.of("value", "targets");
        for (String key : keys) {
            Object value = annotationValue(annotation, key);
            if (value instanceof Type type) {
                targets.add(type.getInternalName());
            } else if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element instanceof Type type) {
                        targets.add(type.getInternalName());
                    } else if (element instanceof String string && key.equals("targets")) {
                        targets.add(string.replace('.', '/'));
                    }
                }
            }
        }
        return targets;
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        List<AnnotationNode>[] groups = new List[] {method.visibleAnnotations,
                method.invisibleAnnotations};
        for (List<AnnotationNode> group : groups) {
            if (group == null) continue;
            for (AnnotationNode node : group) {
                if (node.desc.equals(descriptor)) return node;
            }
        }
        return null;
    }

    private static List<String> annotationStrings(AnnotationNode node, String key) {
        Object value = annotationValue(node, key);
        List<String> strings = new ArrayList<>();
        if (value instanceof String string) {
            strings.add(string);
        } else if (value instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof String string) strings.add(string);
            }
        }
        return strings;
    }

    private static Object annotationValue(AnnotationNode node, String key) {
        if (node.values == null) return null;
        for (int i = 0; i + 1 < node.values.size(); i += 2) {
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        }
        return null;
    }
}
