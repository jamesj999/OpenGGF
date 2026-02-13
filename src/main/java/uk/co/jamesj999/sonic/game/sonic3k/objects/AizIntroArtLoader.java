package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteDplcFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.level.render.TileLoadRequest;
import uk.co.jamesj999.sonic.tools.KosinskiReader;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized loader for all AIZ1 intro cinematic art, mappings, and palettes.
 *
 * <p>Called once during intro object initialization. Data is cached in static fields
 * and reused across all intro objects (plane, emeralds, waves, Knuckles).
 *
 * <p>Art sources:
 * <ul>
 *   <li>Plane art: KosinskiM at {@link Sonic3kConstants#ART_KOSM_AIZ_INTRO_PLANE_ADDR}</li>
 *   <li>Emerald art: KosinskiM at {@link Sonic3kConstants#ART_KOSM_AIZ_INTRO_EMERALDS_ADDR}</li>
 *   <li>Intro sprites (waves): Nemesis at {@link Sonic3kConstants#ART_NEM_AIZ_INTRO_SPRITES_ADDR}</li>
 *   <li>Cutscene Knuckles: Uncompressed at {@link Sonic3kConstants#ART_UNC_CUTSCENE_KNUX_ADDR}</li>
 * </ul>
 *
 * <p><b>S3K mapping format (6 bytes per piece):</b>
 * <pre>
 * byte 0:    yOffset (signed byte)
 * byte 1:    size (width bits 3:2, height bits 1:0)
 * bytes 2-3: tile word (16-bit BE: priority|pal|vflip|hflip|tileIndex)
 * bytes 4-5: xOffset (signed 16-bit BE)
 * </pre>
 * This differs from S2 which has an additional 2-byte "2P tile word" field (8 bytes per piece).
 */
public class AizIntroArtLoader {

    private static final Logger LOG = Logger.getLogger(AizIntroArtLoader.class.getName());

    /** Bytes per S3K mapping piece (no 2P tile word). */
    private static final int S3K_MAPPING_PIECE_SIZE = 6;

    // -----------------------------------------------------------------------
    // Cached data (loaded once, reused across intro objects)
    // -----------------------------------------------------------------------

    private static Pattern[] planePatterns;
    private static Pattern[] emeraldPatterns;
    private static Pattern[] introSpritesPatterns;
    private static Pattern[] knucklesPatterns;

    private static List<SpriteMappingFrame> planeMappings;
    private static List<SpriteMappingFrame> emeraldMappings;
    private static List<SpriteMappingFrame> waveMappings;
    private static List<SpriteMappingFrame> knucklesMappings;

    private static List<SpriteDplcFrame> knucklesDplcFrames;

    private static byte[] superSonicPaletteCycleData;
    private static byte[] cutsceneKnucklesPalette;
    private static byte[] emeraldPalette;

    private static ObjectSpriteSheet planeSheet;
    private static ObjectSpriteSheet emeraldSheet;
    private static ObjectSpriteSheet introSpritesSheet;
    private static ObjectSpriteSheet knucklesSheet;

    private static boolean loaded = false;

    // Renderer cache — lazy-initialized on first render call
    private static final int INTRO_PATTERN_BASE = 0x40000;
    private static PatternSpriteRenderer planeRenderer;
    private static PatternSpriteRenderer emeraldRenderer;
    private static PatternSpriteRenderer introSpritesRenderer;
    private static PatternSpriteRenderer knucklesRenderer;
    private static boolean renderersCached;

    private AizIntroArtLoader() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Loads all intro art, mappings, and palettes. Safe to call multiple times;
     * subsequent calls are no-ops if already loaded.
     */
    public static synchronized void loadAllIntroArt() {
        if (loaded) {
            return;
        }

        LOG.info("Loading AIZ1 intro art...");

        loadPlaneArt();
        loadEmeraldArt();
        loadIntroSpritesArt();
        loadKnucklesArt();

        loadPlaneMappings();
        loadEmeraldMappings();
        loadWaveMappings();
        loadKnucklesMappings();
        loadKnucklesDplc();

        loadSuperSonicPaletteCycleData();
        loadCutsceneKnucklesPalette();
        loadEmeraldPalette();

        buildSpriteSheets();

        loaded = true;
        LOG.info("AIZ1 intro art loaded successfully.");
    }

    /**
     * Resets all cached data, forcing a reload on the next call to
     * {@link #loadAllIntroArt()}. Intended for level transitions.
     */
    public static synchronized void reset() {
        planePatterns = null;
        emeraldPatterns = null;
        introSpritesPatterns = null;
        knucklesPatterns = null;
        planeMappings = null;
        emeraldMappings = null;
        waveMappings = null;
        knucklesMappings = null;
        knucklesDplcFrames = null;
        superSonicPaletteCycleData = null;
        cutsceneKnucklesPalette = null;
        emeraldPalette = null;
        planeSheet = null;
        emeraldSheet = null;
        introSpritesSheet = null;
        knucklesSheet = null;
        planeRenderer = null;
        emeraldRenderer = null;
        introSpritesRenderer = null;
        knucklesRenderer = null;
        renderersCached = false;
        loaded = false;
    }

    // -----------------------------------------------------------------------
    // Art loading
    // -----------------------------------------------------------------------

    /**
     * Decompresses KosinskiM art for the Tornado biplane sprite.
     * ROM address: {@link Sonic3kConstants#ART_KOSM_AIZ_INTRO_PLANE_ADDR}
     * Expected decompressed size: 4352 bytes (136 tiles).
     */
    public static void loadPlaneArt() {
        if (planePatterns != null) return;
        try {
            byte[] data = decompressKosinskiModuled(Sonic3kConstants.ART_KOSM_AIZ_INTRO_PLANE_ADDR);
            planePatterns = bytesToPatterns(data);
            LOG.fine("Loaded plane art: " + planePatterns.length + " patterns");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load plane art", e);
            planePatterns = new Pattern[0];
        }
    }

    /**
     * Decompresses KosinskiM art for the Chaos Emerald sprites.
     * ROM address: {@link Sonic3kConstants#ART_KOSM_AIZ_INTRO_EMERALDS_ADDR}
     * Expected decompressed size: 224 bytes (7 tiles).
     */
    public static void loadEmeraldArt() {
        if (emeraldPatterns != null) return;
        try {
            byte[] data = decompressKosinskiModuled(Sonic3kConstants.ART_KOSM_AIZ_INTRO_EMERALDS_ADDR);
            emeraldPatterns = bytesToPatterns(data);
            LOG.fine("Loaded emerald art: " + emeraldPatterns.length + " patterns");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load emerald art", e);
            emeraldPatterns = new Pattern[0];
        }
    }

    /**
     * Decompresses Nemesis art for the wave/water spray sprites.
     * ROM address: {@link Sonic3kConstants#ART_NEM_AIZ_INTRO_SPRITES_ADDR}
     * Expected decompressed size: 11008 bytes (344 tiles).
     */
    public static void loadIntroSpritesArt() {
        if (introSpritesPatterns != null) return;
        try {
            Rom rom = GameServices.rom().getRom();
            FileChannel channel = rom.getFileChannel();
            synchronized (rom) {
                channel.position(Sonic3kConstants.ART_NEM_AIZ_INTRO_SPRITES_ADDR);
                byte[] data = NemesisReader.decompress(channel);
                introSpritesPatterns = bytesToPatterns(data);
            }
            LOG.fine("Loaded intro sprites art: " + introSpritesPatterns.length + " patterns");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load intro sprites art", e);
            introSpritesPatterns = new Pattern[0];
        }
    }

    /**
     * Loads uncompressed cutscene Knuckles sprite art.
     * ROM address: {@link Sonic3kConstants#ART_UNC_CUTSCENE_KNUX_ADDR}
     * Size: {@link Sonic3kConstants#ART_UNC_CUTSCENE_KNUX_SIZE} (0x4EE0 = 20192 bytes, 631 tiles).
     */
    public static void loadKnucklesArt() {
        if (knucklesPatterns != null) return;
        try {
            Rom rom = GameServices.rom().getRom();
            byte[] data = rom.readBytes(
                    Sonic3kConstants.ART_UNC_CUTSCENE_KNUX_ADDR,
                    Sonic3kConstants.ART_UNC_CUTSCENE_KNUX_SIZE);
            knucklesPatterns = bytesToPatterns(data);
            LOG.fine("Loaded Knuckles art: " + knucklesPatterns.length + " patterns");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load Knuckles art", e);
            knucklesPatterns = new Pattern[0];
        }
    }

    // -----------------------------------------------------------------------
    // Mapping loading
    // -----------------------------------------------------------------------

    /**
     * Parses S3K sprite mappings for the Tornado biplane.
     * ROM address: {@link Sonic3kConstants#MAP_AIZ_INTRO_PLANE_ADDR}
     */
    public static void loadPlaneMappings() {
        if (planeMappings != null) return;
        try {
            RomByteReader reader = getReader();
            planeMappings = loadS3kMappingFrames(reader, Sonic3kConstants.MAP_AIZ_INTRO_PLANE_ADDR);
            LOG.fine("Loaded plane mappings: " + planeMappings.size() + " frames");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load plane mappings", e);
            planeMappings = List.of();
        }
    }

    /**
     * Parses S3K sprite mappings for the Chaos Emeralds.
     * ROM address: {@link Sonic3kConstants#MAP_AIZ_INTRO_EMERALDS_ADDR}
     */
    public static void loadEmeraldMappings() {
        if (emeraldMappings != null) return;
        try {
            RomByteReader reader = getReader();
            emeraldMappings = loadS3kMappingFrames(reader, Sonic3kConstants.MAP_AIZ_INTRO_EMERALDS_ADDR);
            LOG.fine("Loaded emerald mappings: " + emeraldMappings.size() + " frames");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load emerald mappings", e);
            emeraldMappings = List.of();
        }
    }

    /**
     * Parses S3K sprite mappings for the water wave/spray sprites.
     * ROM address: {@link Sonic3kConstants#MAP_AIZ_INTRO_WAVES_ADDR}
     */
    public static void loadWaveMappings() {
        if (waveMappings != null) return;
        try {
            RomByteReader reader = getReader();
            waveMappings = loadS3kMappingFrames(reader, Sonic3kConstants.MAP_AIZ_INTRO_WAVES_ADDR);
            LOG.fine("Loaded wave mappings: " + waveMappings.size() + " frames");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load wave mappings", e);
            waveMappings = List.of();
        }
    }

    /**
     * Parses S3K sprite mappings for cutscene Knuckles.
     * ROM address: {@link Sonic3kConstants#MAP_CUTSCENE_KNUX_ADDR}
     */
    public static void loadKnucklesMappings() {
        if (knucklesMappings != null) return;
        try {
            RomByteReader reader = getReader();
            knucklesMappings = loadS3kMappingFrames(reader, Sonic3kConstants.MAP_CUTSCENE_KNUX_ADDR);
            LOG.fine("Loaded Knuckles mappings: " + knucklesMappings.size() + " frames");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load Knuckles mappings", e);
            knucklesMappings = List.of();
        }
    }

    /**
     * Parses S3K DPLC frames for cutscene Knuckles.
     * ROM address: {@link Sonic3kConstants#DPLC_CUTSCENE_KNUX_ADDR}
     *
     * <p>S3K DPLC format (differs from S2):
     * <pre>
     * Offset table: word-sized offsets (first word = table size / frame count)
     * Per frame:
     *   word: request count
     *   Per request:
     *     word: (count-1 << 12) | startTile
     * </pre>
     */
    public static void loadKnucklesDplc() {
        if (knucklesDplcFrames != null) return;
        try {
            RomByteReader reader = getReader();
            knucklesDplcFrames = loadS3kDplcFrames(reader, Sonic3kConstants.DPLC_CUTSCENE_KNUX_ADDR);
            LOG.fine("Loaded Knuckles DPLC: " + knucklesDplcFrames.size() + " frames");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load Knuckles DPLC", e);
            knucklesDplcFrames = List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Palette loading
    // -----------------------------------------------------------------------

    /**
     * Reads Super Sonic palette cycle data (60 bytes = 10 entries x 6 bytes).
     * ROM address: {@link Sonic3kConstants#PAL_CYCLE_SUPER_SONIC_ADDR}
     *
     * <p>Each entry contains 3 words (6 bytes) representing Mega Drive colors
     * for the palette cycling effect during the intro Super Sonic sequence.
     */
    public static void loadSuperSonicPaletteCycleData() {
        if (superSonicPaletteCycleData != null) return;
        try {
            int size = Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_COUNT
                    * Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ENTRY_SIZE;
            Rom rom = GameServices.rom().getRom();
            superSonicPaletteCycleData = rom.readBytes(Sonic3kConstants.PAL_CYCLE_SUPER_SONIC_ADDR, size);
            LOG.fine("Loaded Super Sonic palette cycle data: " + superSonicPaletteCycleData.length + " bytes");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load Super Sonic palette cycle data", e);
            superSonicPaletteCycleData = new byte[0];
        }
    }

    /**
     * Reads cutscene Knuckles palette (32 bytes = 16 colors in Mega Drive format).
     * ROM address: {@link Sonic3kConstants#PAL_CUTSCENE_KNUX_ADDR}
     */
    public static void loadCutsceneKnucklesPalette() {
        if (cutsceneKnucklesPalette != null) return;
        try {
            Rom rom = GameServices.rom().getRom();
            cutsceneKnucklesPalette = rom.readBytes(Sonic3kConstants.PAL_CUTSCENE_KNUX_ADDR, 32);
            LOG.fine("Loaded cutscene Knuckles palette: " + cutsceneKnucklesPalette.length + " bytes");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load cutscene Knuckles palette", e);
            cutsceneKnucklesPalette = new byte[0];
        }
    }

    /**
     * Reads emerald palette (32 bytes = 16 colors in Mega Drive format).
     * ROM address: {@link Sonic3kConstants#PAL_AIZ_INTRO_EMERALDS_ADDR}
     */
    public static void loadEmeraldPalette() {
        if (emeraldPalette != null) return;
        try {
            Rom rom = GameServices.rom().getRom();
            emeraldPalette = rom.readBytes(Sonic3kConstants.PAL_AIZ_INTRO_EMERALDS_ADDR, 32);
            LOG.fine("Loaded emerald palette: " + emeraldPalette.length + " bytes");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load emerald palette", e);
            emeraldPalette = new byte[0];
        }
    }

    // -----------------------------------------------------------------------
    // Sprite sheet accessors
    // -----------------------------------------------------------------------

    /** Returns the plane sprite sheet, or null if not loaded. */
    public static ObjectSpriteSheet getPlaneSheet() {
        return planeSheet;
    }

    /** Returns the emerald sprite sheet, or null if not loaded. */
    public static ObjectSpriteSheet getEmeraldSheet() {
        return emeraldSheet;
    }

    /** Returns the intro sprites (waves) sprite sheet, or null if not loaded. */
    public static ObjectSpriteSheet getIntroSpritesSheet() {
        return introSpritesSheet;
    }

    /** Returns the Knuckles sprite sheet, or null if not loaded. */
    public static ObjectSpriteSheet getKnucklesSheet() {
        return knucklesSheet;
    }

    // -----------------------------------------------------------------------
    // Raw data accessors (for callers that need direct access)
    // -----------------------------------------------------------------------

    /** Returns plane pattern tiles, or empty array if not loaded. */
    public static Pattern[] getPlanePatterns() {
        return planePatterns != null ? planePatterns : new Pattern[0];
    }

    /** Returns emerald pattern tiles, or empty array if not loaded. */
    public static Pattern[] getEmeraldPatterns() {
        return emeraldPatterns != null ? emeraldPatterns : new Pattern[0];
    }

    /** Returns intro sprites pattern tiles (waves/spray), or empty array if not loaded. */
    public static Pattern[] getIntroSpritesPatterns() {
        return introSpritesPatterns != null ? introSpritesPatterns : new Pattern[0];
    }

    /** Returns Knuckles pattern tiles, or empty array if not loaded. */
    public static Pattern[] getKnucklesPatterns() {
        return knucklesPatterns != null ? knucklesPatterns : new Pattern[0];
    }

    /** Returns plane mapping frames, or empty list if not loaded. */
    public static List<SpriteMappingFrame> getPlaneMappings() {
        return planeMappings != null ? planeMappings : List.of();
    }

    /** Returns emerald mapping frames, or empty list if not loaded. */
    public static List<SpriteMappingFrame> getEmeraldMappings() {
        return emeraldMappings != null ? emeraldMappings : List.of();
    }

    /** Returns wave mapping frames, or empty list if not loaded. */
    public static List<SpriteMappingFrame> getWaveMappings() {
        return waveMappings != null ? waveMappings : List.of();
    }

    /** Returns Knuckles mapping frames, or empty list if not loaded. */
    public static List<SpriteMappingFrame> getKnucklesMappings() {
        return knucklesMappings != null ? knucklesMappings : List.of();
    }

    /** Returns Knuckles DPLC frames, or empty list if not loaded. */
    public static List<SpriteDplcFrame> getKnucklesDplcFrames() {
        return knucklesDplcFrames != null ? knucklesDplcFrames : List.of();
    }

    /** Returns Super Sonic palette cycle data (60 bytes), or empty array if not loaded. */
    public static byte[] getSuperSonicPaletteCycleData() {
        return superSonicPaletteCycleData != null ? superSonicPaletteCycleData : new byte[0];
    }

    /** Returns cutscene Knuckles palette (32 bytes), or empty array if not loaded. */
    public static byte[] getCutsceneKnucklesPalette() {
        return cutsceneKnucklesPalette != null ? cutsceneKnucklesPalette : new byte[0];
    }

    /** Returns emerald palette (32 bytes), or empty array if not loaded. */
    public static byte[] getEmeraldPalette() {
        return emeraldPalette != null ? emeraldPalette : new byte[0];
    }

    /** Returns whether all intro art has been loaded. */
    public static boolean isLoaded() {
        return loaded;
    }

    // -----------------------------------------------------------------------
    // Renderer cache
    // -----------------------------------------------------------------------

    /**
     * Lazily creates PatternSpriteRenderers and uploads patterns to GPU.
     * Safe to call every frame — no-ops if already cached.
     */
    public static void ensureRenderersCached() {
        if (renderersCached || !loaded) return;
        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || !gm.isGlInitialized()) return;

        int nextBase = INTRO_PATTERN_BASE;

        planeRenderer = new PatternSpriteRenderer(planeSheet);
        planeRenderer.ensurePatternsCached(gm, nextBase);
        nextBase += planePatterns.length;

        emeraldRenderer = new PatternSpriteRenderer(emeraldSheet);
        emeraldRenderer.ensurePatternsCached(gm, nextBase);
        nextBase += emeraldPatterns.length;

        introSpritesRenderer = new PatternSpriteRenderer(introSpritesSheet);
        introSpritesRenderer.ensurePatternsCached(gm, nextBase);
        nextBase += introSpritesPatterns.length;

        knucklesRenderer = new PatternSpriteRenderer(knucklesSheet);
        knucklesRenderer.ensurePatternsCached(gm, nextBase);

        renderersCached = true;
        LOG.info("AIZ intro renderers cached. Pattern bases: plane=" +
                Integer.toHexString(INTRO_PATTERN_BASE) + " total patterns=" +
                (planePatterns.length + emeraldPatterns.length +
                 introSpritesPatterns.length + knucklesPatterns.length));
    }

    /** Returns the plane renderer, lazily caching if needed. */
    public static PatternSpriteRenderer getPlaneRenderer() {
        ensureRenderersCached();
        return planeRenderer;
    }

    /** Returns the emerald renderer, lazily caching if needed. */
    public static PatternSpriteRenderer getEmeraldRenderer() {
        ensureRenderersCached();
        return emeraldRenderer;
    }

    /** Returns the intro sprites (waves) renderer, lazily caching if needed. */
    public static PatternSpriteRenderer getIntroSpritesRenderer() {
        ensureRenderersCached();
        return introSpritesRenderer;
    }

    /** Returns the Knuckles renderer, lazily caching if needed. */
    public static PatternSpriteRenderer getKnucklesRenderer() {
        ensureRenderersCached();
        return knucklesRenderer;
    }

    // -----------------------------------------------------------------------
    // S3K mapping parser (6 bytes per piece)
    // -----------------------------------------------------------------------

    /**
     * Parses S3K sprite mapping frames from ROM.
     *
     * <p>S3K uses 6 bytes per mapping piece (vs S2's 8 bytes), with no 2P tile word:
     * <pre>
     * Offset table at mappingAddr:
     *   First word = size of offset table (frameCount = firstWord / 2)
     *   Subsequent words = per-frame offsets relative to mappingAddr
     *
     * Per frame (at mappingAddr + offset[i]):
     *   word: piece count
     *   Per piece (6 bytes):
     *     byte: yOffset (signed)
     *     byte: size (width bits 3:2, height bits 1:0)
     *     word: tile word (priority|pal|vflip|hflip|tileIndex)
     *     word: xOffset (signed)
     * </pre>
     *
     * @param reader  ROM byte reader
     * @param mappingAddr  ROM address of the mapping table
     * @return list of parsed mapping frames
     */
    public static List<SpriteMappingFrame> loadS3kMappingFrames(RomByteReader reader, int mappingAddr) {
        int offsetTableSize = reader.readU16BE(mappingAddr);
        int frameCount = offsetTableSize / 2;

        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int rawOffset = reader.readU16BE(mappingAddr + i * 2);
            // Treat as signed 16-bit offset (negative offsets reference other tables)
            int signedOffset = (rawOffset > 32767) ? rawOffset - 65536 : rawOffset;
            int frameAddr = mappingAddr + signedOffset;

            int pieceCount = reader.readU16BE(frameAddr);
            frameAddr += 2;

            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                // byte 0: yOffset (signed byte)
                int yOffset = (byte) reader.readU8(frameAddr);
                frameAddr += 1;

                // byte 1: size (width bits 3:2, height bits 1:0)
                int size = reader.readU8(frameAddr);
                frameAddr += 1;

                // bytes 2-3: tile word (16-bit BE)
                int tileWord = reader.readU16BE(frameAddr);
                frameAddr += 2;

                // bytes 4-5: xOffset (signed 16-bit BE)
                // NOTE: No 2P tile word in S3K format (unlike S2 which has +2 bytes here)
                int xOffset = (short) reader.readU16BE(frameAddr);
                frameAddr += 2;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileIndex = tileWord & 0x7FF;
                boolean hFlip = (tileWord & 0x800) != 0;
                boolean vFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;
                boolean priority = (tileWord & 0x8000) != 0;

                pieces.add(new SpriteMappingPiece(
                        xOffset, yOffset, widthTiles, heightTiles,
                        tileIndex, hFlip, vFlip, paletteIndex, priority));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }
        return frames;
    }

    /**
     * Parses S3K sprite mapping frames with a tile index offset applied.
     * Used when mapping tile indices reference VRAM positions that differ
     * from the pattern array's zero-based indexing.
     *
     * @param reader      ROM byte reader
     * @param mappingAddr ROM address of the mapping table
     * @param tileOffset  Offset to add to each tile index (use negative to subtract)
     * @return list of parsed mapping frames with adjusted tile indices
     */
    public static List<SpriteMappingFrame> loadS3kMappingFramesWithTileOffset(
            RomByteReader reader, int mappingAddr, int tileOffset) {
        List<SpriteMappingFrame> raw = loadS3kMappingFrames(reader, mappingAddr);
        if (tileOffset == 0) {
            return raw;
        }

        List<SpriteMappingFrame> adjusted = new ArrayList<>(raw.size());
        for (SpriteMappingFrame frame : raw) {
            List<SpriteMappingPiece> pieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                int adjustedTile = Math.max(0, piece.tileIndex() + tileOffset);
                pieces.add(new SpriteMappingPiece(
                        piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(),
                        adjustedTile, piece.hFlip(), piece.vFlip(),
                        piece.paletteIndex(), piece.priority()));
            }
            adjusted.add(new SpriteMappingFrame(pieces));
        }
        return adjusted;
    }

    // -----------------------------------------------------------------------
    // S3K DPLC parser
    // -----------------------------------------------------------------------

    /**
     * Parses S3K DPLC (Dynamic Pattern Loading Cue) frames from ROM.
     *
     * <p>Format is the same as S2 DPLCs: word-sized offset table, then per-frame
     * entries with a word count followed by word-sized (count-1|startTile) entries.
     *
     * @param reader   ROM byte reader
     * @param dplcAddr ROM address of the DPLC table
     * @return list of parsed DPLC frames
     */
    public static List<SpriteDplcFrame> loadS3kDplcFrames(RomByteReader reader, int dplcAddr) {
        int offsetTableSize = reader.readU16BE(dplcAddr);
        int frameCount = offsetTableSize / 2;

        List<SpriteDplcFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = dplcAddr + reader.readU16BE(dplcAddr + i * 2);
            int requestCount = reader.readU16BE(frameAddr);
            frameAddr += 2;

            List<TileLoadRequest> requests = new ArrayList<>(requestCount);
            for (int r = 0; r < requestCount; r++) {
                int entry = reader.readU16BE(frameAddr);
                frameAddr += 2;
                int count = ((entry >> 12) & 0xF) + 1;
                int startTile = entry & 0x0FFF;
                requests.add(new TileLoadRequest(startTile, count));
            }
            frames.add(new SpriteDplcFrame(requests));
        }
        return frames;
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Decompresses KosinskiM data from the given ROM address.
     * Reads enough data from ROM to cover the compressed stream, then
     * delegates to {@link KosinskiReader#decompressModuled(byte[], int)}.
     */
    private static byte[] decompressKosinskiModuled(int romAddr) throws IOException {
        Rom rom = GameServices.rom().getRom();

        // Read KosM 2-byte BE header: total decompressed size
        byte[] header = rom.readBytes(romAddr, 2);
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);

        // Compressed data is smaller than decompressed. Read enough for input.
        // Add padding for module alignment. Cap at 256KB.
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);

        // Guard against reading past end of ROM
        long romSize = rom.getSize();
        if (romAddr + inputSize > romSize) {
            inputSize = (int) (romSize - romAddr);
        }

        byte[] romData = rom.readBytes(romAddr, inputSize);
        return KosinskiReader.decompressModuled(romData, 0);
    }

    /**
     * Converts raw decompressed bytes (4bpp Sega format) into a {@link Pattern} array.
     * Each pattern is 32 bytes in ROM (8x8 pixels, 2 pixels per byte).
     */
    private static Pattern[] bytesToPatterns(byte[] data) {
        if (data == null || data.length == 0) {
            return new Pattern[0];
        }

        int patternCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[patternCount];

        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            byte[] subArray = Arrays.copyOfRange(
                    data,
                    i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(subArray);
        }
        return patterns;
    }

    /**
     * Creates a {@link RomByteReader} from the current ROM.
     */
    private static RomByteReader getReader() throws IOException {
        return RomByteReader.fromRom(GameServices.rom().getRom());
    }

    /**
     * Builds {@link ObjectSpriteSheet} instances from loaded art and mappings.
     * Called after all individual load methods have completed.
     */
    private static void buildSpriteSheets() {
        // Plane sheet: palette index 0, frame delay 1
        if (planePatterns != null && planeMappings != null) {
            planeSheet = new ObjectSpriteSheet(planePatterns, planeMappings, 0, 1);
        }

        // Emerald sheet: palette index 2 (emerald palette line), frame delay 1
        if (emeraldPatterns != null && emeraldMappings != null) {
            emeraldSheet = new ObjectSpriteSheet(emeraldPatterns, emeraldMappings, 2, 1);
        }

        // Intro sprites (waves) sheet: palette index 0, frame delay 1
        if (introSpritesPatterns != null && waveMappings != null) {
            introSpritesSheet = new ObjectSpriteSheet(introSpritesPatterns, waveMappings, 0, 1);
        }

        // Knuckles sheet: palette index 1 (Knuckles palette line), frame delay 1
        if (knucklesPatterns != null && knucklesMappings != null) {
            knucklesSheet = new ObjectSpriteSheet(knucklesPatterns, knucklesMappings, 1, 1);
        }
    }
}
