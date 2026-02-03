package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseProvider;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.TrigLookupTable;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sol fireball (Obj95 child) - orbiting then fired projectile.
 * Uses Ani_obj95_b and follows Obj95_FireballUpdate behavior.
 */
public class SolFireballObjectInstance extends AbstractObjectInstance implements TouchResponseProvider {
    private enum State {
        ORBIT,
        FLYING
    }

    private static final int COLLISION_FLAGS = 0x98; // HURT + size index 0x18
    private static final int X_VELOCITY = 0x200;     // 2 pixels/frame (8.8 fixed)

    private final ObjectAnimationState animationState;
    private SolBadnikInstance parent;
    private State state;
    private int angle;
    private int currentX;
    private int currentY;
    private int xVelocity;
    private int xSubpixel;

    public SolFireballObjectInstance(ObjectSpawn spawn, SolBadnikInstance parent, int angle) {
        super(spawn, "SolFireball");
        this.parent = parent;
        this.angle = angle & 0xFF;
        this.state = State.ORBIT;
        this.currentX = parent != null ? parent.getX() : spawn.x();
        this.currentY = parent != null ? parent.getY() : spawn.y();
        this.animationState = new ObjectAnimationState(SolBadnikInstance.getFireballAnimations(), 0, 3);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        switch (state) {
            case ORBIT -> updateOrbit();
            case FLYING -> updateFlying();
        }
    }

    private void updateOrbit() {
        animationState.update();

        if (parent == null || parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        if (parent.getCurrentMappingFrame() == 2 && (angle & 0xFF) == 0x40) {
            launch();
            return;
        }

        int cos = TrigLookupTable.cosHex(angle);
        int sin = TrigLookupTable.sinHex(angle);
        currentX = parent.getX() + (cos >> 4);
        currentY = parent.getY() + (sin >> 4);

        angle = (angle + parent.getOrbitDirection()) & 0xFF;
    }

    private void updateFlying() {
        int xPos32 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos32 += xVelocity;
        currentX = xPos32 >> 8;
        xSubpixel = xPos32 & 0xFF;

        if (!isOnScreen()) {
            setDestroyed(true);
            return;
        }

        animationState.update();
    }

    private void launch() {
        state = State.FLYING;
        xVelocity = -X_VELOCITY;
        if (parent != null && parent.isXFlip()) {
            xVelocity = -xVelocity;
        }
        if (parent != null) {
            parent.onFireballLaunched();
        }
    }

    void detachFromParent() {
        parent = null;
    }

    @Override
    public int getCollisionFlags() {
        if (isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(
                currentX,
                currentY,
                spawn.objectId(),
                spawn.subtype(),
                spawn.renderFlags(),
                spawn.respawnTracked(),
                spawn.rawYWord());
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
        return RenderPriority.clamp(4);
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SOL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame = animationState.getMappingFrame();
        renderer.drawFrameIndex(frame, currentX, currentY, false, false);
    }
}
