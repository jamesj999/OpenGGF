package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.boss.AbstractBossChild;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * HTZ Boss Flamethrower projectile (Obj52 subtype 4).
 * ROM Reference: s2.asm:63858-63896 (Obj52_FlameThrower)
 *
 * The flamethrower spawns above the boss and drifts horizontally while animating.
 * It damages the player on contact.
 *
 * Animation uses ROM Ani_obj52 animation 5:
 *   byte_302B0: dc.b 3, $C, $D, $FF  ; Delay 3, frames 12, 13, then end
 * This cycles frames 12 ($C) and 13 ($D) which are small lava projectile frames.
 */
public class HTZBossFlamethrower extends AbstractBossChild {

    // Movement constants (ROM: s2.asm:63869-63892)
    /** Horizontal offset from boss (ROM: move.w #-$70,d0) */
    private static final int X_OFFSET = -0x70;
    /** Horizontal velocity (ROM: move.w #-4,d1) */
    private static final int X_VELOCITY = -4;

    // Animation constants (ROM: byte_302B0 - animation 5)
    /** Animation delay (ROM: dc.b 3 = delay of 3+1=4 frames) */
    private static final int ANIM_DELAY = 4;
    /** First frame of animation (ROM: frame $C = 12) */
    private static final int FRAME_FIRE_1 = 12;
    /** Second frame of animation (ROM: frame $D = 13) */
    private static final int FRAME_FIRE_2 = 13;

    // State
    private int xVel;
    private boolean flipped;
    private int animFrame;
    private int animTimer;
    private int animCycles;

    public HTZBossFlamethrower(Sonic2HTZBossInstance parent, int spawnX, int spawnY, boolean flipped) {
        super(parent, "HTZ Flamethrower", 4, Sonic2ObjectIds.HTZ_BOSS);
        this.flipped = flipped;

        // Calculate initial position with offset
        // ROM: add.w d0,x_pos(a0)
        int xOffset = flipped ? -X_OFFSET : X_OFFSET;
        this.currentX = spawnX + xOffset;
        this.currentY = spawnY;

        // Set velocity (flip direction if boss is flipped)
        // ROM: neg.w d0 / neg.w d1 if flipped
        this.xVel = flipped ? -X_VELOCITY : X_VELOCITY;

        // Animation state (ROM: animation 5 cycles frames $C, $D then ends)
        this.animFrame = 0;  // 0 = frame $C, 1 = frame $D
        this.animTimer = ANIM_DELAY;
        this.animCycles = 0;  // Track animation cycles for termination
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }

        // Move horizontally
        // ROM: move.w x_vel(a0),d1 / add.w d1,x_pos(a0)
        currentX += xVel;

        // Update animation (ROM: animation 5 - byte_302B0: dc.b 3, $C, $D, $FF)
        // Delay of 3 means change every 4 frames (delay + 1)
        animTimer--;
        if (animTimer <= 0) {
            animTimer = ANIM_DELAY;
            animFrame++;
            if (animFrame > 1) {
                // Animation sequence complete ($FF in ROM = end)
                // ROM terminates animation after cycling through frames $C, $D
                animCycles++;
                if (animCycles >= 3) {
                    // After a few cycles, destroy
                    setDestroyed(true);
                    return;
                }
                animFrame = 0;  // Loop animation
            }
        }

        updateDynamicSpawn();
    }

    /**
     * Returns collision flags for touch response.
     * ROM: move.b #$98,collision_flags(a1) - enemy projectile collision
     */
    public int getCollisionFlags() {
        if (isDestroyed()) {
            return 0;
        }
        return 0x98;
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

        // ROM: Animation 5 uses frames $C (12) and $D (13) - small lava projectile frames
        // animFrame: 0 = FRAME_FIRE_1 (12), 1 = FRAME_FIRE_2 (13)
        int frame = (animFrame == 0) ? FRAME_FIRE_1 : FRAME_FIRE_2;

        renderer.drawFrameIndex(frame, currentX, currentY, flipped, false);
    }
}
