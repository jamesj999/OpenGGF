package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AnimalType;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ObjectArtProvider for Sonic 1.
 * Loads HUD art and game object art (lamppost, etc.) from the Sonic 1 ROM.
 */
public class Sonic1ObjectArtProvider implements ObjectArtProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ObjectArtProvider.class.getName());
    private static final int ANIMAL_TILE_BANK_SIZE = 18;
    private static final int RESULTS_SCORE_DIGIT_PAIR_COUNT = 8;
    private static final int RESULTS_SCORE_DIGIT_TILE_COUNT = RESULTS_SCORE_DIGIT_PAIR_COUNT * 2;
    private static final int HUD_TEXT_E_PAIR_INDEX = 22;
    private static final AnimalType[] DEFAULT_ANIMALS = { AnimalType.RABBIT, AnimalType.FLICKY };
    private static final AnimalType[][] ZONE_ANIMALS = {
            { AnimalType.RABBIT, AnimalType.FLICKY },   // GHZ
            { AnimalType.PENGUIN, AnimalType.SEAL },    // LZ
            { AnimalType.SQUIRREL, AnimalType.SEAL },   // MZ
            { AnimalType.PIG, AnimalType.FLICKY },      // SLZ
            { AnimalType.PIG, AnimalType.CHICKEN },     // SYZ
            { AnimalType.RABBIT, AnimalType.CHICKEN }   // SBZ
    };

    private Pattern[] hudDigitPatterns;
    private Pattern[] hudTextPatterns;
    private Pattern[] hudLivesPatterns;
    private Pattern[] hudLivesNumbers;
    private int animalTypeA = AnimalType.RABBIT.ordinal();
    private int animalTypeB = AnimalType.FLICKY.ordinal();
    private boolean loaded = false;

    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, ObjectSpriteSheet> sheets = new HashMap<>();
    private final Map<String, SpriteAnimationSet> animations = new HashMap<>();
    private final List<String> rendererKeys = new ArrayList<>();
    private final List<ObjectSpriteSheet> sheetOrder = new ArrayList<>();
    private final List<PatternSpriteRenderer> rendererOrder = new ArrayList<>();

    @Override
    public void loadArtForZone(int zoneIndex) throws IOException {
        if (loaded) {
            return;
        }

        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            throw new IllegalStateException("ROM not loaded");
        }

        hudDigitPatterns = loadUncompressedPatterns(rom,
                Sonic1Constants.ART_UNC_HUD_NUMBERS_ADDR,
                Sonic1Constants.ART_UNC_HUD_NUMBERS_SIZE, "HUDNumbers");

        hudTextPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_HUD_ADDR, "HUDText");

        hudLivesPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_LIFE_ICON_ADDR, "LifeIcon");

        hudLivesNumbers = loadUncompressedPatterns(rom,
                Sonic1Constants.ART_UNC_LIVES_NUMBERS_ADDR,
                Sonic1Constants.ART_UNC_LIVES_NUMBERS_SIZE, "LivesNumbers");

        // Load lamppost art
        loadLamppostArt(rom);

        // Load signpost art
        loadSignpostArt(rom);

        // Load bridge art (GHZ) - also used by Scenery 0x1C subtype 3
        loadBridgeArt(rom);

        // Load SLZ cannon art (fireball launcher base, used by Scenery 0x1C subtypes 0-2)
        loadSlzCannonArt(rom);

        // Load GHZ edge wall art
        loadGhzEdgeWallArt(rom);

        // Load purple rock art (GHZ)
        loadPurpleRockArt(rom);

        // Load breakable wall art (GHZ/SLZ)
        loadBreakableWallArt(rom, zoneIndex);

        // Load spike art
        loadSpikeArt(rom);

        // Load monitor art
        loadMonitorArt(rom);

        // Load explosion art (used by monitors and badniks)
        loadExplosionArt(rom);

        // Load shield art
        loadShieldArt(rom);

        // Load spring art (all zones)
        loadSpringArt(rom);

        // Load dynamic points popups and escaped animals (zone-dependent)
        loadAnimalAndPointsArt(rom, zoneIndex);

        // Load Buzz Bomber art (GHZ/MZ/SYZ badnik + missile + dissolve)
        loadBuzzBomberArt(rom);

        // Load Crabmeat art (GHZ/SYZ badnik + projectile)
        loadCrabmeatArt(rom);

        // Load Chopper art (GHZ badnik)
        loadChopperArt(rom);

        // Load Motobug art (GHZ badnik + exhaust smoke)
        loadMotobugArt(rom);

        // Load Newtron art (GHZ badnik - two subtypes: walking + missile-firing)
        loadNewtronArt(rom);

        // Load results screen art (reuses title card + HUD text)
        loadResultsScreenArt(rom);

        // Load Giant Ring art (uncompressed, all zones)
        loadGiantRingArt(rom);

        // Load Giant Ring Flash art (Nemesis, loaded with ring)
        loadGiantRingFlashArt(rom);

        // Load swinging platform art (zone-dependent: GHZ/MZ, SLZ, SBZ, GHZ giant ball)
        loadSwingingPlatformArt(rom, zoneIndex);

        // Load prison capsule art (all zones - appears in every boss act)
        loadPrisonArt(rom);

        // Load boss art (GHZ: Eggman, weapons/chain anchor, exhaust flame)
        if (zoneIndex == Sonic1Constants.ZONE_GHZ) {
            loadBossArt(rom);
        }

        loaded = true;
        LOGGER.info("Sonic1ObjectArtProvider loaded: digits=" +
                (hudDigitPatterns != null ? hudDigitPatterns.length : 0) +
                " text=" + (hudTextPatterns != null ? hudTextPatterns.length : 0) +
                " lives=" + (hudLivesPatterns != null ? hudLivesPatterns.length : 0) +
                " livesNums=" + (hudLivesNumbers != null ? hudLivesNumbers.length : 0) +
                " renderers=" + rendererKeys.size());
    }

    /**
     * Loads lamppost art (Nem_Lamp) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Lamppost.asm (Map_Lamp_internal).
     */
    private void loadLamppostArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_LAMPPOST_ADDR, "Lamppost");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load lamppost art");
            return;
        }

        List<SpriteMappingFrame> mappings = createLamppostMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.CHECKPOINT, sheet);
    }

    /**
     * Creates lamppost sprite mappings from S1 disassembly Map_Lamp_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.blue):        Pole with blue ball on top (6 pieces)
     * Frame 1 (.poleonly):    Pole without ball (4 pieces)
     * Frame 2 (.redballonly): Red ball only - used for twirl sparkle (2 pieces)
     * Frame 3 (.red):         Pole with red ball on top (6 pieces)
     */
    private List<SpriteMappingFrame> createLamppostMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .blue - pole + blue ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x1C, 1, 2, 0, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x1C, 1, 2, 0, true,  false, 0, false),
                new SpriteMappingPiece(-8, -0x0C, 1, 4, 2, false, false, 1, false),
                new SpriteMappingPiece( 0, -0x0C, 1, 4, 2, true,  false, 1, false),
                new SpriteMappingPiece(-8, -0x2C, 1, 2, 6, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x2C, 1, 2, 6, true,  false, 0, false)
        )));

        // Frame 1: .poleonly - pole without ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x1C, 1, 2, 0, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x1C, 1, 2, 0, true,  false, 0, false),
                new SpriteMappingPiece(-8, -0x0C, 1, 4, 2, false, false, 1, false),
                new SpriteMappingPiece( 0, -0x0C, 1, 4, 2, true,  false, 1, false)
        )));

        // Frame 2: .redballonly - red ball only (twirl sparkle)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 1, 2, 8, false, false, 0, false),
                new SpriteMappingPiece( 0, -8, 1, 2, 8, true,  false, 0, false)
        )));

        // Frame 3: .red - pole + red ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x1C, 1, 2, 0, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x1C, 1, 2, 0, true,  false, 0, false),
                new SpriteMappingPiece(-8, -0x0C, 1, 4, 2, false, false, 1, false),
                new SpriteMappingPiece( 0, -0x0C, 1, 4, 2, true,  false, 1, false),
                new SpriteMappingPiece(-8, -0x2C, 1, 2, 8, false, false, 0, false),
                new SpriteMappingPiece( 0, -0x2C, 1, 2, 8, true,  false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads signpost art (Nem_Sign) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Signpost.asm (Map_Sign_internal).
     */
    private void loadSignpostArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SIGNPOST_ADDR, "Signpost");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load signpost art");
            return;
        }

        List<SpriteMappingFrame> mappings = createSignpostMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.SIGNPOST, sheet);
    }

    /**
     * Creates signpost sprite mappings from S1 disassembly Map_Sign_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.eggman):  Eggman face sign (3 pieces, left+mirrored right+post)
     * Frame 1 (.spin1):   Spinning frame 1 - wide (2 pieces, sign+post)
     * Frame 2 (.spin2):   Spinning frame 2 - thin/edge-on (2 pieces, sign+post)
     * Frame 3 (.spin3):   Spinning frame 3 - wide mirrored (2 pieces, sign+post)
     * Frame 4 (.sonic):   Sonic face sign (3 pieces, left+right+post)
     */
    private List<SpriteMappingFrame> createSignpostMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .eggman - Eggman sign face (left half + mirrored right half + post)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 4, 0x00, true,  false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, false, false, 0, false)
        )));

        // Frame 1: .spin1 - Spinning wide (4x4 sign + post)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x0C, false, false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, false, false, 0, false)
        )));

        // Frame 2: .spin2 - Spinning thin/edge-on (1x4 sign + post with xflip)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -0x10, 1, 4, 0x1C, false, false, 0, false),
                new SpriteMappingPiece(-4,  0x10, 1, 2, 0x38, true,  false, 0, false)
        )));

        // Frame 3: .spin3 - Spinning wide mirrored (4x4 sign xflipped + post with xflip)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x0C, true,  false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, true,  false, 0, false)
        )));

        // Frame 4: .sonic - Sonic sign face (left half + right half + post)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0x20, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 4, 0x2C, false, false, 0, false),
                new SpriteMappingPiece(   -4,  0x10, 1, 2, 0x38, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads purple rock art (Nem_PplRock) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Purple Rock.asm (Map_PRock_internal).
     */
    private void loadPurpleRockArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_PURPLE_ROCK_ADDR, "PurpleRock");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load purple rock art");
            return;
        }

        List<SpriteMappingFrame> mappings = createPurpleRockMappings();
        // Palette line 3 from disassembly: make_art_tile(ArtTile_GHZ_Purple_Rock,3,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 3, 1);
        registerSheet(ObjectArtKeys.ROCK, sheet);
    }

    /**
     * Creates purple rock sprite mappings from S1 disassembly Map_PRock_internal.
     * <p>
     * Single frame with 2 pieces (left half + right half):
     * <pre>
     * spritePiece -$18, -$10, 3, 4, 0, 0, 0, 0, 0
     * spritePiece    0, -$10, 3, 4, $C, 0, 0, 0, 0
     * </pre>
     */
    private List<SpriteMappingFrame> createPurpleRockMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Full rock (2 pieces, left half + right half)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 4, 0, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 4, 0x0C, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads breakable wall art (Nem_GhzWall1 for GHZ, Nem_SlzWall for SLZ) and creates
     * S1-format sprite mappings. 3 frames (left/middle/right sections) from
     * docs/s1disasm/_maps/Smashable Walls.asm.
     * <p>
     * GHZ uses patterns at offset 0, SLZ loads separate art at +4 offset.
     * Each section is 32x64 pixels (2 columns × 4 rows of 2×2 tile pieces).
     * <p>
     * Palette line 2 from disassembly: make_art_tile(ArtTile_GHZ_SLZ_Smashable_Wall,2,0)
     */
    private void loadBreakableWallArt(Rom rom, int zoneIndex) {
        // GHZ uses Nem_GhzWall1, SLZ uses Nem_SlzWall (loaded at +4 tile offset)
        // Both share the same mapping structure
        int artAddr = (zoneIndex == Sonic1Constants.ZONE_SLZ)
                ? Sonic1Constants.ART_NEM_SLZ_BREAKABLE_WALL_ADDR
                : Sonic1Constants.ART_NEM_GHZ_BREAKABLE_WALL_ADDR;
        String artName = (zoneIndex == Sonic1Constants.ZONE_SLZ) ? "SLZBreakableWall" : "GHZBreakableWall";

        Pattern[] patterns = loadNemesisPatterns(rom, artAddr, artName);
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load breakable wall art");
            return;
        }

        // SLZ art loads at ArtTile+4, so its patterns map to indices 4+ in the sheet.
        // GHZ patterns start at index 0. For SLZ, prepend 4 empty placeholder patterns
        // so the same tile indices from the mappings work correctly.
        if (zoneIndex == Sonic1Constants.ZONE_SLZ) {
            Pattern[] padded = new Pattern[patterns.length + 4];
            for (int i = 0; i < 4; i++) {
                padded[i] = new Pattern(); // empty placeholder
            }
            System.arraycopy(patterns, 0, padded, 4, patterns.length);
            patterns = padded;
        }

        List<SpriteMappingFrame> mappings = createBreakableWallMappings();
        // Palette line 2 from disassembly: make_art_tile(ArtTile_GHZ_SLZ_Smashable_Wall,2,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.BREAKABLE_WALL, sheet);
    }

    /**
     * Creates breakable wall sprite mappings from S1 disassembly Map_Smash_internal.
     * <p>
     * Three frames corresponding to subtypes 0/1/2 (left/middle/right wall sections).
     * Each frame has 8 pieces (2 columns × 4 rows of 2×2 tiles = 32×64 pixels).
     * <p>
     * From _maps/Smashable Walls.asm:
     * <pre>
     * .left:   col0=tile 0, col1=tile 4
     * .middle: col0=tile 4, col1=tile 4
     * .right:  col0=tile 4, col1=tile 8
     * </pre>
     */
    private List<SpriteMappingFrame> createBreakableWallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (.left): left column=tile 0, right column=tile 4
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0x10, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x20, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0,     0, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x10, 2, 2, 4, false, false, 0, false)
        )));

        // Frame 1 (.middle): both columns=tile 4
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0x10, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x20, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0,     0, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x10, 2, 2, 4, false, false, 0, false)
        )));

        // Frame 2 (.right): left column=tile 4, right column=tile 8
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0x10, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x20, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(    0,     0, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x10, 2, 2, 8, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads GHZ edge wall art (Nem_GhzWall2) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/GHZ Edge Walls.asm (Map_Edge_internal).
     * <p>
     * Frame 0 (Shadow): Light top + shadow body (4 pieces, 2x2 each, 16x64 px)
     * Frame 1 (Light): All light (4 identical pieces)
     * Frame 2 (Dark): All shadow (4 identical pieces)
     * <p>
     * Palette line 2 from disassembly: make_art_tile(ArtTile_GHZ_Edge_Wall,2,0)
     */
    private void loadGhzEdgeWallArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_GHZ_EDGE_WALL_ADDR, "GHZEdgeWall");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load GHZ edge wall art");
            return;
        }

        List<SpriteMappingFrame> mappings = createGhzEdgeWallMappings();
        // Palette line 2 from disassembly: make_art_tile(ArtTile_GHZ_Edge_Wall,2,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.GHZ_EDGE_WALL, sheet);
    }

    /**
     * Creates GHZ edge wall sprite mappings from S1 disassembly Map_Edge_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Each frame is 4 pieces stacked vertically (each 2x2 tiles = 16x16 px).
     * Pattern indices: 0 = dark/shadow tiles, 4 = light top tile, 8 = light body tile.
     */
    private List<SpriteMappingFrame> createGhzEdgeWallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Edge_Shadow - light with shadow
        // Top piece uses tile 4 (light), body pieces use tile 8 (shadow)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x20, 2, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(-8, -0x10, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(-8,  0x00, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(-8,  0x10, 2, 2, 8, false, false, 0, false)
        )));

        // Frame 1: M_Edge_Light - light with no shadow (all tile 8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x20, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(-8, -0x10, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(-8,  0x00, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(-8,  0x10, 2, 2, 8, false, false, 0, false)
        )));

        // Frame 2: M_Edge_Dark - all shadow (all tile 0)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x20, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-8, -0x10, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-8,  0x00, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-8,  0x10, 2, 2, 0, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads bridge art (Nem_Bridge) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Bridge.asm (Map_Bri).
     * <p>
     * Frame 0: Single log segment (2x2 tiles, 16x16 px)
     * Frame 1: Bridge stump with rope (2 pieces, used by Scenery 0x1C subtype 3)
     * Frame 2: Rope only (2x1 tiles, 16x8 px)
     */
    private void loadBridgeArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_BRIDGE_ADDR, "Bridge");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load bridge art");
            return;
        }

        List<SpriteMappingFrame> mappings = createBridgeMappings();
        // Palette line 2 from disassembly: make_art_tile(ArtTile_GHZ_Bridge,2,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.BRIDGE, sheet);
    }

    /**
     * Creates bridge sprite mappings from S1 disassembly Map_Bri.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createBridgeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Bri_Log - single log (1 piece, 2x2 tiles at -8,-8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0, false)
        )));

        // Frame 1: M_Bri_Stump - bridge stump (2 pieces)
        //   spritePiece -$10, -8, 2, 1, 4, 0, 0, 0, 0
        //   spritePiece -$10, 0, 4, 1, 6, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -8, 2, 1, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0, 4, 1, 6, false, false, 0, false)
        )));

        // Frame 2: M_Bri_Rope - rope only (1 piece, 2x1 tiles at -8,-4)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 2, 1, 8, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads SLZ fireball launcher / lava thrower art (Nem_SlzCannon) and creates
     * S1-format sprite mappings. Used by Scenery object 0x1C subtypes 0-2.
     * Mappings from docs/s1disasm/_maps/Scenery.asm (Map_Scen).
     * <p>
     * Palette line 2 from disassembly: make_art_tile(ArtTile_SLZ_Fireball_Launcher,2,0).
     * Single frame: 2x4 tiles (16x32 px) at offset (-8, -16).
     */
    private void loadSlzCannonArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SLZ_CANNON_ADDR, "SlzCannon");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load SLZ cannon art");
            return;
        }

        List<SpriteMappingFrame> mappings = createSceneryMappings();
        // Palette line 2 from disassembly: make_art_tile(ArtTile_SLZ_Fireball_Launcher,2,0)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.SCENERY, sheet);
    }

    /**
     * Creates scenery (SLZ fireball launcher) sprite mappings from S1 disassembly Map_Scen.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0: Fireball launcher base (1 piece, 2x4 tiles = 16x32 px)
     *   spritePiece -8, -$10, 2, 4, 0, 0, 0, 0, 0
     */
    private List<SpriteMappingFrame> createSceneryMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: Fireball launcher base (2x4 tiles at -8, -16)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x10, 2, 4, 0, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads spike art (Nem_Spikes) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Spikes.asm (Map_Spike_internal).
     * <p>
     * Palette line 0 from disassembly: make_art_tile(ArtTile_Spikes,0,0).
     * Single sheet covers all 6 frames (upward and sideways variants).
     */
    private void loadSpikeArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SPIKES_ADDR, "Spikes");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load spike art");
            return;
        }

        List<SpriteMappingFrame> mappings = createSpikeMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.SPIKE, sheet);
    }

    /**
     * Creates spike sprite mappings from S1 disassembly Map_Spike_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0: 3 upward spikes (3 pieces, 1x4 tiles each, startTile=4)
     * Frame 1: 3 sideways spikes (3 pieces, 4x1 tiles each, startTile=0)
     * Frame 2: 1 upward spike (1 piece, 1x4 tiles, startTile=4)
     * Frame 3: 3 widely spaced upward spikes (3 pieces, 1x4 tiles each, startTile=4)
     * Frame 4: 6 upward spikes (6 pieces, 1x4 tiles each, startTile=4)
     * Frame 5: 1 sideways spike (1 piece, 4x1 tiles, startTile=0)
     */
    private List<SpriteMappingFrame> createSpikeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: 3 upward spikes
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece(   -4, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x10, 1, 4, 4, false, false, 0, false)
        )));

        // Frame 1: 3 sideways spikes
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x14, 4, 1, 0, false, false, 0, false),
                new SpriteMappingPiece(-0x10,    -4, 4, 1, 0, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0x0C, 4, 1, 0, false, false, 0, false)
        )));

        // Frame 2: 1 upward spike
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -0x10, 1, 4, 4, false, false, 0, false)
        )));

        // Frame 3: 3 widely spaced upward spikes
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x1C, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece(   -4, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece( 0x14, -0x10, 1, 4, 4, false, false, 0, false)
        )));

        // Frame 4: 6 upward spikes
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x40, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x28, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece( 0x38, -0x10, 1, 4, 4, false, false, 0, false)
        )));

        // Frame 5: 1 sideways spike
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -4, 4, 1, 0, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads monitor art (Nem_Monitors) and creates S1-format sprite mappings and animations.
     * Mappings from docs/s1disasm/_maps/Monitor.asm (Map_Monitor_internal).
     * Animations from docs/s1disasm/_anim/Monitor.asm (Ani_Monitor).
     */
    private void loadMonitorArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_MONITOR_ADDR, "Monitor");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load monitor art");
            return;
        }

        List<SpriteMappingFrame> mappings = createMonitorMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.MONITOR, sheet);

        SpriteAnimationSet animSet = createMonitorAnimations();
        animations.put(ObjectArtKeys.ANIM_MONITOR, animSet);
    }

    /**
     * Creates monitor sprite mappings from S1 disassembly Map_Monitor_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * 12 frames: 0-2 (static variants), 3 (eggman), 4 (sonic/life), 5 (shoes),
     * 6 (shield), 7 (invincible), 8 (rings), 9 (S), 10 (goggles), 11 (broken shell)
     */
    private List<SpriteMappingFrame> createMonitorMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .static0 - plain monitor box (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 1: .static1 - monitor with static icon variant 1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x10, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 2: .static2 - monitor with static icon variant 2 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x14, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 3: .eggman - Eggman monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 4: .sonic - Sonic/1-up monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x1C, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 5: .shoes - Speed shoes monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x24, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 6: .shield - Shield monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x28, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 7: .invincible - Invincibility monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x2C, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 8: .rings - 10 rings monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x30, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 9: .s - 'S' monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x34, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 10: .goggles - Goggles monitor (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x0B, 2, 2, 0x20, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x11, 4, 4, 0, false, false, 0, false)
        )));

        // Frame 11: .broken - Broken monitor shell (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -1, 4, 2, 0x38, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Creates monitor animation scripts from S1 disassembly Ani_Monitor.
     * <p>
     * 10 animations (0-9): static, eggman, sonic, shoes, shield,
     * invincible, rings, s, goggles, breaking.
     * <p>
     * Non-static animations cycle: box→icon→icon→box-variant→icon→icon→box-variant→icon→icon
     */
    private SpriteAnimationSet createMonitorAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: .static - dc.b 1, 0, 1, 2, afEnd
        set.addScript(0, new SpriteAnimationScript(1,
                List.of(0, 1, 2), SpriteAnimationEndAction.LOOP, 0));

        // Anim 1: .eggman - dc.b 1, 0, 3, 3, 1, 3, 3, 2, 3, 3, afEnd
        set.addScript(1, new SpriteAnimationScript(1,
                List.of(0, 3, 3, 1, 3, 3, 2, 3, 3), SpriteAnimationEndAction.LOOP, 0));

        // Anim 2: .sonic - dc.b 1, 0, 4, 4, 1, 4, 4, 2, 4, 4, afEnd
        set.addScript(2, new SpriteAnimationScript(1,
                List.of(0, 4, 4, 1, 4, 4, 2, 4, 4), SpriteAnimationEndAction.LOOP, 0));

        // Anim 3: .shoes - dc.b 1, 0, 5, 5, 1, 5, 5, 2, 5, 5, afEnd
        set.addScript(3, new SpriteAnimationScript(1,
                List.of(0, 5, 5, 1, 5, 5, 2, 5, 5), SpriteAnimationEndAction.LOOP, 0));

        // Anim 4: .shield - dc.b 1, 0, 6, 6, 1, 6, 6, 2, 6, 6, afEnd
        set.addScript(4, new SpriteAnimationScript(1,
                List.of(0, 6, 6, 1, 6, 6, 2, 6, 6), SpriteAnimationEndAction.LOOP, 0));

        // Anim 5: .invincible - dc.b 1, 0, 7, 7, 1, 7, 7, 2, 7, 7, afEnd
        set.addScript(5, new SpriteAnimationScript(1,
                List.of(0, 7, 7, 1, 7, 7, 2, 7, 7), SpriteAnimationEndAction.LOOP, 0));

        // Anim 6: .rings - dc.b 1, 0, 8, 8, 1, 8, 8, 2, 8, 8, afEnd
        set.addScript(6, new SpriteAnimationScript(1,
                List.of(0, 8, 8, 1, 8, 8, 2, 8, 8), SpriteAnimationEndAction.LOOP, 0));

        // Anim 7: .s - dc.b 1, 0, 9, 9, 1, 9, 9, 2, 9, 9, afEnd
        set.addScript(7, new SpriteAnimationScript(1,
                List.of(0, 9, 9, 1, 9, 9, 2, 9, 9), SpriteAnimationEndAction.LOOP, 0));

        // Anim 8: .goggles - dc.b 1, 0, $A, $A, 1, $A, $A, 2, $A, $A, afEnd
        set.addScript(8, new SpriteAnimationScript(1,
                List.of(0, 10, 10, 1, 10, 10, 2, 10, 10), SpriteAnimationEndAction.LOOP, 0));

        // Anim 9: .breaking - dc.b 2, 0, 1, 2, $B, afBack, 1
        // Plays frames 0→1→2→11, then holds on frame 11 (broken shell)
        set.addScript(9, new SpriteAnimationScript(2,
                List.of(0, 1, 2, 11), SpriteAnimationEndAction.LOOP_BACK, 1));

        return set;
    }

    /**
     * Loads explosion art (Nem_Explode) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Explosions.asm (Map_ExplodeItem).
     * Used by monitor break, badnik destruction, etc.
     */
    private void loadExplosionArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_EXPLOSION_ADDR, "Explosion");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load explosion art");
            return;
        }

        List<SpriteMappingFrame> mappings = createExplosionMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.EXPLOSION, sheet);
    }

    /**
     * Creates explosion sprite mappings from S1 disassembly Map_ExplodeItem.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * 5 frames: small burst → medium → large → scatter (4 pieces) → scatter (4 pieces)
     */
    private List<SpriteMappingFrame> createExplosionMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: small burst (1 piece, 3x2 tiles)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -8, 3, 2, 0, false, false, 0, false)
        )));

        // Frame 1: medium burst (1 piece, 4x4 tiles)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 6, false, false, 0, false)
        )));

        // Frame 2: large burst (1 piece, 4x4 tiles)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x16, false, false, 0, false)
        )));

        // Frame 3: scatter (4 pieces, 3x3 + 2x2 + 2x2 + 3x3 with flips)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 3, 3, 0x26, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x14, 2, 2, 0x2F, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     4, 2, 2, 0x2F, true,  true,  0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 3, 0x26, true,  true,  0, false)
        )));

        // Frame 4: final scatter (4 pieces, 3x3 + 2x2 + 2x2 + 3x3 with flips)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 3, 3, 0x33, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x14, 2, 2, 0x3C, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     4, 2, 2, 0x3C, true,  true,  0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 3, 0x33, true,  true,  0, false)
        )));

        return frames;
    }

    /**
     * Loads shield art (Nem_Shield) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Shield and Invincibility.asm (Map_Shield_internal).
     * <p>
     * S1 shield has 4 frames:
     * <ul>
     *   <li>Frame 0 (.shield1): bottom half only (2 pieces, 3x3 tiles each)</li>
     *   <li>Frame 1 (.shield2): full quad - top + bottom (4 pieces)</li>
     *   <li>Frame 2 (.shield3): full quad, alternate tile ($12) (4 pieces)</li>
     *   <li>Frame 3 (.shield4): full quad, mirrored (4 pieces)</li>
     * </ul>
     */
    private void loadShieldArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SHIELD_ADDR, "Shield");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load shield art");
            return;
        }

        List<SpriteMappingFrame> mappings = createShieldMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.SHIELD, sheet);
    }

    /**
     * Creates shield sprite mappings from S1 disassembly Map_Shield_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createShieldMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .shield1 - bottom half only (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, 0, 3, 3, 0, false, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 9, false, true, 0, false)
        )));

        // Frame 1: .shield2 - full quad (4 pieces: top + bottom)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0, false, false, 0, false),
                new SpriteMappingPiece(0, -0x18, 3, 3, 9, false, false, 0, false),
                new SpriteMappingPiece(-0x18, 0, 3, 3, 0, false, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 9, false, true, 0, false)
        )));

        // Frame 2: .shield3 - full quad, alternate tile $12 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x17, -0x18, 3, 3, 0x12, true, false, 0, false),
                new SpriteMappingPiece(0, -0x18, 3, 3, 0x12, false, false, 0, false),
                new SpriteMappingPiece(-0x17, 0, 3, 3, 0x12, true, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 0x12, false, true, 0, false)
        )));

        // Frame 3: .shield4 - full quad, mirrored (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 9, true, false, 0, false),
                new SpriteMappingPiece(0, -0x18, 3, 3, 0, true, false, 0, false),
                new SpriteMappingPiece(-0x18, 0, 3, 3, 9, true, true, 0, false),
                new SpriteMappingPiece(0, 0, 3, 3, 0, true, true, 0, false)
        )));

        return frames;
    }

    /**
     * Loads Sonic 1 points popups and zone-specific animal art.
     * <p>
     * S1 uses two animal art banks per zone (Anml_VarIndex), selected at runtime
     * when an enemy is destroyed.
     */
    private void loadAnimalAndPointsArt(Rom rom, int zoneIndex) {
        AnimalType[] zoneAnimals = resolveZoneAnimals(zoneIndex);
        animalTypeA = zoneAnimals[0].ordinal();
        animalTypeB = zoneAnimals[1].ordinal();

        Pattern[] firstAnimalPatterns = loadAnimalPatterns(rom, zoneAnimals[0]);
        Pattern[] secondAnimalPatterns = loadAnimalPatterns(rom, zoneAnimals[1]);
        if (firstAnimalPatterns.length == 0 || secondAnimalPatterns.length == 0) {
            LOGGER.warning("Failed to load S1 animal art for zone " + zoneIndex);
            return;
        }

        Pattern[] combinedAnimals = createCombinedAnimalPatterns(firstAnimalPatterns, secondAnimalPatterns);
        ObjectSpriteSheet animalSheet = new ObjectSpriteSheet(
                combinedAnimals,
                createAnimalMappings(),
                0,
                1);
        registerSheet(ObjectArtKeys.ANIMAL, animalSheet);

        Pattern[] pointsPatterns = loadNemesisPatterns(rom, Sonic1Constants.ART_NEM_POINTS_ADDR, "Points");
        if (pointsPatterns.length == 0) {
            LOGGER.warning("Failed to load S1 points art");
            return;
        }
        ObjectSpriteSheet pointsSheet = new ObjectSpriteSheet(pointsPatterns, createPointsMappings(), 1, 0);
        registerSheet(ObjectArtKeys.POINTS, pointsSheet);
    }

    private AnimalType[] resolveZoneAnimals(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= ZONE_ANIMALS.length) {
            return DEFAULT_ANIMALS;
        }
        return ZONE_ANIMALS[zoneIndex];
    }

    private Pattern[] loadAnimalPatterns(Rom rom, AnimalType type) {
        int address = switch (type) {
            case RABBIT -> Sonic1Constants.ART_NEM_ANIMAL_RABBIT_ADDR;
            case CHICKEN -> Sonic1Constants.ART_NEM_ANIMAL_CHICKEN_ADDR;
            case PENGUIN -> Sonic1Constants.ART_NEM_ANIMAL_PENGUIN_ADDR;
            case SEAL -> Sonic1Constants.ART_NEM_ANIMAL_SEAL_ADDR;
            case PIG -> Sonic1Constants.ART_NEM_ANIMAL_PIG_ADDR;
            case FLICKY -> Sonic1Constants.ART_NEM_ANIMAL_FLICKY_ADDR;
            case SQUIRREL -> Sonic1Constants.ART_NEM_ANIMAL_SQUIRREL_ADDR;
            default -> -1;
        };
        if (address < 0) {
            return new Pattern[0];
        }
        return loadNemesisPatterns(rom, address, "Animal_" + type.displayName());
    }

    private Pattern[] createCombinedAnimalPatterns(Pattern[] first, Pattern[] second) {
        int firstLength = Math.max(ANIMAL_TILE_BANK_SIZE, first.length);
        int secondLength = Math.max(ANIMAL_TILE_BANK_SIZE, second.length);
        Pattern[] combined = new Pattern[firstLength + secondLength];

        for (int i = 0; i < combined.length; i++) {
            combined[i] = new Pattern();
        }
        for (int i = 0; i < first.length; i++) {
            combined[i] = first[i];
        }
        for (int i = 0; i < second.length; i++) {
            combined[firstLength + i] = second[i];
        }
        return combined;
    }

    private List<SpriteMappingFrame> createAnimalMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();
        AnimalType.MappingSet[] sets = {
                AnimalType.MappingSet.A,
                AnimalType.MappingSet.B,
                AnimalType.MappingSet.C,
                AnimalType.MappingSet.D,
                AnimalType.MappingSet.E
        };

        // Frame order matches AnimalObjectInstance.getFrameIndex():
        // ((mappingSet * 2) + artVariant) * 3 + animFrame
        for (AnimalType.MappingSet mappingSet : sets) {
            addAnimalSetFrames(frames, mappingSet, 0);
            addAnimalSetFrames(frames, mappingSet, ANIMAL_TILE_BANK_SIZE);
        }
        return frames;
    }

    private void addAnimalSetFrames(List<SpriteMappingFrame> frames, AnimalType.MappingSet mappingSet, int tileOffset) {
        switch (mappingSet) {
            case A, D -> {
                // Map_Animal2: flying set (flicky/chicken/seal style)
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -4, 2, 2, tileOffset + 0x06, false, false, 0, false)
                )));
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -4, 2, 2, tileOffset + 0x0A, false, false, 0, false)
                )));
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -0x0C, 2, 3, tileOffset, false, false, 0, false)
                )));
            }
            case B, C -> {
                // Map_Animal3: wide body set (squirrel/pig style)
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-0x0C, -4, 3, 2, tileOffset + 0x06, false, false, 0, false)
                )));
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-0x0C, -4, 3, 2, tileOffset + 0x0C, false, false, 0, false)
                )));
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -0x0C, 2, 3, tileOffset, false, false, 0, false)
                )));
            }
            case E -> {
                // Map_Animal1: tall walker set (rabbit/penguin style)
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -0x0C, 2, 3, tileOffset + 0x06, false, false, 0, false)
                )));
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -0x0C, 2, 3, tileOffset + 0x0C, false, false, 0, false)
                )));
                frames.add(new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -0x0C, 2, 3, tileOffset, false, false, 0, false)
                )));
            }
        }
    }

    private List<SpriteMappingFrame> createPointsMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Obj29 score popup frames. Frame order is aligned with PointsObjectInstance.
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 2, 1, 0, false, false, 0, false) // 100
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 2, 1, 2, false, false, 0, false) // 200
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 2, 1, 4, false, false, 0, false) // 500
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 3, 1, 6, false, false, 0, false) // 1000
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, 6, false, false, 0, false) // 10
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 3, 1, 6, false, false, 0, false) // 1000 alt slot
        )));

        return frames;
    }

    /**
     * Loads results screen art by compositing title card patterns + HUD text patterns
     * into a single VRAM-aligned pattern array, matching the original S1 layout.
     * <p>
     * VRAM layout:
     * <ul>
     *   <li>$570-$57F (indices 0-15): Writable bonus digit slots (time + ring bonus)</li>
     *   <li>$580+ (indices 16+): Title card art (Nem_TitleCard)</li>
     *   <li>$6CA+ (indices 0x15A+): HUD text art (Nem_Hud: SCORE/TIME/RINGS labels)</li>
     * </ul>
     * <p>
     * Mapping tile IDs are relative to ArtTile_Title_Card ($580).
     * Array index = tile_id + RESULTS_TILE_ADJUST (0x10).
     */
    private void loadResultsScreenArt(Rom rom) {
        Pattern[] titleCardPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_TITLE_CARD_ADDR, "TitleCard");
        if (titleCardPatterns.length == 0) {
            LOGGER.warning("Failed to load title card art for results screen");
            return;
        }
        if (hudTextPatterns == null || hudTextPatterns.length == 0) {
            LOGGER.warning("HUD text patterns not available for results screen");
            return;
        }

        // HUD text starts at VRAM $6CA. Results mappings also reference
        // $6E2-$6F1 for score digits/trailing zero (loaded from Art_Hud).
        int hudTextStartIndex = Sonic1Constants.VRAM_RESULTS_HUD_TEXT - Sonic1Constants.VRAM_RESULTS_BASE;
        int hudScoreDigitsStartIndex =
                (Sonic1Constants.VRAM_RESULTS_HUD_TEXT + 0x18) - Sonic1Constants.VRAM_RESULTS_BASE;
        int totalSize = Math.max(
                hudTextStartIndex + hudTextPatterns.length,
                hudScoreDigitsStartIndex + RESULTS_SCORE_DIGIT_TILE_COUNT);

        Pattern[] compositePatterns = new Pattern[totalSize];

        // Fill with blank patterns
        for (int i = 0; i < totalSize; i++) {
            compositePatterns[i] = new Pattern();
        }

        // Copy title card patterns at index RESULTS_TILE_ADJUST (0x10)
        int titleCardStart = Sonic1Constants.RESULTS_TILE_ADJUST;
        for (int i = 0; i < titleCardPatterns.length && (titleCardStart + i) < totalSize; i++) {
            compositePatterns[titleCardStart + i] = titleCardPatterns[i];
        }

        // Copy HUD text patterns at hudTextStartIndex
        for (int i = 0; i < hudTextPatterns.length && (hudTextStartIndex + i) < totalSize; i++) {
            compositePatterns[hudTextStartIndex + i] = hudTextPatterns[i];
        }
        copyResultsScoreDigitTiles(compositePatterns, hudScoreDigitsStartIndex);

        List<SpriteMappingFrame> mappings = createResultsScreenMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(compositePatterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.RESULTS, sheet);

        LOGGER.info("Results screen art loaded: " + totalSize + " composite patterns, "
                + mappings.size() + " frames");
    }

    private void copyResultsScoreDigitTiles(Pattern[] dest, int startIndex) {
        if (dest == null || hudDigitPatterns == null || hudDigitPatterns.length < 2) {
            return;
        }

        // Tile pair $6E2-$6E3 is "E" (from Nem_Hud), followed by seven "0" pairs.
        copyPatternPair(dest, startIndex, hudTextPatterns, HUD_TEXT_E_PAIR_INDEX);
        for (int pair = 1; pair < RESULTS_SCORE_DIGIT_PAIR_COUNT; pair++) {
            copyPatternPair(dest, startIndex + (pair * 2), hudDigitPatterns, 0);
        }
    }

    private void copyPatternPair(Pattern[] dest, int destIndex, Pattern[] src, int srcIndex) {
        if (src == null || srcIndex < 0 || srcIndex + 1 >= src.length) {
            return;
        }
        if (destIndex < 0 || destIndex + 1 >= dest.length) {
            return;
        }
        if (dest[destIndex] == null) {
            dest[destIndex] = new Pattern();
        }
        if (dest[destIndex + 1] == null) {
            dest[destIndex + 1] = new Pattern();
        }
        dest[destIndex].copyFrom(src[srcIndex]);
        dest[destIndex + 1].copyFrom(src[srcIndex + 1]);
    }

    /**
     * Creates sprite mappings for the Sonic 1 results screen from Map_Got in the disassembly.
     * <p>
     * All tile IDs from the disassembly are relative to ArtTile_Title_Card ($580).
     * We add RESULTS_TILE_ADJUST (0x10) to convert to composite array indices.
     * <p>
     * Frames:
     * <ol start="0">
     *   <li>"SONIC HAS" (8 pieces)</li>
     *   <li>"PASSED" (6 pieces)</li>
     *   <li>"SCORE" + score area (6 pieces)</li>
     *   <li>"TIME BONUS" + digit area (7 pieces)</li>
     *   <li>"RING BONUS" + digit area (7 pieces)</li>
     *   <li>Oval decoration (13 pieces)</li>
     *   <li>"ACT 1" (2 pieces)</li>
     *   <li>"ACT 2" (2 pieces)</li>
     *   <li>"ACT 3" (2 pieces)</li>
     * </ol>
     */
    private List<SpriteMappingFrame> createResultsScreenMappings() {
        final int T = Sonic1Constants.RESULTS_TILE_ADJUST; // 0x10
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Got_SonicHas - "SONIC HAS" (8 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x48, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x38, -8, 2, 2, 0x32 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x28, -8, 2, 2, 0x2E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x18, -8, 1, 2, 0x20 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x08 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x1C + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 2, 2, 0x3E + T, false, false, 0, false)
        )));

        // Frame 1: M_Got_Passed - "PASSED" (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x30, -8, 2, 2, 0x36 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x0C + T, false, false, 0, false)
        )));

        // Frame 2: M_Got_Score - "SCORE" text + score digits (4 pieces)
        // Separator dots split into frame 9 to ensure correct z-ordering:
        // batched rendering can lose within-frame piece priority, so the dots
        // must be in a separate drawFrameIndex call issued before this frame.
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -8, 1, 2, 0x162 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x18, -8, 3, 2, 0x164 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 4, 2, 0x16A + T, false, false, 0, false)
        )));

        // Frame 3: M_Got_TBonus - "TIME BONUS" + digit area (7 pieces)
        // Tile -$10 = time bonus digits at array index 0 (= -0x10 + T = 0)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x15A + T, false, false, 0, false),
                new SpriteMappingPiece(-0x27, -8, 4, 2, 0x66 + T,  false, false, 0, false),
                new SpriteMappingPiece(   -7, -8, 1, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece( -0xA, -9, 2, 1, 0x6E + T,  false, false, 0, false),
                new SpriteMappingPiece( -0xA, -1, 2, 1, 0x6E + T,  true,  true,  0, false),
                new SpriteMappingPiece( 0x28, -8, 4, 2, 0,         false, false, 0, false),
                new SpriteMappingPiece( 0x48, -8, 1, 2, 0x170 + T, false, false, 0, false)
        )));

        // Frame 4: M_Got_RBonus - "RING BONUS" + digit area (7 pieces)
        // Tile -$8 = ring bonus digits at array index 8 (= -0x08 + T = 8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x152 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x27, -8, 4, 2, 0x66 + T,  false, false, 0, false),
                new SpriteMappingPiece(   -7, -8, 1, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece( -0xA, -9, 2, 1, 0x6E + T,  false, false, 0, false),
                new SpriteMappingPiece( -0xA, -1, 2, 1, 0x6E + T,  true,  true,  0, false),
                new SpriteMappingPiece( 0x28, -8, 4, 2, 8,         false, false, 0, false),
                new SpriteMappingPiece( 0x48, -8, 1, 2, 0x170 + T, false, false, 0, false)
        )));

        // Frame 5: M_Card_Oval - Oval decoration (13 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 4, 1, 0x70 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x14, -0x1C, 1, 3, 0x74 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 2, 1, 0x77 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x1C, -0x0C, 2, 2, 0x79 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x14, 4, 1, 0x70 + T, true,  true,  0, false),
                new SpriteMappingPiece(-0x1C,  0x04, 1, 3, 0x74 + T, true,  true,  0, false),
                new SpriteMappingPiece( 0x04,  0x0C, 2, 1, 0x77 + T, true,  true,  0, false),
                new SpriteMappingPiece( 0x0C, -0x04, 2, 2, 0x79 + T, true,  true,  0, false),
                new SpriteMappingPiece(-0x04, -0x14, 3, 1, 0x7D + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x0C, 4, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x04, 3, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x04, 4, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x0C, 3, 1, 0x7C + T, false, false, 0, false)
        )));

        // Frame 6: M_Card_Act1 - "ACT 1" (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x0C, 1, 3, 0x57 + T, false, false, 0, false)
        )));

        // Frame 7: M_Card_Act2 - "ACT 2" (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x0C, 2, 3, 0x5A + T, false, false, 0, false)
        )));

        // Frame 8: M_Card_Act3 - "ACT 3" (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x0C, 2, 3, 0x60 + T, false, false, 0, false)
        )));

        // Frame 9: SCORE separator dots (split from frame 2)
        // Drawn as a separate drawFrameIndex call before the SCORE text frame
        // so that batched rendering correctly places dots behind text.
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x33, -9, 2, 1, 0x6E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x33, -1, 2, 1, 0x6E + T, true,  true,  0, false)
        )));

        return frames;
    }

    /**
     * Loads spring art (Nem_HSpring + Nem_VSpring) and creates sprite sheets and animations.
     * <p>
     * S1 springs use two separate art sets:
     * <ul>
     *   <li>Nem_HSpring (horizontal plate) - used for up/down springs (3 frames: idle, flat, extended)</li>
     *   <li>Nem_VSpring (vertical plate) - used for left/right springs (3 frames: idle, flat, extended)</li>
     * </ul>
     * <p>
     * Red springs use palette line 0, yellow springs use palette line 1
     * (from disassembly: bset #5,obGfx for yellow).
     * <p>
     * Mappings from docs/s1disasm/_maps/Springs.asm (Map_Spring_internal).
     * Animations from docs/s1disasm/_anim/Springs.asm (Ani_Spring).
     */
    private void loadSpringArt(Rom rom) {
        // Load horizontal spring art (for up/down springs)
        Pattern[] hPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_HSPRING_ADDR, "HSpring");
        if (hPatterns.length > 0) {
            List<SpriteMappingFrame> vMappings = createVerticalSpringMappings();
            // Red up/down springs: palette 0
            ObjectSpriteSheet vSheet = new ObjectSpriteSheet(hPatterns, vMappings, 0, 1);
            registerSheet(ObjectArtKeys.SPRING_VERTICAL, vSheet);
            // Yellow up/down springs: palette 1
            ObjectSpriteSheet vSheetYellow = new ObjectSpriteSheet(hPatterns, vMappings, 1, 1);
            registerSheet(ObjectArtKeys.SPRING_VERTICAL_RED, vSheetYellow);
        } else {
            LOGGER.warning("Failed to load horizontal spring art (Nem_HSpring)");
        }

        // Load vertical spring art (for left/right springs)
        Pattern[] vPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_VSPRING_ADDR, "VSpring");
        if (vPatterns.length > 0) {
            List<SpriteMappingFrame> hMappings = createHorizontalSpringMappings();
            // Red left/right springs: palette 0
            ObjectSpriteSheet hSheet = new ObjectSpriteSheet(vPatterns, hMappings, 0, 1);
            registerSheet(ObjectArtKeys.SPRING_HORIZONTAL, hSheet);
            // Yellow left/right springs: palette 1
            ObjectSpriteSheet hSheetYellow = new ObjectSpriteSheet(vPatterns, hMappings, 1, 1);
            registerSheet(ObjectArtKeys.SPRING_HORIZONTAL_RED, hSheetYellow);
        } else {
            LOGGER.warning("Failed to load vertical spring art (Nem_VSpring)");
        }

        // Register spring animation scripts
        SpriteAnimationSet springAnims = createSpringAnimations();
        animations.put(ObjectArtKeys.ANIM_SPRING, springAnims);
    }

    /**
     * Creates sprite mappings for up/down springs (using Nem_HSpring art).
     * <p>
     * Despite the confusing naming, "Horizontal" art has a horizontal plate
     * and is used for springs that push vertically (up/down).
     * <p>
     * 3 frames (re-indexed from Map_Spring_internal frames 0-2):
     * <ul>
     *   <li>Frame 0 (M_Spg_Up): Idle - plate + base (32x16)</li>
     *   <li>Frame 1 (M_Spg_UpFlat): Compressed - plate only (32x8)</li>
     *   <li>Frame 2 (M_Spg_UpExt): Extended - plate + coil + base (32x32)</li>
     * </ul>
     */
    private List<SpriteMappingFrame> createVerticalSpringMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Spg_Up - idle (2 pieces)
        // spritePiece -$10, -8, 4, 1, 0, 0, 0, 0, 0
        // spritePiece -$10,  0, 4, 1, 4, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -8, 4, 1, 0, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0, 4, 1, 4, false, false, 0, false)
        )));

        // Frame 1: M_Spg_UpFlat - compressed (1 piece)
        // spritePiece -$10, 0, 4, 1, 0, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, 0, 4, 1, 0, false, false, 0, false)
        )));

        // Frame 2: M_Spg_UpExt - extended (3 pieces)
        // spritePiece -$10, -$18, 4, 1, 0, 0, 0, 0, 0
        // spritePiece   -8, -$10, 2, 2, 8, 0, 0, 0, 0
        // spritePiece -$10,    0, 4, 1, $C, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x18, 4, 1, 0, false, false, 0, false),
                new SpriteMappingPiece(  -8,  -0x10, 2, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 1, 0xC, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Creates sprite mappings for left/right springs (using Nem_VSpring art).
     * <p>
     * Despite the confusing naming, "Vertical" art has a vertical plate
     * and is used for springs that push horizontally (left/right).
     * <p>
     * 3 frames (re-indexed from Map_Spring_internal frames 3-5):
     * <ul>
     *   <li>Frame 0 (M_Spg_Left): Idle - single tall piece (16x32)</li>
     *   <li>Frame 1 (M_Spg_LeftFlat): Compressed - thin (8x32)</li>
     *   <li>Frame 2 (M_Spg_LeftExt): Extended - plate + coil + corners (4 pieces)</li>
     * </ul>
     */
    private List<SpriteMappingFrame> createHorizontalSpringMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Spg_Left - idle (1 piece)
        // spritePiece -8, -$10, 2, 4, 0, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x10, 2, 4, 0, false, false, 0, false)
        )));

        // Frame 1: M_Spg_LeftFlat - compressed (1 piece)
        // spritePiece -8, -$10, 1, 4, 4, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x10, 1, 4, 4, false, false, 0, false)
        )));

        // Frame 2: M_Spg_LeftExt - extended (4 pieces)
        // spritePiece  $10, -$10, 1, 4, 4, 0, 0, 0, 0
        // spritePiece   -8,   -8, 3, 2, 8, 0, 0, 0, 0
        // spritePiece   -8, -$10, 1, 1, 0, 0, 0, 0, 0
        // spritePiece   -8,    8, 1, 1, 3, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece( 0x10, -0x10, 1, 4, 4, false, false, 0, false),
                new SpriteMappingPiece(   -8,    -8, 3, 2, 8, false, false, 0, false),
                new SpriteMappingPiece(   -8, -0x10, 1, 1, 0, false, false, 0, false),
                new SpriteMappingPiece(   -8,     8, 1, 1, 3, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Creates spring animation scripts from S1 disassembly Ani_Spring.
     * <p>
     * Since vertical and horizontal sheets both use the same frame indices (0-2),
     * only two animation IDs are needed: idle (hold on frame 0) and triggered.
     * <p>
     * Ani_Spring anim 0 (vertical trigger): speed=0, frames [1,0,0,2,2,2,2,2,2,0], afRoutine
     * Ani_Spring anim 1 (horizontal trigger): speed=0, frames [1,0,0,2,2,2,2,2,2,0], afRoutine
     * Both are structurally identical with re-indexed frames.
     */
    private SpriteAnimationSet createSpringAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Idle - hold on frame 0
        set.addScript(0, new SpriteAnimationScript(0,
                List.of(0), SpriteAnimationEndAction.HOLD, 0));

        // Anim 1: Triggered - spring bounce animation, then switch back to idle
        // From disassembly: dc.b 0, 1, 0, 0, 2, 2, 2, 2, 2, 2, 0, afRoutine
        // afRoutine increments obRoutine, which resets to idle state.
        // In our engine: SWITCH to anim 0 (idle) when complete.
        set.addScript(1, new SpriteAnimationScript(0,
                List.of(1, 0, 0, 2, 2, 2, 2, 2, 2, 0), SpriteAnimationEndAction.SWITCH, 0));

        return set;
    }

    /**
     * Loads Crabmeat art (Nem_Crabmeat) and creates sprite sheet.
     * Crabmeat and its projectiles share the same art tile set (ArtTile_Crabmeat = $400).
     * Mappings from docs/s1disasm/_maps/Crabmeat.asm (Map_Crab_internal).
     * <p>
     * 7 mapping frames:
     * 0 (.stand): Standing/idle - 4 pieces (symmetric, right half is h-flipped left)
     * 1 (.walk): Walking - 4 pieces
     * 2 (.slope1): Walking on slope - 4 pieces
     * 3 (.slope2): Walking on slope (other leg) - 4 pieces
     * 4 (.firing): Firing projectiles - 6 pieces (symmetric)
     * 5 (.ball1): Projectile frame 1 - 1 piece (16x16)
     * 6 (.ball2): Projectile frame 2 - 1 piece (16x16)
     */
    private void loadCrabmeatArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_CRABMEAT_ADDR, "Crabmeat");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Crabmeat art");
            return;
        }

        List<SpriteMappingFrame> mappings = createCrabmeatMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.CRABMEAT, sheet);
    }

    /**
     * Creates Crabmeat sprite mappings from S1 disassembly Map_Crab_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createCrabmeatMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .stand - Standing/idle (4 pieces, symmetric body)
        // spritePiece -$18, -$10, 3, 2, 0, 0, 0, 0, 0
        // spritePiece    0, -$10, 3, 2, 0, 1, 0, 0, 0  (h-flipped mirror)
        // spritePiece -$10,    0, 2, 2, 6, 0, 0, 0, 0
        // spritePiece    0,    0, 2, 2, 6, 1, 0, 0, 0  (h-flipped mirror)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 2, 0x00, true,  false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 2, 2, 0x06, false, false, 0, false),
                new SpriteMappingPiece(    0,     0, 2, 2, 0x06, true,  false, 0, false)
        )));

        // Frame 1: .walk - Walking (4 pieces)
        // spritePiece -$18, -$10, 3, 2, $A, 0, 0, 0, 0
        // spritePiece    0, -$10, 3, 2, $10, 0, 0, 0, 0
        // spritePiece -$10,    0, 2, 2, $16, 0, 0, 0, 0
        // spritePiece    0,    0, 3, 2, $1A, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x10, 3, 2, 0x0A, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 3, 2, 0x10, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 2, 2, 0x16, false, false, 0, false),
                new SpriteMappingPiece(    0,     0, 3, 2, 0x1A, false, false, 0, false)
        )));

        // Frame 2: .slope1 - Walking on slope (4 pieces)
        // spritePiece -$18, -$14, 3, 2, 0, 0, 0, 0, 0
        // spritePiece    0, -$14, 3, 2, 0, 1, 0, 0, 0  (h-flipped)
        // spritePiece    0,   -4, 2, 2, 6, 1, 0, 0, 0  (h-flipped)
        // spritePiece -$10,   -4, 2, 3, $20, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x14, 3, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x14, 3, 2, 0x00, true,  false, 0, false),
                new SpriteMappingPiece(    0,    -4, 2, 2, 0x06, true,  false, 0, false),
                new SpriteMappingPiece(-0x10,    -4, 2, 3, 0x20, false, false, 0, false)
        )));

        // Frame 3: .slope2 - Walking on slope, other leg (4 pieces)
        // spritePiece -$18, -$14, 3, 2, $A, 0, 0, 0, 0
        // spritePiece    0, -$14, 3, 2, $10, 0, 0, 0, 0
        // spritePiece    0,   -4, 3, 2, $26, 0, 0, 0, 0
        // spritePiece -$10,   -4, 2, 3, $2C, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x14, 3, 2, 0x0A, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x14, 3, 2, 0x10, false, false, 0, false),
                new SpriteMappingPiece(    0,    -4, 3, 2, 0x26, false, false, 0, false),
                new SpriteMappingPiece(-0x10,    -4, 2, 3, 0x2C, false, false, 0, false)
        )));

        // Frame 4: .firing - Firing projectiles (6 pieces, symmetric)
        // spritePiece -$10, -$10, 2, 1, $32, 0, 0, 0, 0
        // spritePiece    0, -$10, 2, 1, $32, 1, 0, 0, 0  (h-flipped)
        // spritePiece -$18,   -8, 3, 2, $34, 0, 0, 0, 0
        // spritePiece    0,   -8, 3, 2, $34, 1, 0, 0, 0  (h-flipped)
        // spritePiece -$10,    8, 2, 1, $3A, 0, 0, 0, 0
        // spritePiece    0,    8, 2, 1, $3A, 1, 0, 0, 0  (h-flipped)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 2, 1, 0x32, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 2, 1, 0x32, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,  -0x8, 3, 2, 0x34, false, false, 0, false),
                new SpriteMappingPiece(    0,  -0x8, 3, 2, 0x34, true,  false, 0, false),
                new SpriteMappingPiece(-0x10,   0x8, 2, 1, 0x3A, false, false, 0, false),
                new SpriteMappingPiece(    0,   0x8, 2, 1, 0x3A, true,  false, 0, false)
        )));

        // Frame 5: .ball1 - Projectile frame 1 (1 piece, 16x16)
        // spritePiece -8, -8, 2, 2, $3C, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x3C, false, false, 0, false)
        )));

        // Frame 6: .ball2 - Projectile frame 2 (1 piece, 16x16)
        // spritePiece -8, -8, 2, 2, $40, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x40, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads Chopper art (Nem_Chopper) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Chopper.asm (Map_Chop_internal).
     * 2 frames: mouth shut (frame 0) and mouth open (frame 1).
     * Each frame is a single 4x4 (32x32 pixel) sprite piece.
     */
    private void loadChopperArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_CHOPPER_ADDR, "Chopper");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Chopper art");
            return;
        }

        List<SpriteMappingFrame> mappings = createChopperMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.CHOPPER, sheet);
    }

    /**
     * Creates Chopper sprite mappings from S1 disassembly Map_Chop_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.mouthshut): spritePiece -$10, -$10, 4, 4, 0, 0, 0, 0, 0
     * Frame 1 (.mouthopen): spritePiece -$10, -$10, 4, 4, $10, 0, 0, 0, 0
     */
    private List<SpriteMappingFrame> createChopperMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .mouthshut - Single 32x32 piece, tiles 0x00-0x0F
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x00, false, false, 0, false)
        )));

        // Frame 1: .mouthopen - Single 32x32 piece, tiles 0x10-0x1F
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x10, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads Motobug art (Nem_Motobug) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Moto Bug.asm (Map_Moto_internal).
     * 7 frames: 3 body frames (walk cycle), 3 smoke frames, 1 blank frame.
     */
    private void loadMotobugArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_MOTOBUG_ADDR, "Motobug");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Motobug art");
            return;
        }

        List<SpriteMappingFrame> mappings = createMotobugMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.MOTOBUG, sheet);
    }

    /**
     * Creates Motobug sprite mappings from S1 disassembly Map_Moto_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createMotobugMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .moto1 - Standing/walk frame 1 (4 pieces)
        // spritePiece -$14, -$10, 4, 2, 0, 0, 0, 0, 0
        // spritePiece -$14, 0, 4, 1, 8, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $C, 0, 0, 0, 0
        // spritePiece -$C, 8, 3, 1, $E, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x10, 4, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     0, 4, 1, 0x08, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x0C, false, false, 0, false),
                new SpriteMappingPiece( -0xC,   0x8, 3, 1, 0x0E, false, false, 0, false)
        )));

        // Frame 1: .moto2 - Walk frame 2 (4 pieces, slightly shifted)
        // spritePiece -$14, -$F, 4, 2, 0, 0, 0, 0, 0
        // spritePiece -$14, 1, 4, 1, 8, 0, 0, 0, 0
        // spritePiece $C, -7, 1, 2, $C, 0, 0, 0, 0
        // spritePiece -$C, 9, 3, 1, $11, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0xF, 4, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     1, 4, 1, 0x08, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x7, 1, 2, 0x0C, false, false, 0, false),
                new SpriteMappingPiece( -0xC,   0x9, 3, 1, 0x11, false, false, 0, false)
        )));

        // Frame 2: .moto3 - Walk frame 3 (5 pieces, different leg positions)
        // spritePiece -$14, -$10, 4, 2, 0, 0, 0, 0, 0
        // spritePiece -$14, 0, 4, 1, $14, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $C, 0, 0, 0, 0
        // spritePiece -$14, 8, 2, 1, $18, 0, 0, 0, 0
        // spritePiece -4, 8, 2, 1, $12, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x10, 4, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14,     0, 4, 1, 0x14, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x0C, false, false, 0, false),
                new SpriteMappingPiece(-0x14,   0x8, 2, 1, 0x18, false, false, 0, false),
                new SpriteMappingPiece(   -4,   0x8, 2, 1, 0x12, false, false, 0, false)
        )));

        // Frame 3: .smoke1 - Smoke puff frame 1 (1 piece, 8x8)
        // spritePiece $10, -6, 1, 1, $1A, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x10, -6, 1, 1, 0x1A, false, false, 0, false)
        )));

        // Frame 4: .smoke2 - Smoke puff frame 2 (1 piece, 8x8)
        // spritePiece $10, -6, 1, 1, $1B, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x10, -6, 1, 1, 0x1B, false, false, 0, false)
        )));

        // Frame 5: .smoke3 - Smoke puff frame 3 (1 piece, 8x8)
        // spritePiece $10, -6, 1, 1, $1C, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x10, -6, 1, 1, 0x1C, false, false, 0, false)
        )));

        // Frame 6: .blank - Empty frame (0 pieces)
        frames.add(new SpriteMappingFrame(List.of()));

        return frames;
    }

    /**
     * Loads Newtron art (Nem_Newtron) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Newtron.asm (Map_Newt_internal).
     * 11 frames: Trans, Norm, Fires, Drop1-3, Fly1a/b, Fly2a/b, Blank.
     */
    private void loadNewtronArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_NEWTRON_ADDR, "Newtron");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Newtron art");
            return;
        }

        List<SpriteMappingFrame> mappings = createNewtronMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.NEWTRON, sheet);
    }

    /**
     * Creates Newtron sprite mappings from S1 disassembly Map_Newt_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * 11 frames indexed 0-10. Type 1 (green/missile) variant uses palette line 1
     * set via obGfx at runtime, not in mappings.
     */
    private List<SpriteMappingFrame> createNewtronMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: M_Newt_Trans - Partially visible (appearing, 3 pieces)
        // spritePiece -$14, -$14, 4, 2, 0, 0, 0, 0, 0
        // spritePiece $C, -$C, 1, 1, 8, 0, 0, 0, 0
        // spritePiece -$C, -4, 4, 3, 9, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 4, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0xC, 1, 1, 0x08, false, false, 0, false),
                new SpriteMappingPiece( -0xC,    -4, 4, 3, 0x09, false, false, 0, false)
        )));

        // Frame 1: M_Newt_Norm - Normal standing (3 pieces)
        // spritePiece -$14, -$14, 2, 3, $15, 0, 0, 0, 0
        // spritePiece -4, -$14, 3, 2, $1B, 0, 0, 0, 0
        // spritePiece -4, -4, 3, 3, $21, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 2, 3, 0x15, false, false, 0, false),
                new SpriteMappingPiece(   -4, -0x14, 3, 2, 0x1B, false, false, 0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 3, 0x21, false, false, 0, false)
        )));

        // Frame 2: M_Newt_Fires - Firing with mouth open (3 pieces)
        // spritePiece -$14, -$14, 2, 3, $2A, 0, 0, 0, 0
        // spritePiece -4, -$14, 3, 2, $1B, 0, 0, 0, 0
        // spritePiece -4, -4, 3, 3, $21, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 2, 3, 0x2A, false, false, 0, false),
                new SpriteMappingPiece(   -4, -0x14, 3, 2, 0x1B, false, false, 0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 3, 0x21, false, false, 0, false)
        )));

        // Frame 3: M_Newt_Drop1 - Dropping phase 1 (4 pieces)
        // spritePiece -$14, -$14, 2, 3, $30, 0, 0, 0, 0
        // spritePiece -4, -$14, 3, 2, $1B, 0, 0, 0, 0
        // spritePiece -4, -4, 3, 2, $36, 0, 0, 0, 0
        // spritePiece $C, $C, 1, 1, $3C, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x14, 2, 3, 0x30, false, false, 0, false),
                new SpriteMappingPiece(   -4, -0x14, 3, 2, 0x1B, false, false, 0, false),
                new SpriteMappingPiece(   -4,    -4, 3, 2, 0x36, false, false, 0, false),
                new SpriteMappingPiece(  0xC,   0xC, 1, 1, 0x3C, false, false, 0, false)
        )));

        // Frame 4: M_Newt_Drop2 - Dropping phase 2 (3 pieces)
        // spritePiece -$14, -$C, 4, 2, $3D, 0, 0, 0, 0
        // spritePiece $C, -4, 1, 1, $20, 0, 0, 0, 0
        // spritePiece -4, 4, 3, 1, $45, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0xC, 4, 2, 0x3D, false, false, 0, false),
                new SpriteMappingPiece(  0xC,    -4, 1, 1, 0x20, false, false, 0, false),
                new SpriteMappingPiece(   -4,     4, 3, 1, 0x45, false, false, 0, false)
        )));

        // Frame 5: M_Newt_Drop3 - Dropping phase 3 (2 pieces)
        // spritePiece -$14, -8, 4, 2, $48, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $50, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0x8, 4, 2, 0x48, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x50, false, false, 0, false)
        )));

        // Frame 6: M_Newt_Fly1a - Flying variant 1, wing up (3 pieces)
        // spritePiece -$14, -8, 4, 2, $48, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $50, 0, 0, 0, 0
        // spritePiece $14, -2, 1, 1, $52, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0x8, 4, 2, 0x48, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x50, false, false, 0, false),
                new SpriteMappingPiece( 0x14,    -2, 1, 1, 0x52, false, false, 0, false)
        )));

        // Frame 7: M_Newt_Fly1b - Flying variant 1, wing down (3 pieces)
        // spritePiece -$14, -8, 4, 2, $48, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $50, 0, 0, 0, 0
        // spritePiece $14, -2, 2, 1, $53, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0x8, 4, 2, 0x48, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x50, false, false, 0, false),
                new SpriteMappingPiece( 0x14,    -2, 2, 1, 0x53, false, false, 0, false)
        )));

        // Frame 8: M_Newt_Fly2a - Flying variant 2, wing up (3 pieces, pal 3 + pri on wing)
        // spritePiece -$14, -8, 4, 2, $48, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $50, 0, 0, 0, 0
        // spritePiece $14, -2, 1, 1, $52, 0, 0, 3, 1
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0x8, 4, 2, 0x48, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x50, false, false, 0, false),
                new SpriteMappingPiece( 0x14,    -2, 1, 1, 0x52, false, false, 3, true)
        )));

        // Frame 9: M_Newt_Fly2b - Flying variant 2, wing down (3 pieces, pal 3 + pri on wing)
        // spritePiece -$14, -8, 4, 2, $48, 0, 0, 0, 0
        // spritePiece $C, -8, 1, 2, $50, 0, 0, 0, 0
        // spritePiece $14, -2, 2, 1, $53, 0, 0, 3, 1
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14,  -0x8, 4, 2, 0x48, false, false, 0, false),
                new SpriteMappingPiece(  0xC,  -0x8, 1, 2, 0x50, false, false, 0, false),
                new SpriteMappingPiece( 0x14,    -2, 2, 1, 0x53, false, false, 3, true)
        )));

        // Frame 10: M_Newt_Blank - Empty frame (no pieces)
        frames.add(new SpriteMappingFrame(List.of()));

        return frames;
    }

    /**
     * Loads Buzz Bomber art (Nem_Buzz) and creates sprite sheets for the Buzz Bomber,
     * its missile, and the missile dissolve effect.
     * All three share the same Nemesis-compressed art tile set (ArtTile_Buzz_Bomber = $444).
     * Missile dissolve uses a separate VRAM region (ArtTile_Missile_Disolve = $41C),
     * but in practice shares the same ROM art with different tile offsets in mappings.
     */
    private void loadBuzzBomberArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_BUZZ_BOMBER_ADDR, "BuzzBomber");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Buzz Bomber art");
            return;
        }

        // Buzz Bomber body: palette 0, art tile $444
        List<SpriteMappingFrame> buzzMappings = createBuzzBomberMappings();
        ObjectSpriteSheet buzzSheet = new ObjectSpriteSheet(patterns, buzzMappings, 0, 1);
        registerSheet(ObjectArtKeys.BUZZ_BOMBER, buzzSheet);

        // Missile: palette 1, shares same art tiles (base $444, missile tiles at offset $24+)
        List<SpriteMappingFrame> missileMappings = createBuzzBomberMissileMappings();
        ObjectSpriteSheet missileSheet = new ObjectSpriteSheet(patterns, missileMappings, 1, 1);
        registerSheet(ObjectArtKeys.BUZZ_BOMBER_MISSILE, missileSheet);

        // Missile dissolve: palette 0
        // In the original ROM, dissolve references ArtTile_Missile_Disolve ($41C) which is
        // a separate VRAM region. However, $41C is marked as "Unused" in Constants.asm and
        // no PLC ever loads art to that address. Object 24 itself is marked "unused?" in the
        // disassembly. The dissolve effect was likely cut during development.
        // We reuse buzz bomber patterns as a visual stand-in since the original has no art loaded.
        List<SpriteMappingFrame> dissolveMappings = createBuzzBomberMissileDisolveMappings();
        ObjectSpriteSheet dissolveSheet = new ObjectSpriteSheet(patterns, dissolveMappings, 0, 1);
        registerSheet(ObjectArtKeys.BUZZ_BOMBER_MISSILE_DISSOLVE, dissolveSheet);
    }

    /**
     * Creates Buzz Bomber sprite mappings from S1 disassembly Map_Buzz_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.Fly1): Hovering, wings up (6 pieces)
     * Frame 1 (.Fly2): Hovering, wings down (6 pieces)
     * Frame 2 (.Fly3): Flying, wings up + exhaust (7 pieces)
     * Frame 3 (.Fly4): Flying, wings down + exhaust (7 pieces)
     * Frame 4 (.Fire1): Firing, wings up + missile pod (6 pieces)
     * Frame 5 (.Fire2): Firing, wings down (4 pieces - 2 wing pieces after end marker are unused)
     */
    private List<SpriteMappingFrame> createBuzzBomberMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .Fly1 - hovering, wings up
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x0C, 3, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x0C, 3, 2, 0x0F, false, false, 0, false),
                new SpriteMappingPiece(-0x18,  0x04, 3, 1, 0x15, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x04, 2, 1, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x0F, 3, 1, 0x1A, false, false, 0, false),
                new SpriteMappingPiece(  0x04, -0x0F, 2, 1, 0x1D, false, false, 0, false)
        )));

        // Frame 1: .Fly2 - hovering, wings down
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x0C, 3, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x0C, 3, 2, 0x0F, false, false, 0, false),
                new SpriteMappingPiece(-0x18,  0x04, 3, 1, 0x15, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x04, 2, 1, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x0C, 3, 1, 0x1F, false, false, 0, false),
                new SpriteMappingPiece(  0x04, -0x0C, 2, 1, 0x22, false, false, 0, false)
        )));

        // Frame 2: .Fly3 - flying, wings up + small exhaust
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(  0x0C,  0x04, 1, 1, 0x30, false, false, 0, false),
                new SpriteMappingPiece(-0x18, -0x0C, 3, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x0C, 3, 2, 0x0F, false, false, 0, false),
                new SpriteMappingPiece(-0x18,  0x04, 3, 1, 0x15, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x04, 2, 1, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x0F, 3, 1, 0x1A, false, false, 0, false),
                new SpriteMappingPiece(  0x04, -0x0F, 2, 1, 0x1D, false, false, 0, false)
        )));

        // Frame 3: .Fly4 - flying, wings down + large exhaust
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(  0x0C,  0x04, 2, 1, 0x31, false, false, 0, false),
                new SpriteMappingPiece(-0x18, -0x0C, 3, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x0C, 3, 2, 0x0F, false, false, 0, false),
                new SpriteMappingPiece(-0x18,  0x04, 3, 1, 0x15, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x04, 2, 1, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x0C, 3, 1, 0x1F, false, false, 0, false),
                new SpriteMappingPiece(  0x04, -0x0C, 2, 1, 0x22, false, false, 0, false)
        )));

        // Frame 4: .Fire1 - firing, wings up + missile pod
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x0C, 4, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x04, 4, 1, 0x08, false, false, 0, false),
                new SpriteMappingPiece(  0x0C,  0x04, 1, 1, 0x0C, false, false, 0, false),
                new SpriteMappingPiece( -0x0C,  0x0C, 2, 1, 0x0D, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x0F, 3, 1, 0x1A, false, false, 0, false),
                new SpriteMappingPiece(  0x04, -0x0F, 2, 1, 0x1D, false, false, 0, false)
        )));

        // Frame 5: .Fire2 - firing, wings down (4 pieces; wing pieces after end marker are unused)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x0C, 4, 2, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x04, 4, 1, 0x08, false, false, 0, false),
                new SpriteMappingPiece(  0x0C,  0x04, 1, 1, 0x0C, false, false, 0, false),
                new SpriteMappingPiece( -0x0C,  0x0C, 2, 1, 0x0D, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Creates Buzz Bomber missile sprite mappings from S1 disassembly Map_Missile_internal.
     * <p>
     * Frame 0 (.Flare1): Flare pulse 1 (2x2 at tile $24)
     * Frame 1 (.Flare2): Flare pulse 2 (2x2 at tile $28)
     * Frame 2 (.Ball1):  Missile ball 1 (2x2 at tile $2C)
     * Frame 3 (.Ball2):  Missile ball 2 (2x2 at tile $33)
     */
    private List<SpriteMappingFrame> createBuzzBomberMissileMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .Flare1
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x24, false, false, 0, false)
        )));

        // Frame 1: .Flare2
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x28, false, false, 0, false)
        )));

        // Frame 2: .Ball1
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x2C, false, false, 0, false)
        )));

        // Frame 3: .Ball2
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x33, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Creates Buzz Bomber missile dissolve sprite mappings from S1 disassembly
     * Map_MisDissolve_internal. 4 frames of 3x3 tiles (24x24 px) centered at (-$C, -$C).
     * <p>
     * Frame 0: Dissolve step 1 (tile $00)
     * Frame 1: Dissolve step 2 (tile $09)
     * Frame 2: Dissolve step 3 (tile $12)
     * Frame 3: Dissolve step 4 (tile $1B)
     */
    private List<SpriteMappingFrame> createBuzzBomberMissileDisolveMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 3, 3, 0x00, false, false, 0, false)
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 3, 3, 0x09, false, false, 0, false)
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 3, 3, 0x12, false, false, 0, false)
        )));
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 3, 3, 0x1B, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Registers the platform sprite sheet using level tile patterns.
     * Must be called AFTER the level is loaded since platforms use zone tileset art
     * (ArtTile_Level) rather than dedicated Nemesis-compressed object art.
     * <p>
     * Zone-specific mappings from disassembly:
     * <ul>
     *   <li>GHZ: 2 frames (small 64x24, large column 64x140)</li>
     *   <li>SYZ: 1 frame (64x20)</li>
     *   <li>SLZ: 1 frame (64x16)</li>
     * </ul>
     *
     * @param level     The loaded level to extract patterns from
     * @param zoneIndex The current zone index
     */
    public void registerPlatformSheet(Level level, int zoneIndex) {
        if (level == null) {
            return;
        }

        List<SpriteMappingFrame> mappings;
        int maxTileNeeded;

        switch (zoneIndex) {
            case Sonic1Constants.ZONE_SYZ -> {
                mappings = createPlatformMappingsSyz();
                // Highest tile: 0x55 + (3*4) = 0x61
                maxTileNeeded = 0x61;
            }
            case Sonic1Constants.ZONE_SLZ -> {
                mappings = createPlatformMappingsSlz();
                // Highest tile: 0x21 + (4*4) = 0x31
                maxTileNeeded = 0x31;
            }
            default -> {
                // GHZ (and any other zone with platforms)
                mappings = createPlatformMappingsGhz();
                // Highest tile: 0xD5 + (4*4) = 0xE5
                maxTileNeeded = 0xE5;
            }
        }

        int patternCount = level.getPatternCount();
        int copyCount = Math.min(patternCount, maxTileNeeded);
        if (copyCount == 0) {
            LOGGER.warning("No level patterns available for platform art");
            return;
        }
        Pattern[] patterns = new Pattern[copyCount];
        for (int i = 0; i < copyCount; i++) {
            patterns[i] = level.getPattern(i);
        }

        // Palette line 2 (make_art_tile(ArtTile_Level, 2, 0))
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.PLATFORM, sheet);
    }

    /**
     * GHZ platform mappings from docs/s1disasm/_maps/Platforms (GHZ).asm.
     * Frame 0 (.small): 64x24 platform (4 pieces)
     * Frame 1 (.large): 64x140 column platform (10 pieces)
     */
    private List<SpriteMappingFrame> createPlatformMappingsGhz() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (.small): 4 pieces
        // spritePiece -$20, -$C, 3, 4, $3B, 0, 0, 0, 0
        // spritePiece   -8, -$C, 2, 4, $3F, 0, 0, 0, 0  (NOTE: not hflipped in asm)
        // spritePiece    8, -$C, 2, 4, $3F, 0, 0, 0, 0  (NOTE: not hflipped in asm)
        // spritePiece  $18, -$C, 1, 4, $47, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x0C, 3, 4, 0x3B, false, false, 0, false),
                new SpriteMappingPiece(-0x08, -0x0C, 2, 4, 0x3F, false, false, 0, false),
                new SpriteMappingPiece(0x08, -0x0C, 2, 4, 0x3F, false, false, 0, false),
                new SpriteMappingPiece(0x18, -0x0C, 1, 4, 0x47, false, false, 0, false)
        )));

        // Frame 1 (.large): 10 pieces - left column (5) + right column hflipped (5)
        // Left half:
        // spritePiece -$20,  -$C, 4, 4, $C5, 0, 0, 0, 0
        // spritePiece -$20,   $4, 4, 4, $D5, 0, 0, 0, 0
        // spritePiece -$20,  $24, 4, 4, $D5, 0, 0, 0, 0
        // spritePiece -$20,  $44, 4, 4, $D5, 0, 0, 0, 0
        // spritePiece -$20,  $64, 4, 4, $D5, 0, 0, 0, 0
        // Right half (h-flipped):
        // spritePiece    0,  -$C, 4, 4, $C5, 1, 0, 0, 0
        // spritePiece    0,   $4, 4, 4, $D5, 1, 0, 0, 0
        // spritePiece    0,  $24, 4, 4, $D5, 1, 0, 0, 0
        // spritePiece    0,  $44, 4, 4, $D5, 1, 0, 0, 0
        // spritePiece    0,  $64, 4, 4, $D5, 1, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x0C, 4, 4, 0xC5, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x04, 4, 4, 0xD5, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x24, 4, 4, 0xD5, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x44, 4, 4, 0xD5, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x64, 4, 4, 0xD5, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x0C, 4, 4, 0xC5, true, false, 0, false),
                new SpriteMappingPiece(0x00, 0x04, 4, 4, 0xD5, true, false, 0, false),
                new SpriteMappingPiece(0x00, 0x24, 4, 4, 0xD5, true, false, 0, false),
                new SpriteMappingPiece(0x00, 0x44, 4, 4, 0xD5, true, false, 0, false),
                new SpriteMappingPiece(0x00, 0x64, 4, 4, 0xD5, true, false, 0, false)
        )));

        return frames;
    }

    /**
     * SYZ platform mappings from docs/s1disasm/_maps/Platforms (SYZ).asm.
     * Frame 0 (.platform): 64x20 platform (3 pieces)
     */
    private List<SpriteMappingFrame> createPlatformMappingsSyz() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // spritePiece -$20, -$A, 3, 4, $49, 0, 0, 0, 0
        // spritePiece   -8, -$A, 2, 4, $51, 0, 0, 0, 0
        // spritePiece    8, -$A, 3, 4, $55, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x0A, 3, 4, 0x49, false, false, 0, false),
                new SpriteMappingPiece(-0x08, -0x0A, 2, 4, 0x51, false, false, 0, false),
                new SpriteMappingPiece(0x08, -0x0A, 3, 4, 0x55, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * SLZ platform mappings from docs/s1disasm/_maps/Platforms (SLZ).asm.
     * Frame 0 (.platform): 64x16 platform (2 pieces)
     */
    private List<SpriteMappingFrame> createPlatformMappingsSlz() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // spritePiece -$20, -8, 4, 4, $21, 0, 0, 0, 0
        // spritePiece    0, -8, 4, 4, $21, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x08, 4, 4, 0x21, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x08, 4, 4, 0x21, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Registers the collapsing ledge sprite sheet using level tile patterns.
     * Must be called AFTER the level is loaded since ledges use zone tileset art
     * (make_art_tile(ArtTile_Level,2,0)) — same as platforms.
     * <p>
     * GHZ only. 4 frames from Map_Ledge:
     * <ul>
     *   <li>Frame 0 (.left): Left-facing ledge, 16 pieces</li>
     *   <li>Frame 1 (.right): Right-facing ledge, 16 pieces</li>
     *   <li>Frame 2 (.leftsmash): Left-facing fragments, 23 pieces</li>
     *   <li>Frame 3 (.rightsmash): Right-facing fragments, 25 pieces</li>
     * </ul>
     *
     * @param level     The loaded level to extract patterns from
     * @param zoneIndex The current zone index
     */
    public void registerCollapsingLedgeSheet(Level level, int zoneIndex) {
        if (level == null || zoneIndex != Sonic1Constants.ZONE_GHZ) {
            return;
        }

        List<SpriteMappingFrame> mappings = createCollapsingLedgeMappings();

        // Highest tile: 0xC1 + (2*2) = 0xC5
        int maxTileNeeded = 0xC5;
        int patternCount = level.getPatternCount();
        int copyCount = Math.min(patternCount, maxTileNeeded);
        if (copyCount == 0) {
            LOGGER.warning("No level patterns available for collapsing ledge art");
            return;
        }
        Pattern[] patterns = new Pattern[copyCount];
        for (int i = 0; i < copyCount; i++) {
            patterns[i] = level.getPattern(i);
        }

        // Palette line 2 (make_art_tile(ArtTile_Level, 2, 0))
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
        registerSheet(ObjectArtKeys.COLLAPSING_LEDGE, sheet);
    }

    /**
     * Collapsing ledge mappings from docs/s1disasm/_maps/Collapsing Ledge.asm (Map_Ledge_internal).
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     */
    private List<SpriteMappingFrame> createCollapsingLedgeMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0 (.left): ledge facing left, 16 pieces
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x10, -0x38, 4, 3, 0x57, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x30, 4, 2, 0x63, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x20, 4, 2, 0x6B, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x20, 4, 2, 0x73, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x28, 2, 3, 0x7B, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x28, 2, 3, 0x81, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x10, 4, 2, 0x87, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x8F, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x97, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x10, 2, 2, 0x9B, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x00, 4, 2, 0x9F, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x00, 2, 2, 0xA7, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x00, 4, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(-0x30, 0x00, 2, 2, 0xB3, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x10, 4, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x10, 2, 2, 0xB7, false, false, 0, false)
        )));

        // Frame 1 (.right): ledge facing right, 16 pieces
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x10, -0x38, 4, 3, 0x57, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x30, 4, 2, 0x63, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x20, 4, 2, 0x6B, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x20, 4, 2, 0x73, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x28, 2, 3, 0x7B, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x28, 2, 3, 0xBB, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x10, 4, 2, 0x87, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x8F, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x97, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x10, 2, 2, 0xC1, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x00, 4, 2, 0x9F, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x00, 2, 2, 0xA7, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x00, 4, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(-0x30, 0x00, 2, 2, 0xB7, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x10, 4, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x10, 2, 2, 0xB7, false, false, 0, false)
        )));

        // Frame 2 (.leftsmash): left-facing fragments, 23 pieces
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x20, -0x38, 2, 3, 0x5D, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x38, 2, 3, 0x57, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x30, 2, 2, 0x67, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x30, 2, 2, 0x63, false, false, 0, false),
                new SpriteMappingPiece(0x20, -0x20, 2, 2, 0x6F, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x20, 2, 2, 0x6B, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x20, 2, 2, 0x77, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x20, 2, 2, 0x73, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x28, 2, 3, 0x7B, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x28, 2, 3, 0x81, false, false, 0, false),
                new SpriteMappingPiece(0x20, -0x10, 2, 2, 0x8B, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x10, 2, 2, 0x87, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x10, 2, 2, 0x93, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x8F, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x97, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x10, 2, 2, 0x9B, false, false, 0, false),
                new SpriteMappingPiece(0x20, 0x00, 2, 2, 0x8B, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x00, 2, 2, 0x8B, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x00, 2, 2, 0xA7, false, false, 0, false),
                new SpriteMappingPiece(-0x10, 0x00, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x00, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(-0x30, 0x00, 2, 2, 0xB3, false, false, 0, false),
                new SpriteMappingPiece(0x20, 0x10, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x10, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x10, 2, 2, 0xB7, false, false, 0, false)
        )));

        // Frame 3 (.rightsmash): right-facing fragments, 25 pieces
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x20, -0x38, 2, 3, 0x5D, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x38, 2, 3, 0x57, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x30, 2, 2, 0x67, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x30, 2, 2, 0x63, false, false, 0, false),
                new SpriteMappingPiece(0x20, -0x20, 2, 2, 0x6F, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x20, 2, 2, 0x6B, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x20, 2, 2, 0x77, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x20, 2, 2, 0x73, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x28, 2, 3, 0x7B, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x28, 2, 3, 0xBB, false, false, 0, false),
                new SpriteMappingPiece(0x20, -0x10, 2, 2, 0x8B, false, false, 0, false),
                new SpriteMappingPiece(0x10, -0x10, 2, 2, 0x87, false, false, 0, false),
                new SpriteMappingPiece(0x00, -0x10, 2, 2, 0x93, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 2, 2, 0x8F, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -0x10, 2, 2, 0x97, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -0x10, 2, 2, 0xC1, false, false, 0, false),
                new SpriteMappingPiece(0x20, 0x00, 2, 2, 0x8B, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x00, 2, 2, 0x8B, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x00, 2, 2, 0xA7, false, false, 0, false),
                new SpriteMappingPiece(-0x10, 0x00, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(-0x20, 0x00, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(-0x30, 0x00, 2, 2, 0xB7, false, false, 0, false),
                new SpriteMappingPiece(0x20, 0x10, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(0x10, 0x10, 2, 2, 0xAB, false, false, 0, false),
                new SpriteMappingPiece(0x00, 0x10, 2, 2, 0xB7, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Loads Giant Ring art (Art_BigRing) - uncompressed 98-tile ring sprite.
     * Mappings from docs/s1disasm/_maps/Giant Ring.asm (Map_GRing_internal).
     * 4 frames: front view, angled, edge-on, angled reverse.
     */
    private void loadGiantRingArt(Rom rom) {
        Pattern[] patterns = loadUncompressedPatterns(rom,
                Sonic1Constants.ART_UNC_GIANT_RING_ADDR,
                Sonic1Constants.ART_UNC_GIANT_RING_SIZE, "GiantRing");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Giant Ring art");
            return;
        }

        List<SpriteMappingFrame> mappings = createGiantRingMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.GIANT_RING, sheet);
    }

    /**
     * Creates Giant Ring sprite mappings from S1 disassembly Map_GRing_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0: Ring front-on (10 pieces)
     * Frame 1: Ring at angle (8 pieces)
     * Frame 2: Ring perpendicular/edge-on (4 pieces)
     * Frame 3: Ring at angle reverse - frame 1 with H-flip (8 pieces)
     */
    private List<SpriteMappingFrame> createGiantRingMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: byte_9FDA - ring front view (10 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x20, 3, 1, 0x00, false, false, 1, false),
                new SpriteMappingPiece( 0x00, -0x20, 3, 1, 0x03, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x18, 4, 1, 0x06, false, false, 1, false),
                new SpriteMappingPiece( 0x00, -0x18, 4, 1, 0x0A, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x10, 2, 4, 0x0E, false, false, 1, false),
                new SpriteMappingPiece( 0x10, -0x10, 2, 4, 0x16, false, false, 1, false),
                new SpriteMappingPiece(-0x20,  0x10, 4, 1, 0x1E, false, false, 1, false),
                new SpriteMappingPiece( 0x00,  0x10, 4, 1, 0x22, false, false, 1, false),
                new SpriteMappingPiece(-0x18,  0x18, 3, 1, 0x26, false, false, 1, false),
                new SpriteMappingPiece( 0x00,  0x18, 3, 1, 0x29, false, false, 1, false)
        )));

        // Frame 1: byte_A00D - ring at angle (8 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 4, 1, 0x2C, false, false, 1, false),
                new SpriteMappingPiece(-0x18, -0x18, 3, 1, 0x30, false, false, 1, false),
                new SpriteMappingPiece( 0x00, -0x18, 3, 2, 0x33, false, false, 1, false),
                new SpriteMappingPiece(-0x18, -0x10, 2, 4, 0x39, false, false, 1, false),
                new SpriteMappingPiece( 0x08, -0x08, 2, 2, 0x41, false, false, 1, false),
                new SpriteMappingPiece( 0x00,  0x08, 3, 2, 0x45, false, false, 1, false),
                new SpriteMappingPiece(-0x18,  0x10, 3, 1, 0x4B, false, false, 1, false),
                new SpriteMappingPiece(-0x10,  0x18, 4, 1, 0x4E, false, false, 1, false)
        )));

        // Frame 2: byte_A036 - ring perpendicular/edge-on (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x20, 2, 4, 0x52, false, false, 1, false),
                new SpriteMappingPiece( 0x04, -0x20, 1, 4, 0x52, true,  false, 1, false),
                new SpriteMappingPiece(-0x0C,  0x00, 2, 4, 0x5A, false, false, 1, false),
                new SpriteMappingPiece( 0x04,  0x00, 1, 4, 0x5A, true,  false, 1, false)
        )));

        // Frame 3: byte_A04B - ring at angle reverse (8 pieces, all H-flipped)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 4, 1, 0x2C, true, false, 1, false),
                new SpriteMappingPiece( 0x00, -0x18, 3, 1, 0x30, true, false, 1, false),
                new SpriteMappingPiece(-0x18, -0x18, 3, 2, 0x33, true, false, 1, false),
                new SpriteMappingPiece( 0x08, -0x10, 2, 4, 0x39, true, false, 1, false),
                new SpriteMappingPiece(-0x18, -0x08, 2, 2, 0x41, true, false, 1, false),
                new SpriteMappingPiece(-0x18,  0x08, 3, 2, 0x45, true, false, 1, false),
                new SpriteMappingPiece( 0x00,  0x10, 3, 1, 0x4B, true, false, 1, false),
                new SpriteMappingPiece(-0x10,  0x18, 4, 1, 0x4E, true, false, 1, false)
        )));

        return frames;
    }

    /**
     * Loads Giant Ring Flash art (Nem_BigFlash) - Nemesis-compressed flash sprite.
     * Mappings from docs/s1disasm/_maps/Ring Flash.asm (Map_Flash_internal).
     * 8 frames of expanding flash effect.
     */
    private void loadGiantRingFlashArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_GIANT_RING_FLASH_ADDR, "GiantRingFlash");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load Giant Ring Flash art");
            return;
        }

        List<SpriteMappingFrame> mappings = createGiantRingFlashMappings();
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.GIANT_RING_FLASH, sheet);
    }

    /**
     * Creates Giant Ring Flash sprite mappings from S1 disassembly Map_Flash_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * 8 frames of expanding white flash. Frames 0-2 expand right, frame 3 is
     * full symmetric, frames 4-6 contract left, frame 7 is the final flash.
     */
    private List<SpriteMappingFrame> createGiantRingFlashMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: byte_A084 - small flash (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece( 0x00, -0x20, 4, 4, 0x00, false, false, 1, false),
                new SpriteMappingPiece( 0x00,  0x00, 4, 4, 0x00, false, true,  1, false)
        )));

        // Frame 1: byte_A08F - wider flash (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 4, 4, 0x10, false, false, 1, false),
                new SpriteMappingPiece( 0x10, -0x20, 2, 4, 0x20, false, false, 1, false),
                new SpriteMappingPiece(-0x10,  0x00, 4, 4, 0x10, false, true,  1, false),
                new SpriteMappingPiece( 0x10,  0x00, 2, 4, 0x20, false, true,  1, false)
        )));

        // Frame 2: byte_A0A4 - large flash (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x20, 4, 4, 0x28, false, false, 1, false),
                new SpriteMappingPiece( 0x08, -0x20, 3, 4, 0x38, false, false, 1, false),
                new SpriteMappingPiece(-0x18,  0x00, 4, 4, 0x28, false, true,  1, false),
                new SpriteMappingPiece( 0x08,  0x00, 3, 4, 0x38, false, true,  1, false)
        )));

        // Frame 3: byte_A0B9 - full width flash, symmetric (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x20, 4, 4, 0x34, true,  false, 1, false),
                new SpriteMappingPiece( 0x00, -0x20, 4, 4, 0x34, false, false, 1, false),
                new SpriteMappingPiece(-0x20,  0x00, 4, 4, 0x34, true,  true,  1, false),
                new SpriteMappingPiece( 0x00,  0x00, 4, 4, 0x34, false, true,  1, false)
        )));

        // Frame 4: byte_A0CE - contracting left (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x20, 3, 4, 0x38, true,  false, 1, false),
                new SpriteMappingPiece(-0x08, -0x20, 4, 4, 0x28, true,  false, 1, false),
                new SpriteMappingPiece(-0x20,  0x00, 3, 4, 0x38, true,  true,  1, false),
                new SpriteMappingPiece(-0x08,  0x00, 4, 4, 0x28, true,  true,  1, false)
        )));

        // Frame 5: byte_A0E3 - contracting further (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x20, 2, 4, 0x20, true,  false, 1, false),
                new SpriteMappingPiece(-0x10, -0x20, 4, 4, 0x10, true,  false, 1, false),
                new SpriteMappingPiece(-0x20,  0x00, 2, 4, 0x20, true,  true,  1, false),
                new SpriteMappingPiece(-0x10,  0x00, 4, 4, 0x10, true,  true,  1, false)
        )));

        // Frame 6: byte_A0F8 - small flash left (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x20, 4, 4, 0x00, true,  false, 1, false),
                new SpriteMappingPiece(-0x20,  0x00, 4, 4, 0x00, true,  true,  1, false)
        )));

        // Frame 7: byte_A103 - final flash, 4-way symmetric (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x20, 4, 4, 0x44, false, false, 1, false),
                new SpriteMappingPiece( 0x00, -0x20, 4, 4, 0x44, true,  false, 1, false),
                new SpriteMappingPiece(-0x20,  0x00, 4, 4, 0x44, false, true,  1, false),
                new SpriteMappingPiece( 0x00,  0x00, 4, 4, 0x44, true,  true,  1, false)
        )));

        return frames;
    }

    /**
     * Loads swinging platform art for the current zone.
     * <p>
     * Zone-specific art from Pattern Load Cues:
     * <ul>
     *   <li>GHZ: Nem_Swing (ArtTile $380, palette 2) + Nem_Ball (ArtTile $3AA, palette 2)</li>
     *   <li>MZ:  Nem_Swing (ArtTile $380, palette 2)</li>
     *   <li>SLZ: Nem_SlzSwing (ArtTile $3DC, palette 2)</li>
     *   <li>SBZ: Nem_SyzSpike1 (ArtTile $391, palette 0)</li>
     * </ul>
     */
    private void loadSwingingPlatformArt(Rom rom, int zoneIndex) {
        // GHZ/MZ swinging platform (Nem_Swing)
        if (zoneIndex == Sonic1Constants.ZONE_GHZ || zoneIndex == Sonic1Constants.ZONE_MZ) {
            Pattern[] patterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_SWING_ADDR, "SwingGHZ");
            if (patterns.length > 0) {
                List<SpriteMappingFrame> mappings = createSwingGhzMappings();
                // make_art_tile(ArtTile_GHZ_MZ_Swing, 2, 0) — palette line 2
                ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
                registerSheet(ObjectArtKeys.SWING_GHZ, sheet);
            }
        }

        // GHZ giant ball variant (Nem_Ball) — subtype $1X
        if (zoneIndex == Sonic1Constants.ZONE_GHZ) {
            Pattern[] patterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_GIANT_BALL_ADDR, "GiantBall");
            if (patterns.length > 0) {
                List<SpriteMappingFrame> mappings = createGiantBallMappings();
                // make_art_tile(ArtTile_GHZ_Giant_Ball, 2, 0) — palette line 2
                ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
                registerSheet(ObjectArtKeys.SWING_GIANT_BALL, sheet);
            }
        }

        // SLZ swinging platform (Nem_SlzSwing)
        if (zoneIndex == Sonic1Constants.ZONE_SLZ) {
            Pattern[] patterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_SLZ_SWING_ADDR, "SwingSLZ");
            if (patterns.length > 0) {
                List<SpriteMappingFrame> mappings = createSwingSlzMappings();
                // make_art_tile(ArtTile_SLZ_Swing, 2, 0) — palette line 2
                ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 2, 1);
                registerSheet(ObjectArtKeys.SWING_SLZ, sheet);
            }
        }

        // SBZ spiked ball on chain (Nem_SyzSpike1)
        if (zoneIndex == Sonic1Constants.ZONE_SBZ) {
            Pattern[] patterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_SBZ_SPIKED_BALL_ADDR, "SBZSpikedBall");
            if (patterns.length > 0) {
                List<SpriteMappingFrame> mappings = createSbzBallMappings();
                // make_art_tile(ArtTile_SBZ_Swing, 0, 0) — palette line 0
                ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
                registerSheet(ObjectArtKeys.SWING_SBZ_BALL, sheet);
            }
        }
    }

    /**
     * GHZ/MZ swinging platform mappings from docs/s1disasm/_maps/Swinging Platforms (GHZ).asm.
     * <p>
     * Frame 0 (.block):  Platform — 2 pieces (48x16)
     * Frame 1 (.chain):  Chain link — 1 piece (16x16)
     * Frame 2 (.anchor): Anchor point — 1 piece (16x16)
     */
    private List<SpriteMappingFrame> createSwingGhzMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .block — platform (2 pieces of 3x2 tiles)
        // spritePiece -$18, -8, 3, 2, 4, 0, 0, 0, 0
        // spritePiece    0, -8, 3, 2, 4, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -8, 3, 2, 4, false, false, 0, false),
                new SpriteMappingPiece(    0, -8, 3, 2, 4, false, false, 0, false)
        )));

        // Frame 1: .chain — chain link (1 piece of 2x2 tiles)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0, false)
        )));

        // Frame 2: .anchor — anchor point (1 piece of 2x2 tiles)
        // spritePiece -8, -8, 2, 2, $A, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0xA, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * SLZ swinging platform mappings from docs/s1disasm/_maps/Swinging Platforms (SLZ).asm.
     * <p>
     * Frame 0 (.block):  Platform — 8 pieces (larger 64x32 platform)
     * Frame 1 (.chain):  Chain link — 1 piece (16x16, palette 2)
     * Frame 2 (.anchor): Anchor point — 1 piece (16x16)
     */
    private List<SpriteMappingFrame> createSwingSlzMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .block — SLZ platform (8 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x10, 4, 4,    4, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x10, 4, 4,    4, true,  false, 0, false),
                new SpriteMappingPiece(-0x30, -0x10, 2, 2, 0x14, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -0x10, 2, 2, 0x14, true,  false, 0, false),
                new SpriteMappingPiece(-0x20,  0x10, 2, 1, 0x18, false, false, 0, false),
                new SpriteMappingPiece( 0x10,  0x10, 2, 1, 0x18, true,  false, 0, false),
                new SpriteMappingPiece(   -8,  0x10, 1, 2, 0x1A, false, false, 0, false),
                new SpriteMappingPiece(    0,  0x10, 1, 2, 0x1A, true,  false, 0, false)
        )));

        // Frame 1: .chain — chain link (1 piece, palette 2)
        // spritePiece -8, -8, 2, 2, 0, 0, 0, 2, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 2, false)
        )));

        // Frame 2: .anchor — anchor (1 piece)
        // spritePiece -8, -8, 2, 2, $1C, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x1C, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * SBZ spiked ball mappings from docs/s1disasm/_maps/Big Spiked Ball.asm.
     * <p>
     * Frame 0 (.ball):   Spiked ball — 5 pieces (48x48 with spikes)
     * Frame 1 (.chain):  Chain link — 1 piece (16x16)
     * Frame 2 (.anchor): Anchor — 2 pieces (32x32, v-flip pair)
     */
    private List<SpriteMappingFrame> createSbzBallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .ball — spiked ball (5 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(   -8, -0x18, 2, 1,    0, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 4, 4,    2, false, false, 0, false),
                new SpriteMappingPiece(-0x18,    -8, 1, 2, 0x12, false, false, 0, false),
                new SpriteMappingPiece( 0x10,    -8, 1, 2, 0x14, false, false, 0, false),
                new SpriteMappingPiece(   -8,  0x10, 2, 1, 0x16, false, false, 0, false)
        )));

        // Frame 1: .chain — chain link (1 piece)
        // spritePiece -8, -8, 2, 2, $20, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x20, false, false, 0, false)
        )));

        // Frame 2: .anchor — anchor (2 pieces, v-flip pair)
        // spritePiece -$10,   -8, 4, 2, $18, 0, 0, 0, 0
        // spritePiece -$10, -$18, 4, 2, $18, 0, 1, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10,    -8, 4, 2, 0x18, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 2, 0x18, false, true,  0, false)
        )));

        return frames;
    }

    /**
     * GHZ giant ball mappings from docs/s1disasm/_maps/GHZ Ball.asm.
     * <p>
     * Frame 0 (.shiny):  Ball with shine — 6 pieces (48x48)
     * Frame 1 (.check1): Checkered ball frame 1 — 4 pieces
     * Frame 2 (.check2): Checkered ball frame 2 — 4 pieces
     * Frame 3 (.check3): Checkered ball frame 3 — 4 pieces
     */
    private List<SpriteMappingFrame> createGiantBallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .shiny — shine highlight + 4-way symmetric ball
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 2, 1, 0x24, false, false, 0, false),
                new SpriteMappingPiece(-0x10,    -8, 2, 1, 0x24, false, true,  0, false),
                new SpriteMappingPiece(-0x18, -0x18, 3, 3,    0, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x18, 3, 3,    0, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,     0, 3, 3,    0, false, true,  0, false),
                new SpriteMappingPiece(    0,     0, 3, 3,    0, true,  true,  0, false)
        )));

        // Frame 1: .check1 — checkered pattern (4-way symmetric)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 9, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x18, 3, 3, 9, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,     0, 3, 3, 9, false, true,  0, false),
                new SpriteMappingPiece(    0,     0, 3, 3, 9, true,  true,  0, false)
        )));

        // Frame 2: .check2 — mixed pattern
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x12, false, false, 0, false),
                new SpriteMappingPiece(    0, -0x18, 3, 3, 0x1B, false, false, 0, false),
                new SpriteMappingPiece(-0x18,     0, 3, 3, 0x1B, true,  true,  0, false),
                new SpriteMappingPiece(    0,     0, 3, 3, 0x12, true,  true,  0, false)
        )));

        // Frame 3: .check3 — rotated pattern
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x1B, true,  false, 0, false),
                new SpriteMappingPiece(    0, -0x18, 3, 3, 0x12, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,     0, 3, 3, 0x12, false, true,  0, false),
                new SpriteMappingPiece(    0,     0, 3, 3, 0x1B, false, true,  0, false)
        )));

        return frames;
    }

    /**
     * Loads prison capsule art (Nem_Prison) and creates S1-format sprite mappings.
     * Mappings from docs/s1disasm/_maps/Prison Capsule.asm (Map_Pri_internal).
     * <p>
     * Appears in every zone's final act. Uses palette line 1 for capsule body,
     * palette line 0 for switch pieces.
     */
    private void loadPrisonArt(Rom rom) {
        Pattern[] patterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_PRISON_ADDR, "Prison");
        if (patterns.length == 0) {
            LOGGER.warning("Failed to load prison capsule art");
            return;
        }

        List<SpriteMappingFrame> mappings = createPrisonMappings();
        // Palette line 0 as base; capsule body pieces override to pal 1 per-piece
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
        registerSheet(ObjectArtKeys.EGG_PRISON, sheet);
    }

    /**
     * Creates prison capsule sprite mappings from S1 disassembly Map_Pri_internal.
     * <p>
     * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
     * <p>
     * Frame 0 (.capsule): Intact sealed capsule (7 pieces, pal 1)
     * Frame 1 (.switch1): Switch before activation (1 piece, pal 0)
     * Frame 2 (.broken):  Broken/destroyed capsule (6 pieces, pal 1)
     * Frame 3 (.switch2): Switch after activation (1 piece, pal 0)
     * Frame 4 (.unusedthing1): Unused (2 pieces, pal 1)
     * Frame 5 (.unusedthing2): Unused (1 piece, pal 1)
     * Frame 6 (.blank):   Empty/invisible frame (0 pieces)
     */
    private List<SpriteMappingFrame> createPrisonMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .capsule - intact sealed capsule (7 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x20, 4, 1, 0x00, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x18, 4, 2, 0x04, false, false, 1, false),
                new SpriteMappingPiece(    0, -0x18, 4, 2, 0x0C, false, false, 1, false),
                new SpriteMappingPiece(-0x20,    -8, 4, 3, 0x14, false, false, 1, false),
                new SpriteMappingPiece(    0,    -8, 4, 3, 0x20, false, false, 1, false),
                new SpriteMappingPiece(-0x20,  0x10, 4, 2, 0x2C, false, false, 1, false),
                new SpriteMappingPiece(    0,  0x10, 4, 2, 0x34, false, false, 1, false)
        )));

        // Frame 1: .switch1 - switch before activation (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -8, 3, 2, 0x3C, false, false, 0, false)
        )));

        // Frame 2: .broken - destroyed capsule (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20,     0, 3, 1, 0x42, false, false, 1, false),
                new SpriteMappingPiece(-0x20,     8, 4, 1, 0x45, false, false, 1, false),
                new SpriteMappingPiece( 0x10,     0, 2, 1, 0x49, false, false, 1, false),
                new SpriteMappingPiece(    0,     8, 4, 1, 0x4B, false, false, 1, false),
                new SpriteMappingPiece(-0x20,  0x10, 4, 2, 0x2C, false, false, 1, false),
                new SpriteMappingPiece(    0,  0x10, 4, 2, 0x34, false, false, 1, false)
        )));

        // Frame 3: .switch2 - switch after activation (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -8, 3, 2, 0x4F, false, false, 0, false)
        )));

        // Frame 4: .unusedthing1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x55, false, false, 1, false),
                new SpriteMappingPiece(-0x10,     0, 4, 3, 0x61, false, false, 1, false)
        )));

        // Frame 5: .unusedthing2 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x10, 2, 4, 0x6D, false, false, 1, false)
        )));

        // Frame 6: .blank (0 pieces)
        frames.add(new SpriteMappingFrame(List.of()));

        return frames;
    }

    /**
     * Loads boss art for GHZ: Eggman ship/face/flame, boss weapons (chain anchor),
     * and exhaust flame for escape sequence.
     * ROM: Nem_Eggman, Nem_Weapons, Nem_Exhaust.
     */
    private void loadBossArt(Rom rom) {
        // Nem_Eggman: Main Eggman art (ship body, face variants, flames)
        Pattern[] eggmanPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_EGGMAN_ADDR, "Eggman");

        // Nem_Exhaust: Boss exhaust/escape flame (ArtTile_Eggman_Exhaust = ArtTile_Eggman + $12A)
        // Escape flame frames 11-12 in Eggman mappings reference tiles $12A+.
        // Merge exhaust patterns into the Eggman array at offset $12A so a single
        // renderer can draw all frames including escape flames.
        Pattern[] exhaustPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_BOSS_EXHAUST_ADDR, "BossExhaust");

        if (eggmanPatterns.length > 0) {
            // Merge: place exhaust patterns at offset $12A in a combined array
            int exhaustOffset = 0x12A; // ArtTile_Eggman_Exhaust - ArtTile_Eggman
            int mergedLength = Math.max(eggmanPatterns.length,
                    exhaustOffset + exhaustPatterns.length);
            Pattern[] mergedPatterns = java.util.Arrays.copyOf(eggmanPatterns, mergedLength);
            for (int i = 0; i < exhaustPatterns.length; i++) {
                mergedPatterns[exhaustOffset + i] = exhaustPatterns[i];
            }

            // Eggman uses make_art_tile(ArtTile_Eggman, 0, 0) — palette line 0
            // Ship body pieces use palette 1, face pieces use palette 0
            // (palette per-piece is encoded in the mappings)
            List<SpriteMappingFrame> mappings =
                    uk.co.jamesj999.sonic.game.sonic1.objects.bosses.GHZBossMappings.createEggmanMappings();
            ObjectSpriteSheet sheet = new ObjectSpriteSheet(mergedPatterns, mappings, 0, mappings.size());
            registerSheet(ObjectArtKeys.EGGMAN, sheet);
        }

        // Nem_Weapons: Boss weapons art (chain anchor frames for ball/chain)
        Pattern[] weaponsPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_BOSS_WEAPONS_ADDR, "BossWeapons");
        if (weaponsPatterns.length > 0) {
            List<SpriteMappingFrame> mappings =
                    uk.co.jamesj999.sonic.game.sonic1.objects.bosses.GHZBossMappings.createBossItemsMappings();
            ObjectSpriteSheet sheet = new ObjectSpriteSheet(weaponsPatterns, mappings, 0, mappings.size());
            registerSheet(ObjectArtKeys.BOSS_WEAPONS, sheet);
        }

        // GHZ Ball art (Nem_Ball) is already loaded by loadSwingingPlatformArt as SWING_GIANT_BALL.
        // The boss ball uses the same art with make_art_tile(ArtTile_GHZ_Giant_Ball, 2, 0).
        // Register an alias so boss code can find it under BOSS_BALL key too.
        ObjectSpriteSheet ballSheet = sheets.get(ObjectArtKeys.SWING_GIANT_BALL);
        if (ballSheet != null) {
            registerSheet(ObjectArtKeys.BOSS_BALL, ballSheet);
        }

        // Boss defeat explosions use getBossExplosionRenderer() which looks for the S2 key.
        // Register S1's standard explosion art under that key so BossExplosionObjectInstance works.
        // S1 explosion has 5 frames (vs S2's 7); frames 5-6 will be no-ops (graceful bounds check).
        ObjectSpriteSheet explosionSheet = sheets.get(ObjectArtKeys.EXPLOSION);
        if (explosionSheet != null) {
            registerSheet(uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys.BOSS_EXPLOSION,
                    explosionSheet);
        }
    }

    private void registerSheet(String key, ObjectSpriteSheet sheet) {
        if (sheet == null) {
            return;
        }
        sheets.put(key, sheet);
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet);
        renderers.put(key, renderer);
        rendererKeys.add(key);
        sheetOrder.add(sheet);
        rendererOrder.add(renderer);
    }

    private Pattern[] loadUncompressedPatterns(Rom rom, int address, int size, String name) {
        try {
            byte[] data = rom.readBytes(address, size);
            if (data.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
                LOGGER.warning("Inconsistent uncompressed art size for " + name);
                return new Pattern[0];
            }
            int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
            Pattern[] patterns = new Pattern[count];
            for (int i = 0; i < count; i++) {
                patterns[i] = new Pattern();
                byte[] sub = Arrays.copyOfRange(data,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                patterns[i].fromSegaFormat(sub);
            }
            return patterns;
        } catch (Exception e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    private Pattern[] loadNemesisPatterns(Rom rom, int address, String name) {
        try {
            byte[] compressed = rom.readBytes(address, 8192);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = NemesisReader.decompress(channel);
                int count = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
                Pattern[] patterns = new Pattern[count];
                for (int i = 0; i < count; i++) {
                    patterns[i] = new Pattern();
                    byte[] sub = Arrays.copyOfRange(decompressed,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[i].fromSegaFormat(sub);
                }
                return patterns;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    @Override
    public Pattern[] getHudDigitPatterns() {
        return hudDigitPatterns;
    }

    @Override
    public Pattern[] getHudTextPatterns() {
        return hudTextPatterns;
    }

    @Override
    public Pattern[] getHudLivesPatterns() {
        return hudLivesPatterns;
    }

    @Override
    public Pattern[] getHudLivesNumbers() {
        return hudLivesNumbers;
    }

    @Override
    public PatternSpriteRenderer getRenderer(String key) {
        return renderers.get(key);
    }

    @Override
    public ObjectSpriteSheet getSheet(String key) {
        return sheets.get(key);
    }

    @Override
    public SpriteAnimationSet getAnimations(String key) {
        return animations.get(key);
    }

    @Override
    public int getZoneData(String key, int zoneIndex) {
        return switch (key) {
            case ObjectArtKeys.ANIMAL_TYPE_A -> animalTypeA;
            case ObjectArtKeys.ANIMAL_TYPE_B -> animalTypeB;
            default -> -1;
        };
    }

    @Override
    public List<String> getRendererKeys() {
        return new ArrayList<>(rendererKeys);
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
        int next = baseIndex;
        for (int i = 0; i < rendererOrder.size(); i++) {
            ObjectSpriteSheet sheet = sheetOrder.get(i);
            PatternSpriteRenderer renderer = rendererOrder.get(i);
            int count = sheet.getPatterns().length;
            renderer.ensurePatternsCached(graphicsManager, next);
            next += count;
        }
        return next;
    }

    @Override
    public boolean isReady() {
        return loaded && hudDigitPatterns != null && hudDigitPatterns.length > 0;
    }

    @Override
    public int getHudTextPaletteLine() {
        return 0; // Sonic 1: yellow HUD text is in palette line 0
    }

    @Override
    public int getHudFlashPaletteLine() {
        return 0; // Sonic 1: life icon and flash both use palette line 0 (Sonic's palette)
    }
}
