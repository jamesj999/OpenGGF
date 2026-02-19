package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.HudRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteDplcFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;
import uk.co.jamesj999.sonic.sprites.render.PlayerSpriteRenderer;
import uk.co.jamesj999.sonic.tools.NemesisReader;

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

        // Load shield art (DPLC-driven, same for all zones)
        loadShieldArt();

        if (zoneIndex == 0x00) {
            loadAizBadnikArt();
        }

        // Level-art sheets are registered later via registerLevelArtSheets()
        // since the level must be loaded first
        LOG.info("Sonic3kObjectArtProvider initialized for zone " + zoneIndex);
    }

    private void loadAizBadnikArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) {
                return;
            }
            RomByteReader reader = RomByteReader.fromRom(rom);
            Sonic3kObjectArt art = new Sonic3kObjectArt(null, reader);
            registerSheet(Sonic3kObjectArtKeys.BLOOMINATOR, art.loadBloominatorSheet(rom));
            registerSheet(Sonic3kObjectArtKeys.RHINOBOT, art.loadRhinobotSheet(rom));
            registerSheet(Sonic3kObjectArtKeys.MONKEY_DUDE, art.loadMonkeyDudeSheet(rom));
            LOG.info("Loaded AIZ badnik art sheets (Bloominator, Rhinobot, MonkeyDude)");
        } catch (IOException e) {
            LOG.warning("Failed to load AIZ badnik art: " + e.getMessage());
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
        if (level == null) {
            return;
        }

        RomByteReader reader = null;
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom != null) {
                reader = RomByteReader.fromRom(rom);
            }
        } catch (IOException e) {
            LOG.warning("Failed to create RomByteReader for level art: " + e.getMessage());
        }
        Sonic3kObjectArt art = new Sonic3kObjectArt(level, reader);

        // Spikes and springs appear in all zones
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPIKES, art.buildSpikesSheet(), art);
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPRING_VERTICAL, art.buildSpringVerticalSheet(), art);
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPRING_VERTICAL_YELLOW, art.buildSpringVerticalYellowSheet(), art);
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPRING_HORIZONTAL, art.buildSpringHorizontalSheet(), art);
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPRING_HORIZONTAL_YELLOW, art.buildSpringHorizontalYellowSheet(), art);
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPRING_DIAGONAL, art.buildSpringDiagonalSheet(), art);
        registerLevelArtSheet(Sonic3kObjectArtKeys.SPRING_DIAGONAL_YELLOW, art.buildSpringDiagonalYellowSheet(), art);

        // AIZ objects (zone index 0 = AIZ in S3K)
        if (zoneIndex == 0x00) {
            registerLevelArtSheet(Sonic3kObjectArtKeys.AIZ_RIDE_VINE, art.buildAizRideVineSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.ANIMATED_STILL_SPRITES, art.buildAnimatedStillSpritesSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.AIZ1_TREE, art.buildAiz1TreeSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.AIZ1_ZIPLINE_PEG, art.buildAiz1ZiplinePegSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.AIZ_FOREGROUND_PLANT, art.buildAizForegroundPlantSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.AIZ1_ROCK, art.buildAiz1RockSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.AIZ2_ROCK, art.buildAiz2RockSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_AIZ1, art.buildCollapsingPlatformAiz1Sheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_AIZ2, art.buildCollapsingPlatformAiz2Sheet(), art);
        }

        // ICZ objects (zone index 5 = ICZ in S3K)
        if (zoneIndex == 0x05) {
            registerLevelArtSheet(Sonic3kObjectArtKeys.COLLAPSING_PLATFORM_ICZ, art.buildCollapsingPlatformIczSheet(), art);
        }

        // LRZ objects (zone index 9 = LRZ in S3K)
        if (zoneIndex == 0x09) {
            registerLevelArtSheet(Sonic3kObjectArtKeys.LRZ1_ROCK, art.buildLrz1RockSheet(), art);
            registerLevelArtSheet(Sonic3kObjectArtKeys.LRZ2_ROCK, art.buildLrz2RockSheet(), art);
        }

        LOG.info("Sonic3kObjectArtProvider registered " + rendererKeys.size()
                + " level-art sheets for zone " + zoneIndex);
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
