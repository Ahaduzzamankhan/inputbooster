package dev.inputbooster;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.util.version.SemanticVersionImpl;
import net.fabricmc.loader.impl.util.version.VersionPredicateParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the Minecraft version dependency declared in
 * {@code fabric.mod.json}.
 *
 * Fabric loader 0.19.x evaluates a bracket range such as {@code [26.2,26.3)}
 * incorrectly for two-component Minecraft versions: the installed 26.2 does not
 * match its own declared range and the game refuses to start with
 * "requires version [26.2,26.3) of 'Minecraft', but only the wrong version is
 * present: 26.2!". This test parses the predicate that actually ships in the
 * generated metadata and checks it against the real loader implementation.
 */
class FabricDependencyRangeTest {

    private static JsonObject metadata() throws Exception {
        Path generated = Path.of("build", "resources", "main", "fabric.mod.json");
        try (InputStream in = Files.newInputStream(generated)) {
            return JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        }
    }

    @Test
    void declaredMinecraftDependencyAcceptsTheTargetVersion() throws Exception {
        JsonObject depends = metadata().getAsJsonObject("depends");
        String predicateText = depends.get("minecraft").getAsString();
        assertNotNull(predicateText, "minecraft dependency must be declared");

        VersionPredicate predicate = VersionPredicateParser.parse(predicateText);

        String target = propertiesValue("minecraft_version");

        assertTrue(predicate.test(new SemanticVersionImpl(target, false)),
            "declared predicate \"" + predicateText + "\" must accept Minecraft " + target);
    }

    @Test
    void declaredMinecraftDependencyRejectsOtherVersions() throws Exception {
        String predicateText = metadata().getAsJsonObject("depends").get("minecraft").getAsString();
        VersionPredicate predicate = VersionPredicateParser.parse(predicateText);
        String target = propertiesValue("minecraft_version");

        // Whatever the target is, a clearly different Minecraft version must not match.
        assertFalse(predicate.test(new SemanticVersionImpl("1.21.1", false)),
            "predicate must not match an unrelated Minecraft version");
        assertFalse(predicate.test(new SemanticVersionImpl("0.9", false)),
            "predicate must not match a much older Minecraft version");
        assertEquals(target, propertiesValue("minecraft_version"));
    }

    @Test
    void bracketRangesAreNotUsedForTwoComponentVersions() throws Exception {
        String predicateText = metadata().getAsJsonObject("depends").get("minecraft").getAsString();
        assertFalse(predicateText.startsWith("[") && predicateText.endsWith(")"),
            "bracket ranges break two-component Minecraft versions on Fabric loader 0.19.x: " + predicateText);
        assertTrue(predicateText.contains(">=") && predicateText.contains("<"),
            "use the space separated '>=x <y' comparison form: " + predicateText);
    }

    @Test
    void loaderVersionAndApiAreDeclared() throws Exception {
        JsonObject depends = metadata().getAsJsonObject("depends");
        assertTrue(depends.has("fabricloader"));
        assertTrue(depends.has("fabric-api"));
        assertTrue(depends.has("java"));
    }

    private static String propertiesValue(String key) throws Exception {
        // The build passes the resolved value (honouring -P overrides) as a
        // system property; fall back to gradle.properties.
        String resolved = System.getProperty("inputbooster.minecraftVersion");
        if ("minecraft_version".equals(key) && resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        Path file = Path.of("gradle.properties");
        for (String line : Files.readAllLines(file)) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1).trim();
            }
        }
        throw new IllegalStateException("missing " + key);
    }
}