package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
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
import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
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

        // Load shield art (DPLC-driven, same for all zones)
        loadShieldArt();

        // Get act index from LevelManager (available during level load)
        currentActIndex = LevelManager.getInstance().getCurrentAct();
        Sonic3kPlcArtRegistry.ZoneArtPlan plan =
                Sonic3kPlcArtRegistry.getPlan(zoneIndex, currentActIndex);
        loadStandaloneFromRegistry(plan);
        // PLC-based boss art stays separate for now
        if (zoneIndex == 0x00) {
            loadAizMinibossArtFromPlc();
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

        // Lives icon - Nemesis compressed
        hudLivesPatterns = loadNemesisPatterns(rom, Sonic3kConstants.ART_NEM_SONIC_LIFE_ICON_ADDR);
        LOG.info("Loaded " + (hudLivesPatterns != null ? hudLivesPatterns.length : 0) + " HUD lives icon patterns");

        // Lives digits (0-9) - uncompressed
        hudLivesNumbers = loadUncompressedPatterns(rom,
                Sonic3kConstants.ART_UNC_LIVES_DIGITS_ADDR,
                Sonic3kConstants.ART_UNC_LIVES_DIGITS_SIZE);
        LOG.info("Loaded " + (hudLivesNumbers != null ? hudLivesNumbers.length : 0) + " HUD lives digit patterns");
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

    private Pattern[] loadNemesisPatterns(Rom rom, int addr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(addr);
        byte[] data = NemesisReader.decompress(channel);
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

        Pattern[] patterns = loadNemesisPatterns(rom, Sonic3kConstants.ART_NEM_EXPLOSION_ADDR);

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
        Pattern[] monitorBasePatterns = loadNemesisPatterns(rom, Sonic3kConstants.ART_NEM_MONITORS_ADDR);

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
        Pattern[] allPatterns = loadNemesisPatterns(rom, Sonic3kConstants.ART_NEM_ENEMY_PTS_STARPOST_ADDR);

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

        LOG.info("Loaded S3K StarPost art: " + allPatterns.length + " patterns, "
                + adjusted.size() + " frames, " + adjustedStars.size() + " star frames");
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

            loadSingleShieldArt(reader, Sonic3kObjectArtKeys.BUBBLE_SHIELD,
                    Sonic3kConstants.ART_UNC_BUBBLE_SHIELD_ADDR, Sonic3kConstants.ART_UNC_BUBBLE_SHIELD_SIZE,
                    Sonic3kConstants.MAP_BUBBLE_SHIELD_ADDR, Sonic3kConstants.DPLC_BUBBLE_SHIELD_ADDR,
                    Sonic3kConstants.ANI_BUBBLE_SHIELD_ADDR, Sonic3kConstants.ANI_BUBBLE_SHIELD_COUNT,
                    Sonic3kConstants.ART_TILE_SHIELD, 0);
        } catch (IOException e) {
            LOG.warning("Failed to load shield art: " + e.getMessage());
        }
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

    /** Returns the DPLC-driven renderer for a shield type, or null. */
    public PlayerSpriteRenderer getShieldDplcRenderer(String key) {
        return dplcRenderers.get(key);
    }

    /** Returns the art set for a shield type, or null. */
    public SpriteArtSet getShieldArtSet(String key) {
        return shieldArtSets.get(key);
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
                        entry.frameFilter());
            } else if (entry.mappingAddr() > 0) {
                sheet = art.buildLevelArtSheetFromRom(
                        entry.mappingAddr(), entry.artTileBase(), entry.palette());
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
                registerSheet(Sonic2ObjectArtKeys.BOSS_EXPLOSION,
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
     * Builds a sprite sheet from standalone Pattern[] and ROM mappings.
     * Use with {@link PlcParser#decompressEntry} or {@link PlcParser#decompressAll}
     * for PLC-based art loading without level buffer involvement.
     */
    private static ObjectSpriteSheet buildSheetFromPatterns(
            Pattern[] patterns, RomByteReader reader, int mappingAddr, int paletteIndex) {
        if (patterns == null || patterns.length == 0) return null;
        List<SpriteMappingFrame> mappings =
                S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr);
        return new ObjectSpriteSheet(patterns, mappings, paletteIndex, 1);
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
        sheets.put(key, sheet);
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet);
        renderers.put(key, renderer);
        rendererKeys.add(key);
        sheetOrder.add(sheet);
        rendererOrder.add(renderer);
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
        return -1;
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
