package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.playable.AbstractPlayableSprite;

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
public class RexonBadnikInstance extends AbstractBadnikInstance
        implements SolidObjectProvider, SolidObjectListener {
    // Collision size from Obj94_SubObjData (s2.asm:74061)
    // Body has collision 0, not 0x0B - heads have their own collision
    private static final int COLLISION_SIZE_INDEX = 0x00;

    // Solid collision dimensions from s2.asm:73743-73748 (Obj94_SolidCollision)
    private static final int SOLID_HALF_WIDTH = 0x1B;      // 27 pixels
    private static final int SOLID_AIR_HALF_HEIGHT = 8;    // 8 pixels when jumping
    private static final int SOLID_GROUND_HALF_HEIGHT = 8; // 8 pixels when walking

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
    private final SubpixelMotion.State motionState;
    private boolean xFlipFlag;
    private final List<RexonHeadObjectInstance> heads = new ArrayList<>();

    public RexonBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Rexon", Sonic2BadnikConfig.DESTRUCTION);
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.xVelocity = X_VELOCITY;
        this.patrolTimer = PATROL_TIMER;
        this.motionState = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
        this.state = State.WAIT_FOR_PLAYER;

        // Initial flip from spawn
        this.xFlipFlag = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlipFlag;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
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
        motionState.x = currentX;
        motionState.xVel = xVelocity;
        SubpixelMotion.moveX(motionState);
        currentX = motionState.x;
    }

    /**
     * Check if player is within detection range using Obj_GetOrientationToPlayer logic.
     *
     * The original code (s2.asm:72295-72321) does NOT calculate a full angle - it returns
     * a simple 2-bit quadrant value (0, 2, 4, or 6):
     * - d0 = 0 if player is RIGHT, d0 = 2 if player is LEFT
     * - d1 = 0 if object is ABOVE/SAME, d1 = 2 if object is BELOW
     * - Combined result is 0, 2, 4, or 6
     *
     * With quadrant values 0, 2, 4, or 6 plus 0x60 offset = 0x60, 0x62, 0x64, or 0x66.
     * All are < 0x100, so heads ALWAYS spawn when body is on screen.
     */
    private boolean checkPlayerInRange(AbstractPlayableSprite player) {
        if (player == null || !isOnScreen()) {
            return false;
        }

        int dx = currentX - player.getCentreX();  // Object X - Player X (like disasm)
        int dy = currentY - player.getCentreY();  // Object Y - Player Y

        // Quadrant logic from Obj_GetOrientationToPlayer (s2.asm:72295-72321)
        // Returns 0, 2, 4, or 6 based on player position relative to object
        int orientation = 0;
        if (dx < 0) orientation += 2;  // Player is to the right (object X < player X)
        if (dy < 0) orientation += 2;  // Player is above (object Y < player Y)

        // Add offset and check (16-bit compare, no mask)
        int adjusted = orientation + DETECT_ANGLE_OFFSET;
        return adjusted < DETECT_ANGLE_RANGE;
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
                    this,
                    currentX,
                    currentY,
                    headIndex,
                    xFlipFlag
            );
            heads.add(head);
            services().objectManager().addDynamicObject(head);
        }

        // Set up head chain linking (s2.asm:73786-73795)
        // In original: each head's objoff_30 points to the NEXT head toward the tip
        //
        // Body creates heads in order: index 0, 2, 4, 6, 8
        // Body stores addresses at: objoff_2C+0, +2, +4, +6, +8
        // Each head reads from body[0x2E + headIndex] to get its link:
        //   - Head 0 (index 0): reads body[0x2E] = head 1's address
        //   - Head 1 (index 2): reads body[0x30] = head 2's address
        //   - Head 2 (index 4): reads body[0x32] = head 3's address
        //   - Head 3 (index 6): reads body[0x34] = head 4's address
        //   - Head 4 (index 8): no link (skipped in init due to cmpi.w #8,d0)
        //
        // During oscillation, each head moves its LINKED head:
        // - Head 0 (anchor, not moved by anyone) moves head 1
        // - Head 1 (moved by head 0) moves head 2
        // - Head 2 (moved by head 1) moves head 3
        // - Head 3 (moved by head 2) moves head 4
        // - Head 4 (tip, moved by head 3) has no link
        //
        // The oscillation ripples OUTWARD from body to tip.
        // Head 0 stays at its base position (the anchor point).
        for (int i = 0; i < heads.size() - 1; i++) {
            heads.get(i).setLinkedHead(heads.get(i + 1));
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
        var spriteManager = services().spriteManager();
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
        // Body uses frame 2 (s2.asm:73691: move.b #2,mapping_frame(a0))
        animFrame = 2;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(SOLID_HALF_WIDTH, SOLID_AIR_HALF_HEIGHT, SOLID_GROUND_HALF_HEIGHT);
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // Standard solid collision - no special behavior needed
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.REXON);
        if (renderer == null) return;

        // Body uses frame 2 (s2.asm:73691)
        renderer.drawFrameIndex(2, currentX, currentY, xFlipFlag, false);
    }
}
