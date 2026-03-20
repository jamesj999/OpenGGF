package com.openggf.sprites.playable;

import com.openggf.camera.Camera;
import com.openggf.physics.Direction;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

/**
 * Sonic-specific respawn strategy: walks or spindashes in from the nearest floor
 * at the screen edge, opposite to the leader's movement direction.
 * <p>
 * If the leader is moving fast (above {@link #SPINDASH_SPEED_THRESHOLD}), Sonic
 * enters in a rolling state at spindash speed. Otherwise, he walks in at a moderate pace.
 */
public class SonicRespawnStrategy implements SidekickRespawnStrategy {

    /** Maximum downward probe depth in pixels (8 steps x 16px). */
    private static final int FLOOR_SEARCH_DEPTH = 128;

    /** Leader ground speed threshold for spindash vs walk entry (subpixels). */
    private static final int SPINDASH_SPEED_THRESHOLD = 0x600;

    /** Initial rolling ground speed when entering via spindash (subpixels). */
    private static final int SPINDASH_RELEASE_SPEED = 0x800;

    /** Initial walking ground speed (subpixels). */
    private static final int WALK_ENTRY_SPEED = 0x200;

    /** Distance in pixels at which the sidekick is considered close enough to the leader. */
    private static final int APPROACH_COMPLETE_THRESHOLD = 32;

    @Override
    public boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        Camera camera = Camera.getInstance();
        if (camera == null) {
            return false;
        }

        // Determine screen edge: leader moving right -> enter from left, and vice versa
        int edgeX;
        if (leader.getXSpeed() > 0) {
            edgeX = camera.getX() - 32;
        } else if (leader.getXSpeed() < 0) {
            edgeX = camera.getX() + 320 + 32;
        } else {
            // Stopped — default to left edge
            edgeX = camera.getX() - 32;
        }

        // Terrain probe: iterate downward from leader's Y to find floor
        int probeX = edgeX;
        int probeY = leader.getCentreY();
        TerrainCheckResult floorResult = null;
        int foundProbeY = probeY;
        for (int step = 0; step < 8; step++) {
            int testY = probeY + (step * 16);
            TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(probeX, testY);
            if (result.foundSurface() && result.distance() >= 0) {
                floorResult = result;
                foundProbeY = testY;
                break;
            }
        }

        if (floorResult == null) {
            return false; // No ground found — stay in SPAWNING
        }

        // Floor Y is the probe point plus the distance to the surface
        int floorY = foundProbeY + floorResult.distance();

        // Place sidekick on the floor at the screen edge
        sidekick.setCentreX((short) edgeX);
        // The floor is where the bottom of the sprite should be, so centre Y is
        // floor minus half the sprite height
        sidekick.setCentreY((short) (floorY - (sidekick.getHeight() / 2)));

        // Reset state flags
        sidekick.setAir(false);
        sidekick.setDead(false);
        sidekick.setHurt(false);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);

        // Let normal physics drive movement
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        sidekick.setForcedAnimationId(-1);

        // Face toward the leader
        boolean leaderIsRight = leader.getCentreX() >= edgeX;
        sidekick.setDirection(leaderIsRight ? Direction.RIGHT : Direction.LEFT);

        // Choose walk vs spindash based on leader speed
        if (Math.abs(leader.getGSpeed()) > SPINDASH_SPEED_THRESHOLD) {
            sidekick.setRolling(true);
            short rollSpeed = (short) (leaderIsRight ? SPINDASH_RELEASE_SPEED : -SPINDASH_RELEASE_SPEED);
            sidekick.setGSpeed(rollSpeed);
        } else {
            short walkSpeed = (short) (leaderIsRight ? WALK_ENTRY_SPEED : -WALK_ENTRY_SPEED);
            sidekick.setGSpeed(walkSpeed);
        }

        // gSpeed drives ground movement; clear axis speeds
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);

        return true;
    }

    @Override
    public boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                                     int frameCounter) {
        // The sidekick is running via gSpeed set in beginApproach with normal physics.
        // Check proximity to leader — when close enough, approach is complete.
        int dx = Math.abs(leader.getCentreX() - sidekick.getCentreX());
        return dx <= APPROACH_COMPLETE_THRESHOLD;
    }
}
