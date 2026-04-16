package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K Obj $9C - Spiker (MGZ).
 *
 * <p>ROM reference: {@code Obj_Spiker} (sonic3k.asm:185372-185672). The main
 * body rises when Sonic/Tails are within $40 pixels, exposing two side launchers
 * and a spring-loaded top spike. Touching the top spike compresses the shell,
 * then launches the player upward at {@code -$600}. The side launchers animate
 * and fire one gravity-affected spike projectile from the player's side.
 */
public final class SpikerBadnikInstance extends AbstractS3kBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x0A;
    private static final int PRIORITY_BUCKET = 5;
    private static final int TOP_SPIKE_PRIORITY_BUCKET = 4;

    private static final int DETECT_RANGE = 0x40;
    private static final int RISE_DESCEND_FRAMES = 7;
    private static final int RISE_STEP = 1;
    private static final int SIDE_LAUNCHER_OFFSET_X = 0x10;
    private static final int SIDE_LAUNCHER_OFFSET_Y = 0x0C;
    private static final int TOP_SPIKE_OFFSET_Y = -0x0C;

    private static final int LAUNCH_Y_VELOCITY = -0x600;
    private static final int TOP_SPIKE_TOUCH_NUDGE_Y = 6;
    private static final int TOP_SPIKE_COOLDOWN = 0x10;

    private static final int[] LAUNCH_ANIM_FRAMES = {1, 2, 1, 0};
    private static final int[] LAUNCH_ANIM_DELAYS = {0, 1, 0, 5};

    private enum State {
        DETECT,
        RISE,
        OPEN,
        DESCEND,
        LAUNCH_ANIM
    }

    private boolean initialized;
    private State state = State.DETECT;
    private State resumeState = State.DETECT;
    private int stateTimer;
    private int launchAnimIndex = -1;
    private int launchAnimTimer;
    private boolean collisionEnabled = true;
    private boolean spikesExtended;
    private AbstractPlayableSprite pendingLaunchPlayer;

    private SpikerSideLauncherChild leftLauncher;
    private SpikerSideLauncherChild rightLauncher;
    private SpikerTopSpikeChild topSpike;

    public SpikerBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Spiker",
                Sonic3kObjectArtKeys.MGZ_SPIKER, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (destroyed || !isOnScreenX()) {
            return;
        }

        AbstractPlayableSprite player = playerEntity instanceof AbstractPlayableSprite sprite
                ? sprite : null;
        if (!initialized) {
            initializeChildren();
            initialized = true;
            return;
        }

        switch (state) {
            case DETECT -> updateDetect(player);
            case RISE -> updateRise();
            case OPEN -> updateOpen(player);
            case DESCEND -> updateDescend();
            case LAUNCH_ANIM -> updateLaunchAnim();
        }
    }

    @Override
    public void onUnload() {
        spikesExtended = false;
        pendingLaunchPlayer = null;
        collisionEnabled = true;
        destroyChild(leftLauncher);
        destroyChild(rightLauncher);
        destroyChild(topSpike);
        leftLauncher = null;
        rightLauncher = null;
        topSpike = null;
    }

    @Override
    public int getCollisionFlags() {
        return collisionEnabled ? super.getCollisionFlags() : 0;
    }

    private void initializeChildren() {
        leftLauncher = spawnChild(() -> new SpikerSideLauncherChild(this, true));
        rightLauncher = spawnChild(() -> new SpikerSideLauncherChild(this, false));
        topSpike = spawnChild(() -> new SpikerTopSpikeChild(this));
    }

    private void updateDetect(AbstractPlayableSprite mainPlayer) {
        if (nearestPlayerDistance(mainPlayer) >= DETECT_RANGE) {
            return;
        }
        state = State.RISE;
        stateTimer = RISE_DESCEND_FRAMES;
    }

    private void updateRise() {
        currentY -= RISE_STEP;
        stateTimer--;
        if (stateTimer < 0) {
            state = State.OPEN;
            spikesExtended = true;
        }
    }

    private void updateOpen(AbstractPlayableSprite mainPlayer) {
        if (nearestPlayerDistance(mainPlayer) < DETECT_RANGE) {
            return;
        }
        state = State.DESCEND;
        stateTimer = RISE_DESCEND_FRAMES;
        spikesExtended = false;
    }

    private void updateDescend() {
        currentY += RISE_STEP;
        stateTimer--;
        if (stateTimer < 0) {
            state = State.DETECT;
        }
    }

    private void updateLaunchAnim() {
        launchAnimTimer = (launchAnimTimer - 1) & 0xFF;
        if (launchAnimTimer < 0x80) {
            return;
        }

        launchAnimIndex++;
        if (launchAnimIndex >= LAUNCH_ANIM_FRAMES.length) {
            finishLaunchAnim();
            return;
        }

        mappingFrame = LAUNCH_ANIM_FRAMES[launchAnimIndex];
        launchAnimTimer = LAUNCH_ANIM_DELAYS[launchAnimIndex];

        if (launchAnimIndex == LAUNCH_ANIM_FRAMES.length - 1 && pendingLaunchPlayer != null) {
            launchPlayer(pendingLaunchPlayer);
        }
    }

    private void beginTopSpikeCompression(AbstractPlayableSprite player) {
        if (state == State.LAUNCH_ANIM) {
            return;
        }
        pendingLaunchPlayer = player;
        resumeState = state;
        state = State.LAUNCH_ANIM;
        collisionEnabled = false;
        mappingFrame = 1;
        launchAnimIndex = -1;
        launchAnimTimer = 0;
    }

    private void finishLaunchAnim() {
        state = resumeState;
        collisionEnabled = true;
        mappingFrame = 0;
        pendingLaunchPlayer = null;
        launchAnimIndex = -1;
        launchAnimTimer = 0;
    }

    private void launchPlayer(AbstractPlayableSprite player) {
        player.setYSpeed((short) LAUNCH_Y_VELOCITY);
        player.setAir(true);
        player.setOnObject(false);
        player.setJumping(false);
    }

    private AbstractPlayableSprite findNearestTarget(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite nearest = null;
        int nearestDistance = Integer.MAX_VALUE;

        if (mainPlayer != null && !mainPlayer.getDead()) {
            nearest = mainPlayer;
            nearestDistance = Math.abs(currentX - mainPlayer.getCentreX());
        }

        ObjectServices svc = tryServices();
        if (svc == null) {
            return nearest;
        }
        for (PlayableEntity sidekick : svc.sidekicks()) {
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

    private int nearestPlayerDistance(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite target = findNearestTarget(mainPlayer);
        return target == null ? Integer.MAX_VALUE : Math.abs(currentX - target.getCentreX());
    }

    private static boolean playerIsOnLeft(AbstractPlayableSprite player, int objectX) {
        return player != null && !player.getDead() && player.getCentreX() <= objectX;
    }

    private static boolean playerIsOnRight(AbstractPlayableSprite player, int objectX) {
        return player != null && !player.getDead() && player.getCentreX() > objectX;
    }

    private static void destroyChild(AbstractObjectInstance child) {
        if (child != null) {
            child.setDestroyed(true);
        }
    }

    private static final class SpikerSideLauncherChild extends AbstractObjectInstance {

        private static final int IDLE_FRAME = 3;
        private static final int[] ATTACK_FRAMES = {3, 3, 4, 3};
        private static final int[] ATTACK_DELAYS = {1, 0x0F, 7, 0x3F};

        private enum State {
            WAIT_FOR_OPEN,
            ARMED,
            ATTACK
        }

        private final SpikerBadnikInstance parent;
        private final boolean leftSide;

        private State state = State.WAIT_FOR_OPEN;
        private int mappingFrame = IDLE_FRAME;
        private int attackIndex = -1;
        private int attackTimer;
        private boolean projectileFired;

        private SpikerSideLauncherChild(SpikerBadnikInstance parent, boolean leftSide) {
            super(parent.getSpawn(), leftSide ? "SpikerLeftLauncher" : "SpikerRightLauncher");
            this.parent = parent;
            this.leftSide = leftSide;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (isDestroyed()) {
                return;
            }
            if (parent.destroyed) {
                setDestroyed(true);
                return;
            }
            switch (state) {
                case WAIT_FOR_OPEN -> updateWaitForOpen();
                case ARMED -> updateArmed(playerEntity);
                case ATTACK -> updateAttack();
            }
        }

        @Override
        public int getX() {
            return parent.currentX + (leftSide ? -SIDE_LAUNCHER_OFFSET_X : SIDE_LAUNCHER_OFFSET_X);
        }

        @Override
        public int getY() {
            return parent.currentY + SIDE_LAUNCHER_OFFSET_Y;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(getX(), getY());
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_SPIKER);
            if (renderer == null) {
                return;
            }
            renderer.drawFrameIndex(mappingFrame, getX(), getY(), leftSide, false);
        }

        private void spawnProjectile() {
            int xVelocity = leftSide ? -0x200 : 0x200;
            int xOffset = leftSide ? -4 : 4;
            spawnChild(() -> new SpikerSpikeProjectile(
                    parent,
                    getX() + xOffset,
                    getY(),
                    xVelocity,
                    -0x200,
                    leftSide));
        }

        private void playProjectileSfx() {
            try {
                services().playSfx(Sonic3kSfx.PROJECTILE.id);
            } catch (Exception ignored) {
                // Keep gameplay logic independent from audio state.
            }
        }

        private void updateWaitForOpen() {
            mappingFrame = IDLE_FRAME;
            if (parent.spikesExtended) {
                state = State.ARMED;
            }
        }

        private void updateArmed(PlayableEntity playerEntity) {
            mappingFrame = IDLE_FRAME;
            if (!parent.spikesExtended) {
                state = State.WAIT_FOR_OPEN;
                return;
            }

            AbstractPlayableSprite target = playerEntity instanceof AbstractPlayableSprite sprite
                    ? parent.findNearestTarget(sprite) : null;
            if (target == null || Math.abs(parent.currentX - target.getCentreX()) >= DETECT_RANGE) {
                return;
            }
            boolean matchingSide = leftSide
                    ? playerIsOnLeft(target, parent.currentX)
                    : playerIsOnRight(target, parent.currentX);
            if (!matchingSide) {
                return;
            }

            // ROM parity: loc_88CC6 only switches the child to the attack routine.
            // The animation itself begins on the following frame in loc_88D02.
            state = State.ATTACK;
            attackIndex = -1;
            attackTimer = 0;
            projectileFired = false;
        }

        private void updateAttack() {
            attackTimer = (attackTimer - 1) & 0xFF;
            if (attackTimer < 0x80) {
                return;
            }

            attackIndex++;
            if (attackIndex >= ATTACK_FRAMES.length) {
                state = State.ARMED;
                attackIndex = -1;
                attackTimer = 0;
                mappingFrame = IDLE_FRAME;
                return;
            }

            mappingFrame = ATTACK_FRAMES[attackIndex];
            attackTimer = ATTACK_DELAYS[attackIndex];

            if (!projectileFired && mappingFrame == 4) {
                projectileFired = true;
                playProjectileSfx();
                spawnProjectile();
            }
        }
    }

    private static final class SpikerTopSpikeChild extends AbstractObjectInstance
            implements TouchResponseProvider, TouchResponseListener {

        private static final int COLLISION_FLAGS = 0x40 | COLLISION_SIZE_INDEX;

        private final SpikerBadnikInstance parent;
        private int cooldown;

        private SpikerTopSpikeChild(SpikerBadnikInstance parent) {
            super(parent.getSpawn(), "SpikerTopSpike");
            this.parent = parent;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            if (isDestroyed()) {
                return;
            }
            if (parent.destroyed) {
                setDestroyed(true);
                return;
            }
            if (cooldown > 0) {
                cooldown--;
            }
        }

        @Override
        public int getCollisionFlags() {
            return cooldown > 0 || parent.destroyed ? 0 : COLLISION_FLAGS;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
            if (cooldown > 0 || !(playerEntity instanceof AbstractPlayableSprite player) || parent.destroyed) {
                return;
            }

            player.setYSpeed((short) 0);
            player.setAir(true);
            player.setOnObject(false);
            player.setCentreY((short) (player.getCentreY() + TOP_SPIKE_TOUCH_NUDGE_Y));
            player.setJumping(false);
            player.setRollingJump(false);

            try {
                services().playSfx(Sonic3kSfx.SPRING.id);
            } catch (Exception ignored) {
                // Keep gameplay logic independent from audio state.
            }

            cooldown = TOP_SPIKE_COOLDOWN;
            parent.beginTopSpikeCompression(player);
        }

        @Override
        public int getX() {
            return parent.currentX;
        }

        @Override
        public int getY() {
            return parent.currentY + TOP_SPIKE_OFFSET_Y;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(getX(), getY());
        }

        @Override
        public int getPriorityBucket() {
            return TOP_SPIKE_PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // ROM parity: ObjDat child uses mapping frame 7, which is an empty frame.
            // This child only provides the spring/touch region above the body art.
        }
    }

    private static final class SpikerSpikeProjectile extends AbstractObjectInstance
            implements TouchResponseProvider {

        private static final int COLLISION_FLAGS = 0x98;
        private static final int PRIORITY_BUCKET = 5;
        private static final int SHIELD_REACTION_BOUNCE = 1 << 3;
        private static final int DEFLECT_SPEED = 0x800;
        private static final int ANIM_DELAY = 1;
        private static final int[] ANIM_FRAMES = {5, 6};
        private static final int GRAVITY = 0x20;

        private int currentX;
        private int currentY;
        private int xVelocity;
        private int yVelocity;
        private int xSubpixel;
        private int ySubpixel;
        private final boolean hFlip;
        private int animFrame;
        private int animTimer;
        private boolean collisionEnabled = true;

        private SpikerSpikeProjectile(SpikerBadnikInstance parent, int x, int y,
                int xVelocity, int yVelocity, boolean hFlip) {
            super(parent.getSpawn(), "SpikerSpikeProjectile");
            this.currentX = x;
            this.currentY = y;
            this.xVelocity = xVelocity;
            this.yVelocity = yVelocity;
            this.hFlip = hFlip;
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            int oldYVelocity = yVelocity;
            yVelocity += GRAVITY;

            int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
            int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
            xPos24 += xVelocity;
            yPos24 += oldYVelocity;
            currentX = xPos24 >> 8;
            currentY = yPos24 >> 8;
            xSubpixel = xPos24 & 0xFF;
            ySubpixel = yPos24 & 0xFF;

            animTimer--;
            if (animTimer < 0) {
                animTimer = ANIM_DELAY;
                animFrame = (animFrame + 1) & 1;
            }

            if (!isOnScreen(48)) {
                setDestroyed(true);
            }
        }

        @Override
        public int getCollisionFlags() {
            return collisionEnabled ? COLLISION_FLAGS : 0;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public int getShieldReactionFlags() {
            return SHIELD_REACTION_BOUNCE;
        }

        @Override
        public boolean onShieldDeflect(PlayableEntity playerEntity) {
            if (!(playerEntity instanceof AbstractPlayableSprite player)) {
                return false;
            }
            int dx = player.getCentreX() - currentX;
            int dy = player.getCentreY() - currentY;
            int angle = TrigLookupTable.calcAngle(saturateToShort(dx), saturateToShort(dy));
            xVelocity = -((TrigLookupTable.cosHex(angle) * DEFLECT_SPEED) >> 8);
            yVelocity = -((TrigLookupTable.sinHex(angle) * DEFLECT_SPEED) >> 8);
            collisionEnabled = false;
            return true;
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
        public ObjectSpawn getSpawn() {
            return buildSpawnAt(currentX, currentY);
        }

        @Override
        public int getPriorityBucket() {
            return PRIORITY_BUCKET;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }
            ObjectRenderManager renderManager = services().renderManager();
            if (renderManager == null) {
                return;
            }
            PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic3kObjectArtKeys.MGZ_SPIKER);
            if (renderer == null || !renderer.isReady()) {
                return;
            }
            renderer.drawFrameIndex(ANIM_FRAMES[animFrame], currentX, currentY, hFlip, false);
        }

        private static short saturateToShort(int value) {
            if (value > Short.MAX_VALUE) {
                return Short.MAX_VALUE;
            }
            if (value < Short.MIN_VALUE) {
                return Short.MIN_VALUE;
            }
            return (short) value;
        }
    }
}
