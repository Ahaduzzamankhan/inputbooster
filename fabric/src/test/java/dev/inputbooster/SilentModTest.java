package dev.inputbooster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mod must be invisible while the game runs.
 *
 * <p>These tests read the shipped sources rather than the running client, so
 * they can assert the property directly: no HUD, no overlay, no chat, no toast,
 * no title, no render submission, and no gameplay hook. Together with the
 * navigation and setting-reachability checks, this is the contract for
 * "install it and play normally".
 */
class SilentModTest {

    private static final Path MAIN = Path.of("..", "src", "main", "java");

    private static List<Path> mainSources() throws Exception {
        try (var stream = Files.walk(MAIN)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * Source with comments removed. The modules explain in prose which vanilla
     * APIs they deliberately avoid, and a scanner must not read its own
     * documentation as a violation.
     */
    private static String code(Path path) throws Exception {
        String withoutBlocks = read(path).replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder out = new StringBuilder(withoutBlocks.length());
        for (String line : withoutBlocks.split("\\R")) {
            int comment = line.indexOf("//");
            out.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
        }
        return out.toString();
    }

    @Test
    void nothingIsRenderedWhileTheGameRuns() throws Exception {
        List<String> offences = new ArrayList<>();
        for (Path source : mainSources()) {
            String text = code(source);
            for (String forbidden : List.of(
                "GuiRenderState", "GuiTextRenderState", "GuiGraphicsExtractor",
                "GuiGraphics", "blit(")) {
                if (text.contains(forbidden)) {
                    offences.add(source.getFileName() + " -> " + forbidden);
                }
            }
        }
        assertEquals(List.of(), offences,
            "a performance mod must draw nothing; found " + offences);
    }

    @Test
    void nothingIsSaidToThePlayer() throws Exception {
        List<String> offences = new ArrayList<>();
        for (Path source : mainSources()) {
            String text = code(source);
            for (String forbidden : List.of(
                "sendOverlayMessage", "sendSystemMessage", "displayClientMessage",
                "Toast", "showToast", "setTitle", "setOverlayMessage",
                "System.out.print")) {
                if (text.contains(forbidden)) {
                    offences.add(source.getFileName() + " -> " + forbidden);
                }
            }
        }
        assertEquals(List.of(), offences,
            "a silent mod must not talk to the player; found " + offences);
    }

    @Test
    void noGameplayHookRemains() throws Exception {
        List<String> offences = new ArrayList<>();
        for (Path source : mainSources()) {
            String text = code(source);
            for (String forbidden : List.of(
                "startAttack", "startUseItem", "doAttack",
                "KeyMapping", "InputConstants")) {
                if (text.contains(forbidden)) {
                    offences.add(source.getFileName() + " -> " + forbidden);
                }
            }
        }
        assertEquals(List.of(), offences,
            "the gameplay and polling code must be gone; found " + offences);
    }

    @Test
    void theOnlyThreadIsTheDiskWorker() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Path source : mainSources()) {
            String text = code(source);
            if (text.contains("new Thread(") && !text.contains("InputBooster-Disk")) {
                offenders.add(source.getFileName().toString());
            }
        }
        assertEquals(List.of(), offenders,
            "exactly one background thread is allowed, the disk worker");
    }

    @Test
    void noDirectGraphicsApiIsUsed() throws Exception {
        List<String> offences = new ArrayList<>();
        for (Path source : mainSources()) {
            String text = code(source);
            for (String forbidden : List.of(
                "org.lwjgl", "GlStateManager", "RenderSystem", "GL11", "GL20", "GL30")) {
                if (text.contains(forbidden)) offences.add(source.getFileName() + " -> " + forbidden);
            }
        }
        assertEquals(List.of(), offences,
            "drawing must go through Minecraft's abstractions or not happen at all");
    }

    @Test
    void exactlyOneMixinRemainsAndItIsRegistered() throws Exception {
        JsonObject config;
        try (InputStream in = Files.newInputStream(
                Path.of("build", "resources", "main", "inputbooster.mixins.json"))) {
            config = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var client = config.getAsJsonArray("client");
        List<String> registered = new ArrayList<>();
        client.forEach(element -> registered.add(element.getAsString()));
        assertEquals(List.of("OptionsScreenMixin"), registered,
            "the only mixin left is the options-screen entry");

        Path mixinPackage = Path.of("..", "src", "main", "java", "dev", "inputbooster", "mixin");
        List<String> onDisk;
        try (var stream = Files.list(mixinPackage)) {
            onDisk = stream.map(p -> p.getFileName().toString().replace(".java", "")).sorted().toList();
        }
        List<String> sortedRegistered = new ArrayList<>(registered);
        sortedRegistered.sort(null);
        assertEquals(sortedRegistered, onDisk,
            "every class in the mixin package must be a registered mixin");
    }

    @Test
    void theOptionsEntryStillOpensTheSettings() throws Exception {
        String mixin = read(MAIN.resolve("dev/inputbooster/mixin/OptionsScreenMixin.java"));
        assertTrue(mixin.contains("new InputBoosterScreen"),
            "Options must still lead to the InputBooster screen");
        assertTrue(mixin.contains("addToOptionsGrid"),
            "the entry joins the vanilla options grid as a real cell");
        assertFalse(mixin.contains(".bounds("),
            "the entry must not be positioned by hard-coded coordinates");
    }

    @Test
    void everySettingIsReachableFromTheScreen() throws Exception {
        String screen = read(MAIN.resolve("dev/inputbooster/screen/InputBoosterScreen.java"));
        List<String> missing = new ArrayList<>();
        for (String option : List.of("cpu", "memory", "gpu", "disk", "chunk",
            "input", "adaptive", "diagnostics", "adaptive_interval", "write_debounce")) {
            if (!screen.contains("inputbooster.option." + option)) {
                missing.add(option);
            }
        }
        assertEquals(List.of(), missing,
            "every setting must be reachable from the settings screen; missing " + missing);
    }

    @Test
    void everyOptionLabelIsTranslated() throws Exception {
        String screen = read(MAIN.resolve("dev/inputbooster/screen/InputBoosterScreen.java"));
        JsonObject lang;
        try (InputStream in = Files.newInputStream(
                Path.of("..", "src", "main", "resources", "assets", "inputbooster", "lang", "en_us.json"))) {
            lang = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        List<String> missing = new ArrayList<>();
        for (String key : lang.keySet()) {
            if (!screen.contains(key) && !key.startsWith("options.inputbooster")
                && !key.startsWith("inputbooster.section")
                && !key.startsWith("inputbooster.value")) {
                missing.add(key);
            }
        }
        assertEquals(List.of(), missing, "unused translation keys: " + missing);
    }
}