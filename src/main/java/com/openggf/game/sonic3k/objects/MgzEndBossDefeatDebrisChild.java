package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Three flying MGZ end-boss fragments spawned at defeat.
 *
 * <p>ROM: {@code ChildObjDat_6D822 -> loc_6CFBE}. The child uses
 * {@code Map_MGZEndBoss} frames $2E-$30 and {@code Set_IndexedVelocity}
 * starting at velocity index $28.
 */
public class MgzEndBossDefeatDebrisChild extends AbstractObjectInstance {
    private static final int[][] OFFSETS = {
            {0x0C, -0x14},
            {-0x10, 0x08},
            {0x14, 0x08},
    };
    private static final int[][] VELOCITIES = {
            {0x300, -0x300},
            {-0x400, -0x300},
            {0x400, -0x300},
    };
    private static final int FIRST_FRAME = 0x2E;
    private static final int SUBPIXEL_SHIFT = 8;
    private static final int OFFSCREEN_MARGIN = 0x40;

    private final int index;
    private final boolean flipX;
    private int xFixed;
    private int yFixed;
    private final int xVel;
    private final int yVel;

    public MgzEndBossDefeatDebrisChild(int parentX, int parentY, int index, boolean flipX) {
        super(new ObjectSpawn(
                parentX + renderOffsetX(OFFSETS[index][0], flipX),
                parentY + OFFSETS[index][1],
                0, index, 0, false, 0),
                "MGZEndBossDefeatDebris");
        this.index = index;
        this.flipX = flipX;
        this.xFixed = spawn.x() << SUBPIXEL_SHIFT;
        this.yFixed = spawn.y() << SUBPIXEL_SHIFT;
        this.xVel = renderOffsetX(VELOCITIES[index][0], flipX);
        this.yVel = VELOCITIES[index][1];
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        xFixed += xVel;
        yFixed += yVel;
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();
        int x = getX();
        int y = getY();
        if (x < cameraX - OFFSCREEN_MARGIN || x > cameraX + 320 + OFFSCREEN_MARGIN
                || y < cameraY - OFFSCREEN_MARGIN || y > cameraY + 224 + OFFSCREEN_MARGIN) {
            setDestroyed(true);
        }
    }

    @Override
    public int getX() {
        return xFixed >> SUBPIXEL_SHIFT;
    }

    @Override
    public int getY() {
        return yFixed >> SUBPIXEL_SHIFT;
    }

    @Override
    public int getPriorityBucket() {
        return index == 0 ? 6 : 4;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.MGZ_ENDBOSS);
        if (renderer != null) {
            renderer.drawFrameIndex(FIRST_FRAME + index, getX(), getY(), flipX, false);
        }
    }

    private static int renderOffsetX(int value, boolean flipX) {
        return flipX ? -value : value;
    }
}
