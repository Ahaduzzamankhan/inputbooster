package dev.inputbooster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configuration for InputBooster.
 *
 * <p>Holds state only. All file access lives in
 * {@link dev.inputbooster.perf.DiskOptimizer}, which is what keeps the client
 * tick free of I/O: a settings change marks the file dirty and the disk
 * optimizer writes it later, on its own thread, coalescing a burst of changes
 * into a single write.
 *
 * <p>Config file: {@code config/inputbooster.properties}.
 */
public final class InputBoosterConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputBoosterMod.MOD_ID);

    public static final int CONFIG_VERSION = 400;

    private static final Path CONFIG_PATH = configDir().resolve("inputbooster.properties");

    // Module switches
    private static boolean cpuEnabled = true;
    private static boolean memoryEnabled = true;
    private static boolean gpuEnabled = true;
    private static boolean diskEnabled = true;
    private static boolean chunkEnabled = true;
    private static boolean inputEnabled = true;
    private static boolean adaptiveEnabled = true;

    // Engine tuning
    private static int adaptiveIntervalTicks = 600;
    private static int writeDebounceMs = 1000;
    private static boolean diagnostics = false;

    private static int configVersion = CONFIG_VERSION;

    private InputBoosterConfig() {
    }

    /**
     * Directory holding every InputBooster file. The
     * {@code inputbooster.configDir} system property overrides the default
     * {@code config} directory next to the game, which is what the tests use.
     */
    public static Path configDir() {
        String override = System.getProperty("inputbooster.configDir");
        if (override != null && !override.isBlank()) return Path.of(override);
        return Path.of("config");
    }

    public static Path path() {
        return CONFIG_PATH;
    }

    public static void load() {
        resetToDefaults();
        if (!Files.exists(CONFIG_PATH)) {
            saveNow();
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
        } catch (Exception e) {
            LOGGER.warn("Could not read {}, using defaults", CONFIG_PATH, e);
            return;
        }

        List<String> bad = new ArrayList<>();
        configVersion = parseInt(props, "config_version", CONFIG_VERSION, bad);

        cpuEnabled = parseBool(props, "cpu_enabled", cpuEnabled, bad);
        memoryEnabled = parseBool(props, "memory_enabled", memoryEnabled, bad);
        gpuEnabled = parseBool(props, "gpu_enabled", gpuEnabled, bad);
        diskEnabled = parseBool(props, "disk_enabled", diskEnabled, bad);
        chunkEnabled = parseBool(props, "chunk_enabled", chunkEnabled, bad);
        inputEnabled = parseBool(props, "input_enabled", inputEnabled, bad);
        adaptiveEnabled = parseBool(props, "adaptive_enabled", adaptiveEnabled, bad);
        adaptiveIntervalTicks = parseInt(props, "adaptive_interval_ticks", adaptiveIntervalTicks, bad);
        adaptiveIntervalTicks = clampInterval(adaptiveIntervalTicks);
        writeDebounceMs = parseInt(props, "write_debounce_ms", writeDebounceMs, bad);
        writeDebounceMs = clampDebounce(writeDebounceMs);
        diagnostics = parseBool(props, "diagnostics", diagnostics, bad);

        if (!bad.isEmpty()) {
            LOGGER.warn("Ignored {} unreadable value(s) in {}: {}", bad.size(), CONFIG_PATH, bad);
        }
        if (configVersion < CONFIG_VERSION) {
            LOGGER.info("Migrating config from version {} to {}", configVersion, CONFIG_VERSION);
            configVersion = CONFIG_VERSION;
        }
    }

    /** Writes the file on the calling thread. Used at shutdown and by the disk optimizer. */
    public static void saveNow() {
        Properties props = new Properties();
        props.setProperty("config_version", Integer.toString(CONFIG_VERSION));
        props.setProperty("cpu_enabled", Boolean.toString(cpuEnabled));
        props.setProperty("memory_enabled", Boolean.toString(memoryEnabled));
        props.setProperty("gpu_enabled", Boolean.toString(gpuEnabled));
        props.setProperty("disk_enabled", Boolean.toString(diskEnabled));
        props.setProperty("chunk_enabled", Boolean.toString(chunkEnabled));
        props.setProperty("input_enabled", Boolean.toString(inputEnabled));
        props.setProperty("adaptive_enabled", Boolean.toString(adaptiveEnabled));
        props.setProperty("adaptive_interval_ticks", Integer.toString(adaptiveIntervalTicks));
        props.setProperty("write_debounce_ms", Integer.toString(writeDebounceMs));
        props.setProperty("diagnostics", Boolean.toString(diagnostics));

        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "inputbooster", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                props.store(out, "InputBooster configuration");
            }
            try {
                Files.move(temp, CONFIG_PATH.toAbsolutePath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception noAtomic) {
                Files.move(temp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            configVersion = CONFIG_VERSION;
        } catch (Exception e) {
            LOGGER.warn("Could not write {}", CONFIG_PATH, e);
        }
    }

    public static void resetToDefaults() {
        cpuEnabled = true;
        memoryEnabled = true;
        gpuEnabled = true;
        diskEnabled = true;
        chunkEnabled = true;
        inputEnabled = true;
        adaptiveEnabled = true;
        adaptiveIntervalTicks = 600;
        writeDebounceMs = 1000;
        diagnostics = false;
        configVersion = CONFIG_VERSION;
    }

    public static boolean isCpuEnabled()          { return cpuEnabled; }
    public static boolean isMemoryEnabled()       { return memoryEnabled; }
    public static boolean isGpuEnabled()          { return gpuEnabled; }
    public static boolean isDiskEnabled()         { return diskEnabled; }
    public static boolean isChunkEnabled()        { return chunkEnabled; }
    public static boolean isInputEnabled()        { return inputEnabled; }
    public static boolean isAdaptiveEnabled()     { return adaptiveEnabled; }
    public static boolean isDiagnosticsEnabled()  { return diagnostics; }
    public static int getAdaptiveIntervalTicks()  { return adaptiveIntervalTicks; }
    public static int getWriteDebounceMs()        { return writeDebounceMs; }
    public static int getConfigVersion()          { return configVersion; }

    public static void setCpuEnabled(boolean v)         { cpuEnabled = v; }
    public static void setMemoryEnabled(boolean v)      { memoryEnabled = v; }
    public static void setGpuEnabled(boolean v)         { gpuEnabled = v; }
    public static void setDiskEnabled(boolean v)        { diskEnabled = v; }
    public static void setChunkEnabled(boolean v)       { chunkEnabled = v; }
    public static void setInputEnabled(boolean v)       { inputEnabled = v; }
    public static void setAdaptiveEnabled(boolean v)    { adaptiveEnabled = v; }
    public static void setDiagnosticsEnabled(boolean v) { diagnostics = v; }
    public static void setAdaptiveIntervalTicks(int v)  { adaptiveIntervalTicks = clampInterval(v); }
    public static void setWriteDebounceMs(int v)        { writeDebounceMs = clampDebounce(v); }

    public static int clampInterval(int ticks) {
        return Math.max(20, Math.min(6000, ticks));
    }

    public static int clampDebounce(int ms) {
        return Math.max(100, Math.min(10_000, ms));
    }

    private static boolean parseBool(Properties p, String key, boolean fallback, List<String> bad) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        String v = raw.trim();
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1") || v.equalsIgnoreCase("on")) return true;
        if (v.equalsIgnoreCase("false") || v.equalsIgnoreCase("no") || v.equals("0") || v.equalsIgnoreCase("off")) return false;
        bad.add(key);
        return fallback;
    }

    private static int parseInt(Properties p, String key, int fallback, List<String> bad) {
        String raw = p.getProperty(key);
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            bad.add(key);
            return fallback;
        }
    }
}