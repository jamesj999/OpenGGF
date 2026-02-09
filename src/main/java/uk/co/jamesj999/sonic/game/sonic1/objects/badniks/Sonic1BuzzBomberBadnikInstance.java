package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.PointsObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AbstractBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AnimalObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Buzz Bomber (0x22) - Flying bee Badnik from GHZ/MZ/SYZ.
 * Flies horizontally, detects Sonic's proximity, hovers and fires a missile downward,
 * then resumes flying.
 * <p>
 * Based on docs/s1disasm/_incObj/22 Buzz Bomber.asm.
 * <p>
 * State machine:
 * <ul>
 *   <li>ob2ndRout=0 (.move): Hover, waiting for timer. If near Sonic: fire. Otherwise: start flying.</li>
 *   <li>ob2ndRout=2 (.chknearsonic): Flying, checking proximity. Stop on timer expire or Sonic detect.</li>
 * </ul>
 */
public class Sonic1BuzzBomberBadnikInstance extends AbstractBadnikInstance {

    // From disassembly: obColType = 8 (enemy, collision size index 8)
    // Collision size 8: width=$18 (24px), height=$0C (12px)
    private static final int COLLISION_SIZE_INDEX = 0x08;

    // Horizontal velocity: move.w #$400,obVelX(a0)
    private static final int FLY_VELOCITY = 0x400;

    // Timer values (in frames, 60fps)
    private static final int FLY_DURATION = 127;        // buzz_timedelay for flying
    private static final int TURN_DELAY = 59;            // hover time after direction change
    private static final int NEAR_SONIC_DELAY = 29;      // hover time after detecting Sonic
    private static final int POST_FIRE_DELAY = 59;       // hover time after firing missile

    // Proximity detection: bhi.s .notsonic (distance >= $60)
    private static final int SONIC_PROXIMITY = 0x60;     // 96 pixels horizontal distance

    // Missile spawn offsets from disassembly .fire routine
    private static final int MISSILE_Y_OFFSET = 0x1C;    // 28 pixels below Buzz Bomber
    private static final int MISSILE_X_OFFSET = 0x18;    // 24 pixels horizontally (or $14 with FixBugs)

    // Missile velocity: move.w #$200,obVelX(a1) / move.w #$200,obVelY(a1)
    private static final int MISSILE_X_VEL = 0x200;
    private static final int MISSILE_Y_VEL = 0x200;

    // Secondary routine states (ob2ndRout values / 2)
    private static final int STATE_HOVER = 0;
    private static final int STATE_FLY = 1;

    // Buzz status values (buzz_buzzstatus / objoff_34)
    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_FIRED = 1;
    private static final int STATUS_NEAR_SONIC = 2;

    private int secondaryState;
    private int timeDelay;
    private int buzzStatus;
    private int renderedFrame; // Actual frame index for rendering (includes wing cycle)
    private int wingTimer;     // Per-object animation timer (matches ROM's obTimeFrame)

    public Sonic1BuzzBomberBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "BuzzBomber");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        // S1: obStatus bit 0 set = facing right
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlip;
        this.secondaryState = STATE_HOVER;
        this.timeDelay = 0;
        this.buzzStatus = STATUS_NORMAL;
        this.wingTimer = 1; // Start with speed=1 (AnimateSprite initial obTimeFrame)
        this.renderedFrame = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (secondaryState) {
            case STATE_HOVER -> updateHover(player);
            case STATE_FLY -> updateFly(player);
        }
    }

    /**
     * ob2ndRout=0 (.move): Hover state.
     * Decrement timer; when expired, either fire (if near Sonic) or start flying.
     */
    private void updateHover(AbstractPlayableSprite player) {
        timeDelay--;
        if (timeDelay >= 0) {
            return; // Still waiting
        }

        // Timer expired
        if ((buzzStatus & 2) != 0) {
            // Near Sonic: fire missile
            fireMissile();
        } else {
            // Start flying
            timeDelay = FLY_DURATION;
            xVelocity = facingLeft ? -FLY_VELOCITY : FLY_VELOCITY;
            animFrame = 2; // Flying animation (frames 2-3)
            secondaryState = STATE_FLY;
        }
    }

    /**
     * ob2ndRout=2 (.chknearsonic): Flying state.
     * Move horizontally, check proximity to Sonic.
     */
    private void updateFly(AbstractPlayableSprite player) {
        timeDelay--;
        if (timeDelay < 0) {
            // Timer expired: change direction
            changeDirection();
            return;
        }

        // Apply velocity (SpeedToPos: 16.8 fixed-point)
        currentX += (xVelocity >> 8);

        // Check proximity to Sonic (only if buzzStatus == 0 = normal)
        if (buzzStatus == STATUS_NORMAL && player != null) {
            int dx = Math.abs(player.getCentreX() - currentX);
            if (dx < SONIC_PROXIMITY && isOnScreenX()) {
                // Near Sonic: stop and prepare to fire
                buzzStatus = STATUS_NEAR_SONIC;
                timeDelay = NEAR_SONIC_DELAY;
                stopAndHover();
            }
        }
    }

    /**
     * .chgdirection: Reset status, flip direction, set turn delay, stop.
     */
    private void changeDirection() {
        buzzStatus = STATUS_NORMAL;
        facingLeft = !facingLeft;
        timeDelay = TURN_DELAY;
        stopAndHover();
    }

    /**
     * .stop: Return to hover state, zero velocity, hovering animation.
     */
    private void stopAndHover() {
        secondaryState = STATE_HOVER;
        xVelocity = 0;
        animFrame = 0; // Hovering animation (frames 0-1)
    }

    /**
     * .fire: Spawn a missile object below the Buzz Bomber.
     * From disassembly: creates id_Missile at Y+$1C, X+/-$18,
     * velocity $200 in both axes.
     */
    private void fireMissile() {
        int missileX = facingLeft
                ? currentX - MISSILE_X_OFFSET
                : currentX + MISSILE_X_OFFSET;
        int missileY = currentY + MISSILE_Y_OFFSET;

        int missileXVel = facingLeft ? -MISSILE_X_VEL : MISSILE_X_VEL;

        Sonic1BuzzBomberMissileInstance missile = new Sonic1BuzzBomberMissileInstance(
                missileX, missileY, missileXVel, MISSILE_Y_VEL,
                facingLeft, this, levelManager);

        levelManager.getObjectManager().addDynamicObject(missile);

        // Prevent refiring: set buzzStatus = 1
        buzzStatus = STATUS_FIRED;
        timeDelay = POST_FIRE_DELAY;
        animFrame = 4; // Firing animation (frames 4-5)
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // ROM AnimateSprite: obTimeFrame decrements from speed value (1) to 0, then advances.
        // Per-object timer ensures independent wing flap timing for each Buzz Bomber.
        wingTimer--;
        if (wingTimer < 0) {
            wingTimer = 1; // speed=1: each wing frame shows for 2 game frames
            // Toggle between base frame and base+1
            if (renderedFrame == animFrame) {
                renderedFrame = animFrame + 1;
            } else {
                renderedFrame = animFrame;
            }
        }
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    /**
     * Override destroyBadnik to use game-agnostic GameSound instead of
     * hardcoded Sonic2AudioConstants.
     */
    @Override
    protected void destroyBadnik(AbstractPlayableSprite player) {
        destroyed = true;
        setDestroyed(true);

        var objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            objectManager.removeFromActiveSpawns(spawn);
        }

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, currentX, currentY,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);

        AnimalObjectInstance animal = new AnimalObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x28, 0, 0, false, 0), levelManager);
        levelManager.getObjectManager().addDynamicObject(animal);

        int pointsValue = 100;
        if (player != null) {
            pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        PointsObjectInstance points = new PointsObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x29, 0, 0, false, 0), levelManager, pointsValue);
        levelManager.getObjectManager().addDynamicObject(points);

        AudioManager.getInstance().playSfx(Sonic1Sfx.BREAK_ITEM.id);
    }

    /**
     * ROM equivalent: MarkObjGone keeps objects alive while near the camera.
     * The spawn windowing system checks the original spawn.x() against the camera
     * window, but a flying Buzz Bomber can move far from its spawn. When the camera
     * follows the player backward (away from spawn), the spawn may leave the window
     * even though the Buzz Bomber itself is still on-screen.
     *
     * Return true while our current position is within a generous margin of the
     * camera viewport, preventing the spawn windowing system from removing us
     * prematurely. We will be cleaned up normally when we are truly off-screen.
     */
    @Override
    public boolean isPersistent() {
        // ROM MarkObjGone: ~128px left margin, ~64px right margin.
        // We use 160px for a symmetric, generous margin.
        return !destroyed && isOnScreenX(160);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3); // obPriority = 3
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BUZZ_BOMBER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Sprite art faces left by default; flip when facing right (same as S2 Buzzer)
        renderer.drawFrameIndex(renderedFrame, currentX, currentY, !facingLeft, false);
    }
}
