package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;

/**
 * Switches configuration profiles automatically when the player changes server.
 *
 * Server detection is intentionally defensive: the vanilla field/method names it
 * looks up have changed across Minecraft versions, so every reflective step is
 * isolated and a failure degrades to a stable fallback identifier instead of
 * throwing on the client tick. Identifiers are normalised (lower-cased host,
 * default port removed) so {@code Example.com}, {@code example.com:25565} and
 * {@code EXAMPLE.COM} all resolve to the same profile.
 */
public class PerServerProfileManager {
    private static final Path PATH = InputBoosterConfig.configDir().resolve("inputbooster_server_profiles.properties");
    /** Re-detect at most every 20 ticks (~1 s) instead of on every tick. */
    private static final int DETECT_INTERVAL_TICKS = 20;

    private final Properties bindings = new Properties();
    private String lastServerId = "";
    private int ticksSinceDetect = DETECT_INTERVAL_TICKS;
    private String cachedServerId = "singleplayer";
    private boolean reflectionReported = false;

    public void load() {
        try {
            if (Files.exists(PATH)) {
                try (var in = Files.newInputStream(PATH)) {
                    bindings.load(in);
                }
            }
        } catch (Exception e) {
            InputBoosterMod.LOGGER.warn("[ServerProfile] Failed to load bindings: {}", e.getMessage());
        }
    }

    public void bind(String serverId, String profile) {
        if (serverId == null || serverId.isBlank() || profile == null || profile.isBlank()) return;
        bindings.setProperty(normalize(serverId), profile);
        persist();
    }

    public void tick(Minecraft client) {
        if (!InputBoosterConfig.isPerServerProfiles() || InputBoosterMod.profileManager == null) return;
        if (++ticksSinceDetect >= DETECT_INTERVAL_TICKS) {
            ticksSinceDetect = 0;
            cachedServerId = detectServerId(client);
        }
        String serverId = cachedServerId;
        if (serverId.equals(lastServerId)) return;
        lastServerId = serverId;
        String profile = bindings.getProperty(normalize(serverId));
        if (profile != null && InputBoosterMod.profileManager.loadProfile(profile, client)
            && InputBoosterMod.eventLog != null) {
            InputBoosterMod.eventLog.add("Profile auto-switched for " + serverId + " -> " + profile);
        }
    }

    /**
     * Normalises a server identifier so the same server always maps to the same
     * profile regardless of case or an explicit default port.
     */
    public static String normalize(String raw) {
        if (raw == null) return "unknown";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return "unknown";
        if (value.startsWith("tcp://") || value.startsWith("ws://")) {
            int scheme = value.indexOf("://");
            value = value.substring(scheme + 3);
        }
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        if (value.endsWith(":25565")) {
            value = value.substring(0, value.length() - ":25565".length());
        }
        return value.isEmpty() ? "unknown" : value;
    }

    private void persist() {
        try {
            Path parent = PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "inputbooster_server_profiles", ".tmp");
            try (var out = Files.newOutputStream(temp)) {
                bindings.store(out, "InputBooster per-server profile bindings");
            }
            try {
                Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            InputBoosterMod.LOGGER.warn("[ServerProfile] Failed to save bindings: {}", e.getMessage());
        }
    }

    private String detectServerId(Minecraft client) {
        if (client == null) return "singleplayer";
        try {
            if (client.player == null) return "title";
            Object entry = call(client, "getCurrentServerEntry");
            if (entry == null) entry = read(client, "currentServerEntry");
            if (entry == null) {
                boolean singleplayer = callBoolean(client, "hasSingleplayerServer");
                return singleplayer ? "singleplayer" : "unknown";
            }
            Object address = read(entry, "address");
            if (address == null) address = call(entry, "address");
            return address == null ? "unknown" : address.toString();
        } catch (Throwable t) {
            reportReflectionFailure(t);
            return "unknown";
        }
    }

    private void reportReflectionFailure(Throwable t) {
        if (reflectionReported) return;
        reflectionReported = true;
        InputBoosterMod.LOGGER.warn("[ServerProfile] Server detection degraded ({}); "
            + "per-server profiles fall back to a stable identifier.", t.toString());
    }

    private static Object call(Object target, String method) {
        try {
            Method m = findMethod(target.getClass(), method);
            return m == null ? null : m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean callBoolean(Object target, String method) {
        try {
            Method m = findMethod(target.getClass(), method);
            return m != null && Boolean.TRUE.equals(m.invoke(target));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // Try the superclass.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object read(Object target, String field) {
        try {
            for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(field);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Try the superclass.
                }
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}