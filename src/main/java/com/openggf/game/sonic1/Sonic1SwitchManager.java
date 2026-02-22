package com.openggf.game.sonic1;

/**
 * Manages the f_switch state array from the Sonic 1 disassembly.
 * <p>
 * In the ROM, f_switch is a 16-byte RAM area where each byte represents
 * a switch state. Buttons (Object 0x32) set bits in this array, and
 * other objects (Chained Stompers, Glass Blocks, Platforms) read from it
 * to determine their behavior.
 * <p>
 * Each switch byte supports two independent flags via bit positions:
 * <ul>
 *   <li>Bit 0 (d3=0): Standard switch flag</li>
 *   <li>Bit 7 (d3=7): Alternate switch flag (when subtype bit 6 is set)</li>
 * </ul>
 * <p>
 * Reference: docs/s1disasm/_incObj/32 Button.asm
 */
public final class Sonic1SwitchManager {

    private static final int SWITCH_COUNT = 16;
    private static Sonic1SwitchManager instance;

    private final byte[] switchState = new byte[SWITCH_COUNT];

    private Sonic1SwitchManager() {
    }

    public static Sonic1SwitchManager getInstance() {
        if (instance == null) {
            instance = new Sonic1SwitchManager();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    /**
     * Set a bit in the switch state array.
     * Mirrors: bset d3,(a3) in the disassembly.
     *
     * @param switchIndex which switch (0-15), from subtype bits 0-3
     * @param bit         which bit to set (0 or 7)
     */
    public void setBit(int switchIndex, int bit) {
        if (switchIndex >= 0 && switchIndex < SWITCH_COUNT) {
            switchState[switchIndex] |= (byte) (1 << bit);
        }
    }

    /**
     * Clear a bit in the switch state array.
     * Mirrors: bclr d3,(a3) in the disassembly.
     *
     * @param switchIndex which switch (0-15), from subtype bits 0-3
     * @param bit         which bit to clear (0 or 7)
     */
    public void clearBit(int switchIndex, int bit) {
        if (switchIndex >= 0 && switchIndex < SWITCH_COUNT) {
            switchState[switchIndex] &= (byte) ~(1 << bit);
        }
    }

    /**
     * Test whether a switch byte is non-zero (any bit set).
     * Mirrors: tst.b (a3) in the disassembly.
     *
     * @param switchIndex which switch (0-15)
     * @return true if any bit is set for this switch
     */
    public boolean isPressed(int switchIndex) {
        if (switchIndex >= 0 && switchIndex < SWITCH_COUNT) {
            return switchState[switchIndex] != 0;
        }
        return false;
    }

    /**
     * Get the raw byte value for a switch index.
     *
     * @param switchIndex which switch (0-15)
     * @return the raw switch byte
     */
    public byte getRaw(int switchIndex) {
        if (switchIndex >= 0 && switchIndex < SWITCH_COUNT) {
            return switchState[switchIndex];
        }
        return 0;
    }

    /**
     * Reset all switch states. Called on level load.
     */
    public void reset() {
        java.util.Arrays.fill(switchState, (byte) 0);
    }
}
