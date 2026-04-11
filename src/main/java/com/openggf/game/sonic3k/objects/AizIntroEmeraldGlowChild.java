package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Emerald glow child for the AIZ1 intro plane.
 * ROM: loc_67824 / loc_67862 (sonic3k.asm)
 *
 * Two instances are spawned by the plane child to provide a glowing
 * visual effect on the plane's emeralds. Each follows the parent plane
 * with a fixed (x, y) offset and self-destructs when the parent is destroyed.
 */
public class AizIntroEmeraldGlowChild extends AbstractObjectInstance {

    private static final Logger LOG = Logger.getLogger(AizIntroEmeraldGlowChild.class.getName());

    private final AizIntroPlaneChild parent;
    private final int xOffset;
    private final int yOffset;

    private static final int[] GLOW_FRAMES = {0, 5, 6};
    private static final int ANIM_FRAME_DURATION = 3;
    private int animTimer;
    private int animIndex;

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
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        animTimer++;
        if (animTimer >= ANIM_FRAME_DURATION) {
            animTimer = 0;
            animIndex++;
            if (animIndex >= GLOW_FRAMES.length) {
                animIndex = 0;
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getEmeraldRenderer(services());
        if (renderer == null || !renderer.isReady()) return;
        // Screen-space coordinates use the ROM +128 sprite-table bias.
        int renderX = getX();
        int renderY = getY();
        try {
            Camera camera = services().camera();
            renderX += camera.getX() - 128;
            renderY += camera.getY() - 128;
        } catch (Exception e) {
            LOG.fine(() -> "AizIntroEmeraldGlowChild.appendRenderCommands: " + e.getMessage());
        }
        renderer.drawFrameIndex(GLOW_FRAMES[animIndex], renderX, renderY, false, false);
    }
}
