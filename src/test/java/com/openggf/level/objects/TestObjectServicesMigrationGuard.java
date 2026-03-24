package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Migration guard: detects direct singleton {@code getInstance()} calls in
 * object instance classes that should use {@code services()} instead.
 * <p>
 * As objects are migrated to use {@code ObjectServices}, remove them from
 * the {@link #KNOWN_UNMIGRATED} set. New objects must not appear in this set —
 * they should use {@code services()} from the start.
 * <p>
 * This test scans compiled bytecode for the string {@code "getInstance"} in
 * constant pools of classes under the game object packages. It's a heuristic
 * (string matching on the constant pool) rather than full bytecode analysis,
 * but it catches the common pattern and has zero external dependencies.
 */
class TestObjectServicesMigrationGuard {

    /**
     * Object classes known to still use getInstance() directly.
     * As each is migrated, remove it from this set. The test will fail if a
     * class is in this set but no longer uses getInstance() (migration complete),
     * or if a class NOT in this set starts using getInstance() (regression).
     */
    private static final Set<String> KNOWN_UNMIGRATED = Set.of(
            // Sonic 1 — remaining: game-specific singletons only (not migratable)
            "com.openggf.game.sonic1.objects.Sonic1FloatingBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1GlassBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1JunctionObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1LZConveyorObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1MovingBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1PlatformObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SpinConveyorObjectInstance",
            // Sonic 2 — remaining: partial migration (some calls not in ObjectServices)
            "com.openggf.game.sonic2.objects.MTZTwinStompersObjectInstance",
            "com.openggf.game.sonic2.objects.RisingLavaObjectInstance",
            "com.openggf.game.sonic2.objects.SlidingSpikesObjectInstance",
            "com.openggf.game.sonic2.objects.SpecialStageResultsScreenObjectInstance",
            "com.openggf.game.sonic2.objects.StomperObjectInstance",
            "com.openggf.game.sonic2.objects.TornadoObjectInstance",
            "com.openggf.game.sonic2.objects.WFZPalSwitcherObjectInstance",
            "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance",
            // Sonic 3K — remaining: non-ObjectInstance classes or partial migration
            "com.openggf.game.sonic3k.objects.AizGiantRideVineObjectInstance",
            "com.openggf.game.sonic3k.objects.AizIntroArtLoader",
            "com.openggf.game.sonic3k.objects.AizIntroPaletteCycler",
            "com.openggf.game.sonic3k.objects.AizMinibossCutsceneInstance",
            "com.openggf.game.sonic3k.objects.AizMinibossInstance",
            "com.openggf.game.sonic3k.objects.AizRideVineObjectInstance",
            "com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz1Instance",
            "com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow",
            "com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance",
            "com.openggf.game.sonic3k.objects.S3kSignpostInstance"
    );

    /** Packages containing object instance classes to scan. */
    private static final String[] OBJECT_PACKAGES = {
            "com/openggf/game/sonic1/objects",
            "com/openggf/game/sonic2/objects",
            "com/openggf/game/sonic3k/objects",
    };

    /** Singleton class names whose getInstance() should be replaced with services(). */
    private static final Set<String> MONITORED_SINGLETONS = Set.of(
            "Camera",
            "LevelManager",
            "AudioManager",
            "GameStateManager",
            "SpriteManager",
            "WaterSystem",
            "FadeManager",
            "GraphicsManager"
    );

    @Test
    void objectInstances_shouldNotCallGetInstance() throws IOException {
        Path classesDir = Path.of("target/classes");
        if (!Files.isDirectory(classesDir)) {
            // Classes not compiled yet — skip gracefully
            return;
        }

        Map<String, List<String>> violations = new TreeMap<>();

        for (String pkg : OBJECT_PACKAGES) {
            Path pkgDir = classesDir.resolve(pkg);
            if (!Files.isDirectory(pkgDir)) continue;

            try (Stream<Path> classFiles = Files.walk(pkgDir)) {
                classFiles
                        .filter(p -> p.toString().endsWith(".class"))
                        .filter(p -> !p.getFileName().toString().contains("$"))  // skip inner classes for clarity
                        .forEach(classFile -> {
                            String className = classesDir.relativize(classFile).toString()
                                    .replace('\\', '/').replace(".class", "").replace('/', '.');
                            List<String> found = scanForGetInstance(classFile);
                            if (!found.isEmpty()) {
                                violations.put(className, found);
                            }
                        });
            }
        }

        // Check for regressions: classes not in KNOWN_UNMIGRATED that use getInstance()
        Set<String> regressions = new TreeSet<>();
        for (String violator : violations.keySet()) {
            if (!KNOWN_UNMIGRATED.contains(violator)) {
                regressions.add(violator);
            }
        }

        // Check for completed migrations: classes in KNOWN_UNMIGRATED that no longer violate
        Set<String> migrated = new TreeSet<>();
        for (String known : KNOWN_UNMIGRATED) {
            if (!violations.containsKey(known)) {
                migrated.add(known);
            }
        }

        StringBuilder msg = new StringBuilder();

        if (!regressions.isEmpty()) {
            msg.append("\n=== REGRESSIONS: new getInstance() usage in object code ===\n");
            msg.append("These classes use getInstance() but are not in KNOWN_UNMIGRATED.\n");
            msg.append("Migrate them to services() or add to KNOWN_UNMIGRATED if migration is pending:\n\n");
            for (String cls : regressions) {
                msg.append("  \"").append(cls).append("\",  // ").append(String.join(", ", violations.get(cls))).append("\n");
            }
        }

        if (!migrated.isEmpty()) {
            msg.append("\n=== COMPLETED MIGRATIONS: remove from KNOWN_UNMIGRATED ===\n");
            msg.append("These classes no longer use getInstance() — remove them from the set:\n\n");
            for (String cls : migrated) {
                msg.append("  \"").append(cls).append("\",\n");
            }
        }

        if (!msg.isEmpty()) {
            fail(msg.toString());
        }
    }

    /**
     * Scans a .class file's constant pool for references to monitored singleton
     * getInstance() methods. Returns the singleton names found.
     */
    private List<String> scanForGetInstance(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);

            List<String> found = new ArrayList<>();
            for (String singleton : MONITORED_SINGLETONS) {
                // The constant pool contains UTF-8 strings for class names and method refs.
                // A call to Foo.getInstance() will have both "Foo" and "getInstance" in the pool,
                // plus the class descriptor containing the singleton's package path.
                if (content.contains("getInstance") && content.contains(singleton)) {
                    found.add(singleton);
                }
            }
            return found;
        } catch (IOException e) {
            return List.of();
        }
    }
}
