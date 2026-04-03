package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PowerUpObject;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.Direction;

import java.util.List;

/**
 * S3K Invincibility Stars -- trailing position-history stars with orbital sub-sprites.
 * <p>
 * ROM reference: Obj_Invincibility (sonic3k.asm:33751).
 * <p>
 * Structure: 1 parent group (at player position) + 4 child groups (trailing via position
 * history at 0/3/6/9 frames behind). Each group renders 2 sub-sprites at opposite orbit
 * positions using a 32-entry circular offset table.
 * <p>
 * Parent rotates at 9 entries/frame (ROM: $12 byte offset in 2-byte table).
 * Children rotate at 1 entry/frame (ROM: $02 byte offset).
 * Rotation direction reverses when the player faces left.
 */
public class Sonic3kInvincibilityStarsObjectInstance extends AbstractObjectInstance implements PowerUpObject {

    private final PlayableEntity player;
    private final PatternSpriteRenderer renderer;

    /** Orbit offset table (byte_189A0): 32 signed X,Y pairs forming ~16px radius circle. */
    static final int[][] S3K_ORBIT_OFFSETS = {
            { 15,   0}, { 15,   3}, { 14,   6}, { 13,   8},
            { 11,  11}, {  8,  13}, {  6,  14}, {  3,  15},
            {  0,  16}, { -4,  15}, { -7,  14}, { -9,  13},
            {-12,  11}, {-14,   8}, {-15,   6}, {-16,   3},
            {-16,   0}, {-16,  -4}, {-15,  -7}, {-14,  -9},
            {-12, -12}, { -9, -14}, { -7, -15}, { -4, -16},
            { -1, -16}, {  3, -16}, {  6, -15}, {  8, -14},
            { 11, -12}, { 13,  -9}, { 14,  -7}, { 15,  -4}
    };

    private static final int ORBIT_TABLE_SIZE = S3K_ORBIT_OFFSETS.length;
    private static final int SUB_SPRITE_PHASE = 16;

    /** Parent animation table (byte_189E0). */
    static final int[] PARENT_ANIM = {8, 5, 7, 6, 6, 7, 5, 8, 6, 7, 7, 6};

    /** Child primary animation tables. Sub-sprite A uses primary. */
    static final int[][] CHILD_PRIMARY_ANIMS = {
            {8, 7, 6, 5, 4, 3, 4, 5, 6, 7},
            {8, 7, 6, 5, 4, 3, 2, 3, 4, 5, 6, 7},
            {7, 6, 5, 4, 3, 2, 1, 2, 3, 4, 5, 6},
            {8, 5, 7, 6, 6, 7, 5, 8, 6, 7, 7, 6}
    };

    /** Child secondary animation tables. Sub-sprite B uses secondary. */
    static final int[][] CHILD_SECONDARY_ANIMS = {
            {3, 4, 5, 6, 7, 8, 7, 6, 5, 4},
            {2, 3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3},
            {1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2},
            {6, 7, 7, 6, 8, 5, 7, 6, 6, 7, 5, 8}
    };

    private static final int[] CHILD_INIT_ANGLES = {0, 0, 11, 22};
    private static final int CHILD_COUNT = 4;
    private static final int PARENT_ROTATION_SPEED = 9;
    private static final int CHILD_ROTATION_SPEED = 1;

    private int parentAngle = 4;
    private int parentAnimIndex = 0;
    private final int[] childAngles = new int[CHILD_COUNT];
    private final int[] childAnimIndices = new int[CHILD_COUNT];

    public Sonic3kInvincibilityStarsObjectInstance(PlayableEntity player) {
        super(null, "S3kInvincibilityStars");
        this.player = player;

        ObjectRenderManager renderManager = getRenderManager();
        this.renderer = (renderManager != null)
                ? renderManager.getInvincibilityStarsRenderer()
                : null;

        for (int i = 0; i < CHILD_COUNT; i++) {
            childAngles[i] = CHILD_INIT_ANGLES[i] % ORBIT_TABLE_SIZE;
        }
    }

    public static int trailingFramesBehind(int starIndex) {
        return starIndex * 3;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        boolean facingLeft = player.getDirection() == Direction.LEFT;
        int dirSign = facingLeft ? -1 : 1;

        parentAngle = wrapAngle(parentAngle + dirSign * PARENT_ROTATION_SPEED);
        parentAnimIndex = (parentAnimIndex + 1) % PARENT_ANIM.length;

        for (int i = 0; i < CHILD_COUNT; i++) {
            childAngles[i] = wrapAngle(childAngles[i] + dirSign * CHILD_ROTATION_SPEED);
            childAnimIndices[i] = (childAnimIndices[i] + 1) % CHILD_PRIMARY_ANIMS[i].length;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (renderer == null || player == null) {
            return;
        }

        drawStarGroup(player.getCentreX(), player.getCentreY(),
                parentAngle, PARENT_ANIM[parentAnimIndex], PARENT_ANIM[parentAnimIndex]);

        for (int i = 0; i < CHILD_COUNT; i++) {
            int framesBehind = trailingFramesBehind(i);
            int cx = player.getCentreX(framesBehind);
            int cy = player.getCentreY(framesBehind);

            int primaryFrame = CHILD_PRIMARY_ANIMS[i][childAnimIndices[i]];
            int secondaryFrame = CHILD_SECONDARY_ANIMS[i][childAnimIndices[i]];

            drawStarGroup(cx, cy, childAngles[i], primaryFrame, secondaryFrame);
        }
    }

    private void drawStarGroup(int centerX, int centerY, int angle,
                               int frameA, int frameB) {
        int angleA = angle % ORBIT_TABLE_SIZE;
        int[] offsetA = S3K_ORBIT_OFFSETS[angleA];
        renderer.drawFrameIndex(frameA, centerX + offsetA[0], centerY + offsetA[1], false, false);

        int angleB = (angle + SUB_SPRITE_PHASE) % ORBIT_TABLE_SIZE;
        int[] offsetB = S3K_ORBIT_OFFSETS[angleB];
        renderer.drawFrameIndex(frameB, centerX + offsetB[0], centerY + offsetB[1], false, false);
    }

    private static int wrapAngle(int angle) {
        return ((angle % ORBIT_TABLE_SIZE) + ORBIT_TABLE_SIZE) % ORBIT_TABLE_SIZE;
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(1);
    }

    @Override
    public void destroy() {
        setDestroyed(true);
    }

    @Override
    public void setVisible(boolean visible) {
        // Stars are always visible while alive; no-op.
    }
}
