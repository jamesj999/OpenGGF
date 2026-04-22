package com.openggf.game.sonic1.objects;

import com.openggf.game.GameStateManager;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sonic 1 badnik replacement explosion.
 * <p>
 * ROM {@code ExplosionItem} keeps the destroyed badnik's slot, then spawns the
 * animal from its first routine-0 update. The points popup is allocated after
 * the animal, preserving the same FindFreeObj ordering.
 */
public class Sonic1ExplosionItemObjectInstance extends ExplosionObjectInstance {
    private final int pointsValue;
    private boolean spawnedChildren;

    public Sonic1ExplosionItemObjectInstance(int x, int y, ObjectServices services, int pointsValue) {
        super(0x27, x, y, services != null ? services.renderManager() : null);
        this.pointsValue = pointsValue;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (!spawnedChildren) {
            spawnedChildren = true;
            spawnChildren();
        }
        super.update(frameCounter, player);
    }

    private void spawnChildren() {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return;
        }
        ObjectManager objectManager = svc.objectManager();
        if (objectManager == null) {
            return;
        }

        int x = spawn.x();
        int y = spawn.y();
        // ROM parity: ExplosionItem allocates these children into SST slots in
        // the same frame, but they do not execute until the next frame.
        objectManager.addDynamicObjectNextFrame(new Sonic1AnimalsObjectInstance(
                new ObjectSpawn(x, y, 0x28, 0, 0, false, 0)));

        GameStateManager gameState = svc.gameState();
        if (gameState != null && gameState.isBossFightActive()) {
            return;
        }
        if (svc.renderManager() == null) {
            return;
        }
        objectManager.addDynamicObjectNextFrame(new Sonic1PointsObjectInstance(
                new ObjectSpawn(x, y, 0x29, 0, 0, false, 0), svc, pointsValue));
    }
}
