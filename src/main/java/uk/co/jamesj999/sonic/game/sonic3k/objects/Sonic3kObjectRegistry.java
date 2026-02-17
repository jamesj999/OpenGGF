package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kObjectIds;
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
 * Object registry for Sonic 3 &amp; Knuckles.
 *
 * <p>Currently all objects use {@link PlaceholderObjectInstance} for debug
 * rendering. Object names are derived from the SK Set 1 pointer table
 * ({@code Object pointers - SK Set 1.asm}) in the S3K disassembly.
 *
 * <p>S3K uses two zone-set pointer tables (SK Set 1 for S&amp;K zones,
 * S3 Set for S3 zones) which remap some IDs above 110. The names here
 * use SK Set 1 as the canonical source; S3-only remappings share the
 * same underlying object names in most cases.
 */
public class Sonic3kObjectRegistry implements ObjectRegistry {
    private static final Logger LOG = Logger.getLogger(Sonic3kObjectRegistry.class.getName());

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
        LOG.fine("Sonic3kObjectRegistry loaded with " + factories.size() + " factories.");
    }

    private void registerDefaultFactories() {
        factories.put(Sonic3kObjectIds.AIZ1_TREE,
                (spawn, registry) -> new Aiz1TreeObjectInstance(spawn));
        factories.put(Sonic3kObjectIds.AIZ1_ZIPLINE_PEG,
                (spawn, registry) -> new Aiz1ZiplinePegObjectInstance(spawn));
    }

    /**
     * Returns the object name from the SK Set 1 pointer table.
     * Names match the disassembly label with the {@code Obj_} prefix stripped.
     */
    @Override
    public String getPrimaryName(int objectId) {
        return switch (objectId) {
            case 0x00 -> "Ring";
            case 0x01 -> "Monitor";
            case 0x02 -> "PathSwap";
            case 0x03 -> "AIZHollowTree";
            case 0x04 -> "CollapsingPlatform";
            case 0x05 -> "AIZLRZEMZRock";
            case 0x06 -> "AIZRideVine";
            case 0x07 -> "Spring";
            case 0x08 -> "Spikes";
            case 0x09 -> "AIZ1Tree";
            case 0x0A -> "AIZ1ZiplinePeg";
            case 0x0C -> "AIZGiantRideVine";
            case 0x0D -> "BreakableWall";
            case 0x0E -> "TwistedRamp";
            case 0x0F -> "CollapsingBridge";
            case 0x10 -> "LBZTubeElevator";
            case 0x11 -> "LBZMovingPlatform";
            case 0x12 -> "LBZUnusedElevator";
            case 0x13 -> "LBZExplodingTrigger";
            case 0x14 -> "LBZTriggerBridge";
            case 0x15 -> "LBZPlayerLauncher";
            case 0x16 -> "LBZFlameThrower";
            case 0x17 -> "LBZRideGrapple";
            case 0x18 -> "LBZCupElevator";
            case 0x19 -> "LBZCupElevatorPole";
            case 0x1A -> "LBZUnusedTiltingBridge";
            case 0x1B -> "LBZPipePlug";
            case 0x1D -> "LBZUnusedBarPlatform";
            case 0x1E -> "LBZSpinLauncher";
            case 0x1F -> "LBZLoweringGrapple";
            case 0x20 -> "MGZLBZSmashingPillar";
            case 0x21 -> "LBZGateLaser";
            case 0x22 -> "LBZAlarm";
            case 0x23 -> "LBZUnusedForceFall";
            case 0x24 -> "AutomaticTunnel";
            case 0x26 -> "AutoSpin";
            case 0x27 -> "S2LavaMarker";
            case 0x28 -> "InvisibleBlock";
            case 0x29 -> "AIZDisappearingFloor";
            case 0x2A -> "CorkFloor";
            case 0x2B -> "AIZFlippingBridge";
            case 0x2C -> "AIZCollapsingLogBridge";
            case 0x2D -> "AIZFallingLog";
            case 0x2E -> "AIZSpikedLog";
            case 0x2F -> "StillSprite";
            case 0x30 -> "AnimatedStillSprite";
            case 0x31 -> "LBZRollingDrum";
            case 0x32 -> "AIZDrawBridge";
            case 0x33 -> "Button";
            case 0x34 -> "StarPost";
            case 0x35 -> "AIZForegroundPlant";
            case 0x36 -> "HCZBreakableBar";
            case 0x37 -> "HCZWaterRush";
            case 0x38 -> "HCZCGZFan";
            case 0x39 -> "HCZLargeFan";
            case 0x3A -> "HCZHandLauncher";
            case 0x3B -> "HCZWaterWall";
            case 0x3C -> "Door";
            case 0x3D -> "RetractingSpring";
            case 0x3E -> "HCZConveyorBelt";
            case 0x3F -> "HCZConveyorSpike";
            case 0x40 -> "HCZBlock";
            case 0x41 -> "CNZBalloon";
            case 0x42 -> "CNZCannon";
            case 0x43 -> "CNZRisingPlatform";
            case 0x44 -> "CNZTrapDoor";
            case 0x45 -> "CNZLightBulb";
            case 0x46 -> "CNZHoverFan";
            case 0x47 -> "CNZCylinder";
            case 0x48 -> "CNZVacuumTube";
            case 0x49 -> "CNZGiantWheel";
            case 0x4A -> "Bumper";
            case 0x4B -> "CNZTriangleBumpers";
            case 0x4C -> "CNZSpiralTube";
            case 0x4D -> "CNZBarberPoleSprite";
            case 0x4E -> "CNZWireCage";
            case 0x4F -> "SinkingMud";
            case 0x50 -> "MGZTwistingLoop";
            case 0x51 -> "FloatingPlatform";
            case 0x52 -> "MGZSmashingPillar";
            case 0x53 -> "MGZSwingingPlatform";
            case 0x54 -> "Bubbler";
            case 0x55 -> "MGZHeadTrigger";
            case 0x56 -> "MGZMovingSpikePlatform";
            case 0x57 -> "MGZTriggerPlatform";
            case 0x58 -> "MGZSwingingSpikeBall";
            case 0x59 -> "MGZDashTrigger";
            case 0x5A -> "MGZPulley";
            case 0x5B -> "MGZTopPlatform";
            case 0x5C -> "MGZTopLauncher";
            case 0x5D -> "CGZTriangleBumpers";
            case 0x5E -> "CGZBladePlatform";
            case 0x5F -> "2PRetractingSpring";
            case 0x60 -> "BPZElephantBlock";
            case 0x61 -> "BPZBalloon";
            case 0x62 -> "DPZDisolvingSandBar";
            case 0x63 -> "DPZButton";
            case 0x64 -> "2PItem";
            case 0x65 -> "2PGoalMarker";
            case 0x66 -> "EMZDripper";
            case 0x67 -> "HCZSnakeBlocks";
            case 0x68 -> "HCZSpinningColumn";
            case 0x69 -> "HCZTwistingLoop";
            case 0x6A -> "InvisibleHurtBlockH";
            case 0x6B -> "InvisibleHurtBlockV";
            case 0x6C -> "TensionBridge";
            case 0x6D -> "HCZWaterSplash";
            case 0x6E -> "WaterDrop";
            case 0x6F -> "FBZWireCage";
            case 0x70 -> "FBZWireCageStationary";
            case 0x71 -> "FBZFloatingPlatform";
            case 0x72 -> "FBZChainLink";
            case 0x73 -> "FBZMagneticSpikeBall";
            case 0x74 -> "FBZMagneticPlatform";
            case 0x75 -> "FBZSnakePlatform";
            case 0x76 -> "FBZBentPipe";
            case 0x77 -> "FBZRotatingPlatform";
            case 0x78 -> "FBZDEZPlayerLauncher";
            case 0x79 -> "FBZDisappearingPlatform";
            case 0x7A -> "FBZScrewDoor";
            case 0x7B -> "FBZSpinningPole";
            case 0x7C -> "FBZPropeller";
            case 0x7D -> "FBZPiston";
            case 0x7E -> "FBZPlatformBlocks";
            case 0x7F -> "FBZMissileLauncher";
            case 0x80 -> "HiddenMonitor";
            case 0x81 -> "EggCapsule";
            case 0x82 -> "CutsceneKnuckles";
            case 0x83 -> "CutsceneButton";
            case 0x84 -> "AIZPlaneIntro";
            case 0x85 -> "SSEntryRing";
            case 0x86 -> "GumballMachine";
            case 0x87 -> "GumballTriangleBumper";
            case 0x88 -> "CNZWaterLevelCorkFloor";
            case 0x89 -> "CNZWaterLevelButton";
            case 0x8A -> "FBZExitHall";
            case 0x8B -> "SpriteMask";
            case 0x8C -> "Bloominator";
            case 0x8D -> "Rhinobot";
            case 0x8E -> "MonkeyDude";
            case 0x8F -> "CaterKillerJr";
            case 0x90 -> "AIZMinibossCutscene";
            case 0x91 -> "AIZMiniboss";
            case 0x92 -> "AIZEndBoss";
            case 0x93 -> "Jawz";
            case 0x94 -> "Blastoid";
            case 0x95 -> "Buggernaut";
            case 0x96 -> "TurboSpiker";
            case 0x97 -> "MegaChopper";
            case 0x98 -> "Poindexter";
            case 0x99 -> "HCZMiniboss";
            case 0x9A -> "HCZEndBoss";
            case 0x9B -> "BubblesBadnik";
            case 0x9C -> "Spiker";
            case 0x9D -> "Mantis";
            case 0x9E -> "Tunnelbot";
            case 0x9F -> "MGZMiniboss";
            case 0xA0 -> "MGZ2DrillingRobotnik";
            case 0xA1 -> "MGZEndBoss";
            case 0xA2 -> "MGZEndBossKnux";
            case 0xA3 -> "Clamer";
            case 0xA4 -> "Sparkle";
            case 0xA5 -> "Batbot";
            case 0xA6 -> "CNZMiniboss";
            case 0xA7 -> "CNZEndBoss";
            case 0xA8 -> "Blaster";
            case 0xA9 -> "TechnoSqueek";
            case 0xAA -> "FBZMiniboss";
            case 0xAB -> "FBZ2Subboss";
            case 0xAC -> "FBZEndBoss";
            case 0xAD -> "Penguinator";
            case 0xAE -> "StarPointer";
            case 0xAF -> "ICZCrushingColumn";
            case 0xB0 -> "ICZPathFollowPlatform";
            case 0xB1 -> "ICZBreakableWall";
            case 0xB2 -> "ICZFreezer";
            case 0xB3 -> "ICZSegmentColumn";
            case 0xB4 -> "ICZSwingingPlatform";
            case 0xB5 -> "ICZStalagtite";
            case 0xB6 -> "ICZIceCube";
            case 0xB7 -> "ICZIceSpikes";
            case 0xB8 -> "ICZHarmfulIce";
            case 0xB9 -> "ICZSnowPile";
            case 0xBA -> "ICZTensionPlatform";
            case 0xBB -> "ICZIceBlock";
            case 0xBC -> "ICZMiniboss";
            case 0xBD -> "ICZEndBoss";
            case 0xBE -> "SnaleBlaster";
            case 0xBF -> "Ribot";
            case 0xC0 -> "Orbinaut";
            case 0xC1 -> "Corkey";
            case 0xC2 -> "Flybot767";
            case 0xC3 -> "LBZ1Robotnik";
            case 0xC4 -> "LBZMinibossBox";
            case 0xC5 -> "LBZMinibossBoxKnux";
            case 0xC6 -> "LBZ2RobotnikShip";
            case 0xC8 -> "LBZKnuxPillar";
            case 0xC9 -> "LBZMiniboss";
            case 0xCA -> "LBZFinalBoss1";
            case 0xCB -> "LBZEndBoss";
            case 0xCC -> "LBZFinalBoss2";
            case 0xCD -> "LBZFinalBossKnux";
            case 0xCE -> "FBZExitDoor";
            case 0xCF -> "FBZEggPrison";
            case 0xD0 -> "FBZSpringPlunger";
            case 0xE0 -> "FBZWallMissile";
            case 0xE1 -> "FBZMine";
            case 0xE2 -> "FBZElevator";
            case 0xE3 -> "FBZTrapSpring";
            case 0xE4 -> "FBZFlamethrower";
            case 0xE5 -> "FBZSpiderCrane";
            case 0xE6 -> "PachinkoTriangleBumper";
            case 0xE7 -> "PachinkoFlipper";
            case 0xE8 -> "PachinkoEnergyTrap";
            case 0xE9 -> "PachinkoInvisibleUnknown";
            case 0xEA -> "PachinkoPlatform";
            case 0xEB -> "GumballItem";
            case 0xEC -> "PachinkoMagnetOrb";
            case 0xED -> "PachinkoItemOrb";
            case 0xFF -> "FBZMagneticPendulum";
            default -> String.format("S3K_Obj_%02X", objectId & 0xFF);
        };
    }
}
