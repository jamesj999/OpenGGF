package com.openggf.configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves human-readable key name strings (e.g. "Q", "SPACE", "LEFT_SHIFT")
 * to GLFW integer key codes and vice versa. Uses reflection on
 * {@code org.lwjgl.glfw.GLFW} to build the mappings, so new keys added in
 * future LWJGL versions are picked up automatically.
 *
 * <p>Accepted input formats for {@link #resolve(String)}:
 * <ul>
 *   <li>{@code "Q"} — short key name</li>
 *   <li>{@code "GLFW_KEY_Q"} — full GLFW constant name (prefix stripped)</li>
 *   <li>{@code "KEY_Q"} — partial prefix (stripped)</li>
 *   <li>All lookups are case-insensitive</li>
 * </ul>
 */
public final class GlfwKeyNameResolver {

    private static final Logger LOGGER = Logger.getLogger(GlfwKeyNameResolver.class.getName());
    private static final String GLFW_KEY_PREFIX = "GLFW_KEY_";
    private static final String KEY_PREFIX = "KEY_";

    private static Map<String, Integer> nameToCode;
    private static Map<Integer, String> codeToName;

    private GlfwKeyNameResolver() {
    }

    /**
     * Resolves a key name string to its GLFW key code.
     *
     * @param name the key name (e.g. "Q", "SPACE", "GLFW_KEY_ENTER"); case-insensitive
     * @return the GLFW key code, or {@link OptionalInt#empty()} if unrecognised
     */
    public static OptionalInt resolve(String name) {
        if (name == null || name.isEmpty()) {
            return OptionalInt.empty();
        }
        ensureInitialised();
        String normalised = normalise(name);
        Integer code = nameToCode.get(normalised);
        return code != null ? OptionalInt.of(code) : OptionalInt.empty();
    }

    /**
     * Returns the short key name for a GLFW key code (e.g. 81 &rarr; "Q",
     * 32 &rarr; "SPACE"). Returns the numeric string if the code has no
     * known GLFW_KEY_* constant.
     */
    public static String nameOf(int keyCode) {
        ensureInitialised();
        String name = codeToName.get(keyCode);
        return name != null ? name : Integer.toString(keyCode);
    }

    private static String normalise(String name) {
        String upper = name.toUpperCase();
        if (upper.startsWith(GLFW_KEY_PREFIX)) {
            return upper.substring(GLFW_KEY_PREFIX.length());
        }
        if (upper.startsWith(KEY_PREFIX)) {
            return upper.substring(KEY_PREFIX.length());
        }
        return upper;
    }

    private static synchronized void ensureInitialised() {
        if (nameToCode != null) {
            return;
        }
        nameToCode = new HashMap<>();
        codeToName = new HashMap<>();

        try {
            Class<?> glfwClass = Class.forName("org.lwjgl.glfw.GLFW");
            for (Field field : glfwClass.getDeclaredFields()) {
                if (field.getType() == int.class
                        && Modifier.isPublic(field.getModifiers())
                        && Modifier.isStatic(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && field.getName().startsWith(GLFW_KEY_PREFIX)) {
                    String shortName = field.getName().substring(GLFW_KEY_PREFIX.length());
                    int code = field.getInt(null);
                    nameToCode.put(shortName, code);
                    // First constant wins for reverse lookup (avoids aliases)
                    codeToName.putIfAbsent(code, shortName);
                }
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.log(Level.WARNING, "Failed to build GLFW key name map via reflection", e);
        }
    }
}
