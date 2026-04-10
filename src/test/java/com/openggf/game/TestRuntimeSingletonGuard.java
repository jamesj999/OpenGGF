package com.openggf.game;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

/**
 * Guard test that scans production source for direct {@code Foo.getInstance()} calls
 * to the 10 runtime-owned managers. All access should go through {@link GameServices}
 * or {@code services()} (ObjectServices).
 * <p>
 * Allowed call sites:
 * <ul>
 *   <li>The manager class itself (its own {@code getInstance()} declaration)</li>
 *   <li>{@link RuntimeManager} — explicit runtime composition root</li>
 *   <li>Bootstrap/reset infrastructure that runs before a {@link GameRuntime} exists</li>
 *   <li>Leaf infrastructure wrappers that intentionally cache direct manager access</li>
 *   <li>{@link GameServices} — runtime facade / engine-global access</li>
 *   <li>{@code DefaultObjectServices} — object-service facade over an explicit runtime</li>
 *   <li>{@code GraphicsManager} — legitimate fallback for Camera/FadeManager</li>
 * </ul>
 */
public class TestRuntimeSingletonGuard {

    /** The 10 runtime-owned manager class names whose getInstance() is restricted. */
    private static final Set<String> RUNTIME_MANAGERS = Set.of(
            "Camera",
            "LevelManager",
            "SpriteManager",
            "GameStateManager",
            "TimerManager",
            "FadeManager",
            "CollisionSystem",
            "TerrainCollisionManager",
            "WaterSystem",
            "ParallaxManager"
    );

    /** Files allowed to call getInstance() on runtime-owned managers. */
    private static final Set<String> ALLOWED_FILES = Set.of(
            "RuntimeManager.java",
            "GameServices.java",
            "AbstractObjectInstance.java",  // static helpers wrapping singletons for leaf classes
            "AbstractObjectRegistry.java",
            "GraphicsManager.java",
            "Engine.java",
            "GameLoop.java",
            "GameModuleRegistry.java",
            "AbstractLevelEventManager.java",
            "AbstractLevelInitProfile.java",
            "DebugRenderer.java",
            "AbstractPlayableSprite.java",
            "HudRenderManager.java",
            "ObjectManager.java",
            "RingManager.java",
            "GroundSensor.java",
            "PlayableSpriteMovement.java",
            "SpindashCameraTimer.java",
            "LazyMappingHolder.java",
            "Sonic2ObjectRegistry.java",
            "AizIntroArtLoader.java",
            "AizIntroBoosterChild.java",
            "AizIntroPaletteCycler.java",
            "AizIntroTerrainSwap.java"
    );

    /** Pattern matching e.g. Camera.getInstance() or LevelManager.getInstance() */
    private static final Pattern SINGLETON_CALL = Pattern.compile(
            "\\b(\\w+)\\.getInstance\\(\\)");

    private static final Pattern STRICT_GAME_SERVICES_NULL_CHECK = Pattern.compile(
            "GameServices\\.(camera|level|gameState|timers|rng|fade|sprites|collision|terrainCollision|parallax|water|bonusStage)\\(\\)\\s*(==|!=)\\s*null");

    @Test
    public void productionCodeDoesNotCallRuntimeManagerSingletons() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            // Running from a context where source isn't available — skip gracefully
            return;
        }

        List<String> violations = new ArrayList<>();

        Files.walkFileTree(srcMain, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }

                String fileName = file.getFileName().toString();

                // Skip the manager classes themselves (they declare getInstance())
                if (RUNTIME_MANAGERS.stream().anyMatch(m -> fileName.equals(m + ".java"))) {
                    return FileVisitResult.CONTINUE;
                }

                // Skip explicitly allowed files
                if (ALLOWED_FILES.contains(fileName)) {
                    return FileVisitResult.CONTINUE;
                }

                String content = Files.readString(file);
                String[] lines = content.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    // Skip comments
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                        continue;
                    }
                    Matcher matcher = SINGLETON_CALL.matcher(line);
                    while (matcher.find()) {
                        String className = matcher.group(1);
                        if (RUNTIME_MANAGERS.contains(className)) {
                            String relativePath = srcMain.relativize(file).toString();
                            violations.add(String.format(
                                    "%s:%d — %s.getInstance()",
                                    relativePath, i + 1, className));
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            fail("Found " + violations.size() + " direct getInstance() call(s) to runtime-owned managers.\n"
                    + "Use GameServices.foo() or services().foo() instead:\n  "
                    + violations.stream().collect(Collectors.joining("\n  ")));
        }
    }

    @Test
    public void productionCodeDoesNotNullCheckStrictGameServicesAccessors() throws IOException {
        Path srcMain = findSourceRoot();
        if (srcMain == null) {
            return;
        }

        List<String> violations = new ArrayList<>();

        Files.walkFileTree(srcMain, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (!file.toString().endsWith(".java")) {
                    return FileVisitResult.CONTINUE;
                }

                SourceScanText source = sourceWithoutCommentOnlyLines(Files.readAllLines(file));
                Matcher matcher = STRICT_GAME_SERVICES_NULL_CHECK.matcher(source.text);
                while (matcher.find()) {
                    String relativePath = srcMain.relativize(file).toString();
                    violations.add(String.format(
                            "%s:%d — GameServices.%s() %s null",
                            relativePath, source.lineAt(matcher.start()), matcher.group(1), matcher.group(2)));
                }

                return FileVisitResult.CONTINUE;
            }
        });

        if (!violations.isEmpty()) {
            fail("Found " + violations.size() + " null check(s) against strict GameServices accessors.\n"
                    + "Use GameServices.<name>OrNull() or remove the null check:\n  "
                    + violations.stream().collect(Collectors.joining("\n  ")));
        }
    }

    private Path findSourceRoot() {
        // Try common locations relative to working directory
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path srcMain = cwd.resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            return srcMain;
        }
        // Try parent (in case CWD is a submodule)
        srcMain = cwd.getParent().resolve("src/main/java");
        if (Files.isDirectory(srcMain)) {
            return srcMain;
        }
        return null;
    }

    private static SourceScanText sourceWithoutCommentOnlyLines(List<String> lines) {
        StringBuilder source = new StringBuilder();
        List<Integer> lineByOffset = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            String scannedLine = (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*"))
                    ? ""
                    : line;
            source.append(scannedLine);
            for (int j = 0; j < scannedLine.length(); j++) {
                lineByOffset.add(i + 1);
            }
            source.append('\n');
            lineByOffset.add(i + 1);
        }
        return new SourceScanText(source.toString(), lineByOffset);
    }

    private static final class SourceScanText {
        private final String text;
        private final List<Integer> lineByOffset;

        private SourceScanText(String text, List<Integer> lineByOffset) {
            this.text = text;
            this.lineByOffset = lineByOffset;
        }

        private int lineAt(int offset) {
            if (lineByOffset.isEmpty()) {
                return 1;
            }
            return lineByOffset.get(Math.min(offset, lineByOffset.size() - 1));
        }
    }
}
