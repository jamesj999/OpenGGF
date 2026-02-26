package com.openggf.game.sonic2.credits;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Loads all Nemesis-compressed art and palettes for the Sonic 2 ending cutscene.
 * <p>
 * The ending has three character variants determined by emerald count and player mode:
 * <ul>
 *   <li>SONIC: Normal Sonic ending (default)</li>
 *   <li>SUPER_SONIC: All 7 emeralds collected, not Tails-alone</li>
 *   <li>TAILS: Tails-alone player mode</li>
 * </ul>
 * Each variant loads different character art, palette, and animal companion sprite.
 * <p>
 * ROM reference: EndingSequence (s2.asm lines 12998-13100), ObjCA_Init.
 */
public class Sonic2EndingArt {

    private static final Logger LOGGER = Logger.getLogger(Sonic2EndingArt.class.getName());

    /**
     * Character variant for the ending sequence.
     * Determines which art, palette, and animal companion to load.
     */
    public enum EndingRoutine {
        /** Normal Sonic ending: Flicky birds. */
        SONIC,
        /** Super Sonic ending (7 emeralds): Eagle birds. */
        SUPER_SONIC,
        /** Tails-alone ending: Chicken birds. */
        TAILS
    }

    // ========================================================================
    // GPU pattern base IDs (unique ranges to avoid collision with other systems)
    // ========================================================================

    /**
     * Pattern base for ending character art (Sonic/Super Sonic/Tails).
     * Uses 0xF0000 range to avoid collision with credits text (0xE0000),
     * title screen (0x60000-0x80000), and level patterns.
     */
    static final int PATTERN_BASE_CHARACTER = 0xF0000;

    /** Pattern base for ending final tornado closeup. */
    static final int PATTERN_BASE_FINAL_TORNADO = 0xF1000;

    /** Pattern base for ending photo frame art. */
    static final int PATTERN_BASE_PICS = 0xF2000;

    /** Pattern base for ending mini tornado sprites. */
    static final int PATTERN_BASE_MINI_TORNADO = 0xF3000;

    /** Pattern base for cloud art. */
    static final int PATTERN_BASE_CLOUDS = 0xF4000;

    /** Pattern base for animal art (flicky/eagle/chicken). */
    static final int PATTERN_BASE_ANIMAL = 0xF5000;

    // ========================================================================
    // Loaded art
    // ========================================================================

    private Pattern[] characterPatterns;
    private Pattern[] finalTornadoPatterns;
    private Pattern[] picsPatterns;
    private Pattern[] miniTornadoPatterns;
    private Pattern[] cloudPatterns;
    private Pattern[] animalPatterns;

    private Palette[] endingPalettes;

    private EndingRoutine loadedRoutine;
    private boolean initialized;

    // ========================================================================
    // Routine determination
    // ========================================================================

    /**
     * Determines the ending routine based on emerald count and player mode.
     * <p>
     * ROM reference: EndingSequence (s2.asm ~line 13030):
     * <pre>
     *   cmpi.w  #7,(Emerald_count).w
     *   bne.s   +
     *   cmpi.b  #2,(Player_mode).w   ; Tails alone?
     *   beq.s   +
     *   moveq   #2,d0                ; Super Sonic ending
     *   ...
     *   cmpi.b  #2,(Player_mode).w
     *   bne.s   +
     *   moveq   #4,d0                ; Tails ending
     * </pre>
     *
     * @return the ending routine to use
     */
    public static EndingRoutine determineEndingRoutine() {
        GameStateManager gs = GameServices.gameState();
        if (gs == null) {
            return EndingRoutine.SONIC;
        }

        // TODO: Query actual PlayerCharacter from level event manager or game module
        // For now, default to SONIC unless we can detect Tails-alone.
        // The ROM checks Player_mode == 2 for Tails alone.
        boolean tailsAlone = false;

        if (gs.hasAllEmeralds() && !tailsAlone) {
            return EndingRoutine.SUPER_SONIC;
        }
        if (tailsAlone) {
            return EndingRoutine.TAILS;
        }
        return EndingRoutine.SONIC;
    }

    // ========================================================================
    // Art loading
    // ========================================================================

    /**
     * Loads all ending art and palettes from ROM for the given routine.
     * <p>
     * Art loaded per routine:
     * <ul>
     *   <li>Character-specific: ArtNem_EndingSonic / EndingSuperSonic / EndingTails</li>
     *   <li>Shared: EndingFinalTornado, EndingPics, EndingMiniTornado, Clouds</li>
     *   <li>Animal companion: Flicky (SONIC), Eagle (SUPER_SONIC), Chicken (TAILS)</li>
     * </ul>
     *
     * @param rom     the ROM to load from
     * @param routine the character variant
     */
    public void loadArt(Rom rom, EndingRoutine routine) {
        this.loadedRoutine = routine;

        // Load character-specific art
        int characterAddr = switch (routine) {
            case SONIC -> Sonic2Constants.ART_NEM_ENDING_SONIC_ADDR;
            case SUPER_SONIC -> Sonic2Constants.ART_NEM_ENDING_SUPER_SONIC_ADDR;
            case TAILS -> Sonic2Constants.ART_NEM_ENDING_TAILS_ADDR;
        };
        characterPatterns = loadNemesisPatterns(rom, characterAddr, 8192, "EndingCharacter");

        // Load shared art
        finalTornadoPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_ENDING_FINAL_TORNADO_ADDR, 16384, "EndingFinalTornado");
        picsPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_ENDING_PICS_ADDR, 16384, "EndingPics");
        miniTornadoPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_ENDING_MINI_TORNADO_ADDR, 8192, "EndingMiniTornado");
        cloudPatterns = loadNemesisPatterns(rom, Sonic2Constants.ART_NEM_CLOUDS_ADDR, 8192, "Clouds");

        // Load animal companion art
        int animalAddr = switch (routine) {
            case SONIC -> Sonic2Constants.ART_NEM_FLICKY_ADDR;
            case SUPER_SONIC -> Sonic2Constants.ART_NEM_EAGLE_ADDR;
            case TAILS -> Sonic2Constants.ART_NEM_CHICKEN_ADDR;
        };
        animalPatterns = loadNemesisPatterns(rom, animalAddr, 4096, "EndingAnimal");

        LOGGER.info("Ending art loaded for routine " + routine
                + ": character=" + patternCount(characterPatterns)
                + " finalTornado=" + patternCount(finalTornadoPatterns)
                + " pics=" + patternCount(picsPatterns)
                + " miniTornado=" + patternCount(miniTornadoPatterns)
                + " clouds=" + patternCount(cloudPatterns)
                + " animal=" + patternCount(animalPatterns));
    }

    /**
     * Loads ending palettes from ROM for the given routine.
     * <p>
     * ROM reference: EndingSequence palette loading (s2.asm ~line 13050):
     * <pre>
     *   Palette line 0: Character palette (Sonic/Super Sonic/Tails)
     *   Palette line 1: Tails palette (always loaded from PAL_ENDING_TAILS_ADDR)
     *   Palette line 2: Background palette (PAL_ENDING_BACKGROUND_ADDR)
     *   Palette line 3: Photos palette (PAL_ENDING_PHOTOS_ADDR)
     * </pre>
     * For Super Sonic, palette line 0 uses PAL_ENDING_SUPER_SONIC_ADDR.
     *
     * @param rom     the ROM to load from
     * @param routine the character variant
     */
    public void loadPalettes(Rom rom, EndingRoutine routine) {
        endingPalettes = new Palette[4];

        // Palette line 0: character-specific
        int charPalAddr = switch (routine) {
            case SONIC -> Sonic2Constants.PAL_ENDING_SONIC_ADDR;
            case SUPER_SONIC -> Sonic2Constants.PAL_ENDING_SUPER_SONIC_ADDR;
            case TAILS -> Sonic2Constants.PAL_ENDING_SONIC_ADDR; // Tails ending still uses Sonic line 0
        };
        endingPalettes[0] = loadPalette(rom, charPalAddr, "EndingPalLine0");

        // Palette line 1: Tails palette (used for secondary colors in all variants)
        endingPalettes[1] = loadPalette(rom, Sonic2Constants.PAL_ENDING_TAILS_ADDR, "EndingPalLine1");

        // Palette line 2: Background
        endingPalettes[2] = loadPalette(rom, Sonic2Constants.PAL_ENDING_BACKGROUND_ADDR, "EndingPalLine2");

        // Palette line 3: Photos
        endingPalettes[3] = loadPalette(rom, Sonic2Constants.PAL_ENDING_PHOTOS_ADDR, "EndingPalLine3");

        LOGGER.info("Ending palettes loaded for routine " + routine);
    }

    /**
     * Caches all loaded art patterns and palettes to the GPU.
     * Must be called after {@link #loadArt} and {@link #loadPalettes}.
     */
    public void cacheToGpu() {
        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        // Cache palettes
        if (endingPalettes != null) {
            for (int i = 0; i < endingPalettes.length; i++) {
                if (endingPalettes[i] != null) {
                    gm.cachePaletteTexture(endingPalettes[i], i);
                }
            }
        }

        // Cache all pattern sets
        cachePatternSet(gm, characterPatterns, PATTERN_BASE_CHARACTER, "character");
        cachePatternSet(gm, finalTornadoPatterns, PATTERN_BASE_FINAL_TORNADO, "finalTornado");
        cachePatternSet(gm, picsPatterns, PATTERN_BASE_PICS, "pics");
        cachePatternSet(gm, miniTornadoPatterns, PATTERN_BASE_MINI_TORNADO, "miniTornado");
        cachePatternSet(gm, cloudPatterns, PATTERN_BASE_CLOUDS, "clouds");
        cachePatternSet(gm, animalPatterns, PATTERN_BASE_ANIMAL, "animal");

        initialized = true;
        LOGGER.info("Ending art cached to GPU");
    }

    // ========================================================================
    // Accessors
    // ========================================================================

    public Pattern[] getCharacterPatterns() {
        return characterPatterns;
    }

    public Pattern[] getFinalTornadoPatterns() {
        return finalTornadoPatterns;
    }

    public Pattern[] getPicsPatterns() {
        return picsPatterns;
    }

    public Pattern[] getMiniTornadoPatterns() {
        return miniTornadoPatterns;
    }

    public Pattern[] getCloudPatterns() {
        return cloudPatterns;
    }

    public Pattern[] getAnimalPatterns() {
        return animalPatterns;
    }

    public Palette[] getEndingPalettes() {
        return endingPalettes;
    }

    public EndingRoutine getLoadedRoutine() {
        return loadedRoutine;
    }

    public boolean isInitialized() {
        return initialized;
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    /**
     * Loads Nemesis-compressed patterns from ROM.
     */
    private Pattern[] loadNemesisPatterns(Rom rom, int address, int maxCompressedSize, String name) {
        try {
            byte[] compressed = rom.readBytes(address, maxCompressedSize);
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
                LOGGER.fine("Loaded " + patternCount + " " + name + " patterns from ROM at 0x"
                        + Integer.toHexString(address));
                return patterns;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    /**
     * Loads a single 32-byte palette (1 VDP palette line) from ROM.
     */
    private Palette loadPalette(Rom rom, int address, String name) {
        try {
            byte[] data = rom.readBytes(address, 32);
            Palette palette = new Palette();
            palette.fromSegaFormat(data);
            LOGGER.fine("Loaded " + name + " palette from ROM at 0x" + Integer.toHexString(address));
            return palette;
        } catch (Exception e) {
            LOGGER.warning("Failed to load " + name + " palette: " + e.getMessage());
            return null;
        }
    }

    /**
     * Caches a set of patterns to the GPU at the given base index.
     */
    private void cachePatternSet(GraphicsManager gm, Pattern[] patterns, int baseIndex, String name) {
        if (patterns == null || patterns.length == 0) {
            return;
        }
        for (int i = 0; i < patterns.length; i++) {
            if (patterns[i] != null) {
                gm.cachePatternTexture(patterns[i], baseIndex + i);
            }
        }
        LOGGER.fine("Cached " + patterns.length + " " + name + " patterns to GPU at base 0x"
                + Integer.toHexString(baseIndex));
    }

    private static int patternCount(Pattern[] patterns) {
        return patterns != null ? patterns.length : 0;
    }
}
