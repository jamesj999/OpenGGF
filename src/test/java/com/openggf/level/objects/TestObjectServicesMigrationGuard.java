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
            // Sonic 1 — GameServices/getInstance usage (S1 switch/conveyor singletons, camera, etc.)
            "com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1BreakableWallObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1EndingEmeraldsObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1EndingSTHObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1LavaBallObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1MonitorObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1ObjectRegistry",
            "com.openggf.game.sonic1.objects.Sonic1PushBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SignpostObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SmashBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SpringObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SwingingPlatformObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1TryAgainEggmanObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1TryAgainEmeraldsObjectInstance",
            "com.openggf.game.sonic1.objects.bosses.Sonic1BossBlockInstance",
            "com.openggf.game.sonic1.objects.bosses.Sonic1FZBossInstance",
            "com.openggf.game.sonic1.objects.Sonic1FloatingBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1GlassBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1JunctionObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1LZConveyorObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1MovingBlockObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1PlatformObjectInstance",
            "com.openggf.game.sonic1.objects.Sonic1SpinConveyorObjectInstance",
            "com.openggf.game.sonic1.objects.bosses.Sonic1ScrapEggmanInstance",
            // Sonic 2 — GameServices.level/rom/audio usage
            "com.openggf.game.sonic2.objects.BreakableBlockObjectInstance",
            "com.openggf.game.sonic2.objects.CheckpointObjectInstance",
            "com.openggf.game.sonic2.objects.CheckpointStarInstance",
            "com.openggf.game.sonic2.objects.ConveyorObjectInstance",
            "com.openggf.game.sonic2.objects.EggPrisonObjectInstance",
            "com.openggf.game.sonic2.objects.FlipperObjectInstance",
            "com.openggf.game.sonic2.objects.ForcedSpinObjectInstance",
            "com.openggf.game.sonic2.objects.InvisibleBlockObjectInstance",
            "com.openggf.game.sonic2.objects.MTZLongPlatformObjectInstance",
            "com.openggf.game.sonic2.objects.MonitorObjectInstance",
            "com.openggf.game.sonic2.objects.PipeExitSpringObjectInstance",
            "com.openggf.game.sonic2.objects.PointPokeyObjectInstance",
            "com.openggf.game.sonic2.objects.RingPrizeObjectInstance",
            "com.openggf.game.sonic2.objects.RivetObjectInstance",
            "com.openggf.game.sonic2.objects.SidewaysPformObjectInstance",
            "com.openggf.game.sonic2.objects.SignpostObjectInstance",
            "com.openggf.game.sonic2.objects.SmashableGroundObjectInstance",
            "com.openggf.game.sonic2.objects.Sonic2ObjectRegistry",
            "com.openggf.game.sonic2.objects.SpringObjectInstance",
            "com.openggf.game.sonic2.objects.SpringboardObjectInstance",
            "com.openggf.game.sonic2.objects.SuperSonicStarsObjectInstance",
            "com.openggf.game.sonic2.objects.TippingFloorObjectInstance",
            "com.openggf.game.sonic2.objects.badniks.SolBadnikInstance",
            "com.openggf.game.sonic2.objects.bosses.Sonic2ARZBossInstance",
            "com.openggf.game.sonic2.objects.bosses.Sonic2DeathEggRobotInstance",
            "com.openggf.game.sonic2.objects.MTZTwinStompersObjectInstance",
            "com.openggf.game.sonic2.objects.RisingLavaObjectInstance",
            "com.openggf.game.sonic2.objects.SlidingSpikesObjectInstance",
            "com.openggf.game.sonic2.objects.SpecialStageResultsScreenObjectInstance",
            "com.openggf.game.sonic2.objects.StomperObjectInstance",
            "com.openggf.game.sonic2.objects.TornadoObjectInstance",
            "com.openggf.game.sonic2.objects.WFZPalSwitcherObjectInstance",
            "com.openggf.game.sonic2.objects.bosses.Sonic2MechaSonicInstance",
            "com.openggf.game.sonic2.objects.bosses.Sonic2MTZBossInstance",
            // Sonic 3K — GameServices.level/rom/audio usage
            "com.openggf.game.sonic3k.objects.AizGiantRideVineObjectInstance",
            "com.openggf.game.sonic3k.objects.AizIntroArtLoader",
            "com.openggf.game.sonic3k.objects.AizIntroPaletteCycler",
            "com.openggf.game.sonic3k.objects.AizIntroBoosterChild",
            "com.openggf.game.sonic3k.objects.AizIntroTerrainSwap",
            "com.openggf.game.sonic3k.objects.AizMinibossBarrelShotChild",
            "com.openggf.game.sonic3k.objects.AizPlaneIntroInstance",
            "com.openggf.game.sonic3k.objects.AizVineHandleLogic",
            "com.openggf.game.sonic3k.objects.AutoSpinObjectInstance",
            "com.openggf.game.sonic3k.objects.CorkFloorObjectInstance",
            "com.openggf.game.sonic3k.objects.FloatingPlatformObjectInstance",
            "com.openggf.game.sonic3k.objects.S3kSignpostSparkleChild",
            "com.openggf.game.sonic3k.objects.SongFadeTransitionInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kCollapsingPlatformObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kMonitorObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry",
            "com.openggf.game.sonic3k.objects.Sonic3kSSEntryFlashObjectInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kSSEntryRingObjectInstance",
            "com.openggf.game.sonic3k.objects.AizMinibossCutsceneInstance",
            "com.openggf.game.sonic3k.objects.AizMinibossInstance",
            "com.openggf.game.sonic3k.objects.AizRideVineObjectInstance",
            "com.openggf.game.sonic3k.objects.CutsceneKnucklesAiz1Instance",
            "com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow",
            "com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance",
            "com.openggf.game.sonic3k.objects.S3kSignpostInstance",
            "com.openggf.game.sonic3k.objects.Sonic3kStarPostObjectInstance"
    );

    /** Packages containing object instance classes to scan. */
    private static final String[] OBJECT_PACKAGES = {
            "com/openggf/game/sonic1/objects",
            "com/openggf/game/sonic2/objects",
            "com/openggf/game/sonic3k/objects",
    };

    /**
     * Internal class name prefixes for monitored singletons.
     * The bytecode constant pool contains class references like "com/openggf/camera/Camera"
     * and method name strings like "getInstance". We check for both the class reference
     * AND a nearby "getInstance" to avoid false positives from mere type usage.
     */
    private static final Map<String, String> MONITORED_SINGLETONS = Map.ofEntries(
            Map.entry("Camera", "com/openggf/camera/Camera"),
            Map.entry("LevelManager", "com/openggf/level/LevelManager"),
            Map.entry("AudioManager", "com/openggf/audio/AudioManager"),
            Map.entry("GameStateManager", "com/openggf/game/GameStateManager"),
            Map.entry("SpriteManager", "com/openggf/sprites/managers/SpriteManager"),
            Map.entry("WaterSystem", "com/openggf/level/WaterSystem"),
            Map.entry("FadeManager", "com/openggf/graphics/FadeManager"),
            Map.entry("GraphicsManager", "com/openggf/graphics/GraphicsManager")
    );

    /**
     * Also detect GameServices static calls (GameServices.level(), GameServices.camera(), etc.).
     * These bypass the ObjectServices abstraction just like getInstance() does.
     */
    private static final String GAME_SERVICES_CLASS = "com/openggf/game/GameServices";

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
     * getInstance() methods or GameServices static calls. Returns the singleton names found.
     * <p>
     * Uses the internal class name (e.g., "com/openggf/camera/Camera") which is more precise
     * than the simple class name — avoids false positives from type references that don't
     * involve getInstance() calls.
     */
    private List<String> scanForGetInstance(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            String content = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);

            List<String> found = new ArrayList<>();

            // Check for Foo.getInstance() — requires both the internal class name
            // AND "getInstance" in the constant pool
            if (content.contains("getInstance")) {
                for (var entry : MONITORED_SINGLETONS.entrySet()) {
                    if (content.contains(entry.getValue())) {
                        found.add(entry.getKey());
                    }
                }
            }

            // Check for GameServices.level() / .camera() / .audio() / etc.
            if (content.contains(GAME_SERVICES_CLASS)) {
                found.add("GameServices");
            }

            return found;
        } catch (IOException e) {
            return List.of();
        }
    }
}
