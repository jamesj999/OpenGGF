package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.physics.SwingMotion;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Plane child sprite for AIZ1 intro - the biplane that Sonic rides.
 * ROM: loc_45B08 (s3.asm) — child of Obj_intPlane.
 *
 * Uses Map_AIZIntroPlane / ArtTile_AIZIntroPlane.
 * Normal mode follows parent position with an offset and applies swing
 * oscillation to Y (shared Swing_UpAndDown parameters with parent).
 * After parent detaches (routine 8), swings independently.
 * When parent signals walk-left (routine 0xA), walks left and self-deletes at x<0x20.
 *
 * Spawns 2 AizIntroBoosterChild sub-objects for the booster flame animation.
 */
public class AizIntroPlaneChild extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(AizIntroPlaneChild.class.getName());

    // Swing parameters from ROM (Swing_UpAndDown acceleration / max)
    private static final int SWING_ACCELERATION = 0x10;
    private static final int SWING_MAX_VELOCITY = 0xC0;

    // Offset from parent position (plane drawn relative to intro object)
    private static final int PARENT_X_OFFSET = -0x22;
    private static final int PARENT_Y_OFFSET = 0x2C;

    /** X threshold below which walk-left self-deletes. */
    private static final int DELETE_X = 0x20;

    private final AizPlaneIntroInstance parent;
    private int currentX;
    private int currentY;
    private int swingVelocity;
    private boolean swingDirectionDown;
    private int mappingFrame;

    /** Fractional Y accumulator for subpixel swing tracking. */
    private int ySub;

    // Emerald glow children (spawned during init, follow this plane)
    private AizIntroEmeraldGlowChild glowChild1;
    private AizIntroEmeraldGlowChild glowChild2;

    // Booster flame children
    private AizIntroBoosterChild booster1;
    private AizIntroBoosterChild booster2;

    public AizIntroPlaneChild(ObjectSpawn spawn, AizPlaneIntroInstance parent) {
        super(spawn, "AIZIntroPlane");
        this.parent = parent;
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.ySub = 0;

        // Spawn booster flame children
        spawnBoosters();
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

        boolean detached = parent.isPlaneDetached();
        boolean walkLeft = parent.isPlaneShouldWalkLeft();

        if (walkLeft) {
            // Walk left mode: move left 4px/frame, continue swinging
            currentX -= 4;

            // Continue swing oscillation
            SwingMotion.Result result = SwingMotion.update(
                    SWING_ACCELERATION, swingVelocity, SWING_MAX_VELOCITY, swingDirectionDown);
            swingVelocity = result.velocity();
            swingDirectionDown = result.directionDown();

            // Subpixel Y accumulation for swing
            int yTotal = (ySub & 0xFF) + (swingVelocity & 0xFF);
            currentY += (swingVelocity >> 8) + (yTotal >> 8);
            ySub = yTotal & 0xFF;

            // Self-delete when walked off-screen
            if (currentX < DELETE_X) {
                LOG.fine("Plane child: walked off-screen left, destroying");
                setDestroyed(true);
            }
        } else if (detached) {
            // Detached mode: swing independently, don't follow parent
            SwingMotion.Result result = SwingMotion.update(
                    SWING_ACCELERATION, swingVelocity, SWING_MAX_VELOCITY, swingDirectionDown);
            swingVelocity = result.velocity();
            swingDirectionDown = result.directionDown();

            // Subpixel Y accumulation for swing
            int yTotal = (ySub & 0xFF) + (swingVelocity & 0xFF);
            currentY += (swingVelocity >> 8) + (yTotal >> 8);
            ySub = yTotal & 0xFF;
        } else {
            // Normal mode: follow parent position with offset
            currentX = parent.getX() + PARENT_X_OFFSET;
            currentY = parent.getY() + PARENT_Y_OFFSET;

            // Apply swing oscillation to Y
            SwingMotion.Result result = SwingMotion.update(
                    SWING_ACCELERATION, swingVelocity, SWING_MAX_VELOCITY, swingDirectionDown);
            swingVelocity = result.velocity();
            swingDirectionDown = result.directionDown();

            // Subpixel Y accumulation for swing
            int yTotal = (ySub & 0xFF) + (swingVelocity & 0xFF);
            currentY += (swingVelocity >> 8) + (yTotal >> 8);
            ySub = yTotal & 0xFF;
        }

        // Update booster flame children
        if (booster1 != null) {
            booster1.update(frameCounter, player);
        }
        if (booster2 != null) {
            booster2.update(frameCounter, player);
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
     * Spawns the two booster flame sub-children.
     * ROM: loc_45C00 booster at (+0x38,+4), loc_45C3E booster at (+0x18,+0x18).
     */
    private void spawnBoosters() {
        // Booster 1: animation sequence {2, 3, 4, 3, 2} (byte_45E6B)
        booster1 = new AizIntroBoosterChild(this, 0x38, 4,
                new int[]{2, 3, 4, 3, 2});

        // Booster 2: animation sequence {5, 6} (byte_45E73)
        booster2 = new AizIntroBoosterChild(this, 0x18, 0x18,
                new int[]{5, 6});
    }

    /**
     * Sets the two emerald glow children. Called during integration when
     * the parent spawns this plane child and its glow sub-children.
     */
    public void setGlowChildren(AizIntroEmeraldGlowChild glow1, AizIntroEmeraldGlowChild glow2) {
        this.glowChild1 = glow1;
        this.glowChild2 = glow2;
    }

    public int getMappingFrame() {
        return mappingFrame;
    }

    public void setMappingFrame(int mappingFrame) {
        this.mappingFrame = mappingFrame;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getPlaneRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);

        // Render booster flames
        if (booster1 != null) {
            booster1.appendRenderCommands(commands);
        }
        if (booster2 != null) {
            booster2.appendRenderCommands(commands);
        }
    }
}
