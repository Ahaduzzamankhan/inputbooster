package dev.inputbooster.feature;

import dev.inputbooster.InputBoosterConfig;
import dev.inputbooster.InputBoosterMod;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class KeybindConflictDetector {
    private boolean checked;

    public void tick(Minecraft client) {
        if (checked || !InputBoosterConfig.isKeyConflictWarn() || client == null || client.options == null) return;
        checked = true;
        Object keys = readField(client.options, "keyMappings");
        if (keys == null) keys = readField(client.options, "allKeys");
        if (keys == null || !keys.getClass().isArray()) return;

        Map<String, String> seen = new HashMap<>();
        for (int i = 0; i < Array.getLength(keys); i++) {
            Object key = Array.get(keys, i);
            String bound = callString(key, "getKeyModifierAndCode");
            if (bound == null) bound = callString(key, "getTranslatedKeyMessage");
            String name = callString(key, "getName");
            if (bound == null || bound.isBlank() || bound.contains("unknown")) continue;
            String previous = seen.putIfAbsent(bound, name == null ? "unknown" : name);
            if (previous != null && InputBoosterMod.eventLog != null) {
                InputBoosterMod.eventLog.add("Key conflict: " + previous + " and " + name + " use " + bound);
            }
        }
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String callString(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object value = m.invoke(target);
            return value == null ? null : value.toString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
