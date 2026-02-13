package uk.co.jamesj999.sonic.game.sonic3k.objects;

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

    /** Get the Super Sonic mapping frame based on V-blank parity. */
    public int getMappingFrame(int frameCounter) {
        return (frameCounter & 1) != 0 ? MAPPING_FRAME_ODD : MAPPING_FRAME_EVEN;
    }

    public int getPaletteFrame() { return paletteFrame; }
    public void setPaletteFrame(int frame) { this.paletteFrame = frame; }
}
