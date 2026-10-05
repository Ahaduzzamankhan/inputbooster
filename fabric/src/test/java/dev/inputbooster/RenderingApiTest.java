package dev.inputbooster;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the Vulkan requirement.
 *
 * <p>Minecraft 26.x can drive its renderer through Vulkan as well as OpenGL.
 * Anything that reaches past Minecraft's own abstractions into LWJGL or the GL
 * bindings only exists on the OpenGL backend, so the HUD badge, the settings
 * screen and the in-game overlay all have to go through the render state that
 * Minecraft exposes ({@code GuiRenderState}, {@code GuiGraphicsExtractor},
 * {@code RenderPipeline}) instead.
 *
 * <p>These tests scan the shipped sources and the built jar rather than the
 * live game, because a GL call only fails once the Vulkan backend is chosen —
 * long after the automated build has finished.
 */
class RenderingApiTest {

    /**
     * Every one of these is OpenGL- or LWJGL-specific. Minecraft 26.2/26.3 on
     * the Vulkan backend has no such class on the render path, so a single
     * reference means the feature breaks (or crashes) for Vulkan players.
     */
    private static final List<String> FORBIDDEN = List.of(
        "org.lwjgl",
        "com.mojang.blaze3d.platform.GlStateManager",
        "com.mojang.blaze3d.systems.RenderSystem",
        "org.lwjgl.opengl.GL11",
        "org.lwjgl.opengl.GL20",
        "org.lwjgl.opengl.GL30",
        "GL11.gl",
        "GL20.gl",
        "GL30.gl"
    );

    /** Abstractions that are renderer agnostic and are what we must use instead. */
    private static final List<String> REQUIRED = List.of(
        "net/minecraft/client/renderer/state/gui/GuiRenderState",
        "net/minecraft/client/gui/GuiGraphicsExtractor",
        "org/joml/Matrix3x2f"
    );

    private static List<Path> mainSources() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("..", "src", "main", "java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<Path> platformSources() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static List<Path> allSources() throws Exception {
        List<Path> all = new ArrayList<>(mainSources());
        all.addAll(platformSources());
        return all;
    }

    @Test
    void noSourceTouchesLwjglOrRawGl() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path file : allSources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN) {
                if (text.contains(forbidden)) {
                    offenders.add(file.getFileName() + " references " + forbidden);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            "these call past Minecraft's rendering abstractions, which only exist on the OpenGL "
                + "backend and therefore break the Vulkan renderer: " + offenders);
    }

    @Test
    void noShippedClassReferencesLwjglOrGlStateManager() throws Exception {
        // Same rule, checked on the bytecode that actually ships rather than on
        // the sources: a reference can also be smuggled in through a helper.
        Path root = compiledMainClasses();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path classFile : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String pool = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
                for (String forbidden : List.of("org/lwjgl", "platform/GlStateManager", "systems/RenderSystem")) {
                    if (pool.contains(forbidden)) {
                        offenders.add(root.relativize(classFile) + " references " + forbidden);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
            "shipped classes must not reference the OpenGL/LWJGL bindings: " + offenders);
    }

    @Test
    void hudOverlayGoesThroughTheDeferredGuiRenderState() throws Exception {
        String hud = Files.readString(
            Path.of("..", "src", "main", "java", "dev", "inputbooster", "feature", "DebugOverlayManager.java"),
            StandardCharsets.UTF_8);
        assertTrue(hud.contains("GuiRenderState"),
            "the HUD badge must be submitted to GuiRenderState so the Vulkan backend can replay it");
        assertTrue(hud.contains("addText"),
            "the HUD badge must be enqueued as text, not drawn through a backend specific call");
    }

    @Test
    void jomlAndLwjglAreNotRedeclaredAsDependencies() throws Exception {
        // Minecraft ships both. Redeclaring JOML puts a second copy on the
        // classpath, and the copy that wins can differ from the one the game
        // already loaded; declaring LWJGL would put OpenGL bindings on the
        // compile classpath that the Vulkan path never uses.
        String buildScript = stripComments(Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8));
        assertFalse(buildScript.contains("org.joml"),
            "JOML comes from Minecraft; declaring it again risks a duplicate on the classpath");
        assertFalse(buildScript.contains("org.lwjgl"),
            "LWJGL comes from Minecraft and is OpenGL specific; the mod must not depend on it");

        String properties = stripComments(Files.readString(Path.of("gradle.properties"), StandardCharsets.UTF_8));
        assertFalse(properties.contains("joml_version"), "unused JOML version pin left behind");
        assertFalse(properties.contains("lwjgl_version"), "unused LWJGL version pin left behind");
    }

    /**
     * Drops {@code //} comments so the explanatory prose in build.gradle (which
     * names the libraries on purpose) is not mistaken for a declaration.
     */
    private static String stripComments(String script) {
        StringBuilder out = new StringBuilder(script.length());
        for (String line : script.split("\\R")) {
            int comment = line.indexOf("//");
            out.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }
        return out.toString();
    }

    @Test
    void inputPollingStaysRendererIndependent() throws Exception {
        // Input sampling must keep working on every backend: it may only read
        // window/input state, never render state.
        for (Path file : mainSources()) {
            String name = file.getFileName().toString();
            if (!name.endsWith(".java")) continue;
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!text.contains("class InputPollingThread") && !name.equals("RawKeyState.java")
                && !name.equals("KeyBindingSet.java")) {
                continue;
            }
            assertFalse(text.contains("GuiRenderState") || text.contains("GuiGraphicsExtractor"),
                name + " is on the input path and must not touch the renderer at all");
        }
    }

    @Test
    void everyRenderingEntryPointUsesAMinecraftAbstraction() throws Exception {
        // Sanity check that the supported abstractions really are the ones in
        // use, so the scan above cannot pass simply because the HUD was deleted.
        String hud = Files.readString(
            Path.of("..", "src", "main", "java", "dev", "inputbooster", "feature", "DebugOverlayManager.java"),
            StandardCharsets.UTF_8);
        String screen = Files.readString(
            Path.of("..", "src", "main", "java", "dev", "inputbooster", "screen", "InputBoosterScreen.java"),
            StandardCharsets.UTF_8);
        String combined = hud + screen;
        for (String required : REQUIRED) {
            String dotted = required.replace('/', '.');
            assertTrue(combined.contains(dotted),
                "expected the GUI/overlay code to draw through " + dotted);
        }
    }

    private static Path compiledMainClasses() throws Exception {
        Class<?> mixin = Class.forName("dev.inputbooster.mixin.OptionsScreenMixin", false,
            RenderingApiTest.class.getClassLoader());
        return Path.of(mixin.getProtectionDomain().getCodeSource().getLocation().toURI());
    }
}