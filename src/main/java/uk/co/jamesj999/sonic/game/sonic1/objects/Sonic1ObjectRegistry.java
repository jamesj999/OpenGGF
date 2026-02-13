package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1BuzzBomberBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1CaterkillerBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1ChopperBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1CrabmeatBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1MotobugBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1BatbrainBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1NewtronBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1RollerBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1YadrinBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.bosses.Sonic1BossFireInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.bosses.Sonic1GHZBossInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.bosses.Sonic1MZBossInstance;
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
        factories.put(Sonic1ObjectIds.SPINNING_LIGHT,
                (spawn, registry) -> new Sonic1SpinningLightObjectInstance(spawn));
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
        factories.put(Sonic1ObjectIds.GIANT_RING,
                (spawn, registry) -> new Sonic1GiantRingObjectInstance(spawn));
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
        factories.put(Sonic1ObjectIds.CATERKILLER,
                (spawn, registry) -> new Sonic1CaterkillerBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.BATBRAIN,
                (spawn, registry) -> new Sonic1BatbrainBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.YADRIN,
                (spawn, registry) -> new Sonic1YadrinBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.ROLLER,
                (spawn, registry) -> new Sonic1RollerBadnikInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.BUMPER,
                (spawn, registry) -> new Sonic1BumperObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.FLOATING_BLOCK,
                (spawn, registry) -> new Sonic1FloatingBlockObjectInstance(spawn,
                        LevelManager.getInstance().getRomZoneId()));
        factories.put(Sonic1ObjectIds.SPIKED_POLE_HELIX,
                (spawn, registry) -> new Sonic1SpikedPoleHelixObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SWINGING_PLATFORM,
                (spawn, registry) -> new Sonic1SwingingPlatformObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.MZ_BRICK,
                (spawn, registry) -> new Sonic1MzBrickObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.SMASH_BLOCK,
                (spawn, registry) -> new Sonic1SmashBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.CHAINED_STOMPER,
                (spawn, registry) -> new Sonic1ChainedStomperObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.PUSH_BLOCK,
                (spawn, registry) -> new Sonic1PushBlockObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.BUTTON,
                (spawn, registry) -> new Sonic1ButtonObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MZ_GLASS_BLOCK,
                (spawn, registry) -> new Sonic1GlassBlockObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.LAVA_BALL_MAKER,
                (spawn, registry) -> new Sonic1LavaBallMakerObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_GEYSER_MAKER,
                (spawn, registry) -> new Sonic1LavaGeyserMakerObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_GEYSER,
                (spawn, registry) -> new Sonic1LavaGeyserObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_TAG,
                (spawn, registry) -> new Sonic1LavaTagObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.LAVA_WALL,
                (spawn, registry) -> new Sonic1LavaWallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM,
                (spawn, registry) -> new Sonic1LargeGrassyPlatformObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.BURNING_GRASS,
                (spawn, registry) -> new Sonic1GrassFireObjectInstance(
                        spawn.x(), spawn.y(), 0, null, null, spawn.subtype() == 0));
        factories.put(Sonic1ObjectIds.WATERFALL_SOUND,
                (spawn, registry) -> new Sonic1WaterfallSoundObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GIANT_RING,
                (spawn, registry) -> new Sonic1GiantRingObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.HIDDEN_BONUS,
                (spawn, registry) -> new Sonic1HiddenBonusObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.COLLAPSING_FLOOR,
                (spawn, registry) -> new Sonic1CollapsingFloorObjectInstance(
                        spawn, LevelManager.getInstance().getRomZoneId()));
        factories.put(Sonic1ObjectIds.MOVING_BLOCK,
                (spawn, registry) -> new Sonic1MovingBlockObjectInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.SPIKED_BALL_CHAIN,
                (spawn, registry) -> new Sonic1SpikedBallChainObjectInstance(spawn,
                        LevelManager.getInstance().getRomZoneId()));
        factories.put(Sonic1ObjectIds.BIG_SPIKED_BALL,
                (spawn, registry) -> new Sonic1BigSpikedBallObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.INVISIBLE_BARRIER,
                (spawn, registry) -> new Sonic1InvisibleBarrierObjectInstance(spawn));
        factories.put(Sonic1ObjectIds.GHZ_BOSS,
                (spawn, registry) -> new Sonic1GHZBossInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.MZ_BOSS,
                (spawn, registry) -> new Sonic1MZBossInstance(spawn, LevelManager.getInstance()));
        factories.put(Sonic1ObjectIds.BOSS_FIRE,
                (spawn, registry) -> new Sonic1BossFireInstance(spawn));
        factories.put(Sonic1ObjectIds.EGG_PRISON, (spawn, registry) -> {
            // ROM placement has two entries: subtype 0 (body) and subtype 1 (button).
            // Pri_Main creates sub-objects from Pri_Var; our engine loads each entry separately.
            if (spawn.subtype() == 1) {
                return new Sonic1EggPrisonButtonObjectInstance(spawn);
            }
            return new Sonic1EggPrisonObjectInstance(spawn);
        });
    }

    @Override
    public String getPrimaryName(int objectId) {
        return switch (objectId) {
            case Sonic1ObjectIds.SONIC -> "Sonic";
            case Sonic1ObjectIds.SPINNING_LIGHT -> "SpinningLight";
            case Sonic1ObjectIds.SIGNPOST -> "Signpost";
            case Sonic1ObjectIds.BRIDGE -> "Bridge";
            case Sonic1ObjectIds.SPIKED_POLE_HELIX -> "SpikedPoleHelix";
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
            case Sonic1ObjectIds.GHZ_BOSS -> "GHZBoss";
            case Sonic1ObjectIds.MZ_BOSS -> "MZBoss";
            case Sonic1ObjectIds.BOSS_FIRE -> "BossFire";
            case Sonic1ObjectIds.EGG_PRISON -> "EggPrison";
            case Sonic1ObjectIds.MOTOBUG -> "Motobug";
            case Sonic1ObjectIds.SPRING -> "Spring";
            case Sonic1ObjectIds.EDGE_WALLS -> "EdgeWalls";
            case Sonic1ObjectIds.MZ_BRICK -> "MzBrick";
            case Sonic1ObjectIds.NEWTRON -> "Newtron";
            case Sonic1ObjectIds.ROLLER -> "Roller";
            case Sonic1ObjectIds.BUMPER -> "Bumper";
            case Sonic1ObjectIds.BOSS_BALL -> "BossBall";
            case Sonic1ObjectIds.WATERFALL_SOUND -> "WaterfallSound";
            case Sonic1ObjectIds.GIANT_RING -> "GiantRing";
            case Sonic1ObjectIds.YADRIN -> "Yadrin";
            case Sonic1ObjectIds.MZ_GLASS_BLOCK -> "MzGlassBlock";
            case Sonic1ObjectIds.SMASH_BLOCK -> "SmashBlock";
            case Sonic1ObjectIds.COLLAPSING_FLOOR -> "CollapsingFloor";
            case Sonic1ObjectIds.MOVING_BLOCK -> "MovingBlock";
            case Sonic1ObjectIds.CHAINED_STOMPER -> "ChainedStomper";
            case Sonic1ObjectIds.PUSH_BLOCK -> "PushBlock";
            case Sonic1ObjectIds.BUTTON -> "Button";
            case Sonic1ObjectIds.LAVA_BALL_MAKER -> "LavaBallMaker";
            case Sonic1ObjectIds.LAVA_BALL -> "LavaBall";
            case Sonic1ObjectIds.LAVA_GEYSER_MAKER -> "LavaGeyserMaker";
            case Sonic1ObjectIds.LAVA_GEYSER -> "LavaGeyser";
            case Sonic1ObjectIds.LAVA_TAG -> "LavaTag";
            case Sonic1ObjectIds.LAVA_WALL -> "LavaWall";
            case Sonic1ObjectIds.MZ_LARGE_GRASSY_PLATFORM -> "MzLargeGrassyPlatform";
            case Sonic1ObjectIds.BURNING_GRASS -> "BurningGrass";
            case Sonic1ObjectIds.BATBRAIN -> "Batbrain";
            case Sonic1ObjectIds.FLOATING_BLOCK -> "FloatingBlock";
            case Sonic1ObjectIds.SPIKED_BALL_CHAIN -> "SpikedBallChain";
            case Sonic1ObjectIds.BIG_SPIKED_BALL -> "BigSpikedBall";
            case Sonic1ObjectIds.SEESAW -> "Seesaw";
            case Sonic1ObjectIds.BOMB -> "Bomb";
            case Sonic1ObjectIds.ORBINAUT -> "Orbinaut";
            case Sonic1ObjectIds.INVISIBLE_BARRIER -> "InvisibleBarrier";
            case Sonic1ObjectIds.CATERKILLER -> "Caterkiller";
            case Sonic1ObjectIds.LAMPPOST -> "Lamppost";
            case Sonic1ObjectIds.HIDDEN_BONUS -> "HiddenBonus";
            default -> String.format("S1_Obj_%02X", objectId & 0xFF);
        };
    }
}
