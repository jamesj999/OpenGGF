package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.physics.SwingMotion;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Plane child sprite for AIZ1 intro - the biplane that Sonic rides.
 * ROM: loc_6777A (sonic3k.asm:135717)
 *
 * Uses Map_AIZIntroPlane / ArtTile_AIZIntroPlane.
 * Init sets up SwingMotion oscillation parameters and spawns 2 emerald
 * glow children. Normal mode follows parent position with an offset and
 * applies swing oscillation to Y. After Sonic dismounts, the plane
 * spirals offscreen and self-deletes.
 */
public class AizIntroPlaneChild extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(AizIntroPlaneChild.class.getName());

    // Swing parameters from ROM (Swing_UpAndDown acceleration / max)
    private static final int SWING_ACCELERATION = 0x08;
    private static final int SWING_MAX_VELOCITY = 0x100;

    // Offset from parent position (plane drawn relative to intro object)
    private static final int PARENT_X_OFFSET = -0x22;
    private static final int PARENT_Y_OFFSET = 0x2C;

    private final AizPlaneIntroInstance parent;
    private int currentX;
    private int currentY;
    private int xVel;
    private int yVel;
    private int swingVelocity;
    private boolean swingDirectionDown;
    private int mappingFrame;
    private boolean spiraling;

    // Emerald glow children (spawned during init, follow this plane)
    private AizIntroEmeraldGlowChild glowChild1;
    private AizIntroEmeraldGlowChild glowChild2;

    public AizIntroPlaneChild(ObjectSpawn spawn, AizPlaneIntroInstance parent) {
        super(spawn, "AIZIntroPlane");
        this.parent = parent;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
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
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (!spiraling) {
            // Normal mode: follow parent position with offset
            currentX = parent.getX() + PARENT_X_OFFSET;
            currentY = parent.getY() + PARENT_Y_OFFSET;

            // Apply swing oscillation to Y
            SwingMotion.Result result = SwingMotion.update(
                    SWING_ACCELERATION, swingVelocity, SWING_MAX_VELOCITY, swingDirectionDown);
            swingVelocity = result.velocity();
            swingDirectionDown = result.directionDown();

            // Swing velocity is in subpixels; shift to pixels
            currentY += swingVelocity >> 8;
        } else {
            // Spiraling away: apply velocity (subpixel) and self-delete when offscreen
            currentX += xVel >> 8;
            currentY += yVel >> 8;
            if (currentX > 0x200 || currentX < -0x100 || currentY < -0x100) {
                LOG.fine("Plane child: spiraled offscreen, destroying");
                setDestroyed(true);
            }
        }

        // Update emerald glow children
        if (glowChild1 != null) {
            glowChild1.update(frameCounter, player);
        }
        if (glowChild2 != null) {
            glowChild2.update(frameCounter, player);
        }
    }

    /**
     * Called by parent when Sonic dismounts - plane starts spiraling away.
     *
     * @param xVel horizontal velocity in subpixels (256 = 1 pixel/frame)
     * @param yVel vertical velocity in subpixels (256 = 1 pixel/frame)
     */
    public void startSpiral(int xVel, int yVel) {
        this.spiraling = true;
        this.xVel = xVel;
        this.yVel = yVel;
        LOG.fine("Plane child: starting spiral (xVel=" + xVel + ", yVel=" + yVel + ")");
    }

    /**
     * Sets the two emerald glow children. Called during integration when
     * the parent spawns this plane child and its glow sub-children.
     */
    public void setGlowChildren(AizIntroEmeraldGlowChild glow1, AizIntroEmeraldGlowChild glow2) {
        this.glowChild1 = glow1;
        this.glowChild2 = glow2;
    }

    public boolean isSpiraling() {
        return spiraling;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    public void setMappingFrame(int mappingFrame) {
        this.mappingFrame = mappingFrame;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering deferred to integration task (Task 13).
    }
}
