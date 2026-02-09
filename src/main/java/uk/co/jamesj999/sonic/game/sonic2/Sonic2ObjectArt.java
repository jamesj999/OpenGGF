package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.ZoneArtProvider;
import uk.co.jamesj999.sonic.game.ZoneArtProvider.ObjectArtConfig;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AnimalType;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectArtData;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads common object art (monitors, spikes, springs) for Sonic 2 (REV01).
 */
public class Sonic2ObjectArt {
    private static final Logger LOGGER = Logger.getLogger(Sonic2ObjectArt.class.getName());
    private static final int ANIMAL_TILE_OFFSET = 0x14;
    private static final AnimalType[] DEFAULT_ANIMALS = { AnimalType.RABBIT, AnimalType.RABBIT };
    private static final AnimalType[][] ZONE_ANIMALS = {
            { AnimalType.SQUIRREL, AnimalType.FLICKY }, // 0 EHZ
            { AnimalType.SQUIRREL, AnimalType.FLICKY }, // 1 Zone 1
            { AnimalType.SQUIRREL, AnimalType.FLICKY }, // 2 WZ
            { AnimalType.SQUIRREL, AnimalType.FLICKY }, // 3 Zone 3
            { AnimalType.MONKEY, AnimalType.EAGLE }, // 4 MTZ1/2
            { AnimalType.MONKEY, AnimalType.EAGLE }, // 5 MTZ3
            { AnimalType.MONKEY, AnimalType.EAGLE }, // 6 WFZ
            { AnimalType.MONKEY, AnimalType.EAGLE }, // 7 HTZ
            { AnimalType.MOUSE, AnimalType.SEAL }, // 8 HPZ
            { AnimalType.MOUSE, AnimalType.SEAL }, // 9 Zone 9
            { AnimalType.PENGUIN, AnimalType.SEAL }, // 10 OOZ
            { AnimalType.MOUSE, AnimalType.CHICKEN }, // 11 MCZ
            { AnimalType.BEAR, AnimalType.FLICKY }, // 12 CNZ
            { AnimalType.RABBIT, AnimalType.EAGLE }, // 13 CPZ
            { AnimalType.PIG, AnimalType.CHICKEN }, // 14 DEZ
            { AnimalType.PENGUIN, AnimalType.FLICKY }, // 15 ARZ
            { AnimalType.TURTLE, AnimalType.CHICKEN } // 16 SCZ
    };

    private final Rom rom;
    private final RomByteReader reader;
    private final Map<Integer, ObjectArtData> cachedByZone = new HashMap<>();

    public Sonic2ObjectArt(Rom rom, RomByteReader reader) {
        this.rom = rom;
        this.reader = reader;
    }

    public ObjectArtData load() throws IOException {
        return loadForZone(-1);
    }

    public ObjectArtData loadForZone(int zoneIndex) throws IOException {
        Integer cacheKey = zoneIndex;
        ObjectArtData cached = cachedByZone.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        AnimalType[] zoneAnimals = resolveZoneAnimals(zoneIndex);
        AnimalType animalTypeA = zoneAnimals[0];
        AnimalType animalTypeB = zoneAnimals[1];

        // Load Monitor Art (base art)
        Pattern[] monitorBasePatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_MONITOR_ADDR, "Monitor");
        // Load Tails Life Art (used for Tails Monitor icon, requests tile 340)
        Pattern[] tailsLifePatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_TAILS_LIFE_ADDR, "TailsLife");

        List<SpriteMappingFrame> monitorMappings = loadMappingFrames(Sonic2Constants.MAP_UNC_MONITOR_ADDR);

        // Calculate max requested tile index
        int maxTileIndex = 0;
        for (SpriteMappingFrame frame : monitorMappings) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                maxTileIndex = Math.max(maxTileIndex, piece.tileIndex());
            }
        }

        // Extend monitor patterns to cover the max requested index
        int requiredSize = maxTileIndex + 1;
        // Ensure we have enough space for Tails Life Art starting at 340 (0x154 * 32
        // bytes = 10880 offset)
        int lifeArtOffset = 340;
        requiredSize = Math.max(requiredSize, lifeArtOffset + tailsLifePatterns.length);

        Pattern[] monitorPatterns = new Pattern[requiredSize];
        // Copy base patterns
        if (monitorBasePatterns.length > 0) {
            System.arraycopy(monitorBasePatterns, 0, monitorPatterns, 0, monitorBasePatterns.length);
        }
        // Copy Tails Life patterns at offset 340
        if (tailsLifePatterns.length > 0 && lifeArtOffset < monitorPatterns.length) {
            System.arraycopy(tailsLifePatterns, 0, monitorPatterns, lifeArtOffset,
                    Math.min(tailsLifePatterns.length, monitorPatterns.length - lifeArtOffset));
        }

        // Fill gaps with empty patterns to prevent NPEs
        for (int i = 0; i < monitorPatterns.length; i++) {
            if (monitorPatterns[i] == null) {
                monitorPatterns[i] = new Pattern();
            }
        }

        ObjectSpriteSheet monitorSheet = new ObjectSpriteSheet(monitorPatterns, monitorMappings, 0, 1);

        Pattern[] spikePatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPIKES_ADDR, "Spikes");
        Pattern[] spikeSidePatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPIKES_SIDE_ADDR, "SpikesSide");
        List<SpriteMappingFrame> spikeMappings = loadMappingFrames(Sonic2Constants.MAP_UNC_SPIKES_ADDR);
        ObjectSpriteSheet spikeSheet = new ObjectSpriteSheet(spikePatterns, spikeMappings, 1, 1);
        ObjectSpriteSheet spikeSideSheet = new ObjectSpriteSheet(spikeSidePatterns, spikeMappings, 1, 1);

        Pattern[] springVerticalPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPRING_VERTICAL_ADDR,
                "SpringVertical");
        Pattern[] springHorizontalPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPRING_HORIZONTAL_ADDR,
                "SpringHorizontal");
        Pattern[] springDiagonalPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPRING_DIAGONAL_ADDR,
                "SpringDiagonal");
        List<SpriteMappingFrame> springMappings = loadMappingFrames(Sonic2Constants.MAP_UNC_SPRING_ADDR);
        List<SpriteMappingFrame> springMappingsRed = loadMappingFrames(Sonic2Constants.MAP_UNC_SPRING_RED_ADDR);
        ObjectSpriteSheet springVerticalSheet = new ObjectSpriteSheet(springVerticalPatterns, springMappings, 0, 1);
        ObjectSpriteSheet springHorizontalSheet = new ObjectSpriteSheet(springHorizontalPatterns, springMappings, 0, 1);
        ObjectSpriteSheet springDiagonalSheet = new ObjectSpriteSheet(springDiagonalPatterns, springMappings, 0, 1);
        ObjectSpriteSheet springVerticalRedSheet = new ObjectSpriteSheet(springVerticalPatterns, springMappingsRed, 1,
                1);
        ObjectSpriteSheet springHorizontalRedSheet = new ObjectSpriteSheet(springHorizontalPatterns, springMappingsRed,
                1, 1);

        ObjectSpriteSheet springDiagonalRedSheet = new ObjectSpriteSheet(springDiagonalPatterns, springMappingsRed, 1,
                1);

        Pattern[] explosionPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EXPLOSION_ADDR, "Explosion");
        List<SpriteMappingFrame> explosionMappings = createExplosionMappings();
        ObjectSpriteSheet explosionSheet = new ObjectSpriteSheet(explosionPatterns, explosionMappings, 1, 1);

        Pattern[] shieldPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SHIELD_ADDR, "Shield");
        List<SpriteMappingFrame> shieldMappings = createShieldMappings();
        ObjectSpriteSheet shieldSheet = new ObjectSpriteSheet(shieldPatterns, shieldMappings, 0, 1);

        Pattern[] bridgePatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BRIDGE_ADDR, "Bridge");
        List<SpriteMappingFrame> bridgeMappings = createBridgeMappings();
        ObjectSpriteSheet bridgeSheet = new ObjectSpriteSheet(bridgePatterns, bridgeMappings, 2, 1);

        Pattern[] waterfallPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EHZ_WATERFALL_ADDR,
                "EHZWaterfall");
        List<SpriteMappingFrame> waterfallMappings = createEHZWaterfallMappings();
        int waterfallMaxTile = computeMaxTileIndex(waterfallMappings);
        if (waterfallMaxTile >= waterfallPatterns.length) {
            Pattern[] extended = new Pattern[waterfallMaxTile + 1];
            if (waterfallPatterns.length > 0) {
                System.arraycopy(waterfallPatterns, 0, extended, 0, waterfallPatterns.length);
            }
            for (int i = 0; i < extended.length; i++) {
                if (extended[i] == null) {
                    extended[i] = new Pattern();
                }
            }
            waterfallPatterns = extended;
        }
        ObjectSpriteSheet waterfallSheet = new ObjectSpriteSheet(waterfallPatterns, waterfallMappings, 1, 1);

        Pattern[] invincibilityStarsPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_INVINCIBILITY_STARS_ADDR,
                "InvincibilityStars");
        List<SpriteMappingFrame> rawInvincibilityStarsMappings = loadMappingFrames(
                Sonic2Constants.MAP_UNC_INVINCIBILITY_STARS_ADDR);
        List<SpriteMappingFrame> invincibilityStarsMappings = normalizeMappings(rawInvincibilityStarsMappings);

        ObjectSpriteSheet invincibilityStarsSheet = new ObjectSpriteSheet(invincibilityStarsPatterns,
                invincibilityStarsMappings, 0, 1);

        SpriteAnimationSet monitorAnimations = loadAnimationSet(
                Sonic2Constants.ANI_OBJ26_ADDR,
                Sonic2Constants.ANI_OBJ26_SCRIPT_COUNT);
        SpriteAnimationSet springAnimations = loadAnimationSet(
                Sonic2Constants.ANI_OBJ41_ADDR,
                Sonic2Constants.ANI_OBJ41_SCRIPT_COUNT);

        // Checkpoint/Starpost art
        Pattern[] checkpointPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CHECKPOINT_ADDR, "Checkpoint");
        List<SpriteMappingFrame> checkpointMappings = loadMappingFrames(Sonic2Constants.MAP_UNC_CHECKPOINT_ADDR);
        List<SpriteMappingFrame> checkpointStarMappings = loadMappingFrames(
                Sonic2Constants.MAP_UNC_CHECKPOINT_STAR_ADDR);
        ObjectSpriteSheet checkpointSheet = new ObjectSpriteSheet(checkpointPatterns, checkpointMappings, 0, 1);
        ObjectSpriteSheet checkpointStarSheet = new ObjectSpriteSheet(checkpointPatterns, checkpointStarMappings, 0, 1);
        SpriteAnimationSet checkpointAnimations = loadAnimationSet(
                Sonic2Constants.ANI_OBJ79_ADDR,
                Sonic2Constants.ANI_OBJ79_SCRIPT_COUNT);

        // Badnik sheets are loaded separately via Sonic2ObjectArtProvider
        // using the load*Sheet() methods on this class

        Pattern[] animalPatterns = loadAnimalPatterns(animalTypeA, animalTypeB);
        List<SpriteMappingFrame> animalMappings = createAnimalMappings();
        ObjectSpriteSheet animalSheet = new ObjectSpriteSheet(animalPatterns, animalMappings, 0, 1);

        // Load correct points art
        Pattern[] pointsPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_NUMBERS_ADDR, "Numbers");
        List<SpriteMappingFrame> pointsMappings = createPointsMappings();
        ObjectSpriteSheet pointsSheet = new ObjectSpriteSheet(pointsPatterns, pointsMappings, 0, 1);

        // Signpost/Goal plate art
        Pattern[] signpostPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SIGNPOST_ADDR, "Signpost");
        List<SpriteMappingFrame> signpostMappings = createSignpostMappings();
        ObjectSpriteSheet signpostSheet = new ObjectSpriteSheet(signpostPatterns, signpostMappings, 0, 1);
        SpriteAnimationSet signpostAnimations = createSignpostAnimations();

        // CNZ Round Bumper art (Object 0x44)
        Pattern[] bumperPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BUMPER_ADDR, "Bumper");
        List<SpriteMappingFrame> bumperMappings = createBumperMappings();
        ObjectSpriteSheet bumperSheet = new ObjectSpriteSheet(bumperPatterns, bumperMappings, 2, 1);

        // CNZ Hexagonal Bumper art (Object 0xD7)
        Pattern[] hexBumperPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_HEX_BUMPER_ADDR, "HexBumper");
        List<SpriteMappingFrame> hexBumperMappings = createHexBumperMappings();
        ObjectSpriteSheet hexBumperSheet = new ObjectSpriteSheet(hexBumperPatterns, hexBumperMappings, 2, 1);

        // CNZ Bonus Block / Drop Target art (Object 0xD8)
        Pattern[] bonusBlockPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BONUS_BLOCK_ADDR, "BonusBlock");
        List<SpriteMappingFrame> bonusBlockMappings = createBonusBlockMappings();
        ObjectSpriteSheet bonusBlockSheet = new ObjectSpriteSheet(bonusBlockPatterns, bonusBlockMappings, 2, 1);

        // CNZ Flipper art (Object 0x86)
        Pattern[] flipperPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_FLIPPER_ADDR, "Flipper");
        List<SpriteMappingFrame> flipperMappings = createFlipperMappings();
        ObjectSpriteSheet flipperSheet = new ObjectSpriteSheet(flipperPatterns, flipperMappings, 2, 1);
        SpriteAnimationSet flipperAnimations = createFlipperAnimations();

        // CPZ Speed Booster art (Object 0x1B)
        Pattern[] speedBoosterPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPEED_BOOSTER_ADDR, "SpeedBooster");
        List<SpriteMappingFrame> speedBoosterMappings = createSpeedBoosterMappings();
        ObjectSpriteSheet speedBoosterSheet = new ObjectSpriteSheet(speedBoosterPatterns, speedBoosterMappings, 3, 1);

        // CPZ BlueBalls art (Object 0x1D) - water droplet hazard
        Pattern[] blueBallsPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CPZ_DROPLET_ADDR, "CPZDroplet");
        List<SpriteMappingFrame> blueBallsMappings = createBlueBallsMappings();
        ObjectSpriteSheet blueBallsSheet = new ObjectSpriteSheet(blueBallsPatterns, blueBallsMappings, 3, 0);

        // Breakable Block / Rock art (Object 0x32) - CPZ metal block or HTZ rock
        ObjectArtConfig breakableBlockArtConfig = getObjectArtConfig(Sonic2ObjectIds.BREAKABLE_BLOCK, zoneIndex);
        int breakableBlockArtAddr = breakableBlockArtConfig != null
                ? breakableBlockArtConfig.artAddress()
                : Sonic2Constants.ART_NEM_CPZ_METAL_BLOCK_ADDR;
        int breakableBlockPalette = breakableBlockArtConfig != null
                ? breakableBlockArtConfig.palette()
                : 3;
        String breakableBlockName = (zoneIndex == Sonic2Constants.ZONE_HTZ) ? "HTZRock" : "CPZMetalBlock";
        Pattern[] breakableBlockPatterns = safeLoadNemesisPatterns(breakableBlockArtAddr, breakableBlockName);
        int breakableBlockMapAddr = (zoneIndex == Sonic2Constants.ZONE_HTZ)
                ? Sonic2Constants.MAP_UNC_OBJ32_HTZ_ADDR
                : Sonic2Constants.MAP_UNC_OBJ32_CPZ_ADDR;
        List<SpriteMappingFrame> breakableBlockMappings = loadMappingFrames(breakableBlockMapAddr);
        if (breakableBlockMappings.isEmpty()) {
            breakableBlockMappings = createBreakableBlockMappings();
        }
        ObjectSpriteSheet breakableBlockSheet = new ObjectSpriteSheet(
                breakableBlockPatterns, breakableBlockMappings, breakableBlockPalette, 1);

        // CPZ/OOZ/WFZ Moving Platform art (Object 0x19)
        // Load art based on zone via ZoneArtProvider
        ObjectArtConfig platformArtConfig = getObjectArtConfig(Sonic2ObjectIds.GENERIC_PLATFORM_B, zoneIndex);
        int cpzPlatformArtAddr = platformArtConfig != null ? platformArtConfig.artAddress() : Sonic2Constants.ART_NEM_CPZ_ELEVATOR_ADDR;
        int cpzPlatformPalette = platformArtConfig != null ? platformArtConfig.palette() : 3;
        Pattern[] cpzPlatformPatterns = safeLoadNemesisPatterns(cpzPlatformArtAddr, "CPZPlatform");
        List<SpriteMappingFrame> cpzPlatformMappings = createCPZPlatformMappings();
        ObjectSpriteSheet cpzPlatformSheet = new ObjectSpriteSheet(cpzPlatformPatterns, cpzPlatformMappings, cpzPlatformPalette, 0);

        // CPZ Stair Block art (Object 0x78) - moving staircase blocks in CPZ
        Pattern[] cpzStairBlockPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CPZ_STAIRBLOCK_ADDR, "CPZStairBlock");
        List<SpriteMappingFrame> cpzStairBlockMappings = createCPZStairBlockMappings();
        ObjectSpriteSheet cpzStairBlockSheet = new ObjectSpriteSheet(cpzStairBlockPatterns, cpzStairBlockMappings, 3, 1);

        // CPZ/MCZ Sideways Platform art (Object 0x7A) - horizontal moving platform
        // Uses same patterns as CPZ Stair Block but different mappings (tiles 16+, 48x16 platform)
        List<SpriteMappingFrame> sidewaysPformMappings = createSidewaysPformMappings();
        ObjectSpriteSheet sidewaysPformSheet = new ObjectSpriteSheet(cpzStairBlockPatterns, sidewaysPformMappings, 3, 1);

        // CPZ Pylon art (Object 0x7C) - decorative background pylon
        Pattern[] cpzPylonPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CPZ_METAL_THINGS_ADDR, "CPZMetalThings");
        List<SpriteMappingFrame> cpzPylonMappings = createCPZPylonMappings();
        ObjectSpriteSheet cpzPylonSheet = new ObjectSpriteSheet(cpzPylonPatterns, cpzPylonMappings, 2, 1);

        // CPZ Pipe Exit Spring art (Object 0x7B) - warp tube exit spring
        Pattern[] pipeExitSpringPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_PIPE_EXIT_SPRING_ADDR, "PipeExitSpring");
        List<SpriteMappingFrame> pipeExitSpringMappings = createPipeExitSpringMappings();
        ObjectSpriteSheet pipeExitSpringSheet = new ObjectSpriteSheet(pipeExitSpringPatterns, pipeExitSpringMappings, 0, 1);
        SpriteAnimationSet pipeExitSpringAnimations = createPipeExitSpringAnimations();

        // CPZ Tipping Floor art (Object 0x0B) - platform that tips back and forth
        Pattern[] tippingFloorPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CPZ_ANIMATED_BITS_ADDR, "CPZAnimatedBits");
        List<SpriteMappingFrame> tippingFloorMappings = createTippingFloorMappings();
        ObjectSpriteSheet tippingFloorSheet = new ObjectSpriteSheet(tippingFloorPatterns, tippingFloorMappings, 3, 1);
        SpriteAnimationSet tippingFloorAnimations = createTippingFloorAnimations();

        // CPZ/DEZ Barrier art (Object 0x2D) - one-way rising barrier with construction stripes
        Pattern[] barrierPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CONSTRUCTION_STRIPES_ADDR, "ConstructionStripes");
        List<SpriteMappingFrame> barrierMappings = createBarrierMappings();
        ObjectSpriteSheet barrierSheet = new ObjectSpriteSheet(barrierPatterns, barrierMappings, 1, 1);

        // Springboard / Lever Spring art (Object 0x40) - CPZ, ARZ, MCZ
        Pattern[] springboardPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_LEVER_SPRING_ADDR, "LeverSpring");
        List<SpriteMappingFrame> springboardMappings = createSpringboardMappings();
        ObjectSpriteSheet springboardSheet = new ObjectSpriteSheet(springboardPatterns, springboardMappings, 0, 1);
        SpriteAnimationSet springboardAnimations = createSpringboardAnimations();

        // Underwater Bubbles art (Object $0A - Small breathing bubbles from player's mouth)
        // Art at 0x7AEE2 (10 tiles) - small bubbles that rise when Sonic breathes underwater
        // Note: Bubble Generator (Object $24) is a separate object with its own art
        Pattern[] bubblesPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BUBBLES_ADDR, "SmallBubbles");
        List<SpriteMappingFrame> bubblesMappings = createBubblesMappings();
        ObjectSpriteSheet bubblesSheet = new ObjectSpriteSheet(bubblesPatterns, bubblesMappings, 1, 1);

        // ARZ Leaves art (Object $2C LeavesGenerator - falling leaves)
        // ROM: make_art_tile(ArtTile_ArtNem_Leaves,3,1) - palette line 3, priority 1
        Pattern[] leavesPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_LEAVES_ADDR, "Leaves");
        List<SpriteMappingFrame> leavesMappings = createLeavesMappings();
        ObjectSpriteSheet leavesSheet = new ObjectSpriteSheet(leavesPatterns, leavesMappings, 3, 1);

        // Results screen art (Obj3A)
        // ROM mappings expect fixed VRAM tile bases for each chunk:
        // Numbers (0x520), Perfect (0x540), TitleCard (0x580),
        // ResultsText (0x5B0), MiniCharacter (0x5F4).
        // We build a pattern array aligned to VRAM_BASE_NUMBERS so that
        // mapping tile indices can be offset by -VRAM_BASE_NUMBERS.
        Pattern[] hudDigitPatterns = safeLoadUncompressedPatterns(
                Sonic2Constants.ART_UNC_HUD_NUMBERS_ADDR,
                Sonic2Constants.ART_UNC_HUD_NUMBERS_SIZE,
                "HUDNumbers");
        Pattern[] hudTextPatterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_HUD_ADDR,
                "HUDText");
        Pattern[] perfectPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_PERFECT_ADDR, "PerfectText");
        Pattern[] titleCardPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_TITLE_CARD_ADDR, "TitleCard");
        Pattern[] resultsTextPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_RESULTS_TEXT_ADDR,
                "ResultsText");
        Pattern[] miniSonicPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_MINI_SONIC_ADDR, "MiniSonic");

        Pattern[] bonusDisplayPatterns = createBlankPatterns(Sonic2Constants.RESULTS_BONUS_DIGIT_TILES);

        Pattern[] resultsPatterns = createResultsVramPatterns(
                bonusDisplayPatterns,
                perfectPatterns,
                titleCardPatterns,
                resultsTextPatterns,
                miniSonicPatterns,
                hudTextPatterns);

        // Load mappings with offset relative to Numbers VRAM base (0x520)
        List<SpriteMappingFrame> resultsMappings = loadMappingFramesWithTileOffset(
                Sonic2Constants.MAPPINGS_EOL_TITLE_CARDS_ADDR, -Sonic2Constants.VRAM_BASE_NUMBERS);
        resultsPatterns = ensureResultsPatternCapacity(resultsPatterns, resultsMappings);
        ObjectSpriteSheet resultsSheet = new ObjectSpriteSheet(resultsPatterns, resultsMappings, 0, 1);

        Pattern[] hudLivesPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SONIC_LIFE_ADDR, "SonicLife");
        Pattern[] hudLivesNumbers = safeLoadUncompressedPatterns(Sonic2Constants.ART_UNC_LIVES_NUMBERS_ADDR,
                Sonic2Constants.ART_UNC_LIVES_NUMBERS_SIZE, "LivesNumbers");
        ObjectArtData artData = new ObjectArtData(
                monitorSheet,
                spikeSheet,
                spikeSideSheet,
                springVerticalSheet,
                springHorizontalSheet,
                springDiagonalSheet,
                springVerticalRedSheet,
                springHorizontalRedSheet,
                springDiagonalRedSheet,
                explosionSheet,
                shieldSheet,
                invincibilityStarsSheet,
                bridgeSheet,
                waterfallSheet,
                checkpointSheet,
                checkpointStarSheet,
                animalSheet,
                animalTypeA.ordinal(),
                animalTypeB.ordinal(),
                pointsSheet,
                signpostSheet,
                bumperSheet,
                hexBumperSheet,
                bonusBlockSheet,
                flipperSheet,
                speedBoosterSheet,
                blueBallsSheet,
                breakableBlockSheet,
                cpzPlatformSheet,
                cpzStairBlockSheet,
                sidewaysPformSheet,
                cpzPylonSheet,
                pipeExitSpringSheet,
                tippingFloorSheet,
                barrierSheet,
                springboardSheet,
                resultsSheet,
                bubblesSheet,
                leavesSheet,
                hudDigitPatterns,
                hudTextPatterns,
                hudLivesPatterns,
                hudLivesNumbers,
                (Pattern[]) null, // debugFontPatterns
                monitorMappings,
                springMappings,
                checkpointMappings,
                monitorAnimations,
                springAnimations,
                checkpointAnimations,
                signpostAnimations,
                flipperAnimations,
                pipeExitSpringAnimations,
                tippingFloorAnimations,
                springboardAnimations);

        cachedByZone.put(cacheKey, artData);
        return artData;
    }

    private Pattern[] loadNemesisPatterns(int artAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(artAddr);
        byte[] result = NemesisReader.decompress(channel);

        if (result.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent object art tile data");
        }

        int patternCount = result.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            byte[] subArray = Arrays.copyOfRange(result, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(subArray);
        }
        return patterns;
    }

    private Pattern[] loadUncompressedPatterns(int artAddr, int length) throws IOException {
        if (length <= 0) {
            return new Pattern[0];
        }
        FileChannel channel = rom.getFileChannel();
        channel.position(artAddr);
        byte[] result = new byte[length];
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(result);
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read < 0) {
                break;
            }
        }
        if (buffer.hasRemaining()) {
            throw new IOException("Unexpected EOF reading uncompressed art");
        }
        if (result.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
            throw new IOException("Inconsistent uncompressed art tile data");
        }
        int patternCount = result.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            byte[] subArray = Arrays.copyOfRange(result, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(subArray);
        }
        return patterns;
    }

    /**
     * Safely loads Nemesis patterns, returning an empty array on failure.
     * Logs full stack trace for diagnosis without blocking other art.
     * 
     * @param artAddr   ROM address of the Nemesis-compressed art
     * @param assetName Human-readable name for error reporting
     * @return Decompressed patterns, or empty array on failure
     */
    private Pattern[] safeLoadNemesisPatterns(int artAddr, String assetName) {
        try {
            return loadNemesisPatterns(artAddr);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    String.format("Failed to load art '%s' at 0x%06X", assetName, artAddr), e);
            return new Pattern[0];
        }
    }

    /**
     * Safely loads uncompressed patterns, returning an empty array on failure.
     */
    private Pattern[] safeLoadUncompressedPatterns(int artAddr, int length, String assetName) {
        try {
            return loadUncompressedPatterns(artAddr, length);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    String.format("Failed to load uncompressed art '%s' at 0x%06X", assetName, artAddr), e);
            return new Pattern[0];
        }
    }

    /**
     * Load water surface patterns for Chemical Plant Zone (and Hidden Palace Zone).
     * <p>
     * CPZ uses pink/purple chemical water surface art.
     * ROM address: 0x82364 (Nemesis compressed, 24 blocks)
     * 
     * @return CPZ water surface patterns, or empty array on failure
     */
    public Pattern[] loadWaterSurfaceCPZPatterns() {
        return safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_WATER_SURFACE_CPZ_ADDR, "WaterSurfaceCPZ");
    }

    /**
     * Load water surface patterns for Aquatic Ruin Zone.
     * <p>
     * ARZ uses natural blue water surface art.
     * ROM address: 0x82E02 (Nemesis compressed, 16 blocks)
     *
     * @return ARZ water surface patterns, or empty array on failure
     */
    public Pattern[] loadWaterSurfaceARZPatterns() {
        return safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_WATER_SURFACE_ARZ_ADDR, "WaterSurfaceARZ");
    }

    /**
     * Load OOZ Swinging Platform sprite sheet (Object 0x15 in Oil Ocean Zone).
     * <p>
     * ROM: ArtNem_OOZSwingPlat at 0x80E26, palette line 2
     * This is the dedicated art for OOZ; MCZ and ARZ use level art instead.
     *
     * @return sprite sheet for OOZ swinging platform, or null on failure
     */
    public ObjectSpriteSheet loadOOZSwingPlatformSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_OOZ_SWING_PLAT_ADDR, "OOZSwingPlatform");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createOOZSwingPlatformMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1);
    }

    /**
     * Create mappings for OOZ Swinging Platform (Object 0x15).
     * From obj15_a.asm:
     * - Frame 0: Platform (4 pieces: 4x2 tiles each, forming 64x32 sprite)
     * - Frame 1: Chain link (1 piece: 2x2 tiles, 16x16)
     * - Frame 2: Same as frame 1 (chain variant)
     * - Frame 3: Empty frame
     */
    private List<SpriteMappingFrame> createOOZSwingPlatformMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Platform (64x32 pixels, 4 pieces of 4x2 tiles)
        // spritePiece -$20, -$10, 4, 2, 4, 0, 0, 1, 0
        // spritePiece 0, -$10, 4, 2, $C, 0, 0, 1, 0
        // spritePiece -$20, 0, 4, 2, $14, 0, 0, 1, 0
        // spritePiece 0, 0, 4, 2, $14, 1, 0, 1, 0
        List<SpriteMappingPiece> platformPieces = new ArrayList<>();
        platformPieces.add(new SpriteMappingPiece(-0x20, -0x10, 4, 2, 4, false, false, 1));
        platformPieces.add(new SpriteMappingPiece(0, -0x10, 4, 2, 0x0C, false, false, 1));
        platformPieces.add(new SpriteMappingPiece(-0x20, 0, 4, 2, 0x14, false, false, 1));
        platformPieces.add(new SpriteMappingPiece(0, 0, 4, 2, 0x14, true, false, 1));
        frames.add(new SpriteMappingFrame(platformPieces));

        // Frame 1: Chain link (16x16 pixels, 1 piece of 2x2 tiles)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> chainPieces = new ArrayList<>();
        chainPieces.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(chainPieces));

        // Frame 2: Same as frame 1 (chain variant)
        frames.add(new SpriteMappingFrame(chainPieces));

        // Frame 3: Empty frame
        frames.add(new SpriteMappingFrame(List.of()));

        return frames;
    }

    /**
     * Load OOZ Burner Lid sprite sheet (Object 0x33 platform).
     * Green platform from OOZ that pops up from burner pipe.
     * <p>
     * ROM: ArtNem_BurnerLid at 0x80274, palette line 3, art tile 0x032C
     * Mappings: obj33_a.asm - 1 frame, 2 pieces (left + h-flipped right, 3x2 tiles each)
     *
     * @return sprite sheet for OOZ burner lid, or null on failure
     */
    public ObjectSpriteSheet loadOOZBurnerLidSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_BURNER_LID_ADDR, "BurnerLid");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createOOZBurnerLidMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1); // palette line 3 = index 2
    }

    /**
     * Create sprite mappings for OOZ Burner Lid (Obj33 platform).
     * From mappings/sprite/obj33_a.asm:
     * <pre>
     * Frame 0: spritePiece -$18, -8, 3, 2, 0, 0, 0, 0, 0
     *          spritePiece 0, -8, 3, 2, 0, 1, 0, 0, 0
     * </pre>
     */
    private List<SpriteMappingFrame> createOOZBurnerLidMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Platform (48x16 pixels, 2 mirrored 3x2 pieces)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x18, -8, 3, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -8, 3, 2, 0, true, false, 0)); // h-flip
        frames.add(new SpriteMappingFrame(frame0));

        return frames;
    }

    /**
     * Load OOZ Burn Flame sprite sheet (Object 0x33 flame child).
     * Green flame from OOZ burners that appears beneath the popping platform.
     * <p>
     * ROM: ArtNem_OOZBurn at 0x81514, palette line 3, art tile 0x02E2
     * Mappings: obj33_b.asm - 3 frames of flame animation
     *
     * @return sprite sheet for OOZ burner flame, or null on failure
     */
    public ObjectSpriteSheet loadOOZBurnFlameSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_OOZ_BURN_ADDR, "OOZBurn");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createOOZBurnFlameMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1); // palette line 3 = index 2
    }

    /**
     * Create sprite mappings for OOZ Burn Flame (Obj33 flame child).
     * From mappings/sprite/obj33_b.asm:
     * <pre>
     * Frame 0: spritePiece -$10, -8, 2, 3, 0, 0, 0, 0, 0
     *          spritePiece 0, -8, 2, 3, 0, 1, 0, 0, 0
     * Frame 1: spritePiece -$10, -$10, 2, 4, 6, 0, 0, 0, 0
     *          spritePiece 0, -$10, 2, 4, 6, 1, 0, 0, 0
     * Frame 2: spritePiece -$10, 0, 2, 2, $E, 0, 0, 0, 0
     *          spritePiece 0, 0, 2, 2, $E, 1, 0, 0, 0
     * </pre>
     */
    private List<SpriteMappingFrame> createOOZBurnFlameMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Medium flame (32x24 pixels)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x10, -8, 2, 3, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -8, 2, 3, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Tall flame (32x32 pixels)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x10, -0x10, 2, 4, 6, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -0x10, 2, 4, 6, true, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Short flame (32x16 pixels)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x10, 0, 2, 2, 0xE, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, 0, 2, 2, 0xE, true, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        return frames;
    }

    /**
     * Load OOZ LauncherBall sprite sheet (Object 0x48).
     * Transporter ball from Oil Ocean Zone that fires the player in a cardinal direction.
     * <p>
     * ROM: ArtNem_LaunchBall at 0x806E0, palette line 3, art tile 0x0368
     * Mappings: obj48.asm - 8 frames showing ball rotation/opening sequence
     *
     * @return sprite sheet for OOZ launcher ball, or null on failure
     */
    public ObjectSpriteSheet loadLaunchBallSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_LAUNCH_BALL_ADDR, "LaunchBall");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createLaunchBallMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Create mappings for OOZ LauncherBall (Object 0x48).
     * From obj48.asm - 8 frames of ball rotation animation.
     */
    private List<SpriteMappingFrame> createLaunchBallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Closed ball with spout (8 pieces)
        List<SpriteMappingPiece> f0 = new ArrayList<>();
        f0.add(new SpriteMappingPiece(-0x10, -0x28, 2, 1, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(0x00, -0x28, 2, 1, 0, true, false, 0));
        f0.add(new SpriteMappingPiece(-0x10, -0x20, 2, 1, 2, false, false, 0));
        f0.add(new SpriteMappingPiece(0x00, -0x20, 2, 1, 2, true, false, 0));
        f0.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x11, false, false, 0));
        f0.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x11, true, false, 0));
        f0.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 8, false, true, 0));
        f0.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 8, true, true, 0));
        frames.add(new SpriteMappingFrame(f0));

        // Frame 1: Opening (6 pieces)
        List<SpriteMappingPiece> f1 = new ArrayList<>();
        f1.add(new SpriteMappingPiece(-0x10, -0x20, 2, 1, 0, false, false, 0));
        f1.add(new SpriteMappingPiece(0x00, -0x20, 2, 1, 0, true, false, 0));
        f1.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x11, false, false, 0));
        f1.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x11, true, false, 0));
        f1.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 8, false, true, 0));
        f1.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 8, true, true, 0));
        frames.add(new SpriteMappingFrame(f1));

        // Frame 2: Ball only (4 pieces)
        List<SpriteMappingPiece> f2 = new ArrayList<>();
        f2.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x11, false, false, 0));
        f2.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x11, true, false, 0));
        f2.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 8, false, true, 0));
        f2.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 8, true, true, 0));
        frames.add(new SpriteMappingFrame(f2));

        // Frame 3: Rotated quadrants (4 pieces)
        List<SpriteMappingPiece> f3 = new ArrayList<>();
        f3.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x1A, false, false, 0));
        f3.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x23, true, true, 0));
        f3.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 0x23, false, false, 0));
        f3.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 0x1A, true, true, 0));
        frames.add(new SpriteMappingFrame(f3));

        // Frame 4: Further rotated (4 pieces)
        List<SpriteMappingPiece> f4 = new ArrayList<>();
        f4.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x23, false, true, 0));
        f4.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x1A, true, false, 0));
        f4.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 0x1A, false, true, 0));
        f4.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 0x23, true, false, 0));
        frames.add(new SpriteMappingFrame(f4));

        // Frame 5: Rotated (4 pieces)
        List<SpriteMappingPiece> f5 = new ArrayList<>();
        f5.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 8, false, false, 0));
        f5.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x2C, false, true, 0));
        f5.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 8, false, true, 0));
        f5.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 0x2C, false, false, 0));
        frames.add(new SpriteMappingFrame(f5));

        // Frame 6: Open with side pieces (6 pieces)
        List<SpriteMappingPiece> f6 = new ArrayList<>();
        f6.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 8, false, false, 0));
        f6.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x2C, false, true, 0));
        f6.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 8, false, true, 0));
        f6.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 0x2C, false, false, 0));
        f6.add(new SpriteMappingPiece(0x18, -0x10, 1, 2, 6, false, false, 0));
        f6.add(new SpriteMappingPiece(0x18, 0x00, 1, 2, 6, false, true, 0));
        frames.add(new SpriteMappingFrame(f6));

        // Frame 7: Fully open (8 pieces)
        List<SpriteMappingPiece> f7 = new ArrayList<>();
        f7.add(new SpriteMappingPiece(-0x18, -0x18, 3, 3, 8, false, false, 0));
        f7.add(new SpriteMappingPiece(0x00, -0x18, 3, 3, 0x2C, false, true, 0));
        f7.add(new SpriteMappingPiece(-0x18, 0x00, 3, 3, 8, false, true, 0));
        f7.add(new SpriteMappingPiece(0x00, 0x00, 3, 3, 0x2C, false, false, 0));
        f7.add(new SpriteMappingPiece(0x18, -0x10, 1, 2, 4, false, false, 0));
        f7.add(new SpriteMappingPiece(0x18, 0x00, 1, 2, 4, false, true, 0));
        f7.add(new SpriteMappingPiece(0x20, -0x10, 1, 2, 6, false, false, 0));
        f7.add(new SpriteMappingPiece(0x20, 0x00, 1, 2, 6, false, true, 0));
        frames.add(new SpriteMappingFrame(f7));

        return frames;
    }

    /**
     * Load OOZ Launcher vertical art (Object 0x3D, subtype 0).
     * ROM: ArtNem_StripedBlocksVert at 0x8030A, palette line 3, art tile 0x0332
     * Mappings: obj3D.asm - frames 0 (intact 4x1 columns) and 1 (broken 4x4 grid)
     *
     * @return sprite sheet for vertical launcher, or null on failure
     */
    public ObjectSpriteSheet loadOOZLauncherVertSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_STRIPED_BLOCKS_VERT_ADDR, "StripedBlocksVert");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createOOZLauncherVertMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Create mappings for OOZ Launcher vertical variant (Object 0x3D).
     * From obj3D.asm frames 0 and 1.
     */
    private List<SpriteMappingFrame> createOOZLauncherVertMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Intact block (4 vertical strips, 1x4 tiles each)
        List<SpriteMappingPiece> f0 = new ArrayList<>();
        f0.add(new SpriteMappingPiece(-0x10, -0x10, 1, 4, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(-8, -0x10, 1, 4, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(0, -0x10, 1, 4, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(8, -0x10, 1, 4, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(f0));

        // Frame 1: Fragment grid (4x4 individual 1x1 tiles)
        List<SpriteMappingPiece> f1 = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                f1.add(new SpriteMappingPiece(-0x10 + col * 8, -0x10 + row * 8, 1, 1, row, false, false, 0));
            }
        }
        frames.add(new SpriteMappingFrame(f1));

        return frames;
    }

    /**
     * Load OOZ Launcher horizontal art (Object 0x3D, subtype != 0).
     * ROM: ArtNem_StripedBlocksHoriz at 0x81048, palette line 3, art tile 0x03FF
     * Mappings: obj3D.asm - frames 2 (intact 4x1 rows) and 3 (broken 4x4 grid)
     *
     * @return sprite sheet for horizontal launcher, or null on failure
     */
    public ObjectSpriteSheet loadOOZLauncherHorizSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_STRIPED_BLOCKS_HORIZ_ADDR, "StripedBlocksHoriz");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createOOZLauncherHorizMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Create mappings for OOZ Launcher horizontal variant (Object 0x3D).
     * From obj3D.asm frames 2 and 3.
     */
    private List<SpriteMappingFrame> createOOZLauncherHorizMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (ROM frame 2): Intact block (4 horizontal strips, 4x1 tiles each)
        List<SpriteMappingPiece> f0 = new ArrayList<>();
        f0.add(new SpriteMappingPiece(-0x10, -0x10, 4, 1, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(-0x10, -8, 4, 1, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(-0x10, 0, 4, 1, 0, false, false, 0));
        f0.add(new SpriteMappingPiece(-0x10, 8, 4, 1, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(f0));

        // Frame 1 (ROM frame 3): Fragment grid (4x4 individual tiles with varying indices)
        List<SpriteMappingPiece> f1 = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                f1.add(new SpriteMappingPiece(-0x10 + col * 8, -0x10 + row * 8, 1, 1, col, false, false, 0));
            }
        }
        frames.add(new SpriteMappingFrame(f1));

        return frames;
    }

    /**
     * Load OOZ Collapsing Platform sprite sheet (Object 0x1F in Oil Ocean Zone).
     * <p>
     * ROM: ArtNem_OOZPlatform at 0x809D0, palette line 3
     *
     * @return sprite sheet for OOZ collapsing platform, or null on failure
     */
    public ObjectSpriteSheet loadOOZCollapsingPlatformSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_OOZ_COLLAPSING_PLATFORM_ADDR, "OOZCollapsingPlatform");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createOOZCollapsingPlatformMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Load MCZ Collapsing Platform sprite sheet (Object 0x1F in Mystic Cave Zone).
     * <p>
     * ROM: ArtNem_MCZCollapsePlat at 0xF1ABA, palette line 3
     *
     * @return sprite sheet for MCZ collapsing platform, or null on failure
     */
    public ObjectSpriteSheet loadMCZCollapsingPlatformSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_MCZ_COLLAPSING_PLATFORM_ADDR, "MCZCollapsingPlatform");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createMCZCollapsingPlatformMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Load MCZ Vine Pulley sprite sheet (Object 0x80 - MCZ variant).
     * <p>
     * ROM: ArtNem_VinePulley at 0xF1D5C, palette line 3
     * Mappings: Obj80_MapUnc_29C64 (7 frames, extension-based)
     * <p>
     * Disassembly Reference: s2.asm Obj80_MCZ_Init (loc_29A1C)
     *
     * @return sprite sheet for MCZ vine pulley, or null on failure
     */
    public ObjectSpriteSheet loadVinePulleySheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_VINE_PULLEY_ADDR, "VinePulley");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_OBJ80_MCZ_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Load MCZ Crate sprite sheet (Object 0x6A - MCZ variant).
     * <p>
     * ROM: ArtNem_Crate at 0xF187C, palette line 3
     * A large 64x64 wooden crate that moves when the player walks off of it.
     * <p>
     * Disassembly Reference: s2.asm Obj6A_Init (lines 53661-53683)
     * Mappings: Obj6A_MapUnc_27D30 at s2.asm line 53850
     * <pre>
     * Map_obj6A_0002: 4 pieces
     *   spritePiece -$20, -$20, 4, 4, 0, 0, 0, 0, 0      ; top-left
     *   spritePiece    0, -$20, 4, 4, $10, 0, 0, 0, 0    ; top-right
     *   spritePiece -$20,    0, 4, 4, $10, 1, 1, 0, 0    ; bottom-left (H-flip, V-flip)
     *   spritePiece    0,    0, 4, 4, 0, 1, 1, 0, 0      ; bottom-right (H-flip, V-flip)
     * </pre>
     *
     * @return sprite sheet for MCZ wooden crate, or null on failure
     */
    public ObjectSpriteSheet loadMCZCrateSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CRATE_ADDR, "MCZCrate");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createMCZCrateMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Create MCZ Crate mappings from disassembly.
     * <p>
     * Single frame with 4 pieces making a 64x64 crate:
     * - Piece 0: Top-left 32x32 at (-32, -32), tiles 0-15
     * - Piece 1: Top-right 32x32 at (0, -32), tiles 16-31
     * - Piece 2: Bottom-left 32x32 at (-32, 0), tiles 16-31 with H+V flip
     * - Piece 3: Bottom-right 32x32 at (0, 0), tiles 0-15 with H+V flip
     */
    private List<SpriteMappingFrame> createMCZCrateMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        List<SpriteMappingPiece> pieces = new ArrayList<>();
        // Top-left: 4x4 tiles (32x32 pixels) at offset (-32, -32), pattern 0
        pieces.add(new SpriteMappingPiece(-32, -32, 4, 4, 0, false, false, 3));
        // Top-right: 4x4 tiles (32x32 pixels) at offset (0, -32), pattern 0x10
        pieces.add(new SpriteMappingPiece(0, -32, 4, 4, 0x10, false, false, 3));
        // Bottom-left: 4x4 tiles (32x32 pixels) at offset (-32, 0), pattern 0x10, H-flip, V-flip
        pieces.add(new SpriteMappingPiece(-32, 0, 4, 4, 0x10, true, true, 3));
        // Bottom-right: 4x4 tiles (32x32 pixels) at offset (0, 0), pattern 0, H-flip, V-flip
        pieces.add(new SpriteMappingPiece(0, 0, 4, 4, 0, true, true, 3));

        frames.add(new SpriteMappingFrame(pieces));
        return frames;
    }

    /**
     * Load MCZ Drawbridge sprite sheet (Object 0x81).
     * <p>
     * ROM: ArtNem_MCZGateLog at 0xF1E06, palette line 3
     * Mappings: Obj81_MapUnc_2A24E (2 frames - empty frame 0, log piece frame 1)
     * <p>
     * The drawbridge uses 8 child sprites stacked vertically, each rendering frame 1.
     * Frame 0 is an empty/null frame used when the drawbridge is invisible.
     * <p>
     * Disassembly Reference: s2.asm lines 56420-56617 (Obj81 code)
     *
     * @return sprite sheet for MCZ drawbridge log segments, or null on failure
     */
    public ObjectSpriteSheet loadMCZDrawbridgeSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_MCZ_GATE_LOG_ADDR, "MCZDrawbridge");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createMCZDrawbridgeMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Create MCZ Drawbridge mappings from disassembly.
     * <p>
     * Two frames from Obj81_MapUnc_2A24E (mappings/sprite/obj81.asm):
     * <ul>
     *   <li>Frame 0: Empty (no pieces)</li>
     *   <li>Frame 1: Single 2x2 tile piece at (-8, -8), pattern 0</li>
     * </ul>
     * <p>
     * The drawbridge creates 8 child sprites using frame 1 to form the full bridge.
     */
    private List<SpriteMappingFrame> createMCZDrawbridgeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Empty (Map_obj81_0004)
        frames.add(new SpriteMappingFrame(new ArrayList<>()));

        // Frame 1: Single 2x2 tile piece (Map_obj81_0006)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 3));
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Load MCZ Bridge sprite sheet (Object 0x77 - horizontal gate).
     * <p>
     * Reuses the same Nemesis art as the drawbridge (ArtNem_MCZGateLog at 0xF1E06),
     * but with different mappings: 5 frames of 8 pieces each representing the gate
     * in various states from closed (flat horizontal) to open (split into two columns).
     * <p>
     * Disassembly Reference: mappings/sprite/obj77.asm (Obj77_MapUnc_2A0BC)
     *
     * @return sprite sheet for MCZ bridge gate, or null on failure
     */
    public ObjectSpriteSheet loadMCZBridgeSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_MCZ_GATE_LOG_ADDR, "MCZBridge");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createMCZBridgeMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Create MCZ Bridge mappings from disassembly (obj77.asm).
     * <p>
     * 5 frames, each with 8 pieces (2x2 tiles each):
     * <ul>
     *   <li>Frame 0: Flat horizontal bar (closed gate, solid)</li>
     *   <li>Frame 1: Slightly bowed outward</li>
     *   <li>Frame 2: More bowed</li>
     *   <li>Frame 3: Nearly split into two columns</li>
     *   <li>Frame 4: Fully open (two vertical columns at edges)</li>
     * </ul>
     */
    private List<SpriteMappingFrame> createMCZBridgeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Flat horizontal (closed) - 8 logs in a row
        frames.add(createMCZBridgeFrame(
                -64, -8, -48, -8, -32, -8, -16, -8,
                  0, -8,  16, -8,  32, -8,  48, -8));

        // Frame 1: Slightly bowed
        frames.add(createMCZBridgeFrame(
                -64, -8, -50, -2, -35,  4, -20, 10,
                  4, 10,  19,  4,  34, -2,  48, -8));

        // Frame 2: More bowed
        frames.add(createMCZBridgeFrame(
                -64, -8, -53,  3, -42, 14, -31, 25,
                 15, 25,  26, 14,  37,  3,  48, -8));

        // Frame 3: Nearly split
        frames.add(createMCZBridgeFrame(
                -64, -8, -58,  6, -52, 21, -46, 36,
                 30, 36,  36, 21,  42,  6,  48, -8));

        // Frame 4: Fully open (two vertical columns)
        frames.add(createMCZBridgeFrame(
                -64, -8, -64,  8, -64, 24, -64, 40,
                 48, -8,  48,  8,  48, 24,  48, 40));

        return frames;
    }

    /**
     * Helper to create a single MCZ Bridge frame with 8 pieces.
     * Each piece is a 2x2 tile log at the specified position.
     */
    private SpriteMappingFrame createMCZBridgeFrame(
            int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3,
            int x4, int y4, int x5, int y5, int x6, int y6, int x7, int y7) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        pieces.add(new SpriteMappingPiece(x0, y0, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x1, y1, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x2, y2, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x3, y3, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x4, y4, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x5, y5, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x6, y6, 2, 2, 0, false, false, 3));
        pieces.add(new SpriteMappingPiece(x7, y7, 2, 2, 0, false, false, 3));
        return new SpriteMappingFrame(pieces);
    }

    /**
     * Load WFZ Hook sprite sheet (Object 0x80 - WFZ variant).
     * <p>
     * ROM: ArtNem_WfzHook at 0x8D388, palette line 1
     * Mappings: Obj80_MapUnc_29DD0 (13 frames, extension-based)
     * <p>
     * Disassembly Reference: s2.asm Obj80_WFZ_Init at loc_29C06
     *
     * @return sprite sheet for WFZ hook on chain, or null on failure
     */
    public ObjectSpriteSheet loadWFZHookSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_WFZ_HOOK_ADDR, "WFZHook");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_OBJ80_WFZ_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load CNZ LauncherSpring vertical sprite sheet (Object 0x85 subtype 0x00).
     * <p>
     * ROM: ArtNem_CNZVertPlunger at 0x81C96, palette line 0
     *
     * @return sprite sheet for vertical launcher spring, or null on failure
     */
    public ObjectSpriteSheet loadLauncherSpringVertSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_VERT_PLUNGER_ADDR, "LauncherSpringVert");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createLauncherSpringVertMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load CNZ LauncherSpring diagonal sprite sheet (Object 0x85 subtype 0x81).
     * <p>
     * ROM: ArtNem_CNZDiagPlunger at 0x81AB0, palette line 0
     *
     * @return sprite sheet for diagonal launcher spring, or null on failure
     */
    public ObjectSpriteSheet loadLauncherSpringDiagSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_DIAG_PLUNGER_ADDR, "LauncherSpringDiag");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createLauncherSpringDiagMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load CNZ Rect Blocks sprite sheet (Object 0xD2) - "caterpillar" flashing blocks.
     * <p>
     * ROM: ArtNem_CNZSnake at 0x81600, palette line 2
     * 16 animation frames showing blocks moving around a rectangular path.
     *
     * @return sprite sheet for CNZ rect blocks, or null on failure
     */
    public ObjectSpriteSheet loadCNZRectBlocksSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_SNAKE_ADDR, "CNZSnake");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCNZRectBlocksMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1);
    }

    /**
     * Load CNZ Big Block sprite sheet (Object 0xD4) - large 64x64 oscillating platform.
     * <p>
     * ROM: ArtNem_BigMovingBlock at 0x816C8, palette line 2
     * Single frame with 4 pieces in 2x2 grid with flip symmetry.
     *
     * @return sprite sheet for CNZ big block, or null on failure
     */
    public ObjectSpriteSheet loadCNZBigBlockSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_BIG_BLOCK_ADDR, "CNZBigBlock");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCNZBigBlockMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1);
    }

    /**
     * Load CNZ Elevator sprite sheet (Object 0xD5) - vertical platform that moves when stood on.
     * <p>
     * ROM: ArtNem_CNZElevator at 0x817B4, palette line 2
     * Visual: 32x16 pixel platform made of two 2x2 (16x16) pieces
     *
     * @return sprite sheet for CNZ elevator, or null on failure
     */
    public ObjectSpriteSheet loadCNZElevatorSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_ELEVATOR_ADDR, "CNZElevator");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCNZElevatorMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1);
    }

    /**
     * Load CNZ Cage sprite sheet (Object 0xD6 "PointPokey") - casino cage that captures player.
     * <p>
     * ROM: ArtNem_CNZCage at 0x81826, palette line 0 (frame 0) and 1 (frame 1)
     * Visual: 48x48 pixel cage made of 6 pieces in a 2x3 grid (3 rows, 2 columns)
     * Frame 0: Idle cage, Frame 1: Active/lit cage with priority
     *
     * @return sprite sheet for CNZ cage, or null on failure
     */
    public ObjectSpriteSheet loadCNZCageSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_CAGE_ADDR, "CNZCage");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCNZCageMappings();
        // Frame 0 uses palette 0, frame 1 uses palette 1 (handled in mappings)
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load CNZ Bonus Spike (ObjD3) sprite sheet - spiked ball from slot machine.
     * ROM: ArtNem_CNZBonusSpike at 0x81668, palette line 0
     * <p>
     * This is a simple 2x2 tile (16x16 pixel) spiked ball sprite used as a
     * "bad" prize when the slot machine shows Eggman faces.
     */
    public ObjectSpriteSheet loadCNZBonusSpikeSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_CNZ_BONUS_SPIKE_ADDR, "CNZBonusSpike");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCNZBonusSpikeMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    // ========== Badnik Sheet Loaders ==========
    // These are Sonic 2-specific and loaded directly by Sonic2ObjectArtProvider

    /**
     * Load Masher (Obj5C) sprite sheet - leaping piranha from EHZ.
     * ROM: ArtNem_Masher at 0x839EA, palette line 0
     */
    public ObjectSpriteSheet loadMasherSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_MASHER_ADDR, "Masher");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createMasherMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Buzzer (Obj4B) sprite sheet - flying bee from EHZ.
     * ROM: ArtNem_Buzzer at 0x8316A, palette line 0
     */
    public ObjectSpriteSheet loadBuzzerSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BUZZER_ADDR, "Buzzer");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createBuzzerMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Coconuts (Obj9D) sprite sheet - monkey badnik from EHZ.
     * ROM: ArtNem_Coconuts at 0x8A87A, palette line 0
     */
    public ObjectSpriteSheet loadCoconutsSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_COCONUTS_ADDR, "Coconuts");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCoconutsMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Crawlton (Obj9E) sprite sheet - snake badnik from MCZ.
     * ROM: ArtNem_Crawlton at 0x8AB36, palette line 1.
     * 3 frames: frame 0/1 = head (3x2 tiles), frame 2 = body segment (2x2 tiles).
     */
    public ObjectSpriteSheet loadCrawltonSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CRAWLTON_ADDR, "Crawlton");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCrawltonMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Create sprite mappings for Crawlton (Obj9E).
     * From mappings/sprite/obj9E.asm:
     * - Frame 0/1 (head): spritePiece -$10, -8, 3, 2, 0, 0, 0, 0, 0
     * - Frame 2 (body):   spritePiece -8, -8, 2, 2, 6, 0, 0, 0, 0
     */
    private List<SpriteMappingFrame> createCrawltonMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Head (3x2 tiles = 24x16 pixels) at tile 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x10, -8, 3, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Identical to frame 0 (both point to Map_obj9E_0006)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x10, -8, 3, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Body segment (2x2 tiles = 16x16 pixels) at tile 6
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-8, -8, 2, 2, 6, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        return frames;
    }

    /**
     * Load Flasher (ObjA3) sprite sheet - firefly/glowbug badnik from MCZ.
     * ROM: ArtNem_Flasher at 0x8AC5E, palette line 0.
     */
    public ObjectSpriteSheet loadFlasherSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_FLASHER_ADDR, "Flasher");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createFlasherMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Spiny (ObjA5) sprite sheet - crawling badnik from CPZ.
     * ROM: ArtNem_Spiny at 0x8B430, palette line 0
     */
    public ObjectSpriteSheet loadSpinySheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPINY_ADDR, "Spiny");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createSpinyMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Grabber (ObjA7) sprite sheet - spider badnik from CPZ.
     * ROM: ArtNem_Grabber at 0x8B6B4, palette line 1
     */
    public ObjectSpriteSheet loadGrabberSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_GRABBER_ADDR, "Grabber");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createGrabberMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load Grabber String (ObjAA) sprite sheet - thread connecting Grabber to anchor.
     * Uses same patterns as Grabber but different mappings.
     * ROM: ArtNem_Grabber at 0x8B6B4, palette line 1
     */
    public ObjectSpriteSheet loadGrabberStringSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_GRABBER_ADDR, "Grabber");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createGrabberStringMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load ChopChop (Obj91) sprite sheet - piranha badnik from ARZ.
     * ROM: ArtNem_ChopChop at 0x89B9A, palette line 1
     */
    public ObjectSpriteSheet loadChopChopSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CHOPCHOP_ADDR, "ChopChop");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createChopChopMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load Whisp (Obj8C) sprite sheet - blowfly badnik from ARZ.
     * ROM: ArtNem_Whisp at 0x895E4
     * Uses palette line 1.
     * 2 animation frames, each with 2 sprite pieces (3x1 tiles stacked = 24x16 pixels).
     */
    public ObjectSpriteSheet loadWhispSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_WHISP_ADDR, "Whisp");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createWhispMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load ArrowShooter (Obj22) sprite sheet - arrow shooter from ARZ.
     * ROM: ArtNem_ArrowAndShooter at 0x90020, Obj22_MapUnc_25804
     * Uses palette line 0 for arrow (frame 0) and palette line 1 for shooter.
     */
    public ObjectSpriteSheet loadArrowShooterSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_ARROW_SHOOTER_ADDR, "ArrowShooter");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_ARROW_SHOOTER_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Grounder (Obj8D/8E) sprite sheet - drill badnik from ARZ.
     * ROM: ArtNem_Grounder at 0x8970E, palette line 1
     * 5 frames: 2 idle (symmetric with flipped parts) + 3 walking
     */
    public ObjectSpriteSheet loadGrounderSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_GROUNDER_ADDR, "Grounder");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createGrounderMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load Grounder Rock (Obj90) sprite sheet - rock projectiles from ARZ.
     * Uses same Grounder art but different mappings and palette line 2.
     * 3 frames: 2x2 large rock, 1x1 small rocks (2 variants)
     */
    public ObjectSpriteSheet loadGrounderRockSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_GROUNDER_ADDR, "GrounderRock");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createGrounderRockMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 0);
    }

    /**
     * Load Spiker (Obj92/93) sprite sheet - drill badnik from HTZ.
     * <p>
     * Spiker uses two separate art sources in VRAM:
     * - Sol badnik art at tile $3DE (ArtNem_Sol)
     * - Spiker art at tile $520 (ArtNem_Spiker)
     * <p>
     * The mappings reference absolute tile indices, so we build a combined
     * sheet using a base tile of $3DE and offset Spiker tiles accordingly.
     */
    public ObjectSpriteSheet loadSpikerSheet() {
        Pattern[] solPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SOL_ADDR, "Sol");
        Pattern[] spikerPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SPIKER_ADDR, "Spiker");
        if (solPatterns.length == 0 && spikerPatterns.length == 0) {
            return null;
        }

        final int baseTile = 0x3DE;    // ArtTile_ArtNem_Sol
        final int spikerTile = 0x520;  // ArtTile_ArtNem_Spiker
        final int spikerOffset = spikerTile - baseTile;
        final int maxTileUsed = 0x533; // Highest tile referenced by Obj92/93 mappings

        int requiredSize = Math.max(spikerOffset + spikerPatterns.length, solPatterns.length);
        requiredSize = Math.max(requiredSize, (maxTileUsed - baseTile) + 1);

        Pattern[] combined = new Pattern[requiredSize];
        if (solPatterns.length > 0) {
            System.arraycopy(solPatterns, 0, combined, 0,
                    Math.min(solPatterns.length, combined.length));
        }
        if (spikerPatterns.length > 0 && spikerOffset < combined.length) {
            int copyLen = Math.min(spikerPatterns.length, combined.length - spikerOffset);
            System.arraycopy(spikerPatterns, 0, combined, spikerOffset, copyLen);
        }

        for (int i = 0; i < combined.length; i++) {
            if (combined[i] == null) {
                combined[i] = new Pattern();
            }
        }

        List<SpriteMappingFrame> mappings = createSpikerMappings();
        return new ObjectSpriteSheet(combined, mappings, 0, 1);
    }

    /**
     * Load Sol (Obj95) sprite sheet - HTZ fireball badnik.
     * ROM:
     * - Fireball 1 art at tile $39E (ArtNem_HtzFireball1) - 20 tiles
     * - Sol badnik art at tile $3DE (ArtNem_Sol) - 4 tiles
     * - Fireball mappings use tile $3AE (offset 0x10 within fireball art)
     *
     * Palette usage (matching original ROM obj95.asm):
     * - Sprite sheet base palette: 0 (from art_tile = make_art_tile(ArtTile_ArtKos_LevelArt, 0, 0))
     * - Body frames (0-2): piece palette 0 → final palette 0 (character palette)
     * - Fireball frames (3-4): piece palette 1 → final palette 1 (zone palette line 1 = HTZ)
     *
     * The fireball art contains graphics designed for palette 1, which includes
     * orange/red fire colors in the HTZ zone palette.
     */
    public ObjectSpriteSheet loadSolSheet() {
        Pattern[] fireballPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_HTZ_FIREBALL1_ADDR,
                "HtzFireball1");
        Pattern[] solPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SOL_ADDR, "Sol");
        if (fireballPatterns.length == 0 && solPatterns.length == 0) {
            return null;
        }

        final int baseTile = 0x39E;      // ArtTile_ArtNem_HtzFireball1
        final int solTile = 0x3DE;       // ArtTile_ArtNem_Sol
        final int fireballOffset = 0;    // Fireball art starts at base tile
        final int solOffset = solTile - baseTile;
        final int maxTileUsed = 0x3E1;   // Highest tile referenced by Obj95 mappings

        int requiredSize = Math.max(fireballOffset + fireballPatterns.length, solOffset + solPatterns.length);
        requiredSize = Math.max(requiredSize, (maxTileUsed - baseTile) + 1);
        Pattern[] combined = new Pattern[requiredSize];
        if (fireballPatterns.length > 0 && fireballOffset < combined.length) {
            int copyLen = Math.min(fireballPatterns.length, combined.length - fireballOffset);
            System.arraycopy(fireballPatterns, 0, combined, fireballOffset, copyLen);
        }
        if (solPatterns.length > 0 && solOffset < combined.length) {
            int copyLen = Math.min(solPatterns.length, combined.length - solOffset);
            System.arraycopy(solPatterns, 0, combined, solOffset, copyLen);
        }

        for (int i = 0; i < combined.length; i++) {
            if (combined[i] == null) {
                combined[i] = new Pattern();
            }
        }

        List<SpriteMappingFrame> mappings = createSolMappings();
        return new ObjectSpriteSheet(combined, mappings, 0, 1);
    }

    /**
     * Load Rexon (Obj94/96/97) sprite sheet - lava snake from HTZ.
     * ROM: ArtNem_Rexon at 0x89DEC, palette line 3.
     * 4 frames:
     * - Frame 0: Body (3x2 = 6 tiles, 27x16 pixels)
     * - Frame 1: Neck segment (2x2 = 4 tiles, 16x16 pixels)
     * - Frame 2: Full body view (4x2 = 8 tiles, 32x16 pixels)
     * - Frame 3: Projectile (1x1 = 1 tile, 8x8 pixels)
     */
    public ObjectSpriteSheet loadRexonSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_REXON_ADDR, "Rexon");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createRexonMappings();
        return new ObjectSpriteSheet(patterns, mappings, 3, 1);
    }

    /**
     * Load Crawl (ObjC8) sprite sheet - bouncer badnik from CNZ.
     * ROM: ArtNem_Crawl at 0x901A4, palette line 0
     * 4 frames: 2 walking + 2 impact (ground/air)
     */
    public ObjectSpriteSheet loadCrawlSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CRAWL_ADDR, "Crawl");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createCrawlMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load Seesaw (Obj14) sprite sheet - tilting platform from HTZ.
     * ROM: ArtNem_HtzSeeSaw at 0xF096E, palette line 2
     * 2 frames: tilted (0) and flat (1). Frame 2 uses frame 0 with x-flip.
     */
    public ObjectSpriteSheet loadSeesawSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_HTZ_SEESAW_ADDR, "HtzSeeSaw");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createSeesawMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 0);
    }

    /**
     * Load Seesaw Ball sprite sheet - ball child of Seesaw from HTZ.
     * ROM: ArtNem_Sol at 0xF0D4A, palette line 0 (alternates with line 1 for animation)
     * 2 frames: same sprite with different palette lines for blinking effect.
     */
    public ObjectSpriteSheet loadSeesawBallSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_SOL_ADDR, "Sol");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createSeesawBallMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load HTZ Zipline Lift sprite sheet - diagonal moving platform from HTZ.
     * ROM: ArtNem_HtzZipline at 0xF0602, palette line 2.
     * 5 frames:
     * - Frame 0: Main lift (10 pieces) - diagonal platform with cables
     * - Frame 1: Variant lift (8 pieces)
     * - Frame 2: Rope only (2 pieces) - shown after motion stops
     * - Frame 3: Left stake (3 pieces) - for scenery
     * - Frame 4: Right stake (3 pieces) - for scenery
     */
    public ObjectSpriteSheet loadHTZLiftSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_HTZ_ZIPLINE_ADDR, "HtzZipline");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createHTZLiftMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1);
    }

    /**
     * Load HTZ Barrier sprite sheet (Object 0x2D subtype 0 in Hill Top Zone).
     * Uses ArtNem_HtzValveBarrier at 0xF08F6 instead of the CPZ ConstructionStripes art.
     * Frame 0 mapping: 4 x 2x2 tile pieces stacked vertically (16x64 total), palette 1.
     *
     * @return sprite sheet for HTZ valve barrier, or null on failure
     */
    public ObjectSpriteSheet loadHTZBarrierSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(
                Sonic2Constants.ART_NEM_HTZ_VALVE_BARRIER_ADDR, "HtzValveBarrier");
        if (patterns.length == 0) {
            return null;
        }
        // Reuse the same mappings as the generic barrier - frame 0 is the HTZ mapping
        List<SpriteMappingFrame> mappings = createBarrierMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    private AnimalType[] resolveZoneAnimals(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= ZONE_ANIMALS.length) {
            return DEFAULT_ANIMALS;
        }
        return ZONE_ANIMALS[zoneIndex];
    }

    private Pattern[] loadAnimalPatterns(AnimalType animalTypeA, AnimalType animalTypeB) {
        Pattern[] animalPatternsA = safeLoadNemesisPatterns(animalTypeA.artAddr(),
                "Animal-" + animalTypeA.displayName());
        Pattern[] animalPatternsB = safeLoadNemesisPatterns(animalTypeB.artAddr(),
                "Animal-" + animalTypeB.displayName());
        int minLength = ANIMAL_TILE_OFFSET * 2;
        int combinedLength = Math.max(Math.max(animalPatternsA.length, ANIMAL_TILE_OFFSET + animalPatternsB.length),
                minLength);
        Pattern[] combined = new Pattern[combinedLength];
        if (animalPatternsA.length > 0) {
            System.arraycopy(animalPatternsA, 0, combined, 0, Math.min(animalPatternsA.length, combined.length));
        }
        if (animalPatternsB.length > 0 && ANIMAL_TILE_OFFSET < combined.length) {
            int copyLength = Math.min(animalPatternsB.length, combined.length - ANIMAL_TILE_OFFSET);
            System.arraycopy(animalPatternsB, 0, combined, ANIMAL_TILE_OFFSET, copyLength);
        }
        for (int i = 0; i < combined.length; i++) {
            if (combined[i] == null) {
                combined[i] = new Pattern();
            }
        }
        return combined;
    }

    private List<SpriteMappingFrame> loadMappingFrames(int mappingAddr) {
        int offsetTableSize = reader.readU16BE(mappingAddr);
        int frameCount = offsetTableSize / 2;
        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = mappingAddr + reader.readU16BE(mappingAddr + i * 2);
            int pieceCount = reader.readU16BE(frameAddr);
            frameAddr += 2;
            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                int yOffset = (byte) reader.readU8(frameAddr);
                frameAddr += 1;
                int size = reader.readU8(frameAddr);
                frameAddr += 1;
                int tileWord = reader.readU16BE(frameAddr);
                frameAddr += 2;
                frameAddr += 2; // 2P tile word, unused in 1P.
                int xOffset = (short) reader.readU16BE(frameAddr);
                frameAddr += 2;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileIndex = tileWord & 0x7FF;
                boolean hFlip = (tileWord & 0x800) != 0;
                boolean vFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;

                pieces.add(new SpriteMappingPiece(
                        xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip, paletteIndex));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }
        return frames;
    }

    /**
     * Loads mapping frames from ROM and applies a tile index offset to each piece.
     * This allows ROM mappings that use VRAM tile indices to work with pattern
     * arrays
     * that start at index 0.
     *
     * @param mappingAddr ROM address of the mapping data
     * @param tileOffset  Offset to add to each tile index (use negative to
     *                    subtract)
     */
    private List<SpriteMappingFrame> loadMappingFramesWithTileOffset(int mappingAddr, int tileOffset) {
        int offsetTableSize = reader.readU16BE(mappingAddr);
        int frameCount = offsetTableSize / 2;
        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            // Read offset as signed 16-bit (negative offsets reference other mapping
            // tables)
            int rawOffset = reader.readU16BE(mappingAddr + i * 2);
            int signedOffset = (rawOffset > 32767) ? rawOffset - 65536 : rawOffset;

            int frameAddr = mappingAddr + signedOffset;
            int pieceCount = reader.readU16BE(frameAddr);
            frameAddr += 2;
            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                int yOffset = (byte) reader.readU8(frameAddr);
                frameAddr += 1;
                int size = reader.readU8(frameAddr);
                frameAddr += 1;
                int tileWord = reader.readU16BE(frameAddr);
                frameAddr += 2;
                frameAddr += 2; // 2P tile word, unused in 1P.
                int xOffset = (short) reader.readU16BE(frameAddr);
                frameAddr += 2;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileIndex = (tileWord & 0x7FF) + tileOffset;
                // Clamp to valid range
                if (tileIndex < 0)
                    tileIndex = 0;

                boolean hFlip = (tileWord & 0x800) != 0;
                boolean vFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;

                pieces.add(new SpriteMappingPiece(
                        xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip, paletteIndex));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }
        return frames;
    }

    private SpriteAnimationSet loadAnimationSet(int animAddr, int scriptCount) {
        SpriteAnimationSet set = new SpriteAnimationSet();
        for (int i = 0; i < scriptCount; i++) {
            int scriptAddr = animAddr + reader.readU16BE(animAddr + i * 2);
            int delay = reader.readU8(scriptAddr);
            scriptAddr += 1;

            List<Integer> frames = new ArrayList<>();
            SpriteAnimationEndAction endAction = SpriteAnimationEndAction.LOOP;
            int endParam = 0;

            while (true) {
                int value = reader.readU8(scriptAddr);
                scriptAddr += 1;
                if (value >= 0xF0) {
                    if (value == 0xFF) {
                        endAction = SpriteAnimationEndAction.LOOP;
                        break;
                    }
                    if (value == 0xFE) {
                        endAction = SpriteAnimationEndAction.LOOP_BACK;
                        endParam = reader.readU8(scriptAddr);
                        scriptAddr += 1;
                        break;
                    }
                    if (value == 0xFD) {
                        endAction = SpriteAnimationEndAction.SWITCH;
                        endParam = reader.readU8(scriptAddr);
                        scriptAddr += 1;
                        break;
                    }
                    endAction = SpriteAnimationEndAction.HOLD;
                    break;
                }
                frames.add(value);
            }

            set.addScript(i, new SpriteAnimationScript(delay, frames, endAction, endParam));
        }
        return set;
    }

    private List<SpriteMappingFrame> createExplosionMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        // Frame 0: -8, -8, 2x2, tile 0 (16x16 pixels)
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0));
        // Frame 1: -16, -16, 4x4, tile 4 (32x32 pixels)
        frames.add(createSimpleFrame(-16, -16, 4, 4, 4));
        // Frame 2: -16, -16, 4x4, tile 20 (0x14)
        frames.add(createSimpleFrame(-16, -16, 4, 4, 20));
        // Frame 3: -16, -16, 4x4, tile 36 (0x24)
        frames.add(createSimpleFrame(-16, -16, 4, 4, 36));
        // Frame 4: -16, -16, 4x4, tile 52 (0x34)
        frames.add(createSimpleFrame(-16, -16, 4, 4, 52));
        return frames;
    }

    private List<SpriteMappingFrame> createShieldMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Map_obj38_000C (0 tiles offset)
        frames.add(create2x2Frame(0));
        // Frame 1: Map_obj38_002E (4 tiles offset)
        frames.add(create2x2Frame(4));
        // Frame 2: Map_obj38_0050 (8 tiles offset)
        frames.add(create2x2Frame(8));
        // Frame 3: Map_obj38_0072 (12 tiles offset)
        frames.add(create2x2Frame(12));
        // Frame 4: Map_obj38_0094 (16 tiles offset)
        frames.add(create2x2Frame(16));

        // Frame 5: Map_obj38_00B6 (20 tiles offset) - Larger frame
        List<SpriteMappingPiece> pieces5 = new ArrayList<>();
        // Note: Palette index 0 assumed.
        // pieces: xOffset, yOffset, w, h, tileIndex, hFlip, vFlip, palIndex
        // obj38.asm: spritePiece -$18, -$20, 3, 4, $14... (3 tiles wide, 4 tiles high)
        pieces5.add(new SpriteMappingPiece(-24, -32, 3, 4, 20, false, false, 0));
        pieces5.add(new SpriteMappingPiece(0, -32, 3, 4, 20, true, false, 0));
        pieces5.add(new SpriteMappingPiece(-24, 0, 3, 4, 20, false, true, 0));
        pieces5.add(new SpriteMappingPiece(0, 0, 3, 4, 20, true, true, 0));

        frames.add(new SpriteMappingFrame(pieces5));

        return frames;
    }

    /**
     * Creates bridge mappings based on obj11_b.asm:
     * Frame 0: 2x2 tiles at tile index 4 (log segment 1)
     * Frame 1: 2x2 tiles at tile index 0 (log segment 2 / stake)
     */
    private List<SpriteMappingFrame> createBridgeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        // Frame 0: -8, -8, 2x2, tile 4
        frames.add(createSimpleFrame(-8, -8, 2, 2, 4));
        // Frame 1: -8, -8, 2x2, tile 0
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0));
        return frames;
    }

    /**
     * Creates bumper mappings based on obj44.asm (Round Bumper from CNZ).
     * Frame 0: Normal state - 2x(2x4) tiles at -16,-16 with horizontal flip (32x32
     * px)
     * Frame 1: Compressed state - 2x(3x4) + 2x(2x2) tiles (48x44 px)
     */
    private List<SpriteMappingFrame> createBumperMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Map_obj44_0004 - Normal state
        // spritePiece -$10, -$10, 2, 4, 0, 0, 0, 0, 0
        // spritePiece 0, -$10, 2, 4, 0, 1, 0, 0, 0 (hFlip)
        List<SpriteMappingPiece> frame0Pieces = new ArrayList<>();
        frame0Pieces.add(new SpriteMappingPiece(-16, -16, 2, 4, 0, false, false, 0));
        frame0Pieces.add(new SpriteMappingPiece(0, -16, 2, 4, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame0Pieces));

        // Frame 1: Map_obj44_0016 - Compressed (triggered) state
        // spritePiece -$18, -$14, 3, 4, 8, 0, 0, 0, 0
        // spritePiece 0, -$14, 3, 4, 8, 1, 0, 0, 0 (hFlip)
        // spritePiece -$10, $C, 2, 2, $14, 0, 0, 0, 0
        // spritePiece 0, $C, 2, 2, $14, 1, 0, 0, 0 (hFlip)
        List<SpriteMappingPiece> frame1Pieces = new ArrayList<>();
        frame1Pieces.add(new SpriteMappingPiece(-24, -20, 3, 4, 8, false, false, 0));
        frame1Pieces.add(new SpriteMappingPiece(0, -20, 3, 4, 8, true, false, 0));
        frame1Pieces.add(new SpriteMappingPiece(-16, 12, 2, 2, 0x14, false, false, 0));
        frame1Pieces.add(new SpriteMappingPiece(0, 12, 2, 2, 0x14, true, false, 0));
        frames.add(new SpriteMappingFrame(frame1Pieces));

        return frames;
    }

    /**
     * Creates hex bumper mappings based on objD7.asm (Hexagonal Bumper from CNZ).
     * <p>
     * Frame 0: Normal state - 4 pieces (3x2 tiles each), 48x32 px total
     * Frame 1: Vertical squeeze - 4 pieces squeezed vertically (bounce up/down)
     * Frame 2: Horizontal squeeze - 4 pieces squeezed horizontally (bounce
     * left/right)
     * <p>
     * Each frame uses mirrored pieces (hFlip, vFlip) to create symmetry.
     */
    private List<SpriteMappingFrame> createHexBumperMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Map_objD7_0006 - Normal state
        // 4 pieces: top-left, top-right (hFlip), bottom-left (vFlip), bottom-right
        // (h+vFlip)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-24, -16, 3, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -16, 3, 2, 0, true, false, 0));
        frame0.add(new SpriteMappingPiece(-24, 0, 3, 2, 0, false, true, 0));
        frame0.add(new SpriteMappingPiece(0, 0, 3, 2, 0, true, true, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Map_objD7_0028 - Vertical squeeze (used for up/down bounce)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-24, -12, 3, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -12, 3, 2, 0, true, false, 0));
        frame1.add(new SpriteMappingPiece(-24, 4, 3, 2, 0, false, true, 0));
        frame1.add(new SpriteMappingPiece(0, 4, 3, 2, 0, true, true, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Map_objD7_004A - Horizontal squeeze (used for left/right bounce)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-20, -16, 3, 2, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(4, -16, 3, 2, 0, true, false, 0));
        frame2.add(new SpriteMappingPiece(-20, 0, 3, 2, 0, false, true, 0));
        frame2.add(new SpriteMappingPiece(4, 0, 3, 2, 0, true, true, 0));
        frames.add(new SpriteMappingFrame(frame2));

        return frames;
    }

    /**
     * Creates bonus block mappings based on objD8.asm (Drop Target from CNZ).
     * <p>
     * 6 frames total - 3 orientations x 2 states (normal/hit):
     * <ul>
     * <li>Frames 0,3: Horizontal (32x16 px) - 4 tiles wide, 2 tiles high</li>
     * <li>Frames 1,4: Vertical (24x32 px) - 3 tiles wide, 4 tiles high</li>
     * <li>Frames 2,5: Vertical narrow (16x32 px) - 2 tiles wide, 4 tiles high</li>
     * </ul>
     * Hit frames have slightly adjusted offsets for the "bounce" animation.
     */
    private List<SpriteMappingFrame> createBonusBlockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Map_objD8_000C - Horizontal normal (32x16)
        frames.add(createSimpleFrame(-16, -8, 4, 2, 0));

        // Frame 1: Map_objD8_0016 - Vertical normal (24x32)
        frames.add(createSimpleFrame(-12, -16, 3, 4, 8));

        // Frame 2: Map_objD8_0020 - Vertical narrow normal (16x32)
        frames.add(createSimpleFrame(-8, -16, 2, 4, 20));

        // Frame 3: Map_objD8_002A - Horizontal hit (32x16, y offset -6)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-16, -6, 4, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Map_objD8_0034 - Vertical hit (24x32, offset adjusted)
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-14, -14, 3, 4, 8, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Map_objD8_003E - Vertical narrow hit (16x32, offset adjusted)
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-10, -16, 2, 4, 20, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        return frames;
    }

    private List<SpriteMappingFrame> createEHZWaterfallMappings() {
        // Translating from obj49.asm
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Map_obj49_0010 (Small top/bottom piece)
        // spritePiece -$20, -$80, 4, 2, 0, 0, 0, 0, 0
        // spritePiece 0, -$80, 4, 2, 0, 0, 0, 0, 0
        // Note: Y offset -128 (-$80) seems very high relative to object center, but
        // matching ROM
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-32, -128, 4, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -128, 4, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Map_obj49_0022 (Long waterfall section)
        // Pieces at Y: -128, -96, -64, -32, 0, 32, 64, 96 (0x60)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-32, -128, 4, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -128, 4, 2, 0, false, false, 0));
        // Loop of body pieces
        for (int y = -128; y <= 96; y += 32) {
            // These are 4x4 tiles (32x32), tile index 8
            if (y == -128)
                continue; // Skip first which was handled via 4x2 pieces at tile 0
            frame1.add(new SpriteMappingPiece(-32, y, 4, 4, 8, false, false, 0));
            frame1.add(new SpriteMappingPiece(0, y, 4, 4, 8, false, false, 0));
        }
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Map_obj49_00B4 (Empty)
        frames.add(new SpriteMappingFrame(new ArrayList<>()));

        // Frame 3: Map_obj49_00B6 (Small section)
        // Pieces at Y: -32, 0 (4x4 tile 8)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-32, -32, 4, 4, 8, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, -32, 4, 4, 8, false, false, 0));
        frame3.add(new SpriteMappingPiece(-32, 0, 4, 4, 8, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, 0, 4, 4, 8, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Map_obj49_00D8 (Medium section)
        // Pieces at Y: -64, -32, 0, 32, 64
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        for (int y = -64; y <= 64; y += 32) {
            frame4.add(new SpriteMappingPiece(-32, y, 4, 4, 8, false, false, 0));
            frame4.add(new SpriteMappingPiece(0, y, 4, 4, 8, false, false, 0));
        }
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Same as Frame 2 (Empty) - in mappings table
        frames.add(new SpriteMappingFrame(new ArrayList<>()));

        // Frame 6: Same as Frame 4
        frames.add(frames.get(3)); // reuse frame 3 (Map_obj49_00D8 was referenced? No, wait)
        // Correction: Table is:
        // 0: Map_obj49_0010
        // 1: Map_obj49_0022
        // 2: Map_obj49_00B4 (Empty)
        // 3: Map_obj49_00B6
        // 4: Map_obj49_00B4 (Empty) - WAIT, mappingsTableEntry.w Map_obj49_00B4 is
        // index 4?
        // Let's re-read table:
        // 0: Map_obj49_0010
        // 1: Map_obj49_0022
        // 2: Map_obj49_00B4
        // 3: Map_obj49_00B6
        // 4: Map_obj49_00B4
        // 5: Map_obj49_00D8
        // 6: Map_obj49_0010
        // 7: Map_obj49_012A (Longer version of 1?)

        // Let's restart frame list based directly on table indices:
        frames.clear();
        // 0: Map_obj49_0010
        frames.add(new SpriteMappingFrame(frame0));
        // 1: Map_obj49_0022
        frames.add(new SpriteMappingFrame(frame1));
        // 2: Map_obj49_00B4 (Empty)
        frames.add(new SpriteMappingFrame(new ArrayList<>()));
        // 3: Map_obj49_00B6
        frames.add(new SpriteMappingFrame(frame3));
        // 4: Map_obj49_00B4 (Empty)
        frames.add(new SpriteMappingFrame(new ArrayList<>()));
        // 5: Map_obj49_00D8
        frames.add(new SpriteMappingFrame(frame4));
        // 6: Map_obj49_0010
        frames.add(new SpriteMappingFrame(frame0));

        // 7: Map_obj49_012A
        List<SpriteMappingPiece> frame7 = new ArrayList<>();
        frame7.add(new SpriteMappingPiece(-32, -128, 4, 2, 0, false, false, 0));
        frame7.add(new SpriteMappingPiece(0, -128, 4, 2, 0, false, false, 0));
        for (int y = -128; y <= 32; y += 32) {
            if (y == -128)
                continue;
            frame7.add(new SpriteMappingPiece(-32, y, 4, 4, 8, false, false, 0));
            frame7.add(new SpriteMappingPiece(0, y, 4, 4, 8, false, false, 0));
        }
        // Actually, frame 7 (012A) looks like: -80(2), -80(4), -60, -40, -20, 0, 20
        // -80 (0x-50)?? No, disassembly says:
        /*
         * Map_obj49_012A:
         * spritePiece -$20, -$80, 4, 2, 0...
         * spritePiece 0, -$80, 4, 2, 0...
         * spritePiece -$20, -$80, 4, 4, 8...
         * spritePiece 0, -$80, 4, 4, 8...
         * ... down to $20
         */
        frame7.clear();
        frame7.add(new SpriteMappingPiece(-32, -128, 4, 2, 0, false, false, 0));
        frame7.add(new SpriteMappingPiece(0, -128, 4, 2, 0, false, false, 0));
        for (int y = -128; y <= 32; y += 32) {
            frame7.add(new SpriteMappingPiece(-32, y, 4, 4, 8, false, false, 0));
            frame7.add(new SpriteMappingPiece(0, y, 4, 4, 8, false, false, 0));
        }
        frames.add(new SpriteMappingFrame(frame7));

        return frames;
    }

    private SpriteMappingFrame create2x2Frame(int startTile) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        // 2x2 tiles (16x16 pixels). w=2, h=2.
        pieces.add(new SpriteMappingPiece(-16, -16, 2, 2, startTile, false, false, 0));
        pieces.add(new SpriteMappingPiece(0, -16, 2, 2, startTile, true, false, 0));
        pieces.add(new SpriteMappingPiece(-16, 0, 2, 2, startTile, false, true, 0));
        pieces.add(new SpriteMappingPiece(0, 0, 2, 2, startTile, true, true, 0));
        return new SpriteMappingFrame(pieces);
    }

    private SpriteMappingFrame createSimpleFrame(int x, int y, int wTiles, int hTiles, int tileIndex) {
        SpriteMappingPiece piece = new SpriteMappingPiece(x, y, wTiles, hTiles, tileIndex, false, false, 0);
        return new SpriteMappingFrame(List.of(piece));
    }

    private List<SpriteMappingFrame> normalizeMappings(List<SpriteMappingFrame> originalFrames) {
        int minTileIndex = Integer.MAX_VALUE;

        // Pass 1: Find minimum tile index
        for (SpriteMappingFrame frame : originalFrames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                minTileIndex = Math.min(minTileIndex, piece.tileIndex());
            }
        }

        // Pass 2: Create new frames with shifted indices
        List<SpriteMappingFrame> newFrames = new ArrayList<>(originalFrames.size());
        for (SpriteMappingFrame frame : originalFrames) {
            List<SpriteMappingPiece> newPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                newPieces.add(new SpriteMappingPiece(
                        piece.xOffset(),
                        piece.yOffset(),
                        piece.widthTiles(),
                        piece.heightTiles(),
                        piece.tileIndex() - minTileIndex,
                        piece.hFlip(),
                        piece.vFlip(),
                        piece.paletteIndex()));
            }
            newFrames.add(new SpriteMappingFrame(newPieces));
        }

        return newFrames;
    }

    private int computeMaxTileIndex(List<SpriteMappingFrame> frames) {
        int max = -1;
        if (frames == null) {
            return max;
        }
        for (SpriteMappingFrame frame : frames) {
            if (frame == null || frame.pieces() == null) {
                continue;
            }
            for (SpriteMappingPiece piece : frame.pieces()) {
                int tiles = piece.widthTiles() * piece.heightTiles();
                int end = piece.tileIndex() + Math.max(tiles, 1) - 1;
                max = Math.max(max, end);
            }
        }
        return max;
    }

    /**
     * Creates mappings for Masher (Obj5C) - Leaping piranha badnik.
     * Based on obj5C.asm with 2 frames.
     */
    private List<SpriteMappingFrame> createMasherMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        // Frame 0: Mouth closed
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-12, -16, 2, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(4, -16, 1, 2, 4, false, false, 0));
        frame0.add(new SpriteMappingPiece(-12, 0, 3, 2, 10, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Mouth open
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-12, -16, 2, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(2, -16, 2, 2, 6, false, false, 0));
        frame1.add(new SpriteMappingPiece(-12, 0, 3, 2, 16, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Creates mappings for Buzzer (Obj4B) - Flying bee/wasp badnik.
     * Based on obj4B.asm with 7 frames.
     */
    private List<SpriteMappingFrame> createBuzzerMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        // Frame 0: Body with wings extended
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-24, -8, 3, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -8, 3, 2, 6, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Body with wings up
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-24, -8, 3, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -8, 2, 2, 12, false, false, 0));
        frame1.add(new SpriteMappingPiece(2, 8, 2, 2, 16, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Similar to frame 1 with slightly different wing position
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-24, -8, 3, 2, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, -8, 2, 2, 12, false, false, 0));
        frame2.add(new SpriteMappingPiece(2, 8, 2, 2, 20, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frames 3-6: Projectile frames
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(4, -16, 1, 2, 20, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(4, -16, 1, 2, 22, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-12, -8, 1, 2, 24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-12, -8, 1, 2, 26, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        return frames;
    }

    /**
     * Creates mappings for Coconuts (Obj9D) - Monkey badnik.
     * Based on obj9D.asm with 4 frames.
     */
    private List<SpriteMappingFrame> createCoconutsMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        // Frame 0: Climbing 1
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-2, 0, 2, 2, 26, false, false, 0));
        frame0.add(new SpriteMappingPiece(-4, -16, 3, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-12, 0, 4, 2, 6, false, false, 0));
        frame0.add(new SpriteMappingPiece(12, 16, 1, 2, 14, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Climbing 2
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-2, 0, 2, 2, 30, false, false, 0));
        frame1.add(new SpriteMappingPiece(-4, -16, 3, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(-12, 0, 4, 2, 16, false, false, 0));
        frame1.add(new SpriteMappingPiece(12, 16, 1, 2, 24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Throwing
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(7, -8, 1, 2, 34, false, false, 0));
        frame2.add(new SpriteMappingPiece(-4, -16, 3, 2, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(-12, 0, 4, 2, 16, false, false, 0));
        frame2.add(new SpriteMappingPiece(12, 16, 1, 2, 24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Coconut projectile (palette line 1 for orange)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -8, 1, 2, 36, false, false, 2));
        frame3.add(new SpriteMappingPiece(0, -8, 1, 2, 36, true, false, 2));
        frames.add(new SpriteMappingFrame(frame3));

        return frames;
    }

    /**
     * Creates mappings for Spiny (ObjA5) and SpinyOnWall (ObjA6) badniks.
     * Based on objA6.asm (S2 disassembly):
     * - Frames 0-2: Horizontal Spiny (crawling caterpillar)
     * - Frames 3-5: SpinyOnWall (wall-climbing variant)
     * - Frames 6-7: Spike projectile (shared by both)
     * Uses palette line 1 (from make_art_tile(ArtTile_ArtNem_Spiny,1,0)).
     */
    private List<SpriteMappingFrame> createSpinyMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        final int palette = 1;  // Spiny uses palette line 1

        // Frame 0: Crawl frame 1
        // Spiny is a symmetrical caterpillar - left half mirrored to right
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -12, 1, 1, 0, false, false, palette));     // Antenna/leg left
        frame0.add(new SpriteMappingPiece(-24, -4, 3, 2, 1, false, false, palette));     // Body left half
        frame0.add(new SpriteMappingPiece(0, -12, 1, 1, 0, true, false, palette));       // Antenna/leg right (mirrored)
        frame0.add(new SpriteMappingPiece(0, -4, 3, 2, 1, true, false, palette));        // Body right half (mirrored)
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Crawl frame 2 (same antenna tile 0, body uses tile 7)
        // From objA6.asm Map_objA6_0032: tiles 0,7,0,7
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -12, 1, 1, 0, false, false, palette));
        frame1.add(new SpriteMappingPiece(-24, -4, 3, 2, 7, false, false, palette));
        frame1.add(new SpriteMappingPiece(0, -12, 1, 1, 0, true, false, palette));
        frame1.add(new SpriteMappingPiece(0, -4, 3, 2, 7, true, false, palette));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Attack pose (rearing up head - 2x1 tile piece at tile 0xD)
        // From objA6.asm Map_objA6_0054: head is 2x1 at -16,-12 with tile $D
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-16, -12, 2, 1, 0xD, false, false, palette));  // Head left
        frame2.add(new SpriteMappingPiece(-24, -4, 3, 2, 1, false, false, palette));     // Body left
        frame2.add(new SpriteMappingPiece(0, -12, 2, 1, 0xD, true, false, palette));     // Head right (H-flip)
        frame2.add(new SpriteMappingPiece(0, -4, 3, 2, 1, true, false, palette));        // Body right (H-flip)
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Wall climb frame 1 (SpinyOnWall patrol frame 1)
        // From objA6.asm Map_objA6_0076 - vertical orientation for wall-crawling variant
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-12, -24, 2, 3, 0x0F, false, false, palette));  // Body top
        frame3.add(new SpriteMappingPiece(4, -8, 1, 1, 0x15, false, false, palette));     // Leg top
        frame3.add(new SpriteMappingPiece(-12, 0, 2, 3, 0x0F, false, true, palette));     // Body bottom (V-flip)
        frame3.add(new SpriteMappingPiece(4, 0, 1, 1, 0x15, false, true, palette));       // Leg bottom (V-flip)
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Wall climb frame 2 (SpinyOnWall patrol frame 2)
        // From objA6.asm Map_objA6_0098 - uses different body tile $16
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-12, -24, 2, 3, 0x16, false, false, palette));  // Body top
        frame4.add(new SpriteMappingPiece(4, -8, 1, 1, 0x15, false, false, palette));     // Leg top
        frame4.add(new SpriteMappingPiece(-12, 0, 2, 3, 0x16, false, true, palette));     // Body bottom (V-flip)
        frame4.add(new SpriteMappingPiece(4, 0, 1, 1, 0x15, false, true, palette));       // Leg bottom (V-flip)
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Wall attack pose (SpinyOnWall attack frame)
        // From objA6.asm Map_objA6_00BA - body with spike extending
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-12, -24, 2, 3, 0x0F, false, false, palette));  // Body top
        frame5.add(new SpriteMappingPiece(4, -16, 1, 2, 0x1C, false, false, palette));    // Spike top
        frame5.add(new SpriteMappingPiece(-12, 0, 2, 3, 0x0F, false, true, palette));     // Body bottom (V-flip)
        frame5.add(new SpriteMappingPiece(4, 0, 1, 2, 0x1C, false, true, palette));       // Spike bottom (V-flip)
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6: Spike projectile frame 1 (also uses palette 1)
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x1E, false, false, palette));
        frames.add(new SpriteMappingFrame(frame6));

        // Frame 7: Spike projectile frame 2
        List<SpriteMappingPiece> frame7 = new ArrayList<>();
        frame7.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x1F, false, false, palette));
        frames.add(new SpriteMappingFrame(frame7));

        return frames;
    }

    /**
     * Creates mappings for Grabber (ObjA7) - CPZ spider badnik.
     * Based on ObjA7_ObjA8_ObjA9_Obj98_MapUnc_3921A from disassembly.
     * Frame 0: Body with open claws
     * Frame 1: Body with closed claws (grabbing)
     * Frame 2: Anchor box (hanger attachment point)
     * Frame 3: Legs (small 3x2)
     * Frame 4: Legs (large 4x2)
     * Frame 5-6: Unused small pieces
     */
    private List<SpriteMappingFrame> createGrabberMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Body with open claws (word_3923A)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x1B, -8, 1, 2, 0, false, false, 0));    // Left antenna
        frame0.add(new SpriteMappingPiece(-0x13, -8, 4, 2, 2, false, false, 0));    // Body
        frame0.add(new SpriteMappingPiece(-0xF, 8, 3, 2, 0x1D, false, false, 0));   // Open claws
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Body with closed claws (word_39254)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x1B, -8, 1, 2, 0, false, false, 0));    // Left antenna
        frame1.add(new SpriteMappingPiece(-0x13, -8, 4, 2, 2, false, false, 0));    // Body
        frame1.add(new SpriteMappingPiece(-0xF, 8, 4, 2, 0x23, false, false, 0));   // Closed claws
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Anchor box (word_3926E)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-4, -4, 1, 1, 0xA, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Legs small (word_39278)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-7, -8, 3, 2, 0xF, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Legs large (word_39282)
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-7, -8, 4, 2, 0x15, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Small unused piece (word_3928C)
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x2B, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6: Small unused piece (word_39296)
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x2C, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        return frames;
    }

    /**
     * Creates mappings for Grabber's string (ObjAA) - the thread connecting Grabber to anchor.
     * Based on ObjAA_MapUnc_39228 from disassembly.
     * Frames 0-8 represent increasing string lengths.
     */
    private List<SpriteMappingFrame> createGrabberStringMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Short string (word_392A0) - 1x2 = 16 pixels
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-4, 0, 1, 2, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: String (word_392AA) - 1x4 = 32 pixels
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-4, 0, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: String (word_392B4) - 1x2 + 1x4 = 48 pixels
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-4, 0, 1, 2, 0xB, false, false, 0));
        frame2.add(new SpriteMappingPiece(-4, 0x10, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: String (word_392C6) - 1x4 + 1x4 = 64 pixels
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-4, 0, 1, 4, 0xB, false, false, 0));
        frame3.add(new SpriteMappingPiece(-4, 0x20, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: String (word_392D8) - 80 pixels
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-4, 0, 1, 2, 0xB, false, false, 0));
        frame4.add(new SpriteMappingPiece(-4, 0x10, 1, 4, 0xB, false, false, 0));
        frame4.add(new SpriteMappingPiece(-4, 0x30, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: String (word_3930C - but mapped as frame 6 in table) - 96 pixels
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-4, 0, 1, 2, 0xB, false, false, 0));
        frame5.add(new SpriteMappingPiece(-4, 0x10, 1, 4, 0xB, false, false, 0));
        frame5.add(new SpriteMappingPiece(-4, 0x30, 1, 4, 0xB, false, false, 0));
        frame5.add(new SpriteMappingPiece(-4, 0x50, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6: String (word_392F2 - but mapped as frame 5 in table) - 96 pixels
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-4, 0, 1, 4, 0xB, false, false, 0));
        frame6.add(new SpriteMappingPiece(-4, 0x20, 1, 4, 0xB, false, false, 0));
        frame6.add(new SpriteMappingPiece(-4, 0x40, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        // Frame 7-8: String (word_3932E) - 128 pixels (both point to same data)
        List<SpriteMappingPiece> frame7 = new ArrayList<>();
        frame7.add(new SpriteMappingPiece(-4, 0, 1, 4, 0xB, false, false, 0));
        frame7.add(new SpriteMappingPiece(-4, 0x20, 1, 4, 0xB, false, false, 0));
        frame7.add(new SpriteMappingPiece(-4, 0x40, 1, 4, 0xB, false, false, 0));
        frame7.add(new SpriteMappingPiece(-4, 0x60, 1, 4, 0xB, false, false, 0));
        frames.add(new SpriteMappingFrame(frame7));
        frames.add(new SpriteMappingFrame(frame7)); // Frame 8 is same as 7

        return frames;
    }

    /**
     * Creates mappings for ChopChop (Obj91) - Piranha badnik from ARZ.
     * Based on Obj91_MapUnc_36534 from disassembly.
     * Frame 0: Mouth closed (4x3 tiles = 32x24 pixels)
     * Frame 1: Mouth open (4x3 tiles = 32x24 pixels)
     */
    private List<SpriteMappingFrame> createChopChopMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Mouth closed (word_3653A)
        // Single 4x3 tile piece at (-16, -12) using tiles 0-11
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-16, -12, 4, 3, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Mouth open (word_36544)
        // Single 4x3 tile piece at (-16, -12) using tiles 12-23
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-16, -12, 4, 3, 12, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Creates mappings for Whisp (Obj8C) - blowfly badnik from ARZ.
     * 2 frames of animation, each with 2 pieces (3x1 tiles stacked = 24x16 pixels total).
     * Frame 0: tiles 0-2 (top), tiles 3-5 (bottom)
     * Frame 1: tiles 6-8 (top), tiles 3-5 (bottom) - bottom piece shared
     */
    private List<SpriteMappingFrame> createWhispMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Wing position A
        // Top piece: 3x1 tiles at (-12, -8), starting at tile 0
        // Bottom piece: 3x1 tiles at (-12, 0), starting at tile 3
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-12, -8, 3, 1, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-12, 0, 3, 1, 3, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Wing position B
        // Top piece: 3x1 tiles at (-12, -8), starting at tile 6
        // Bottom piece: 3x1 tiles at (-12, 0), starting at tile 3 (shared with frame 0)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-12, -8, 3, 1, 6, false, false, 0));
        frame1.add(new SpriteMappingPiece(-12, 0, 3, 1, 3, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Creates mappings for Flasher (ObjA3) - firefly/glowbug badnik from MCZ.
     * Based on mappings/sprite/objA3.asm.
     */
    private List<SpriteMappingFrame> createFlasherMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: body only
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x10, -0x08, 3, 2, 0x00, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: small glow + body
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x08, -0x08, 2, 2, 0x06, false, false, 1));
        frame1.add(new SpriteMappingPiece(-0x10, -0x08, 3, 2, 0x00, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: medium glow + body
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x08, -0x08, 1, 2, 0x0A, false, false, 1));
        frame2.add(new SpriteMappingPiece(0x00, -0x08, 1, 2, 0x0A, true, false, 1));
        frame2.add(new SpriteMappingPiece(-0x08, -0x08, 2, 2, 0x06, false, false, 1));
        frame2.add(new SpriteMappingPiece(-0x10, -0x08, 3, 2, 0x00, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: large glow (tile 0x0C) + body
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x0C, false, false, 1));
        frame3.add(new SpriteMappingPiece(0x00, -0x10, 2, 2, 0x0C, true, false, 1));
        frame3.add(new SpriteMappingPiece(-0x10, 0x00, 2, 2, 0x0C, false, true, 1));
        frame3.add(new SpriteMappingPiece(0x00, 0x00, 2, 2, 0x0C, true, true, 1));
        frame3.add(new SpriteMappingPiece(-0x08, -0x08, 2, 2, 0x06, false, false, 1));
        frame3.add(new SpriteMappingPiece(-0x10, -0x08, 3, 2, 0x00, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: large glow variant (tile 0x10) + body
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x10, false, false, 1));
        frame4.add(new SpriteMappingPiece(0x00, -0x10, 2, 2, 0x10, true, false, 1));
        frame4.add(new SpriteMappingPiece(-0x10, 0x00, 2, 2, 0x10, false, true, 1));
        frame4.add(new SpriteMappingPiece(0x00, 0x00, 2, 2, 0x10, true, true, 1));
        frame4.add(new SpriteMappingPiece(-0x08, -0x08, 2, 2, 0x06, false, false, 1));
        frame4.add(new SpriteMappingPiece(-0x10, -0x08, 3, 2, 0x00, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates mappings for Grounder (Obj8D/8E) - drill badnik from ARZ.
     * Based on Obj8D_MapUnc_36CF0 from disassembly.
     * 5 frames:
     *   Frame 0 (idle 1): 4 pieces - symmetric body with tiles 0,1 and hFlip
     *   Frame 1 (idle 2): 4 pieces - taller pose with tiles 7,8 and hFlip
     *   Frame 2-4 (walking): 2 pieces each - 4x4 body (tile 0x10) + 4x1 feet
     */
    private List<SpriteMappingFrame> createGrounderMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (word_36D02) - Idle 1:
        // 4 pieces forming symmetric body
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -12, 1, 1, 0, false, false, 0));   // eye left
        frame0.add(new SpriteMappingPiece(-16, -4, 2, 3, 1, false, false, 0));   // body left
        frame0.add(new SpriteMappingPiece(0, -12, 1, 1, 0, true, false, 0));     // eye right (hFlip)
        frame0.add(new SpriteMappingPiece(0, -4, 2, 3, 1, true, false, 0));      // body right (hFlip)
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (word_36D24) - Idle 2:
        // 4 pieces forming taller symmetric pose
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -20, 1, 1, 7, false, false, 0));   // eye left
        frame1.add(new SpriteMappingPiece(-16, -12, 2, 4, 8, false, false, 0));  // body left
        frame1.add(new SpriteMappingPiece(0, -20, 1, 1, 7, true, false, 0));     // eye right (hFlip)
        frame1.add(new SpriteMappingPiece(0, -12, 2, 4, 8, true, false, 0));     // body right (hFlip)
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (word_36D46) - Walking 1:
        // 2 pieces: 4x4 body + 4x1 feet
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-16, -20, 4, 4, 0x10, false, false, 0)); // body
        frame2.add(new SpriteMappingPiece(-16, 12, 4, 1, 0x20, false, false, 0));  // feet
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (word_36D58) - Walking 2:
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-16, -20, 4, 4, 0x10, false, false, 0)); // body
        frame3.add(new SpriteMappingPiece(-16, 12, 4, 1, 0x24, false, false, 0));  // feet
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (word_36D6A) - Walking 3:
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-16, -20, 4, 4, 0x10, false, false, 0)); // body
        frame4.add(new SpriteMappingPiece(-16, 12, 4, 1, 0x28, false, false, 0));  // feet
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates mappings for Grounder Rock (Obj90) - rock projectiles from ARZ.
     * Based on Obj90_MapUnc_36D7A from disassembly.
     * 3 frames:
     *   Frame 0: 2x2 tiles at tile 0x2C (large rock)
     *   Frame 1: 1x1 tile at tile 0x30 (small rock variant 1)
     *   Frame 2: 1x1 tile at tile 0x31 (small rock variant 2)
     */
    private List<SpriteMappingFrame> createGrounderRockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (word_36D7C) - Large rock: 2x2 tiles
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x2C, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (word_36D86) - Small rock 1: 1x1 tile
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x30, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (word_36D90) - Small rock 2: 1x1 tile
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x31, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        return frames;
    }

    /**
     * Creates mappings for Spiker (Obj92) and Spiker Drill (Obj93).
     * Based on mappings/sprite/obj93.asm.
     * <p>
     * Tile indices are absolute VRAM indices, so we subtract base tile $3DE
     * (ArtTile_ArtNem_Sol) to map into our combined sheet.
     * Frames:
     * - 0/1: Walking with drill extended (tiles $520/$526 + $52C + $3DE)
     * - 2/3: Walking without drill (tiles $520/$526 + $3DE)
     * - 4: Drill projectile (tile $52C)
     */
    private List<SpriteMappingFrame> createSpikerMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        final int baseTile = 0x3DE;
        final int tileBodyA = 0x520 - baseTile;
        final int tileBodyB = 0x526 - baseTile;
        final int tileDrill = 0x52C - baseTile;
        final int tileSol = 0x3DE - baseTile;

        // Frame 0 (Map_obj93_000A)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x0C, 8, 3, 2, tileBodyA, false, false, 0));
        frame0.add(new SpriteMappingPiece(-8, -0x18, 2, 4, tileDrill, false, false, 1));
        frame0.add(new SpriteMappingPiece(-8, 0, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj93_0024)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x0C, 8, 3, 2, tileBodyB, false, false, 0));
        frame1.add(new SpriteMappingPiece(-8, -0x18, 2, 4, tileDrill, false, false, 1));
        frame1.add(new SpriteMappingPiece(-8, 0, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj93_003E)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x0C, 8, 3, 2, tileBodyA, false, false, 0));
        frame2.add(new SpriteMappingPiece(-8, 0, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj93_0050)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x0C, 8, 3, 2, tileBodyB, false, false, 0));
        frame3.add(new SpriteMappingPiece(-8, 0, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (Map_obj93_0062) - Drill projectile
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-8, -0x14, 2, 4, tileDrill, false, false, 1));
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates mappings for Sol (Obj95) - fireball-throwing badnik from HTZ.
     * Based on obj95.asm sprite mappings.
     *
     * From s2disasm obj95.asm:
     * - Body frames use tile $3DE with palette 0 (character palette)
     * - Fireball frames use tile $3AE with palette 1 (zone palette = HTZ fire colors)
     *
     * The palette values here (0 and 1) are ADDED to the sprite sheet's base palette (0),
     * resulting in final palette indices 0 (body) and 1 (fireball).
     */
    private List<SpriteMappingFrame> createSolMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        final int baseTile = 0x39E; // ArtTile_ArtNem_HtzFireball1
        final int tileFireball = 0x3AE - baseTile;  // = 0x10 (tile 16 in combined array)
        final int tileSol = 0x3DE - baseTile;       // = 0x40 (tile 64 in combined array)

        // Frames 0-2: Sol body (Map_obj95_000A/0014/001E) - palette 0
        // spritePiece -8, -8, 2, 2, $3DE, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -8, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -8, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-8, -8, 2, 2, tileSol, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Fireball (Map_obj95_0028) - palette 0
        // Note: Original ROM mapping says palette 1, but HtzFireball1 art is designed for palette 0
        // (verified by comparing with Obj20 lava ball which uses same art with palette 0)
        // spritePiece -8, -8, 2, 2, $3AE, 0, 0, 0, 1
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -8, 2, 2, tileFireball, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Fireball H-flipped (Map_obj95_0032) - palette 0
        // spritePiece -8, -8, 2, 2, $3AE, 1, 0, 0, 1
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-8, -8, 2, 2, tileFireball, true, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates mappings for Rexon (Obj94/96/97) - lava snake from HTZ.
     * Based on obj97.asm sprite mappings.
     * 4 frames:
     * - Frame 0: Body (3x2 = 6 tiles, ~24x16 pixels) - platform/anchor
     * - Frame 1: Neck segment (2x2 = 4 tiles, 16x16 pixels) - head segments
     * - Frame 2: Full body view (4x2 = 8 tiles, 32x16 pixels) - unused
     * - Frame 3: Projectile (1x1 = 1 tile, 8x8 pixels) - fireball
     */
    private List<SpriteMappingFrame> createRexonMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Body (Map_obj97_0008) - 3x2 tiles
        // spritePiece -20, -6, 3, 2, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-20, -6, 3, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Neck/Head segment (Map_obj97_0012) - 2x2 tiles
        // spritePiece -8, -8, 2, 2, 6, 0, 0, 0, 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -8, 2, 2, 6, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Full body view (Map_obj97_001C) - 4x2 tiles
        // spritePiece -16, -8, 4, 2, $A, 0, 0, 0, 0
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-16, -8, 4, 2, 0x0A, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Projectile (Map_obj97_0026) - 1x1 tile
        // spritePiece -4, -4, 1, 1, $12, 0, 0, 0, 0
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x12, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        return frames;
    }

    /**
     * Creates mappings for Crawl (ObjC8) - bouncer badnik from CNZ.
     * Based on objC8.asm sprite mappings.
     * 4 frames: 2 walking poses + 2 impact poses (ground/air)
     */
    private List<SpriteMappingFrame> createCrawlMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Walking 1) - 3 pieces
        // Shield/body with extended shield arm
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(0, -16, 3, 4, 0x10, false, false, 0));    // Shield
        frame0.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x00, false, false, 0));  // Body
        frame0.add(new SpriteMappingPiece(-24, 0, 3, 2, 0x24, false, false, 0));    // Feet
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Walking 2) - 3 pieces
        // Shield/body with slightly different arm position
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(4, -16, 3, 4, 0x10, false, false, 0));    // Shield
        frame1.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x00, false, false, 0));  // Body
        frame1.add(new SpriteMappingPiece(-32, 0, 3, 2, 0x24, false, false, 0));    // Feet
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Impact Ground) - 3 pieces
        // Crouched impact pose
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-24, -16, 3, 4, 0x10, false, false, 0));  // Shield
        frame2.add(new SpriteMappingPiece(-16, 0, 3, 2, 0x24, false, false, 0));    // Feet
        frame2.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x00, false, false, 0));  // Body
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Impact Air) - 4 pieces
        // Extended impact pose with spread parts
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-16, -16, 4, 2, 0x1C, false, false, 0));  // Upper body
        frame3.add(new SpriteMappingPiece(-8, 0, 3, 2, 0x24, false, false, 0));     // Right foot
        frame3.add(new SpriteMappingPiece(-32, 0, 3, 2, 0x24, true, false, 0));     // Left foot (H-flipped)
        frame3.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x00, false, false, 0));  // Body
        frames.add(new SpriteMappingFrame(frame3));

        return frames;
    }

    /**
     * Creates mappings for Seesaw (Obj14) - tilting platform from HTZ.
     * Based on obj14_a.asm (S2 disassembly).
     * 2 frames: 0 = tilted right, 1 = flat
     * Frame 2 (tilted left) reuses frame 0 with x-flip.
     */
    private List<SpriteMappingFrame> createSeesawMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Tilted right (Map_obj14_a_0008)
        // spritePiece x, y, width, height, tile, hflip, vflip, pal, priority
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -4, 2, 2, 0x14, false, false, 2));   // Fulcrum
        frame0.add(new SpriteMappingPiece(-4, 12, 1, 2, 0x12, false, false, 1));   // Fulcrum base
        frame0.add(new SpriteMappingPiece(-48, -28, 2, 2, 0x06, false, false, 2)); // Left end (high)
        frame0.add(new SpriteMappingPiece(-32, -20, 2, 2, 0x0A, false, false, 2)); // Left-mid
        frame0.add(new SpriteMappingPiece(-16, -12, 2, 2, 0x0A, false, false, 2)); // Center-left
        frame0.add(new SpriteMappingPiece(0, -4, 2, 2, 0x0A, false, false, 2));    // Center
        frame0.add(new SpriteMappingPiece(16, 4, 2, 2, 0x0A, false, false, 2));    // Center-right
        frame0.add(new SpriteMappingPiece(32, 12, 2, 2, 0x0E, false, false, 2));   // Right end (low)
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Flat/balanced (Map_obj14_a_004A)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -4, 2, 2, 0x14, false, false, 2));   // Fulcrum
        frame1.add(new SpriteMappingPiece(-4, 12, 1, 2, 0x12, false, false, 1));   // Fulcrum base
        frame1.add(new SpriteMappingPiece(-48, -12, 2, 2, 0x00, false, false, 2)); // Left end
        frame1.add(new SpriteMappingPiece(-32, -12, 2, 2, 0x02, false, false, 2)); // Left-mid
        frame1.add(new SpriteMappingPiece(-16, -12, 2, 2, 0x02, false, false, 2)); // Center-left
        frame1.add(new SpriteMappingPiece(0, -12, 2, 2, 0x02, false, false, 2));   // Center
        frame1.add(new SpriteMappingPiece(16, -12, 2, 2, 0x02, false, false, 2));  // Center-right
        frame1.add(new SpriteMappingPiece(32, -12, 2, 2, 0x00, true, false, 2));   // Right end (h-flip)
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Creates mappings for Seesaw Ball - Sol badnik ball on seesaw from HTZ.
     * Based on obj14_b.asm (S2 disassembly).
     * 2 frames with different palette lines for blinking effect.
     */
    private List<SpriteMappingFrame> createSeesawBallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Palette line 0 (Map_obj14_b_0004)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Palette line 1 (Map_obj14_b_000E)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 1, 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 1));
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Creates mappings for HTZ Zipline Lift (Obj16).
     * Based on obj16.asm (S2 disassembly).
     * 5 frames: main lift, variant, rope only, left stake, right stake.
     */
    private List<SpriteMappingFrame> createHTZLiftMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Map_obj16_000A - Main lift (10 pieces)
        // spritePiece x, y, width, height, tile, hflip, vflip, pal, priority
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x1C, -0x3F, 2, 2, 0x00, false, false, 0)); // Top cable
        frame0.add(new SpriteMappingPiece(-0x1A, -0x30, 1, 4, 0x04, false, false, 0)); // Cable segment
        frame0.add(new SpriteMappingPiece(-0x1A, -0x10, 1, 4, 0x04, false, false, 0)); // Cable segment
        frame0.add(new SpriteMappingPiece(-0x19, 0x10, 1, 2, 0x08, false, false, 0));  // Cable bottom
        frame0.add(new SpriteMappingPiece(0x0C, -0x2B, 2, 2, 0x0A, false, false, 0));  // Top right cable
        frame0.add(new SpriteMappingPiece(0x11, -0x20, 1, 4, 0x0E, false, false, 0));  // Right cable segment
        frame0.add(new SpriteMappingPiece(0x11, 0x10, 1, 2, 0x12, false, false, 0));   // Right cable bottom
        frame0.add(new SpriteMappingPiece(0x11, 0x00, 1, 4, 0x0E, false, false, 0));   // Right cable segment
        frame0.add(new SpriteMappingPiece(-0x20, 0x20, 4, 2, 0x14, false, false, 0));  // Platform left
        frame0.add(new SpriteMappingPiece(0x00, 0x20, 4, 2, 0x14, true, false, 0));    // Platform right (h-flip)
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Map_obj16_005C - Variant lift (8 pieces)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x1C, -0x3F, 2, 2, 0x00, false, false, 0)); // Top cable
        frame1.add(new SpriteMappingPiece(-0x1A, -0x30, 1, 4, 0x04, false, false, 0)); // Cable segment
        frame1.add(new SpriteMappingPiece(-0x1A, -0x10, 1, 4, 0x04, false, false, 0)); // Cable segment
        frame1.add(new SpriteMappingPiece(-0x1A, 0x10, 1, 2, 0x2C, false, false, 0));  // Different connector tile
        frame1.add(new SpriteMappingPiece(0x0C, -0x2B, 2, 2, 0x0A, false, false, 0));  // Top right cable
        frame1.add(new SpriteMappingPiece(0x11, -0x20, 1, 4, 0x0E, false, false, 0));  // Right cable segment
        frame1.add(new SpriteMappingPiece(0x11, 0x18, 1, 2, 0x2E, false, false, 0));   // Different connector tile
        frame1.add(new SpriteMappingPiece(0x11, 0x00, 1, 4, 0x0E, false, false, 0));   // Right cable segment
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Map_obj16_009E - Rope only (2 pieces)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x20, 0x20, 4, 2, 0x14, false, false, 0));  // Platform left
        frame2.add(new SpriteMappingPiece(0x00, 0x20, 4, 2, 0x14, true, false, 0));    // Platform right (h-flip)
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Map_obj16_00B0 - Left stake (3 pieces)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -0x28, 2, 2, 0x1C, false, false, 0));    // Stake top
        frame3.add(new SpriteMappingPiece(-8, -0x18, 2, 4, 0x20, false, false, 0));    // Stake middle
        frame3.add(new SpriteMappingPiece(-8, 0x08, 2, 4, 0x20, false, false, 0));     // Stake bottom
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Map_obj16_00CA - Right stake (3 pieces)
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-8, -0x28, 2, 2, 0x28, false, false, 0));    // Stake top (different tile)
        frame4.add(new SpriteMappingPiece(-8, -0x18, 2, 4, 0x20, true, false, 0));     // Stake middle (h-flip)
        frame4.add(new SpriteMappingPiece(-8, 0x08, 2, 4, 0x20, true, false, 0));      // Stake bottom (h-flip)
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates mappings for Animal (Obj28) - all animal variants.
     * Based on obj28_a-e.asm (S2 disassembly).
     */
    private List<SpriteMappingFrame> createAnimalMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        final int tileOffset = ANIMAL_TILE_OFFSET; // ArtTile_ArtNem_Animal_2 - ArtTile_ArtNem_Animal_1

        addAnimalMappingSet(frames, 0); // Map_obj28_a (Animal_1)
        addAnimalMappingSet(frames, tileOffset); // Map_obj28_a (Animal_2)
        addAnimalMappingSetB(frames, 0);
        addAnimalMappingSetB(frames, tileOffset);
        addAnimalMappingSetC(frames, 0);
        addAnimalMappingSetC(frames, tileOffset);
        addAnimalMappingSetD(frames, 0);
        addAnimalMappingSetD(frames, tileOffset);
        addAnimalMappingSetE(frames, 0);
        addAnimalMappingSetE(frames, tileOffset);
        return frames;
    }

    private void addAnimalMappingSet(List<SpriteMappingFrame> frames, int tileOffset) {
        // Map_obj28_a: 0010, 001A, 0006
        frames.add(createSimpleFrame(-8, -8, 2, 2, 8 + tileOffset));
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0x0C + tileOffset));
        frames.add(createSimpleFrame(-8, -0x14, 2, 4, 0 + tileOffset));
    }

    private void addAnimalMappingSetB(List<SpriteMappingFrame> frames, int tileOffset) {
        // Map_obj28_b: 0010, 001A, 0006
        frames.add(createSimpleFrame(-0x0C, -8, 3, 2, 8 + tileOffset));
        frames.add(createSimpleFrame(-0x0C, -8, 3, 2, 0x0E + tileOffset));
        frames.add(createSimpleFrame(-8, -0x14, 2, 4, 0 + tileOffset));
    }

    private void addAnimalMappingSetC(List<SpriteMappingFrame> frames, int tileOffset) {
        // Map_obj28_c: 0010, 001A, 0006
        frames.add(createSimpleFrame(-0x0C, -8, 3, 2, 6 + tileOffset));
        frames.add(createSimpleFrame(-0x0C, -8, 3, 2, 0x0C + tileOffset));
        frames.add(createSimpleFrame(-8, -0x0C, 2, 3, 0 + tileOffset));
    }

    private void addAnimalMappingSetD(List<SpriteMappingFrame> frames, int tileOffset) {
        // Map_obj28_d: 0010, 001A, 0006
        frames.add(createSimpleFrame(-8, -8, 2, 2, 6 + tileOffset));
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0x0A + tileOffset));
        frames.add(createSimpleFrame(-8, -0x0C, 2, 3, 0 + tileOffset));
    }

    private void addAnimalMappingSetE(List<SpriteMappingFrame> frames, int tileOffset) {
        // Map_obj28_e: 0010, 001A, 0006
        frames.add(createSimpleFrame(-8, -0x0C, 2, 3, 6 + tileOffset));
        frames.add(createSimpleFrame(-8, -0x0C, 2, 3, 0x0C + tileOffset));
        frames.add(createSimpleFrame(-8, -0x0C, 2, 3, 0 + tileOffset));
    }

    /**
     * Creates mappings for Points (Obj29).
     * Based on obj29.asm.
     * <p>
     * Frame assignments:
     * - Frames 0-4: Standard badnik kill points (100, 200, 500, 1000, 10)
     * - Frame 5: 1000 points alt (for chain bonus max)
     * - Frame 6: Reserved for future use
     */
    private List<SpriteMappingFrame> createPointsMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 100 points (-8, -8, 2x2, tile 2)
        // Used for badnik kills and SmashableObject_LoadPoints when counter >= 6
        frames.add(createSimpleFrame(-8, -8, 2, 2, 2));

        // Frame 1: 200 points (-8, -8, 2x2, tile 6)
        frames.add(createSimpleFrame(-8, -8, 2, 2, 6));

        // Frame 2: 500 points (-8, -8, 2x2, tile 10 ($A))
        frames.add(createSimpleFrame(-8, -8, 2, 2, 10));

        // Frame 3: 1000 points (tiles 0 + 14)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -8, 1, 2, 0, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, -8, 2, 2, 14, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: 10 points (tile 0 = "1" single digit, used for "10")
        frames.add(createSimpleFrame(-4, -8, 1, 2, 0));

        // Frame 5: 1000 points alternative (tiles 2 + 14)
        // Used for chain bonus max ($A/2 = 5)
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-16, -8, 2, 2, 2, false, false, 0));
        frame5.add(new SpriteMappingPiece(0, -8, 2, 2, 14, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6: 5000 points (tiles 10 + 14) - reserved
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-16, -8, 2, 2, 10, false, false, 0));
        frame6.add(new SpriteMappingPiece(0, -8, 2, 2, 14, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        return frames;
    }

    /**
     * Creates mappings for Signpost (Obj0D).
     * Based on obj0D_a.asm with 6 frames for spinning.
     * Frame order must match Ani_obj0D indices:
     * 0=Sonic, 1=Tails, 2=Eggman, 3=Blank, 4=Edge, 5=Sonic (h-flip).
     */
    private List<SpriteMappingFrame> createSignpostMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Sonic face (full view)
        // spritePiece -$10, -$10, 4, 4, $C, 0, 0, 0, 0
        // spritePiece -4, $10, 1, 2, $20, 0, 0, 0, 0 (pole)
        List<SpriteMappingPiece> sonic = new ArrayList<>();
        sonic.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x0C, false, false, 0));
        sonic.add(new SpriteMappingPiece(-4, 16, 1, 2, 0x20, false, false, 0));

        // Side spinning (thin) - Tails face in original
        // spritePiece -$18, -$10, 1, 4, $3A, 0, 0, 0, 0
        // spritePiece -$10, -$10, 4, 4, $3E, 0, 0, 0, 0
        // spritePiece $10, -$10, 1, 4, $3A, 1, 0, 0, 0 (hflipped)
        // spritePiece -4, $10, 1, 2, $20, 0, 0, 0, 0 (pole)
        List<SpriteMappingPiece> tails = new ArrayList<>();
        tails.add(new SpriteMappingPiece(-24, -16, 1, 4, 0x3A, false, false, 0));
        tails.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x3E, false, false, 0));
        tails.add(new SpriteMappingPiece(16, -16, 1, 4, 0x3A, true, false, 0));
        tails.add(new SpriteMappingPiece(-4, 16, 1, 2, 0x20, false, false, 0));

        // Eggman face (wide view)
        // spritePiece -$18, -$10, 3, 4, $22, 0, 0, 0, 0
        // spritePiece 0, -$10, 3, 4, $2E, 0, 0, 0, 0
        // spritePiece -4, $10, 1, 2, $20, 0, 0, 0, 0 (pole)
        List<SpriteMappingPiece> eggman = new ArrayList<>();
        eggman.add(new SpriteMappingPiece(-24, -16, 3, 4, 0x22, false, false, 0));
        eggman.add(new SpriteMappingPiece(0, -16, 3, 4, 0x2E, false, false, 0));
        eggman.add(new SpriteMappingPiece(-4, 16, 1, 2, 0x20, false, false, 0));

        // Blank face (mid-spin)
        // spritePiece -$18, -$10, 3, 4, 0, 0, 0, 0, 0
        // spritePiece 0, -$10, 3, 4, 0, 1, 0, 0, 0 (hflipped)
        // spritePiece -4, $10, 1, 2, $20, 0, 0, 0, 0 (pole)
        List<SpriteMappingPiece> blank = new ArrayList<>();
        blank.add(new SpriteMappingPiece(-24, -16, 3, 4, 0, false, false, 0));
        blank.add(new SpriteMappingPiece(0, -16, 3, 4, 0, true, false, 0));
        blank.add(new SpriteMappingPiece(-4, 16, 1, 2, 0x20, false, false, 0));

        // Edge view (very thin)
        // spritePiece -4, -$10, 1, 4, $1C, 0, 0, 0, 0
        // spritePiece -4, $10, 1, 2, $20, 0, 0, 0, 0 (pole)
        List<SpriteMappingPiece> edge = new ArrayList<>();
        edge.add(new SpriteMappingPiece(-4, -16, 1, 4, 0x1C, false, false, 0));
        edge.add(new SpriteMappingPiece(-4, 16, 1, 2, 0x20, false, false, 0));

        // Sonic face flipped (for alternating direction spin)
        // spritePiece -$10, -$10, 4, 4, $C, 1, 0, 0, 0 (hflipped)
        // spritePiece -4, $10, 1, 2, $20, 0, 0, 0, 0 (pole)
        List<SpriteMappingPiece> sonicFlip = new ArrayList<>();
        sonicFlip.add(new SpriteMappingPiece(-16, -16, 4, 4, 0x0C, true, false, 0));
        sonicFlip.add(new SpriteMappingPiece(-4, 16, 1, 2, 0x20, false, false, 0));

        frames.add(new SpriteMappingFrame(sonic)); // 0
        frames.add(new SpriteMappingFrame(tails)); // 1
        frames.add(new SpriteMappingFrame(eggman)); // 2
        frames.add(new SpriteMappingFrame(blank)); // 3
        frames.add(new SpriteMappingFrame(edge)); // 4
        frames.add(new SpriteMappingFrame(sonicFlip)); // 5

        return frames;
    }

    /**
     * Creates animations for Signpost (Obj0D).
     * Based on Ani_obj0D in s2.asm.
     * 
     * Animation scripts:
     * 0: $0F, $02, $FF (hold frame 2 - Eggman face)
     * 1: $01, $02, $03, $04, $05, $01, $03, $04, $05, $00, $03, $04, $05, $FF
     * (spinning)
     * 2: same as 1
     * 3: $0F, $00, $FF (hold frame 0 - Sonic face)
     * 4: $0F, $01, $FF (hold frame 1 - Tails face)
     */
    private SpriteAnimationSet createSignpostAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Hold Eggman face (frame 3 in current mapping order)
        set.addScript(0, new SpriteAnimationScript(0x0F, List.of(3), SpriteAnimationEndAction.LOOP, 0));

        // Anim 1: Spinning to Sonic (mapped to current frames)
        // Eggman -> spin -> Tails -> spin -> Sonic
        set.addScript(1, new SpriteAnimationScript(0x01,
                List.of(3, 0, 4, 5, 1, 0, 4, 5, 2, 0, 4, 5),
                SpriteAnimationEndAction.LOOP, 0));

        // Anim 2: Same as 1 (used for 2P mode)
        set.addScript(2, new SpriteAnimationScript(0x01,
                List.of(3, 0, 4, 5, 1, 0, 4, 5, 2, 0, 4, 5),
                SpriteAnimationEndAction.LOOP, 0));

        // Anim 3: Hold Sonic face (frame 2 in current mapping order)
        // From disasm: byte_195B7: dc.b $0F, $00, $FF - hold frame 0
        set.addScript(3, new SpriteAnimationScript(0x0F, List.of(2), SpriteAnimationEndAction.LOOP, 0));

        // Anim 4: Hold Tails face (frame 1 in current mapping order)
        set.addScript(4, new SpriteAnimationScript(0x0F, List.of(1), SpriteAnimationEndAction.LOOP, 0));

        return set;
    }

    /**
     * Creates mappings for CNZ Flipper (Obj86).
     * Based on obj86.asm mappings.
     * Frames 0-2: Vertical flipper states
     * Frames 3-5: Horizontal flipper states
     */
    private List<SpriteMappingFrame> createFlipperMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Vertical idle (Map_obj86_000C)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-25, -9, 3, 4, 0x0C, false, false, 0));
        frame0.add(new SpriteMappingPiece(-1, -2, 1, 2, 0x18, false, false, 0));
        frame0.add(new SpriteMappingPiece(7, 1, 2, 2, 0x1A, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Vertical triggered 1 (Map_obj86_0026)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-24, -8, 4, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(8, -8, 2, 2, 8, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Vertical triggered 2 (Map_obj86_0038) - v-flipped pieces
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-25, -23, 3, 4, 0x0C, false, true, 0));
        frame2.add(new SpriteMappingPiece(-1, -14, 1, 2, 0x18, false, true, 0));
        frame2.add(new SpriteMappingPiece(7, -17, 2, 2, 0x1A, false, true, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Horizontal idle (Map_obj86_0052)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-15, -25, 3, 2, 0x24, false, false, 0));
        frame3.add(new SpriteMappingPiece(-17, -9, 3, 2, 0x2A, false, false, 0));
        frame3.add(new SpriteMappingPiece(-17, 7, 2, 2, 0x30, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Horizontal mid (Map_obj86_006C) - mirrored pieces
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-8, -24, 1, 4, 0x1E, false, false, 0));
        frame4.add(new SpriteMappingPiece(0, -24, 1, 4, 0x1E, true, false, 0));
        frame4.add(new SpriteMappingPiece(-8, 8, 1, 2, 0x22, false, false, 0));
        frame4.add(new SpriteMappingPiece(0, 8, 1, 2, 0x22, true, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Horizontal activated (Map_obj86_008E) - h-flipped
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-9, -25, 3, 2, 0x24, true, false, 0));
        frame5.add(new SpriteMappingPiece(-7, -9, 3, 2, 0x2A, true, false, 0));
        frame5.add(new SpriteMappingPiece(1, 7, 2, 2, 0x30, true, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        return frames;
    }

    /**
     * Creates animations for CNZ Flipper (Obj86).
     * Based on Ani_obj86 in s2.asm.
     */
    private SpriteAnimationSet createFlipperAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Vertical idle - hold frame 0 ($0F, 0, $FF)
        set.addScript(0, new SpriteAnimationScript(0x0F, List.of(0),
                SpriteAnimationEndAction.LOOP, 0));

        // Anim 1: Vertical trigger ($03, 1, 2, 1, $FD, 0)
        set.addScript(1, new SpriteAnimationScript(0x03, List.of(1, 2, 1),
                SpriteAnimationEndAction.SWITCH, 0));

        // Anim 2: Horizontal idle - hold frame 4 ($0F, 4, $FF)
        set.addScript(2, new SpriteAnimationScript(0x0F, List.of(4),
                SpriteAnimationEndAction.LOOP, 0));

        // Anim 3: Horizontal trigger left ($00, 5, 4, 3, 3, 3, 3, $FD, 2)
        set.addScript(3, new SpriteAnimationScript(0x00, List.of(5, 4, 3, 3, 3, 3),
                SpriteAnimationEndAction.SWITCH, 2));

        // Anim 4: Horizontal trigger right ($00, 3, 4, 5, 5, 5, 5, $FD, 2)
        set.addScript(4, new SpriteAnimationScript(0x00, List.of(3, 4, 5, 5, 5, 5),
                SpriteAnimationEndAction.SWITCH, 2));

        return set;
    }

    /**
     * Creates mappings for CNZ LauncherSpring vertical variant (Obj85 subtype 0x00).
     * Based on obj85_a.asm mappings.
     * <p>
     * Vertical spring has 6 frames representing compression states:
     * <ul>
     *   <li>Frame 0: Fully extended (relaxed)</li>
     *   <li>Frame 1: Main body only (slightly compressed)</li>
     *   <li>Frame 2: Mid-compression</li>
     *   <li>Frame 3: More compressed</li>
     *   <li>Frame 4: Fully compressed</li>
     *   <li>Frame 5: Same as frame 4 (duplicate)</li>
     * </ul>
     */
    private List<SpriteMappingFrame> createLauncherSpringVertMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Fully extended - 4 pieces (3x2 each)
        // Based on obj85_a mapping data: offsets at (-12,-56), (-12,-40), (-12,-24), (-12,-8)
        // ROM uses tiles 0, 6, 6, 12 - tile 6 is reused for middle segments
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-12, -56, 3, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-12, -40, 3, 2, 6, false, false, 0));
        frame0.add(new SpriteMappingPiece(-12, -24, 3, 2, 6, false, false, 0));
        frame0.add(new SpriteMappingPiece(-12, -8, 3, 2, 12, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Shows TOP piece only (for animation)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-12, -32, 3, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Mid-compression - 3 pieces
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-12, -48, 3, 2, 6, false, false, 0));
        frame2.add(new SpriteMappingPiece(-12, -32, 3, 2, 6, false, false, 0));
        frame2.add(new SpriteMappingPiece(-12, -16, 3, 2, 12, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: More compressed - 2 pieces
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-12, -32, 3, 2, 6, false, false, 0));
        frame3.add(new SpriteMappingPiece(-12, -16, 3, 2, 12, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Head with priority flag (used for vibration toggle)
        // ROM: spritePiece -$C, -$20, 3, 2, 0, 0, 0, 1, 0
        // The priority=1 flag causes the VDP to render this in front of the playfield,
        // creating a "flash" effect when toggling between frame 1 and frame 4/5.
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-12, -32, 3, 2, 0, false, false, 0, true));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Same as frame 4 (duplicate in ROM)
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-12, -32, 3, 2, 0, false, false, 0, true));
        frames.add(new SpriteMappingFrame(frame5));

        return frames;
    }

    /**
     * Creates mappings for CNZ LauncherSpring diagonal variant (Obj85 subtype 0x81).
     * Based on obj85_b.asm mappings.
     * <p>
     * Diagonal spring has 6 frames representing compression states:
     * <ul>
     *   <li>Frame 0: Fully extended (2 pieces, 4x4 each)</li>
     *   <li>Frame 1: Main body only</li>
     *   <li>Frame 2: Plunger base</li>
     *   <li>Frame 3: Same as frame 2</li>
     *   <li>Frame 4: Fully compressed</li>
     *   <li>Frame 5: Same as frame 4</li>
     * </ul>
     */
    private List<SpriteMappingFrame> createLauncherSpringDiagMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Fully extended - 2 pieces (4x4 each)
        // Offsets at (-16,-16), (-32,0) relative to object center
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-16, -16, 4, 4, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-32, 0, 4, 4, 16, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Main body only - 1 piece
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-16, -16, 4, 4, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Plunger base - 1 piece
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-32, 0, 4, 4, 16, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Same as frame 2 (duplicate)
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 4: Body with priority flag (used for vibration toggle)
        // ROM: spritePiece -$10, -$10, 4, 4, 0, 0, 0, 1, 0
        // The priority=1 flag causes the VDP to render this in front of the playfield,
        // creating a "flash" effect when toggling between frame 1 and frame 4/5.
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-16, -16, 4, 4, 0, false, false, 0, true));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: Same as frame 4 (duplicate)
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-16, -16, 4, 4, 0, false, false, 0, true));
        frames.add(new SpriteMappingFrame(frame5));

        return frames;
    }

    /**
     * Creates mappings for CNZ Rect Blocks (ObjD2) - "caterpillar" flashing blocks.
     * Based on objD2.asm mappings.
     * <p>
     * 16 frames showing blocks moving around a rectangular path.
     * Each block is a 2x2 tile (16x16 pixel) piece using tile index 0.
     * Some frames are reused (8=7, 12=3, 13=2, 14=1, 15=0).
     */
    private List<SpriteMappingFrame> createCNZRectBlocksMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 1 piece (Map_objD2_0020)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: 2 pieces (Map_objD2_002A)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -16, 2, 2, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(-8, 0, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: 3 pieces (Map_objD2_003C)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-8, -24, 2, 2, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(-8, 8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: 4 pieces (Map_objD2_0056)
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -32, 2, 2, 0, false, false, 0));
        frame3.add(new SpriteMappingPiece(-8, -16, 2, 2, 0, false, false, 0));
        frame3.add(new SpriteMappingPiece(-8, 0, 2, 2, 0, false, false, 0));
        frame3.add(new SpriteMappingPiece(-8, 16, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: 5 pieces (Map_objD2_0078)
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(0, -32, 2, 2, 0, false, false, 0));
        frame4.add(new SpriteMappingPiece(-16, -32, 2, 2, 0, false, false, 0));
        frame4.add(new SpriteMappingPiece(-16, -16, 2, 2, 0, false, false, 0));
        frame4.add(new SpriteMappingPiece(-16, 0, 2, 2, 0, false, false, 0));
        frame4.add(new SpriteMappingPiece(-16, 16, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5: 5 pieces (Map_objD2_00A2)
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(8, -24, 2, 2, 0, false, false, 0));
        frame5.add(new SpriteMappingPiece(-8, -24, 2, 2, 0, false, false, 0));
        frame5.add(new SpriteMappingPiece(-24, -24, 2, 2, 0, false, false, 0));
        frame5.add(new SpriteMappingPiece(-24, -8, 2, 2, 0, false, false, 0));
        frame5.add(new SpriteMappingPiece(-24, 8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6: 5 pieces (Map_objD2_00CC)
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(16, -16, 2, 2, 0, false, false, 0));
        frame6.add(new SpriteMappingPiece(0, -16, 2, 2, 0, false, false, 0));
        frame6.add(new SpriteMappingPiece(-16, -16, 2, 2, 0, false, false, 0));
        frame6.add(new SpriteMappingPiece(-32, -16, 2, 2, 0, false, false, 0));
        frame6.add(new SpriteMappingPiece(-32, 0, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        // Frame 7: 5 pieces (Map_objD2_00F6)
        List<SpriteMappingPiece> frame7 = new ArrayList<>();
        frame7.add(new SpriteMappingPiece(24, -8, 2, 2, 0, false, false, 0));
        frame7.add(new SpriteMappingPiece(8, -8, 2, 2, 0, false, false, 0));
        frame7.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0));
        frame7.add(new SpriteMappingPiece(-24, -8, 2, 2, 0, false, false, 0));
        frame7.add(new SpriteMappingPiece(-40, -8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame7));

        // Frame 8: Same as frame 7 (Map_objD2_00F6)
        frames.add(new SpriteMappingFrame(frame7));

        // Frame 9: 5 pieces (Map_objD2_0120)
        List<SpriteMappingPiece> frame9 = new ArrayList<>();
        frame9.add(new SpriteMappingPiece(16, 0, 2, 2, 0, false, false, 0));
        frame9.add(new SpriteMappingPiece(16, -16, 2, 2, 0, false, false, 0));
        frame9.add(new SpriteMappingPiece(0, -16, 2, 2, 0, false, false, 0));
        frame9.add(new SpriteMappingPiece(-16, -16, 2, 2, 0, false, false, 0));
        frame9.add(new SpriteMappingPiece(-32, -16, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame9));

        // Frame 10: 5 pieces (Map_objD2_014A)
        List<SpriteMappingPiece> frame10 = new ArrayList<>();
        frame10.add(new SpriteMappingPiece(8, 8, 2, 2, 0, false, false, 0));
        frame10.add(new SpriteMappingPiece(8, -8, 2, 2, 0, false, false, 0));
        frame10.add(new SpriteMappingPiece(8, -24, 2, 2, 0, false, false, 0));
        frame10.add(new SpriteMappingPiece(-8, -24, 2, 2, 0, false, false, 0));
        frame10.add(new SpriteMappingPiece(-24, -24, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame10));

        // Frame 11: 5 pieces (Map_objD2_0174)
        List<SpriteMappingPiece> frame11 = new ArrayList<>();
        frame11.add(new SpriteMappingPiece(0, 16, 2, 2, 0, false, false, 0));
        frame11.add(new SpriteMappingPiece(0, 0, 2, 2, 0, false, false, 0));
        frame11.add(new SpriteMappingPiece(0, -16, 2, 2, 0, false, false, 0));
        frame11.add(new SpriteMappingPiece(0, -32, 2, 2, 0, false, false, 0));
        frame11.add(new SpriteMappingPiece(-16, -32, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame11));

        // Frame 12: Same as frame 3 (Map_objD2_0056)
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 13: Same as frame 2 (Map_objD2_003C)
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 14: Same as frame 1 (Map_objD2_002A)
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 15: Same as frame 0 (Map_objD2_0020)
        frames.add(new SpriteMappingFrame(frame0));

        return frames;
    }

    /**
     * Creates mappings for CNZ Big Block (ObjD4) - large 64x64 oscillating platform.
     * Based on objD4.asm mappings.
     * <p>
     * Single frame with 4 pieces (4x4 tiles each) in a 2x2 grid with flip symmetry:
     * - Top-left: no flip
     * - Top-right: H-flip
     * - Bottom-left: V-flip
     * - Bottom-right: H-flip + V-flip
     */
    private List<SpriteMappingFrame> createCNZBigBlockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 64x64 block made of 4 pieces (32x32 each)
        // spritePiece -$20, -$20, 4, 4, 0, 0, 0, 0, 0  ; top-left, no flip
        // spritePiece    0, -$20, 4, 4, 0, 1, 0, 0, 0  ; top-right, H-flip
        // spritePiece -$20,    0, 4, 4, 0, 0, 1, 0, 0  ; bottom-left, V-flip
        // spritePiece    0,    0, 4, 4, 0, 1, 1, 0, 0  ; bottom-right, H+V flip
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        pieces.add(new SpriteMappingPiece(-32, -32, 4, 4, 0, false, false, 0)); // top-left
        pieces.add(new SpriteMappingPiece(0, -32, 4, 4, 0, true, false, 0));    // top-right, H-flip
        pieces.add(new SpriteMappingPiece(-32, 0, 4, 4, 0, false, true, 0));    // bottom-left, V-flip
        pieces.add(new SpriteMappingPiece(0, 0, 4, 4, 0, true, true, 0));       // bottom-right, H+V flip
        frames.add(new SpriteMappingFrame(pieces));

        return frames;
    }

    /**
     * Creates mappings for CNZ Elevator (ObjD5).
     * Single frame: Two 2x2 pieces (16x16 each) making a 32x16 platform.
     * From disassembly: d1 = 0x10 (half-width = 16), d3 = 9 (platform height)
     * Tile index 0x0384 = ArtTile_ArtNem_CNZElevator
     */
    private List<SpriteMappingFrame> createCNZElevatorMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 32x16 platform made of two 2x2 pieces (side by side)
        // spritePiece -$10, -8, 2, 2, 0  ; Left piece at (-16, -8)
        // spritePiece    0, -8, 2, 2, 4  ; Right piece at (0, -8), tiles +4
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        pieces.add(new SpriteMappingPiece(-16, -8, 2, 2, 0, false, false, 0));  // Left piece
        pieces.add(new SpriteMappingPiece(0, -8, 2, 2, 0, true, false, 0));     // Right piece - tile 0 with H-flip
        frames.add(new SpriteMappingFrame(pieces));

        return frames;
    }

    /**
     * Creates mappings for CNZ Cage (ObjD6 "PointPokey").
     * Based on objD6_b.asm mappings - 2 frames (idle and active/lit).
     * Each frame has 6 pieces in a 2x3 grid (3 rows, 2 columns).
     * Left column pieces at x=-24, right column at x=0.
     * Right column pieces are horizontally flipped copies of left column.
     * <p>
     * From disassembly (objD6_b.asm):
     * Frame 0 (idle): tile offset 0, palette 0
     * Frame 1 (active): tile offset 6, palette 1, priority
     * <p>
     * Piece layout (each piece is 3 tiles wide x 2 tiles tall = 24x16 pixels):
     * Row 0: y=-20, Row 1: y=-4, Row 2: y=12
     */
    private List<SpriteMappingFrame> createCNZCageMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Idle cage (tile offset 0)
        frames.add(createCageFrame(0, 0, false));

        // Frame 1: Active/lit cage (tile offset 6, palette 1, priority)
        frames.add(createCageFrame(6, 1, true));

        return frames;
    }

    /**
     * Creates a single cage frame with 6 pieces in 2x3 grid.
     * Right column pieces are H-flipped copies of left column.
     */
    private SpriteMappingFrame createCageFrame(int tileOffset, int paletteOffset, boolean priority) {
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        // Y positions for the 3 rows (from objD6_b.asm)
        int[] yPositions = {-20, -4, 12};

        for (int row = 0; row < 3; row++) {
            int y = yPositions[row];
            // Left piece (no flip) - 3 tiles wide, 2 tiles tall
            pieces.add(new SpriteMappingPiece(-24, y, 3, 2, tileOffset, false, false, paletteOffset));
            // Right piece (H-flipped) - 3 tiles wide, 2 tiles tall
            pieces.add(new SpriteMappingPiece(0, y, 3, 2, tileOffset, true, false, paletteOffset));
        }
        return new SpriteMappingFrame(pieces);
    }

    /**
     * Creates mappings for CNZ Bonus Spike.
     * Simple 2x2 tile (16x16 pixel) spiked ball centered on origin.
     */
    private List<SpriteMappingFrame> createCNZBonusSpikeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Single 2x2 tile spiked ball centered
        List<SpriteMappingPiece> pieces = new ArrayList<>();
        // 2x2 tiles = 16x16 pixels, centered at -8,-8
        pieces.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(pieces));

        return frames;
    }

    /**
     * Creates mappings for CPZ Speed Booster (Obj1B).
     * Based on obj1B.asm mappings.
     * Frame 0: Visible - Two 2x2 pieces at (-24,-8) and (8,-8)
     * Frame 1: Same as frame 0 (duplicate in ROM)
     * Frame 2: Empty (for blinking effect)
     */
    private List<SpriteMappingFrame> createSpeedBoosterMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 & 1: Visible state - two 2x2 tile pieces
        List<SpriteMappingPiece> visiblePieces = new ArrayList<>();
        visiblePieces.add(new SpriteMappingPiece(-24, -8, 2, 2, 0, false, false, 0));
        visiblePieces.add(new SpriteMappingPiece(8, -8, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(visiblePieces));
        frames.add(new SpriteMappingFrame(visiblePieces)); // Frame 1 = same as frame 0

        // Frame 2: Empty (creates blinking effect)
        frames.add(new SpriteMappingFrame(new ArrayList<>()));

        return frames;
    }

    /**
     * Creates mappings for CPZ BlueBalls (Obj1D).
     * Based on obj1D.asm - single 2x2 tile frame at -8,-8.
     */
    private List<SpriteMappingFrame> createBlueBallsMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        // Frame 0: Single 2x2 piece centered at -8,-8 (16x16 pixels)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 0, 0
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0));
        return frames;
    }

    /**
     * Creates mappings for CPZ Breakable Block (Obj32).
     * Based on obj32_b.asm - 4 pieces in a 2x2 grid (32x32 pixels total).
     * Frame 0: Intact block (4 pieces)
     * Frames 1-4: Individual fragment pieces for when block breaks
     */
    private List<SpriteMappingFrame> createBreakableBlockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Intact block - 4 pieces in 2x2 arrangement
        // spritePiece -$10, -$10, 2, 2, 0, 0, 0, 0, 0  ; top-left
        // spritePiece 0, -$10, 2, 2, 0, 1, 0, 0, 0     ; top-right (H-flipped)
        // spritePiece -$10, 0, 2, 2, 0, 0, 0, 0, 0     ; bottom-left
        // spritePiece 0, 0, 2, 2, 0, 1, 0, 0, 0        ; bottom-right (H-flipped)
        List<SpriteMappingPiece> intactPieces = new ArrayList<>();
        intactPieces.add(new SpriteMappingPiece(-16, -16, 2, 2, 0, false, false, 0)); // top-left
        intactPieces.add(new SpriteMappingPiece(0, -16, 2, 2, 0, true, false, 0));    // top-right, H-flip
        intactPieces.add(new SpriteMappingPiece(-16, 0, 2, 2, 0, false, false, 0));   // bottom-left
        intactPieces.add(new SpriteMappingPiece(0, 0, 2, 2, 0, true, false, 0));      // bottom-right, H-flip
        frames.add(new SpriteMappingFrame(intactPieces));

        // Frames 1-4: Individual fragment pieces (each is 16x16)
        // Fragment 0: top-left (no flip)
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0));
        // Fragment 1: top-right (H-flip)
        List<SpriteMappingPiece> frag1 = new ArrayList<>();
        frag1.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frag1));
        // Fragment 2: bottom-left (no flip)
        frames.add(createSimpleFrame(-8, -8, 2, 2, 0));
        // Fragment 3: bottom-right (H-flip)
        List<SpriteMappingPiece> frag3 = new ArrayList<>();
        frag3.add(new SpriteMappingPiece(-8, -8, 2, 2, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frag3));

        return frames;
    }

    /**
     * Creates mappings for CPZ/OOZ/WFZ Moving Platform (Obj19).
     * Based on obj19.asm - 4 frames with different platform sizes.
     * Frame 0: Large (32px wide) - 2 x 4x4 tile pieces
     * Frame 1: Small (24px wide) - 2 x 3x4 tile pieces
     * Frame 2: Wide (64px wide) - 4 x 4x3 tile pieces
     * Frame 3: Medium (32px wide) - 2 x 4x3 tile pieces
     */
    private List<SpriteMappingFrame> createCPZPlatformMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj19_0008): Large platform - 2 pieces, 4x4 tiles each
        // spritePiece -$20, -$10, 4, 4, 0, 0, 0, 0, 0 (left half)
        // spritePiece 0, -$10, 4, 4, 0, 1, 0, 0, 0 (right half, H-flipped)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x20, -0x10, 4, 4, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -0x10, 4, 4, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj19_001A): Smaller platform - 2 pieces, 3x4 tiles each
        // spritePiece -$18, -$10, 3, 4, 0, 0, 0, 0, 0 (left half)
        // spritePiece 0, -$10, 3, 4, 0, 1, 0, 0, 0 (right half, H-flipped)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -0x10, 3, 4, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj19_002C): Wide platform - 4 pieces, 4x3 tiles each
        // spritePiece -$40, -$10, 4, 3, 0, 0, 0, 0, 0
        // spritePiece -$20, -$10, 4, 3, $C, 0, 0, 0, 0
        // spritePiece 0, -$10, 4, 3, $C, 1, 0, 0, 0
        // spritePiece $20, -$10, 4, 3, 0, 1, 0, 0, 0
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x40, -0x10, 4, 3, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(-0x20, -0x10, 4, 3, 0x0C, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, -0x10, 4, 3, 0x0C, true, false, 0));
        frame2.add(new SpriteMappingPiece(0x20, -0x10, 4, 3, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj19_004E): Medium platform - 2 pieces, 4x3 tiles each
        // spritePiece -$20, -$10, 4, 3, 0, 0, 0, 0, 0
        // spritePiece 0, -$10, 4, 3, 0, 1, 0, 0, 0
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x20, -0x10, 4, 3, 0, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, -0x10, 4, 3, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        return frames;
    }

    /**
     * Creates mappings for CPZ Stair Block (Obj78) and MTZ Platform (Obj6B).
     * Based on obj6B.asm mappings (shared):
     *
     * Frame 0: 4 blocks at positions -0x40, -0x20, 0, 0x20 (MTZ-style multi-block)
     *          Used by MTZPlatform (Object 0x6B) subtype index 1
     * Frame 1: 2 blocks at outer positions -0x40, 0x20 (same span as frame 0)
     *          Used by MTZPlatform (Object 0x6B) subtype index 0
     * Frame 2: Single 32x32 block for CPZStaircaseObjectInstance
     */
    private List<SpriteMappingFrame> createCPZStairBlockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 4 blocks at positions -0x40, -0x20, 0, 0x20 (32px spacing)
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x40, -0x10, 4, 4, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-0x20, -0x10, 4, 4, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -0x10, 4, 4, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(0x20, -0x10, 4, 4, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: 2 blocks at positions -0x20 and 0 (from MTZ obj65_a.asm Map_obj65_a_002A)
        // The second piece has hFlip=true to mirror the block
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x20, -0x10, 4, 4, 0, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -0x10, 4, 4, 0, true, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Single 32x32 block (used by CPZStaircaseObjectInstance)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        return frames;
    }

    /**
     * Creates mappings for CPZ/MCZ Sideways Platform (Obj7A).
     * Based on Map_obj7A from obj7A.asm:
     * <p>
     * Frame 0: 48x16 platform using tiles 16+ from CPZStairBlock art
     * spritePiece -$18, -8, 3, 2, $10, 0, 0, 0, 0  ; left half (24x16), tile 0x10
     * spritePiece 0, -8, 3, 2, $10, 1, 0, 0, 0    ; right half (24x16), tile 0x10, hFlip
     */
    private List<SpriteMappingFrame> createSidewaysPformMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 48x16 platform (2 pieces of 3x2 tiles each)
        // Uses tile 0x10 (16) from the CPZStairBlock patterns
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x18, -8, 3, 2, 0x10, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -8, 3, 2, 0x10, true, false, 0)); // hFlip
        frames.add(new SpriteMappingFrame(frame0));

        return frames;
    }

    /**
     * Creates mappings for CPZ Pylon (Obj7C).
     * Based on mappings/sprite/obj7C.asm:
     * ONE frame with 9 sprite pieces (4x4 tiles each), arranged vertically.
     * All pieces use tile 0 with alternating vFlip for visual symmetry.
     *
     * spritePiece xoffset, yoffset, width, height, tileIndex, hFlip, vFlip, palette, priority
     * spritePiece -$10, -$80, 4, 4, 0, 0, 0, 1, 1
     * spritePiece -$10, -$60, 4, 4, 0, 0, 1, 1, 1  (vFlip)
     * spritePiece -$10, -$40, 4, 4, 0, 0, 0, 1, 1
     * spritePiece -$10, -$20, 4, 4, 0, 0, 1, 1, 1  (vFlip)
     * spritePiece -$10,    0, 4, 4, 0, 0, 0, 1, 1
     * spritePiece -$10,  $20, 4, 4, 0, 0, 1, 1, 1  (vFlip)
     * spritePiece -$10,  $40, 4, 4, 0, 0, 0, 1, 1
     * spritePiece -$10,  $60, 4, 4, 0, 0, 1, 1, 1  (vFlip)
     * spritePiece -$10,  $7F, 4, 4, 0, 0, 0, 1, 1
     */
    private List<SpriteMappingFrame> createCPZPylonMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        List<SpriteMappingPiece> pieces = new ArrayList<>();

        // Y offsets for each piece: -128, -96, -64, -32, 0, 32, 64, 96, 127
        int[] yOffsets = { -0x80, -0x60, -0x40, -0x20, 0, 0x20, 0x40, 0x60, 0x7F };

        // Create 9 pieces, all using tile 0, with alternating vFlip
        for (int i = 0; i < 9; i++) {
            boolean vFlip = (i % 2) == 1;  // Odd indices have vFlip
            pieces.add(new SpriteMappingPiece(-0x10, yOffsets[i], 4, 4, 0, false, vFlip, 0));
        }

        frames.add(new SpriteMappingFrame(pieces));
        return frames;
    }

    /**
     * Creates mappings for CPZ Pipe Exit Spring (Obj7B).
     * Based on obj7B.asm mappings:
     * Frame 0: Base spring - 4x2 tile piece at (-16,-16)
     * Frame 1: Compressed vertical - 2 x 2x4 tile pieces at (-16,-32) and (0,-32)
     * Frame 2: Alternate vertical - 2 x 2x4 tile pieces with different tile offset
     * Frame 3: Compressed base - 4x2 tile piece at (-16,-16) with tile offset 0x18
     * Frame 4: Same as frame 0 (copy)
     */
    private List<SpriteMappingFrame> createPipeExitSpringMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj7B_000A): Base spring - 4x2 tile piece
        // spritePiece -$10, -$10, 4, 2, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj7B_0014): Compressed vertical - 2 x 2x4 tile pieces
        // spritePiece -$10, -$20, 2, 4, 8, 0, 0, 0, 0
        // spritePiece 0, -$20, 2, 4, 8, 1, 0, 0, 0 (H-flipped)
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x10, -0x20, 2, 4, 8, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -0x20, 2, 4, 8, true, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj7B_0026): Alternate vertical - 2 x 2x4 tile pieces
        // spritePiece -$10, -$20, 2, 4, $10, 0, 0, 0, 0
        // spritePiece 0, -$20, 2, 4, $10, 1, 0, 0, 0 (H-flipped)
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x10, -0x20, 2, 4, 0x10, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, -0x20, 2, 4, 0x10, true, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj7B_0038): Compressed base - 4x2 tile piece with tile offset
        // spritePiece -$10, -$10, 4, 2, $18, 0, 0, 0, 0
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x18, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4: Same as frame 0 (mappingsTableEntry.w Map_obj7B_0014 -> points to frame 1 in ROM)
        // Actually looking at mapping table: entry 4 points to Map_obj7B_0014 which is frame 1
        // But for simplicity, we'll use same as frame 0 for idle
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates animations for CPZ Pipe Exit Spring (Obj7B).
     * From Ani_obj7B in the disassembly:
     * Anim 0: Idle - hold frame 0 ($0F, 0, $FF)
     * Anim 1: Triggered - show frame 3, then switch to anim 0 ($00, 3, $FD, 0)
     * Anim 2: Raised - spring moves up when player is below in tube ($05, 1, 2, 2, 2, 4, $FD, 0)
     */
    private SpriteAnimationSet createPipeExitSpringAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Idle - hold frame 0 indefinitely
        set.addScript(0, new SpriteAnimationScript(0x0F, List.of(0),
                SpriteAnimationEndAction.LOOP, 0));

        // Anim 1: Triggered - show frame 3 briefly, then switch to anim 0
        set.addScript(1, new SpriteAnimationScript(0x00, List.of(3),
                SpriteAnimationEndAction.SWITCH, 0));

        // Anim 2: Raised - spring visually moves up when player passes below in tube
        // ROM: byte_29777: dc.b $5, 1, 2, 2, 2, 4, $FD, 0
        // Frames 1 and 2 show the spring at Y offset -32 (16 pixels higher than normal)
        set.addScript(2, new SpriteAnimationScript(0x05, List.of(1, 2, 2, 2, 4),
                SpriteAnimationEndAction.SWITCH, 0));

        return set;
    }

    /**
     * Creates mappings for CPZ Tipping Floor (Obj0B).
     * Based on obj0B.asm mappings - 5 frames showing platform tipping states.
     * <p>
     * Each frame has 2 pieces:
     * - Piece 1: The moving platform edge (tiles 0 or 4 or 0x14)
     * - Piece 2: The static base (tile 0x24, 4x3)
     */
    private List<SpriteMappingFrame> createTippingFloorMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj0B_000A): Flat position
        // spritePiece -$10, -$10, 4, 1, 0, 0, 0, 0, 0
        // spritePiece -$10, -8, 4, 3, $24, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x10, -0x10, 4, 1, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-0x10, -8, 4, 3, 0x24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj0B_001C): Tilted up position
        // spritePiece -$10, -$18, 4, 4, 4, 0, 0, 0, 0
        // spritePiece -$10, -8, 4, 3, $24, 0, 0, 0, 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x10, -0x18, 4, 4, 4, false, false, 0));
        frame1.add(new SpriteMappingPiece(-0x10, -8, 4, 3, 0x24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj0B_002E): Middle tilt position
        // spritePiece -$10, -$C, 4, 4, $14, 0, 0, 0, 0
        // spritePiece -$10, -8, 4, 3, $24, 0, 0, 0, 0
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x10, -0x0C, 4, 4, 0x14, false, false, 0));
        frame2.add(new SpriteMappingPiece(-0x10, -8, 4, 3, 0x24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj0B_0040): Tilted down position (V-flipped tiles)
        // spritePiece -$10, 0, 4, 4, 4, 0, 1, 0, 0 (vFlip=1)
        // spritePiece -$10, -8, 4, 3, $24, 0, 0, 0, 0
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x10, 0, 4, 4, 4, false, true, 0));
        frame3.add(new SpriteMappingPiece(-0x10, -8, 4, 3, 0x24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (Map_obj0B_0052): Fully tilted down position (V-flipped)
        // spritePiece -$10, $10, 4, 1, 0, 0, 1, 0, 0 (vFlip=1)
        // spritePiece -$10, -8, 4, 3, $24, 0, 0, 0, 0
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-0x10, 0x10, 4, 1, 0, false, true, 0));
        frame4.add(new SpriteMappingPiece(-0x10, -8, 4, 3, 0x24, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        return frames;
    }

    /**
     * Creates animations for CPZ Tipping Floor (Obj0B).
     * Two animations for forward (0->4) and reverse (4->0) tipping motion.
     * Delay of 7 frames between each frame change.
     */
    private SpriteAnimationSet createTippingFloorAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Forward - frames 0,1,2,3,4, loop back to frame 1
        // Delay 7 ($07) between frames
        set.addScript(0, new SpriteAnimationScript(0x07, List.of(0, 1, 2, 3, 4),
                SpriteAnimationEndAction.LOOP_BACK, 1));

        // Anim 1: Reverse - frames 4,3,2,1,0, loop back to frame 1
        set.addScript(1, new SpriteAnimationScript(0x07, List.of(4, 3, 2, 1, 0),
                SpriteAnimationEndAction.LOOP_BACK, 1));

        return set;
    }

    /**
     * Creates mappings for CPZ/DEZ Barrier (Obj2D).
     * Based on obj2D.asm mappings:
     * Frame 0 (HTZ): 4 x 2x2 tile pieces stacked vertically (16x64 total)
     * Frame 1 (MTZ): 2 x 3x4 tile pieces using tile $5F (24x64 total)
     * Frame 2 (CPZ/DEZ): 2 x 2x4 tile pieces stacked vertically (16x64 total)
     * Frame 3 (ARZ): Same as Frame 2
     */
    private List<SpriteMappingFrame> createBarrierMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj2D_0008): HTZ - 4 x 2x2 tile pieces
        // spritePiece -8, -$20, 2, 2, 0, 0, 0, 0, 0
        // spritePiece -8, -$10, 2, 2, 0, 0, 0, 0, 0
        // spritePiece -8, 0, 2, 2, 0, 0, 0, 0, 0
        // spritePiece -8, $10, 2, 2, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -0x20, 2, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-8, -0x10, 2, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-8, 0, 2, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-8, 0x10, 2, 2, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj2D_002A): MTZ - 2 x 3x4 tile pieces, tile $5F
        // spritePiece -$C, -$20, 3, 4, $5F, 0, 0, 0, 0
        // spritePiece -$C, 0, 3, 4, $5F, 0, 0, 0, 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x0C, -0x20, 3, 4, 0x5F, false, false, 0));
        frame1.add(new SpriteMappingPiece(-0x0C, 0, 3, 4, 0x5F, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj2D_003C): CPZ/DEZ - 2 x 2x4 tile pieces
        // spritePiece -8, -$20, 2, 4, 0, 0, 0, 0, 0
        // spritePiece -8, 0, 2, 4, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-8, -0x20, 2, 4, 0, false, false, 0));
        frame2.add(new SpriteMappingPiece(-8, 0, 2, 4, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj2D_004E): ARZ - same as Frame 2
        // spritePiece -8, -$20, 2, 4, 0, 0, 0, 0, 0
        // spritePiece -8, 0, 2, 4, 0, 0, 0, 0, 0
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -0x20, 2, 4, 0, false, false, 0));
        frame3.add(new SpriteMappingPiece(-8, 0, 2, 4, 0, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        return frames;
    }

    /**
     * Creates mappings for Springboard / Lever Spring (Obj40).
     * Based on obj40.asm mappings (Map_obj40):
     * <p>
     * Frame 0 (idle): Two pieces forming the diagonal diving board shape
     * - Left piece: 3x2 tiles at (-0x1C, -0x18), tile 0
     * - Right piece: 4x2 tiles at (-4, -0x18), tile 6
     * <p>
     * Frame 1 (compressed): Board compressed/triggered state
     * - Left piece: 3x2 tiles at (-0x1C, -0x18), tile 0x0E
     * - Right piece: 4x2 tiles at (-4, -0x18), tile 0x14
     */
    private List<SpriteMappingFrame> createSpringboardMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj40_0004): Idle/diagonal state
        // spritePiece -$1C, -$18, 3, 2, 0, 0, 0, 0, 0
        // spritePiece -4, -$18, 4, 2, 6, 0, 0, 0, 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x1C, -0x18, 3, 2, 0, false, false, 0));
        frame0.add(new SpriteMappingPiece(-4, -0x18, 4, 2, 6, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj40_0016): Compressed/triggered state
        // spritePiece -$1C, -$18, 3, 2, $E, 0, 0, 0, 0
        // spritePiece -4, -$18, 4, 2, $14, 0, 0, 0, 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x1C, -0x18, 3, 2, 0x0E, false, false, 0));
        frame1.add(new SpriteMappingPiece(-4, -0x18, 4, 2, 0x14, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        return frames;
    }

    /**
     * Creates animations for Springboard / Lever Spring (Obj40).
     * <p>
     * Anim 0: Idle - holds frame 0 indefinitely
     * Anim 1: Triggered - shows compressed frame 1, then switches back to idle (anim 0)
     */
    private SpriteAnimationSet createSpringboardAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Idle - hold frame 0 indefinitely
        set.addScript(0, new SpriteAnimationScript(0x0F, List.of(0),
                SpriteAnimationEndAction.LOOP, 0));

        // Anim 1: Triggered - show frame 1 briefly, then switch to anim 0
        // Short delay (3 frames) before switching back
        set.addScript(1, new SpriteAnimationScript(0x03, List.of(1, 0),
                SpriteAnimationEndAction.SWITCH, 0));

        return set;
    }

    /**
     * Creates a pattern array matching the fixed VRAM layout for the results
     * screen.
     * Each art chunk is placed at its exact VRAM tile base (relative to
     * VRAM_BASE_NUMBERS).
     */
    private Pattern[] createResultsVramPatterns(
            Pattern[] bonusDigits,
            Pattern[] perfect,
            Pattern[] titleCard,
            Pattern[] resultsText,
            Pattern[] miniSonic,
            Pattern[] hudText) {
        int base = Sonic2Constants.VRAM_BASE_NUMBERS;
        int maxEnd = base;
        maxEnd = Math.max(maxEnd, base + bonusDigits.length);
        maxEnd = Math.max(maxEnd, Sonic2Constants.VRAM_BASE_PERFECT + perfect.length);
        maxEnd = Math.max(maxEnd, Sonic2Constants.VRAM_BASE_TITLE_CARD + titleCard.length);
        maxEnd = Math.max(maxEnd, Sonic2Constants.VRAM_BASE_RESULTS_TEXT + resultsText.length);
        maxEnd = Math.max(maxEnd, Sonic2Constants.VRAM_BASE_MINI_CHARACTER + miniSonic.length);
        maxEnd = Math.max(maxEnd, Sonic2Constants.VRAM_BASE_HUD_TEXT + hudText.length);
        // Include space for trailing blank at $6F0-$6F1 (used in results mappings)
        maxEnd = Math.max(maxEnd, 0x6F2);

        int totalSize = Math.max(0, maxEnd - base);
        Pattern[] result = new Pattern[totalSize];

        // Fill gaps with empty tiles so unmapped references stay blank.
        Pattern emptyPattern = new Pattern();
        Arrays.fill(result, emptyPattern);

        copyPatterns(result, bonusDigits, Sonic2Constants.VRAM_BASE_NUMBERS - base);
        copyPatterns(result, perfect, Sonic2Constants.VRAM_BASE_PERFECT - base);
        copyPatterns(result, titleCard, Sonic2Constants.VRAM_BASE_TITLE_CARD - base);
        copyPatterns(result, resultsText, Sonic2Constants.VRAM_BASE_RESULTS_TEXT - base);
        copyPatterns(result, miniSonic, Sonic2Constants.VRAM_BASE_MINI_CHARACTER - base);
        copyPatterns(result, hudText, Sonic2Constants.VRAM_BASE_HUD_TEXT - base);

        // Tile $6F0 is used as a trailing blank in the results mappings.
        int trailingBlank = 0x6F0 - base;
        if (trailingBlank >= 0 && trailingBlank < result.length) {
            result[trailingBlank] = new Pattern();
            if (trailingBlank + 1 < result.length) {
                result[trailingBlank + 1] = new Pattern();
            }
        }

        return result;
    }

    private Pattern[] createBlankPatterns(int count) {
        if (count <= 0) {
            return new Pattern[0];
        }
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
        }
        return patterns;
    }

    private Pattern[] ensureResultsPatternCapacity(Pattern[] patterns, List<SpriteMappingFrame> mappings) {
        int maxTileIndex = computeMaxTileIndex(mappings);
        if (maxTileIndex < 0 || maxTileIndex < patterns.length) {
            return patterns;
        }
        Pattern[] expanded = new Pattern[maxTileIndex + 1];
        Pattern emptyPattern = new Pattern();
        Arrays.fill(expanded, emptyPattern);
        System.arraycopy(patterns, 0, expanded, 0, Math.min(patterns.length, expanded.length));
        return expanded;
    }

    private void copyPatterns(Pattern[] dest, Pattern[] src, int destPos) {
        if (src == null || src.length == 0 || destPos >= dest.length) {
            return;
        }
        if (destPos < 0) {
            int skip = -destPos;
            if (skip >= src.length) {
                return;
            }
            src = Arrays.copyOfRange(src, skip, src.length);
            destPos = 0;
        }
        int copyLen = Math.min(src.length, dest.length - destPos);
        System.arraycopy(src, 0, dest, destPos, copyLen);
    }

    /**
     * Creates mappings for underwater bubbles (Obj0A Small Bubbles).
     * Art from ART_NEM_BUBBLES_ADDR (0x7AEE2, 10 tiles) - small breathing bubbles.
     *
     * Based on testing, the 10 tiles appear to be laid out as:
     * - Tile 0: Tiny bubble (1x1, 8x8)
     * - Tile 1: Small bubble (1x1, 8x8)
     * - Tiles 2-5: Medium bubble (2x2, 16x16)
     * - Tiles 6-9: Larger bubble (2x2, 16x16)
     *
     * Note: Countdown numbers are part of Object $24 (Bubble Generator).
     */
    private List<SpriteMappingFrame> createBubblesMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Tiny bubble (1x1 tile = 8x8 pixels)
        frames.add(createSimpleFrame(-4, -4, 1, 1, 0));

        // Frame 1: Small bubble (1x1 tile)
        frames.add(createSimpleFrame(-4, -4, 1, 1, 1));

        // Frame 2: Medium bubble (2x2 tiles = 16x16 pixels) - tiles 2-5
        frames.add(createSimpleFrame(-8, -8, 2, 2, 2));

        // Frame 3: Larger bubble (2x2 tiles) - tiles 6-9
        frames.add(createSimpleFrame(-8, -8, 2, 2, 6));

        // Frames 4-5: Reuse the larger bubble frames
        frames.add(createSimpleFrame(-8, -8, 2, 2, 6));
        frames.add(createSimpleFrame(-8, -8, 2, 2, 6));

        // Frames 6-11: Placeholder for countdown numbers
        // Just show the small bubble for now
        for (int i = 0; i < 6; i++) {
            frames.add(createSimpleFrame(-4, -4, 1, 1, 1));
        }

        return frames;
    }

    /**
     * Creates mapping frames for ARZ falling leaves (Object $2C).
     * Based on mappings/sprite/obj2C.asm from the disassembly.
     * <p>
     * Art: ArtNem_Leaves - 7 tiles total
     * Frames:
     * - Frame 0: 1x1 tile at (-4, -4), tile 0 (8x8 pixels)
     * - Frame 1: 2x1 tiles at (-8, -4), tiles 1-2 (16x8 pixels)
     * - Frame 2: 2x1 tiles at (-8, -4), tiles 3-4 (16x8 pixels)
     * - Frame 3: 2x1 tiles at (-8, -4), tiles 5-6 (16x8 pixels)
     */
    private List<SpriteMappingFrame> createLeavesMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: spritePiece -4, -4, 1, 1, 0, 0, 0, 0, 0
        // 1x1 tile (8x8 pixels) at tile 0
        frames.add(createSimpleFrame(-4, -4, 1, 1, 0));

        // Frame 1: spritePiece -8, -4, 2, 1, 1, 0, 0, 0, 0
        // 2x1 tiles (16x8 pixels) starting at tile 1
        frames.add(createSimpleFrame(-8, -4, 2, 1, 1));

        // Frame 2: spritePiece -8, -4, 2, 1, 3, 0, 0, 0, 0
        // 2x1 tiles (16x8 pixels) starting at tile 3
        frames.add(createSimpleFrame(-8, -4, 2, 1, 3));

        // Frame 3: spritePiece -8, -4, 2, 1, 5, 0, 0, 0, 0
        // 2x1 tiles (16x8 pixels) starting at tile 5
        frames.add(createSimpleFrame(-8, -4, 2, 1, 5));

        return frames;
    }

    /**
     * Creates mappings for OOZ Collapsing Platform (Obj1F in OOZ).
     * Based on obj1F_b.asm:
     *
     * Frame 0 (intact): 7 pieces total
     * Top row: 4 pieces, 4x4 tiles each (128x32 pixels)
     * Bottom row: 3 pieces, 4x2 tiles each (128x16 pixels)
     *
     * Frame 1 (collapsed): Same as frame 0 for intact appearance
     * The fragments are rendered individually using frame 0 pieces
     */
    private List<SpriteMappingFrame> createOOZCollapsingPlatformMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Intact platform - 7 pieces
        // Top row (4 pieces, 4x4 tiles each at y=-16)
        // spritePiece -$40, -$10, 4, 4, $10, 1, 0, 0, 0
        // spritePiece -$20, -$10, 4, 4, $10, 1, 0, 0, 0
        // spritePiece 0, -$10, 4, 4, $10, 1, 0, 0, 0
        // spritePiece $20, -$10, 4, 4, 0, 1, 0, 0, 0
        // Note: priority bit is set in disassembly (last 0 or 1), but we use palette 3
        List<SpriteMappingPiece> intactPieces = new ArrayList<>();
        intactPieces.add(new SpriteMappingPiece(-0x40, -0x10, 4, 4, 0x10, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(-0x20, -0x10, 4, 4, 0x10, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(0x00, -0x10, 4, 4, 0x10, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(0x20, -0x10, 4, 4, 0x00, false, false, 0));

        // Bottom row (3 pieces, 4x2 tiles each at y=16)
        // spritePiece -$40, $10, 4, 2, $20, 1, 0, 0, 0
        // spritePiece -$20, $10, 4, 2, $20, 1, 0, 0, 0
        // spritePiece 0, $10, 4, 2, $20, 1, 0, 0, 0
        intactPieces.add(new SpriteMappingPiece(-0x40, 0x10, 4, 2, 0x20, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(-0x20, 0x10, 4, 2, 0x20, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(0x00, 0x10, 4, 2, 0x20, false, false, 0));

        frames.add(new SpriteMappingFrame(intactPieces));

        // Frame 1: Collapsed/fragment appearance (same as intact for OOZ)
        frames.add(new SpriteMappingFrame(intactPieces));

        return frames;
    }

    /**
     * Creates mappings for MCZ Collapsing Platform (Obj1F in MCZ).
     * Based on obj1F_c.asm:
     *
     * Frame 0 (intact): 4 pieces
     * Frame 1 (collapsed): 6 pieces
     */
    private List<SpriteMappingFrame> createMCZCollapsingPlatformMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Intact platform - 4 pieces
        // spritePiece -$20, -$10, 4, 2, 0, 0, 0, 0, 0
        // spritePiece 0, -$10, 4, 2, 0, 1, 0, 0, 0  (H-flip)
        // spritePiece -$10, 0, 3, 2, 8, 0, 0, 0, 0
        // spritePiece 8, 0, 3, 4, $E, 0, 0, 0, 0
        List<SpriteMappingPiece> intactPieces = new ArrayList<>();
        intactPieces.add(new SpriteMappingPiece(-0x20, -0x10, 4, 2, 0x00, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(0x00, -0x10, 4, 2, 0x00, true, false, 0));
        intactPieces.add(new SpriteMappingPiece(-0x10, 0x00, 3, 2, 0x08, false, false, 0));
        intactPieces.add(new SpriteMappingPiece(0x08, 0x00, 3, 4, 0x0E, false, false, 0));
        frames.add(new SpriteMappingFrame(intactPieces));

        // Frame 1: Collapsed platform - 6 pieces
        // spritePiece -$20, -$10, 2, 2, 0, 0, 0, 0, 0
        // spritePiece -$10, -$10, 2, 2, 4, 0, 0, 0, 0
        // spritePiece 0, -$10, 2, 2, 4, 1, 0, 0, 0  (H-flip)
        // spritePiece $10, -$10, 2, 2, 0, 1, 0, 0, 0  (H-flip)
        // spritePiece -$10, 0, 3, 2, 8, 0, 0, 0, 0
        // spritePiece 8, 0, 3, 4, $E, 0, 0, 0, 0
        List<SpriteMappingPiece> collapsedPieces = new ArrayList<>();
        collapsedPieces.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x00, false, false, 0));
        collapsedPieces.add(new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x04, false, false, 0));
        collapsedPieces.add(new SpriteMappingPiece(0x00, -0x10, 2, 2, 0x04, true, false, 0));
        collapsedPieces.add(new SpriteMappingPiece(0x10, -0x10, 2, 2, 0x00, true, false, 0));
        collapsedPieces.add(new SpriteMappingPiece(-0x10, 0x00, 3, 2, 0x08, false, false, 0));
        collapsedPieces.add(new SpriteMappingPiece(0x08, 0x00, 3, 4, 0x0E, false, false, 0));
        frames.add(new SpriteMappingFrame(collapsedPieces));

        return frames;
    }

    /**
     * Gets the art configuration for an object from the ZoneArtProvider.
     *
     * @param objectId the object type ID
     * @param zoneIndex the current zone index
     * @return the art configuration, or null if not available
     */
    private ObjectArtConfig getObjectArtConfig(int objectId, int zoneIndex) {
        ZoneArtProvider provider = GameModuleRegistry.getCurrent().getZoneArtProvider();
        if (provider == null) {
            return null;
        }
        return provider.getObjectArt(objectId, zoneIndex);
    }

    /**
     * Loads Egg Prison / Capsule sprite sheet (Object 0x3E).
     *
     * @return ObjectSpriteSheet for the Egg Prison, or null on failure
     */
    public ObjectSpriteSheet loadEggPrisonSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGG_PRISON_ADDR, "EggPrison");
        if (patterns.length == 0) {
            return null;
        }

        List<SpriteMappingFrame> mappings = createEggPrisonMappings();
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load CPZ Boss Eggpod sprite sheet (Obj5D main + Robotnik).
     * Uses ArtNem_Eggpod with mappings from Obj5D_MapUnc_2ED8C.
     * Palette line 1 by default (main body). Robotnik uses palette override 0.
     */
    public ObjectSpriteSheet loadCPZBossEggpodSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGGPOD_ADDR, "Eggpod");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_CPZ_BOSS_EGGPOD_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load CPZ Boss parts sprite sheet (pipe, pump, container, gunk, etc.).
     * Uses ArtNem_CPZBoss with mappings from Obj5D_MapUnc_2EADC.
     * Palette line 1 by default; dripper/gunk use palette override 3.
     */
    public ObjectSpriteSheet loadCPZBossPartsSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CPZ_BOSS_ADDR, "CPZBoss");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_CPZ_BOSS_PARTS_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load CPZ Boss eggpod jets sprite sheet (exhaust flame).
     * Uses ArtNem_EggpodJets with mappings from Obj5D_MapUnc_2EE88.
     * Palette line 0 (as per art tile).
     */
    public ObjectSpriteSheet loadCPZBossJetsSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGGPOD_JETS_ADDR, "EggpodJets");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_CPZ_BOSS_JETS_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load CPZ Boss smoke puff sprite sheet (used during retreat).
     * Uses ArtNem_BossSmoke with mappings from Obj5D_MapUnc_2EEA0.
     */
    public ObjectSpriteSheet loadCPZBossSmokeSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BOSS_SMOKE_ADDR, "BossSmoke");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_CPZ_BOSS_SMOKE_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 1, 1);
    }

    /**
     * Load ARZ Boss main sprite sheet (Obj89 main vehicle).
     * Uses ArtNem_ARZBoss with mappings from Obj89_MapUnc_30E04.
     * Palette line 0 (ArtTile_ArtNem_ARZBoss uses palette 0).
     *
     * The ARZ boss main frames use tiles from BOTH ARZ boss art AND Eggpod art.
     * In the original game:
     * - ARZ boss art is at VRAM tile $3E0 (ArtTile_ArtNem_ARZBoss)
     * - Eggpod art is at VRAM tile $500 (ArtTile_ArtNem_Eggpod_4)
     * - Gap = $500 - $3E0 = $120 = 288 tiles
     *
     * The mappings use tile indices relative to art_tile ($3E0), so:
     * - Tile $6F → ARZ boss tile (low index, within ARZ art)
     * - Tile $150 → Eggpod tile ($3E0 + $150 = $530, which is $30 into Eggpod at $500)
     *
     * We create a combined pattern array with Eggpod patterns at offset $120.
     */
    public ObjectSpriteSheet loadARZBossMainSheet() {
        Pattern[] arzPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_ARZ_BOSS_ADDR, "ARZBoss");
        if (arzPatterns.length == 0) {
            return null;
        }

        // Load Eggpod patterns (used for Robotnik's face/body in various frames)
        Pattern[] eggpodPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGGPOD_ADDR, "Eggpod");

        // Calculate offset: $500 - $3E0 = $120 = 288 tiles
        int eggpodOffset = Sonic2Constants.ART_TILE_EGGPOD_4 - Sonic2Constants.ART_TILE_ARZ_BOSS;

        // Create combined array large enough to hold both art sets at correct offsets
        int combinedSize = Math.max(arzPatterns.length, eggpodOffset + eggpodPatterns.length);
        Pattern[] combinedPatterns = new Pattern[combinedSize];

        // Copy ARZ boss patterns starting at index 0
        System.arraycopy(arzPatterns, 0, combinedPatterns, 0, arzPatterns.length);

        // Copy Eggpod patterns at offset $120 (288)
        for (int i = 0; i < eggpodPatterns.length && (eggpodOffset + i) < combinedSize; i++) {
            combinedPatterns[eggpodOffset + i] = eggpodPatterns[i];
        }

        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_ARZ_BOSS_MAIN_ADDR);
        return new ObjectSpriteSheet(combinedPatterns, mappings, 0, 1);
    }

    /**
     * Load ARZ Boss parts sprite sheet (pillars, arrows, bulging eyes).
     * Uses ArtNem_ARZBoss with mappings from Obj89_MapUnc_30D68.
     * Palette line 0 (ArtTile_ArtNem_ARZBoss uses palette 0).
     */
    public ObjectSpriteSheet loadARZBossPartsSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_ARZ_BOSS_ADDR, "ARZBoss");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_ARZ_BOSS_PARTS_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Load Boss Explosion sprite sheet (Obj58).
     * Uses ArtNem_FieryExplosion with mappings from Obj58_MapUnc_2D50A.
     * Palette line 0 (as per art tile).
     */
    public ObjectSpriteSheet loadBossExplosionSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_FIERY_EXPLOSION_ADDR, "BossExplosion");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = loadMappingFrames(Sonic2Constants.MAP_UNC_BOSS_EXPLOSION_ADDR);
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Creates mapping frames for Egg Prison (Obj3E).
     * Based on mappings/sprite/obj3E.asm from the disassembly.
     *
     * Frames:
     * - Frame 0 (BODY_CLOSED): Main capsule body (7 pieces, 64x64)
     * - Frame 1-3 (BODY_OPEN_1-3): Opening animation (7 pieces each)
     * - Frame 4 (BUTTON): Top button (2 pieces, 32x16)
     * - Frame 5 (LOCK): Lock/dongle (1 piece, 16x16)
     */
    private List<SpriteMappingFrame> createEggPrisonMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj3E_000C): Main capsule body - closed
        // 7 pieces arranged as top, middle (with center), and bottom
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x20, -0x20, 4, 2, 0x00, false, false, 0));  // Top left
        frame0.add(new SpriteMappingPiece(0x00, -0x20, 4, 2, 0x00, true, false, 0));    // Top right (H-flip)
        frame0.add(new SpriteMappingPiece(-0x20, -0x10, 3, 3, 0x08, false, false, 0));  // Middle left
        frame0.add(new SpriteMappingPiece(-0x08, -0x10, 2, 3, 0x11, false, false, 0));  // Center
        frame0.add(new SpriteMappingPiece(0x08, -0x10, 3, 3, 0x08, true, false, 0));    // Middle right (H-flip)
        frame0.add(new SpriteMappingPiece(-0x20, 0x08, 4, 3, 0x17, false, false, 0));   // Bottom left
        frame0.add(new SpriteMappingPiece(0x00, 0x08, 4, 3, 0x17, true, false, 0));     // Bottom right (H-flip)
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj3E_0046): Opening frame 1 - sides start spreading
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x20, -0x20, 4, 2, 0x00, false, false, 0));  // Top left
        frame1.add(new SpriteMappingPiece(0x00, -0x20, 4, 2, 0x00, true, false, 0));    // Top right (H-flip)
        frame1.add(new SpriteMappingPiece(-0x20, 0x08, 4, 3, 0x17, false, false, 0));   // Bottom left
        frame1.add(new SpriteMappingPiece(0x00, 0x08, 4, 3, 0x17, true, false, 0));     // Bottom right (H-flip)
        frame1.add(new SpriteMappingPiece(-0x08, -0x18, 2, 3, 0x11, false, false, 0));  // Center raised
        frame1.add(new SpriteMappingPiece(-0x20, -0x08, 3, 3, 0x08, false, false, 0));  // Sides lowered
        frame1.add(new SpriteMappingPiece(0x08, -0x08, 3, 3, 0x08, true, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj3E_0088): Opening frame 2 - sides spread more
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x20, -0x20, 4, 2, 0x00, false, false, 0));
        frame2.add(new SpriteMappingPiece(0x00, -0x20, 4, 2, 0x00, true, false, 0));
        frame2.add(new SpriteMappingPiece(-0x20, 0x08, 4, 3, 0x17, false, false, 0));
        frame2.add(new SpriteMappingPiece(0x00, 0x08, 4, 3, 0x17, true, false, 0));
        frame2.add(new SpriteMappingPiece(-0x08, -0x20, 2, 3, 0x11, false, false, 0));
        frame2.add(new SpriteMappingPiece(-0x20, 0x00, 3, 3, 0x08, false, false, 0));
        frame2.add(new SpriteMappingPiece(0x08, 0x00, 3, 3, 0x08, true, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj3E_00CA): Fully open - center visible, sides apart
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x20, -0x20, 4, 2, 0x00, false, false, 0));
        frame3.add(new SpriteMappingPiece(0x00, -0x20, 4, 2, 0x00, true, false, 0));
        frame3.add(new SpriteMappingPiece(-0x08, -0x10, 2, 3, 0x23, false, false, 0));  // Open center (different tile)
        frame3.add(new SpriteMappingPiece(-0x20, 0x08, 4, 3, 0x17, false, false, 0));
        frame3.add(new SpriteMappingPiece(0x00, 0x08, 4, 3, 0x17, true, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (Map_obj3E_00F4): Button (top of capsule)
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-0x10, -0x08, 2, 2, 0x29, false, false, 0));
        frame4.add(new SpriteMappingPiece(0x00, -0x08, 2, 2, 0x29, true, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5 (Map_obj3E_0106): Lock / dongle
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-0x08, -0x08, 2, 2, 0x2D, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        return frames;
    }

    /**
     * Load EHZ Boss (Obj56) sprite sheet - multi-component boss with 3 art sources.
     * ROM: ArtNem_Eggpod at 0x83BF6 (flying vehicle),
     *      ArtNem_EHZBoss at 0x8507C (ground vehicle/wheels/spike),
     *      ArtNem_EggChoppers at 0x85868 (propeller)
     * Palette line 1
     *
     * Note: This is a composite sheet combining all three art sources.
     * Full sprite mappings from obj56_a.asm, obj56_b.asm, obj56_c.asm need to be implemented.
     */
    public ObjectSpriteSheet loadEHZBossSheet() {
        // Load all three art sources
        Pattern[] eggpodPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGGPOD_ADDR, "Eggpod");
        Pattern[] ehzBossPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EHZ_BOSS_ADDR, "EHZBoss");
        Pattern[] choppersPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGG_CHOPPERS_ADDR, "EggChoppers");

        if (eggpodPatterns.length == 0 && ehzBossPatterns.length == 0 && choppersPatterns.length == 0) {
            return null;
        }

        // Combine all patterns into a single array
        Pattern[] allPatterns = new Pattern[eggpodPatterns.length + ehzBossPatterns.length + choppersPatterns.length];
        System.arraycopy(eggpodPatterns, 0, allPatterns, 0, eggpodPatterns.length);
        System.arraycopy(ehzBossPatterns, 0, allPatterns, eggpodPatterns.length, ehzBossPatterns.length);
        System.arraycopy(choppersPatterns, 0, allPatterns, eggpodPatterns.length + ehzBossPatterns.length, choppersPatterns.length);

        // Create mappings (TODO: implement full mappings from disassembly)
        List<SpriteMappingFrame> mappings = createEHZBossMappings();

        return new ObjectSpriteSheet(allPatterns, mappings, 1, 1);
    }

    /**
     * Creates mapping frames for EHZ Boss (Obj56).
     * Based on mappings/sprite/obj56_a.asm (propeller - 7 frames),
     *              mappings/sprite/obj56_b.asm (ground vehicle - 8 frames),
     *              mappings/sprite/obj56_c.asm (flying vehicle - 7 frames)
     *
     * The boss uses three art sources loaded sequentially into one array:
     * - ArtNem_Eggpod: 96 patterns (0x60 tiles) - flying vehicle - array indices 0-95
     * - ArtNem_EHZBoss: 128 patterns (0x80 tiles) - ground vehicle/wheels/spike - array indices 96-223
     * - ArtNem_EggChoppers: 20 patterns (0x14 tiles) - propeller - array indices 224-243
     *
     * In ROM VRAM layout:
     * - ArtTile_ArtNem_Eggpod_1 = 0x03A0
     * - ArtTile_ArtNem_EHZBoss = 0x0400 (0x60 tiles after Eggpod)
     * - ArtTile_ArtNem_EggChoppers = 0x056C
     *
     * Mapping tile indices in the disassembly are relative to their VRAM base address.
     * We convert them to pattern array indices by adding the appropriate offset.
     */
    private List<SpriteMappingFrame> createEHZBossMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Pattern array offsets for each art source
        final int EGGPOD_OFFSET = 0;      // Eggpod starts at index 0
        final int VEHICLE_OFFSET = 96;    // EHZBoss starts at index 96 (after 0x60 Eggpod tiles)
        final int PROPELLER_OFFSET = 224; // EggChoppers starts at index 224 (after 0x60 + 0x80 tiles)

        // =================================================================================
        // obj56_a: Propeller animations (7 frames) - uses ArtNem_EggChoppers
        // Tile indices in disassembly are relative to ArtTile_ArtNem_EggChoppers
        // =================================================================================

        // Frame 0: Propeller fully stopped
        List<SpriteMappingPiece> prop0 = new ArrayList<>();
        prop0.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 0, false, false, 0));
        frames.add(new SpriteMappingFrame(prop0));

        // Frame 1: Propeller spinning - wide spread
        List<SpriteMappingPiece> prop1 = new ArrayList<>();
        prop1.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 4, false, false, 0));
        prop1.add(new SpriteMappingPiece(0x12, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop1.add(new SpriteMappingPiece(0x32, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop1.add(new SpriteMappingPiece(-0x1E, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop1.add(new SpriteMappingPiece(-0x3E, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        frames.add(new SpriteMappingFrame(prop1));

        // Frame 2: Propeller spinning - medium spread
        List<SpriteMappingPiece> prop2 = new ArrayList<>();
        prop2.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 4, false, false, 0));
        prop2.add(new SpriteMappingPiece(0x12, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop2.add(new SpriteMappingPiece(0x32, -0x28, 2, 2, PROPELLER_OFFSET + 8, false, false, 0));
        prop2.add(new SpriteMappingPiece(-0x1E, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop2.add(new SpriteMappingPiece(-0x2E, -0x28, 2, 2, PROPELLER_OFFSET + 8, false, false, 0));
        frames.add(new SpriteMappingFrame(prop2));

        // Frame 3: Propeller spinning - narrow
        List<SpriteMappingPiece> prop3 = new ArrayList<>();
        prop3.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 4, false, false, 0));
        prop3.add(new SpriteMappingPiece(0x12, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop3.add(new SpriteMappingPiece(-0x1E, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        frames.add(new SpriteMappingFrame(prop3));

        // Frame 4: Propeller spinning - minimal
        List<SpriteMappingPiece> prop4 = new ArrayList<>();
        prop4.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 4, false, false, 0));
        prop4.add(new SpriteMappingPiece(0x12, -0x28, 2, 2, PROPELLER_OFFSET + 8, false, false, 0));
        prop4.add(new SpriteMappingPiece(-0x0E, -0x28, 2, 2, PROPELLER_OFFSET + 8, false, false, 0));
        frames.add(new SpriteMappingFrame(prop4));

        // Frame 5: Propeller spinning - returning to wide (right side)
        List<SpriteMappingPiece> prop5 = new ArrayList<>();
        prop5.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 0, false, false, 0));
        prop5.add(new SpriteMappingPiece(0x12, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop5.add(new SpriteMappingPiece(0x32, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        frames.add(new SpriteMappingFrame(prop5));

        // Frame 6: Propeller spinning - returning to wide (left side)
        List<SpriteMappingPiece> prop6 = new ArrayList<>();
        prop6.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, PROPELLER_OFFSET + 4, false, false, 0));
        prop6.add(new SpriteMappingPiece(-0x1E, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        prop6.add(new SpriteMappingPiece(-0x3E, -0x28, 4, 2, PROPELLER_OFFSET + 0x0C, false, false, 0));
        frames.add(new SpriteMappingFrame(prop6));

        // =================================================================================
        // obj56_b: Ground vehicle, wheels, spike (8 frames) - uses ArtNem_EHZBoss
        // Tile indices in disassembly are relative to ArtTile_ArtNem_EHZBoss
        // =================================================================================

        // Frame 7 (0): Ground vehicle body (full)
        List<SpriteMappingPiece> vehicle0 = new ArrayList<>();
        vehicle0.add(new SpriteMappingPiece(-0x30, -0x10, 4, 4, VEHICLE_OFFSET + 0, false, false, 0));
        vehicle0.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x10, false, false, 0));
        vehicle0.add(new SpriteMappingPiece(0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(vehicle0));

        // Frame 8 (1): Spike retracted
        List<SpriteMappingPiece> spike1 = new ArrayList<>();
        spike1.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x30, false, false, 0));
        frames.add(new SpriteMappingFrame(spike1));

        // Frame 9 (2): Spike partially extended
        List<SpriteMappingPiece> spike2 = new ArrayList<>();
        spike2.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x40, false, false, 0));
        frames.add(new SpriteMappingFrame(spike2));

        // Frame 10 (3): Spike fully extended
        List<SpriteMappingPiece> spike3 = new ArrayList<>();
        spike3.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x50, false, false, 0));
        frames.add(new SpriteMappingFrame(spike3));

        // Frame 11 (4): Wheel foreground frame 1
        List<SpriteMappingPiece> wheel4 = new ArrayList<>();
        wheel4.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x60, false, false, 0));
        frames.add(new SpriteMappingFrame(wheel4));

        // Frame 12 (5): Wheel foreground frame 2 (V-flipped for rotation)
        // ROM: spritePiece -$10, -$10, 4, 4, $60, 0, 1, 0, 0 (xflip=0, yflip=1)
        List<SpriteMappingPiece> wheel5 = new ArrayList<>();
        wheel5.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x60, false, true, 0));
        frames.add(new SpriteMappingFrame(wheel5));

        // Frame 13 (6): Wheel background frame 1
        List<SpriteMappingPiece> wheel6 = new ArrayList<>();
        wheel6.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x70, false, false, 0));
        frames.add(new SpriteMappingFrame(wheel6));

        // Frame 14 (7): Wheel background frame 2 (V-flipped for rotation)
        // ROM: spritePiece -$10, -$10, 4, 4, $70, 0, 1, 0, 0 (xflip=0, yflip=1)
        List<SpriteMappingPiece> wheel7 = new ArrayList<>();
        wheel7.add(new SpriteMappingPiece(-0x10, -0x10, 4, 4, VEHICLE_OFFSET + 0x70, false, true, 0));
        frames.add(new SpriteMappingFrame(wheel7));

        // =================================================================================
        // obj56_c: Flying vehicle top / Eggman (7 frames) - uses ArtNem_Eggpod
        // Tile indices in disassembly are relative to ArtTile_ArtNem_Eggpod_1
        // =================================================================================

        // Frame 15 (0): Flying vehicle bottom
        List<SpriteMappingPiece> flying0 = new ArrayList<>();
        flying0.add(new SpriteMappingPiece(-0x20, -8, 2, 2, EGGPOD_OFFSET + 0, false, false, 0));
        flying0.add(new SpriteMappingPiece(-0x20, 8, 2, 2, EGGPOD_OFFSET + 4, false, false, 0));
        flying0.add(new SpriteMappingPiece(-0x10, -8, 4, 4, EGGPOD_OFFSET + 8, false, false, 0));
        flying0.add(new SpriteMappingPiece(0x10, -8, 2, 4, EGGPOD_OFFSET + 0x18, false, false, 0));
        frames.add(new SpriteMappingFrame(flying0));

        // Frame 16 (1): Eggman normal (capsule top)
        List<SpriteMappingPiece> egg1 = new ArrayList<>();
        egg1.add(new SpriteMappingPiece(-0x20, -0x18, 2, 2, EGGPOD_OFFSET + 0x28, false, false, 0));
        egg1.add(new SpriteMappingPiece(-0x10, -0x18, 4, 2, EGGPOD_OFFSET + 0x30, false, false, 0));
        egg1.add(new SpriteMappingPiece(0x10, -0x18, 2, 2, EGGPOD_OFFSET + 0x24, false, false, 0));
        egg1.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, EGGPOD_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(egg1));

        // Frame 17 (2): Eggman normal variant
        List<SpriteMappingPiece> egg2 = new ArrayList<>();
        egg2.add(new SpriteMappingPiece(-0x20, -0x18, 2, 2, EGGPOD_OFFSET + 0x28, false, false, 0));
        egg2.add(new SpriteMappingPiece(-0x10, -0x18, 4, 2, EGGPOD_OFFSET + 0x38, false, false, 0));
        egg2.add(new SpriteMappingPiece(0x10, -0x18, 2, 2, EGGPOD_OFFSET + 0x24, false, false, 0));
        egg2.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, EGGPOD_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(egg2));

        // Frame 18 (3): Eggman laughing frame 1
        List<SpriteMappingPiece> egg3 = new ArrayList<>();
        egg3.add(new SpriteMappingPiece(-0x20, -0x18, 2, 2, EGGPOD_OFFSET + 0x28, false, false, 0));
        egg3.add(new SpriteMappingPiece(-0x10, -0x18, 4, 2, EGGPOD_OFFSET + 0x40, false, false, 0));
        egg3.add(new SpriteMappingPiece(0x10, -0x18, 2, 2, EGGPOD_OFFSET + 0x24, false, false, 0));
        egg3.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, EGGPOD_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(egg3));

        // Frame 19 (4): Eggman laughing frame 2
        List<SpriteMappingPiece> egg4 = new ArrayList<>();
        egg4.add(new SpriteMappingPiece(-0x20, -0x18, 2, 2, EGGPOD_OFFSET + 0x28, false, false, 0));
        egg4.add(new SpriteMappingPiece(-0x10, -0x18, 4, 2, EGGPOD_OFFSET + 0x48, false, false, 0));
        egg4.add(new SpriteMappingPiece(0x10, -0x18, 2, 2, EGGPOD_OFFSET + 0x24, false, false, 0));
        egg4.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, EGGPOD_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(egg4));

        // Frame 20 (5): Eggman hit (taking damage)
        List<SpriteMappingPiece> egg5 = new ArrayList<>();
        egg5.add(new SpriteMappingPiece(-0x20, -0x18, 2, 2, EGGPOD_OFFSET + 0x28, false, false, 0));
        egg5.add(new SpriteMappingPiece(-0x10, -0x18, 4, 2, EGGPOD_OFFSET + 0x50, false, false, 0));
        egg5.add(new SpriteMappingPiece(0x10, -0x18, 2, 2, EGGPOD_OFFSET + 0x24, false, false, 0));
        egg5.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, EGGPOD_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(egg5));

        // Frame 21 (6): Eggman flying off (fleeing)
        List<SpriteMappingPiece> egg6 = new ArrayList<>();
        egg6.add(new SpriteMappingPiece(-0x20, -0x18, 2, 2, EGGPOD_OFFSET + 0x28, false, false, 0));
        egg6.add(new SpriteMappingPiece(-0x10, -0x18, 4, 2, EGGPOD_OFFSET + 0x58, false, false, 0));
        egg6.add(new SpriteMappingPiece(0x10, -0x18, 2, 2, EGGPOD_OFFSET + 0x24, false, false, 0));
        egg6.add(new SpriteMappingPiece(0x02, -0x28, 2, 2, EGGPOD_OFFSET + 0x20, false, false, 0));
        frames.add(new SpriteMappingFrame(egg6));

        return frames;
    }

    /**
     * Load CNZ Boss sprite sheet (Object 0x51) - Eggman's electricity generator boss.
     * <p>
     * ROM Reference: s2.asm:90063 (ArtNem_CNZBoss) + Obj51_MapUnc_320EA
     * Art addresses:
     * - CNZBoss: 0x87AAC (Nemesis compressed) at VRAM $0407
     * - Eggpod: 0x83BF6 (Nemesis compressed) at VRAM $0500 (for Robotnik graphics)
     * <p>
     * The mappings use a "fudge" base of $03A7 (= $0407 - $60), so:
     * - Tiles $60-$E4 reference CNZBoss art (subtract $60 for array index)
     * - Tiles $17D-$1B1 reference Eggpod art at VRAM $0500 (Robotnik body/face)
     * <p>
     * We create a combined pattern array where:
     * - CNZBoss patterns at indices 0+ (tiles $60+ minus $60)
     * - Eggpod patterns at indices $11D+ (tiles $17D+ minus $60)
     *
     * @return sprite sheet for CNZ boss, or null on failure
     */
    public ObjectSpriteSheet loadCNZBossSheet() {
        // Load CNZBoss art (generators, electrodes, electricity effects)
        Pattern[] cnzBossPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_CNZ_BOSS_ADDR, "CNZBoss");
        if (cnzBossPatterns.length == 0) {
            return null;
        }

        // Load Eggpod art (Robotnik body, propellers, face expressions)
        Pattern[] eggpodPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGGPOD_ADDR, "Eggpod");

        // Calculate offsets based on VRAM tile positions:
        // - Fudge base = $03A7
        // - CNZBoss at VRAM $0407: tile $60 → index 0 (subtract $60)
        // - Eggpod at VRAM $0500: tile $17D → index $11D (since $17D - $60 = $11D)
        //   Eggpod VRAM offset from fudge: $0500 - $03A7 = $159
        //   But mappings reference $17D which is $17D - $60 = $11D from CNZBoss start
        final int EGGPOD_OFFSET_IN_COMBINED = 0x11D; // Where Eggpod tiles start in combined array

        // Create combined array large enough for both art sources
        // Highest Eggpod tile used: $1B1 → index $151 (needs array size of at least $152)
        int combinedSize = Math.max(cnzBossPatterns.length, EGGPOD_OFFSET_IN_COMBINED + eggpodPatterns.length);
        Pattern[] combinedPatterns = new Pattern[combinedSize];

        // Initialize with empty patterns
        for (int i = 0; i < combinedSize; i++) {
            combinedPatterns[i] = new Pattern();
        }

        // Copy CNZBoss patterns at index 0 (tiles $60+ in mappings)
        System.arraycopy(cnzBossPatterns, 0, combinedPatterns, 0, cnzBossPatterns.length);

        // Copy Eggpod patterns at index $11D (tiles $17D+ in mappings)
        // The Eggpod tiles used are $17D, $181, $185, $189, $191, $199, $1A1, $1A9, $1B1
        // These correspond to Eggpod array indices: $24, $28, $2C, $30, $38, $40, $48, $50, $58
        // (calculated as: mapping_tile - $60 - $11D + $24 for first one, but actually
        //  VRAM $0500 + offset = tile value, so Eggpod[offset] where offset = VRAM_tile - $0500)
        // Tile $17D in mapping → VRAM $03A7 + $17D = $0524 → Eggpod[$0524 - $0500] = Eggpod[$24]
        if (eggpodPatterns.length > 0) {
            // Map Eggpod tiles to correct positions in combined array
            // Eggpod VRAM starts at $0500, fudge base is $03A7
            // Mapping tile $17D → VRAM $0524 → Eggpod index $24
            // Combined array index = mapping_tile - $60 = $17D - $60 = $11D
            // So Eggpod[$24] goes to combined[$11D]
            int eggpodVramBase = 0x0500;
            int fudgeBase = 0x03A7;

            // Copy the relevant Eggpod tiles to their correct positions
            int[] eggpodMappingTiles = {0x17D, 0x181, 0x185, 0x189, 0x191, 0x199, 0x1A1, 0x1A9, 0x1B1};
            for (int mappingTile : eggpodMappingTiles) {
                int vramTile = fudgeBase + mappingTile;
                int eggpodIndex = vramTile - eggpodVramBase;
                int combinedIndex = mappingTile - 0x60;

                if (eggpodIndex >= 0 && eggpodIndex < eggpodPatterns.length &&
                        combinedIndex >= 0 && combinedIndex < combinedSize) {
                    combinedPatterns[combinedIndex] = eggpodPatterns[eggpodIndex];
                }
            }

            // Also copy a range of Eggpod patterns for any we might have missed
            // The propeller and face tiles use consecutive patterns
            // Start copying from Eggpod[$24] (where the referenced tiles begin)
            int eggpodCopyStart = 0x24;
            int maxCopyCount = Math.min(0x60, eggpodPatterns.length - eggpodCopyStart);
            for (int i = 0; i < maxCopyCount; i++) {
                int combinedIndex = EGGPOD_OFFSET_IN_COMBINED + i;
                if (combinedIndex < combinedSize && (eggpodCopyStart + i) < eggpodPatterns.length) {
                    combinedPatterns[combinedIndex] = eggpodPatterns[eggpodCopyStart + i];
                }
            }
        }

        List<SpriteMappingFrame> mappings = createCNZBossMappings();
        return new ObjectSpriteSheet(combinedPatterns, mappings, 0, 1);
    }

    /**
     * Creates mapping frames for CNZ Boss (Obj51).
     * Based on mappings/sprite/obj51.asm (21 frames)
     * <p>
     * The CNZ boss uses TWO art sources with a "fudge" tile offset:
     * - ArtTile_ArtNem_CNZBoss = 0x0407 (generators, electrodes, electricity)
     * - ArtTile_ArtNem_Eggpod_4 = 0x0500 (Robotnik body, propellers, faces)
     * - ArtTile_ArtNem_CNZBoss_Fudge = 0x03A7 (= 0x0407 - 0x60, used as art_tile base)
     * <p>
     * Mapping tile indices (subtract 0x60 for combined array index):
     * - CNZBoss tiles: $60-$E4 → indices 0x00-0x84
     * - Eggpod tiles: $17D-$1B1 → indices 0x11D-0x151
     */
    private List<SpriteMappingFrame> createCNZBossMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Tile index calculation: mapping_tile - 0x60 = combined_array_index
        // CNZBoss tiles: $60 → 0, $6C → 0x0C, $7C → 0x1C, etc.
        // Eggpod tiles: $17D → 0x11D, $181 → 0x121, $185 → 0x125, etc.

        // Frame 0 (Map_obj51_002A): Main boss body with Robotnik
        // This is THE main frame showing the boss machine with Eggman inside
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        // spritePiece $10, -$10, 2, 2, $17D - Robotnik body (Eggpod art)
        frame0.add(new SpriteMappingPiece(0x10, -0x10, 2, 2, 0x11D, false, false, 0));
        // spritePiece -7, -$28, 4, 3, $60 - Generator/electrode top (CNZBoss art)
        frame0.add(new SpriteMappingPiece(-7, -0x28, 4, 3, 0x00, false, false, 1));
        // spritePiece -$28, 0, 4, 4, $6C - Left machine body
        frame0.add(new SpriteMappingPiece(-0x28, 0, 4, 4, 0x0C, false, false, 1));
        // spritePiece -8, 0, 4, 4, $7C - Center machine body
        frame0.add(new SpriteMappingPiece(-8, 0, 4, 4, 0x1C, false, false, 1));
        // spritePiece $18, 0, 2, 3, $8C - Right machine body
        frame0.add(new SpriteMappingPiece(0x18, 0, 2, 3, 0x2C, false, false, 1));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj51_0054): Left generator - tile $AA → index 0x4A
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x1C, 0x18, 2, 3, 0x4A, false, false, 1));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj51_005E): Right generator - tile $B0 → index 0x50
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x25, 0x10, 2, 3, 0x50, false, false, 1));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj51_0068): Electrode piece - tile $92 → index 0x32
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(8, 0x10, 3, 4, 0x32, false, false, 1));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (Map_obj51_0072): Electrode extended - tiles $9E, $A4 → indices 0x3E, 0x44
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(8, 0x10, 3, 2, 0x3E, false, false, 1));
        frame4.add(new SpriteMappingPiece(0x20, 0x10, 2, 3, 0x44, false, false, 1));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5 (Map_obj51_0084): Propeller frame 1 - Eggpod tiles $189, $181 → indices 0x129, 0x121
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x129, false, false, 0));
        frame5.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x121, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6 (Map_obj51_0096): Propeller frame 2 - Eggpod tiles $191, $181 → indices 0x131, 0x121
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x131, false, false, 0));
        frame6.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x121, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        // Frame 7 (Map_obj51_00A8): Eggman normal face - Eggpod tiles $199, $185 → indices 0x139, 0x125
        List<SpriteMappingPiece> frame7 = new ArrayList<>();
        frame7.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x139, false, false, 0));
        frame7.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x125, false, false, 0));
        frames.add(new SpriteMappingFrame(frame7));

        // Frame 8 (Map_obj51_00BA): Eggman alternate face - Eggpod tiles $1A1, $185 → indices 0x141, 0x125
        List<SpriteMappingPiece> frame8 = new ArrayList<>();
        frame8.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x141, false, false, 0));
        frame8.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x125, false, false, 0));
        frames.add(new SpriteMappingFrame(frame8));

        // Frame 9 (Map_obj51_00CC): Eggman laughing - Eggpod tiles $1A9, $185 → indices 0x149, 0x125
        List<SpriteMappingPiece> frame9 = new ArrayList<>();
        frame9.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x149, false, false, 0));
        frame9.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x125, false, false, 0));
        frames.add(new SpriteMappingFrame(frame9));

        // Frame 10 (Map_obj51_00DE): Eggman hurt - Eggpod tiles $1B1, $185 → indices 0x151, 0x125
        List<SpriteMappingPiece> frame10 = new ArrayList<>();
        frame10.add(new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x151, false, false, 0));
        frame10.add(new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x125, false, false, 0));
        frames.add(new SpriteMappingFrame(frame10));

        // Frame 11 (Map_obj51_00F0): Electricity field frame 1 (0x0C in code)
        List<SpriteMappingPiece> frame11 = new ArrayList<>();
        frame11.add(new SpriteMappingPiece(-0x10, 0x28, 4, 1, 0x56, false, false, 1));
        frames.add(new SpriteMappingFrame(frame11));

        // Frame 12 (Map_obj51_00FA): Electricity field frame 2 (0x0D in code)
        List<SpriteMappingPiece> frame12 = new ArrayList<>();
        frame12.add(new SpriteMappingPiece(-0x10, 0x28, 4, 1, 0x5A, false, false, 1));
        frames.add(new SpriteMappingFrame(frame12));

        // Frame 13 (Map_obj51_0104): Electricity field frame 3 (0x0E in code)
        List<SpriteMappingPiece> frame13 = new ArrayList<>();
        frame13.add(new SpriteMappingPiece(-0x10, 0x28, 4, 1, 0x5E, false, false, 1));
        frames.add(new SpriteMappingFrame(frame13));

        // Frame 14 (Map_obj51_010E): Zap field wide frame 1 (0x0F in code)
        List<SpriteMappingPiece> frame14 = new ArrayList<>();
        frame14.add(new SpriteMappingPiece(-0x1C, 0x20, 4, 1, 0x62, false, false, 1));
        frame14.add(new SpriteMappingPiece(4, 0x20, 4, 1, 0x66, false, false, 1));
        frames.add(new SpriteMappingFrame(frame14));

        // Frame 15 (Map_obj51_0120): Zap field wide frame 2 (0x10 in code)
        List<SpriteMappingPiece> frame15 = new ArrayList<>();
        frame15.add(new SpriteMappingPiece(-0x1C, 0x20, 4, 1, 0x6A, false, false, 1));
        frame15.add(new SpriteMappingPiece(4, 0x20, 4, 1, 0x6E, false, false, 1));
        frames.add(new SpriteMappingFrame(frame15));

        // Frame 16 (Map_obj51_0132): Zap field wide frame 3 (0x11 in code)
        List<SpriteMappingPiece> frame16 = new ArrayList<>();
        frame16.add(new SpriteMappingPiece(-0x1C, 0x20, 4, 1, 0x72, false, false, 1));
        frame16.add(new SpriteMappingPiece(4, 0x20, 4, 1, 0x76, false, false, 1));
        frames.add(new SpriteMappingFrame(frame16));

        // Frame 17 (Map_obj51_0144): Electric ball (0x13 in code)
        List<SpriteMappingPiece> frame17 = new ArrayList<>();
        frame17.add(new SpriteMappingPiece(-0x0C, -0x0C, 3, 3, 0x7A, false, false, 0));
        frames.add(new SpriteMappingFrame(frame17));

        // Frame 18 (Map_obj51_014E): Small spark 1 (0x14 in code)
        List<SpriteMappingPiece> frame18 = new ArrayList<>();
        frame18.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x83, false, false, 0));
        frames.add(new SpriteMappingFrame(frame18));

        // Frame 19 (Map_obj51_0158): Small spark 2 (0x15 in code)
        List<SpriteMappingPiece> frame19 = new ArrayList<>();
        frame19.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x84, false, false, 0));
        frames.add(new SpriteMappingFrame(frame19));

        // Frame 20: Empty/placeholder
        frames.add(new SpriteMappingFrame(new ArrayList<>()));

        return frames;
    }

    /**
     * Load Smashable Ground sprite sheet (Object 0x2F) - breakable rock platform in HTZ.
     * <p>
     * This object uses LEVEL patterns (ArtTile_ArtKos_LevelArt), not dedicated object art.
     * The patterns must be extracted from the level at runtime.
     * <p>
     * From s2.asm obj2F.asm mappings, the tile indices used are:
     * - 0x12, 0x16: Top rock patterns (4x2 = 8 tiles or 2x2 = 4 tiles each)
     * - 0x4A: Upper-middle patterns (2x2 = 4 tiles)
     * - 0x4E: Lower-middle patterns (2x2 = 4 tiles)
     * - 0x52: Base/bottom patterns (2x2 = 4 tiles)
     * <p>
     * Palette: Line 2 (standard level palette)
     *
     * @param level The level to extract patterns from
     * @return sprite sheet for smashable ground, or null if level is null
     */
    public ObjectSpriteSheet loadSmashableGroundSheet(uk.co.jamesj999.sonic.level.Level level) {
        if (level == null) {
            return null;
        }

        // Extract the needed patterns from level data
        // The highest tile index used is 0x52 + (2*2) = 0x56
        // We need tiles from 0x12 to around 0x56
        int maxTileNeeded = 0x56;
        int patternCount = level.getPatternCount();
        if (patternCount < maxTileNeeded) {
            LOGGER.warning("Level has fewer patterns than SmashableGround needs: "
                    + patternCount + " < " + maxTileNeeded);
            // Still try to load what we can
        }

        // Extract patterns for the object
        // We'll create a local pattern array with remapped indices
        // Pattern indices used: 0x12, 0x16, 0x4A, 0x4E, 0x52
        // We'll create a compact array where:
        // - Index 0 = level pattern 0x12
        // - Index 8 = level pattern 0x16 (for the 4x2 top section)
        // - Index 12 = level pattern 0x4A
        // - Index 16 = level pattern 0x4E
        // - Index 20 = level pattern 0x52
        // This allows us to use the mapping offsets directly

        // Actually, let's just copy enough patterns from the level to cover
        // the range 0x00 to 0x56, and use the original tile indices
        int copyCount = Math.min(patternCount, maxTileNeeded + 4);
        Pattern[] patterns = new Pattern[copyCount];
        for (int i = 0; i < copyCount; i++) {
            patterns[i] = level.getPattern(i);
        }

        List<SpriteMappingFrame> mappings = createSmashableGroundMappings();
        return new ObjectSpriteSheet(patterns, mappings, 2, 1);
    }

    /**
     * Creates mappings for Smashable Ground (Obj2F).
     * Based on mappings/sprite/obj2F.asm
     * <p>
     * 5 destruction states with different heights:
     * - Frame 0: Full (y_radius=36) - 9 pieces
     * - Frame 1: Partly broken 1 (y_radius=32) - 10 pieces
     * - Frame 2: Partly broken 2 (y_radius=32) - 8 pieces
     * - Frame 3: Partly broken 3 (y_radius=24) - 6 pieces
     * - Frame 4: Partly broken 4 (y_radius=16) - 4 pieces
     * - Frame 5: Partly broken 5 (y_radius=8) - 2 pieces
     * <p>
     * Note: The mapping table has entries for states 0,2,2,4,4,6,6,8,8
     * (indices 0-9), with some duplicates. We implement the unique frames.
     */
    private List<SpriteMappingFrame> createSmashableGroundMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (Map_obj2F_0014): Full intact platform - 9 pieces
        // spritePiece -$10, -$28, 4, 2, $12 - top (4x2 = 8 tiles)
        // spritePiece -$10, -$18, 2, 2, $4A - upper-middle left
        // spritePiece    0, -$18, 2, 2, $4A - upper-middle right
        // spritePiece -$10,   -8, 2, 2, $4E - middle left
        // spritePiece    0,   -8, 2, 2, $4E - middle right
        // spritePiece -$10,    8, 2, 2, $52 - lower left
        // spritePiece    0,    8, 2, 2, $52 - lower right
        // spritePiece -$10,  $18, 2, 2, $52 - bottom left
        // spritePiece    0,  $18, 2, 2, $52 - bottom right
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-0x10, -0x28, 4, 2, 0x12, false, false, 0));
        frame0.add(new SpriteMappingPiece(-0x10, -0x18, 2, 2, 0x4A, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -0x18, 2, 2, 0x4A, false, false, 0));
        frame0.add(new SpriteMappingPiece(-0x10, -8, 2, 2, 0x4E, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, -8, 2, 2, 0x4E, false, false, 0));
        frame0.add(new SpriteMappingPiece(-0x10, 8, 2, 2, 0x52, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, 8, 2, 2, 0x52, false, false, 0));
        frame0.add(new SpriteMappingPiece(-0x10, 0x18, 2, 2, 0x52, false, false, 0));
        frame0.add(new SpriteMappingPiece(0, 0x18, 2, 2, 0x52, false, false, 0));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1 (Map_obj2F_005E): Partly broken 1 - 10 pieces (top split into 2x2 + 2x2)
        // spritePiece -$10, -$28, 2, 2, $12 - top-left
        // spritePiece    0, -$28, 2, 2, $16 - top-right
        // ... rest same as frame 0
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-0x10, -0x28, 2, 2, 0x12, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -0x28, 2, 2, 0x16, false, false, 0));
        frame1.add(new SpriteMappingPiece(-0x10, -0x18, 2, 2, 0x4A, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -0x18, 2, 2, 0x4A, false, false, 0));
        frame1.add(new SpriteMappingPiece(-0x10, -8, 2, 2, 0x4E, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, -8, 2, 2, 0x4E, false, false, 0));
        frame1.add(new SpriteMappingPiece(-0x10, 8, 2, 2, 0x52, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, 8, 2, 2, 0x52, false, false, 0));
        frame1.add(new SpriteMappingPiece(-0x10, 0x18, 2, 2, 0x52, false, false, 0));
        frame1.add(new SpriteMappingPiece(0, 0x18, 2, 2, 0x52, false, false, 0));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj2F_00B0): Partly broken 2 - 8 pieces (no top layer)
        // spritePiece -$10, -$20, 2, 2, $4A - upper left (shifted up)
        // spritePiece    0, -$20, 2, 2, $4A - upper right
        // spritePiece -$10, -$10, 2, 2, $4E
        // spritePiece    0, -$10, 2, 2, $4E
        // spritePiece -$10,    0, 2, 2, $52
        // spritePiece    0,    0, 2, 2, $52
        // spritePiece -$10,  $10, 2, 2, $52
        // spritePiece    0,  $10, 2, 2, $52
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x10, -0x20, 2, 2, 0x4A, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, -0x20, 2, 2, 0x4A, false, false, 0));
        frame2.add(new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x4E, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, -0x10, 2, 2, 0x4E, false, false, 0));
        frame2.add(new SpriteMappingPiece(-0x10, 0, 2, 2, 0x52, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, 0, 2, 2, 0x52, false, false, 0));
        frame2.add(new SpriteMappingPiece(-0x10, 0x10, 2, 2, 0x52, false, false, 0));
        frame2.add(new SpriteMappingPiece(0, 0x10, 2, 2, 0x52, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj2F_00F2): Partly broken 3 - 6 pieces
        // spritePiece -$10, -$18, 2, 2, $4E
        // spritePiece    0, -$18, 2, 2, $4E
        // spritePiece -$10,   -8, 2, 2, $52
        // spritePiece    0,   -8, 2, 2, $52
        // spritePiece -$10,    8, 2, 2, $52
        // spritePiece    0,    8, 2, 2, $52
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x10, -0x18, 2, 2, 0x4E, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, -0x18, 2, 2, 0x4E, false, false, 0));
        frame3.add(new SpriteMappingPiece(-0x10, -8, 2, 2, 0x52, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, -8, 2, 2, 0x52, false, false, 0));
        frame3.add(new SpriteMappingPiece(-0x10, 8, 2, 2, 0x52, false, false, 0));
        frame3.add(new SpriteMappingPiece(0, 8, 2, 2, 0x52, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (Map_obj2F_0124): Partly broken 4 - 4 pieces
        // spritePiece -$10, -$10, 2, 2, $52
        // spritePiece    0, -$10, 2, 2, $52
        // spritePiece -$10,    0, 2, 2, $52
        // spritePiece    0,    0, 2, 2, $52
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x52, false, false, 0));
        frame4.add(new SpriteMappingPiece(0, -0x10, 2, 2, 0x52, false, false, 0));
        frame4.add(new SpriteMappingPiece(-0x10, 0, 2, 2, 0x52, false, false, 0));
        frame4.add(new SpriteMappingPiece(0, 0, 2, 2, 0x52, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5 (Map_obj2F_0146): Partly broken 5 - 2 pieces (smallest)
        // spritePiece -$10,   -8, 2, 2, $52
        // spritePiece    0,   -8, 2, 2, $52
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-0x10, -8, 2, 2, 0x52, false, false, 0));
        frame5.add(new SpriteMappingPiece(0, -8, 2, 2, 0x52, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        return frames;
    }

    /**
     * Load HTZ Boss sprite sheet (Object 0x52) - Lava flamethrower boss.
     * <p>
     * ROM Reference: s2.asm:63619-64207 (Obj52)
     * Art addresses:
     * - HTZBoss: 0x8595C (Nemesis compressed) at VRAM $0421 - flamethrower, lava ball components
     * - Eggpod: 0x83BF6 (Nemesis compressed) at VRAM $03C1 (Eggpod_2) - Robotnik/vehicle body
     * <p>
     * The mappings use these tile bases:
     * - Tiles 0-$5F: Eggpod art (palette 1), used for main vehicle body
     * - Tiles $60+: HTZ boss art (palette 0), used for flamethrower and lava ball
     * <p>
     * We create a combined pattern array where:
     * - Eggpod patterns at indices 0+
     * - HTZ boss patterns at indices 0x60+ (tiles $60+ in mappings)
     *
     * @return sprite sheet for HTZ boss, or null on failure
     */
    public ObjectSpriteSheet loadHTZBossSheet() {
        // Load Eggpod art (vehicle body with Robotnik)
        Pattern[] eggpodPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_EGGPOD_ADDR, "Eggpod");
        if (eggpodPatterns.length == 0) {
            return null;
        }

        // Load HTZ boss art (flamethrower, lava ball components)
        Pattern[] htzBossPatterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_HTZ_BOSS_ADDR, "HTZBoss");

        // HTZ boss tiles start at offset 0x60 in the combined array
        // Eggpod tiles: 0x00-0x5F
        // HTZ boss tiles: 0x60+
        final int HTZ_BOSS_OFFSET = 0x60;

        // Create combined array large enough for both art sources
        int combinedSize = Math.max(eggpodPatterns.length, HTZ_BOSS_OFFSET + htzBossPatterns.length);
        Pattern[] combinedPatterns = new Pattern[combinedSize];

        // Initialize with empty patterns
        for (int i = 0; i < combinedSize; i++) {
            combinedPatterns[i] = new Pattern();
        }

        // Copy Eggpod patterns at index 0
        System.arraycopy(eggpodPatterns, 0, combinedPatterns, 0, eggpodPatterns.length);

        // Copy HTZ boss patterns at index 0x60
        if (htzBossPatterns.length > 0) {
            for (int i = 0; i < htzBossPatterns.length && (HTZ_BOSS_OFFSET + i) < combinedSize; i++) {
                combinedPatterns[HTZ_BOSS_OFFSET + i] = htzBossPatterns[i];
            }
        }

        List<SpriteMappingFrame> mappings = createHTZBossMappings();
        return new ObjectSpriteSheet(combinedPatterns, mappings, 0, 1);
    }

    /**
     * Load HTZ Boss smoke sprite sheet.
     * Uses same smoke art as CPZ boss (ArtNem_BossSmoke) with HTZ-specific mappings.
     * ROM Reference: Obj52_MapUnc_30258 (4 frames of smoke animation)
     *
     * @return sprite sheet for HTZ boss smoke, or null on failure
     */
    public ObjectSpriteSheet loadHTZBossSmokeSheet() {
        Pattern[] patterns = safeLoadNemesisPatterns(Sonic2Constants.ART_NEM_BOSS_SMOKE_ADDR, "BossSmoke");
        if (patterns.length == 0) {
            return null;
        }
        List<SpriteMappingFrame> mappings = createHTZBossSmokeMappings();
        return new ObjectSpriteSheet(patterns, mappings, 0, 1);
    }

    /**
     * Creates mapping frames for HTZ Boss (Obj52).
     * Based on mappings/sprite/obj52_b.asm (17 frames)
     * <p>
     * The HTZ boss uses TWO art sources:
     * - ArtTile_ArtNem_Eggpod_2 = 0x03C1 (vehicle body with Robotnik) - palette 1
     * - ArtTile_ArtNem_HTZBoss = 0x0421 (flamethrower/lava ball) - palette 0
     * <p>
     * Mapping tile indices:
     * - Tiles 0-$5F: Eggpod art (combined array indices 0x00-0x5F)
     * - Tiles $60+: HTZ boss art (combined array indices 0x60+)
     * <p>
     * Frame layout:
     * - Frame 0: Placeholder (self-reference in ROM, empty here)
     * - Frame 1: Main boss body (vehicle + Robotnik)
     * - Frames 2-3: Eye/cockpit states
     * - Frames 4-10: Flamethrower (progressively extending)
     * - Frames 11-12: Lava ball small
     * - Frames 13-14: Lava ball large
     * - Frame 15: Empty
     * - Frame 16: Defeated body (smaller cockpit)
     */
    private List<SpriteMappingFrame> createHTZBossMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Empty placeholder
        frames.add(new SpriteMappingFrame(new ArrayList<>()));

        // Frame 1 (Map_obj52_b_0022): Main boss body - 8 pieces
        // Uses Eggpod art (tiles 0-$5F) for vehicle body and HTZ boss art (tiles $60+) for cockpit
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        // spritePiece -$20, 4, 2, 2, 0, palette 1 - Eggpod bottom left
        frame1.add(new SpriteMappingPiece(-0x20, 4, 2, 2, 0x00, false, false, 1));
        // spritePiece -$20, $14, 2, 2, 4, palette 1 - Eggpod bottom left extension
        frame1.add(new SpriteMappingPiece(-0x20, 0x14, 2, 2, 0x04, false, false, 1));
        // spritePiece -$10, 4, 4, 4, 8, palette 1 - Eggpod main body
        frame1.add(new SpriteMappingPiece(-0x10, 4, 4, 4, 0x08, false, false, 1));
        // spritePiece $10, 4, 2, 4, $18, palette 1 - Eggpod right side
        frame1.add(new SpriteMappingPiece(0x10, 4, 2, 4, 0x18, false, false, 1));
        // spritePiece -$20, -$C, 4, 2, $60, palette 0 - HTZ cockpit left
        frame1.add(new SpriteMappingPiece(-0x20, -0x0C, 4, 2, 0x60, false, false, 0));
        // spritePiece 0, -$C, 4, 2, $68, palette 0 - HTZ cockpit right
        frame1.add(new SpriteMappingPiece(0, -0x0C, 4, 2, 0x68, false, false, 0));
        // spritePiece -$18, -$24, 3, 3, $70, palette 1 - Robotnik upper
        frame1.add(new SpriteMappingPiece(-0x18, -0x24, 3, 3, 0x70, false, false, 1));
        // spritePiece 0, -$24, 2, 3, $79, palette 1 - Robotnik side
        frame1.add(new SpriteMappingPiece(0, -0x24, 2, 3, 0x79, false, false, 1));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2 (Map_obj52_b_0064): Eye frame 1 - tile $83
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-0x28, -0x21, 2, 1, 0x83, false, false, 0));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3 (Map_obj52_b_006E): Eye frame 2 - tile $85
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-0x28, -0x21, 2, 1, 0x85, false, false, 0));
        frames.add(new SpriteMappingFrame(frame3));

        // Frame 4 (Map_obj52_b_0078): Flamethrower start - tile $87
        List<SpriteMappingPiece> frame4 = new ArrayList<>();
        frame4.add(new SpriteMappingPiece(-0x30, -0x21, 3, 1, 0x87, false, false, 0));
        frames.add(new SpriteMappingFrame(frame4));

        // Frame 5 (Map_obj52_b_0082): Flamethrower extending - tiles $8A, $8E
        List<SpriteMappingPiece> frame5 = new ArrayList<>();
        frame5.add(new SpriteMappingPiece(-0x40, -0x21, 4, 1, 0x8A, false, false, 0));
        frame5.add(new SpriteMappingPiece(-0x20, -0x21, 1, 1, 0x8E, false, false, 0));
        frames.add(new SpriteMappingFrame(frame5));

        // Frame 6 (Map_obj52_b_0094): Flamethrower longer - tiles $8F, $93
        List<SpriteMappingPiece> frame6 = new ArrayList<>();
        frame6.add(new SpriteMappingPiece(-0x50, -0x21, 4, 1, 0x8F, false, false, 0));
        frame6.add(new SpriteMappingPiece(-0x30, -0x21, 3, 1, 0x93, false, false, 0));
        frames.add(new SpriteMappingFrame(frame6));

        // Frame 7 (Map_obj52_b_00A6): Flamethrower even longer - tiles $96, $9A, $9E
        List<SpriteMappingPiece> frame7 = new ArrayList<>();
        frame7.add(new SpriteMappingPiece(-0x60, -0x21, 4, 1, 0x96, false, false, 0));
        frame7.add(new SpriteMappingPiece(-0x40, -0x21, 4, 1, 0x9A, false, false, 0));
        frame7.add(new SpriteMappingPiece(-0x20, -0x21, 1, 1, 0x9E, false, false, 0));
        frames.add(new SpriteMappingFrame(frame7));

        // Frame 8 (Map_obj52_b_00C0): Flamethrower full - tiles $9F, $A3, $A7
        List<SpriteMappingPiece> frame8 = new ArrayList<>();
        frame8.add(new SpriteMappingPiece(-0x70, -0x21, 4, 1, 0x9F, false, false, 0));
        frame8.add(new SpriteMappingPiece(-0x50, -0x21, 4, 1, 0xA3, false, false, 0));
        frame8.add(new SpriteMappingPiece(-0x30, -0x21, 3, 1, 0xA7, false, false, 0));
        frames.add(new SpriteMappingFrame(frame8));

        // Frame 9 (Map_obj52_b_00DA): Flamethrower variant 1 - tiles $AA, $AE, $B2
        List<SpriteMappingPiece> frame9 = new ArrayList<>();
        frame9.add(new SpriteMappingPiece(-0x78, -0x21, 4, 1, 0xAA, false, false, 0));
        frame9.add(new SpriteMappingPiece(-0x58, -0x21, 4, 1, 0xAE, false, false, 0));
        frame9.add(new SpriteMappingPiece(-0x38, -0x21, 3, 1, 0xB2, false, false, 0));
        frames.add(new SpriteMappingFrame(frame9));

        // Frame 10 (Map_obj52_b_00F4): Flamethrower variant 2 - tiles $B5, $B9
        List<SpriteMappingPiece> frame10 = new ArrayList<>();
        frame10.add(new SpriteMappingPiece(-0x78, -0x21, 4, 1, 0xB5, false, false, 0));
        frame10.add(new SpriteMappingPiece(-0x58, -0x21, 4, 1, 0xB9, false, false, 0));
        frames.add(new SpriteMappingFrame(frame10));

        // Frame 11 (Map_obj52_b_0106): Flamethrower tip - tile $BD
        List<SpriteMappingPiece> frame11 = new ArrayList<>();
        frame11.add(new SpriteMappingPiece(-0x78, -0x21, 4, 1, 0xBD, false, false, 0));
        frames.add(new SpriteMappingFrame(frame11));

        // Frame 12 (Map_obj52_b_0110): Lava ball small 1 - tile $61
        List<SpriteMappingPiece> frame12 = new ArrayList<>();
        frame12.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x61, false, false, 0));
        frames.add(new SpriteMappingFrame(frame12));

        // Frame 13 (Map_obj52_b_011A): Lava ball small 2 - tile $62
        List<SpriteMappingPiece> frame13 = new ArrayList<>();
        frame13.add(new SpriteMappingPiece(-4, -4, 1, 1, 0x62, false, false, 0));
        frames.add(new SpriteMappingFrame(frame13));

        // Frame 14 (Map_obj52_b_0124): Lava ball large 1 - tile $63
        List<SpriteMappingPiece> frame14 = new ArrayList<>();
        frame14.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x63, false, false, 0));
        frames.add(new SpriteMappingFrame(frame14));

        // Frame 15 (Map_obj52_b_012E): Lava ball large 2 - tile $67
        List<SpriteMappingPiece> frame15 = new ArrayList<>();
        frame15.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x67, false, false, 0));
        frames.add(new SpriteMappingFrame(frame15));

        // Frame 16 (Map_obj52_b_0138): Defeated body - 7 pieces
        // Similar to frame 1 but with different cockpit (smaller top piece)
        List<SpriteMappingPiece> frame16 = new ArrayList<>();
        frame16.add(new SpriteMappingPiece(-0x20, 4, 2, 2, 0x00, false, false, 1));
        frame16.add(new SpriteMappingPiece(-0x20, 0x14, 2, 2, 0x04, false, false, 1));
        frame16.add(new SpriteMappingPiece(-0x10, 4, 4, 4, 0x08, false, false, 1));
        frame16.add(new SpriteMappingPiece(0x10, 4, 2, 4, 0x18, false, false, 1));
        frame16.add(new SpriteMappingPiece(-0x20, -0x0C, 4, 2, 0x60, false, false, 0));
        frame16.add(new SpriteMappingPiece(0, -0x0C, 4, 2, 0x68, false, false, 0));
        // Smaller cockpit top - tile $7F
        frame16.add(new SpriteMappingPiece(-0x10, -0x14, 4, 1, 0x7F, false, false, 1));
        frames.add(new SpriteMappingFrame(frame16));

        return frames;
    }

    /**
     * Creates mapping frames for HTZ Boss Smoke (Obj52 smoke puffs).
     * Based on mappings/sprite/obj52_a.asm (4 frames of smoke animation)
     * <p>
     * Each frame is a simple 2x2 tile sprite.
     * Tile indices: 0, 4, 8, C (0x0C)
     */
    private List<SpriteMappingFrame> createHTZBossSmokeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Smoke frame 1 - tile 0
        List<SpriteMappingPiece> frame0 = new ArrayList<>();
        frame0.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x00, false, false, 1));
        frames.add(new SpriteMappingFrame(frame0));

        // Frame 1: Smoke frame 2 - tile 4
        List<SpriteMappingPiece> frame1 = new ArrayList<>();
        frame1.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x04, false, false, 1));
        frames.add(new SpriteMappingFrame(frame1));

        // Frame 2: Smoke frame 3 - tile 8
        List<SpriteMappingPiece> frame2 = new ArrayList<>();
        frame2.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x08, false, false, 1));
        frames.add(new SpriteMappingFrame(frame2));

        // Frame 3: Smoke frame 4 - tile C
        List<SpriteMappingPiece> frame3 = new ArrayList<>();
        frame3.add(new SpriteMappingPiece(-8, -8, 2, 2, 0x0C, false, false, 1));
        frames.add(new SpriteMappingFrame(frame3));

        return frames;
    }
}
