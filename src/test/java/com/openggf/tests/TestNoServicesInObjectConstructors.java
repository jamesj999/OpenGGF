package com.openggf.tests;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * Guards against calling AbstractObjectInstance.services() inside constructors.
 * Services are injected by ObjectManager after object creation, so constructor-time
 * services() calls can throw IllegalStateException.
 */
public class TestNoServicesInObjectConstructors {

    private static final Path MAIN_JAVA = Paths.get("src/main/java");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w.]*)\\s*;");
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("\\b(?:public|protected|private|abstract|final|static|\\s)*class\\s+([A-Za-z_][\\w]*)\\s+extends\\s+([A-Za-z_][\\w]*)\\b");

    @Test
    public void noServicesCallsInAnyObjectSubclassConstructor() throws IOException {
        List<ClassInfo> classes = discoverClasses();
        Map<String, List<ClassInfo>> bySimpleName = classes.stream()
                .collect(Collectors.groupingBy(ClassInfo::simpleName));

        List<String> violations = new ArrayList<>();
        for (ClassInfo info : classes) {
            if (!isObjectSubclass(info, bySimpleName, new HashSet<>())) {
                continue;
            }

            Pattern ctorPattern = Pattern.compile(
                    "\\b(public|protected|private)\\s+" + Pattern.quote(info.simpleName()) + "\\s*\\([^)]*\\)\\s*\\{");
            Matcher ctorMatcher = ctorPattern.matcher(info.sanitizedSource());

            while (ctorMatcher.find()) {
                int bodyStart = ctorMatcher.end();
                int bodyEnd = findMatchingBrace(info.sanitizedSource(), bodyStart);
                String body = info.sanitizedSource().substring(bodyStart, bodyEnd);
                if (Pattern.compile("\\bservices\\s*\\(").matcher(body).find()) {
                    int line = lineNumber(info.sanitizedSource(), ctorMatcher.start());
                    violations.add(info.path() + ":" + line + " (" + info.simpleName() + ")");
                }
            }
        }

        assertTrue("Constructor-time services() calls found:\n" + String.join("\n", violations), violations.isEmpty());
    }

    private static List<ClassInfo> discoverClasses() throws IOException {
        List<ClassInfo> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            List<Path> javaFiles = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                String raw = Files.readString(file);
                String sanitized = sanitizeSource(raw);
                String pkg = extractPackage(raw);

                Matcher classMatcher = CLASS_PATTERN.matcher(sanitized);
                while (classMatcher.find()) {
                    String simpleName = classMatcher.group(1);
                    String superName = classMatcher.group(2);
                    String fqcn = pkg.isEmpty() ? simpleName : pkg + "." + simpleName;
                    classes.add(new ClassInfo(file, fqcn, simpleName, superName, sanitized));
                }
            }
        }
        return classes;
    }

    private static String extractPackage(String source) {
        Matcher m = PACKAGE_PATTERN.matcher(source);
        return m.find() ? m.group(1) : "";
    }

    private static boolean isObjectSubclass(
            ClassInfo info,
            Map<String, List<ClassInfo>> bySimpleName,
            Set<String> visiting) {
        if ("AbstractObjectInstance".equals(info.superName())) {
            return true;
        }

        String key = info.fqcn();
        if (!visiting.add(key)) {
            return false;
        }

        List<ClassInfo> candidates = bySimpleName.get(info.superName());
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }

        for (ClassInfo candidate : candidates) {
            if (isObjectSubclass(candidate, bySimpleName, visiting)) {
                return true;
            }
        }
        return false;
    }

    private static int findMatchingBrace(String source, int bodyStart) {
        int depth = 1;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Unmatched constructor braces");
    }

    private static int lineNumber(String source, int index) {
        int lines = 1;
        for (int i = 0; i < index && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private static String sanitizeSource(String source) {
        StringBuilder out = new StringBuilder(source.length());
        final int normal = 0;
        final int lineComment = 1;
        final int blockComment = 2;
        final int stringLiteral = 3;
        final int charLiteral = 4;
        int state = normal;

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (state == normal) {
                if (c == '/' && next == '/') {
                    out.append(' ').append(' ');
                    i++;
                    state = lineComment;
                } else if (c == '/' && next == '*') {
                    out.append(' ').append(' ');
                    i++;
                    state = blockComment;
                } else if (c == '"') {
                    out.append(' ');
                    state = stringLiteral;
                } else if (c == '\'') {
                    out.append(' ');
                    state = charLiteral;
                } else {
                    out.append(c);
                }
                continue;
            }

            if (state == lineComment) {
                if (c == '\n') {
                    out.append('\n');
                    state = normal;
                } else {
                    out.append(' ');
                }
                continue;
            }

            if (state == blockComment) {
                if (c == '*' && next == '/') {
                    out.append(' ').append(' ');
                    i++;
                    state = normal;
                } else if (c == '\n') {
                    out.append('\n');
                } else {
                    out.append(' ');
                }
                continue;
            }

            if (state == stringLiteral) {
                if (c == '\\') {
                    out.append(' ');
                    if (i + 1 < source.length()) {
                        out.append(' ');
                        i++;
                    }
                } else if (c == '"') {
                    out.append(' ');
                    state = normal;
                } else if (c == '\n') {
                    out.append('\n');
                } else {
                    out.append(' ');
                }
                continue;
            }

            if (state == charLiteral) {
                if (c == '\\') {
                    out.append(' ');
                    if (i + 1 < source.length()) {
                        out.append(' ');
                        i++;
                    }
                } else if (c == '\'') {
                    out.append(' ');
                    state = normal;
                } else if (c == '\n') {
                    out.append('\n');
                } else {
                    out.append(' ');
                }
            }
        }

        return out.toString();
    }

    private record ClassInfo(
            Path path,
            String fqcn,
            String simpleName,
            String superName,
            String sanitizedSource) {
    }
}
