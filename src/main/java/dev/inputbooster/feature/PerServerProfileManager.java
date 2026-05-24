package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class PerServerProfileManager {
    private static final Path PATH = Path.of("config", "inputbooster_server_profiles.properties");
    private final Properties bindings = new Properties();
    private String lastServerId = "";

    public void load() {
        try {
            if (Files.exists(PATH)) {
                try (var in = Files.newInputStream(PATH)) {
                    bindings.load(in);
                }
            }
        } catch (Exception e) {
            InputBoosterMod.LOGGER.warn("[PerServerProfile] Failed to load bindings: {}", e.getMessage());
        }
    }

    public void bind(String serverId, String profile) {
        if (serverId == null || serverId.isBlank() || profile == null || profile.isBlank()) return;
        bindings.setProperty(serverId.toLowerCase(), profile);
        persist();
    }

    public void tick(Minecraft client) {
        if (!InputBoosterConfig.isPerServerProfiles() || InputBoosterMod.profileManager == null) return;
        String serverId = detectServerId(client);
        if (serverId.equals(lastServerId)) return;
        lastServerId = serverId;
        String profile = bindings.getProperty(serverId.toLowerCase());
        if (profile != null && InputBoosterMod.profileManager.loadProfile(profile, client)
            && InputBoosterMod.eventLog != null) {
            InputBoosterMod.eventLog.add("Profile auto-switched for " + serverId + " -> " + profile);
        }
    }

    private void persist() {
        try {
            Files.createDirectories(PATH.getParent());
            try (var out = Files.newOutputStream(PATH)) {
                bindings.store(out, "InputBooster per-server profile bindings");
            }
        } catch (Exception e) {
            InputBoosterMod.LOGGER.warn("[PerServerProfile] Failed to save bindings: {}", e.getMessage());
        }
    }

    private static String detectServerId(Minecraft client) {
        if (client == null) return "singleplayer";
        Object entry = call(client, "getCurrentServer");
        if (entry == null) entry = read(client, "currentServer");
        if (entry == null) return client.hasSingleplayerServer() ? "singleplayer" : "unknown";
        Object address = read(entry, "ip");
        if (address == null) address = read(entry, "address");
        return address == null ? "unknown" : address.toString();
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object read(Object target, String field) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }
}
