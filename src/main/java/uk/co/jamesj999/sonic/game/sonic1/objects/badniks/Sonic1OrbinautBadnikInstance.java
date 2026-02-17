package uk.co.jamesj999.sonic.game.sonic1.objects.badniks;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic1.objects.Sonic1PointsObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.ExplosionObjectInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AbstractBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic2.objects.badniks.AnimalObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectArtKeys;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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
    private int xSubpixel;

    private int animationId;
    private int animationFrame;
    private int animationTimer;

    private int angleStep;
    private int activeSpikes;

    private List<OrbSpikeObjectInstance> spikes;
    private boolean initialized;

    public Sonic1OrbinautBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Orbinaut");
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
        this.xSubpixel = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        if (!initialized) {
            spawnSatellites();
            initialized = true;
        }

        if (routine == ROUTINE_CHK_SONIC && shouldBecomeAngry(player)) {
            animationId = 1;
            animationTimer = ANIM_SPEED;
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
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += xVelocity;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (animationId == 0) {
            animationFrame = 0;
            return;
        }

        animationTimer--;
        if (animationTimer > 0) {
            return;
        }
        animationTimer = ANIM_SPEED;

        // .angers: 1,2,afBack,1
        animationFrame = (animationFrame == 1) ? 2 : 1;
    }

    private void spawnSatellites() {
        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }

        spikes = new ArrayList<>(4);
        spikes.add(new OrbSpikeObjectInstance(this, 0x00));
        spikes.add(new OrbSpikeObjectInstance(this, 0x40));
        spikes.add(new OrbSpikeObjectInstance(this, 0x80));
        spikes.add(new OrbSpikeObjectInstance(this, 0xC0));

        activeSpikes = spikes.size();
        for (OrbSpikeObjectInstance spike : spikes) {
            levelManager.getObjectManager().addDynamicObject(spike);
        }
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
    protected void destroyBadnik(AbstractPlayableSprite player) {
        destroyed = true;
        setDestroyed(true);
        destroySpikes();

        if (levelManager == null || levelManager.getObjectManager() == null) {
            return;
        }

        var objectManager = levelManager.getObjectManager();
        if (objectManager != null) {
            if (spawn.respawnTracked()) {
                objectManager.markRemembered(spawn);
            } else {
                objectManager.removeFromActiveSpawns(spawn);
            }
        }

        ExplosionObjectInstance explosion = new ExplosionObjectInstance(0x27, currentX, currentY,
                levelManager.getObjectRenderManager());
        levelManager.getObjectManager().addDynamicObject(explosion);

        AnimalObjectInstance animal = new AnimalObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x28, 0, 0, false, 0), levelManager);
        levelManager.getObjectManager().addDynamicObject(animal);

        int pointsValue = 100;
        if (player != null) {
            pointsValue = player.incrementBadnikChain();
            GameServices.gameState().addScore(pointsValue);
        }

        Sonic1PointsObjectInstance points = new Sonic1PointsObjectInstance(
                new ObjectSpawn(currentX, currentY, 0x29, 0, 0, false, 0), levelManager, pointsValue);
        levelManager.getObjectManager().addDynamicObject(points);

        AudioManager.getInstance().playSfx(Sonic1Sfx.BREAK_ITEM.id);
    }

    @Override
    public boolean isPersistent() {
        return !destroyed && isOnScreenX(192);
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
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.ORBINAUT);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        renderer.drawFrameIndex(animationFrame, currentX, currentY, !facingLeft, false);
    }

    private static final class OrbSpikeObjectInstance extends AbstractObjectInstance
            implements TouchResponseProvider {

        private final Sonic1OrbinautBadnikInstance parent;

        private int x;
        private int y;
        private int angle;

        private boolean launched;
        private int xVelocity;
        private int xSubpixel;

        OrbSpikeObjectInstance(Sonic1OrbinautBadnikInstance parent, int startAngle) {
            super(new ObjectSpawn(parent.currentX, parent.currentY, parent.spawn.objectId(), 0, 0, false, 0),
                    "OrbinautSpike");
            this.parent = parent;
            this.angle = startAngle & 0xFF;
            this.x = parent.currentX;
            this.y = parent.currentY;
            this.launched = false;
            this.xVelocity = 0;
            this.xSubpixel = 0;
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
        public void update(int frameCounter, AbstractPlayableSprite player) {
            if (isDestroyed()) {
                return;
            }

            if (parent.isDestroyed()) {
                setDestroyed(true);
                return;
            }

            if (launched) {
                int xPos24 = (x << 8) | (xSubpixel & 0xFF);
                xPos24 += xVelocity;
                x = xPos24 >> 8;
                xSubpixel = xPos24 & 0xFF;

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

            ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
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
