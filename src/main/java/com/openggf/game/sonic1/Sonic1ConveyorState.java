package com.openggf.game.sonic1;

/**
 * Manages global state for Labyrinth Zone / Scrap Brain Zone conveyor belt platforms.
 * <p>
 * From the Sonic 1 disassembly (Variables.asm):
 * <pre>
 *   f_conveyrev:  ds.b 1  ; flag set to reverse conveyor belts in LZ/SBZ
 *   v_obj63:      ds.b 6  ; object 63 (LZ/SBZ platforms) spawner tracking variables
 * </pre>
 * <p>
 * The f_conveyrev flag reverses conveyor direction when set by switch $E
 * (via sub_12502 in the disassembly). The v_obj63 array tracks which spawner
 * groups have already been instantiated, preventing duplicate spawning.
 * <p>
 * Reference: docs/s1disasm/_incObj/63 LZ Conveyor.asm
 */
public final class Sonic1ConveyorState {

    private static final int SPAWNER_SLOTS = 6;
    private static Sonic1ConveyorState instance;

    /** f_conveyrev: global direction reversal flag. */
    private boolean reversed;

    /** v_obj63: per-spawner instantiation tracking (bit 0 of each byte). */
    private final boolean[] spawned = new boolean[SPAWNER_SLOTS];

    private Sonic1ConveyorState() {
    }

    public static Sonic1ConveyorState getInstance() {
        if (instance == null) {
            instance = new Sonic1ConveyorState();
        }
        return instance;
    }

    public static void resetInstance() {
        instance = null;
    }

    /**
     * Returns true if conveyor direction has been reversed.
     * Mirrors: tst.b (f_conveyrev).w
     */
    public boolean isReversed() {
        return reversed;
    }

    /**
     * Sets the conveyor reversal flag.
     * Mirrors: move.b #1,(f_conveyrev).w
     */
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    /**
     * Checks if a spawner slot has already been instantiated.
     * Mirrors: bset #0,(a2,d0.w) with a2 = v_obj63
     *
     * @param index spawner slot (0-5), from (subtype & 0x7F)
     * @return true if already spawned (bset returned NE)
     */
    public boolean testAndSetSpawned(int index) {
        if (index < 0 || index >= SPAWNER_SLOTS) {
            return true; // treat out-of-range as already spawned
        }
        boolean wasSpawned = spawned[index];
        spawned[index] = true;
        return wasSpawned;
    }

    /**
     * Clears a spawner slot.
     * Mirrors: bclr #0,(a2,d0.w) with a2 = v_obj63
     *
     * @param index spawner slot (0-5)
     */
    public void clearSpawned(int index) {
        if (index >= 0 && index < SPAWNER_SLOTS) {
            spawned[index] = false;
        }
    }

    /**
     * Resets all state. Called on level load.
     */
    public void reset() {
        reversed = false;
        java.util.Arrays.fill(spawned, false);
    }
}
