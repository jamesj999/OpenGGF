package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AnimalType;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.resources.PlcParser;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.level.resources.PlcParser.PlcEntry;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tools.KosinskiReader;
import com.openggf.tools.NemesisReader;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Object art provider for Sonic 3 &amp; Knuckles.
 * <p>
 * Many S3K objects use level patterns rather than dedicated compressed art.
 * This provider builds sprite sheets from the loaded level's pattern data
 * after the level has been loaded.
 */
public class Sonic3kObjectArtProvider implements ObjectArtProvider {
    private static final Logger LOG = Logger.getLogger(Sonic3kObjectArtProvider.class.getName());
    private static final Path HCZ_MINIBOSS_MAPPING_ASM = Path.of(
            "docs", "skdisasm", "Levels", "HCZ", "Misc Object Data", "Map - Miniboss.asm");
    private static final Path HCZ_END_BOSS_MAPPING_ASM = Path.of(
            "docs", "skdisasm", "Levels", "HCZ", "Misc Object Data", "Map - End Boss.asm");

    private int currentZoneIndex = -2;
    private int currentActIndex = 0;

    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, ObjectSpriteSheet> sheets = new HashMap<>();
    private final Map<String, SpriteAnimationSet> animations = new HashMap<>();
    private final List<String> rendererKeys = new ArrayList<>();
    private final List<ObjectSpriteSheet> sheetOrder = new ArrayList<>();
    private final List<PatternSpriteRenderer> rendererOrder = new ArrayList<>();

    // Tracks which level tile indices each level-art sheet depends on.
    // Used by Sonic3kPlcLoader.refreshAffectedRenderers() to find which
    // renderers need GPU texture re-upload after PLC application.
    private final Map<String, Sonic3kPlcLoader.TileRange> levelArtTileRanges = new HashMap<>();

    // Shield DPLC-driven renderers and art sets
    private final Map<String, PlayerSpriteRenderer> dplcRenderers = new HashMap<>();
    private final Map<String, SpriteArtSet> shieldArtSets = new HashMap<>();

    // HUD pattern caches
    private Pattern[] hudDigitPatterns;
    private Pattern[] hudTextPatterns;
    private Pattern[] hudLivesPatterns;
    private Pattern[] hudLivesNumbers;

    // Zone-specific animal types (set per loadArtForZone call)
    private int animalTypeA = AnimalType.FLICKY.ordinal();
    private int animalTypeB = AnimalType.CHICKEN.ordinal();

    /**
     * S3K zone-to-animal mapping from PLCLoad_Animals_Index in sonic3k.asm.
     * Each entry is {AnimalA, AnimalB} for that zone index.
     */
    private static final AnimalType[][] S3K_ZONE_ANIMALS = {
            {AnimalType.FLICKY, AnimalType.CHICKEN},    // 0x00 AIZ
            {AnimalType.RABBIT, AnimalType.SEAL},       // 0x01 HCZ
            {AnimalType.FLICKY, AnimalType.CHICKEN},    // 0x02 MGZ
            {AnimalType.RABBIT, AnimalType.FLICKY},     // 0x03 CNZ
            {AnimalType.SQUIRREL, AnimalType.FLICKY},   // 0x04 FBZ
            {AnimalType.PENGUIN, AnimalType.SEAL},       // 0x05 ICZ
            {AnimalType.FLICKY, AnimalType.CHICKEN},    // 0x06 LBZ
            {AnimalType.SQUIRREL, AnimalType.CHICKEN},  // 0x07 MHZ
            {AnimalType.RABBIT, AnimalType.CHICKEN},    // 0x08 SOZ
            {AnimalType.FLICKY, AnimalType.CHICKEN},    // 0x09 LRZ
            {AnimalType.RABBIT, AnimalType.CHICKEN},    // 0x0A SSZ
            {AnimalType.SQUIRREL, AnimalType.CHICKEN},  // 0x0B DEZ
            {AnimalType.SQUIRREL, AnimalType.CHICKEN},  // 0x0C DDZ
    };
    private static final AnimalType[] DEFAULT_ANIMALS = {AnimalType.FLICKY, AnimalType.CHICKEN};

    @Override
    public void loadArtForZone(int zoneIndex) throws IOException {
        currentZoneIndex = zoneIndex;

        // Clear previous registrations
        renderers.clear();
        sheets.clear();
        animations.clear();
        rendererKeys.clear();
        sheetOrder.clear();
        rendererOrder.clear();
        dplcRenderers.clear();
        shieldArtSets.clear();
        levelArtTileRanges.clear();

        // Load HUD art (same for all zones)
        loadHudArt();

        // Load shared object art (Nemesis compressed from ROM)
        loadExplosionArt();
        loadMonitorArt();
        loadStarPostArt();
        loadEggCapsuleArt();
        loadAnimalArt(zoneIndex);
        loadPointsArt();

        // Load shield art (DPLC-driven, same for all zones)
        loadShieldArt();

        // Load invincibility star art (non-DPLC, same for all zones)
        loadInvincibilityStarArt();

        // Get act index from LevelManager (available during level load)
        currentActIndex = GameServices.level().getCurrentAct();
        Sonic3kPlcArtRegistry.ZoneArtPlan plan =
                Sonic3kPlcArtRegistry.getPlan(zoneIndex, currentActIndex);
        loadStandaloneFromRegistry(plan);
        // PLC-based boss art stays separate for now
        if (zoneIndex == 0x00) {
            loadAizMinibossArtFromPlc();
            loadAizEndBossArt();
            loadAiz2BattleshipArt();
        } else if (zoneIndex == 0x01) {
            loadSharedBossExplosionArt();
            loadHczMinibossArtFromPlc();
            loadHczEndBossArt();
        }

        // Level-art sheets are registered later via registerLevelArtSheets()
        // since the level must be loaded first
        LOG.info("Sonic3kObjectArtProvider initialized for zone " + zoneIndex);
    }

    private void loadStandaloneFromRegistry(Sonic3kPlcArtRegistry.ZoneArtPlan plan) {
        Rom rom;
        try {
            rom = GameServices.rom().getRom();
        } catch (IOException e) {
            LOG.warning("Failed to get ROM for standalone art: " + e.getMessage());
            return;
        }
        if (rom == null) return;

        RomByteReader reader;
        try {
            reader = RomByteReader.fromRom(rom);
        } catch (IOException e) {
            LOG.warning("Failed to create RomByteReader: " + e.getMessage());
            return;
        }

        Sonic3kObjectArt art = new Sonic3kObjectArt(null, reader);

        for (Sonic3kPlcArtRegistry.StandaloneArtEntry entry : plan.standaloneArt()) {
            try {
                ObjectSpriteSheet sheet = art.loadStandaloneSheet(rom, entry);
                registerSheet(entry.key(), sheet);
            } catch (IOException e) {
                LOG.warning("Failed to load standalone art '" + entry.key() + "': " + e.getMessage());
            }
        }
    }

    private void loadHudArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            LOG.warning("ROM not available for HUD art loading");
            return;
        }

        // HUD digits (0-9, colon, E) - uncompressed
        hudDigitPatterns = loadUncompressedPatterns(rom,
                Sonic3kConstants.ART_UNC_HUD_DIGITS_ADDR,
                Sonic3kConstants.ART_UNC_HUD_DIGITS_SIZE);
        LOG.info("Loaded " + (hudDigitPatterns != null ? hudDigitPatterns.length : 0) + " HUD digit patterns");

        // HUD text labels (SCORE/RINGS/TIME) - Nemesis compressed, tiles 14+ of ring/HUD blob
        hudTextPatterns = loadHudTextFromNemesis(rom);
        LOG.info("Loaded " + (hudTextPatterns != null ? hudTextPatterns.length : 0) + " HUD text patterns");

        // Lives icon - Nemesis compressed, character-specific
        int livesIconAddr = resolveLifeIconAddr();
        hudLivesPatterns = PatternDecompressor.nemesis(rom, livesIconAddr);
        LOG.info("Loaded " + (hudLivesPatterns != null ? hudLivesPatterns.length : 0) + " HUD lives icon patterns");

        // Lives digits (0-9) - uncompressed
        hudLivesNumbers = loadUncompressedPatterns(rom,
                Sonic3kConstants.ART_UNC_LIVES_DIGITS_ADDR,
                Sonic3kConstants.ART_UNC_LIVES_DIGITS_SIZE);
        LOG.info("Loaded " + (hudLivesNumbers != null ? hudLivesNumbers.length : 0) + " HUD lives digit patterns");
    }

    /**
     * Returns the ROM address for the character-specific life icon art.
     * ROM: PLC_01 (Sonic), PLC_05 (Knuckles), PLC_07 (Tails).
     */
    private int resolveLifeIconAddr() {
        String mainChar = GameServices.configuration()
                .getString(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE);
        if ("knuckles".equalsIgnoreCase(mainChar)) {
            return Sonic3kConstants.ART_NEM_KNUCKLES_LIFE_ICON_ADDR;
        } else if ("tails".equalsIgnoreCase(mainChar)) {
            return Sonic3kConstants.ART_NEM_TAILS_LIFE_ICON_ADDR;
        }
        return Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR;
    }

    private Pattern[] loadUncompressedPatterns(Rom rom, int addr, int size) throws IOException {
        byte[] data = rom.readBytes(addr, size);
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(data, i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }


    /**
     * Loads HUD text patterns from the shared ring/HUD Nemesis blob.
     * The first 14 tiles are ring art; tiles 14+ are HUD text (S, C, O, R, R, I, N, G, T, I, M, E).
     */
    private Pattern[] loadHudTextFromNemesis(Rom rom) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR);
        byte[] data = NemesisReader.decompress(channel);

        int totalTiles = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        int ringTiles = 14; // First 14 tiles are ring sprite data
        int textTiles = totalTiles - ringTiles;
        if (textTiles <= 0) {
            LOG.warning("No HUD text tiles found in ring/HUD blob (total=" + totalTiles + ")");
            return null;
        }

        Pattern[] patterns = new Pattern[textTiles];
        for (int i = 0; i < textTiles; i++) {
            int offset = (ringTiles + i) * Pattern.PATTERN_SIZE_IN_ROM;
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(data, offset, offset + Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }

    /**
     * Loads S3K explosion art from ROM (Nemesis compressed).
     * <p>
     * Art: ArtNem_Explosion at 0x19200A.
     * Mappings: Map - Explosion.asm (5 frames, same layout as S2).
     * <p>
     * The explosion object itself plays sfx_Break (0x3D) — see Obj_Explosion loc_1E61A.
     */
    private void loadExplosionArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            return;
        }

        Pattern[] patterns = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_EXPLOSION_ADDR);

        // 5 frames from Map - Explosion.asm (identical to S2)
        List<SpriteMappingFrame> frames = new ArrayList<>(5);
        // Frame 0: 2x2 at tile 0 (-8, -8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0))));
        // Frames 1-4: 4x4 at tiles 4, 0x14, 0x24, 0x34 (-16, -16)
        for (int tile : new int[]{4, 0x14, 0x24, 0x34}) {
            frames.add(new SpriteMappingFrame(List.of(
                    new SpriteMappingPiece(-16, -16, 4, 4, tile, false, false, 0))));
        }

        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, frames, 0, 1);
        registerSheet(ObjectArtKeys.EXPLOSION, sheet);
        LOG.info("Loaded S3K explosion art: " + patterns.length + " patterns, 5 frames");
    }

    /**
     * Loads S3K monitor art from ROM (Nemesis compressed).
     * Builds mapping frames and animation set from disassembly data.
     * <p>
     * Art: ArtNem_Monitors at 0x190F4A (60 tiles).
     * 1-Up icon uses player life icon patterns at tile offset 0x310.
     * Mappings: Map - Monitor.asm (12 frames, 6-byte piece format).
     * Animations: Anim - Monitor.asm (11 sequences).
     */
    private void loadMonitorArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            LOG.warning("ROM not available for monitor art loading");
            return;
        }

        // Load monitor base patterns (Nemesis compressed, ~60 tiles)
        Pattern[] monitorBasePatterns = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_MONITORS_ADDR);

        // Extend pattern array to cover life icon at offset 0x310
        // (1-Up monitor icon uses ArtTile_Player_Life_Icon = ArtTile_Monitors + $310)
        int lifeArtOffset = 0x310;
        int requiredSize = lifeArtOffset + (hudLivesPatterns != null ? hudLivesPatterns.length : 0);
        requiredSize = Math.max(requiredSize, monitorBasePatterns.length);

        Pattern[] monitorPatterns = new Pattern[requiredSize];
        System.arraycopy(monitorBasePatterns, 0, monitorPatterns, 0, monitorBasePatterns.length);

        // Copy life icon patterns at offset 0x310
        if (hudLivesPatterns != null && hudLivesPatterns.length > 0) {
            System.arraycopy(hudLivesPatterns, 0, monitorPatterns, lifeArtOffset,
                    Math.min(hudLivesPatterns.length, monitorPatterns.length - lifeArtOffset));
        }

        // Fill gaps with empty patterns to prevent NPEs
        for (int i = 0; i < monitorPatterns.length; i++) {
            if (monitorPatterns[i] == null) {
                monitorPatterns[i] = new Pattern();
            }
        }

        // Build mapping frames from disassembly (Map - Monitor.asm)
        List<SpriteMappingFrame> frames = buildMonitorMappingFrames();

        // Create and register sprite sheet
        ObjectSpriteSheet monitorSheet = new ObjectSpriteSheet(monitorPatterns, frames, 0, 1);
        registerSheet(Sonic3kObjectArtKeys.MONITOR, monitorSheet);

        // Build and register animation set (Anim - Monitor.asm)
        SpriteAnimationSet monitorAnimations = buildMonitorAnimations();
        animations.put(ObjectArtKeys.ANIM_MONITOR, monitorAnimations);

        LOG.info("Loaded S3K monitor art: " + monitorBasePatterns.length + " base patterns, "
                + frames.size() + " mapping frames, 11 animations");
    }

    /**
     * Builds S3K monitor mapping frames from disassembly data.
     * <p>
     * From Map - Monitor.asm: 12 frames (0=box, 1-10=icon+box, 11=broken shell).
     * Piece format: y_offset(8), size(8), pattern_word(16), x_offset(16).
     */
    private static List<SpriteMappingFrame> buildMonitorMappingFrames() {
        // Common box piece: 4x4 tiles (32x32px) at tile 0, palette 0
        SpriteMappingPiece box = new SpriteMappingPiece(-16, -16, 4, 4, 0, false, false, 0);

        List<SpriteMappingFrame> frames = new ArrayList<>(12);

        // Frame 0: static box only
        frames.add(new SpriteMappingFrame(List.of(box)));

        // Frame 1: Eggman icon ($18) + box
        frames.add(iconAndBox(0x18, 0));

        // Frame 2: 1-Up icon ($310 = life icon art offset, palette 0)
        frames.add(iconAndBox(0x310, 0));

        // Frame 3: Eggman 2 icon ($1C) + box
        frames.add(iconAndBox(0x1C, 0));

        // Frame 4: Rings icon ($20, palette 1 for ring colors)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -13, 2, 2, 0x20, false, false, 1),
                box)));

        // Frame 5: Speed Shoes icon ($24) + box
        frames.add(iconAndBox(0x24, 0));

        // Frame 6: Fire Shield icon ($30) + box
        frames.add(iconAndBox(0x30, 0));

        // Frame 7: Lightning Shield icon ($2C) + box
        frames.add(iconAndBox(0x2C, 0));

        // Frame 8: Bubble Shield icon ($34) + box
        frames.add(iconAndBox(0x34, 0));

        // Frame 9: Invincibility icon ($28) + box
        frames.add(iconAndBox(0x28, 0));

        // Frame 10: Super icon ($38) + box
        frames.add(iconAndBox(0x38, 0));

        // Frame 11: Broken shell - 4x2 tiles (32x16px) at tile $10, y=0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, 0, 4, 2, 0x10, false, false, 0))));

        return frames;
    }

    private static SpriteMappingFrame iconAndBox(int iconTile, int iconPalette) {
        return new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -13, 2, 2, iconTile, false, false, iconPalette),
                new SpriteMappingPiece(-16, -16, 4, 4, 0, false, false, 0)));
    }

    /**
     * Builds S3K monitor animation set from disassembly data.
     * <p>
     * From Anim - Monitor.asm: 11 animation sequences.
     * Anims 0-9 are monitor type animations (alternating icon/box).
     * Anim 10 is the break animation (box, eggman, broken shell → hold).
     */
    private static SpriteAnimationSet buildMonitorAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0 (Eggman): delay=1, [0, 1], loop
        set.addScript(0, new SpriteAnimationScript(1, List.of(0, 1),
                SpriteAnimationEndAction.LOOP, 0));

        // Anims 1-9: delay=1, [0, icon, icon, 1, icon, icon], loop
        // icon frame = anim_id + 1 (maps to mapping frames 2-10)
        for (int i = 1; i <= 9; i++) {
            int iconFrame = i + 1;
            set.addScript(i, new SpriteAnimationScript(1,
                    List.of(0, iconFrame, iconFrame, 1, iconFrame, iconFrame),
                    SpriteAnimationEndAction.LOOP, 0));
        }

        // Anim 10 (break): delay=2, [0, 1, 11], loop back 1 (holds on broken)
        set.addScript(10, new SpriteAnimationScript(2, List.of(0, 1, 11),
                SpriteAnimationEndAction.LOOP_BACK, 1));

        return set;
    }

    /**
     * Loads S3K StarPost art from ROM (Nemesis compressed).
     * <p>
     * Art: ArtNem_EnemyPtsStarPost at 0x192D2A (28 tiles: 8 enemy pts + 20 starpost).
     * Mappings: Map_StarPost at 0x2D348 (5 frames).
     * art_tile: make_art_tile(ArtTile_StarPost+8, 0, 0) — mapping tiles offset by 8 from blob start.
     * <p>
     * Also loads StarPost Stars mappings (Map_StarpostStars, 3 frames) for bonus star rendering.
     */
    private void loadStarPostArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) return;

        RomByteReader reader;
        try {
            reader = RomByteReader.fromRom(rom);
        } catch (IOException e) {
            LOG.warning("Failed to create RomByteReader for StarPost art: " + e.getMessage());
            return;
        }

        // Load combined enemy pts + starpost Nemesis art (28 tiles)
        Pattern[] allPatterns = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_ENEMY_PTS_STARPOST_ADDR);

        // Parse starpost mappings from ROM
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(reader,
                Sonic3kConstants.MAP_STARPOST_ADDR);

        // Mapping tile indices are relative to art_tile (ArtTile_StarPost+8),
        // but the Nemesis blob starts at ArtTile_StarPost. Offset by +8.
        List<SpriteMappingFrame> adjusted = new ArrayList<>(frames.size());
        for (SpriteMappingFrame frame : frames) {
            List<SpriteMappingPiece> adjustedPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                adjustedPieces.add(new SpriteMappingPiece(
                        piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(),
                        piece.tileIndex() + 8,
                        piece.hFlip(), piece.vFlip(),
                        piece.paletteIndex(), piece.priority()));
            }
            adjusted.add(new SpriteMappingFrame(adjustedPieces));
        }

        ObjectSpriteSheet sheet = new ObjectSpriteSheet(allPatterns, adjusted, 0, 1);
        registerSheet(ObjectArtKeys.CHECKPOINT, sheet);

        // Also load StarPost Stars mappings for bonus star rendering
        List<SpriteMappingFrame> starFrames = S3kSpriteDataLoader.loadMappingFrames(reader,
                Sonic3kConstants.MAP_STARPOST_STARS_ADDR);
        // Star mappings use tiles relative to art_tile (same blob, same +8 offset)
        List<SpriteMappingFrame> adjustedStars = new ArrayList<>(starFrames.size());
        for (SpriteMappingFrame frame : starFrames) {
            List<SpriteMappingPiece> adjustedPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                adjustedPieces.add(new SpriteMappingPiece(
                        piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(),
                        piece.tileIndex() + 8,
                        piece.hFlip(), piece.vFlip(),
                        piece.paletteIndex(), piece.priority()));
            }
            adjustedStars.add(new SpriteMappingFrame(adjustedPieces));
        }

        ObjectSpriteSheet starSheet = new ObjectSpriteSheet(allPatterns, adjustedStars, 0, 1);
        registerSheet(ObjectArtKeys.CHECKPOINT_STAR, starSheet);

        // Load 3 KosinskiM star art variants (ROM: Queue_Kos_Module at loc_2D436).
        // Each variant is 3 tiles replacing tiles at ArtTile_StarPost+8 (index 8 in our blob).
        loadStarVariant(rom, allPatterns, adjustedStars,
                Sonic3kConstants.ART_KOSM_STARPOST_STARS3_ADDR, ObjectArtKeys.CHECKPOINT_STAR_YELLOW);
        loadStarVariant(rom, allPatterns, adjustedStars,
                Sonic3kConstants.ART_KOSM_STARPOST_STARS1_ADDR, ObjectArtKeys.CHECKPOINT_STAR_BLUE);
        loadStarVariant(rom, allPatterns, adjustedStars,
                Sonic3kConstants.ART_KOSM_STARPOST_STARS2_ADDR, ObjectArtKeys.CHECKPOINT_STAR_RED);

        LOG.info("Loaded S3K StarPost art: " + allPatterns.length + " patterns, "
                + adjusted.size() + " frames, " + adjustedStars.size() + " star frames"
                + ", 3 bonus star variants");
    }

    /**
     * Loads a single KosinskiM star art variant and creates a sprite sheet.
     * ROM: Stars are 3 tiles decompressed to ArtTile_StarPost+8 (tile index 8 in our blob).
     */
    private void loadStarVariant(Rom rom, Pattern[] basePatterns,
                                  List<SpriteMappingFrame> starFrames,
                                  int kosmAddr, String artKey) {
        try {
            // Read and decompress KosinskiM data
            byte[] header = rom.readBytes(kosmAddr, 2);
            int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
            int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
            byte[] romData = rom.readBytes(kosmAddr, inputSize);
            byte[] decompressed = KosinskiReader.decompressModuled(romData, 0);

            int tileCount = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
            if (tileCount < 1) {
                LOG.warning("Star variant at 0x" + Integer.toHexString(kosmAddr)
                        + " decompressed to " + decompressed.length + " bytes (0 tiles)");
                return;
            }

            // Clone base patterns and replace tiles 8..8+tileCount with variant art
            Pattern[] variantPatterns = Arrays.copyOf(basePatterns, basePatterns.length);
            for (int i = 0; i < tileCount && (8 + i) < variantPatterns.length; i++) {
                variantPatterns[8 + i] = new Pattern();
                byte[] tile = Arrays.copyOfRange(decompressed, i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                variantPatterns[8 + i].fromSegaFormat(tile);
            }

            registerSheet(artKey, new ObjectSpriteSheet(variantPatterns, starFrames, 0, 1));
        } catch (Exception e) {
            LOG.warning("Failed to load star variant at 0x" + Integer.toHexString(kosmAddr)
                    + ": " + e.getMessage());
        }
    }

    /**
     * Loads animal art for the current zone.
     * <p>
     * Each zone has two assigned animal types. Art is Nemesis-compressed per animal type.
     * Mappings are parsed from ROM (Map_Animals1-5, 3 frames each, 6-byte S3K pieces).
     * The combined sheet follows the same layout as S2: 5 mapping sets × 2 variants × 3 frames.
     * <p>
     * The animal tile bank offset in S3K is 18 tiles (ArtTile_Animals2 - ArtTile_Animals1).
     */
    private void loadAnimalArt(int zoneIndex) throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) return;

        RomByteReader reader = RomByteReader.fromRom(rom);

        // Resolve zone-specific animal types
        AnimalType[] zoneAnimals = (zoneIndex >= 0 && zoneIndex < S3K_ZONE_ANIMALS.length)
                ? S3K_ZONE_ANIMALS[zoneIndex] : DEFAULT_ANIMALS;
        AnimalType typeA = zoneAnimals[0];
        AnimalType typeB = zoneAnimals[1];
        animalTypeA = typeA.ordinal();
        animalTypeB = typeB.ordinal();

        // Load Nemesis-compressed art for both animal types
        Pattern[] patternsA = PatternDecompressor.nemesis(rom, getS3kAnimalArtAddr(typeA));
        Pattern[] patternsB = PatternDecompressor.nemesis(rom, getS3kAnimalArtAddr(typeB));

        // Combine into a single bank with S3K's 18-tile offset
        int tileOffset = Sonic3kConstants.S3K_ANIMAL_TILE_OFFSET;
        int combinedLength = Math.max(
                Math.max(patternsA.length, tileOffset + patternsB.length),
                tileOffset * 2);
        Pattern[] combined = new Pattern[combinedLength];
        System.arraycopy(patternsA, 0, combined, 0, Math.min(patternsA.length, combined.length));
        if (tileOffset < combined.length) {
            int copyLen = Math.min(patternsB.length, combined.length - tileOffset);
            System.arraycopy(patternsB, 0, combined, tileOffset, copyLen);
        }
        for (int i = 0; i < combined.length; i++) {
            if (combined[i] == null) combined[i] = new Pattern();
        }

        // Parse all 5 mapping tables from ROM and build the combined frame list.
        // Layout: 5 sets × 2 variants (offset 0, offset 18) × 3 frames = 30 frames.
        // This matches the indexing in AnimalObjectInstance.getFrameIndex().
        int[] mapAddrs = {
                Sonic3kConstants.MAP_ANIMALS1_ADDR,
                Sonic3kConstants.MAP_ANIMALS2_ADDR,
                Sonic3kConstants.MAP_ANIMALS3_ADDR,
                Sonic3kConstants.MAP_ANIMALS4_ADDR,
                Sonic3kConstants.MAP_ANIMALS5_ADDR,
        };

        List<SpriteMappingFrame> allFrames = new ArrayList<>(30);
        for (int mapAddr : mapAddrs) {
            List<SpriteMappingFrame> setFrames = S3kSpriteDataLoader.loadMappingFrames(reader, mapAddr, 3);
            // Variant 0: tile indices as-is (animal A at offset 0)
            allFrames.addAll(setFrames);
            // Variant 1: tile indices shifted by tileOffset (animal B at offset 18)
            for (SpriteMappingFrame frame : setFrames) {
                List<SpriteMappingPiece> shifted = new ArrayList<>(frame.pieces().size());
                for (SpriteMappingPiece piece : frame.pieces()) {
                    shifted.add(new SpriteMappingPiece(
                            piece.xOffset(), piece.yOffset(),
                            piece.widthTiles(), piece.heightTiles(),
                            piece.tileIndex() + tileOffset,
                            piece.hFlip(), piece.vFlip(),
                            piece.paletteIndex(), piece.priority()));
                }
                allFrames.add(new SpriteMappingFrame(shifted));
            }
        }

        ObjectSpriteSheet animalSheet = new ObjectSpriteSheet(combined, allFrames, 0, 1);
        registerSheet(ObjectArtKeys.ANIMAL, animalSheet);
        LOG.info("Loaded S3K animal art: " + typeA.displayName() + " + " + typeB.displayName()
                + ", " + combined.length + " patterns, " + allFrames.size() + " frames");
    }

    /**
     * Returns the S3K-specific Nemesis art ROM address for the given animal type.
     * S3K uses BlueFlicky (mapped to FLICKY) instead of S2's Flicky.
     */
    private static int getS3kAnimalArtAddr(AnimalType type) {
        return switch (type) {
            case RABBIT -> Sonic3kConstants.ART_NEM_RABBIT_ADDR;
            case CHICKEN -> Sonic3kConstants.ART_NEM_CHICKEN_ADDR;
            case PENGUIN -> Sonic3kConstants.ART_NEM_PENGUIN_ADDR;
            case SEAL -> Sonic3kConstants.ART_NEM_SEAL_ADDR;
            case PIG -> Sonic3kConstants.ART_NEM_PIG_ADDR;
            case FLICKY -> Sonic3kConstants.ART_NEM_BLUE_FLICKY_ADDR;
            case SQUIRREL -> Sonic3kConstants.ART_NEM_SQUIRREL_ADDR;
            // Animals not present in S3K ROM - fall back to BlueFlicky
            default -> Sonic3kConstants.ART_NEM_BLUE_FLICKY_ADDR;
        };
    }

    /**
     * Loads enemy score/points popup art.
     * <p>
     * The score tiles are the first 8 tiles of ArtNem_EnemyPtsStarPost (already loaded
     * by loadStarPostArt). Mappings are parsed from Map_EnemyScore (7 frames).
     * Unlike the StarPost mappings which need a +8 tile offset, the score mappings
     * reference tiles starting at 0 (the beginning of the combined art blob).
     */
    private void loadPointsArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) return;

        RomByteReader reader = RomByteReader.fromRom(rom);

        // Load the same combined art blob used by StarPost
        Pattern[] patterns = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_ENEMY_PTS_STARPOST_ADDR);

        // Parse Map_EnemyScore (7 frames, tile indices relative to start of blob - no offset needed)
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(reader,
                Sonic3kConstants.MAP_ENEMY_SCORE_ADDR, 7);

        ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, frames, 0, 1);
        registerSheet(ObjectArtKeys.POINTS, sheet);
        LOG.info("Loaded S3K enemy score art: " + patterns.length + " patterns, " + frames.size() + " frames");
    }

    /**
     * Loads shield art (fire, lightning, bubble) from ROM using DPLC-driven rendering.
     * Each shield has its own uncompressed art, mappings, DPLCs, and animation scripts.
     */
    private void loadShieldArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) {
                return;
            }
            RomByteReader reader = RomByteReader.fromRom(rom);
            loadSingleShieldArt(reader, Sonic3kObjectArtKeys.FIRE_SHIELD,
                    Sonic3kConstants.ART_UNC_FIRE_SHIELD_ADDR, Sonic3kConstants.ART_UNC_FIRE_SHIELD_SIZE,
                    Sonic3kConstants.MAP_FIRE_SHIELD_ADDR, Sonic3kConstants.DPLC_FIRE_SHIELD_ADDR,
                    Sonic3kConstants.ANI_FIRE_SHIELD_ADDR, Sonic3kConstants.ANI_FIRE_SHIELD_COUNT,
                    Sonic3kConstants.ART_TILE_SHIELD, 0);

            loadSingleShieldArt(reader, Sonic3kObjectArtKeys.LIGHTNING_SHIELD,
                    Sonic3kConstants.ART_UNC_LIGHTNING_SHIELD_ADDR, Sonic3kConstants.ART_UNC_LIGHTNING_SHIELD_SIZE,
                    Sonic3kConstants.MAP_LIGHTNING_SHIELD_ADDR, Sonic3kConstants.DPLC_LIGHTNING_SHIELD_ADDR,
                    Sonic3kConstants.ANI_LIGHTNING_SHIELD_ADDR, Sonic3kConstants.ANI_LIGHTNING_SHIELD_COUNT,
                    Sonic3kConstants.ART_TILE_SHIELD, 0);

            // ROM: Obj_LightningShield init DMA-loads spark art to ArtTile_Shield_Sparks
            // (fixed VRAM, not managed by PLCLoad_Shields). Sparks use their own renderer
            // with the 5 spark tiles and rebased mappings (tile indices 0-4 instead of 31-35).
            buildSparkArtSet(reader);

            loadSingleShieldArt(reader, Sonic3kObjectArtKeys.BUBBLE_SHIELD,
                    Sonic3kConstants.ART_UNC_BUBBLE_SHIELD_ADDR, Sonic3kConstants.ART_UNC_BUBBLE_SHIELD_SIZE,
                    Sonic3kConstants.MAP_BUBBLE_SHIELD_ADDR, Sonic3kConstants.DPLC_BUBBLE_SHIELD_ADDR,
                    Sonic3kConstants.ANI_BUBBLE_SHIELD_ADDR, Sonic3kConstants.ANI_BUBBLE_SHIELD_COUNT,
                    Sonic3kConstants.ART_TILE_SHIELD, 0);

            loadSingleShieldArt(reader, Sonic3kObjectArtKeys.INSTA_SHIELD,
                    Sonic3kConstants.ART_UNC_INSTA_SHIELD_ADDR, Sonic3kConstants.ART_UNC_INSTA_SHIELD_SIZE,
                    Sonic3kConstants.MAP_INSTA_SHIELD_ADDR, Sonic3kConstants.DPLC_INSTA_SHIELD_ADDR,
                    Sonic3kConstants.ANI_INSTA_SHIELD_ADDR, Sonic3kConstants.ANI_INSTA_SHIELD_COUNT,
                    Sonic3kConstants.ART_TILE_SHIELD, 0);
        } catch (IOException e) {
            LOG.warning("Failed to load shield art: " + e.getMessage());
        }
    }

    /**
     * Loads invincibility star art from ROM (uncompressed art + ROM-parsed mappings).
     * ROM: ArtUnc_Invincibility binclude (32 tiles), Map_Invincibility (9 frames).
     * No DPLCs — static tile assignment like explosion art.
     */
    private void loadInvincibilityStarArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) {
                return;
            }
            Pattern[] patterns = loadUncompressedPatterns(rom,
                    Sonic3kConstants.ART_UNC_INVINCIBILITY_ADDR,
                    Sonic3kConstants.ART_UNC_INVINCIBILITY_SIZE);

            RomByteReader reader = RomByteReader.fromRom(rom);
            List<SpriteMappingFrame> rawMappings =
                    S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_INVINCIBILITY_ADDR);

            // Normalize tile indices to 0-based (ROM mappings reference ArtTile_Shield = $079C)
            List<SpriteMappingFrame> mappings = normalizeMappingTileIndices(rawMappings);

            ObjectSpriteSheet sheet = new ObjectSpriteSheet(patterns, mappings, 0, 1);
            registerSheet(ObjectArtKeys.INVINCIBILITY_STARS, sheet);
            LOG.info("Loaded S3K invincibility star art: " + patterns.length
                    + " tiles, " + mappings.size() + " mapping frames");
        } catch (Exception e) {
            LOG.warning("Failed to load invincibility star art: " + e.getMessage());
        }
    }

    /**
     * Normalizes mapping tile indices to be 0-based by subtracting the minimum
     * tile index found across all pieces. Required when ROM mappings reference
     * absolute VRAM tile positions (e.g. ArtTile_Shield = $079C).
     */
    private static List<SpriteMappingFrame> normalizeMappingTileIndices(List<SpriteMappingFrame> frames) {
        int minTile = Integer.MAX_VALUE;
        for (SpriteMappingFrame frame : frames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                if (piece.tileIndex() < minTile) {
                    minTile = piece.tileIndex();
                }
            }
        }
        if (minTile == 0 || minTile == Integer.MAX_VALUE) {
            return frames;
        }
        final int offset = minTile;
        List<SpriteMappingFrame> normalized = new ArrayList<>(frames.size());
        for (SpriteMappingFrame frame : frames) {
            List<SpriteMappingPiece> pieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece p : frame.pieces()) {
                pieces.add(new SpriteMappingPiece(
                        p.xOffset(), p.yOffset(), p.widthTiles(), p.heightTiles(),
                        p.tileIndex() - offset, p.hFlip(), p.vFlip(),
                        p.paletteIndex(), p.priority()));
            }
            normalized.add(new SpriteMappingFrame(pieces));
        }
        return normalized;
    }

    private void loadSingleShieldArt(RomByteReader reader, String key,
            int artAddr, int artSize, int mapAddr, int dplcAddr,
            int animAddr, int animCount, int baseTile, int paletteIndex) throws IOException {
        Pattern[] tiles = S3kSpriteDataLoader.loadArtTiles(reader, artAddr, artSize);
        List<SpriteMappingFrame> mappings = S3kSpriteDataLoader.loadMappingFrames(reader, mapAddr);
        List<SpriteDplcFrame> dplcs = S3kSpriteDataLoader.loadDplcFrames(reader, dplcAddr);

        // Ensure DPLC count doesn't exceed mapping count
        if (dplcs.size() > mappings.size()) {
            dplcs = new ArrayList<>(dplcs.subList(0, mappings.size()));
        }
        // Pad DPLC list if shorter than mappings (empty DPLC = reuse previous tiles)
        while (dplcs.size() < mappings.size()) {
            dplcs.add(new SpriteDplcFrame(List.of()));
        }

        int bankSize = S3kSpriteDataLoader.resolveBankSize(dplcs, mappings);
        SpriteAnimationSet animSet = S3kSpriteDataLoader.loadAnimationSet(reader, animAddr, animCount);

        SpriteArtSet artSet = new SpriteArtSet(tiles, mappings, dplcs,
                paletteIndex, baseTile, 1, bankSize, null, animSet);
        PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet);

        shieldArtSets.put(key, artSet);
        dplcRenderers.put(key, renderer);

        LOG.info("Loaded " + key + " art: " + tiles.length + " tiles, "
                + mappings.size() + " mapping frames, " + animCount + " animations");
    }

    /**
     * Builds the spark art set (ROM: DMA to ArtTile_Shield_Sparks).
     * Only loads the 5 spark tiles and animation script — rendering is handled
     * directly by {@link LightningSparkObjectInstance} via renderPatternWithId,
     * matching the ROM where spark art is DMA-loaded once (not managed by PLCLoad_Shields).
     */
    private void buildSparkArtSet(RomByteReader reader) throws IOException {
        Pattern[] sparkTiles = S3kSpriteDataLoader.loadArtTiles(reader,
                Sonic3kConstants.ART_UNC_LIGHTNING_SHIELD_SPARKS_ADDR,
                Sonic3kConstants.ART_UNC_LIGHTNING_SHIELD_SPARKS_SIZE);

        // Animation script matching ROM Ani_LightningShield script 1:
        // delay=0, pattern [0,1,2] × 6 then [0,1], endAction=LOOP
        List<Integer> sparkFrames = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            sparkFrames.add(0);
            sparkFrames.add(1);
            sparkFrames.add(2);
        }
        sparkFrames.add(0);
        sparkFrames.add(1);
        SpriteAnimationScript sparkScript = new SpriteAnimationScript(
                0, sparkFrames, SpriteAnimationEndAction.LOOP, 0);
        SpriteAnimationSet sparkAnimSet = new SpriteAnimationSet();
        sparkAnimSet.addScript(0, sparkScript);

        // Only artTiles and animationSet are used; other fields are placeholders.
        SpriteArtSet sparkArtSet = new SpriteArtSet(sparkTiles, List.of(), List.of(),
                0, 0, 1, 0, null, sparkAnimSet);
        shieldArtSets.put(Sonic3kObjectArtKeys.LIGHTNING_SPARK, sparkArtSet);

        LOG.info("Built lightning spark art: " + sparkTiles.length + " tiles");
    }

    /** Returns the DPLC-driven renderer for a shield type, or null. */
    public PlayerSpriteRenderer getShieldDplcRenderer(String key) {
        return dplcRenderers.get(key);
    }

    /** Returns the art set for a shield type, or null. */
    public SpriteArtSet getShieldArtSet(String key) {
        return shieldArtSets.get(key);
    }

    @Override
    public void registerLevelTileArt(Level level, int zoneIndex) {
        registerLevelArtSheets(level, zoneIndex);
    }

    /**
     * Registers object sprite sheets that use level patterns.
     * Must be called AFTER the level is loaded.
     *
     * @param level the loaded level
     * @param zoneIndex the zone index
     */
    public void registerLevelArtSheets(Level level, int zoneIndex) {
        if (level == null) return;

        // Refresh act index from LevelManager — required for act transitions
        // (e.g. AIZ1→AIZ2 seamless reload) where the act changed after initial load.
        currentActIndex = GameServices.level().getCurrentAct();

        RomByteReader reader = null;
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom != null) reader = RomByteReader.fromRom(rom);
        } catch (IOException e) {
            LOG.warning("Failed to create RomByteReader for level art: " + e.getMessage());
        }
        Sonic3kObjectArt art = new Sonic3kObjectArt(level, reader);

        Sonic3kPlcArtRegistry.ZoneArtPlan plan =
                Sonic3kPlcArtRegistry.getPlan(zoneIndex, currentActIndex);
        loadLevelArtFromRegistry(plan, art);

        LOG.info("Sonic3kObjectArtProvider registered " + rendererKeys.size()
                + " level-art sheets for zone " + zoneIndex);
    }

    private void loadLevelArtFromRegistry(Sonic3kPlcArtRegistry.ZoneArtPlan plan,
            Sonic3kObjectArt art) {
        for (Sonic3kPlcArtRegistry.LevelArtEntry entry : plan.levelArt()) {
            ObjectSpriteSheet sheet;
            if (entry.builderName() != null) {
                sheet = invokeBuilder(art, entry.builderName());
            } else if (entry.mappingAddr() > 0 && entry.frameFilter() != null) {
                sheet = art.buildLevelArtSheetFromRomFiltered(
                        entry.mappingAddr(), entry.artTileBase(), entry.palette(),
                        entry.frameFilter(), entry.mappingFormat());
            } else if (entry.mappingAddr() > 0) {
                sheet = art.buildLevelArtSheetFromRom(
                        entry.mappingAddr(), entry.artTileBase(), entry.palette(), entry.mappingFormat());
            } else {
                LOG.warning("LevelArtEntry '" + entry.key() + "' has no builder or mapping addr");
                continue;
            }
            registerLevelArtSheet(entry.key(), sheet, art);
        }
    }

    private ObjectSpriteSheet invokeBuilder(Sonic3kObjectArt art, String builderName) {
        return switch (builderName) {
            case "buildSpikesSheet" -> art.buildSpikesSheet();
            case "buildSpringVerticalSheet" -> art.buildSpringVerticalSheet();
            case "buildSpringVerticalYellowSheet" -> art.buildSpringVerticalYellowSheet();
            case "buildSpringHorizontalSheet" -> art.buildSpringHorizontalSheet();
            case "buildSpringHorizontalYellowSheet" -> art.buildSpringHorizontalYellowSheet();
            case "buildSpringDiagonalSheet" -> art.buildSpringDiagonalSheet();
            case "buildSpringDiagonalYellowSheet" -> art.buildSpringDiagonalYellowSheet();
            case "buildAiz1TreeSheet" -> art.buildAiz1TreeSheet();
            case "buildAiz1ZiplinePegSheet" -> art.buildAiz1ZiplinePegSheet();
            case "buildAizForegroundPlantSheet" -> art.buildAizForegroundPlantSheet();
            case "buildAnimatedStillSpritesSheet" -> art.buildAnimatedStillSpritesSheet();
            case "buildAnimStillLrzD3Sheet" -> art.buildAnimStillLrzD3Sheet();
            case "buildAnimStillLrz2Sheet" -> art.buildAnimStillLrz2Sheet();
            case "buildAnimStillSozSheet" -> art.buildAnimStillSozSheet();
            case "buildFlippingBridgeSheet" -> art.buildFlippingBridgeSheet();
            case "buildDrawBridgeSheet" -> art.buildDrawBridgeSheet();
            case "buildDisappearingFloorSheet" -> art.buildDisappearingFloorSheet();
            case "buildDisappearingFloorBorderSheet" -> art.buildDisappearingFloorBorderSheet();
            case "buildHczWaterDropSheet" -> art.buildHczWaterDropSheet();
            case "buildHczWaterRushBlockSheet" -> art.buildHczWaterRushBlockSheet();
            case "buildDoorVerticalHczSheet" -> art.buildDoorVerticalHczSheet();
            case "buildDoorVerticalCnzSheet" -> art.buildDoorVerticalCnzSheet();
            case "buildDoorVerticalDezSheet" -> art.buildDoorVerticalDezSheet();
            case "buildDoorHorizontalSheet" -> art.buildDoorHorizontalSheet();
            default -> {
                LOG.warning("Unknown builder: " + builderName);
                yield null;
            }
        };
    }

    /**
     * Registers a level-art sheet and records its level tile range for PLC refresh.
     */
    private void registerLevelArtSheet(String key, ObjectSpriteSheet sheet, Sonic3kObjectArt art) {
        registerSheet(key, sheet);
        if (sheet != null && art.getLastBuildStartTile() >= 0) {
            levelArtTileRanges.put(key, new Sonic3kPlcLoader.TileRange(
                    art.getLastBuildStartTile(), art.getLastBuildTileCount()));
        }
    }

    private void loadEggCapsuleArt() throws IOException {
        Rom rom = GameServices.rom().getRom();
        if (rom == null) {
            return;
        }
        RomByteReader reader = RomByteReader.fromRom(rom);
        Sonic3kObjectArt art = new Sonic3kObjectArt(null, reader);
        registerSheet(Sonic3kObjectArtKeys.EGG_CAPSULE, art.loadEggCapsuleSheet(rom));
    }

    /**
     * Loads AIZ miniboss art via PLC 0x5A, matching the ROM's Load_PLC call.
     * PLC entries provide both the Nemesis art ROM addresses and VRAM tile destinations,
     * eliminating the need for hardcoded art/tile constants.
     *
     * <p>On real hardware, the boss PLC decompresses into shared VRAM, overwriting
     * spike/spring tiles at 0x0494+ (the boss fire art at 0x0482 overlaps). We avoid
     * this by decompressing into standalone Pattern[] arrays instead of the level's
     * pattern buffer. The ROM restores spike/spring art via Load_PLC(PLC_Monitors)
     * after the boss is defeated; with standalone arrays we don't need that.
     *
     * <p>PLC 0x5A contains 4 entries:
     * <ol start="0">
     *   <li>ArtNem_AIZMiniboss → ArtTile_AIZMiniboss (main boss)</li>
     *   <li>ArtNem_AIZMinibossSmall → ArtTile_AIZMinibossSmall (debris)</li>
     *   <li>ArtNem_AIZBossFire → ArtTile_AIZBossFire (flames)</li>
     *   <li>ArtNem_BossExplosion → ArtTile_BossExplosion2 (shared explosion)</li>
     * </ol>
     */
    private void loadAizMinibossArtFromPlc() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return;
            RomByteReader reader = RomByteReader.fromRom(rom);

            // Parse PLC 0x5A to get art ROM addresses (no level application)
            PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_AIZ_MINIBOSS);
            List<PlcEntry> entries = plc.entries();
            if (entries.size() < 3) {
                LOG.warning("PLC 0x5A has fewer than 3 entries (" + entries.size() + "), skipping miniboss art");
                return;
            }

            // Decompress each entry's Nemesis art into standalone Pattern[] arrays
            // via PlcParser.decompressEntry() — not into the level's pattern buffer,
            // to avoid overwriting spike/spring tiles at 0x0494+
            List<Pattern[]> decompressed = PlcParser.decompressAll(rom, plc);

            // Entry 0: main boss art
            registerSheet(Sonic3kObjectArtKeys.AIZ_MINIBOSS,
                    buildSheetFromPatterns(decompressed.get(0), reader,
                            Sonic3kConstants.MAP_AIZ_MINIBOSS_ADDR, 1));

            // Entry 1: small debris art
            registerSheet(Sonic3kObjectArtKeys.AIZ_MINIBOSS_SMALL,
                    buildSheetFromPatterns(decompressed.get(1), reader,
                            Sonic3kConstants.MAP_AIZ_MINIBOSS_SMALL_ADDR, 1));

            // Entry 2: flame art
            registerSheet(Sonic3kObjectArtKeys.AIZ_MINIBOSS_FLAME,
                    buildSheetFromPatterns(decompressed.get(2), reader,
                            Sonic3kConstants.MAP_AIZ_MINIBOSS_FLAME_ADDR, 0));

            // Entry 3: boss explosion art (ArtNem_BossExplosion → ArtTile_BossExplosion2)
            if (decompressed.size() >= 4) {
                registerSheet(ObjectArtKeys.BOSS_EXPLOSION,
                        buildSheetFromPatterns(decompressed.get(3), reader,
                                Sonic3kConstants.MAP_BOSS_EXPLOSION_ADDR, 0));
            }

            LOG.info(String.format("Loaded AIZ miniboss art via PLC 0x5A (standalone): " +
                            "main=%d tiles, small=%d tiles, flame=%d tiles, explosion=%s",
                    decompressed.get(0).length,
                    decompressed.get(1).length,
                    decompressed.get(2).length,
                    decompressed.size() >= 4 ? decompressed.get(3).length + " tiles" : "n/a"));
        } catch (IOException e) {
            LOG.warning("Failed to load AIZ miniboss art from PLC: " + e.getMessage());
        }
    }

    /**
     * Loads the shared boss explosion sheet directly from ROM.
     * This keeps non-AIZ bosses from depending on AIZ-specific PLC paths.
     */
    private void loadSharedBossExplosionArt() {
        if (sheets.containsKey(ObjectArtKeys.BOSS_EXPLOSION)) {
            return;
        }
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return;
            RomByteReader reader = RomByteReader.fromRom(rom);
            Pattern[] patterns = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_BOSS_EXPLOSION_ADDR);
            registerSheet(ObjectArtKeys.BOSS_EXPLOSION,
                    buildSheetFromPatterns(patterns, reader, Sonic3kConstants.MAP_BOSS_EXPLOSION_ADDR, 0));
        } catch (IOException e) {
            LOG.warning("Failed to load shared boss explosion art: " + e.getMessage());
        }
    }

    /**
     * Loads HCZ miniboss art via PLC 0x5B, matching the ROM's Load_PLC call.
     */
    private void loadHczMinibossArtFromPlc() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return;
            PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_HCZ_MINIBOSS);
            List<Pattern[]> decompressed = PlcParser.decompressAll(rom, plc);
            if (decompressed.isEmpty() || decompressed.get(0).length == 0) {
                LOG.warning("HCZ miniboss PLC produced no art");
                return;
            }
            List<SpriteMappingFrame> mappings = loadMappingsFromAsmInclude(HCZ_MINIBOSS_MAPPING_ASM);
            if (mappings.isEmpty()) {
                LOG.warning("HCZ miniboss asm mapping include produced no frames");
                return;
            }
            // The ASM mapping has 36 header entries but only 35 unique Frame_ labels —
            // frames 25 and 26 both point to Frame_362BB0 (blank). The sequential parser
            // produces only one frame for the shared label, shifting all subsequent frames.
            // Insert a duplicate blank frame at index 26 to restore correct alignment.
            if (mappings.size() == 35) {
                mappings.add(26, new SpriteMappingFrame(List.of()));
            }

            registerSheet(Sonic3kObjectArtKeys.HCZ_MINIBOSS,
                    buildSheetFromPatterns(decompressed.get(0), mappings, 1));
            LOG.info("Loaded HCZ miniboss art via PLC 0x5B: "
                    + decompressed.get(0).length + " tiles");
        } catch (IOException e) {
            LOG.warning("Failed to load HCZ miniboss art from PLC: " + e.getMessage());
        }
    }

    /**
     * Loads HCZ end boss art via PLC 0x6C, matching the ROM's Load_PLC call.
     * PLC entries: 0=boss body, 1=Robotnik ship, 2=boss explosion, 3=egg capsule.
     * Only loads entry 0 (boss body) here; explosion and egg capsule art loaded separately.
     */
    private void loadHczEndBossArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return;
            PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_HCZ_END_BOSS);
            List<Pattern[]> decompressed = PlcParser.decompressAll(rom, plc);
            if (decompressed.isEmpty() || decompressed.get(0).length == 0) {
                LOG.warning("HCZ end boss PLC produced no art");
                return;
            }
            List<SpriteMappingFrame> mappings = loadMappingsFromAsmInclude(HCZ_END_BOSS_MAPPING_ASM);
            if (mappings.isEmpty()) {
                LOG.warning("HCZ end boss asm mapping include produced no frames");
                return;
            }
            registerSheet(Sonic3kObjectArtKeys.HCZ_END_BOSS,
                    buildSheetFromPatterns(decompressed.get(0), mappings, 1));
        } catch (IOException e) {
            LOG.warning("Failed to load HCZ end boss art: " + e.getMessage());
        }
    }

    /**
     * Loads AIZ end boss art: KosinskiModuled main art + PLC 0x6B (Robotnik ship + explosions).
     * The main boss art at ArtKosM_AIZEndBoss is separate from the PLC-loaded shared assets.
     * Matching ROM: Queue_Kos_Module + Load_PLC(#$6B) in Obj_AIZEndBossWait.
     */
    private void loadAizEndBossArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return;
            RomByteReader reader = RomByteReader.fromRom(rom);

            // Main boss art (KosinskiModuled)
            Pattern[] bossPatterns = decompressKosinskiModuled(rom,
                    Sonic3kConstants.ART_KOSM_AIZ_END_BOSS_ADDR);
            if (bossPatterns.length > 0) {
                registerSheet(Sonic3kObjectArtKeys.AIZ_END_BOSS,
                        buildSheetFromPatterns(bossPatterns, reader,
                                Sonic3kConstants.MAP_AIZ_END_BOSS_ADDR, 1));
            }

            // PLC 0x6B: ArtNem_RobotnikShip + ArtNem_BossExplosion (shared assets)
            PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_AIZ_END_BOSS);
            List<Pattern[]> decompressed = PlcParser.decompressAll(rom, plc);

            // Entry 0: Robotnik ship art
            if (!decompressed.isEmpty() && decompressed.get(0).length > 0) {
                registerSheet(Sonic3kObjectArtKeys.ROBOTNIK_SHIP,
                        buildSheetFromPatterns(decompressed.get(0), reader,
                                Sonic3kConstants.MAP_ROBOTNIK_SHIP_ADDR, 0));
            }

            // Entry 1: Boss explosion art (may already be registered by miniboss PLC)
            if (decompressed.size() >= 2 && decompressed.get(1).length > 0
                    && sheets.get(ObjectArtKeys.BOSS_EXPLOSION) == null) {
                registerSheet(ObjectArtKeys.BOSS_EXPLOSION,
                        buildSheetFromPatterns(decompressed.get(1), reader,
                                Sonic3kConstants.MAP_BOSS_EXPLOSION_ADDR, 0));
            }

            LOG.info(String.format("Loaded AIZ end boss art: main=%d tiles, ship=%s, explosion=%s",
                    bossPatterns.length,
                    !decompressed.isEmpty() ? decompressed.get(0).length + " tiles" : "n/a",
                    decompressed.size() >= 2 ? decompressed.get(1).length + " tiles" : "n/a"));
        } catch (IOException e) {
            LOG.warning("Failed to load AIZ end boss art: " + e.getMessage());
        }
    }

    /**
     * Loads AIZ2 battleship sequence art via standalone KosinskiModuled decompression.
     *
     * <p>The bombership sprites share a single KosinskiModuled art source
     * (ArtKosM_AIZ2Bombership2 at 0x399CC4, 176 tiles). Each object type uses
     * different mapping frames referencing tile indices within this art set.
     * The battleship palette is loaded into palette line 2.
     */
    private void loadAiz2BattleshipArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return;
            RomByteReader reader = RomByteReader.fromRom(rom);

            // Decompress the shared bombership art (KosinskiModuled, 176 tiles)
            Pattern[] bomberPatterns = decompressKosinskiModuled(rom,
                    Sonic3kConstants.ART_KOSM_AIZ2_BOMBERSHIP_ADDR);
            if (bomberPatterns.length == 0) {
                LOG.warning("Failed to decompress bombership art");
                return;
            }

            // Bomb explosions: 12 frames (tile indices ~$08-$80).
            // Mapping pieces carry palette 1 ($20 byte). Sheet palette 0 means
            // pieces render at their native palette: (1+0)&3 = palette 1.
            // Palette 1 is patched with Pal_AIZBossSmall/Pal_AIZBattleship
            // during the bombing sequence.
            registerSheet(Sonic3kObjectArtKeys.AIZ2_BOMB_EXPLODE,
                    buildSheetFromPatterns(bomberPatterns, reader,
                            Sonic3kConstants.MAP_AIZ2_BOMB_EXPLODE_ADDR, 0));

            // Ship propeller: 4 frames (tile indices ~$00-$07).
            // Mapping pieces carry palette 2 ($A0 byte includes priority).
            registerSheet(Sonic3kObjectArtKeys.AIZ2_SHIP_PROPELLER,
                    buildSheetFromPatterns(bomberPatterns, reader,
                            Sonic3kConstants.MAP_AIZ_SHIP_PROPELLER_ADDR, 0));

            // Small boss craft: 1 frame (tile indices ~$86-$AC).
            // Mapping pieces carry palette 1 ($20 byte). Sheet palette 0 means
            // pieces render at their native palette: (1+0)&3 = palette 1.
            // The small boss patches palette 1 with Pal_AIZBossSmall at spawn time.
            registerSheet(Sonic3kObjectArtKeys.AIZ2_BOSS_SMALL,
                    buildSheetFromPatterns(bomberPatterns, reader,
                            Sonic3kConstants.MAP_AIZ2_BOSS_SMALL_ADDR, 0));

            // Background parallax trees: Nemesis-compressed, 1 frame, 4 stacked pieces
            // Mapping pieces carry palette 2 ($40 byte = priority + pal 2).
            // Sheet palette 0: rendered palette = (2+0)&3 = 2.
            Pattern[] treePatterns = PatternDecompressor.nemesis(rom,
                    Sonic3kConstants.ART_NEM_AIZ_BG_TREE_ADDR);
            if (treePatterns.length > 0) {
                registerSheet(Sonic3kObjectArtKeys.AIZ2_BG_TREE,
                        buildSheetFromPatterns(treePatterns, reader,
                                Sonic3kConstants.MAP_AIZ2_BG_TREE_ADDR, 0));
            }

            LOG.info(String.format("Loaded AIZ2 bombership art: %d tiles, " +
                            "bomb=%s, propeller=%s, small=%s, tree=%s",
                    bomberPatterns.length,
                    sheets.containsKey(Sonic3kObjectArtKeys.AIZ2_BOMB_EXPLODE) ? "ok" : "fail",
                    sheets.containsKey(Sonic3kObjectArtKeys.AIZ2_SHIP_PROPELLER) ? "ok" : "fail",
                    sheets.containsKey(Sonic3kObjectArtKeys.AIZ2_BOSS_SMALL) ? "ok" : "fail",
                    sheets.containsKey(Sonic3kObjectArtKeys.AIZ2_BG_TREE) ? "ok" : "fail"));
        } catch (IOException e) {
            LOG.warning("Failed to load AIZ2 bombership art: " + e.getMessage());
        }
    }

    /**
     * Decompresses KosinskiModuled art from the ROM into Pattern arrays.
     */
    private static Pattern[] decompressKosinskiModuled(Rom rom, int romAddr) throws IOException {
        byte[] header = rom.readBytes(romAddr, 2);
        if (header.length < 2) return new Pattern[0];
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
        long romSize = rom.getSize();
        if (romAddr + inputSize > romSize) {
            inputSize = (int) (romSize - romAddr);
        }
        byte[] compressed = rom.readBytes(romAddr, inputSize);
        byte[] data = KosinskiReader.decompressModuled(compressed, 0);
        return PatternDecompressor.fromBytes(data);
    }

    /**
     * Builds a sprite sheet from standalone Pattern[] and ROM mappings.
     * Use with {@link PlcParser#decompressEntry} or {@link PlcParser#decompressAll}
     * for PLC-based art loading without level buffer involvement.
     */
    private static ObjectSpriteSheet buildSheetFromPatterns(
            Pattern[] patterns, RomByteReader reader, int mappingAddr, int paletteIndex) {
        if (patterns == null || patterns.length == 0) return null;
        List<SpriteMappingFrame> mappings =
                S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr);
        return buildSheetFromPatterns(patterns, mappings, paletteIndex);
    }

    private static ObjectSpriteSheet buildSheetFromPatterns(
            Pattern[] patterns, List<SpriteMappingFrame> mappings, int paletteIndex) {
        if (patterns == null || patterns.length == 0) return null;
        if (mappings.isEmpty()) {
            return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
        }

        int minTile = Integer.MAX_VALUE;
        int maxTileExclusive = Integer.MIN_VALUE;
        for (SpriteMappingFrame frame : mappings) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                minTile = Math.min(minTile, piece.tileIndex());
                int pieceTiles = piece.widthTiles() * piece.heightTiles();
                maxTileExclusive = Math.max(maxTileExclusive, piece.tileIndex() + pieceTiles);
            }
        }

        if (minTile == Integer.MAX_VALUE) {
            return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
        }

        int tileRange = maxTileExclusive - minTile;
        Pattern[] sheetPatterns = patterns;
        int tileAdjustment = 0;
        if (maxTileExclusive <= patterns.length) {
            // Mapping tile words are relative to the decompressed source art.
            // Keep source tile N aligned with mapping tile N by slicing before
            // zero-basing the mappings. AIZ2 bombership sprites rely on this:
            // bomb/explosion frames start at tile $08 and the small boss at $86.
            if (minTile > 0) {
                sheetPatterns = Arrays.copyOfRange(patterns, minTile, maxTileExclusive);
                tileAdjustment = -minTile;
            }
        } else {
            // Some standalone sheets carry absolute VRAM tile numbers in their
            // mappings. In that case the decompressed art starts at mapping min.
            tileAdjustment = -minTile;
        }

        List<SpriteMappingFrame> adjustedMappings = Sonic3kObjectArt.adjustTileIndices(mappings, tileAdjustment);
        if (tileRange > sheetPatterns.length) {
            LOG.warning("Standalone S3K sheet mapping range exceeds pattern count"
                    + ": tileRange=" + tileRange
                    + " patterns=" + sheetPatterns.length);
        }
        return new ObjectSpriteSheet(sheetPatterns, adjustedMappings, paletteIndex, 1);
    }

    private static List<SpriteMappingFrame> loadMappingsFromAsmInclude(Path path) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }

        List<String> lines = Files.readAllLines(path);
        List<SpriteMappingFrame> frames = new ArrayList<>();
        List<SpriteMappingPiece> currentPieces = null;
        int remainingPieces = 0;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("Frame_")) {
                int dcw = line.indexOf("dc.w");
                if (dcw < 0) {
                    continue;
                }
                int pieceCount = parseAsmNumber(line.substring(dcw + 4).trim());
                currentPieces = new ArrayList<>(Math.max(pieceCount, 0));
                remainingPieces = pieceCount;
                if (pieceCount == 0) {
                    frames.add(new SpriteMappingFrame(currentPieces));
                    currentPieces = null;
                }
                continue;
            }

            if (remainingPieces <= 0 || currentPieces == null || !line.startsWith("dc.b")) {
                continue;
            }

            String[] parts = line.substring(4).split(",");
            if (parts.length < 6) {
                continue;
            }

            int yOffset = (byte) parseAsmNumber(parts[0].trim());
            int size = parseAsmNumber(parts[1].trim()) & 0xFF;
            int tileWord = ((parseAsmNumber(parts[2].trim()) & 0xFF) << 8)
                    | (parseAsmNumber(parts[3].trim()) & 0xFF);
            int xOffset = (short) (((parseAsmNumber(parts[4].trim()) & 0xFF) << 8)
                    | (parseAsmNumber(parts[5].trim()) & 0xFF));

            int widthTiles = ((size >> 2) & 0x3) + 1;
            int heightTiles = (size & 0x3) + 1;
            int tileIndex = tileWord & 0x7FF;
            boolean hFlip = (tileWord & 0x800) != 0;
            boolean vFlip = (tileWord & 0x1000) != 0;
            int paletteIndex = (tileWord >> 13) & 0x3;
            boolean priority = (tileWord & 0x8000) != 0;

            currentPieces.add(new SpriteMappingPiece(
                    xOffset, yOffset, widthTiles, heightTiles,
                    tileIndex, hFlip, vFlip, paletteIndex, priority));
            remainingPieces--;
            if (remainingPieces == 0) {
                frames.add(new SpriteMappingFrame(currentPieces));
                currentPieces = null;
            }
        }

        return frames;
    }

    private static int parseAsmNumber(String token) {
        String value = token.trim();
        boolean negative = value.startsWith("-");
        if (negative) {
            value = value.substring(1).trim();
        }
        int parsed;
        if (value.startsWith("$")) {
            parsed = Integer.parseInt(value.substring(1), 16);
        } else {
            parsed = Integer.parseInt(value);
        }
        return negative ? -parsed : parsed;
    }

    /**
     * Returns renderer keys whose level tile ranges overlap any of the given modified ranges.
     * Used by {@link Sonic3kPlcLoader#refreshAffectedRenderers} to find which
     * renderers need GPU texture re-upload after PLC application.
     */
    public List<String> getAffectedRendererKeys(List<Sonic3kPlcLoader.TileRange> modifiedRanges) {
        List<String> affected = new ArrayList<>();
        for (var entry : levelArtTileRanges.entrySet()) {
            Sonic3kPlcLoader.TileRange sheetRange = entry.getValue();
            int sheetStart = sheetRange.startTileIndex();
            int sheetEnd = sheetStart + sheetRange.tileCount();

            for (Sonic3kPlcLoader.TileRange modified : modifiedRanges) {
                int modStart = modified.startTileIndex();
                int modEnd = modStart + modified.tileCount();
                if (modStart < sheetEnd && modEnd > sheetStart) {
                    affected.add(entry.getKey());
                    break;
                }
            }
        }
        return affected;
    }

    private void registerSheet(String key, ObjectSpriteSheet sheet) {
        if (sheet == null) {
            return;
        }
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet);
        int existingIndex = rendererKeys.indexOf(key);
        if (existingIndex >= 0) {
            sheets.put(key, sheet);
            renderers.put(key, renderer);
            sheetOrder.set(existingIndex, sheet);
            rendererOrder.set(existingIndex, renderer);
            return;
        }
        sheets.put(key, sheet);
        renderers.put(key, renderer);
        rendererKeys.add(key);
        sheetOrder.add(sheet);
        rendererOrder.add(renderer);
    }

    /**
     * Refreshes an existing sheet's pattern data and re-uploads GPU textures.
     * Used after PLC reloads or boss defeat to ensure renderers show updated art.
     *
     * <p>Unlike creating a whole new renderer (which would lose the cached
     * {@code patternBase} and cause invisible sprites), this copies patterns
     * from the new sheet into the existing one and asks the renderer to
     * re-upload the affected GPU textures.
     *
     * @param key the art key to refresh
     * @param freshSheet a newly built sheet with up-to-date patterns
     */
    public void refreshSheetPatterns(String key, ObjectSpriteSheet freshSheet) {
        if (freshSheet == null) return;
        ObjectSpriteSheet existing = sheets.get(key);
        PatternSpriteRenderer renderer = renderers.get(key);
        if (existing == null || renderer == null) {
            // Not registered yet — register fresh so first draw works normally
            registerSheet(key, freshSheet);
            return;
        }

        // Copy fresh patterns into the existing sheet's array so the renderer
        // (which holds a reference to the same array) sees updated data.
        Pattern[] dst = existing.getPatterns();
        Pattern[] src = freshSheet.getPatterns();
        int count = Math.min(dst.length, src.length);
        System.arraycopy(src, 0, dst, 0, count);

        // Ask the renderer to re-upload the affected GPU textures.
        GraphicsManager gfx = GameServices.graphics();
        if (gfx != null && gfx.isGlInitialized() && renderer.isReady()) {
            renderer.updatePatternRange(gfx, 0, count);
        }
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
    public HudRenderManager.HudFlashMode getHudFlashMode() {
        return HudRenderManager.HudFlashMode.TEXT_HIDE;
    }

    @Override
    public List<String> getRendererKeys() {
        return new ArrayList<>(rendererKeys);
    }

    @Override
    public int ensurePatternsCached(GraphicsManager graphicsManager, int basePatternIndex) {
        int next = basePatternIndex;
        for (int i = 0; i < rendererOrder.size(); i++) {
            ObjectSpriteSheet sheet = sheetOrder.get(i);
            PatternSpriteRenderer renderer = rendererOrder.get(i);
            renderer.ensurePatternsCached(graphicsManager, next);
            next += sheet.getPatterns().length;
        }
        // Also cache DPLC-driven shield renderers
        for (PlayerSpriteRenderer dplcRenderer : dplcRenderers.values()) {
            dplcRenderer.ensureCached(graphicsManager);
        }
        return next;
    }

    @Override
    public boolean isReady() {
        // Ready if at least one renderer has been cached
        for (PatternSpriteRenderer renderer : renderers.values()) {
            if (renderer.isReady()) {
                return true;
            }
        }
        return false;
    }
}
