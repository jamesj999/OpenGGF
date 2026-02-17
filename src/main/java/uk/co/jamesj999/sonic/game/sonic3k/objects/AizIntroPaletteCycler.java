package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Palette;

/**
 * Palette cycling for the AIZ1 intro's Super Sonic visual effect.
 * Port of sub_679B8 (sonic3k.asm:135904).
 *
 * This is NOT the SuperStateController palette cycling - it's a standalone
 * helper used only by the intro cutscene object. Cycles through
 * PalCycle_SuperSonic entries at 6-frame intervals.
 */
public class AizIntroPaletteCycler {
    private static final int TIMER_PERIOD = 6;
    private static final int FRAME_ADVANCE = 6;   // bytes per cycle step
    private static final int CYCLE_MIN = 0x24;     // cycling range start
    private static final int CYCLE_MAX = 0x36;     // cycling range end (inclusive)
    private static final int MAPPING_FRAME_EVEN = 0x21;
    private static final int MAPPING_FRAME_ODD = 0x22;

    /** Sonic palette line index (line 0). */
    private static final int SONIC_PALETTE_INDEX = 0;

    /** First color index within the palette line to overwrite (colors 2, 3, 4). */
    private static final int FIRST_COLOR_INDEX = 2;

    /** Number of colors written per cycle step (3 MD colors = 6 bytes). */
    private static final int COLORS_PER_STEP = 3;

    private int paletteTimer;
    private int paletteFrame;

    public void init() {
        paletteTimer = TIMER_PERIOD;
        paletteFrame = CYCLE_MIN;
    }

    /**
     * Advance one frame. Decrements timer; on expiry, advances palette frame
     * and resets timer. Wraps frame index within cycling range.
     */
    public void advance() {
        paletteTimer--;
        if (paletteTimer < 0) {
            paletteTimer = TIMER_PERIOD;
            paletteFrame += FRAME_ADVANCE;
            if (paletteFrame > CYCLE_MAX) {
                paletteFrame = CYCLE_MIN;
            }
        }
    }

    /**
     * Writes the current palette cycle colors to the GPU.
     * Reads 3 Mega Drive colors from the cycle data at the current frame offset
     * and writes them to palette line 0, colors 2-4.
     *
     * Reference: Sonic2SuperStateController.applyPaletteFrame() pattern.
     */
    public void applyToGpu() {
        byte[] data = AizIntroArtLoader.getSuperSonicPaletteCycleData();
        if (data == null || data.length == 0) return;

        // paletteFrame is already the correct byte offset into cycle data (range 0x24..0x36)
        int offset = paletteFrame;
        if (offset + COLORS_PER_STEP * 2 > data.length) return;

        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm == null) return;
            Level level = lm.getCurrentLevel();
            if (level == null) return;
            Palette palette = level.getPalette(SONIC_PALETTE_INDEX);
            if (palette == null) return;

            for (int i = 0; i < COLORS_PER_STEP; i++) {
                palette.getColor(FIRST_COLOR_INDEX + i)
                        .fromSegaFormat(data, offset + i * 2);
            }

            GraphicsManager gfx = GraphicsManager.getInstance();
            if (gfx.isGlInitialized()) {
                gfx.cachePaletteTexture(palette, SONIC_PALETTE_INDEX);
            }
        } catch (Exception ignored) {
            // May not be available in test environments
        }
    }

    /** Get the Super Sonic mapping frame based on V-blank parity. */
    public int getMappingFrame(int frameCounter) {
        return (frameCounter & 1) != 0 ? MAPPING_FRAME_ODD : MAPPING_FRAME_EVEN;
    }

    public int getPaletteFrame() { return paletteFrame; }
    public void setPaletteFrame(int frame) { this.paletteFrame = frame; }
}
