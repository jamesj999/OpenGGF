package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.Sprite;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Rexon (0x94/0x96) - Lava snake badnik from HTZ.
 * This is the body/platform that patrols and spawns head segments.
 * Based on disassembly Obj94.
 *
 * Behavior:
 * - Patrols left/right with -0x20 velocity
 * - Checks for player in angular range
 * - When player detected, spawns 5 head segments that rise up
 * - Body stays stationary as anchor after spawning heads
 */
public class RexonBadnikInstance extends AbstractBadnikInstance {
    // Collision size from Obj94_SubObjData (s2.asm:74061)
    // Body has collision 0, not 0x0B - heads have their own collision
    private static final int COLLISION_SIZE_INDEX = 0x00;

    // Movement constants from disassembly
    private static final int X_VELOCITY = -0x20;  // Patrol velocity (8.8 fixed)
    private static final int PATROL_TIMER = 128;  // Frames before reversing direction

    // Detection constants
    private static final int DETECT_ANGLE_OFFSET = 0x60;  // Added to angle before comparison
    private static final int DETECT_ANGLE_RANGE = 0x100;  // If adjusted angle < this, player detected

    private enum State {
        WAIT_FOR_PLAYER,    // Routine 2 - Patrol and detect
        READY_TO_CREATE,    // Routine 4 - About to spawn heads
        POST_CREATE_HEAD    // Routine 6 - Heads spawned, act as platform
    }

    private State state;
    private int patrolTimer;
    private int xSubpixel;
    private boolean xFlipFlag;
    private final List<RexonHeadObjectInstance> heads = new ArrayList<>();

    public RexonBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Rexon");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xVelocity = X_VELOCITY;
        this.patrolTimer = PATROL_TIMER;
        this.xSubpixel = 0;
        this.state = State.WAIT_FOR_PLAYER;

        // Initial flip from spawn
        this.xFlipFlag = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlipFlag;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case WAIT_FOR_PLAYER -> updatePatrol(player);
            case READY_TO_CREATE -> createHeads();
            case POST_CREATE_HEAD -> updateAsAnchor();
        }
    }

    private void updatePatrol(AbstractPlayableSprite player) {
        // Check if player is in detection range
        if (checkPlayerInRange(player)) {
            state = State.READY_TO_CREATE;
            return;
        }

        // Patrol movement
        patrolTimer--;
        if (patrolTimer < 0) {
            // Reverse direction
            xVelocity = -xVelocity;
            xFlipFlag = !xFlipFlag;
            facingLeft = !facingLeft;
            patrolTimer = PATROL_TIMER;
        }

        // Apply velocity (8.8 fixed point)
        int xPos32 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos32 += xVelocity;
        currentX = xPos32 >> 8;
        xSubpixel = xPos32 & 0xFF;
    }

    /**
     * Check if player is within angular detection range using Obj_GetOrientationToPlayer logic.
     * The original code calculates angle to player, adds 0x60, and checks if < 0x100.
     */
    private boolean checkPlayerInRange(AbstractPlayableSprite player) {
        if (player == null || !isOnScreen()) {
            return false;
        }

        int dx = player.getCentreX() - currentX;
        int dy = player.getCentreY() - currentY;

        // Calculate angle (0-255 range, 0 = right, 64 = down, 128 = left, 192 = up)
        int angle = calculateAngle(dx, dy);

        // Apply flip adjustment if facing left
        if (xFlipFlag) {
            angle = (256 - angle) & 0xFF;
        }

        // Add offset and check range
        int adjusted = (angle + DETECT_ANGLE_OFFSET) & 0xFF;
        return adjusted < (DETECT_ANGLE_RANGE & 0xFF);
    }

    /**
     * Calculate angle from delta x/y using atan2 approximation.
     * Returns 0-255 where 0 = right, 64 = down, 128 = left, 192 = up.
     */
    private int calculateAngle(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return 0;
        }
        double radians = Math.atan2(dy, dx);
        int degrees256 = (int) Math.round(radians * 128.0 / Math.PI);
        return degrees256 & 0xFF;
    }

    private void createHeads() {
        // Determine flip direction based on player position
        AbstractPlayableSprite player = getPlayer();
        if (player != null) {
            boolean playerLeft = player.getCentreX() < currentX;
            xFlipFlag = !playerLeft;
            facingLeft = playerLeft;
        }

        // Spawn 5 head segments with indices 0, 2, 4, 6, 8
        for (int i = 0; i < 5; i++) {
            int headIndex = i * 2;  // 0, 2, 4, 6, 8
            RexonHeadObjectInstance head = new RexonHeadObjectInstance(
                    spawn,
                    levelManager,
                    this,
                    currentX,
                    currentY,
                    headIndex,
                    xFlipFlag
            );
            heads.add(head);
            levelManager.getObjectManager().addDynamicObject(head);
        }

        state = State.POST_CREATE_HEAD;
    }

    private void updateAsAnchor() {
        // Body stays still, acts as platform for heads
        // Check if all heads are destroyed
        boolean allDestroyed = true;
        for (RexonHeadObjectInstance head : heads) {
            if (!head.isDestroyed()) {
                allDestroyed = false;
                break;
            }
        }

        // If all heads destroyed, body can be destroyed too
        // (In original, body stays but this keeps behavior simple)
    }

    private AbstractPlayableSprite getPlayer() {
        SpriteManager spriteManager = SpriteManager.getInstance();
        if (spriteManager == null) {
            return null;
        }
        for (Sprite sprite : spriteManager.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite) {
                return (AbstractPlayableSprite) sprite;
            }
        }
        return null;
    }

    /**
     * Called by head segments when they are destroyed.
     * Triggers "death drop" for remaining heads.
     */
    public void onHeadDestroyed(RexonHeadObjectInstance destroyedHead) {
        for (RexonHeadObjectInstance head : heads) {
            if (head != destroyedHead && !head.isDestroyed()) {
                head.triggerDeathDrop();
            }
        }
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // Body uses frame 0, no animation
        animFrame = 0;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.REXON);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Body uses frame 0
        renderer.drawFrameIndex(0, currentX, currentY, xFlipFlag, false);
    }
}
