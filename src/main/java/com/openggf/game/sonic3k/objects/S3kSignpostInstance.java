package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * S3K end-of-act signpost (falls from sky after miniboss defeat).
 *
 * <p>ROM: Obj_Signpost (sonic3k.asm) — falling sign with spin animation,
 * bump-from-below mechanic, and hidden monitor interaction.
 *
 * <p>State machine: INIT -> FALLING -> LANDED -> RESULTS -> AFTER
 */
public class S3kSignpostInstance extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(S3kSignpostInstance.class.getName());

    // ---- Static reference for hidden monitors ----
    private static S3kSignpostInstance activeSignpost;

    public static S3kSignpostInstance getActiveSignpost() {
        return activeSignpost;
    }

    // ---- State machine ----
    private enum State { INIT, FALLING, LANDED, RESULTS, AFTER }

    private State state = State.INIT;

    // ---- Physics (pixel-level velocities, fixed-point 8.8 where noted) ----
    private int xVel;
    private int yVel;
    private int worldX;
    private int worldY;

    /** Subpixel accumulators for fractional movement (lower 8 bits). */
    private int subX;
    private int subY;

    // ---- Signpost flags ----
    private boolean landed;
    private int postLandTimer;
    private int bumpCooldown;

    // ---- Animation ----
    private int animFrame;
    private int animIndex;
    private int animTimer;
    private int sparkleCounter;

    /**
     * ROM-accurate spin animation sequences.
     * Default (Sonic/Tails): Eggman -> spin -> Tails -> spin -> face -> spin, loop.
     * Knuckles: Tails -> spin -> Knux -> spin -> face -> spin, loop.
     */
    private static final int[] ANIM_SONIC = {0, 4, 5, 6, 1, 4, 5, 6, 3, 4, 5, 6};
    private static final int[] ANIM_KNUCKLES = {1, 4, 5, 6, 2, 4, 5, 6, 3, 4, 5, 6};

    /**
     * Face frame lookup indexed by PlayerCharacter ordinal.
     * 0=SONIC_AND_TAILS -> Sonic face (0), 1=SONIC_ALONE -> Sonic face (0),
     * 2=TAILS_ALONE -> Tails face (1), 3=KNUCKLES -> Knuckles face (2).
     */
    private static final int[] FACE_FRAMES = {0, 0, 1, 2};

    private static final int GRAVITY = 0x0C;
    private static final int Y_RADIUS = 0x1E;
    private static final int ANIM_FRAME_DELAY = 2;
    private static final int SPARKLE_INTERVAL = 4;
    private static final int POST_LAND_TIMER = 0x40;
    private static final int BUMP_COOLDOWN = 0x20;

    // Bump detection box relative to signpost center
    private static final int BUMP_LEFT = -0x20;
    private static final int BUMP_RIGHT = 0x40;
    private static final int BUMP_TOP = -0x18;
    private static final int BUMP_BOTTOM = 0x30;

    // Wall bounce margins relative to camera
    private static final int WALL_RIGHT_MARGIN = 0x128;
    private static final int WALL_LEFT_MARGIN = 0x18;

    // Landing Y threshold relative to camera
    private static final int LAND_Y_THRESHOLD = 0x50;

    private int[] animSequence;

    /**
     * Creates the signpost at the given X position.
     * Y is set to above the camera in INIT state.
     *
     * @param spawnX world X position for the signpost
     */
    public S3kSignpostInstance(int spawnX) {
        super(null, "S3kSignpost");
        this.worldX = spawnX;
        this.worldY = 0; // Set properly in INIT
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }

    public boolean isLanded() {
        return landed;
    }

    public void setLanded(boolean landed) {
        this.landed = landed;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        switch (state) {
            case INIT -> updateInit(player);
            case FALLING -> updateFalling(player);
            case LANDED -> updateLanded();
            case RESULTS -> updateResults(player);
            case AFTER -> updateAfter();
        }
    }

    // =========================================================================
    // INIT
    // =========================================================================

    private void updateInit(AbstractPlayableSprite player) {
        activeSignpost = this;

        Camera camera = Camera.getInstance();
        worldY = camera.getY() - 0x20;

        // Select animation based on player character
        PlayerCharacter pc = getPlayerCharacter();
        animSequence = (pc == PlayerCharacter.KNUCKLES) ? ANIM_KNUCKLES : ANIM_SONIC;
        animIndex = 0;
        animFrame = animSequence[0];
        animTimer = 0;
        sparkleCounter = 0;

        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.SIGNPOST.id);
        } catch (Exception e) {
            LOG.fine("Could not play signpost SFX: " + e.getMessage());
        }

        // Spawn the stub/post child
        spawnDynamicObject(new S3kSignpostStubChild(this));

        state = State.FALLING;
        LOG.fine("S3K Signpost INIT -> FALLING at X=" + worldX + " Y=" + worldY);
    }

    // =========================================================================
    // FALLING
    // =========================================================================

    private void updateFalling(AbstractPlayableSprite player) {
        // Apply gravity
        yVel += GRAVITY;

        // Move (8.8 fixed-point accumulation)
        subX += xVel;
        worldX += subX >> 8;
        subX &= 0xFF;

        subY += yVel;
        worldY += subY >> 8;
        subY &= 0xFF;

        // Decrement bump cooldown
        if (bumpCooldown > 0) {
            bumpCooldown--;
        }

        // Sparkle effect
        sparkleCounter++;
        if (sparkleCounter >= SPARKLE_INTERVAL) {
            sparkleCounter = 0;
            spawnDynamicObject(new S3kSignpostSparkleChild(worldX, worldY));
        }

        // Check bump from below
        checkBumpFromBelow(player);

        // Wall bounce
        Camera camera = Camera.getInstance();
        int camX = camera.getX();
        if (worldX > camX + WALL_RIGHT_MARGIN) {
            xVel = -Math.abs(xVel);
        } else if (worldX < camX + WALL_LEFT_MARGIN) {
            xVel = Math.abs(xVel);
        }

        // Animate spin
        advanceAnimation();

        // Landing check — use terrain collision (ROM: ObjCheckFloorDist)
        // Only check when moving downward and past the minimum camera-relative Y
        if (yVel > 0 && worldY >= camera.getY() + LAND_Y_THRESHOLD) {
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(worldX, worldY, Y_RADIUS);
            if (floor.distance() < 0) {
                // Snap to floor surface
                worldY += floor.distance();
            } else {
                return; // No floor contact yet — keep falling
            }
            landed = true;
            postLandTimer = POST_LAND_TIMER;
            yVel = 0;
            xVel = 0;
            subX = 0;
            subY = 0;
            state = State.LANDED;
            LOG.fine("S3K Signpost FALLING -> LANDED at Y=" + worldY);
        }
    }

    /**
     * ROM: Signpost bump-from-below mechanic.
     * Player must be jumping (in air + rolling jump) and moving upward,
     * and within the bump detection box.
     */
    private void checkBumpFromBelow(AbstractPlayableSprite player) {
        if (player == null || bumpCooldown > 0) {
            return;
        }

        // Player must be in air and jumping (animation ID 2 = rolling/jumping)
        if (!player.getAir()) {
            return;
        }
        if (player.getYSpeed() >= 0) {
            return;
        }

        // Range check
        int dx = player.getCentreX() - worldX;
        int dy = player.getCentreY() - worldY;
        if (dx < BUMP_LEFT || dx >= BUMP_RIGHT || dy < BUMP_TOP || dy >= BUMP_BOTTOM) {
            return;
        }

        // Bump!
        int kickX = (worldX - player.getCentreX()) * 16;
        if (kickX == 0) {
            kickX = 8;
        }
        // xVel/yVel are 8.8 fixed-point
        xVel = kickX;
        yVel = -0x200;

        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.SIGNPOST.id);
        } catch (Exception e) {
            LOG.fine("Could not play signpost bump SFX: " + e.getMessage());
        }

        GameServices.gameState().addScore(100);
        bumpCooldown = BUMP_COOLDOWN;
        LOG.fine("S3K Signpost bumped! xVel=" + xVel);
    }

    // =========================================================================
    // LANDED
    // =========================================================================

    private void updateLanded() {
        // If a hidden monitor cleared our landed flag, bounce back up
        if (!landed) {
            yVel = -0x200;
            bumpCooldown = BUMP_COOLDOWN;
            state = State.FALLING;
            LOG.fine("S3K Signpost LANDED -> FALLING (hidden monitor bounce)");
            return;
        }

        // Continue spin animation during post-land timer
        advanceAnimation();

        postLandTimer--;
        if (postLandTimer <= 0) {
            // Show final face frame
            PlayerCharacter pc = getPlayerCharacter();
            animFrame = FACE_FRAMES[pc.ordinal()];
            xVel = 0;
            yVel = 0;
            state = State.RESULTS;
            LOG.fine("S3K Signpost LANDED -> RESULTS");
        }
    }

    // =========================================================================
    // RESULTS
    // =========================================================================

    private void updateResults(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Wait for player to be on the ground
        if (player.getAir()) {
            return;
        }

        // Spawn the results screen (handles score tally and act transition signal)
        spawnDynamicObject(new S3kResultsScreenObjectInstance(
                getPlayerCharacter(),
                LevelManager.getInstance().getCurrentAct()));
        LOG.fine("S3K Signpost RESULTS -> AFTER (results instance spawned)");
        state = State.AFTER;
    }

    // =========================================================================
    // AFTER
    // =========================================================================

    private void updateAfter() {
        if (!isOnScreen(64)) {
            setDestroyed(true);
            activeSignpost = null;
            LOG.fine("S3K Signpost destroyed (off-screen)");
        }
    }

    // =========================================================================
    // Animation
    // =========================================================================

    private void advanceAnimation() {
        animTimer++;
        if (animTimer >= ANIM_FRAME_DELAY) {
            animTimer = 0;
            animIndex++;
            if (animIndex >= animSequence.length) {
                animIndex = 0;
            }
            animFrame = animSequence[animIndex];
        }
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getEndSignRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(animFrame, worldX, worldY, false, false);
    }

    private PatternSpriteRenderer getEndSignRenderer() {
        try {
            LevelManager lm = LevelManager.getInstance();
            if (lm != null) {
                ObjectRenderManager orm = lm.getObjectRenderManager();
                if (orm != null) {
                    return orm.getRenderer(Sonic3kObjectArtKeys.END_SIGN);
                }
            }
        } catch (Exception ignored) {
            // Render manager unavailable (e.g. headless test)
        }
        return null;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PlayerCharacter getPlayerCharacter() {
        try {
            return Sonic3kLevelEventManager.getInstance().getPlayerCharacter();
        } catch (Exception e) {
            return PlayerCharacter.SONIC_AND_TAILS;
        }
    }

    public int getWorldX() {
        return worldX;
    }

    public int getWorldY() {
        return worldY;
    }
}
