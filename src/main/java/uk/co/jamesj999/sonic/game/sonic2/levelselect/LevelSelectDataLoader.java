package uk.co.jamesj999.sonic.game.sonic2.levelselect;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.tools.EnigmaReader;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Loads level select screen graphics and data from the Sonic 2 ROM.
 *
 * <p>Graphics loaded:
 * <ul>
 *   <li>ArtNem_MenuBox - Menu borders and text frames</li>
 *   <li>ArtNem_LevelSelectPics - Zone preview icons (15 icons)</li>
 *   <li>ArtNem_FontStuff - Standard menu font</li>
 * </ul>
 *
 * <p>Mappings loaded:
 * <ul>
 *   <li>MapEng_LevSel - Main screen layout (Enigma compressed)</li>
 *   <li>MapEng_LevSelIcon - Icon box layout (Enigma compressed)</li>
 * </ul>
 *
 * <p>Palettes loaded:
 * <ul>
 *   <li>Pal_LevelIcons - 15 icon palettes (32 bytes each, uncompressed)</li>
 * </ul>
 */
public class LevelSelectDataLoader {
    private static final Logger LOGGER = Logger.getLogger(LevelSelectDataLoader.class.getName());

    /** Pattern offset for menu box art within level select patterns */
    private static final int MENU_BOX_OFFSET = 0;

    /** Pattern offset for level select pics art */
    private static final int LEVEL_SELECT_PICS_OFFSET = 0x80;

    /** Pattern offset for font art */
    private static final int FONT_OFFSET = 0x400;

    // Loaded art patterns
    private Pattern[] menuBoxPatterns;
    private Pattern[] levelSelectPicsPatterns;
    private Pattern[] fontPatterns;

    // Combined pattern array for VRAM-style access
    private Pattern[] combinedPatterns;

    // Screen layout decoded from Enigma
    private int[] screenLayout;
    private int screenLayoutWidth;
    private int screenLayoutHeight;

    // Icon mappings decoded from Enigma
    private int[] iconMappings;
    private int iconMappingsWidth;
    private int iconMappingsHeight;

    // Icon palettes (15 palettes, one per icon)
    private Palette[] iconPalettes;

    // Menu palettes (4 palette lines for text)
    private Palette[] menuPalettes;

    // Menu.bin palette data from s2disasm (128 bytes = 4 palette lines)
    // Format: Mega Drive 0x0BGR, 16 colors per line, 2 bytes per color
    private static final byte[] MENU_PALETTE_DATA = {
        // Line 0 (normal text)
        0x0C, 0x20, 0x00, 0x00, 0x0A, 0x22, 0x0C, 0x42, 0x0E, 0x44, 0x0E, 0x66, 0x0E, (byte)0xEE, 0x0A, (byte)0xAA,
        0x08, (byte)0x88, 0x04, 0x44, 0x08, (byte)0xAE, 0x04, 0x6A, 0x00, 0x0E, 0x00, 0x08, 0x00, (byte)0xAE, 0x00, (byte)0x8E,
        // Line 1
        0x0C, 0x20, 0x00, 0x00, 0x0E, 0x62, 0x0A, (byte)0x86, 0x0E, (byte)0x86, 0x00, 0x44, 0x0E, (byte)0xEE, 0x0A, (byte)0xAA,
        0x08, (byte)0x88, 0x04, 0x44, 0x06, 0x66, 0x0E, (byte)0x86, 0x00, (byte)0xEE, 0x00, (byte)0x88, 0x0E, (byte)0xA8, 0x0E, (byte)0xCA,
        // Line 2 (mostly empty)
        0x0C, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        // Line 3 (highlighted/selected text)
        0x0C, 0x20, 0x00, 0x00, 0x06, 0x00, 0x0C, 0x20, 0x0A, 0x00, 0x0E, (byte)0xEE, 0x00, (byte)0xEE, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0E, 0x60, 0x08, 0x00, 0x00, 0x0E, 0x00, 0x08
    };

    private boolean dataLoaded = false;
    private boolean artCached = false;

    /**
     * Loads all level select data from ROM.
     *
     * @return true if data was loaded successfully
     */
    public boolean loadData() {
        if (dataLoaded) {
            return true;
        }

        RomManager romManager = GameServices.rom();
        if (!romManager.isRomAvailable()) {
            LOGGER.warning("ROM not available for level select data loading");
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
            // Load Nemesis-compressed art
            menuBoxPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_MENU_BOX_ADDR, "MenuBox");
            levelSelectPicsPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_LEVEL_SELECT_PICS_ADDR, "LevelSelectPics");
            fontPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_FONT_STUFF_ADDR, "FontStuff");

            LOGGER.info("Loaded patterns: MenuBox=" + (menuBoxPatterns != null ? menuBoxPatterns.length : 0) +
                    ", LevelSelectPics=" + (levelSelectPicsPatterns != null ? levelSelectPicsPatterns.length : 0) +
                    ", Font=" + (fontPatterns != null ? fontPatterns.length : 0));

            // Combine all patterns into a single VRAM-style array
            combinePatternsToVram();

            // Load Enigma-compressed mappings
            loadScreenLayout(rom);
            loadIconMappings(rom);

            // Load icon palettes
            loadIconPalettes(rom);

            // Load menu palettes (from hardcoded data - same as Menu.bin)
            loadMenuPalettes();

            dataLoaded = true;
            return true;

        } catch (Exception e) {
            LOGGER.warning("Failed to load level select data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Loads Nemesis-compressed patterns from ROM.
     */
    private Pattern[] loadNemesisPatterns(Rom rom, int address, String name) {
        try {
            // Read enough compressed data (8KB should be plenty)
            byte[] compressed = rom.readBytes(address, 8192);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = NemesisReader.decompress(channel);
                int patternCount = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
                Pattern[] patterns = new Pattern[patternCount];
                for (int i = 0; i < patternCount; i++) {
                    patterns[i] = new Pattern();
                    byte[] subArray = Arrays.copyOfRange(decompressed,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[i].fromSegaFormat(subArray);
                }
                return patterns;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    /**
     * Combines loaded patterns into a VRAM-style array for easy access.
     */
    private void combinePatternsToVram() {
        // Calculate total size needed
        int totalSize = FONT_OFFSET + (fontPatterns != null ? fontPatterns.length : 0);
        combinedPatterns = new Pattern[totalSize];

        // Fill with empty patterns
        Pattern emptyPattern = new Pattern();
        Arrays.fill(combinedPatterns, emptyPattern);

        // Copy menu box patterns to offset 0
        if (menuBoxPatterns != null) {
            for (int i = 0; i < menuBoxPatterns.length && (MENU_BOX_OFFSET + i) < combinedPatterns.length; i++) {
                if (menuBoxPatterns[i] != null) {
                    combinedPatterns[MENU_BOX_OFFSET + i] = menuBoxPatterns[i];
                }
            }
        }

        // Copy level select pics to offset 0x80
        if (levelSelectPicsPatterns != null) {
            // Extend array if needed
            int neededSize = LEVEL_SELECT_PICS_OFFSET + levelSelectPicsPatterns.length;
            if (neededSize > combinedPatterns.length) {
                Pattern[] newArray = new Pattern[neededSize];
                Arrays.fill(newArray, emptyPattern);
                System.arraycopy(combinedPatterns, 0, newArray, 0, combinedPatterns.length);
                combinedPatterns = newArray;
            }
            for (int i = 0; i < levelSelectPicsPatterns.length; i++) {
                if (levelSelectPicsPatterns[i] != null) {
                    combinedPatterns[LEVEL_SELECT_PICS_OFFSET + i] = levelSelectPicsPatterns[i];
                }
            }
        }

        // Copy font patterns to offset 0x400
        if (fontPatterns != null) {
            // Extend array if needed
            int neededSize = FONT_OFFSET + fontPatterns.length;
            if (neededSize > combinedPatterns.length) {
                Pattern[] newArray = new Pattern[neededSize];
                Arrays.fill(newArray, emptyPattern);
                System.arraycopy(combinedPatterns, 0, newArray, 0, combinedPatterns.length);
                combinedPatterns = newArray;
            }
            for (int i = 0; i < fontPatterns.length; i++) {
                if (fontPatterns[i] != null) {
                    combinedPatterns[FONT_OFFSET + i] = fontPatterns[i];
                }
            }
        }

        LOGGER.info("Combined " + combinedPatterns.length + " patterns for level select");
    }

    /**
     * Loads the main screen layout from Enigma-compressed data.
     */
    private void loadScreenLayout(Rom rom) {
        try {
            // Read Enigma data
            byte[] compressed = rom.readBytes(Sonic2Constants.MAP_ENI_LEVEL_SELECT_ADDR, 1024);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = EnigmaReader.decompress(channel, 0);

                // Enigma output is 16-bit words in big-endian format
                // Convert to tile indices
                int wordCount = decompressed.length / 2;
                screenLayout = new int[wordCount];
                ByteBuffer buf = ByteBuffer.wrap(decompressed);
                for (int i = 0; i < wordCount; i++) {
                    screenLayout[i] = buf.getShort() & 0xFFFF;
                }

                // Screen is 40 tiles wide (H40 mode) x 28 tiles tall
                screenLayoutWidth = 40;
                screenLayoutHeight = wordCount / screenLayoutWidth;

                LOGGER.info("Loaded screen layout: " + screenLayoutWidth + "x" + screenLayoutHeight +
                        " (" + wordCount + " tiles)");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load screen layout: " + e.getMessage());
            screenLayout = new int[0];
            screenLayoutWidth = 0;
            screenLayoutHeight = 0;
        }
    }

    /**
     * Loads the icon box mappings from Enigma-compressed data.
     */
    private void loadIconMappings(Rom rom) {
        try {
            byte[] compressed = rom.readBytes(Sonic2Constants.MAP_ENI_LEVEL_SELECT_ICON_ADDR, 256);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = EnigmaReader.decompress(channel, 0);

                int wordCount = decompressed.length / 2;
                iconMappings = new int[wordCount];
                ByteBuffer buf = ByteBuffer.wrap(decompressed);
                for (int i = 0; i < wordCount; i++) {
                    iconMappings[i] = buf.getShort() & 0xFFFF;
                }

                // Icon box is 3 tiles wide x 4 tiles tall
                iconMappingsWidth = 3;
                iconMappingsHeight = wordCount / iconMappingsWidth;

                LOGGER.info("Loaded icon mappings: " + iconMappingsWidth + "x" + iconMappingsHeight);
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load icon mappings: " + e.getMessage());
            iconMappings = new int[0];
            iconMappingsWidth = 0;
            iconMappingsHeight = 0;
        }
    }

    /**
     * Loads the menu palettes from hardcoded data (same as Menu.bin from s2disasm).
     */
    private void loadMenuPalettes() {
        menuPalettes = new Palette[4];

        for (int line = 0; line < 4; line++) {
            menuPalettes[line] = new Palette();
            int offset = line * 32;
            for (int c = 0; c < 16; c++) {
                // Mega Drive format: 0x0BGR (3 bits per channel, bit 0 unused)
                int colorWord = ((MENU_PALETTE_DATA[offset + c * 2] & 0xFF) << 8) |
                                (MENU_PALETTE_DATA[offset + c * 2 + 1] & 0xFF);
                int b = ((colorWord >> 9) & 0x07) * 36;  // Scale 0-7 to 0-252
                int g = ((colorWord >> 5) & 0x07) * 36;
                int r = ((colorWord >> 1) & 0x07) * 36;
                menuPalettes[line].setColor(c, new Palette.Color((byte) r, (byte) g, (byte) b));
            }
        }

        LOGGER.info("Loaded 4 menu palette lines");
    }

    /**
     * Loads the 15 icon palettes from ROM (uncompressed).
     */
    private void loadIconPalettes(Rom rom) {
        try {
            byte[] paletteData = rom.readBytes(Sonic2Constants.PAL_LEVEL_ICONS_ADDR, Sonic2Constants.PAL_LEVEL_ICONS_SIZE);
            iconPalettes = new Palette[15];

            for (int i = 0; i < 15; i++) {
                iconPalettes[i] = new Palette();
                // Each palette is 32 bytes (16 colors * 2 bytes per color in Mega Drive format)
                int offset = i * 32;
                for (int c = 0; c < 16 && (offset + c * 2 + 1) < paletteData.length; c++) {
                    // Mega Drive format: 0x0BGR (4 bits per channel)
                    int colorWord = ((paletteData[offset + c * 2] & 0xFF) << 8) | (paletteData[offset + c * 2 + 1] & 0xFF);
                    int b = ((colorWord >> 9) & 0x07) * 36;  // Scale 0-7 to 0-252
                    int g = ((colorWord >> 5) & 0x07) * 36;
                    int r = ((colorWord >> 1) & 0x07) * 36;
                    iconPalettes[i].setColor(c, new Palette.Color((byte) r, (byte) g, (byte) b));
                }
            }

            LOGGER.info("Loaded 15 icon palettes");
        } catch (Exception e) {
            LOGGER.warning("Failed to load icon palettes: " + e.getMessage());
            iconPalettes = new Palette[0];
        }
    }

    /**
     * Caches all loaded patterns and palettes to the GPU.
     */
    public void cacheToGpu() {
        if (artCached || !dataLoaded || combinedPatterns == null) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager == null || graphicsManager.isHeadlessMode()) {
            return;
        }

        // Cache menu palettes (lines 0-3)
        if (menuPalettes != null) {
            for (int i = 0; i < menuPalettes.length; i++) {
                if (menuPalettes[i] != null) {
                    graphicsManager.cachePaletteTexture(menuPalettes[i], i);
                }
            }
            LOGGER.info("Cached 4 menu palette lines to GPU");
        }

        // Cache all patterns with level select pattern base ID
        int cachedCount = 0;
        for (int i = 0; i < combinedPatterns.length; i++) {
            if (combinedPatterns[i] != null) {
                graphicsManager.cachePatternTexture(combinedPatterns[i], LevelSelectConstants.PATTERN_BASE + i);
                cachedCount++;
            }
        }

        LOGGER.info("Cached " + cachedCount + " level select patterns to GPU");
        artCached = true;
    }

    /**
     * Gets the screen layout tile array.
     */
    public int[] getScreenLayout() {
        return screenLayout;
    }

    public int getScreenLayoutWidth() {
        return screenLayoutWidth;
    }

    public int getScreenLayoutHeight() {
        return screenLayoutHeight;
    }

    /**
     * Gets the icon mappings tile array.
     */
    public int[] getIconMappings() {
        return iconMappings;
    }

    public int getIconMappingsWidth() {
        return iconMappingsWidth;
    }

    public int getIconMappingsHeight() {
        return iconMappingsHeight;
    }

    /**
     * Gets the palette for a specific icon index.
     */
    public Palette getIconPalette(int iconIndex) {
        if (iconPalettes != null && iconIndex >= 0 && iconIndex < iconPalettes.length) {
            return iconPalettes[iconIndex];
        }
        return null;
    }

    /**
     * Gets the menu palette for a specific line (0-3).
     * Line 0 = normal text, Line 3 = highlighted text.
     */
    public Palette getMenuPalette(int lineIndex) {
        if (menuPalettes != null && lineIndex >= 0 && lineIndex < menuPalettes.length) {
            return menuPalettes[lineIndex];
        }
        return null;
    }

    /**
     * Gets the combined pattern array.
     */
    public Pattern[] getCombinedPatterns() {
        return combinedPatterns;
    }

    /**
     * Gets the level select pics patterns (zone preview icons).
     */
    public Pattern[] getLevelSelectPicsPatterns() {
        return levelSelectPicsPatterns;
    }

    /**
     * Gets the pattern offset for level select pics.
     */
    public int getLevelSelectPicsOffset() {
        return LEVEL_SELECT_PICS_OFFSET;
    }

    /**
     * Gets the font patterns.
     */
    public Pattern[] getFontPatterns() {
        return fontPatterns;
    }

    /**
     * Gets the pattern offset for font.
     */
    public int getFontOffset() {
        return FONT_OFFSET;
    }

    /**
     * Returns whether data has been loaded.
     */
    public boolean isDataLoaded() {
        return dataLoaded;
    }

    /**
     * Returns whether art has been cached to GPU.
     */
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
