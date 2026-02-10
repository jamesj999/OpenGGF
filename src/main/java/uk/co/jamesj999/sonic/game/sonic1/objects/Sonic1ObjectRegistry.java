package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1BuzzBomberBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1ChopperBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1NewtronBadnikInstance;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectFactory;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.PlaceholderObjectInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Object registry for Sonic the Hedgehog 1.
 * Uses factory-based registration following the Sonic 2 pattern.
 */
public class Sonic1ObjectRegistry implements ObjectRegistry {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ObjectRegistry.class.getName());

    private final Map<Integer, ObjectFactory> factories = new HashMap<>();
    private boolean loaded;

    private final ObjectFactory defaultFactory = (spawn, registry) ->
            new PlaceholderObjectInstance(spawn, registry.getPrimaryName(spawn.objectId()));

    @Override
    public ObjectInstance create(ObjectSpawn spawn) {
        ensureLoaded();
        int id = spawn.objectId();
        ObjectFactory factory = factories.getOrDefault(id, defaultFactory);
        return factory.create(spawn, this);
    }

    @Override
    public void reportCoverage(List<ObjectSpawn> spawns) {
        // No-op for now
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        registerDefaultFactories();
        LOGGER.fine("Sonic1ObjectRegistry loaded with " + factories.size() + " factories.");
    }

    private void registerDefaultFactories() {
        factories.put(Sonic1ObjectIds.LAMPPOST,
                (spawn, registry) -> new Sonic1LamppostObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SIGNPOST,
                (spawn, registry) -> new Sonic1SignpostObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MONITOR,
                (spawn, registry) -> new Sonic1MonitorObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.ROCK,
                (spawn, registry) -> new Sonic1RockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BRIDGE,
                (spawn, registry) -> new Sonic1BridgeObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SCENERY,
                (spawn, registry) -> new Sonic1SceneryObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPIKES,
                (spawn, registry) -> new Sonic1SpikeObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SPRING,
                (spawn, registry) -> new Sonic1SpringObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.PLATFORM,
                (spawn, registry) -> new Sonic1PlatformObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.COLLAPSING_LEDGE,
                (spawn, registry) -> new Sonic1CollapsingLedgeObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.BREAKABLE_WALL,
                (spawn, registry) -> new Sonic1BreakableWallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.EDGE_WALLS,
                (spawn, registry) -> new Sonic1EdgeWallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.CHOPPER,
                (spawn, registry) -> new Sonic1ChopperBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.BUZZ_BOMBER,
                (spawn, registry) -> new Sonic1BuzzBomberBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.CRABMEAT,
                (spawn, registry) -> new Sonic1CrabmeatBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.MOTOBUG,
                (spawn, registry) -> new Sonic1MotobugBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.NEWTRON,
                (spawn, registry) -> new Sonic1NewtronBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.SWINGING_PLATFORM,
                (spawn, registry) -> new Sonic1SwingingPlatformObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.WATERFALL_SOUND,
                (spawn, registry) -> new Sonic1WaterfallSoundObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GIANT_RING,
                (spawn, registry) -> new Sonic1GiantRingObjectInstance(spawn));
    }

    @Override
    public String getPrimaryName(int objectId) {
        return switch (objectId) {
            case Sonic1ObjectIds.SONIC -> "Sonic";
            case Sonic1ObjectIds.SIGNPOST -> "Signpost";
            case Sonic1ObjectIds.BRIDGE -> "Bridge";
            case Sonic1ObjectIds.SWINGING_PLATFORM -> "SwingingPlatform";
            case Sonic1ObjectIds.PLATFORM -> "Platform";
            case Sonic1ObjectIds.COLLAPSING_LEDGE -> "CollapsingLedge";
            case Sonic1ObjectIds.SCENERY -> "Scenery";
            case Sonic1ObjectIds.CRABMEAT -> "Crabmeat";
            case Sonic1ObjectIds.BUZZ_BOMBER -> "BuzzBomber";
            case Sonic1ObjectIds.BUZZ_BOMBER_MISSILE -> "BuzzBomberMissile";
            case Sonic1ObjectIds.MISSILE_DISSOLVE -> "MissileDissolve";
            case Sonic1ObjectIds.RING -> "Ring";
            case Sonic1ObjectIds.MONITOR -> "Monitor";
            case Sonic1ObjectIds.CHOPPER -> "Chopper";
            case Sonic1ObjectIds.JAWS -> "Jaws";
            case Sonic1ObjectIds.BURROBOT -> "Burrobot";
            case Sonic1ObjectIds.SPIKES -> "Spikes";
            case Sonic1ObjectIds.ROCK -> "Rock";
            case Sonic1ObjectIds.BREAKABLE_WALL -> "BreakableWall";
            case Sonic1ObjectIds.EGG_PRISON -> "EggPrison";
            case Sonic1ObjectIds.MOTOBUG -> "Motobug";
            case Sonic1ObjectIds.SPRING -> "Spring";
            case Sonic1ObjectIds.EDGE_WALLS -> "EdgeWalls";
            case Sonic1ObjectIds.NEWTRON -> "Newtron";
            case Sonic1ObjectIds.BUMPER -> "Bumper";
            case Sonic1ObjectIds.WATERFALL_SOUND -> "WaterfallSound";
            case Sonic1ObjectIds.GIANT_RING -> "GiantRing";
            case Sonic1ObjectIds.YADRIN -> "Yadrin";
            case Sonic1ObjectIds.BATBRAIN -> "Batbrain";
            case Sonic1ObjectIds.SEESAW -> "Seesaw";
            case Sonic1ObjectIds.BOMB -> "Bomb";
            case Sonic1ObjectIds.ORBINAUT -> "Orbinaut";
            case Sonic1ObjectIds.CATERKILLER -> "Caterkiller";
            case Sonic1ObjectIds.LAMPPOST -> "Lamppost";
            default -> String.format("S1_Obj_%02X", objectId & 0xFF);
        };
    }
}
