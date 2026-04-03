package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.util.List;

/**
 * Individual bomb dropped from the AIZ2 Flying Battery battleship.
 *
 * <p>ROM: Obj_AIZShipBomb (sonic3k.asm:105362).
 * Three states matching the ROM:
 * <ol>
 *   <li><b>READY_DROP</b>: Bomb descends slowly (2px/frame) in the ship's bomb port
 *       until its Y offset reaches $A80.</li>
 *   <li><b>DELAY</b>: Pauses for 6 frames at the bottom of the port.</li>
 *   <li><b>DROP</b>: Falls with gravity ($2000 in 16:16 = $20 in 8:8). Plays
 *       sfx_MissileThrow at the start. On ground impact: screen shake ($10 frames),
 *       sfx_MissileExplode, 8 explosion fragments.</li>
 * </ol>
 *
 * <p>The bomb keeps translating from the ship's live secondary-camera state until
 * it is actually released, matching the ROM's {@code Translate_Camera2ObjPosition}
 * and {@code Translate_Camera2ObjX} calls.
 */
public class AizShipBombInstance extends AbstractObjectInstance implements TouchResponseProvider {
    private static final int COLLISION_FLAGS = 0x8B;
    private static final int GRAVITY = 0x20;       // 8:8 fixed-point
    private static final int Y_RADIUS = 0x10;

    // ROM states
    private static final int STATE_READY_DROP = 0;
    private static final int STATE_DELAY = 1;
    private static final int STATE_DROP = 2;

    // ROM: addq.w #2,$30(a0) — descend rate in ReadyDrop
    private static final int READY_DROP_SPEED = 2;
    // ROM: $30(a0) starts at $A60 (initial Y in secondary camera), threshold $A80.
    // Descent = $A80 - $A60 = $20 = 32 pixels over 16 frames.
    private static final int READY_DROP_START = 0xA60;
    private static final int READY_DROP_THRESHOLD = 0xA80;
    // ROM: move.w #6,$32(a0) — delay frames before dropping
    private static final int DROP_DELAY_FRAMES = 6;
    // ROM: move.w #$10,(Screen_shake_flag).w — screen shake duration on impact
    private static final int SCREEN_SHAKE_FRAMES = 0x10;

    /** Bomb-port X in the battleship's secondary-camera space (ROM: $2E). */
    private final int sourceSecondaryX;
    /** Ship object that owns the live secondary-camera translation. */
    private final AizBattleshipInstance sourceShip;
    /** Initial world Y used as a fallback when the source ship is unavailable. */
    private final int initialWorldY;

    private int state;
    private int portYOffset;    // ROM: $30(a0) — Y offset within the bomb port
    private int delayCounter;   // ROM: $32(a0)
    private int currentY;
    private int ySub;
    private int yVel;
    private int frameCounter;

    /**
     * @param spawn            object spawn (engine bookkeeping)
     * @param sourceShip       live battleship object driving the secondary-camera translation
     * @param sourceSecondaryX bomb-port X in the ship's secondary-camera space
     * @param startY           initial world Y position (at the ship's port level)
     */
    public AizShipBombInstance(ObjectSpawn spawn,
                               AizBattleshipInstance sourceShip,
                               int sourceSecondaryX,
                               int startY) {
        super(spawn, "AIZShipBomb");
        this.sourceShip = sourceShip;
        this.sourceSecondaryX = sourceSecondaryX;
        this.initialWorldY = startY;
        this.currentY = startY;
        this.ySub = 0;
        this.yVel = 0;
        this.frameCounter = 0;
        this.state = STATE_READY_DROP;
        this.portYOffset = READY_DROP_START;  // ROM: $30(a0) = $A60
        this.delayCounter = DROP_DELAY_FRAMES;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) return;
        this.frameCounter++;

        switch (state) {
            case STATE_READY_DROP -> {
                // ROM: addq.w #2,$30(a0) — slowly descend in port ($A60→$A80, 16 frames)
                portYOffset += READY_DROP_SPEED;
                currentY = translatePortYToWorld(portYOffset);
                if (portYOffset >= READY_DROP_THRESHOLD) {
                    state = STATE_DELAY;
                }
            }
            case STATE_DELAY -> {
                currentY = translatePortYToWorld(portYOffset);
                // ROM: subq.w #1,$32(a0) — countdown then drop
                delayCounter--;
                if (delayCounter <= 0) {
                    state = STATE_DROP;
                    services().playSfx(Sonic3kSfx.MISSILE_THROW.id);
                }
            }
            case STATE_DROP -> {
                // ROM: gravity fall
                yVel += GRAVITY;
                int yPos16 = (currentY << 8) | (ySub & 0xFF);
                yPos16 += yVel;
                currentY = yPos16 >> 8;
                ySub = yPos16 & 0xFF;

                // Floor collision
                int worldX = getX();
                TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(
                        worldX, currentY, Y_RADIUS);
                if (floorResult != null && floorResult.distance() < 0) {
                    currentY += floorResult.distance();
                    onGroundImpact(worldX);
                    return;
                }

                // Safety: destroy if fallen way off-screen
                if (currentY > 0x0800) {
                    setDestroyed(true);
                }
            }
        }
    }

    private void onGroundImpact(int impactX) {
        // ROM: sfx_MissileExplode
        services().playSfx(Sonic3kSfx.MISSILE_EXPLODE.id);

        // ROM: move.w #$10,(Screen_shake_flag).w — 16-frame timed screen shake
        try {
            Sonic3kAIZEvents events = ((Sonic3kLevelEventManager) services().levelEventProvider()).getAizEvents();
            if (events != null) {
                events.triggerScreenShake(SCREEN_SHAKE_FRAMES);
            }
        } catch (Exception e) {
            // Non-fatal
        }

        spawnExplosionFragments();
        setDestroyed(true);
    }

    private void spawnExplosionFragments() {
        var om = services().objectManager();
        if (om == null) return;

        int[][] fragmentData = {
                {0, -0x3C, 0, 0x0A},
                {0, -0x0C, 1, 0x09},
                {-4, -0x34, 0, 0x08},
                {0x0C, -4, 1, 0x07},
                {-0x0C, -4, 1, 0x05},
                {8, -0x24, 0, 0x04},
                {-8, -0x1C, 0, 0x02},
                {0, -0x0C, 0, 0x00},
        };

        for (int[] data : fragmentData) {
            int fragX = getX() + data[0];
            int fragY = currentY + data[1];
            AizBombExplosionInstance fragment = new AizBombExplosionInstance(
                    fragX, fragY, data[2], data[3]);
            om.addDynamicObject(fragment);
        }
    }

    private int translatePortYToWorld(int secondaryY) {
        try {
            if (sourceShip != null && services().camera() != null) {
                return services().camera().getY() + (secondaryY - sourceShip.getSecondaryCameraY());
            }
        } catch (Exception e) {
            // Fall back to the initial translated position when services are unavailable.
        }
        return initialWorldY + (secondaryY - READY_DROP_START);
    }

    public boolean shouldRenderBehindBattleship() {
        return !isDestroyed()
                && state != STATE_DROP
                && sourceShip != null
                && !sourceShip.isDestroyed();
    }

    public void renderBehindBattleship(ObjectRenderManager renderManager) {
        if (!shouldRenderBehindBattleship() || renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.AIZ2_BOMB_EXPLODE);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(0, getX(), currentY, false, false);
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        // Only damaging during drop phase
        return (state == STATE_DROP) ? COLLISION_FLAGS : 0;
    }

    @Override
    public int getCollisionProperty() { return 0; }

    // --- Position ---

    @Override
    public int getX() {
        try {
            if (sourceShip != null && services().camera() != null) {
                return services().camera().getX() + (sourceSecondaryX - sourceShip.getSecondaryCameraX());
            }
        } catch (Exception e) {
            // Fall through to the spawn X when services are unavailable.
        }
        ObjectSpawn spawn = getSpawn();
        return spawn != null ? spawn.x() : 0;
    }

    @Override
    public int getY() { return currentY; }

    /** The live secondary-camera translation already tracks wrap-back correctly. */
    public void applyWrapOffset(int offset) { }

    // --- Rendering ---

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || shouldRenderBehindBattleship()) return;

        ObjectRenderManager rm = services().renderManager();
        if (rm == null) return;

        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ2_BOMB_EXPLODE);
        if (renderer == null || !renderer.isReady()) return;

        renderer.drawFrameIndex(0, getX(), currentY, false, false);
    }

    @Override
    public boolean isHighPriority() { return false; }

    @Override
    public int getPriorityBucket() { return 2; }
}
