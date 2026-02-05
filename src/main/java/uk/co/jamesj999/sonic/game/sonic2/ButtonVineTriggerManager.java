package uk.co.jamesj999.sonic.game.sonic2;

import java.util.Arrays;

/**
 * Manages the ButtonVine_Trigger system used by MCZ objects.
 * <p>
 * ROM Reference: s2.asm ButtonVine_Trigger ($FFFFF7CA) - 16-byte array
 * <p>
 * This system coordinates between:
 * <ul>
 *   <li>MovingVine (Object 0x80) - Sets trigger bit when player grabs a button vine</li>
 *   <li>MCZDrawbridge (Object 0x81) - Reads trigger bit to raise/lower drawbridge</li>
 * </ul>
 * <p>
 * Each vine/drawbridge pair shares a switch ID (0-15) encoded in the subtype's lower nibble.
 * When a player grabs a button vine, its trigger is set; when released, it's cleared.
 * The corresponding drawbridge monitors its trigger to animate accordingly.
 */
public class ButtonVineTriggerManager {
    /**
     * 16-entry trigger array (one per switch ID).
     * ROM: ButtonVine_Trigger at $FFFFF7CA
     */
    private static final boolean[] triggers = new boolean[16];

    private ButtonVineTriggerManager() {
        // Static utility class
    }

    /**
     * Sets or clears a trigger for the given switch ID.
     * ROM: bset/bclr #0,(ButtonVine_Trigger,d0.w)
     *
     * @param id    Switch ID (0-15, from subtype & 0x0F)
     * @param value true to set trigger, false to clear
     */
    public static void setTrigger(int id, boolean value) {
        if (id >= 0 && id < 16) {
            triggers[id] = value;
        }
    }

    /**
     * Checks if a trigger is currently set.
     * ROM: btst #0,(ButtonVine_Trigger,d0.w)
     *
     * @param id Switch ID (0-15)
     * @return true if trigger is set
     */
    public static boolean getTrigger(int id) {
        return id >= 0 && id < 16 && triggers[id];
    }

    /**
     * Resets all triggers to false.
     * Called on level load to ensure clean state.
     */
    public static void reset() {
        Arrays.fill(triggers, false);
    }
}
