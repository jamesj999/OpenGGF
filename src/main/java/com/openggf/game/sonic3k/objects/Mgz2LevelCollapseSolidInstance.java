package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Invisible carrier solid for the MGZ2 boss-floor collapse.
 *
 * <p>ROM: Obj_MGZ2LevelCollapseSolid. The screen event clears the real level
 * chunks, then creates 20 of these full-solid objects so Sonic can stand on the
 * visually deforming floor columns while they drop away.
 */
public final class Mgz2LevelCollapseSolidInstance extends AbstractObjectInstance
        implements SolidObjectProvider {

    private static final int OBJECT_ID = 0xFF;
    private static final int HALF_WIDTH = 0x1B;
    private static final int HALF_HEIGHT = 0x40;

    private final int anchorX;
    private final int baseY;
    private final IntSupplier scrollSupplier;
    private final BooleanSupplier deleteSupplier;

    public Mgz2LevelCollapseSolidInstance(int anchorX, int baseY,
                                          IntSupplier scrollSupplier,
                                          BooleanSupplier deleteSupplier) {
        super(new ObjectSpawn(anchorX, baseY, OBJECT_ID, 0, 0, false, 0),
                "MGZ2LevelCollapseSolid");
        this.anchorX = anchorX;
        this.baseY = baseY;
        this.scrollSupplier = scrollSupplier;
        this.deleteSupplier = deleteSupplier;
        updateDynamicSpawn(anchorX, baseY);
    }

    @Override
    public int getX() {
        return anchorX;
    }

    @Override
    public int getY() {
        return baseY + scrollSupplier.getAsInt();
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (deleteSupplier.getAsBoolean()) {
            setDestroyed(true);
            return;
        }
        updateDynamicSpawn(anchorX, getY());
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(HALF_WIDTH, HALF_HEIGHT, HALF_HEIGHT);
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // ROM sets the invisible status bit; collision only.
    }

    @Override
    public boolean isHighPriority() {
        return false;
    }
}
