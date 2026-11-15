package dev.inputbooster;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ordinary code must never reference a class from the mixin package.
 *
 * <p>Mixin loads the classes of the mixin package with its own class loader
 * and refuses to let anything else touch them:
 *
 * <pre>
 * IllegalClassLoadError: Illegal classload request for dev.inputbooster.mixin.GuiAccessor.
 * Mixin is defined in inputbooster.mixins.json and cannot be referenced directly
 * </pre>
 *
 * <p>The failure happened while the overlay was drawn, so the overlay never
 * appeared and the client reported a mixin transformation failure on every
 * frame. The accessors are duck interfaces in {@code dev.inputbooster.access}
 * that the mixin classes implement, and this test makes sure nothing reaches
 * back into the mixin package.
 */
class MixinPackageIsolationTest {

    private static final String MIXIN_PACKAGE = "dev/inputbooster/mixin/";

    private static Path compiledClasses() {
        return Path.of("build", "classes", "java", "main");
    }

    @Test
    void noClassOutsideTheMixinPackageReferencesIt() throws IOException {
        Path root = compiledClasses();
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = root.relativize(path).toString().replace('\\', '/');
                if (name.startsWith(MIXIN_PACKAGE)) continue;

                String content = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.ISO_8859_1);
                if (content.contains(MIXIN_PACKAGE)) {
                    offenders.add(name);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                () -> "These classes reference the mixin package, which Mixin refuses to load: "
                        + String.join(", ", offenders));
    }
}
