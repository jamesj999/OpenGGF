package com.openggf.game.sonic1.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.List;

/**
 * Sonic 1 monitor contents object (ROM object $2E).
 * <p>
 * This is a real child allocation in the SST, separate from the broken monitor shell.
 * The first same-frame update consumes Pow_Main, then later updates perform the icon rise
 * and apply the monitor effect at the apex.
 */
public final class Sonic1MonitorPowerUpObjectInstance extends AbstractMonitorObjectInstance {
    private static final int ICON_FRAME_OFFSET = 2;

    private final int subtype;

    public Sonic1MonitorPowerUpObjectInstance(int x, int y, int subtype, PlayableEntity player) {
        super(new ObjectSpawn(x, y, Sonic1ObjectIds.POWER_UP, subtype, 0, false, 0), "PowerUp");
        this.subtype = subtype & 0xFF;
        startIconRise(y, player);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        updateDynamicSpawn(spawn.x(), iconSubY >> 8);
        updateIcon();
    }

    @Override
    protected void applyPowerup(PlayableEntity player) {
        Sonic1MonitorObjectInstance.applyMonitorPowerup(subtype, player, services());
    }

    @Override
    protected void onIconDeactivated() {
        setDestroyed(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getMonitorRenderer();
        ObjectSpriteSheet sheet = renderManager.getMonitorSheet();
        int frameIndex = subtype + ICON_FRAME_OFFSET;
        if (renderer == null || !renderer.isReady() || sheet == null
                || frameIndex < 0 || frameIndex >= sheet.getFrameCount()) {
            return;
        }
        SpriteMappingFrame frame = sheet.getFrame(frameIndex);
        if (frame == null || frame.pieces().isEmpty()) {
            return;
        }
        SpriteMappingPiece iconPiece = frame.pieces().get(0);
        renderer.drawPieces(List.of(iconPiece), spawn.x(), iconSubY >> 8, false, false);
    }
}
