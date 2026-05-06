package com.openggf.game.sonic1.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.game.rewind.RewindTransient;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.game.PlayableEntity;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonic 1 Badnik 0x60 - Orbinaut (LZ/SLZ/SBZ).
 * <p>
 * ROM reference: docs/s1disasm/_incObj/60 Orbinaut.asm
 */
public class Sonic1OrbinautBadnikInstance extends AbstractBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x0B;

    private static final int DETECT_X = 0xA0;
    private static final int DETECT_Y = 0x50;

    private static final int MOVE_SPEED = 0x40;
    private static final int SPIKE_SHOT_SPEED = 0x200;

    private static final int ANIM_SPEED = 0x10; // $F + 1

    private static final int ROUTINE_CHK_SONIC = 2;
    private static final int ROUTINE_MOVE = 4;

    private int routine;
    /** Subpixel accumulators (xSub / ySub) for ROM-accurate 16:8 fixed-point integration. */
    private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    private int animationId;
    private int animationFrame;
    private int animationTimer;

    private int angleStep;
    private int activeSpikes;

    private List<OrbSpikeObjectInstance> spikes;
    private boolean initialized;

    public Sonic1OrbinautBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Orbinaut");
        this.currentX = spawn.x();
        this.currentY = spawn.y();

        this.facingLeft = (spawn.renderFlags() & 0x01) == 0;

        this.routine = ((spawn.subtype() & 0xFF) == 2) ? ROUTINE_MOVE : ROUTINE_CHK_SONIC;
        this.xVelocity = facingLeft ? -MOVE_SPEED : MOVE_SPEED;
        this.angleStep = facingLeft ? 1 : -1;

        this.animationId = 0;
        this.animationFrame = 0;
        this.animationTimer = ANIM_SPEED;

        this.activeSpikes = 0;
        this.initialized = false;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!initialized) {
            spawnSatellites();
            initialized = true;
        }

        if (routine == ROUTINE_CHK_SONIC && shouldBecomeAngry(player)) {
            // ROM: move.b #1,obAnim(a0) — AnimateSprite only resets when obAnim
            // changes. On first transition, immediately set frame 1 (ROM's
            // AnimateSprite sets the first frame instantly on animation change).
            if (animationId != 1) {
                animationId = 1;
                animationFrame = 1;         // Immediately show angry face
                animationTimer = ANIM_SPEED; // Timer for transition to frame 2
            }
        }

        if (routine >= ROUTINE_MOVE) {
            applySpeedToPos();
        }

        if (activeSpikes == 0) {
            routine = ROUTINE_MOVE;
        }
    }

    private boolean shouldBecomeAngry(AbstractPlayableSprite player) {
        if (player == null || player.isDebugMode()) {
            return false;
        }

        int dx = Math.abs(player.getCentreX() - currentX);
        if (dx >= DETECT_X) {
            return false;
        }

        int dy = Math.abs(player.getCentreY() - currentY);
        return dy < DETECT_Y;
    }

    private void applySpeedToPos() {
        motion.x = currentX;
        motion.xVel = xVelocity;
        SubpixelMotion.moveX(motion);
        currentX = motion.x;
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (animationId == 0) {
            animationFrame = 0;
            return;
        }

        // ROM animation: .angers: dc.b $F, 1, 2, afBack, 1
        // Plays frame 1 once, then stays at frame 2 permanently (afBack loops
        // back to frame 2). Frame 1 is set immediately in updateMovement when
        // the orbinaut first gets angry.
        if (animationFrame >= 2) {
            return; // afBack: stay at frame 2 permanently
        }

        animationTimer--;
        if (animationTimer > 0) {
            return;
        }
        animationTimer = ANIM_SPEED;
        animationFrame = 2; // Advance from frame 1 to frame 2
    }

    private void spawnSatellites() {
        if (services().objectManager() == null) {
            return;
        }

        spikes = new ArrayList<>(4);
        int[] angleOffsets = { 0x00, 0x40, 0x80, 0xC0 };
        // ROM: Orb_Loop uses FindNextFreeObj, allocating consecutive slots
        int prevSlot = getSlotIndex();
        for (int angleOffset : angleOffsets) {
            final int angle = angleOffset;
            final int prevSlotFinal = prevSlot;
            OrbSpikeObjectInstance spike = spawnFreeChild(() -> {
                OrbSpikeObjectInstance s = new OrbSpikeObjectInstance(this, angle);
                if (prevSlotFinal >= 0) {
                    int spikeSlot = services().objectManager().allocateSlotAfter(prevSlotFinal);
                    if (spikeSlot >= 0) {
                        s.setSlotIndex(spikeSlot);
                    }
                }
                return s;
            });
            spikes.add(spike);
            if (spike.getSlotIndex() >= 0) {
                prevSlot = spike.getSlotIndex();
            }
        }
        activeSpikes = spikes.size();
    }

    int getAnimationFrame() {
        return animationFrame;
    }

    int getAngleStep() {
        return angleStep;
    }

    boolean isFacingLeft() {
        return facingLeft;
    }

    void onSpikeLaunched() {
        if (activeSpikes > 0) {
            activeSpikes--;
        }
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    protected DestructionConfig getDestructionConfig() {
        return Sonic1DestructionConfig.S1_DESTRUCTION_CONFIG;
    }

    @Override
    protected void destroyBadnik(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        destroySpikes();
        super.destroyBadnik(player);
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed() && isOnScreenX(192);
    }

    @Override
    public void onUnload() {
        destroySpikes();
    }

    private void destroySpikes() {
        if (spikes != null) {
            for (OrbSpikeObjectInstance spike : spikes) {
                spike.setDestroyed(true);
            }
        }
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

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.ORBINAUT);
        if (renderer == null) return;

        renderer.drawFrameIndex(animationFrame, currentX, currentY, !facingLeft, false);
    }

    private static final class OrbSpikeObjectInstance extends AbstractObjectInstance
            implements TouchResponseProvider {

        @RewindTransient(reason = "parent/child satellite link; live object graph persists across rewind")
        private final Sonic1OrbinautBadnikInstance parent;

        private int x;
        private int y;
        private int angle;

        private boolean launched;
        private int xVelocity;
        /** Subpixel accumulators (xSub / ySub) for ROM-accurate 16:8 fixed-point integration. */
        private final SubpixelMotion.State motion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

        OrbSpikeObjectInstance(Sonic1OrbinautBadnikInstance parent, int startAngle) {
            super(new ObjectSpawn(parent.currentX, parent.currentY, parent.spawn.objectId(), 0, 0, false, 0),
                    "OrbinautSpike");
            this.parent = parent;
            this.angle = startAngle & 0xFF;
            this.x = parent.currentX;
            this.y = parent.currentY;
            this.launched = false;
            this.xVelocity = 0;
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
        public ObjectSpawn getSpawn() {
            return new ObjectSpawn(x, y, spawn.objectId(), spawn.subtype(), 0, false, 0);
        }

        @Override
        public void update(int frameCounter, PlayableEntity playerEntity) {
            AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
            if (isDestroyed()) {
                return;
            }

            if (parent.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            if (launched) {
                motion.x = x;
                motion.xVel = xVelocity;
                SubpixelMotion.moveX(motion);
                x = motion.x;

                if (!isOnScreen(256)) {
                    setDestroyed(true);
                }
                return;
            }

            // Orb_MoveOrb launch condition.
            if (parent.getAnimationFrame() == 2 && angle == 0x40) {
                launched = true;
                parent.onSpikeLaunched();
                xVelocity = parent.isFacingLeft() ? -SPIKE_SHOT_SPEED : SPIKE_SHOT_SPEED;
                return;
            }

            // Orbit around parent with radius 16.
            double radians = (angle & 0xFF) * (Math.PI * 2.0 / 256.0);
            x = parent.currentX + (int) Math.round(Math.cos(radians) * 16.0);
            y = parent.currentY + (int) Math.round(Math.sin(radians) * 16.0);
            angle = (angle + parent.getAngleStep()) & 0xFF;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (isDestroyed()) {
                return;
            }

            ObjectServices svc = tryServices();
            ObjectRenderManager renderManager = svc != null ? svc.renderManager() : null;
            if (renderManager == null) {
                return;
            }

            PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.ORBINAUT);
            if (renderer == null || !renderer.isReady()) {
                return;
            }

            renderer.drawFrameIndex(3, x, y, false, false);
        }

        @Override
        public int getCollisionFlags() {
            // $98 from Orb_MoveOrb setup.
            return 0x98;
        }

        @Override
        public int getCollisionProperty() {
            return 0;
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(4);
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed() && isOnScreenX(256);
        }
    }
}
