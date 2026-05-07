package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $9D - Mantis (MGZ Act 2).
 *
 * <p>ROM reference: {@code Obj_Mantis} (sonic3k.asm:185695-185840).
 * The enemy waits on-screen, turns to face the nearest player, and if the
 * player gets within 64 pixels horizontally it runs a short prep animation,
 * leaps upward, then plays a return animation before resuming idle.
 *
 * <p>Collision: {@code $1A} (ENEMY, size $1A). Art is loaded from
 * {@code ArtKosM_Mantis} / {@code Map_Mantis}.
 */
public final class MantisBadnikInstance extends AbstractS3kBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x1A;
    private static final int PRIORITY_BUCKET = 5;
    private static final int DETECT_RANGE = 0x40;
    private static final int LAUNCH_Y_VELOCITY = -0x600;
    private static final int GRAVITY = 0x38;
    private static final int FLOOR_Y_RADIUS = 0x29;

    private static final int[] PREP_FRAMES = {0, 1, 2};
    private static final int[] PREP_DELAYS = {0, 2, 0};
    private static final int[] PREP_Y_OFFSETS = {0, -5, -0x13};

    private static final int[] LAND_FRAMES = {2, 1, 3, 0};
    private static final int[] LAND_DELAYS = {0, 2, 2, 0x1F};
    private static final int[] LAND_Y_OFFSETS = {0, 0x12, 6, -1};

    private static final int WAIT_FRAME = 0;
    private static final int CHILD_IDLE_FRAME = 4;
    private static final int CHILD_RISING_FRAME = 5;

    private enum State {
        WAIT,
        PREPARE,
        LAUNCH,
        LAND
    }

    private State state = State.WAIT;
    private boolean initialized;
    private int animIndex = -1;
    private int animTimer;
    private int restY;
    private MantisChild child;

    public MantisBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Mantis",
                Sonic3kObjectArtKeys.MGZ_MANTIS, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = WAIT_FRAME;
        this.restY = spawn.y();
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // ROM entry / delete flow only gates Mantis on X visibility, so the
        // jump arc must continue even if it leaves the top of the viewport.
        if (destroyed || !isOnScreenX()) {
            return;
        }

        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite
                ? sprite : null;

        if (!initialized) {
            initialize();
            initialized = true;
            updateDynamicSpawn(currentX, currentY);
            return;
        }

        switch (state) {
            case WAIT -> updateWait(player);
            case PREPARE -> updatePrep();
            case LAUNCH -> updateLaunch();
            case LAND -> updateLand();
        }

        updateDynamicSpawn(currentX, currentY);
    }

    @Override
    public void onUnload() {
        destroyChild(child);
        child = null;
    }

    private void initialize() {
        child = spawnChild(() -> new MantisChild(this));
        mappingFrame = WAIT_FRAME;
        animIndex = -1;
        animTimer = 0;
        currentY = restY;
        yVelocity = 0;
    }

    private void updateWait(AbstractPlayableSprite player) {
        AbstractPlayableSprite target = findNearestTarget(player);
        if (target != null) {
            facingLeft = target.getCentreX() < currentX;
            if (Math.abs(target.getCentreX() - currentX) < DETECT_RANGE) {
                beginPrep();
            }
        }
    }

    private AbstractPlayableSprite findNearestTarget(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite nearest = null;
        int nearestDistance = Integer.MAX_VALUE;

        if (mainPlayer != null && !mainPlayer.getDead()) {
            nearest = mainPlayer;
            nearestDistance = Math.abs(mainPlayer.getCentreX() - currentX);
        }

        ObjectServices svc = tryServices();
        if (svc == null) {
            return nearest;
        }

        for (PlayableEntity sidekickEntity : svc.sidekicks()) {
            if (!(sidekickEntity instanceof AbstractPlayableSprite sidekick) || sidekick.getDead()) {
                continue;
            }
            int distance = Math.abs(sidekick.getCentreX() - currentX);
            if (distance < nearestDistance) {
                nearest = sidekick;
                nearestDistance = distance;
            }
            break;
        }

        return nearest;
    }

    private void updatePrep() {
        if (advanceScript(PREP_FRAMES, PREP_DELAYS, PREP_Y_OFFSETS, this::beginLaunch)) {
            updateDynamicSpawn(currentX, currentY);
        }
    }

    private void updateLaunch() {
        moveWithVelocity();
        yVelocity += GRAVITY;
        if (yVelocity < 0) {
            updateDynamicSpawn(currentX, currentY);
            return;
        }
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, FLOOR_Y_RADIUS);
        if (floor.hasCollision()) {
            currentY += floor.distance();
            restY = currentY;
            ySubpixel = 0;
            beginLand();
        }
        updateDynamicSpawn(currentX, currentY);
    }

    private void updateLand() {
        if (advanceScript(LAND_FRAMES, LAND_DELAYS, LAND_Y_OFFSETS, this::finishCycle)) {
            updateDynamicSpawn(currentX, currentY);
        }
    }

    private void beginPrep() {
        state = State.PREPARE;
        // Animate_RawNoSSTMultiDelay starts with frame 0 already visible and
        // advances to entry 1 on the first timer underflow.
        animIndex = 0;
        animTimer = 0;
        mappingFrame = PREP_FRAMES[0];
    }

    private void beginLaunch() {
        state = State.LAUNCH;
        yVelocity = LAUNCH_Y_VELOCITY;
    }

    private void beginLand() {
        state = State.LAND;
        yVelocity = 0;
        // Same raw animation semantics as the prep script: frame 0 is the
        // already-displayed landing pose, so the first tick advances to entry 1.
        animIndex = 0;
        animTimer = 0;
        mappingFrame = LAND_FRAMES[0];
    }

    private void finishCycle() {
        state = State.WAIT;
        currentY = restY;
        yVelocity = 0;
        ySubpixel = 0;
        animIndex = -1;
        animTimer = 0;
        mappingFrame = WAIT_FRAME;
    }

    private boolean advanceScript(int[] frames, int[] delays, int[] yOffsets, Runnable onComplete) {
        animTimer--;
        if (animTimer >= 0) {
            return false;
        }

        animIndex++;
        if (animIndex >= frames.length) {
            if (onComplete != null) {
                onComplete.run();
            }
            return false;
        }

        mappingFrame = frames[animIndex];
        animTimer = delays[animIndex];
        currentY += yOffsets[animIndex];
        return true;
    }

    private static void destroyChild(AbstractObjectInstance child) {
        if (child != null) {
            child.setDestroyed(true);
        }
    }

    private static final class MantisChild extends AbstractObjectInstance {
        private final MantisBadnikInstance parent;
        private int currentX;
        private int currentY;
        private int mappingFrame = CHILD_IDLE_FRAME;

        private MantisChild(MantisBadnikInstance parent) {
            super(parent.spawn, "MantisChild");
            this.parent = parent;
            syncFromParent();
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (parent.destroyed) {
                setDestroyed(true);
                return;
            }
            syncFromParent();
        }

        private void syncFromParent() {
            currentX = parent.currentX + (parent.facingLeft ? -9 : 9);
            currentY = parent.currentY - 0x0B;
            mappingFrame = parent.yVelocity < 0 ? CHILD_RISING_FRAME : CHILD_IDLE_FRAME;
            updateDynamicSpawn(currentX, currentY);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (parent.destroyed) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_MANTIS);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, !parent.facingLeft, false);
        }
    }
}
