package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigTools {
    public void applyPreset(String preset) {
        String p = preset == null ? "balanced" : preset.toLowerCase();
        switch (p) {
            case "pvp" -> {
                InputBoosterConfig.setPollRateAutoMode(false);
                InputBoosterConfig.setPollRateHz(500);
                InputBoosterConfig.setMaxCps(20);
                InputBoosterConfig.setCpsMode("HUMANIZED");
            }
            case "low_fps" -> {
                InputBoosterConfig.setPollRateAutoMode(true);
                InputBoosterConfig.setMaxCps(16);
                InputBoosterConfig.setCpsMode("COOLDOWN");
            }
            case "debug" -> {
                InputBoosterConfig.setShowF3Info(true);
                InputBoosterConfig.setDebugMode(true);
                InputBoosterConfig.setEventLogEnabled(true);
            }
            default -> {
                InputBoosterConfig.setPollRateAutoMode(true);
                InputBoosterConfig.setMaxCps(18);
                InputBoosterConfig.setCpsMode("FIXED");
            }
        }
        InputBoosterConfig.save();
    }

    public void exportConfig(Path target) throws IOException {
        if (target == null) throw new IllegalArgumentException("export target must not be null");
        InputBoosterConfig.save();
        Path parent = target.getParent();
        // Path.of("backup.properties").getParent() is null — creating
        // directories for it used to throw a NullPointerException.
        if (parent != null) Files.createDirectories(parent);
        Files.copy(Path.of("config", "inputbooster.properties"), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void importConfig(Path source) throws IOException {
        if (source == null) throw new IllegalArgumentException("import source must not be null");
        if (!Files.isRegularFile(source)) throw new IOException("Not a file: " + source);
        Path dir = Path.of("config");
        Files.createDirectories(dir);
        Files.copy(source, dir.resolve("inputbooster.properties"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        InputBoosterConfig.load();
    }
}
