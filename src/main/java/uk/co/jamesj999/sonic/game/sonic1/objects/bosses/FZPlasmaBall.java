package uk.co.jamesj999.sonic.game.sonic1.objects.bosses;

import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * FZ Plasma Ball — energy ball projectile fired by the plasma launcher.
 * ROM: _incObj/86 FZ Plasma Ball Launcher.asm (routine 8, BossPlasma_Index2)
 *
 * 3-phase movement via ob2ndRout:
 *   Phase 0 (Launch): Calculate xvel from distance to target X, set lifetime $B4
 *   Phase 2 (Travel): Move to target X via velocity. When reached, set collision
 *                      (obColType=$9A), chase Sonic with xvel=player distance, yvel=$140
 *   Phase 4 (Chase):  SpeedToPos. Delete when Y >= boss_fz_y+$D0 or lifetime expired
 *
 * Uses palette line 1 (obGfx has palette bit set).
 * Collision type $9A = category 4 (hurt player), size $1A.
 */
public class FZPlasmaBall extends AbstractObjectInstance implements TouchResponseProvider {

    private static final int BOSS_FZ_Y = Sonic1Constants.BOSS_FZ_Y;

    private final LevelManager levelManager;
    private final FZPlasmaLauncher launcher;

    // Movement state
    private int phase;       // ob2ndRout: 0, 2, 4
    private int xVel;        // obVelX
    private int yVel;        // obVelY
    private int targetX;     // objoff_30 — target X position
    private int lifetime;    // obSubtype — countdown timer

    // Position — 32-bit fixed-point (16.16) to match ROM's SpeedToPos
    // ROM: SpeedToPos adds (obVelX << 8) to 32-bit obX position each frame
    private int posXFixed;  // 16.16 fixed-point X
    private int posYFixed;  // 16.16 fixed-point Y
    private int posX;       // Pixel X (posXFixed >> 16)
    private int posY;       // Pixel Y (posYFixed >> 16)

    // Animation
    private int animFrame;
    private int animTimer;
    private boolean hasCollision; // Whether touch response is active

    public FZPlasmaBall(FZPlasmaLauncher launcher, LevelManager levelManager,
                        int startX, int startY, int targetX) {
        super(new ObjectSpawn(startX, startY, Sonic1ObjectIds.BOSS_PLASMA, 0, 0, false, 0),
                "FZ Plasma Ball");
        this.levelManager = levelManager;
        this.launcher = launcher;
        this.posX = startX;
        this.posY = startY;
        this.posXFixed = startX << 16;
        this.posYFixed = startY << 16;
        this.targetX = targetX;
        this.phase = 0;
        this.xVel = 0;
        this.yVel = 0;
        this.lifetime = 0;
        this.animFrame = 0;
        this.animTimer = 0;
        this.hasCollision = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (phase) {
            case 0 -> updateLaunch();
            case 2 -> updateTravel(player);
            case 4 -> updateChase(player);
        }

        // Update animation
        updateAnimation();
    }

    /**
     * Phase 0 (loc_1A9A6): Calculate initial velocity toward target X.
     * ROM: xvel = (targetX - currentX) << 4, lifetime = $B4
     */
    private void updateLaunch() {
        int dist = targetX - posX;
        xVel = dist << 4;
        lifetime = 0xB4; // ROM: move.w #$B4,obSubtype
        phase = 2;
    }

    /**
     * Phase 2 (loc_1A9C0): Travel to target X, then chase Sonic.
     * ROM: SpeedToPos, check if reached target, then set collision and chase
     */
    private void updateTravel(AbstractPlayableSprite player) {
        if (xVel != 0) {
            // ROM: SpeedToPos — adds (velocity << 8) to 32-bit position
            posXFixed += (xVel << 8);
            posX = posXFixed >> 16;

            // ROM: Check if reached target (overshot)
            // ROM: move.w obX(a1),d0 / sub.w objoff_30(a1),d0 / bcc.s (unsigned >= 0)
            int dist = posX - targetX;
            if (dist >= 0) {
                // Reached target
                xVel = 0;
                posX = targetX;
                posXFixed = posX << 16;
                // ROM: subq.w #1,objoff_32(a1) — decrement launcher's ball counter
            }
        }

        // ROM: move.b #0,obAnim — full plasma animation
        animFrame = 0;

        // ROM: subq.w #1,obSubtype — decrement lifetime
        lifetime--;
        if (lifetime <= 0) {
            // Lifetime expired during travel — transition to chase
            startChase(player);
        }
    }

    /**
     * Transition to chase phase.
     * ROM: loc_1A9E6 end section
     */
    private void startChase(AbstractPlayableSprite player) {
        phase = 4;
        hasCollision = true; // ROM: move.b #$9A,obColType

        // ROM: Calculate chase velocity
        if (player != null) {
            int dist = (player.getCentreX() & 0xFFFF) - posX;
            xVel = dist; // ROM: move.w d0,obVelX
        } else {
            xVel = 0;
        }
        yVel = 0x140; // ROM: move.w #$140,obVelY
        lifetime = 0xB4; // ROM: move.w #$B4,obSubtype
    }

    /**
     * Phase 4 (loc_1AA1E): Chase with gravity.
     * ROM: SpeedToPos, delete when Y >= boss_fz_y+$D0 or lifetime expired
     */
    private void updateChase(AbstractPlayableSprite player) {
        // ROM: SpeedToPos — adds (velocity << 8) to 32-bit position
        posXFixed += (xVel << 8);
        posYFixed += (yVel << 8);
        posX = posXFixed >> 16;
        posY = posYFixed >> 16;

        // ROM: cmpi.w #boss_fz_y+$D0,obY — check ground
        if (posY >= BOSS_FZ_Y + 0xD0) {
            destroyBall();
            return;
        }

        // ROM: subq.w #1,obSubtype — decrement lifetime
        lifetime--;
        if (lifetime <= 0) {
            destroyBall();
        }
    }

    private void destroyBall() {
        // ROM: subq.w #1,objoff_38(a1) — decrement launcher's ball count
        if (launcher != null) {
            launcher.onBallDestroyed();
        }
        setDestroyed(true);
    }

    private void updateAnimation() {
        animTimer++;
        // Simple frame cycling — the ROM uses Ani_Plasma which has complex sequences
        // Simplified to cycle through key frames
        if (phase <= 2) {
            // Full plasma animation (anim 0): cycles through fuzzy/white frames
            animFrame = ((animTimer >> 1) % 6); // Cycle 0-5
        } else {
            // Short plasma animation (anim 1): smaller frames
            animFrame = 6 + ((animTimer >> 1) % 2); // Frames 6-7
        }
    }

    @Override
    public int getCollisionFlags() {
        // ROM: obColType = $9A during chase phase (category 4 = hurt, size $1A)
        if (hasCollision) {
            return 0x9A;
        }
        return 0;
    }

    @Override
    public int getCollisionProperty() {
        // Plasma balls have no collision property (not a boss with hit count)
        return 0;
    }

    @Override
    public int getX() {
        return posX;
    }

    @Override
    public int getY() {
        return posY;
    }

    @Override
    public int getPriorityBucket() {
        return 3;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.FZ_PLASMA);
        if (renderer == null || !renderer.isReady()) return;

        int frame = Math.min(animFrame, 10);
        renderer.drawFrameIndex(frame, posX, posY, false, false);
    }
}
