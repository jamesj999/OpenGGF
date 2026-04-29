package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.EggPrisonAnimalInstance;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Floating upside-down egg prison used by the AIZ2 post-boss cutscene.
 */
public class Aiz2EndEggCapsuleInstance extends AbstractS3kFloatingEndEggCapsuleInstance {
    public Aiz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(initialX, initialY, "AIZ2EndEggCapsule");
    }

    public static Aiz2EndEggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new Aiz2EndEggCapsuleInstance(cameraX + X_OFFSET, cameraY + Y_START_OFFSET);
    }

    @Override
    protected AbstractObjectInstance createCapsuleAnimal(ObjectSpawn spawn, int delay, int artVariant, int index) {
        return new HighPriorityAnimal(spawn, delay, artVariant);
    }

    @Override
    protected void onResultsComplete() {
        Aiz2BossEndSequenceState.releaseEggCapsule();
        // ROM: The capsule stays visible while Sonic walks right and Knuckles
        // does his cutscene. It leaves only when camera scroll or zone transition
        // removes it from the active scene.
    }

    private static final class HighPriorityAnimal extends EggPrisonAnimalInstance {
        HighPriorityAnimal(ObjectSpawn spawn, int delay, int artVariant) {
            super(spawn, delay, artVariant);
        }

        @Override
        public boolean isHighPriority() {
            return true;
        }
    }
}
