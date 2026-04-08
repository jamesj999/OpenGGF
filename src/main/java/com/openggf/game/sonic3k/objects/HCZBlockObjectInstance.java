package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Object 0x40 - HCZ Block (Hydrocity Zone).
 * <p>
 * Static full-solid block with four ROM-defined width variants selected directly
 * by subtype. The block uses HCZ level art at {@code ArtTile_HCZMisc + $A} with
 * palette line 2 and otherwise just delegates collision to the generic solid-object
 * pipeline.
 * <p>
 * ROM references: Obj_HCZBlock (sonic3k.asm:43233), byte_1F38A, Map_HCZBlock.
 */
public class HCZBlockObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SolidObjectListener {

    private static final String ART_KEY = Sonic3kObjectArtKeys.HCZ_BLOCK;
    private static final int PRIORITY = 5; // ROM: move.w #$280,priority(a0)

    // byte_1F38A: {halfWidth, halfHeight}
    private static final int[][] SIZE_TABLE = {
            {0x10, 0x10},
            {0x20, 0x10},
            {0x30, 0x10},
            {0x40, 0x10}
    };

    private final int x;
    private final int y;
    private final int mappingFrame;
    private final int halfWidth;
    private final int halfHeight;

    public HCZBlockObjectInstance(ObjectSpawn spawn) {
        super(spawn, "HCZBlock");
        this.x = spawn.x();
        this.y = spawn.y();

        int sizeIndex = Math.min(spawn.subtype() & 0xFF, SIZE_TABLE.length - 1);
        this.mappingFrame = sizeIndex;
        this.halfWidth = SIZE_TABLE[sizeIndex][0];
        this.halfHeight = SIZE_TABLE[sizeIndex][1];
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(halfWidth + 0x0B, halfHeight, halfHeight + 1);
    }

    @Override
    public int getTopLandingHalfWidth(PlayableEntity playerEntity, int collisionHalfWidth) {
        // ROM: Solid_Landed uses width_pixels for ridden top-surface retention, not d1.
        return halfWidth;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        // ROM has no per-contact behavior beyond SolidObjectFull2.
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ART_KEY);
        if (renderer != null) {
            renderer.drawFrameIndex(mappingFrame, x, y, false, false);
        }
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(PRIORITY);
    }
}
