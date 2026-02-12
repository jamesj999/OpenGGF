package uk.co.jamesj999.sonic.game.sonic2.objects;

import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.level.objects.ObjectFactory;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.PlaceholderObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AsteronBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AquisBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.OctusBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.MasherBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.BuzzerBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.CoconutsBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.FlasherBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.SpinyBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.SpinyOnWallBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.GrabberBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.ChopChopBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.WhispBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.GrounderBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.CrawlBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.CrawltonBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.SpikerBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.SpikerDrillObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.SolBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.RexonBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.ShellcrackerBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.SlicerBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.NebulaBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.TurtloidBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.BalkiryBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2EHZBossInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2MCZBossInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CPZBossInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2ARZBossInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2CNZBossInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.bosses.Sonic2HTZBossInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class Sonic2ObjectRegistry implements ObjectRegistry {
    private static final Logger LOGGER = Logger.getLogger(Sonic2ObjectRegistry.class.getName());
    private static Sonic2ObjectRegistry instance;

    private final Map<Integer, List<String>> namesById = new HashMap<>();
    private final Map<Integer, ObjectFactory> factories = new HashMap<>();
    private final Set<Integer> unknownIds = new HashSet<>();
    private boolean loaded;

    private final ObjectFactory defaultFactory = (spawn, registry) -> new PlaceholderObjectInstance(spawn,
            registry.getPrimaryName(spawn.objectId()));

    private Sonic2ObjectRegistry() {
    }

    public static synchronized Sonic2ObjectRegistry getInstance() {
        if (instance == null) {
            instance = new Sonic2ObjectRegistry();
        }
        return instance;
    }

    public ObjectInstance create(ObjectSpawn spawn) {
        ensureLoaded();
        int id = spawn.objectId();
        ObjectFactory factory = factories.get(id);
        if (factory == null) {
            factory = defaultFactory;
            if (!namesById.containsKey(id) && unknownIds.add(id)) {
                LOGGER.info(() -> String.format("Object registry missing id 0x%02X (seen in placement list).", id));
            }
        }
        return factory.create(spawn, this);
    }

    public void registerFactory(int objectId, ObjectFactory factory) {
        ensureLoaded();
        factories.put(objectId & 0xFF, factory);
    }

    public String getPrimaryName(int objectId) {
        ensureLoaded();
        List<String> names = namesById.get(objectId);
        if (names == null || names.isEmpty()) {
            return String.format("Obj%02X", objectId & 0xFF);
        }
        return names.get(0);
    }

    public List<String> getAliases(int objectId) {
        ensureLoaded();
        List<String> names = namesById.get(objectId);
        if (names == null) {
            return List.of();
        }
        return Collections.unmodifiableList(names);
    }

    public void reportCoverage(List<ObjectSpawn> spawns) {
        ensureLoaded();
        if (spawns == null || spawns.isEmpty()) {
            return;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (ObjectSpawn spawn : spawns) {
            counts.merge(spawn.objectId(), 1, Integer::sum);
        }

        int totalIds = counts.size();
        int missing = 0;
        List<String> missingEntries = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int id = entry.getKey();
            if (!namesById.containsKey(id)) {
                missing++;
                missingEntries.add(String.format("0x%02X (%d)", id, entry.getValue()));
            }
        }

        LOGGER.info(String.format("Object registry coverage: %d unique ids in level, %d missing names.",
                totalIds, missing));
        if (!missingEntries.isEmpty()) {
            LOGGER.info("Missing object ids: " + String.join(", ", missingEntries));
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        namesById.putAll(Sonic2ObjectRegistryData.NAMES_BY_ID);
        registerDefaultFactories();
        LOGGER.fine("Loaded " + namesById.size() + " object name ids from built-in registry.");
    }

    private void registerDefaultFactories() {
        // LayerSwitcher (0x03) is handled by PlaneSwitcherManager, not as a rendered object
        registerFactory(Sonic2ObjectIds.LAYER_SWITCHER, (spawn, registry) -> null);

        registerFactory(Sonic2ObjectIds.SPRING,
                (spawn, registry) -> new SpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.SPIKES,
                (spawn, registry) -> new SpikeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.MONITOR,
                (spawn, registry) -> new MonitorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CHECKPOINT,
                (spawn, registry) -> new CheckpointObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // Springboard / Lever Spring (CPZ, ARZ, MCZ)
        registerFactory(Sonic2ObjectIds.SPRINGBOARD,
                (spawn, registry) -> new SpringboardObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // ARZ Leaves Generator
        registerFactory(Sonic2ObjectIds.LEAVES_GENERATOR,
                (spawn, registry) -> new LeavesGeneratorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // ARZ Bubble Generator (Object 0x24)
        // Subtype bit 7 determines mode: generator (invisible spawner) vs child bubble
        registerFactory(Sonic2ObjectIds.BUBBLES, (spawn, registry) -> {
            if ((spawn.subtype() & 0x80) != 0) {
                // Generator mode - invisible spawner that creates rising bubbles
                return new BubbleGeneratorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId()));
            } else {
                // Child bubble mode - shouldn't be in level data normally
                // but handle it by creating a rising bubble at that position
                return new BubbleObjectInstance(spawn.x(), spawn.y(), spawn.subtype() & 0x07, 0);
            }
        });

        // CPZ Objects
        registerFactory(Sonic2ObjectIds.TIPPING_FLOOR,
                (spawn, registry) -> new TippingFloorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.SPEED_BOOSTER,
                (spawn, registry) -> new SpeedBoosterObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CPZ_SPIN_TUBE,
                (spawn, registry) -> new CPZSpinTubeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BLUE_BALLS,
                (spawn, registry) -> new BlueBallsObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BREAKABLE_BLOCK,
                (spawn, registry) -> new BreakableBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.PIPE_EXIT_SPRING,
                (spawn, registry) -> new PipeExitSpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BARRIER,
                (spawn, registry) -> new BarrierObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CPZ_STAIRCASE,
                (spawn, registry) -> new CPZStaircaseObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CPZ_PYLON,
                (spawn, registry) -> new CPZPylonObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // CNZ Objects
        registerFactory(Sonic2ObjectIds.BUMPER,
                (spawn, registry) -> new BumperObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.HEX_BUMPER,
                (spawn, registry) -> new HexBumperObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BONUS_BLOCK,
                (spawn, registry) -> new BonusBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.FLIPPER,
                (spawn, registry) -> new FlipperObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.FORCED_SPIN,
                (spawn, registry) -> new ForcedSpinObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.LAUNCHER_SPRING,
                (spawn, registry) -> new LauncherSpringObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_CONVEYOR_BELT,
                (spawn, registry) -> new CNZConveyorBeltObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_RECT_BLOCKS,
                (spawn, registry) -> new CNZRectBlocksObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_BIG_BLOCK,
                (spawn, registry) -> new CNZBigBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.CNZ_ELEVATOR,
                (spawn, registry) -> new ElevatorObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.POINT_POKEY,
                (spawn, registry) -> new PointPokeyObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        ObjectFactory platformFactory = (spawn, registry) -> new PlatformObjectInstance(spawn,
                registry.getPrimaryName(spawn.objectId()));
        registerFactory(Sonic2ObjectIds.BRIDGE,
                (spawn, registry) -> new BridgeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.BRIDGE_STAKE,
                (spawn, registry) -> new BridgeStakeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.EHZ_WATERFALL,
                (spawn, registry) -> new EHZWaterfallObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.OOZ_LAUNCHER,
                (spawn, registry) -> new OOZLauncherObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.LAUNCHER_BALL,
                (spawn, registry) -> new LauncherBallObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.FAN,
                (spawn, registry) -> new FanObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.OOZ_POPPING_PLATFORM,
                (spawn, registry) -> new OOZPoppingPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.SPIRAL,
                (spawn, registry) -> new SpiralObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // OOZ Badniks
        registerFactory(Sonic2ObjectIds.OCTUS,
                (spawn, registry) -> new OctusBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.AQUIS,
                (spawn, registry) -> new AquisBadnikInstance(spawn, LevelManager.getInstance()));

        // EHZ Badniks
        registerFactory(Sonic2ObjectIds.MASHER,
                (spawn, registry) -> new MasherBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.BUZZER,
                (spawn, registry) -> new BuzzerBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.COCONUTS,
                (spawn, registry) -> new CoconutsBadnikInstance(spawn, LevelManager.getInstance()));

        // CPZ Badniks
        registerFactory(Sonic2ObjectIds.SPINY,
                (spawn, registry) -> new SpinyBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.SPINY_ON_WALL,
                (spawn, registry) -> new SpinyOnWallBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.GRABBER,
                (spawn, registry) -> new GrabberBadnikInstance(spawn, LevelManager.getInstance()));

        // ARZ Badniks
        registerFactory(Sonic2ObjectIds.CHOP_CHOP,
                (spawn, registry) -> new ChopChopBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.WHISP,
                (spawn, registry) -> new WhispBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.GROUNDER_IN_WALL,
                (spawn, registry) -> new GrounderBadnikInstance(spawn, LevelManager.getInstance(), false));
        registerFactory(Sonic2ObjectIds.GROUNDER_IN_WALL2,
                (spawn, registry) -> new GrounderBadnikInstance(spawn, LevelManager.getInstance(), true));
        // Note: GROUNDER_WALL (0x8F) and GROUNDER_ROCKS (0x90) are spawned dynamically

        // HTZ Badniks
        registerFactory(Sonic2ObjectIds.SPIKER,
                (spawn, registry) -> new SpikerBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.SPIKER_DRILL,
                (spawn, registry) -> new SpikerDrillObjectInstance(
                        spawn, spawn.x(), spawn.y(),
                        (spawn.renderFlags() & 0x01) != 0,
                        (spawn.renderFlags() & 0x02) != 0));
        registerFactory(Sonic2ObjectIds.SOL,
                (spawn, registry) -> new SolBadnikInstance(spawn, LevelManager.getInstance()));
        // Rexon (lava snake) - both 0x94 and 0x96 point to same implementation
        registerFactory(Sonic2ObjectIds.REXON,
                (spawn, registry) -> new RexonBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.REXON2,
                (spawn, registry) -> new RexonBadnikInstance(spawn, LevelManager.getInstance()));
        // Note: REXON_HEAD (0x97) is spawned dynamically by RexonBadnikInstance

        // CNZ Badniks
        registerFactory(Sonic2ObjectIds.CRAWL,
                (spawn, registry) -> new CrawlBadnikInstance(spawn, LevelManager.getInstance()));

        // MTZ Badniks
        registerFactory(Sonic2ObjectIds.ASTERON,
                (spawn, registry) -> new AsteronBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.SHELLCRACKER,
                (spawn, registry) -> new ShellcrackerBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.SLICER,
                (spawn, registry) -> new SlicerBadnikInstance(spawn, LevelManager.getInstance()));

        // MCZ Badniks
        registerFactory(Sonic2ObjectIds.CRAWLTON,
                (spawn, registry) -> new CrawltonBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.FLASHER,
                (spawn, registry) -> new FlasherBadnikInstance(spawn, LevelManager.getInstance()));

        // SCZ Badniks
        registerFactory(Sonic2ObjectIds.NEBULA,
                (spawn, registry) -> new NebulaBadnikInstance(spawn, LevelManager.getInstance()));
        registerFactory(Sonic2ObjectIds.TURTLOID,
                (spawn, registry) -> new TurtloidBadnikInstance(spawn, LevelManager.getInstance()));
        // Note: TURTLOID_RIDER (0x9B) and Turtloid jet are spawned dynamically by TurtloidBadnikInstance
        registerFactory(Sonic2ObjectIds.BALKIRY,
                (spawn, registry) -> new BalkiryBadnikInstance(spawn, LevelManager.getInstance()));
        // Note: BALKIRY_JET (0x9C) is spawned dynamically by BalkiryBadnikInstance

        // Level completion objects
        registerFactory(Sonic2ObjectIds.SIGNPOST,
                (spawn, registry) -> new SignpostObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.EGG_PRISON,
                (spawn, registry) -> new EggPrisonObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // CNZ Boss (Object 0x51)
        registerFactory(Sonic2ObjectIds.CNZ_BOSS,
                (spawn, registry) -> new Sonic2CNZBossInstance(spawn, LevelManager.getInstance()));
        // HTZ Boss (Object 0x52)
        registerFactory(Sonic2ObjectIds.HTZ_BOSS,
                (spawn, registry) -> new Sonic2HTZBossInstance(spawn, LevelManager.getInstance()));
        // EHZ Boss (Object 0x56)
        registerFactory(Sonic2ObjectIds.EHZ_BOSS,
                (spawn, registry) -> new Sonic2EHZBossInstance(spawn, LevelManager.getInstance()));
        // MCZ Boss (Object 0x57)
        registerFactory(Sonic2ObjectIds.MCZ_BOSS,
                (spawn, registry) -> new Sonic2MCZBossInstance(spawn, LevelManager.getInstance()));
        // CPZ Boss (Object 0x5D)
        registerFactory(Sonic2ObjectIds.CPZ_BOSS,
                (spawn, registry) -> new Sonic2CPZBossInstance(spawn, LevelManager.getInstance()));
        // ARZ Boss (Object 0x89)
        registerFactory(Sonic2ObjectIds.ARZ_BOSS,
                (spawn, registry) -> new Sonic2ARZBossInstance(spawn, LevelManager.getInstance()));
        // Boss Explosion (Object 0x58)
        registerFactory(Sonic2ObjectIds.BOSS_EXPLOSION,
                (spawn, registry) -> new BossExplosionObjectInstance(
                        spawn.x(),
                        spawn.y(),
                        LevelManager.getInstance().getObjectRenderManager()));

        // SwingingPlatform (Object 0x15) - chain-suspended platform in OOZ, ARZ, MCZ
        registerFactory(Sonic2ObjectIds.SWINGING_PLATFORM,
                (spawn, registry) -> new SwingingPlatformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        registerFactory(Sonic2ObjectIds.GENERIC_PLATFORM_A,
                (spawn, registry) -> new ARZPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.GENERIC_PLATFORM_B,
                (spawn, registry) -> new CPZPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Twin Stompers (Obj64) - crushing piston pair
        registerFactory(Sonic2ObjectIds.MTZ_TWIN_STOMPERS,
                (spawn, registry) -> new MTZTwinStompersObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // Button (Obj47) - trigger button that activates other objects via ButtonVine_Trigger
        registerFactory(Sonic2ObjectIds.BUTTON,
                (spawn, registry) -> new ButtonObjectInstance(spawn));

        // MTZ Spin Tube (Obj67) - tube transport with sinusoidal entry
        registerFactory(Sonic2ObjectIds.MTZ_SPIN_TUBE,
                (spawn, registry) -> new MTZSpinTubeObjectInstance(spawn));

        // MTZ spring wall - invisible solid wall that bounces player
        registerFactory(Sonic2ObjectIds.MTZ_SPRING_WALL,
                (spawn, registry) -> new MTZSpringWallObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Nut - screw nut that moves vertically when player pushes it
        registerFactory(Sonic2ObjectIds.NUT,
                (spawn, registry) -> new NutObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Long Platform (Obj65) - long moving platform with cog child
        registerFactory(Sonic2ObjectIds.MTZ_LONG_PLATFORM,
                (spawn, registry) -> {
                    // Properties index 2 = standalone cog (routine 6 in disassembly)
                    int propsIndex = ((spawn.subtype() >> 2) & 0x1C) >> 2;
                    if (propsIndex == 2) {
                        return new MTZLongPlatformCogInstance(spawn);
                    }
                    return new MTZLongPlatformObjectInstance(spawn);
                });

        // MTZ SpikyBlock (Obj68) - block with rotating spike from MTZ
        registerFactory(Sonic2ObjectIds.SPIKY_BLOCK,
                (spawn, registry) -> new SpikyBlockObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ SteamSpring (Obj42) - steam-powered spring piston from MTZ
        registerFactory(Sonic2ObjectIds.STEAM_SPRING,
                (spawn, registry) -> new SteamSpringObjectInstance(spawn));

        // MTZ Floor Spike (Obj6D) - retractable spike from MTZ
        registerFactory(Sonic2ObjectIds.FLOOR_SPIKE,
                (spawn, registry) -> new FloorSpikeObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ LargeRotPform (Obj6E) - large rotating platform moving in circle
        registerFactory(Sonic2ObjectIds.LARGE_ROT_PFORM,
                (spawn, registry) -> new LargeRotPformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Cog (Obj70) - giant rotating cog with 8 solid teeth
        registerFactory(Sonic2ObjectIds.COG,
                (spawn, registry) -> new CogObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ/CPZ multi-purpose platform with 12 movement subtypes
        registerFactory(Sonic2ObjectIds.MTZ_PLATFORM,
                (spawn, registry) -> new MTZPlatformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        // MTZ Conveyor (Obj6C) - small platform on pulleys
        // Subtype bit 7 set = parent spawner (creates children), bit 7 clear = individual platform
        registerFactory(Sonic2ObjectIds.CONVEYOR,
                (spawn, registry) -> ConveyorObjectInstance.createOrSpawnChildren(spawn));

        // MTZ Lava Bubble (Obj71) - animated lava bubble scenery
        registerFactory(Sonic2ObjectIds.MTZ_LAVA_BUBBLE,
                (spawn, registry) -> new MTZLavaBubbleObjectInstance(spawn));

        // CPZ/MCZ horizontal moving platform
        registerFactory(Sonic2ObjectIds.SIDEWAYS_PFORM,
                (spawn, registry) -> new SidewaysPformObjectInstance(spawn, registry.getPrimaryName(spawn.objectId())));

        registerFactory(Sonic2ObjectIds.INVISIBLE_BLOCK,
                (spawn, registry) -> new InvisibleBlockObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // ARZ Objects
        registerFactory(Sonic2ObjectIds.FALLING_PILLAR,
                (spawn, registry) -> new FallingPillarObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.RISING_PILLAR,
                (spawn, registry) -> new RisingPillarObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));
        registerFactory(Sonic2ObjectIds.ARROW_SHOOTER,
                (spawn, registry) -> new ArrowShooterObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // OOZ/MCZ/ARZ Collapsing Platform
        registerFactory(Sonic2ObjectIds.COLLAPSING_PLATFORM,
                (spawn, registry) -> new CollapsingPlatformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // ARZ Swinging Platform
        registerFactory(Sonic2ObjectIds.SWINGING_PFORM,
                (spawn, registry) -> new SwingingPformObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ/MTZ Rotating Platforms (moving wooden crates)
        registerFactory(Sonic2ObjectIds.MCZ_ROT_PFORMS,
                (spawn, registry) -> new MCZRotPformsObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // ARZ Rotating Platforms
        registerFactory(Sonic2ObjectIds.ARZ_ROT_PFORMS,
                (spawn, registry) -> new ARZRotPformsObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ/MTZ Lava Marker (invisible hazard collision zone)
        registerFactory(Sonic2ObjectIds.LAVA_MARKER,
                (spawn, registry) -> new LavaMarkerObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Rising Lava (invisible solid platform during earthquake)
        registerFactory(Sonic2ObjectIds.RISING_LAVA,
                (spawn, registry) -> new RisingLavaObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Seesaw (Object 0x14)
        registerFactory(Sonic2ObjectIds.SEESAW,
                (spawn, registry) -> new SeesawObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Zipline Lift (Object 0x16)
        registerFactory(Sonic2ObjectIds.HTZ_LIFT,
                (spawn, registry) -> new HTZLiftObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // HTZ Smashable Ground (Object 0x2F) - breakable rock platform
        registerFactory(Sonic2ObjectIds.SMASHABLE_GROUND,
                (spawn, registry) -> new SmashableGroundObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Brick / Spike Ball (Object 0x75)
        registerFactory(Sonic2ObjectIds.MCZ_BRICK,
                (spawn, registry) -> new MCZBrickObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Sliding Spikes (Object 0x76) - spike block that slides out of wall
        registerFactory(Sonic2ObjectIds.SLIDING_SPIKES,
                (spawn, registry) -> new SlidingSpikesObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Stomper (Object 0x2A) - ceiling crusher
        registerFactory(Sonic2ObjectIds.STOMPER,
                (spawn, registry) -> new StomperObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ VineSwitch (Object 0x7F) - pull switch that triggers ButtonVine
        registerFactory(Sonic2ObjectIds.VINE_SWITCH,
                (spawn, registry) -> new VineSwitchObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ/WFZ MovingVine (Object 0x80) - vine pulley or hook on chain
        registerFactory(Sonic2ObjectIds.MOVING_VINE,
                (spawn, registry) -> new MovingVineObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Bridge (Object 0x77) - horizontal gate triggered by ButtonVine
        registerFactory(Sonic2ObjectIds.MCZ_BRIDGE,
                (spawn, registry) -> new MCZBridgeObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // MCZ Drawbridge (Object 0x81) - rotatable drawbridge triggered by ButtonVine
        registerFactory(Sonic2ObjectIds.MCZ_DRAWBRIDGE,
                (spawn, registry) -> new MCZDrawbridgeObjectInstance(spawn,
                        registry.getPrimaryName(spawn.objectId())));

        // SCZ Cloud (ObjB3) - decorative scrolling clouds
//        registerFactory(Sonic2ObjectIds.TORNADO,
//                (spawn, registry) -> new TornadoObjectInstance(spawn));
        registerFactory(Sonic2ObjectIds.CLOUD,
                (spawn, registry) -> new CloudObjectInstance(spawn));
    }
}
