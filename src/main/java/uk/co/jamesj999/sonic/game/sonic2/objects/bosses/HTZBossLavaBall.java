package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossChild;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * HTZ Boss Lava Ball projectile (Obj52 subtype 6).
 * ROM Reference: s2.asm:63900-64006 (Obj52_LavaBall)
 *
 * Lava balls are spawned in pairs (left and right) when the boss descends.
 * They arc upward then fall, transforming into lava bubbles on ground contact.
 * They damage the player on contact.
 */
public class HTZBossLavaBall extends AbstractBossChild implements TouchResponseProvider {

    // Physics constants (ROM: s2.asm:63987-64006)
    /** Left ball X velocity (ROM: move.w #$1C00,d0 / neg.w d0) */
    private static final int LEFT_X_VEL = -0x1C00;
    /** Right ball X velocity (ROM: move.w #$1C00,d0) */
    private static final int RIGHT_X_VEL = 0x1C00;
    /** Initial Y velocity from left side (ROM: move.w #-$5400,y_vel(a1)) */
    private static final int Y_VEL_LEFT_SIDE = -0x5400;
    /** Initial Y velocity from right side (ROM: move.w #-$6400,y_vel(a1)) */
    private static final int Y_VEL_RIGHT_SIDE = -0x6400;
    /** Gravity per frame (ROM: addi.w #$380,y_vel(a0)) */
    private static final int GRAVITY = 0x380;
    /**
     * Obj52 child objects use ArtTile_ArtNem_HTZBoss as their art base, while the
     * parent renderer sheet is aligned to ArtTile_ArtNem_Eggpod_2. The VRAM delta is
     * $60 tiles, so child tile indices must be shifted by +$60 on this combined sheet.
     */
    private static final int CHILD_TILE_BASE_OFFSET = 0x60;
    private static final int TILE_LARGE_FIRE_1 = CHILD_TILE_BASE_OFFSET + 0x63;
    private static final int TILE_LARGE_FIRE_2 = CHILD_TILE_BASE_OFFSET + 0x67;

    // Fixed-point position accumulators (ROM: objoff_2A for x, y_pos for y)
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;

    // State
    private boolean leftBall;
    private boolean fromLeftSide;
    private int animFrame;
    private int animTimer;
    private static final int Y_RADIUS = 8;  // ROM: move.b #8,y_radius(a1)

    public HTZBossLavaBall(Sonic2HTZBossInstance parent, int spawnX, int spawnY,
                           boolean leftBall, boolean fromLeftSide) {
        super(parent, "HTZ Lava Ball", 3, Sonic2ObjectIds.HTZ_BOSS);
        this.leftBall = leftBall;
        this.fromLeftSide = fromLeftSide;

        // Initial position
        this.currentX = spawnX;
        this.currentY = spawnY;
        this.xFixed = spawnX << 16;
        this.yFixed = spawnY << 16;

        // Set velocities based on which ball and which side boss is on
        // ROM: s2.asm:63968-64000
        this.xVel = leftBall ? LEFT_X_VEL : RIGHT_X_VEL;

        // Y velocity depends on boss position (stronger launch from right side)
        // ROM: cmpi.w #$2F40,x_pos(a1) / beq.s loc_30000 / move.w #-$6400,y_vel(a1)
        if (fromLeftSide) {
            this.yVel = Y_VEL_LEFT_SIDE;
        } else {
            this.yVel = Y_VEL_RIGHT_SIDE;
        }

        // Animation state
        this.animFrame = 0;
        this.animTimer = 3;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }

        // Apply physics (ROM: Obj52_LavaBall_Move)
        // ROM uses 16.16 fixed-point math
        // move.w x_vel(a0),d0 / ext.l d0 / asl.l #4,d0 / add.l d0,d2
        xFixed += (xVel << 4);
        yFixed += (yVel << 4);

        // Apply gravity
        // ROM: addi.w #$380,y_vel(a0)
        yVel += GRAVITY;

        // Update position from fixed-point
        currentX = xFixed >> 16;
        currentY = yFixed >> 16;

        // Check floor collision
        // ROM: jsrto JmpTo4_ObjCheckFloorDist
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        if (floor.hasCollision() && floor.distance() < 0) {
            // Hit the floor - transform to lava bubble
            // ROM: s2.asm:64016-64060
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_FIRE_BURN);

            // Spawn lava bubble at floor contact point
            LavaBubbleObjectInstance bubble = new LavaBubbleObjectInstance(
                    currentX,
                    currentY + floor.distance() // Snap to floor surface
            );
            LevelManager.getInstance().getObjectManager().addDynamicObject(bubble);

            setDestroyed(true);
            return;
        }

        // Update animation
        animTimer--;
        if (animTimer <= 0) {
            animTimer = 3;
            animFrame = (animFrame + 1) % 2;  // ROM: Animation 7 has 2 frames
        }

        // Check if off-screen (safety destroy)
        if (currentY > 0x700) {  // Well below lava level
            setDestroyed(true);
        }

        updateDynamicSpawn();
    }

    /**
     * Returns collision flags for touch response.
     * ROM: move.b #$8B,collision_flags(a1) - enemy projectile with collision size
     */
    public int getCollisionFlags() {
        if (isDestroyed()) {
            return 0;
        }
        return 0x8B;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        uk.co.jamesj999.sonic.level.LevelManager levelMgr = uk.co.jamesj999.sonic.level.LevelManager.getInstance();
        uk.co.jamesj999.sonic.level.objects.ObjectRenderManager renderManager =
                levelMgr != null ? levelMgr.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer renderer =
                renderManager.getRenderer(uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys.HTZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Lava ball uses frames 14-15 (large lava ball frames)
        // ROM: Animation 7 - byte_302B7: dc.b 3, $E, $F, $FF → frames 14 ($E), 15 ($F)
        int baseTile = (animFrame == 0) ? TILE_LARGE_FIRE_1 : TILE_LARGE_FIRE_2;
        int drawX = currentX - 8;
        int drawY = currentY - 8;
        // Sprite mapping pieces use column-major tile order:
        // tileOffset = (tx * heightTiles) + ty (same as SpritePieceRenderer).
        for (int ty = 0; ty < 2; ty++) {
            for (int tx = 0; tx < 2; tx++) {
                int tile = baseTile + (tx * 2) + ty;
                renderer.drawPatternIndex(tile, drawX + (tx * 8), drawY + (ty * 8), 0);
            }
        }
    }
}
