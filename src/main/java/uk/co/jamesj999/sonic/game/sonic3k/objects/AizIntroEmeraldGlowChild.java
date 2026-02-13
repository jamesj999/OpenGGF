package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.objects.AbstractObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Emerald glow child for the AIZ1 intro plane.
 * ROM: loc_67824 / loc_67862 (sonic3k.asm)
 *
 * Two instances are spawned by the plane child to provide a glowing
 * visual effect on the plane's emeralds. Each follows the parent plane
 * with a fixed (x, y) offset and self-destructs when the parent is destroyed.
 */
public class AizIntroEmeraldGlowChild extends AbstractObjectInstance {

    private final AizIntroPlaneChild parent;
    private final int xOffset;
    private final int yOffset;

    /**
     * @param spawn   spawn data (position is overridden by parent tracking)
     * @param parent  plane child to follow
     * @param xOffset horizontal offset from parent position
     * @param yOffset vertical offset from parent position
     */
    public AizIntroEmeraldGlowChild(ObjectSpawn spawn, AizIntroPlaneChild parent,
                                     int xOffset, int yOffset) {
        super(spawn, "AIZEmeraldGlow");
        this.parent = parent;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    @Override
    public int getX() {
        return parent.getX() + xOffset;
    }

    @Override
    public int getY() {
        return parent.getY() + yOffset;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Follow parent automatically via getX()/getY().
        // Self-destruct when parent plane is destroyed.
        if (parent.isDestroyed()) {
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Rendering deferred to integration task (Task 13).
    }
}
