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
        InputBoosterConfig.save();
        Files.createDirectories(target.getParent());
        Files.copy(Path.of("config", "inputbooster.properties"), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public void importConfig(Path source) throws IOException {
        Files.createDirectories(Path.of("config"));
        Files.copy(source, Path.of("config", "inputbooster.properties"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        InputBoosterConfig.load();
    }
}
