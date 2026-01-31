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
public class CNZBossElectricBall extends AbstractObjectInstance {

    // Routine states
    private static final int BALL_ATTACH = 0;
    private static final int BALL_FALL = 1;
    private static final int BALL_SPLIT = 2;

    // Physics constants
    private static final int GRAVITY = 0x38;
    private static final int Y_RADIUS = 8;
    private static final int DELETE_Y = 0x705;

    // Animation constants
    private static final int FRAME_NORMAL = 0x13;
    private static final int FRAME_EXPLODE_1 = 0x13;
    private static final int FRAME_EXPLODE_2 = 0x14;

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
     */
    public CNZBossElectricBall(ObjectSpawn spawn, LevelManager levelManager, Sonic2CNZBossInstance mainBoss) {
        super(spawn, "CNZ Boss Ball");
        this.levelManager = levelManager;
        this.mainBoss = mainBoss;

        // ROM: position = parent (x, y+0x30)
        this.x = mainBoss.getX();
        this.y = mainBoss.getY() + 0x30;
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.xVel = 0;
        this.yVel = 0;

        this.routineState = BALL_ATTACH;
        this.ballRiseOffset = 0;
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
     */
    private void updateBallAttach() {
        x = mainBoss.getX();
        y = mainBoss.getY() + ballRiseOffset;

        ballRiseOffset++;
        if (ballRiseOffset > 0x2E) {
            ballRiseOffset = 0x2E;
        }

        // Wait for main boss countdown to reach 0
        if (mainBoss.getBossCountdown() <= 0) {
            routineState = BALL_FALL;
            xVel = 0;
            yVel = 0;
        }
    }

    /**
     * ROM: loc_31FDC - Ball falling.
     */
    private void updateBallFall() {
        applyBallPhysics();

        // Floor collision check
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(x, y, Y_RADIUS);
        if (floor.hasCollision() && floor.distance() < 0) {
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
     */
    private int getBallMappingFrame() {
        if (exploding) {
            return FRAME_EXPLODE_1 + ((lastFrameCounter >> 2) & 1);
        }
        return FRAME_NORMAL;
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
     */
    public int getCollisionFlags() {
        // ROM: collision_flags = $98 (harmful)
        return 0x98;
    }
}
