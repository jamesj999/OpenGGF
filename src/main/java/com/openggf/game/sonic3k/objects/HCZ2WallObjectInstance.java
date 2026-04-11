package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.List;

/**
 * HCZ2 Moving Wall — invisible solid collision object for the Act 2 wall-chase sequence.
 *
 * <p>ROM: Obj_HCZ2Wall (sonic3k.asm line ~106226).
 * This is a pure collision rectangle with no art — status bit 7 is set (invisible).
 *
 * <p>Collision rectangle: half-width $4B (75px), height $100 (256px).
 * ROM passes d1=$4B, d2=$100, d3=$100 to SolidObjectFull2.
 * Width_pixels field set to $40 (64px).
 *
 * <p>Y position is fixed at $700 (1792). X position is calculated from the wall offset:
 * {@code x = -wallOffset + $5BE} (negated offset + 1470).
 *
 * <p>The object deletes itself when deactivated by the event handler
 * (Events_routine_bg != 4, i.e. wall-chase state ended).
 */
public class HCZ2WallObjectInstance extends AbstractObjectInstance implements SolidObjectProvider {

    /** ROM: d1 = $4B (75 pixels half-width for SolidObjectFull2). */
    private static final int HALF_WIDTH = 0x4B;

    /** ROM: d2 = $100, d3 = $100 (256 pixels height for both air and ground). */
    private static final int HALF_HEIGHT = 0x100;

    /** ROM: y_pos set to $700 (1792 decimal). */
    private static final int FIXED_Y = 0x700;

    /** ROM: X base offset ($5BE = 1470 decimal). x_pos = -wallOffset + $5BE. */
    private static final int X_BASE_OFFSET = 0x5BE;

    /** Dummy spawn so SolidContacts can safely read objectId/subtype. */
    private static final ObjectSpawn DUMMY_SPAWN =
            new ObjectSpawn(X_BASE_OFFSET, FIXED_Y, 0xFF, 0, 0, false, 0);

    private int wallOffsetSupplierValue;
    private boolean active = true;

    /**
     * Create the wall object.
     * ROM: AllocateObject → move.l #Obj_HCZ2Wall,address(a1)
     */
    public HCZ2WallObjectInstance() {
        super(DUMMY_SPAWN, "HCZ2Wall");
    }

    /**
     * Update the wall position based on the current wall offset.
     * Called each frame by the event handler while the wall chase is active.
     *
     * @param wallOffset current wall movement offset (negative = wall advancing left)
     */
    public void updateWallPosition(int wallOffset) {
        this.wallOffsetSupplierValue = wallOffset;
    }

    /**
     * Mark the wall as inactive (will be destroyed on next update).
     */
    public void deactivate() {
        this.active = false;
    }

    @Override
    public int getX() {
        // ROM: move.w (Events_bg).w,d0 / neg.w d0 / addi.w #$5BE,d0 / move.w d0,x_pos(a0)
        return -wallOffsetSupplierValue + X_BASE_OFFSET;
    }

    @Override
    public int getY() {
        return FIXED_Y;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        // ROM: Obj_HCZ2Wall checks Events_routine_bg == 4; if not, deletes itself.
        // The event handler calls deactivate() when the wall chase ends.
        if (!active) {
            setDestroyed(true);
        }
        // SolidObjectFull2 collision is handled by the engine's SolidContacts system
        // automatically via the SolidObjectProvider interface.
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        // SolidObjectFull2: full solidity on all sides
        return false;
    }

    @Override
    public boolean isPersistent() {
        // Wall must stay active regardless of spawn position / camera window
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> cmds) {
        // ROM: bset #7,status(a0) — invisible, no rendering
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }
}
