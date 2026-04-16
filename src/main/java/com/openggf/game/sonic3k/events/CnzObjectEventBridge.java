package com.openggf.game.sonic3k.events;

/**
 * Narrow write surface for CNZ object code that needs to mutate level-event state.
 *
 * <p>CNZ relies on object-owned routines to poke a small set of event variables
 * that the ROM stores in the shared {@code Events_fg_*} / {@code Events_bg_*}
 * work area. Keeping that surface explicit avoids scattering hidden event-state
 * mutations through object code and preserves a traceable mapping back to
 * {@code Obj_CNZMinibossScrollControl}, the water helpers, and the Knuckles
 * teleporter route.
 */
public interface CnzObjectEventBridge {
    void queueArenaChunkDestruction(int chunkWorldX, int chunkWorldY);
    void setBossScrollState(int offsetY, int velocityY);
    void setBossFlag(boolean value);
    void setEventsFg5(boolean value);
    void setWallGrabSuppressed(boolean value);
    void setWaterButtonArmed(boolean value);
    boolean isWaterButtonArmed();
    void setWaterTargetY(int targetY);
    void beginKnucklesTeleporterRoute();
    void markTeleporterBeamSpawned();
}
