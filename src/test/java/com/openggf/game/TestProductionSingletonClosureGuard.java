package com.openggf.game;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestProductionSingletonClosureGuard {

    private static final List<String> FORBIDDEN_SINGLETONS = List.of(
            "Sonic1SwitchManager.getInstance(",
            "Sonic1ConveyorState.getInstance(",
            "Sonic1LevelEventManager.getInstance(",
            "Sonic2LevelEventManager.getInstance(",
            "Sonic2SpecialStageManager.getInstance(",
            "Sonic2SpecialStageSpriteDebug.getInstance(",
            "Sonic3kLevelEventManager.getInstance(",
            "Sonic3kTitleCardManager.getInstance(",
            "Sonic3kSpecialStageManager.getInstance(",
            "Sonic3kZoneRegistry.getInstance(",
            "Sonic2ZoneRegistry.getInstance(",
            "Sonic1ZoneRegistry.getInstance("
    );

    private static final List<String> FORBIDDEN_PROCESS_SINGLETONS = List.of(
            "GraphicsManager.getInstance(",
            "AudioManager.getInstance(",
            "RomManager.getInstance(",
            "SonicConfigurationService.getInstance(",
            "PerformanceProfiler.getInstance(",
            "DebugOverlayManager.getInstance(",
            "DebugRenderer.getInstance(",
            "DebugRenderer.current(",
            "PlaybackDebugManager.getInstance(",
            "CrossGameFeatureProvider.getInstance(",
            "Engine.getInstance(",
            "Engine.current("
    );

    private static final String ENGINE_SERVICES_BOOTSTRAP_EXCEPTION =
            "com/openggf/game/EngineServices.java";
    private static final String LEGACY_BOOTSTRAP_BRIDGE = "EngineServices.fromLegacySingletonsForBootstrap(";
    private static final List<String> LEGACY_BOOTSTRAP_BRIDGE_ALLOWLIST = List.of(
            "com/openggf/game/EngineServices.java",
            "com/openggf/game/RuntimeManager.java"
    );

    @Test
    public void productionCodeDoesNotUseClosedGameSpecificSingletons() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<String> violations = new ArrayList<>();
        Files.walk(srcMain)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanFile(srcMain, path, violations));

        if (!violations.isEmpty()) {
            fail("Found closed game-specific singleton access in production code:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    public void productionCodeDoesNotUseForbiddenProcessSingletonsOutsideEngineServices() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<String> violations = new ArrayList<>();
        Files.walk(srcMain)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !ENGINE_SERVICES_BOOTSTRAP_EXCEPTION.equals(
                        srcMain.relativize(path).toString().replace('\\', '/')))
                .forEach(path -> scanFile(srcMain, path, violations, FORBIDDEN_PROCESS_SINGLETONS));

        if (!violations.isEmpty()) {
            fail("Found forbidden process singleton access in production code:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    public void productionCodeDoesNotUseLegacyBootstrapBridgeOutsideAllowlist() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<String> violations = new ArrayList<>();
        Files.walk(srcMain)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !LEGACY_BOOTSTRAP_BRIDGE_ALLOWLIST.contains(
                        srcMain.relativize(path).toString().replace('\\', '/')))
                .forEach(path -> scanFile(srcMain, path, violations, List.of(LEGACY_BOOTSTRAP_BRIDGE)));

        if (!violations.isEmpty()) {
            fail("Found legacy bootstrap bridge usage outside the allowlist:\n  "
                    + String.join("\n  ", violations));
        }
    }

    @Test
    public void detectsForbiddenProcessSingletonSplitAcrossLines() {
        List<String> violations = scanSourceText("sample/Split.java", """
                package sample;

                class Split {
                    void render() {
                        GraphicsManager
                                .getInstance();
                    }
                }
                """, FORBIDDEN_PROCESS_SINGLETONS);

        assertEquals(List.of("sample/Split.java:5 - GraphicsManager.getInstance("), violations);
    }

    @Test
    public void detectsLegacyBootstrapBridgeSplitAcrossLines() {
        List<String> violations = scanSourceText("sample/Bootstrap.java", """
                package sample;

                class Bootstrap {
                    void wire() {
                        EngineServices
                                .fromLegacySingletonsForBootstrap();
                    }
                }
                """, List.of(LEGACY_BOOTSTRAP_BRIDGE));

        assertEquals(List.of("sample/Bootstrap.java:5 - " + LEGACY_BOOTSTRAP_BRIDGE), violations);
    }

    @Test
    public void ignoresForbiddenPatternsInsideComments() {
        List<String> violations = scanSourceText("sample/Comments.java", """
                package sample;

                class Comments {
                    // Engine.getInstance();
                    /* DebugRenderer.current(); */
                    String s = "GraphicsManager.getInstance(";
                }
                """, FORBIDDEN_PROCESS_SINGLETONS);

        assertTrue(violations.isEmpty());
    }

    @Test
    public void detectsAliasSingletonAccessPatterns() {
        List<String> violations = scanSourceText("sample/Alias.java", """
                package sample;

                class Alias {
                    void use() {
                        Engine.current();
                        DebugRenderer
                                .current();
                    }
                }
                """, FORBIDDEN_PROCESS_SINGLETONS);

        assertEquals(2, violations.size());
        assertTrue(violations.contains("sample/Alias.java:5 - Engine.current("));
        assertTrue(violations.contains("sample/Alias.java:6 - DebugRenderer.current("));
    }

    private static void scanFile(Path srcMain, Path file, List<String> violations) {
        scanFile(srcMain, file, violations, FORBIDDEN_SINGLETONS);
    }

    private static void scanFile(Path srcMain, Path file, List<String> violations, List<String> forbiddenSingletons) {
        try {
            String relative = srcMain.relativize(file).toString().replace('\\', '/');
            String source = Files.readString(file);
            violations.addAll(scanSourceText(relative, source, forbiddenSingletons));
        } catch (IOException ignored) {
        }
    }

    static List<String> scanSourceText(String relative, String source, List<String> forbiddenSingletons) {
        String stripped = stripComments(source);
        List<String> violations = new ArrayList<>();
        for (String forbidden : forbiddenSingletons) {
            Matcher matcher = compileForbiddenPattern(forbidden).matcher(stripped);
            while (matcher.find()) {
                violations.add(relative + ":" + lineNumberForOffset(stripped, matcher.start()) + " - " + forbidden);
            }
        }
        return violations;
    }

    static String stripComments(String source) {
        StringBuilder stripped = new StringBuilder(source.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        boolean inChar = false;
        boolean escaping = false;

        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    stripped.append('\n');
                } else if (current == '\r') {
                    stripped.append('\r');
                } else {
                    stripped.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    stripped.append("  ");
                    i++;
                    inBlockComment = false;
                } else if (current == '\n' || current == '\r') {
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                continue;
            }

            if (inString) {
                if (current == '\n' || current == '\r') {
                    stripped.append(current);
                } else if (current == '"') {
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (inChar) {
                if (current == '\n' || current == '\r') {
                    stripped.append(current);
                } else if (current == '\'') {
                    stripped.append(current);
                } else {
                    stripped.append(' ');
                }
                if (escaping) {
                    escaping = false;
                } else if (current == '\\') {
                    escaping = true;
                } else if (current == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (current == '/' && next == '/') {
                stripped.append("  ");
                i++;
                inLineComment = true;
                continue;
            }

            if (current == '/' && next == '*') {
                stripped.append("  ");
                i++;
                inBlockComment = true;
                continue;
            }

            if (current == '"') {
                inString = true;
                stripped.append(current);
                continue;
            }

            if (current == '\'') {
                inChar = true;
                stripped.append(current);
                continue;
            }

            stripped.append(current);
        }
        return stripped.toString();
    }

    private static Pattern compileForbiddenPattern(String forbidden) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < forbidden.length(); i++) {
            char current = forbidden.charAt(i);
            if (Character.isWhitespace(current)) {
                continue;
            }
            if (Character.isJavaIdentifierPart(current)) {
                regex.append(Pattern.quote(String.valueOf(current)));
            } else {
                regex.append("\\s*")
                        .append(Pattern.quote(String.valueOf(current)))
                        .append("\\s*");
            }
        }
        return Pattern.compile(regex.toString(), Pattern.MULTILINE);
    }

    private static int lineNumberForOffset(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static Path findSourceRoot() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path srcMain = cwd.resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            return srcMain;
        }
        Path parent = cwd.getParent();
        if (parent == null) {
            return null;
        }
        srcMain = parent.resolve("src/main/java");
        return Files.isDirectory(srcMain) ? srcMain : null;
    }
}
