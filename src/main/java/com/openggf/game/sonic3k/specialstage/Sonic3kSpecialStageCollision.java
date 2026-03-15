package com.openggf.game.sonic3k.specialstage;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;

/**
 * Grid collision detection for the S3K Blue Ball special stage.
 * <p>
 * Checks the cell at the player's current position and triggers
 * appropriate responses (collection, fail, bounce, spring).
 * <p>
 * Collision only triggers when the player is aligned to a cell boundary
 * (low bits of position are zero).
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm sub_972E (line 12088)
 */
public class Sonic3kSpecialStageCollision {

    /** Result of a collision check. */
    public enum CollisionResult {
        /** No collision occurred (empty cell or not aligned). */
        NONE,
        /** Blue sphere collected. */
        BLUE_SPHERE,
        /** Red sphere hit - stage failed. */
        RED_SPHERE,
        /** Bumper hit - bounce backward. */
        BUMPER,
        /** Ring collected. */
        RING,
        /** Spring hit - spring jump. */
        SPRING,
        /** Emerald collected - stage complete. */
        EMERALD
    }

    /** Data returned from a collision check. */
    public static class CollisionData {
        public final CollisionResult result;
        /** Grid buffer index where collision occurred. */
        public final int gridIndex;

        public CollisionData(CollisionResult result, int gridIndex) {
            this.result = result;
            this.gridIndex = gridIndex;
        }

        public static final CollisionData NONE = new CollisionData(CollisionResult.NONE, -1);
    }

    /**
     * Check for collision at the player's current position.
     * ROM: sub_972E (sonic3k.asm:12088)
     *
     * @param grid the game grid
     * @param player the player state
     * @return collision data describing what happened
     */
    public CollisionData checkCollision(Sonic3kSpecialStageGrid grid,
                                        Sonic3kSpecialStagePlayer player) {
        // Skip collision during jump
        if (player.getJumping() < 0) {
            return CollisionData.NONE;
        }
        // Skip during clear routine
        // (clearRoutineActive would be checked by the caller)

        int xPos = player.getXPos();
        int yPos = player.getYPos();
        int gridIndex = Sonic3kSpecialStageGrid.positionToIndex(xPos, yPos);
        int cellType = grid.getCellByIndex(gridIndex);

        if (cellType == CELL_EMPTY) {
            return CollisionData.NONE;
        }

        switch (cellType) {
            case CELL_RED:
                return checkRedSphere(xPos, yPos, gridIndex);

            case CELL_BLUE:
                return new CollisionData(CollisionResult.BLUE_SPHERE, gridIndex);

            case CELL_BUMPER:
                // Only trigger bumper if not already locked
                if (!player.isBumperLocked()) {
                    return new CollisionData(CollisionResult.BUMPER, gridIndex);
                }
                return CollisionData.NONE;

            case CELL_RING:
                return new CollisionData(CollisionResult.RING, gridIndex);

            case CELL_SPRING:
                return checkSpring(player, gridIndex);

            case CELL_CHAOS_EMERALD:
            case CELL_SUPER_EMERALD:
                return checkEmerald(xPos, yPos, gridIndex);

            default:
                return CollisionData.NONE;
        }
    }

    /**
     * Red sphere collision: only triggers fail when player is
     * fully aligned to the cell (both X and Y low bits zero).
     * ROM: loc_97AA (sonic3k.asm:12131)
     */
    private CollisionData checkRedSphere(int xPos, int yPos, int gridIndex) {
        int combined = xPos | yPos;
        if ((combined & CELL_ALIGN_MASK) != 0) {
            return CollisionData.NONE; // Not aligned - don't fail yet
        }
        return new CollisionData(CollisionResult.RED_SPHERE, gridIndex);
    }

    /**
     * Spring collision: only triggers when not jumping and angle-aligned.
     * ROM: loc_97EE (sonic3k.asm:12158)
     */
    private CollisionData checkSpring(Sonic3kSpecialStagePlayer player, int gridIndex) {
        if (player.getJumping() < 0) {
            return CollisionData.NONE;
        }
        if ((player.getAngle() & ANGLE_ALIGN_MASK) != 0) {
            return CollisionData.NONE;
        }
        return new CollisionData(CollisionResult.SPRING, gridIndex);
    }

    /**
     * Emerald collision: triggers when player is aligned and at the emerald cell.
     * ROM: loc_9C80 (sonic3k.asm:12629)
     */
    private CollisionData checkEmerald(int xPos, int yPos, int gridIndex) {
        int combined = xPos | yPos;
        if ((combined & CELL_ALIGN_MASK) != 0) {
            return CollisionData.NONE;
        }
        return new CollisionData(CollisionResult.EMERALD, gridIndex);
    }
}
