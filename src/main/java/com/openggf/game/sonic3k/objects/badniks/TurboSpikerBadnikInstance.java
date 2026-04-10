package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $96 - TurboSpiker (HCZ).
 *
 * <p>Implements the HCZ crab badnik's full ROM state flow:
 * patrol toward the player, periodically pause-turn at subtype-defined
 * intervals, back away and launch its shell when the player approaches from the
 * facing side, and optionally hide behind a waterfall overlay on Y-flipped
 * placements before emerging in a burst of splash particles.
 *
 * <p>ROM reference: {@code Obj_TurboSpiker} (sonic3k.asm:183861-184226).
 */
public final class TurboSpikerBadnikInstance extends AbstractS3kBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x1A;      // ObjDat_TurboSpiker flags $1A
    private static final int PRIORITY_BUCKET_NORMAL = 5;       // ObjDat_TurboSpiker priority $280
    private static final int PRIORITY_BUCKET_WATERFALL = 3;    // ObjDat3_87EDA priority $180

    private static final int DETECT_RANGE = 0x60;
    private static final int INITIAL_TRACK_SPEED = 0x80;
    private static final int RETREAT_SPEED = 0x200;

    private static final int FLOOR_MIN_DIST = -1;
    private static final int FLOOR_MAX_DIST = 0x0C;
    private static final int Y_RADIUS = 0x0F;

    private static final int TURN_DELAY = 0x0F;
    private static final int WATERFALL_EMERGE_DELAY = 3;
    private static final int WATERFALL_PRIORITY_DELAY = 0x0F;

    private static final int WALK_ANIM_DELAY = 5;
    private static final int SHELLLESS_ANIM_DELAY = 1;
    private static final int[] WALK_FRAMES = {0, 1, 2};

    private static final int SHELL_OFFSET_X = 4;
    private static final int SHELL_LAUNCH_SPEED_X = 0x100;
    private static final int SHELL_LAUNCH_SPEED_Y = -0x400;
    private static final int SHELL_COLLISION_FLAGS = 0x9E;
    private static final int SHELL_FRAME = 3;
    private static final int SHELL_TRAIL_FRAME = 4;
    private static final int SHELL_PRIORITY_BUCKET = 5;
    private static final int SHELL_OFF_SCREEN_MARGIN = 160;

    private static final int[] SHELL_DRIP_FRAMES = {5, 5, 5, 6, 7};
    private static final int[] WATER_SPLASH_FRAMES = {8, 9, 10, 11, 12, 13};
    private static final int[] WATER_SPLASH_OFFSETS_X = {4, -6, 6, -8, 8};
    private static final int[] WATER_SPLASH_OFFSETS_Y = {-8, 0, 0, 0, 0};

    private enum State {
        HIDDEN_WAIT,
        EMERGE_DELAY,
        EMERGE_WATERFALL,
        PATROL,
        TURN_PAUSE,
        LAUNCH_PREP,
        SHELLLESS_RUN
    }

    private final boolean hiddenVariant;
    private final int turnResetTimer;

    private State state;
    private State resumeState;
    private int stateTimer;
    private int turnTimer;
    private int currentPriorityBucket = PRIORITY_BUCKET_NORMAL;

    private boolean initialized;
    private boolean waterfallOverlayVisible;
    private int animIndex;
    private int animTimer;

    private TurboSpikerShellChild shellChild;

    public TurboSpikerBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "TurboSpiker",
                Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER, COLLISION_SIZE_INDEX, PRIORITY_BUCKET_NORMAL);
        this.hiddenVariant = (spawn.renderFlags() & 0x02) != 0;
        this.state = hiddenVariant ? State.HIDDEN_WAIT : State.PATROL;
        this.turnTimer = (spawn.subtype() & 0xFF) << 1;
        this.turnResetTimer = this.turnTimer << 1;
        this.mappingFrame = WALK_FRAMES[0];
        this.waterfallOverlayVisible = hiddenVariant;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (destroyed || !isOnScreenX()) {
            return;
        }

        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!initialized) {
            initialize(player);
            return;
        }

        switch (state) {
            case HIDDEN_WAIT -> updateHiddenWait(player);
            case EMERGE_DELAY -> updateEmergeDelay();
            case EMERGE_WATERFALL -> updateEmergeWaterfall();
            case PATROL -> updatePatrol(player);
            case TURN_PAUSE -> updateTurnPause();
            case LAUNCH_PREP -> updateLaunchPrep();
            case SHELLLESS_RUN -> updateShelllessRun();
        }
    }

    @Override
    public int getPriorityBucket() {
        return currentPriorityBucket;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        boolean hFlip = !facingLeft;
        if (shellChild != null && shellChild.isAttached()) {
            renderer.drawFrameIndex(SHELL_FRAME,
                    currentX + adjustedOffsetX(SHELL_OFFSET_X),
                    currentY,
                    hFlip, false);
        }
        renderer.drawFrameIndex(mappingFrame, getRenderAnchorX(), getRenderAnchorY(), hFlip, false);
    }

    private void initialize(AbstractPlayableSprite player) {
        trackInitialFacing(player);
        shellChild = spawnChild(() -> new TurboSpikerShellChild(this));
        if (hiddenVariant) {
            spawnChild(() -> new TurboSpikerWaterfallOverlayChild(this));
        }
        initialized = true;
    }

    private void updateHiddenWait(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite target = findNearestTarget(mainPlayer);
        if (target == null || findNearestPlayerXDistance(target) >= DETECT_RANGE) {
            return;
        }

        waterfallOverlayVisible = false;
        state = State.EMERGE_DELAY;
        stateTimer = WATERFALL_EMERGE_DELAY;
        spawnWaterSplashBurst();
    }

    private void updateEmergeDelay() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.EMERGE_WATERFALL;
        stateTimer = WATERFALL_PRIORITY_DELAY;
        currentPriorityBucket = PRIORITY_BUCKET_WATERFALL;
    }

    private void updateEmergeWaterfall() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.PATROL;
        currentPriorityBucket = PRIORITY_BUCKET_NORMAL;
    }

    private void updatePatrol(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite target = findNearestTarget(mainPlayer);
        if (shouldLaunchShell(target)) {
            enterLaunchPrep();
            return;
        }

        animateWalking(WALK_ANIM_DELAY);
        moveWithVelocity();
        if (!snapToFloorOrPause(State.PATROL)) {
            return;
        }

        turnTimer--;
        if (turnTimer < 0) {
            enterTurnPause(State.PATROL);
        }
    }

    private void updateTurnPause() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = resumeState;
        xVelocity = -xVelocity;
        facingLeft = !facingLeft;
        turnTimer = turnResetTimer;
        currentPriorityBucket = PRIORITY_BUCKET_NORMAL;
    }

    private void updateLaunchPrep() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.SHELLLESS_RUN;
        if (shellChild != null) {
            shellChild.launch();
        }
    }

    private void updateShelllessRun() {
        animateWalking(SHELLLESS_ANIM_DELAY);
        moveWithVelocity();
        snapToFloorOrPause(State.SHELLLESS_RUN);
    }

    private void enterLaunchPrep() {
        state = State.LAUNCH_PREP;
        stateTimer = TURN_DELAY;
        facingLeft = !facingLeft;
        xVelocity = facingLeft ? -RETREAT_SPEED : RETREAT_SPEED;
    }

    private void enterTurnPause(State previousState) {
        if (state == State.TURN_PAUSE) {
            return;
        }
        resumeState = previousState;
        state = State.TURN_PAUSE;
        stateTimer = TURN_DELAY;
    }

    private boolean snapToFloorOrPause(State previousState) {
        int probeX = currentX + (xVelocity >> 8);
        TerrainCheckResult floor;
        try {
            floor = ObjectTerrainUtils.checkFloorDist(probeX, currentY, Y_RADIUS);
        } catch (IllegalStateException e) {
            return true;
        }
        if (!floor.foundSurface() || floor.distance() < FLOOR_MIN_DIST || floor.distance() >= FLOOR_MAX_DIST) {
            enterTurnPause(previousState);
            return false;
        }
        currentY += floor.distance();
        return true;
    }

    private void animateWalking(int delay) {
        animTimer--;
        if (animTimer >= 0) {
            return;
        }
        animIndex = (animIndex + 1) % WALK_FRAMES.length;
        mappingFrame = WALK_FRAMES[animIndex];
        animTimer = delay;
    }

    private void trackInitialFacing(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite target = findNearestTarget(mainPlayer);
        if (target == null || target.getDead()) {
            xVelocity = facingLeft ? -INITIAL_TRACK_SPEED : INITIAL_TRACK_SPEED;
            return;
        }

        if (currentX - target.getCentreX() >= 0) {
            facingLeft = true;
            xVelocity = -INITIAL_TRACK_SPEED;
        } else {
            facingLeft = false;
            xVelocity = INITIAL_TRACK_SPEED;
        }
    }

    private boolean shouldLaunchShell(AbstractPlayableSprite target) {
        if (target == null || target.getDead()) {
            return false;
        }
        if (findNearestPlayerXDistance(target) >= DETECT_RANGE) {
            return false;
        }

        int directionCode = currentX - target.getCentreX() >= 0 ? 0 : 2;
        if (!facingLeft) {
            directionCode -= 2;
        }
        return directionCode == 0;
    }

    private AbstractPlayableSprite findNearestTarget(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite nearest = null;
        int nearestDistance = Integer.MAX_VALUE;

        if (mainPlayer != null && !mainPlayer.getDead()) {
            nearest = mainPlayer;
            nearestDistance = Math.abs(currentX - mainPlayer.getCentreX());
        }
        if (services() == null) {
            return nearest;
        }

        for (PlayableEntity sidekick : services().sidekicks()) {
            if (!(sidekick instanceof AbstractPlayableSprite sprite) || sprite.getDead()) {
                continue;
            }
            int distance = Math.abs(currentX - sprite.getCentreX());
            if (distance < nearestDistance) {
                nearest = sprite;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private int findNearestPlayerXDistance(AbstractPlayableSprite target) {
        return target == null ? Integer.MAX_VALUE : Math.abs(currentX - target.getCentreX());
    }

    private boolean shouldShowWaterfallOverlay() {
        return waterfallOverlayVisible && !destroyed;
    }

    private int adjustedOffsetX(int baseOffset) {
        return adjustedOffsetX(baseOffset, facingLeft);
    }

    private static int adjustedOffsetX(int baseOffset, boolean facingLeft) {
        return facingLeft ? baseOffset : -baseOffset;
    }

    private void spawnWaterSplashBurst() {
        for (int i = 0; i < WATER_SPLASH_OFFSETS_X.length; i++) {
            int index = i;
            boolean playSound = i == 0;
            spawnChild(() -> new TurboSpikerWaterSplashParticle(
                    this,
                    currentX + adjustedOffsetX(WATER_SPLASH_OFFSETS_X[index]),
                    currentY + WATER_SPLASH_OFFSETS_Y[index],
                    playSound));
        }
    }

    private static final class TurboSpikerShellChild extends AbstractObjectInstance
            implements TouchResponseProvider {

        private final TurboSpikerBadnikInstance parent;

        private int currentX;
        private int currentY;
        private int xVelocity;
        private int yVelocity;
        private int xSubpixel;
        private int ySubpixel;
        private boolean attached = true;
        private boolean facingLeft;

        private TurboSpikerTrailEmitter trailEmitter;

        TurboSpikerShellChild(TurboSpikerBadnikInstance parent) {
            super(parent.getSpawn(), "TurboSpikerShell");
            this.parent = parent;
            this.facingLeft = parent.facingLeft;
            this.currentX = parent.currentX + adjustedOffsetX(SHELL_OFFSET_X, facingLeft);
            this.currentY = parent.currentY;
        }

        void launch() {
            if (!attached || parent.isDestroyed()) {
                return;
            }
            attached = false;
            facingLeft = parent.facingLeft;
            currentX = parent.currentX + adjustedOffsetX(SHELL_OFFSET_X, facingLeft);
            currentY = parent.currentY;
            xVelocity = facingLeft ? -SHELL_LAUNCH_SPEED_X : SHELL_LAUNCH_SPEED_X;
            yVelocity = SHELL_LAUNCH_SPEED_Y;
            trailEmitter = spawnChild(() -> new TurboSpikerTrailEmitter(this));
            services().playSfx(Sonic3kSfx.FLOOR_LAUNCHER.id);
        }

        boolean isAttached() {
            return attached;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (attached) {
                if (parent.isDestroyed()) {
                    setDestroyed(true);
                    return;
                }
                facingLeft = parent.facingLeft;
                currentX = parent.currentX + adjustedOffsetX(SHELL_OFFSET_X, facingLeft);
                currentY = parent.currentY;
                return;
            }

            int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
            int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
            xPos24 += xVelocity;
            yPos24 += yVelocity;
            currentX = xPos24 >> 8;
            currentY = yPos24 >> 8;
            xSubpixel = xPos24 & 0xFF;
            ySubpixel = yPos24 & 0xFF;

            if (!isOnScreen(SHELL_OFF_SCREEN_MARGIN)) {
                setDestroyed(true);
            }
        }

        @Override
        public int getCollisionFlags() {
            if (isDestroyed()) {
                return 0;
            }
            return SHELL_COLLISION_FLAGS;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(currentX, currentY);
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
            return SHELL_PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (attached) {
                return;
            }
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(SHELL_FRAME, currentX, currentY, !facingLeft, false);
        }
    }

    private static final class TurboSpikerTrailEmitter extends AbstractObjectInstance {

        private static final int SPAWN_INTERVAL_MASK = 0x03;
        private static final int OFFSET_X = -4;
        private static final int OFFSET_Y = 0x14;

        private final TurboSpikerShellChild shell;
        private int mappingFrame = SHELL_TRAIL_FRAME;

        TurboSpikerTrailEmitter(TurboSpikerShellChild shell) {
            super(shell.getSpawn(), "TurboSpikerTrailEmitter");
            this.shell = shell;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (shell.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            if ((frameCounter & SPAWN_INTERVAL_MASK) == 0) {
                int xJitter = ((frameCounter >> 2) & 7) - 3;
                int yJitter = ((frameCounter >> 3) & 7) - 3;
                spawnChild(() -> new TurboSpikerShellDripParticle(
                        getX() + xJitter,
                        getY() + yJitter + 4,
                        shell.getSpawn()));
            }

            mappingFrame ^= 1;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(getX(), getY());
        }

        @Override
        public int getX() {
            return shell.getX() + adjustedOffsetX(OFFSET_X, shell.facingLeft);
        }

        @Override
        public int getY() {
            return shell.getY() + OFFSET_Y;
        }

        @Override
        public int getPriorityBucket() {
            return 4;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if ((mappingFrame & 1) == 0) {
                return;
            }
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(SHELL_TRAIL_FRAME, getX(), getY(), !shell.facingLeft, false);
        }
    }

    private static final class TurboSpikerShellDripParticle extends TurboSpikerAnimatedParticle {
        TurboSpikerShellDripParticle(int x, int y, ObjectSpawn ownerSpawn) {
            super(ownerSpawn, "TurboSpikerShellDrip", x, y, SHELL_DRIP_FRAMES, 1, 5, false);
        }
    }

    private static final class TurboSpikerWaterSplashParticle extends TurboSpikerAnimatedParticle {
        TurboSpikerWaterSplashParticle(TurboSpikerBadnikInstance parent, int x, int y, boolean playSound) {
            super(parent.getSpawn(), "TurboSpikerWaterSplash", x, y, WATER_SPLASH_FRAMES, 1, 4, playSound);
        }
    }

    private static class TurboSpikerAnimatedParticle extends AbstractObjectInstance {

        private final int currentX;
        private final int currentY;
        private final int[] frames;
        private final int frameDelay;
        private final int priorityBucket;

        private int frameIndex;
        private int frameTimer;
        private int mappingFrame;

        TurboSpikerAnimatedParticle(ObjectSpawn ownerSpawn, String name, int x, int y,
                int[] frames, int frameDelay, int priorityBucket, boolean playSound) {
            super(ownerSpawn, name);
            this.currentX = x;
            this.currentY = y;
            this.frames = frames;
            this.frameDelay = frameDelay;
            this.priorityBucket = priorityBucket;
            this.mappingFrame = frames[0];
            if (playSound) {
                services().playSfx(Sonic3kSfx.SPLASH.id);
            }
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            frameTimer--;
            if (frameTimer >= 0) {
                return;
            }
            frameTimer = frameDelay;
            frameIndex++;
            if (frameIndex >= frames.length) {
                setDestroyed(true);
                return;
            }
            mappingFrame = frames[frameIndex];
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(currentX, currentY);
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
            return priorityBucket;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
        }
    }

    private static final class TurboSpikerWaterfallOverlayChild extends AbstractObjectInstance {

        private final TurboSpikerBadnikInstance parent;

        TurboSpikerWaterfallOverlayChild(TurboSpikerBadnikInstance parent) {
            super(parent.getSpawn(), "TurboSpikerWaterfallOverlay");
            this.parent = parent;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (!parent.shouldShowWaterfallOverlay()) {
                setDestroyed(true);
            }
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(parent.currentX, parent.currentY);
        }

        @Override
        public int getX() {
            return parent.currentX;
        }

        @Override
        public int getY() {
            return parent.currentY;
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET_WATERFALL;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.HCZ_TURBO_SPIKER_HIDDEN);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(0, parent.currentX, parent.currentY, false, false);
        }
    }
}
