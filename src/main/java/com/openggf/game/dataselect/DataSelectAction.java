package com.openggf.game.dataselect;

/**
 * Represents a user action from the data select screen.
 * Consumed by the game loop to trigger level loading, slot deletion, etc.
 */
public record DataSelectAction(DataSelectActionType type, int slot, int zone, int act) {

    /** Returns a no-op action indicating no selection has been made. */
    public static DataSelectAction none() {
        return new DataSelectAction(DataSelectActionType.NONE, -1, -1, -1);
    }
}
