package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Object 0xEA - Pachinko platform.
 *
 * <p>ROM reference: {@code Obj_Pachinko_Platform}. Static top-solid platform using
 * {@code SolidObjectTop} with D1 = width_pixels + $0B, D2 = height_pixels,
 * D3 = height_pixels + 1.
 */
public class PachinkoPlatformObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider {

    private static final SolidObjectParams SOLID_PARAMS = new SolidObjectParams(0x2B, 0x0C, 0x0D);

    public PachinkoPlatformObjectInstance(ObjectSpawn spawn) {
        super(spawn, "PachinkoPlatform");
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        // Static top-solid platform.
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return SOLID_PARAMS;
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.PACHINKO_PLATFORM);
        if (renderer == null) {
            return;
        }
        boolean hFlip = (spawn.renderFlags() & 0x1) != 0;
        boolean vFlip = (spawn.renderFlags() & 0x2) != 0;
        renderer.drawFrameIndex(0, spawn.x(), spawn.y(), hFlip, vFlip);
    }
}
