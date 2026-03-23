package com.openggf.game.sonic2.titlescreen;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tools.EnigmaReader;
import com.openggf.util.PatternDecompressor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads title screen graphics and data from the Sonic 2 ROM.
 *
 * <p>Graphics loaded:
 * <ul>
 *   <li>ArtNem_Title - Background patterns (Nemesis compressed)</li>
 * </ul>
 *
 * <p>Mappings loaded:
 * <ul>
 *   <li>MapEng_TitleScreen - Plane B background (40x28, Enigma compressed)</li>
 *   <li>MapEng_TitleBack - Plane B water/horizon (24x28, Enigma compressed)</li>
 *   <li>MapEng_TitleLogo - Plane A logo/emblem (40x28, Enigma compressed)</li>
 * </ul>
 *
 * <p>Palettes loaded:
 * <ul>
 *   <li>Pal_Title - Title screen palette (1 palette line, loaded at line 1)</li>
 * </ul>
 */
public class TitleScreenDataLoader {
    private static final Logger LOGGER = Logger.getLogger(TitleScreenDataLoader.class.getName());

    /** Pattern base ID for GPU caching (unique, avoids level select 0x50000, title card 0x40000). */
    static final int PATTERN_BASE = 0x60000;

    /** Pattern base ID for sprite art (separate from background). */
    static final int SPRITE_PATTERN_BASE = 0x70000;

    /** Pattern base ID for credit text art (separate from background and sprites). */
    static final int CREDIT_TEXT_PATTERN_BASE = 0x80000;

    // Loaded art patterns
    private Pattern[] titlePatterns;

    // Sprite art patterns (Sonic/Tails/stars from title screen intro)
    private Pattern[] spritePatterns;

    // Credit text patterns (intro "SONIC AND MILES 'TAILS' PROWER IN" screen)
    private Pattern[] creditTextPatterns;

    // Intro palette (palette line 0 for credit text - uses Sonic/Tails palette)
    private Palette introPalette;

    // Plane B composed map: cols 0-39 from TitleScreen, cols 40-63 from TitleBack
    private int[] planeBMap;
    private static final int PLANE_B_WIDTH = 64;
    private static final int PLANE_B_HEIGHT = 28;

    // Plane A logo map (40x28)
    private int[] planeAMap;
    private static final int PLANE_A_WIDTH = 40;
    private static final int PLANE_A_HEIGHT = 28;

    // Title palettes (3 separate palettes for different VDP palette lines)
    // Line 1: Pal_Title (Title screen.bin) - loaded via PalLoad_ForFade
    private Palette titlePalette;
    // Line 2: Pal_1340C (Title Background.bin) - used by Plane B (palette index 2)
    private Palette backgroundPalette;
    // Line 3: Pal_1342C (Title Emblem.bin) - used by Plane A logo (palette index 3)
    private Palette emblemPalette;
    // Line 0: Pal_133EC (Title Sonic.bin) - Sonic's palette, loaded at palette line 0
    private Palette sonicPalette;

    private boolean dataLoaded = false;
    private boolean artCached = false;

    /**
     * Loads all title screen data from ROM.
     *
     * @return true if data was loaded successfully
     */
    public boolean loadData() {
        if (dataLoaded) {
            return true;
        }

        RomManager romManager = GameServices.rom();
        if (!romManager.isRomAvailable()) {
            LOGGER.warning("ROM not available for title screen data loading");
            return false;
        }

        Rom rom;
        try {
            rom = romManager.getRom();
        } catch (IOException e) {
            LOGGER.warning("Failed to get ROM: " + e.getMessage());
            return false;
        }

        try {
            // Load Nemesis-compressed art (background: ~180 patterns, 8KB buffer is sufficient)
            titlePatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_TITLE_ADDR, 8192, "Title");
            LOGGER.info("Loaded title patterns: " + (titlePatterns != null ? titlePatterns.length : 0));

            // Load sprite art (Sonic/Tails/stars: 674 patterns)
            spritePatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_TITLE_SPRITES_ADDR, 65536, "TitleSprites");
            LOGGER.info("Loaded title sprite patterns: " + (spritePatterns != null ? spritePatterns.length : 0));

            // Load MenuJunk art (extra tiles at VRAM 0x03F2, which is sprite index 0x2A2)
            // These tiles contain Sonic's right arm (frame 18 last piece) and Tails' hand (frame 19)
            Pattern[] menuJunkPatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_MENU_JUNK_ADDR, 1024, "MenuJunk");
            LOGGER.info("Loaded MenuJunk patterns: " + (menuJunkPatterns != null ? menuJunkPatterns.length : 0));

            // Extend spritePatterns to include MenuJunk at the correct offset (0x2A2)
            if (spritePatterns != null && menuJunkPatterns != null && menuJunkPatterns.length > 0) {
                int menuJunkOffset = 0x2A2; // VRAM 0x03F2 - base 0x0150
                int totalSize = menuJunkOffset + menuJunkPatterns.length;
                Pattern[] extended = new Pattern[totalSize];
                System.arraycopy(spritePatterns, 0, extended, 0, spritePatterns.length);
                System.arraycopy(menuJunkPatterns, 0, extended, menuJunkOffset, menuJunkPatterns.length);
                spritePatterns = extended;
                LOGGER.info("Extended sprite patterns with MenuJunk: total " + spritePatterns.length +
                        " (MenuJunk at offset 0x" + Integer.toHexString(menuJunkOffset) + ")");
            }

            // Load credit text art (64 Nemesis patterns for intro text screen)
            creditTextPatterns = PatternDecompressor.nemesis(rom, Sonic2Constants.ART_NEM_CREDIT_TEXT_ADDR, 4096, "CreditText");
            LOGGER.info("Loaded credit text patterns: " + (creditTextPatterns != null ? creditTextPatterns.length : 0));

            // Load intro palette (Sonic/Tails palette used as text color on the intro screen)
            loadIntroPalette(rom);

            // Load Enigma-compressed mappings and compose plane B
            loadPlaneMaps(rom);

            // Load palettes
            loadTitlePalette(rom);
            loadBackgroundPalette(rom);
            loadEmblemPalette(rom);
            loadSonicPalette(rom);

            dataLoaded = true;
            return true;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load title screen data", e);
            return false;
        }
    }


    /**
     * Loads and composes the plane maps from Enigma-compressed data.
     *
     * <p>From the disassembly:
     * <ul>
     *   <li>MapEng_TitleScreen: Plane B, 40x28, startingArtTile = make_art_tile(0, 2, 0) = 0x4000</li>
     *   <li>MapEng_TitleBack: Plane B cols 40-63, 24x28, same startingArtTile</li>
     *   <li>MapEng_TitleLogo: Plane A, 40x28, startingArtTile = make_art_tile(0, 3, 1) = 0xE000</li>
     * </ul>
     */
    private void loadPlaneMaps(Rom rom) {
        // Plane B background (40x28)
        int[] titleScreen = loadEnigmaMap(rom, Sonic2Constants.MAP_ENI_TITLE_SCREEN_ADDR, 0x4000, "TitleScreen");
        // Plane B water portion (24x28)
        int[] titleBack = loadEnigmaMap(rom, Sonic2Constants.MAP_ENI_TITLE_BACK_ADDR, 0x4000, "TitleBack");
        // Plane A logo (40x28)
        planeAMap = loadEnigmaMap(rom, Sonic2Constants.MAP_ENI_TITLE_LOGO_ADDR, 0xE000, "TitleLogo");

        // Compose Plane B: 64-tile wide plane
        // Cols 0-39 from TitleScreen, cols 40-63 from TitleBack
        planeBMap = new int[PLANE_B_WIDTH * PLANE_B_HEIGHT];

        if (titleScreen != null && titleScreen.length > 0) {
            int srcWidth = 40;
            for (int row = 0; row < PLANE_B_HEIGHT; row++) {
                for (int col = 0; col < srcWidth; col++) {
                    int srcIdx = row * srcWidth + col;
                    if (srcIdx < titleScreen.length) {
                        planeBMap[row * PLANE_B_WIDTH + col] = titleScreen[srcIdx];
                    }
                }
            }
        }

        if (titleBack != null && titleBack.length > 0) {
            int srcWidth = 24;
            for (int row = 0; row < PLANE_B_HEIGHT; row++) {
                for (int col = 0; col < srcWidth; col++) {
                    int srcIdx = row * srcWidth + col;
                    if (srcIdx < titleBack.length) {
                        planeBMap[row * PLANE_B_WIDTH + (40 + col)] = titleBack[srcIdx];
                    }
                }
            }
        }

        LOGGER.info("Composed Plane B: " + PLANE_B_WIDTH + "x" + PLANE_B_HEIGHT +
                ", Plane A: " + PLANE_A_WIDTH + "x" + PLANE_A_HEIGHT);
    }

    /**
     * Loads a single Enigma-compressed map.
     */
    private int[] loadEnigmaMap(Rom rom, int address, int startingArtTile, String name) {
        try {
            byte[] compressed = rom.readBytes(address, 1024);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = EnigmaReader.decompress(channel, startingArtTile);

                int wordCount = decompressed.length / 2;
                int[] map = new int[wordCount];
                ByteBuffer buf = ByteBuffer.wrap(decompressed);
                for (int i = 0; i < wordCount; i++) {
                    map[i] = buf.getShort() & 0xFFFF;
                }

                LOGGER.info("Loaded " + name + " map: " + wordCount + " tiles");
                return map;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " map: " + e.getMessage());
            return new int[0];
        }
    }

    /**
     * Loads the title screen palette from ROM.
     * The palette is 32 bytes (1 palette line) loaded at palette line 1.
     */
    private void loadTitlePalette(Rom rom) {
        try {
            byte[] paletteData = rom.readBytes(Sonic2Constants.PAL_TITLE_ADDR, Sonic2Constants.PAL_TITLE_SIZE);
            titlePalette = new Palette();
            titlePalette.fromSegaFormat(paletteData);

            LOGGER.info("Loaded title palette from ROM at 0x" +
                    Integer.toHexString(Sonic2Constants.PAL_TITLE_ADDR));
        } catch (Exception e) {
            LOGGER.warning("Failed to load title palette: " + e.getMessage());
            titlePalette = null;
        }
    }

    /**
     * Loads the title background palette from ROM (Pal_1340C = Title Background.bin).
     * This palette is used by Plane B tiles (VDP palette line 2).
     */
    private void loadBackgroundPalette(Rom rom) {
        try {
            byte[] paletteData = rom.readBytes(Sonic2Constants.PAL_TITLE_BACKGROUND_ADDR,
                    Sonic2Constants.PAL_TITLE_BACKGROUND_SIZE);
            backgroundPalette = new Palette();
            backgroundPalette.fromSegaFormat(paletteData);

            LOGGER.info("Loaded title background palette from ROM at 0x" +
                    Integer.toHexString(Sonic2Constants.PAL_TITLE_BACKGROUND_ADDR));
        } catch (Exception e) {
            LOGGER.warning("Failed to load title background palette: " + e.getMessage());
            backgroundPalette = null;
        }
    }

    /**
     * Loads Sonic's palette from ROM (Pal_133EC = Title Sonic.bin).
     * This palette is loaded to palette line 0 during the intro animation.
     */
    private void loadSonicPalette(Rom rom) {
        try {
            byte[] paletteData = rom.readBytes(Sonic2Constants.PAL_TITLE_SONIC_ADDR,
                    Sonic2Constants.PAL_TITLE_SONIC_SIZE);
            sonicPalette = new Palette();
            sonicPalette.fromSegaFormat(paletteData);

            LOGGER.info("Loaded Sonic palette from ROM at 0x" +
                    Integer.toHexString(Sonic2Constants.PAL_TITLE_SONIC_ADDR));
        } catch (Exception e) {
            LOGGER.warning("Failed to load Sonic palette: " + e.getMessage());
            sonicPalette = null;
        }
    }

    /**
     * Loads the intro palette from ROM for the credit text screen.
     * Uses the Sonic/Tails palette (SONIC_TAILS_PALETTE_ADDR, 32 bytes = 1 line).
     * The original game loads Pal_BGND (background palette) for the text screen.
     */
    private void loadIntroPalette(Rom rom) {
        try {
            byte[] paletteData = rom.readBytes(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR, 32);
            introPalette = new Palette();
            introPalette.fromSegaFormat(paletteData);

            LOGGER.info("Loaded intro palette from ROM at 0x" +
                    Integer.toHexString(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR));
        } catch (Exception e) {
            LOGGER.warning("Failed to load intro palette: " + e.getMessage());
            introPalette = null;
        }
    }

    /**
     * Loads the title emblem palette from ROM (Pal_1342C = Title Emblem.bin).
     * This palette is used by Plane A logo tiles (VDP palette line 3).
     */
    private void loadEmblemPalette(Rom rom) {
        try {
            byte[] paletteData = rom.readBytes(Sonic2Constants.PAL_TITLE_EMBLEM_ADDR,
                    Sonic2Constants.PAL_TITLE_EMBLEM_SIZE);
            emblemPalette = new Palette();
            emblemPalette.fromSegaFormat(paletteData);

            LOGGER.info("Loaded title emblem palette from ROM at 0x" +
                    Integer.toHexString(Sonic2Constants.PAL_TITLE_EMBLEM_ADDR));
        } catch (Exception e) {
            LOGGER.warning("Failed to load title emblem palette: " + e.getMessage());
            emblemPalette = null;
        }
    }

    /**
     * Caches all loaded patterns and palettes to the GPU.
     */
    public void cacheToGpu() {
        if (artCached || !dataLoaded || titlePatterns == null) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager == null || graphicsManager.isHeadlessMode()) {
            return;
        }

        // Cache palettes at their VDP palette line indices:
        // Line 0: Pal_133EC (Title Sonic.bin) - Sonic's palette
        if (sonicPalette != null) {
            graphicsManager.cachePaletteTexture(sonicPalette, 0);
        }
        // Line 1: Pal_Title (Title screen.bin) - loaded via PalLoad_ForFade at palptr line 1
        if (titlePalette != null) {
            graphicsManager.cachePaletteTexture(titlePalette, 1);
        }
        // Line 2: Pal_1340C (Title Background.bin) - used by Plane B (make_art_tile palette 2)
        if (backgroundPalette != null) {
            graphicsManager.cachePaletteTexture(backgroundPalette, 2);
        }
        // Line 3: Pal_1342C (Title Emblem.bin) - used by Plane A logo (make_art_tile palette 3)
        if (emblemPalette != null) {
            graphicsManager.cachePaletteTexture(emblemPalette, 3);
        }

        // Cache background patterns
        int cachedCount = 0;
        for (int i = 0; i < titlePatterns.length; i++) {
            if (titlePatterns[i] != null) {
                graphicsManager.cachePatternTexture(titlePatterns[i], PATTERN_BASE + i);
                cachedCount++;
            }
        }

        // Cache sprite patterns
        int spriteCachedCount = 0;
        if (spritePatterns != null) {
            for (int i = 0; i < spritePatterns.length; i++) {
                if (spritePatterns[i] != null) {
                    graphicsManager.cachePatternTexture(spritePatterns[i], SPRITE_PATTERN_BASE + i);
                    spriteCachedCount++;
                }
            }
        }

        LOGGER.info("Cached " + cachedCount + " background + " + spriteCachedCount + " sprite patterns to GPU");
        artCached = true;
    }

    /**
     * Caches credit text patterns and intro palette to the GPU.
     * Uses palette line 0 for text color. Called during the intro text phase.
     */
    public void cacheCreditTextToGpu() {
        if (!dataLoaded || creditTextPatterns == null) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager == null || graphicsManager.isHeadlessMode()) {
            return;
        }

        // Cache intro palette at line 0 (text color)
        if (introPalette != null) {
            graphicsManager.cachePaletteTexture(introPalette, 0);
        }

        // Cache credit text patterns
        for (int i = 0; i < creditTextPatterns.length; i++) {
            if (creditTextPatterns[i] != null) {
                graphicsManager.cachePatternTexture(creditTextPatterns[i], CREDIT_TEXT_PATTERN_BASE + i);
            }
        }

        LOGGER.fine("Cached " + creditTextPatterns.length + " credit text patterns to GPU");
    }

    public Pattern[] getCreditTextPatterns() {
        return creditTextPatterns;
    }

    public Palette getIntroPalette() {
        return introPalette;
    }

    public int[] getPlaneBMap() {
        return planeBMap;
    }

    public int getPlaneBWidth() {
        return PLANE_B_WIDTH;
    }

    public int getPlaneBHeight() {
        return PLANE_B_HEIGHT;
    }

    public int[] getPlaneAMap() {
        return planeAMap;
    }

    public int getPlaneAWidth() {
        return PLANE_A_WIDTH;
    }

    public int getPlaneAHeight() {
        return PLANE_A_HEIGHT;
    }

    public Palette getTitlePalette() {
        return titlePalette;
    }

    public Palette getBackgroundPalette() {
        return backgroundPalette;
    }

    public Palette getEmblemPalette() {
        return emblemPalette;
    }

    public Palette getSonicPalette() {
        return sonicPalette;
    }

    public Pattern[] getSpritePatterns() {
        return spritePatterns;
    }

    public boolean isDataLoaded() {
        return dataLoaded;
    }

    public boolean isArtCached() {
        return artCached;
    }

    /**
     * Resets the cached state to force re-upload on next draw.
     */
    public void resetCache() {
        artCached = false;
    }
}
