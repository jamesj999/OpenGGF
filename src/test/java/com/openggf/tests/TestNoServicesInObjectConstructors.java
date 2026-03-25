package com.openggf.tests;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.fail;

/**
 * Guards against the antipattern of creating objects with {@code new} then passing
 * them to {@code spawnDynamicObject()}, when the object's constructor calls
 * {@code services()}.
 * <p>
 * {@code spawnDynamicObject()} does NOT set the CONSTRUCTION_CONTEXT ThreadLocal,
 * so if the constructor calls {@code services()}, it will throw
 * {@code IllegalStateException} at runtime.
 * <p>
 * The safe pattern is {@code spawnChild(() -> new Foo(...))} which sets the
 * ThreadLocal before invoking the constructor.
 * <p>
 * This test has two parts:
 * <ol>
 *   <li>Detect {@code spawnDynamicObject(new X(...))} call sites — these are
 *       always suspicious and should use {@code spawnChild()} instead.</li>
 *   <li>Detect {@code services()} calls inside constructors — these are only
 *       safe when the object is created through ObjectManager or
 *       {@code spawnChild()}.</li>
 * </ol>
 *
 * @see com.openggf.level.objects.AbstractObjectInstance#spawnDynamicObject
 * @see com.openggf.level.objects.AbstractObjectInstance#spawnChild
 */
public class TestNoServicesInObjectConstructors {

    /** Packages containing object instance classes to scan. */
    private static final String[] OBJECT_PACKAGES = {
            "com/openggf/game/sonic1/objects",
            "com/openggf/game/sonic2/objects",
            "com/openggf/game/sonic3k/objects",
            "com/openggf/level/objects",
    };

    /**
     * Objects whose constructors do NOT call services(), so
     * {@code spawnDynamicObject(new X(...))} is safe for them.
     * <p>
     * If a class is added here, add a comment explaining why its constructor
     * is guaranteed not to call services().
     */
    private static final Set<String> SAFE_FOR_SPAWN_DYNAMIC = Set.of(
            // Constructor only stores fields, no services() call
            "SongFadeTransitionInstance",
            "S3kSignpostInstance",
            "S3kSignpostStubChild",
            "S3kSignpostSparkleChild",
            "S3kResultsScreenObjectInstance",
            "Sonic3kSSEntryFlashObjectInstance",
            "AizTreeRevealControlObjectInstance"
    );

    /**
     * Detects {@code spawnDynamicObject(new X(...))} patterns in source code.
     * These are dangerous when the constructed object calls services() in its
     * constructor. Flags any that aren't in the known-safe set.
     */
    @Test
    public void spawnDynamicObject_shouldNotConstructInlineUnlessConstructorIsSafe() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        // Pattern: spawnDynamicObject(new SomeClassName(
        Pattern inlineNew = Pattern.compile(
                "spawnDynamicObject\\(\\s*new\\s+(\\w+)\\s*\\(");

        List<String> violations = new ArrayList<>();

        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                Matcher m = inlineNew.matcher(content);
                                while (m.find()) {
                                    String className = m.group(1);
                                    if (!SAFE_FOR_SPAWN_DYNAMIC.contains(className)) {
                                        String fileName = path.getFileName().toString();
                                        violations.add(fileName + ": spawnDynamicObject(new "
                                                + className + "(...)) — use spawnChild() instead, "
                                                + "or add to SAFE_FOR_SPAWN_DYNAMIC if constructor "
                                                + "does not call services()");
                                    }
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        if (!violations.isEmpty()) {
            fail("Unsafe spawnDynamicObject(new ...) patterns found.\n"
                    + "If the constructor calls services(), this will throw "
                    + "IllegalStateException at runtime.\n"
                    + "Use spawnChild(() -> new X(...)) instead:\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    /**
     * Detects constructors that call services() in object classes.
     * These are only safe when the object is created through ObjectManager
     * (factory pattern) or spawnChild() (which sets CONSTRUCTION_CONTEXT).
     * <p>
     * This is a heuristic scan — it looks for services() calls between
     * a constructor signature and its closing brace at the same indent level.
     */
    @Test
    public void constructors_callingServices_shouldBeDocumented() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        // Heuristic: find "services()" calls that appear after a constructor
        // declaration and before the next method/constructor declaration.
        // This catches the common case without full AST parsing.
        Pattern constructorPattern = Pattern.compile(
                "(?:public|protected|private)?\\s+\\w+\\s*\\([^)]*\\)\\s*\\{");

        List<String> classesWithServicesInConstructor = new ArrayList<>();

        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                String fileName = path.getFileName().toString();
                                String className = fileName.replace(".java", "");

                                // Find constructor(s) for this class
                                Pattern ctorPattern = Pattern.compile(
                                        "(?:public|protected|private)\\s+" + Pattern.quote(className)
                                                + "\\s*\\([^)]*\\)\\s*\\{");
                                Matcher ctorMatcher = ctorPattern.matcher(content);

                                while (ctorMatcher.find()) {
                                    // Find the body of this constructor (simplistic: up to next
                                    // method-level declaration or a reasonable chunk)
                                    int start = ctorMatcher.end();
                                    int braceDepth = 1;
                                    int end = start;
                                    while (end < content.length() && braceDepth > 0) {
                                        char c = content.charAt(end);
                                        if (c == '{') braceDepth++;
                                        else if (c == '}') braceDepth--;
                                        end++;
                                    }

                                    String ctorBody = content.substring(start, end);
                                    if (ctorBody.contains("services()")) {
                                        classesWithServicesInConstructor.add(
                                                className + " — calls services() in constructor; "
                                                        + "must be created via ObjectManager or spawnChild()");
                                    }
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        // This is informational — just log them so developers know which classes
        // require CONSTRUCTION_CONTEXT. The spawnDynamicObject test above catches
        // the actual antipattern at the call site.
        if (!classesWithServicesInConstructor.isEmpty()) {
            System.out.println("Objects calling services() in constructor (require "
                    + "ObjectManager or spawnChild() for construction):");
            classesWithServicesInConstructor.forEach(c -> System.out.println("  " + c));
        }
    }
}
