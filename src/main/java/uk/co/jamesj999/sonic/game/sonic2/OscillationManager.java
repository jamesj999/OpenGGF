package uk.co.jamesj999.sonic.game.sonic2;

/**
 * Tracks the global oscillating values used by multiple Sonic 1/2 objects.
 * Ported from OscillateNumInit/OscillateNumDo in the disassembly.
 * <p>
 * Supports per-game initialization: oscillators 0-7 are identical between
 * Sonic 1 and Sonic 2, but oscillators 8-15 differ in initial values,
 * speeds, and amplitudes. Call {@link #resetForSonic1()} or {@link #reset()}
 * (Sonic 2 default) at level load time.
 */
public final class OscillationManager {
    private static final int OSC_COUNT = 16;

    // ---- Sonic 2 defaults (also used as base for shared oscillators 0-7) ----

    // Osc_Data (control + 16 value/delta pairs)
    private static final int S2_INITIAL_CONTROL = 0x007D;
    private static final int[] S2_INITIAL_VALUES = {
            0x0080, 0x0080, 0x0080, 0x0080,
            0x0080, 0x0080, 0x0080, 0x0080,
            0x0080, 0x3848, 0x2080, 0x3080,
            0x5080, 0x7080, 0x0080, 0x4000
    };
    private static final int[] S2_INITIAL_DELTAS = {
            0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x00EE, 0x00B4, 0x010E,
            0x01C2, 0x0276, 0x0000, 0x00FE
    };

    // Osc_Data2 (speed, limit) — Sonic 2
    private static final int[] S2_SPEEDS = {
            2, 2, 2, 2,
            4, 8, 8, 4,
            2, 2, 2, 3,
            5, 7, 2, 2
    };
    private static final int[] S2_LIMITS = {
            0x10, 0x18, 0x20, 0x30,
            0x20, 0x08, 0x40, 0x40,
            0x38, 0x38, 0x20, 0x30,
            0x50, 0x70, 0x40, 0x40
    };

    // ---- Sonic 1 overrides (from docs/s1disasm/_inc/Oscillatory Routines.asm) ----

    // S1 control bitfield: %0000000001111100 = $007C
    private static final int S1_INITIAL_CONTROL = 0x007C;
    private static final int[] S1_INITIAL_VALUES = {
            0x0080, 0x0080, 0x0080, 0x0080,
            0x0080, 0x0080, 0x0080, 0x0080,
            0x0080, 0x50F0, 0x2080, 0x3080,
            0x5080, 0x7080, 0x0080, 0x0080
    };
    private static final int[] S1_INITIAL_DELTAS = {
            0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x0000, 0x0000, 0x0000,
            0x0000, 0x011E, 0x00B4, 0x010E,
            0x01C2, 0x0276, 0x0000, 0x0000
    };
    private static final int[] S1_SPEEDS = {
            2, 2, 2, 2,
            4, 8, 8, 4,
            2, 2, 2, 3,
            5, 7, 2, 2
    };
    private static final int[] S1_LIMITS = {
            0x10, 0x18, 0x20, 0x30,
            0x20, 0x08, 0x40, 0x40,
            0x50, 0x50, 0x20, 0x30,
            0x50, 0x70, 0x10, 0x10
    };

    private static final int[] values = new int[OSC_COUNT];
    private static final int[] deltas = new int[OSC_COUNT];
    // Active speed/limit tables (swapped on reset)
    private static int[] activeSpeeds = S2_SPEEDS;
    private static int[] activeLimits = S2_LIMITS;
    private static int control = S2_INITIAL_CONTROL;
    private static int lastFrame = Integer.MIN_VALUE;

    static {
        reset();
    }

    private OscillationManager() {
    }

    /**
     * Resets oscillation state to Sonic 2 defaults.
     */
    public static void reset() {
        control = S2_INITIAL_CONTROL;
        activeSpeeds = S2_SPEEDS;
        activeLimits = S2_LIMITS;
        for (int i = 0; i < OSC_COUNT; i++) {
            values[i] = S2_INITIAL_VALUES[i] & 0xFFFF;
            deltas[i] = S2_INITIAL_DELTAS[i] & 0xFFFF;
        }
        lastFrame = Integer.MIN_VALUE;
    }

    /**
     * Resets oscillation state to Sonic 1 values.
     * <p>
     * Sonic 1 differs from Sonic 2 in oscillators 8-15:
     * <ul>
     *   <li>Osc 8-9: amplitude $50 (vs S2's $38) — used by SLZ circling platforms</li>
     *   <li>Osc 9: initial value $50F0/$011E (vs S2's $3848/$00EE)</li>
     *   <li>Osc 14-15: amplitude $10, initial value/delta $80/$00 (vs S2's $40, $80/$00 and $4000/$00FE)</li>
     * </ul>
     * Reference: docs/s1disasm/_inc/Oscillatory Routines.asm
     */
    public static void resetForSonic1() {
        control = S1_INITIAL_CONTROL;
        activeSpeeds = S1_SPEEDS;
        activeLimits = S1_LIMITS;
        for (int i = 0; i < OSC_COUNT; i++) {
            values[i] = S1_INITIAL_VALUES[i] & 0xFFFF;
            deltas[i] = S1_INITIAL_DELTAS[i] & 0xFFFF;
        }
        lastFrame = Integer.MIN_VALUE;
    }

    public static void update(int frameCounter) {
        if (frameCounter == lastFrame) {
            return;
        }
        lastFrame = frameCounter;

        for (int i = 0; i < OSC_COUNT; i++) {
            int bit = OSC_COUNT - 1 - i;
            boolean decreasing = (control & (1 << bit)) != 0;
            int speed = activeSpeeds[i];
            int limit = activeLimits[i];

            int value = values[i];
            int delta = deltas[i];

            if (!decreasing) {
                delta = (delta + speed) & 0xFFFF;
                value = (value + delta) & 0xFFFF;
                int highByte = (value >> 8) & 0xFF;
                if (highByte >= limit) {
                    control |= (1 << bit);
                }
            } else {
                delta = (delta - speed) & 0xFFFF;
                value = (value + delta) & 0xFFFF;
                int highByte = (value >> 8) & 0xFF;
                if (highByte < limit) {
                    control &= ~(1 << bit);
                }
            }

            values[i] = value;
            deltas[i] = delta;
        }
    }

    /**
     * Returns the byte at the given offset into Oscillating_Data.
     * Offsets follow the ROM layout: value word then delta word per oscillator.
     */
    public static int getByte(int offset) {
        if (offset < 0 || offset >= OSC_COUNT * 4) {
            return 0;
        }
        int index = offset / 4;
        int within = offset % 4;
        int word = (within < 2) ? values[index] : deltas[index];
        return ((within & 1) == 0) ? ((word >> 8) & 0xFF) : (word & 0xFF);
    }

    /**
     * Returns the word at the given offset into Oscillating_Data.
     * Offsets follow the ROM layout: value word then delta word per oscillator.
     * Used by circular motion platforms (Obj6B types 8-11) to detect delta zero crossings.
     *
     * @param offset byte offset into oscillating data (must be word-aligned: 0, 2, 4, ...)
     * @return the 16-bit word at that offset, sign-extended to int
     */
    public static int getWord(int offset) {
        if (offset < 0 || offset >= OSC_COUNT * 4) {
            return 0;
        }
        int index = offset / 4;
        int within = offset % 4;
        int word = (within < 2) ? values[index] : deltas[index];
        return (short) word; // Sign-extend to int
    }
}
