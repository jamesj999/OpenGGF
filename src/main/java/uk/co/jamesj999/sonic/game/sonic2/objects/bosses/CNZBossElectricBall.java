package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.ObjectTerrainUtils;
import uk.co.jamesj999.sonic.physics.TerrainCheckResult;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * CNZ Boss Electric Ball - Hazard projectile spawned by the CNZ boss.
 * ROM Reference: s2.asm Obj51 (subtype 4)
 *
 * States:
 * - ATTACH: Ball lowers from boss
 * - FALL: Ball drops toward floor
 * - SPLIT: Ball splits into two after hitting floor
 */
public class CNZBossElectricBall extends AbstractObjectInstance implements TouchResponseProvider {

    // Routine states
    private static final int BALL_ATTACH = 0;
    private static final int BALL_FALL = 1;
    private static final int BALL_SPLIT = 2;

    // Physics constants
    private static final int GRAVITY = 0x38;
    private static final int Y_RADIUS = 8;
    private static final int DELETE_Y = 0x705;

    // Animation constants
    // Frame 17 = Map_obj51_0144 = 3x3 spiked ball (24x24 pixels)
    // Frame 18 = Map_obj51_014E = 1x1 small orb 1 (8x8 pixels)
    // Frame 19 = Map_obj51_0158 = 1x1 small orb 2 (8x8 pixels)
    private static final int FRAME_SPIKED_BALL = 17;
    private static final int FRAME_ORB_1 = 18;
    private static final int FRAME_ORB_2 = 19;

    private final LevelManager levelManager;
    private final Sonic2CNZBossInstance mainBoss;

    // Position
    private int x;
    private int y;
    private int xFixed;
    private int yFixed;
    private int xVel;
    private int yVel;

    // State
    private int routineState;
    private int ballRiseOffset;
    private boolean exploding;
    private int renderFlags;
    private int lastFrameCounter;

    /**
     * Create electric ball attached to boss.
     * ROM: loc_31F48 - Init sets position to parent.y + 0x30, then advances to attach.
     */
    public CNZBossElectricBall(ObjectSpawn spawn, LevelManager levelManager, Sonic2CNZBossInstance mainBoss) {
        super(spawn, "CNZ Boss Ball");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;

        // ROM: loc_31F48 - position = parent (x, y+0x30) during init
        // Then immediately advances to attach routine where objoff_28 starts at 0
        // So ball position becomes parent.y + 0 on first frame of attach
        // This matches the ROM behavior where the ball "appears" at parent position
        this.x = mainBoss.getX();
        this.y = mainBoss.getY();  // Will be adjusted by ballRiseOffset in attach
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.xVel = 0;
        this.yVel = 0;

        this.routineState = BALL_ATTACH;
        this.ballRiseOffset = 0;  // ROM: objoff_28 starts at 0
        this.exploding = false;
        this.renderFlags = 0;
    }

    /**
     * Create split ball (called internally after floor hit).
     */
    private CNZBossElectricBall(int x, int y, int xVel, int yVel, LevelManager levelManager, Sonic2CNZBossInstance mainBoss) {
        super(new ObjectSpawn(x, y, Sonic2ObjectIds.CNZ_BOSS, 4, 0, false, 0), "CNZ Boss Ball");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;
        this.x = x;
        this.y = y;
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.xVel = xVel;
        this.yVel = yVel;
        this.routineState = BALL_SPLIT;
        this.exploding = true;
        this.renderFlags = 0;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }
        lastFrameCounter = frameCounter;

        // Check if parent boss is defeated - delete ball
        if (mainBoss.isInDefeatSequence()) {
            setDestroyed(true);
            return;
        }

        switch (routineState) {
            case BALL_ATTACH -> updateBallAttach();
            case BALL_FALL -> updateBallFall();
            case BALL_SPLIT -> updateBallSplit();
        }
    }

    /**
     * ROM: loc_31F96 - Ball attached to boss, lowering.
     * objoff_28 starts at 0, increments by 1 per frame, caps at $2E.
     * Position = parent.y + objoff_28
     */
    private void updateBallAttach() {
        x = mainBoss.getX();
        y = mainBoss.getY() + ballRiseOffset;

        // ROM: addi_.w #1,d0 / cmpi.w #$2E,d0 / blt.s + / move.w #$2E,d0
        ballRiseOffset++;
        if (ballRiseOffset >= 0x2E) {
            ballRiseOffset = 0x2E;
        }

        // Sync fixed-point position for when we transition to fall
        xFixed = x << 16;
        yFixed = y << 16;

        // ROM: tst.w (Boss_Countdown).w / bne.w DisplaySprite
        // Wait for main boss countdown to reach 0
        if (mainBoss.getBossCountdown() <= 0) {
            routineState = BALL_FALL;
            xVel = 0;
            yVel = 0;
        }
    }

    /**
     * ROM: loc_31FDC - Ball falling.
     * Uses ObjCheckFloorDist, triggers split when d1 (distance) is negative or zero.
     */
    private void updateBallFall() {
        applyBallPhysics();

        // ROM: jsr (ObjCheckFloorDist).l / tst.w d1 / bpl.w DisplaySprite
        // Triggers when d1 <= 0 (at or below floor)
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, Y_RADIUS);
        if (floor.hasCollision() && floor.distance() <= 0) {
            y += floor.distance();
            yFixed = y << 16;
            explodeAndSplit();
        }
    }

    /**
     * ROM: loc_32080 - Ball split, falling off.
     */
    private void updateBallSplit() {
        applyBallPhysics();

        // Delete when below floor
        if (y >= DELETE_Y) {
            setDestroyed(true);
        }
    }

    /**
     * Apply physics to ball (ROM: loc_31FF8).
     */
    private void applyBallPhysics() {
        xFixed += (xVel << 8);
        yFixed += (yVel << 8);
        yVel += GRAVITY;
        x = xFixed >> 16;
        y = yFixed >> 16;
    }

    /**
     * ROM: loc_32030 - Explode and split into two pieces.
     */
    private void explodeAndSplit() {
        AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_BOSS_EXPLOSION);
        exploding = true;
        yVel = -0x300;
        xVel = -0x100;
        routineState = BALL_SPLIT;

        // Spawn clone with opposite X velocity
        spawnBallClone();
    }

    private void spawnBallClone() {
        if (levelManager.getObjectManager() == null) {
            return;
        }
        CNZBossElectricBall clone = new CNZBossElectricBall(x, y, 0x100, -0x300, levelManager, mainBoss);
        levelManager.getObjectManager().addDynamicObject(clone);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = levelManager != null
                ? levelManager.getObjectRenderManager() : null;
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.CNZ_BOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean flipped = (renderFlags & 1) != 0;
        int frame = getBallMappingFrame();
        if (frame >= 0) {
            renderer.drawFrameIndex(frame, x, y, flipped, false);
        }
    }

    /**
     * Get ball mapping frame based on animation state.
     * ROM animation 7 (byte_320DD): delay=3, frames cycle between orb 1 and orb 2.
     */
    private int getBallMappingFrame() {
        if (exploding) {
            // Cycle between FRAME_ORB_1 (18) and FRAME_ORB_2 (19) every 4 frames
            return FRAME_ORB_1 + ((lastFrameCounter >> 2) & 1);
        }
        // During attach/fall phases, show the spiked ball
        return FRAME_SPIKED_BALL;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        return 7;
    }

    @Override
    protected boolean isOnScreen() {
        Camera camera = Camera.getInstance();
        int screenX = x - camera.getX();
        int screenY = y - camera.getY();
        return screenX >= -64 && screenX <= camera.getWidth() + 64
                && screenY >= -64 && screenY <= camera.getHeight() + 64;
    }

    /**
     * Get collision flags for hazard detection.
     * ROM: collision_flags = $98 (harmful, category HAZARD, size 0x18)
     */
    @Override
    public int getCollisionFlags() {
        if (isDestroyed()) {
            return 0;
        }
        // ROM: collision_flags = $98 (harmful)
        return 0x98;
    }

    /**
     * Get collision property (used for enemy bounce/hurt behavior).
     */
    @Override
    public int getCollisionProperty() {
        return 0;
    }
}
