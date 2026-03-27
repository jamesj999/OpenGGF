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
 * Guards against calling {@code services()} before {@code ObjectServices}
 * have been injected.
 * <p>
 * {@code services()} depends on injection which happens AFTER construction
 * when using {@code addDynamicObject()} or {@code spawnDynamicObject()}.
 * <p>
 * Four guards:
 * <ol>
 *   <li>Detect {@code spawnDynamicObject(new X(...))} call sites.</li>
 *   <li>Hard-fail if any object constructor calls {@code services()}.</li>
 *   <li>Detect {@code addDynamicObject(new X(...))} where X's constructor
 *       calls {@code services()}.</li>
 *   <li>Detect method calls on freshly constructed objects before
 *       registration, where the method calls {@code services()}.</li>
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
     * Hard-fails if any object constructor calls {@code services()}.
     * <p>
     * Constructors run BEFORE {@code addDynamicObject()} or
     * {@code spawnDynamicObject()} can inject {@code ObjectServices}.
     * The only safe patterns are {@code spawnChild()} or
     * {@code setConstructionContext()}, but both are easy to forget at
     * call sites. The simplest universal rule: defer {@code services()}
     * to lazy init or the first {@code update()} call.
     */
    @Test
    public void constructors_mustNotCallServices() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        List<String> violations = new ArrayList<>();

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

                                findServicesInConstructors(content, className, fileName, violations);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        if (!violations.isEmpty()) {
            fail("Object constructors must not call services(). "
                    + "Defer to lazy init (ensureInitialized pattern) or first update() call.\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    /**
     * Scans ALL source for {@code addDynamicObject(new X(...))} where X's
     * constructor calls {@code services()}. This catches call sites outside
     * the object packages (e.g. event managers, level controllers).
     * <p>
     * {@code addDynamicObject()} injects services AFTER construction, so
     * if the constructor calls {@code services()}, it will crash.
     */
    @Test
    public void addDynamicObject_shouldNotConstructObjectThatNeedsServicesInConstructor() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        // Step 1: Build set of class names whose constructors call services()
        Set<String> classesCallingServicesInCtor = new HashSet<>();
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

                                List<String> dummy = new ArrayList<>();
                                findServicesInConstructors(content, className, fileName, dummy);
                                if (!dummy.isEmpty()) {
                                    classesCallingServicesInCtor.add(className);
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        // Step 2: Scan ALL source for addDynamicObject(new X(...)) where X
        // calls services() in its constructor
        Pattern inlineNew = Pattern.compile(
                "addDynamicObject\\(\\s*new\\s+(\\w+)\\s*\\(");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> allFiles = Files.walk(srcMain)) {
            allFiles.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            Matcher m = inlineNew.matcher(content);
                            while (m.find()) {
                                String className = m.group(1);
                                if (classesCallingServicesInCtor.contains(className)) {
                                    String fileName = path.getFileName().toString();
                                    violations.add(fileName + ": addDynamicObject(new "
                                            + className + "(...)) — " + className
                                            + " calls services() in constructor. "
                                            + "Remove services() from the constructor "
                                            + "(use lazy init) or use spawnChild()");
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }

        if (!violations.isEmpty()) {
            fail("addDynamicObject(new X(...)) where X calls services() in constructor.\n"
                    + "addDynamicObject() injects services AFTER construction, "
                    + "so services() will throw IllegalStateException.\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    /**
     * Detects the pattern where a method is called on a freshly constructed
     * object BEFORE it is passed to {@code addDynamicObject()} or
     * {@code spawnDynamicObject()}, and that method calls {@code services()}.
     * <p>
     * Example of the dangerous pattern:
     * <pre>
     *   X obj = new X(...);
     *   obj.initialize();              // CRASH — services not injected yet
     *   manager.addDynamicObject(obj);
     * </pre>
     * <p>
     * Field assignments ({@code obj.field = value}) are safe and ignored.
     */
    @Test
    public void methodCallsBeforeRegistration_mustNotCallServices() throws IOException {
        Path srcMain = Path.of("src/main/java");
        if (!Files.isDirectory(srcMain)) {
            return;
        }

        // Step 1: Build a map of className -> set of methods that call services()
        Map<String, Set<String>> methodsCallingServices = new HashMap<>();
        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = srcMain.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> files = Files.walk(pkgDir)) {
                files.filter(p -> p.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                String content = Files.readString(path);
                                String className = path.getFileName().toString().replace(".java", "");
                                Set<String> methods = findMethodsCallingServices(content, className);
                                if (!methods.isEmpty()) {
                                    methodsCallingServices.put(className, methods);
                                }
                            } catch (IOException ignored) {
                            }
                        });
            }
        }

        // Step 2: Scan ALL source for the pattern:
        //   varName = new ClassName(...);
        //   varName.method(...);
        //   ... addDynamicObject(varName) or spawnDynamicObject(varName)
        //
        // We look for method calls (not field assignments) on a variable
        // between its construction and registration.

        // Match: ClassName varName = new ClassName(...)
        // or:    var varName = new ClassName(...)
        Pattern construction = Pattern.compile(
                "(?:\\w+(?:<[^>]+>)?|var)\\s+(\\w+)\\s*=\\s*new\\s+(\\w+)\\s*\\(");
        // Match: varName.methodName(
        Pattern methodCall = Pattern.compile(
                "(\\w+)\\.(\\w+)\\s*\\(");
        // Match: addDynamicObject(varName) or spawnDynamicObject(varName)
        Pattern registration = Pattern.compile(
                "(?:addDynamicObject|spawnDynamicObject)\\s*\\(\\s*(\\w+)\\s*\\)");

        List<String> violations = new ArrayList<>();

        try (Stream<Path> allFiles = Files.walk(srcMain)) {
            allFiles.filter(p -> p.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();
                            String[] lines = content.split("\n");

                            // Track constructed-but-not-yet-registered variables
                            // Key: varName, Value: className
                            Map<String, String> pending = new HashMap<>();

                            for (int i = 0; i < lines.length; i++) {
                                String line = lines[i];

                                // Check for construction
                                Matcher ctorMatch = construction.matcher(line);
                                while (ctorMatch.find()) {
                                    pending.put(ctorMatch.group(1), ctorMatch.group(2));
                                }

                                // Check for registration — removes from pending
                                Matcher regMatch = registration.matcher(line);
                                while (regMatch.find()) {
                                    pending.remove(regMatch.group(1));
                                }

                                // Check for method calls on pending objects
                                Matcher callMatch = methodCall.matcher(line);
                                while (callMatch.find()) {
                                    String varName = callMatch.group(1);
                                    String method = callMatch.group(2);
                                    String className = pending.get(varName);
                                    if (className != null) {
                                        Set<String> dangerous = methodsCallingServices.get(className);
                                        if (dangerous != null && dangerous.contains(method)) {
                                            violations.add(fileName + ":" + (i + 1)
                                                    + ": " + varName + "." + method
                                                    + "() called before addDynamicObject — "
                                                    + method + "() calls services() in "
                                                    + className);
                                        }
                                    }
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }

        if (!violations.isEmpty()) {
            fail("Method calling services() invoked on object before registration.\n"
                    + "addDynamicObject/spawnDynamicObject injects services AFTER "
                    + "the call, so services() will throw IllegalStateException.\n"
                    + "Move the method call after registration, or defer to "
                    + "ensureInitialized() in update().\n\n  "
                    + String.join("\n  ", violations));
        }
    }

    /**
     * Returns the set of non-constructor method names in a class that call
     * {@code services()}, either directly or transitively through other
     * methods in the same class.
     */
    private static Set<String> findMethodsCallingServices(String content, String className) {
        // Parse all methods: name -> body
        Map<String, String> methodBodies = new HashMap<>();
        Pattern methodDecl = Pattern.compile(
                "(?:public|protected|private|)\\s+"
                        + "(?:static\\s+)?(?:final\\s+)?(?:synchronized\\s+)?"
                        + "\\S+\\s+(\\w+)\\s*\\([^)]*\\)\\s*"
                        + "(?:throws\\s+[^{]+)?\\{");

        Matcher m = methodDecl.matcher(content);
        while (m.find()) {
            String methodName = m.group(1);
            if (methodName.equals(className)) continue; // skip constructors

            int start = m.end();
            int braceDepth = 1;
            int end = start;
            while (end < content.length() && braceDepth > 0) {
                char c = content.charAt(end);
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
                end++;
            }
            methodBodies.put(methodName, content.substring(start, end));
        }

        // Seed: methods that directly call services()
        Pattern servicesCall = Pattern.compile("(?<![.\\w])services\\(\\)");
        Set<String> callers = new HashSet<>();
        for (var entry : methodBodies.entrySet()) {
            if (servicesCall.matcher(entry.getValue()).find()) {
                callers.add(entry.getKey());
            }
        }

        // Transitive closure: methods that call methods already in the set
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var entry : methodBodies.entrySet()) {
                if (callers.contains(entry.getKey())) continue;
                for (String caller : callers) {
                    // Check if this method's body calls a known services-calling method
                    if (Pattern.compile("(?<![.\\w])" + Pattern.quote(caller) + "\\s*\\(")
                            .matcher(entry.getValue()).find()) {
                        callers.add(entry.getKey());
                        changed = true;
                        break;
                    }
                }
            }
        }

        return callers;
    }

    /**
     * Finds {@code services()} calls inside constructors of the given class.
     * Appends human-readable descriptions to the violations list.
     */
    private static void findServicesInConstructors(
            String content, String className, String fileName,
            List<String> violations) {
        Pattern ctorPattern = Pattern.compile(
                "(?:public|protected|private)\\s+" + Pattern.quote(className)
                        + "\\s*\\([^)]*\\)\\s*\\{");
        Matcher ctorMatcher = ctorPattern.matcher(content);

        while (ctorMatcher.find()) {
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
            // Match unqualified services() — not obj.services()
            if (Pattern.compile("(?<![.\\w])services\\(\\)").matcher(ctorBody).find()) {
                violations.add(fileName + ": " + className
                        + " calls services() in constructor");
            }
        }
    }
}
