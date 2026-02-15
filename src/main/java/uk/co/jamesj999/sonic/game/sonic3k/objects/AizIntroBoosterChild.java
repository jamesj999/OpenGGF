package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Booster flame sub-child for the AIZ1 intro plane.
 * ROM: loc_45C00 and loc_45C3E (s3.asm).
 *
 * Two instances follow the plane child at fixed offsets, cycling through
 * animation frames using Animate_RawNoSST (simple frame cycling with loop).
 *
 * Booster 1: offset (+0x38, +4), frames {2, 3, 4, 3, 2} (byte_45E6B)
 * Booster 2: offset (+0x18, +0x18), frames {5, 6} (byte_45E73)
 *
 * Not spawned as a dynamic object — the plane child manages update/render directly.
 */
public class AizIntroBoosterChild {

    private static final int ANIM_FRAME_DURATION = 3;

    private final AizIntroPlaneChild parent;
    private final int xOffset;
    private final int yOffset;
    private final int[] animSequence;

    private int currentX;
    private int currentY;
    private int animTimer;
    private int animIndex;

    public AizIntroBoosterChild(AizIntroPlaneChild parent, int xOffset, int yOffset, int[] animSequence) {
        this.parent = parent;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.animSequence = animSequence;
        this.animTimer = 0;
        this.animIndex = 0;
    }

    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Follow parent plane position with fixed offset
        currentX = parent.getX() + xOffset;
        currentY = parent.getY() + yOffset;

        // Animate_RawNoSST: cycle through frames, loop at end
        animTimer++;
        if (animTimer >= ANIM_FRAME_DURATION) {
            animTimer = 0;
            animIndex++;
            if (animIndex >= animSequence.length) {
                animIndex = 0;
            }
        }
    }

    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = AizIntroArtLoader.getPlaneRenderer();
        if (renderer == null || !renderer.isReady()) return;
        int frame = animSequence[animIndex];
        // Screen-space coordinates use the ROM +128 sprite-table bias.
        int renderX = currentX;
        int renderY = currentY;
        try {
            Camera camera = Camera.getInstance();
            renderX += camera.getX() - 128;
            renderY += camera.getY() - 128;
        } catch (Exception ignored) {}
        renderer.drawFrameIndex(frame, renderX, renderY, false, false);
    }

    public int getCurrentX() {
        return currentX;
    }

    public int getCurrentY() {
        return currentY;
    }

    public int getAnimFrame() {
        return animSequence[animIndex];
    }
}
