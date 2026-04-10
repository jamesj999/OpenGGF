package com.openggf.game;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
            "PlaybackDebugManager.getInstance(",
            "CrossGameFeatureProvider.getInstance(",
            "Engine.getInstance("
    );

    private static final List<String> PROCESS_SINGLETON_GUARD_FILES = List.of(
            "com/openggf/graphics/BatchedPatternRenderer.java",
            "com/openggf/graphics/InstancedPatternRenderer.java",
            "com/openggf/graphics/PatternRenderCommand.java",
            "com/openggf/graphics/GLCommand.java",
            "com/openggf/level/render/PatternSpriteRenderer.java",
            "com/openggf/level/render/BackgroundRenderer.java",
            "com/openggf/sprites/render/PlayerSpriteRenderer.java",
            "com/openggf/audio/LWJGLAudioBackend.java",
            "com/openggf/audio/smps/SmpsSequencer.java"
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
    public void task5ProcessServiceFilesDoNotUseForbiddenProcessSingletons() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<String> violations = new ArrayList<>();
        for (String relative : PROCESS_SINGLETON_GUARD_FILES) {
            Path file = srcMain.resolve(relative);
            if (Files.isRegularFile(file)) {
                scanFile(srcMain, file, violations, FORBIDDEN_PROCESS_SINGLETONS);
            }
        }

        if (!violations.isEmpty()) {
            fail("Found forbidden process singleton access in Task 5 production files:\n  "
                    + String.join("\n  ", violations));
        }
    }

    private static void scanFile(Path srcMain, Path file, List<String> violations) {
        scanFile(srcMain, file, violations, FORBIDDEN_SINGLETONS);
    }

    private static void scanFile(Path srcMain, Path file, List<String> violations, List<String> forbiddenSingletons) {
        try {
            List<String> lines = Files.readAllLines(file);
            String relative = srcMain.relativize(file).toString().replace('\\', '/');
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String trimmed = line.trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                    continue;
                }
                for (String forbidden : forbiddenSingletons) {
                    if (line.contains(forbidden)) {
                        violations.add(relative + ":" + (i + 1) + " - " + forbidden);
                    }
                }
            }
        } catch (IOException ignored) {
        }
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
