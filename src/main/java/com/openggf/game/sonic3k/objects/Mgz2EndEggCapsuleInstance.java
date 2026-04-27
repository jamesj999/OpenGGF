package com.openggf.game.sonic3k.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/**
 * MGZ2 floating end capsule.
 *
 * <p>ROM: {@code Obj_EggCapsule} enters routine 8 when render flag bit 1 is
 * set. MGZ reuses that floating capsule path, then waits for level results to
 * finish before running {@code loc_6D104}, the MGZ-to-CNZ palette fade.
 */
public class Mgz2EndEggCapsuleInstance extends Aiz2EndEggCapsuleInstance {

    public Mgz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(initialX, initialY);
    }

    public static Mgz2EndEggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new Mgz2EndEggCapsuleInstance(cameraX + 0xA0, cameraY - 0x40);
    }

    @Override
    protected boolean shouldStartResults(AbstractPlayableSprite player) {
        return true;
    }

    @Override
    protected boolean shouldLockPlayersForResults() {
        return false;
    }

    @Override
    protected AbstractObjectInstance createResultsScreen() {
        return new Mgz2ResultsScreenObjectInstance(getPlayerCharacter(), services().currentAct());
    }

    @Override
    protected AbstractObjectInstance createCapsuleAnimal(ObjectSpawn spawn, int delay, int artVariant, int index) {
        return new Mgz2CapsuleAnimalInstance(spawn, delay, artVariant, index);
    }

    @Override
    protected void onResultsComplete() {
        // MGZ keeps the original boss-side waiter alive for the post-results
        // loc_6D104 fade. The capsule only opens and starts the results screen.
    }
}
