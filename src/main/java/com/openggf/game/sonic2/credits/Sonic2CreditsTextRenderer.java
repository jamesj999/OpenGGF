package com.openggf.game.sonic2.credits;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Renders Sonic 2 credit text at screen center using the Nemesis credit font.
 * <p>
 * Loads the credit text patterns from ROM ({@code ArtNem_CreditText}), creates
 * an {@link ObjectSpriteSheet} and {@link PatternSpriteRenderer}, and draws
 * mapping frames from {@link Sonic2CreditsMappings}.
 * <p>
 * The credit screen uses the ending palette ({@code PAL_ENDING_FULL_ADDR}):
 * palette line 0 for names (white text) and palette line 1 for role titles
 * (yellow/gold text). Each mapping piece carries its own palette index.
 */
public class Sonic2CreditsTextRenderer {

    private static final Logger LOGGER = Logger.getLogger(Sonic2CreditsTextRenderer.class.getName());

    /** Screen center position: 320/2 = 160, 224/2 = 112. */
    private static final int SCREEN_CENTER_X = 160;
    private static final int SCREEN_CENTER_Y = 112;

    /**
     * Unique pattern base ID for S2 credit text GPU caching.
     * Avoids collision with S1 credit text (0xB0000), S2 title screen credit text (0x80000),
     * S1 GHZ background (0xD0000), and other pattern ranges.
     */
    private static final int PATTERN_BASE = 0xE0000;

    private PatternSpriteRenderer renderer;
    private List<SpriteMappingFrame> mappingFrames;
    private Pattern[] creditTextPatterns;
    private Palette[] endingPalettes;
    private boolean initialized;
    private boolean gpuDirty;

    /**
     * Initializes the renderer by loading credit text patterns and ending
     * palette from ROM, creating the sprite sheet and caching to GPU.
     */
    public void initialize() {
        if (initialized) {
            return;
        }

        RomManager romManager = GameServices.rom();
        if (!romManager.isRomAvailable()) {
            LOGGER.warning("ROM not available for credit text loading");
            return;
        }

        Rom rom;
        try {
            rom = romManager.getRom();
        } catch (IOException e) {
            LOGGER.warning("Failed to get ROM: " + e.getMessage());
            return;
        }

        // Load Nemesis-compressed credit text font from ROM
        creditTextPatterns = loadNemesisPatterns(rom);
        if (creditTextPatterns == null || creditTextPatterns.length == 0) {
            LOGGER.warning("Credit text patterns not available from ROM");
            return;
        }

        // Load ending palettes (4 lines, 128 bytes total)
        endingPalettes = loadEndingPalettes(rom);

        // Build mapping frames for all 21 credit screens
        mappingFrames = Sonic2CreditsMappings.createFrames();

        // Create sprite sheet with paletteIndex = -1 (absolute mode: each piece
        // carries its own palette line, 0 for names, 1 for roles)
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(
                creditTextPatterns,
                mappingFrames,
                -1, // absolute palette mode
                1
        );
        renderer = new PatternSpriteRenderer(sheet);

        // Cache patterns and palettes to GPU
        cacheToGpu();

        initialized = true;
        LOGGER.info("S2 credits text renderer initialized with " + mappingFrames.size()
                + " frames, " + creditTextPatterns.length + " patterns");
    }

    /**
     * Draws the credit text for the given credit number at screen center.
     *
     * @param creditsNum credit index (0-20)
     */
    public void draw(int creditsNum) {
        if (!initialized || renderer == null || !renderer.isReady()) {
            return;
        }
        if (creditsNum < 0 || creditsNum >= mappingFrames.size()) {
            return;
        }

        // Re-upload patterns and palette if a zone load has dirtied the GPU cache
        if (gpuDirty) {
            cacheToGpu();
            gpuDirty = false;
        }

        GraphicsManager gm = GraphicsManager.getInstance();
        gm.beginPatternBatch();
        renderer.drawFrameIndex(creditsNum, SCREEN_CENTER_X, SCREEN_CENTER_Y);
        gm.flushPatternBatch();
    }

    /**
     * Marks the GPU pattern/palette cache as dirty. Call this when a zone load
     * has overwritten the GPU state (e.g., between CREDITS_DEMO and CREDITS_TEXT phases).
     */
    public void markGpuDirty() {
        gpuDirty = true;
    }

    public boolean isReady() {
        return initialized && renderer != null && renderer.isReady();
    }

    /**
     * Caches credit text patterns and ending palettes to the GPU.
     */
    private void cacheToGpu() {
        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        // Cache ending palettes at their VDP palette line indices (0-3)
        if (endingPalettes != null) {
            for (int i = 0; i < endingPalettes.length; i++) {
                if (endingPalettes[i] != null) {
                    gm.cachePaletteTexture(endingPalettes[i], i);
                }
            }
        }

        // Cache credit text patterns and mark renderer ready
        renderer.ensurePatternsCached(gm, PATTERN_BASE);
    }

    /**
     * Loads Nemesis-compressed credit text patterns from ROM.
     *
     * @return array of Pattern objects, or null on failure
     */
    private Pattern[] loadNemesisPatterns(Rom rom) {
        try {
            byte[] compressed = rom.readBytes(Sonic2Constants.ART_NEM_CREDIT_TEXT_ADDR, 4096);
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
                LOGGER.fine("Loaded " + patternCount + " credit text patterns from ROM");
                return patterns;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load credit text patterns: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads the ending palette from ROM (4 palette lines, 128 bytes total).
     * <p>
     * Palette line 0 = Ending Sonic palette (white text for names)
     * Palette line 1 = Ending Tails palette (gold/yellow text for roles)
     * Lines 2-3 = Ending background and photos palettes
     *
     * @return array of 4 Palette objects, one per VDP palette line
     */
    private Palette[] loadEndingPalettes(Rom rom) {
        try {
            byte[] fullData = rom.readBytes(Sonic2Constants.PAL_ENDING_FULL_ADDR,
                    Sonic2Constants.PAL_ENDING_FULL_SIZE);
            Palette[] palettes = new Palette[4];
            for (int i = 0; i < 4; i++) {
                byte[] lineData = Arrays.copyOfRange(fullData, i * 32, (i + 1) * 32);
                palettes[i] = new Palette();
                palettes[i].fromSegaFormat(lineData);
            }
            LOGGER.fine("Loaded 4 ending palette lines from ROM");
            return palettes;
        } catch (Exception e) {
            LOGGER.warning("Failed to load ending palettes: " + e.getMessage());
            return null;
        }
    }
}
