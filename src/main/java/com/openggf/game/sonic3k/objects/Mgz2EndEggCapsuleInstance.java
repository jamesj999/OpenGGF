package com.openggf.game.sonic3k.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.Tails;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;

/**
 * MGZ2 floating end capsule.
 *
 * <p>ROM: {@code Obj_EggCapsule} enters routine 8 when render flag bit 1 is
 * set. MGZ reuses that floating capsule path, then waits for level results to
 * finish before running {@code loc_6D104}, the MGZ-to-CNZ palette fade.
 */
public class Mgz2EndEggCapsuleInstance extends AbstractS3kFloatingEndEggCapsuleInstance {

    public Mgz2EndEggCapsuleInstance(int initialX, int initialY) {
        super(initialX, initialY, "MGZ2EndEggCapsule");
    }

    public static Mgz2EndEggCapsuleInstance createForCamera(int cameraX, int cameraY) {
        return new Mgz2EndEggCapsuleInstance(cameraX + X_OFFSET, cameraY + Y_START_OFFSET);
    }

    @Override
    protected boolean shouldStartResults(AbstractPlayableSprite player) {
        if (player.getDead() || player.isDrowningPreDeath() || player.getDeathCountdown() > 0) {
            return false;
        }
        if (player.hasRenderFlagOnScreenState() && !player.isRenderFlagOnScreen()) {
            return false;
        }
        return isTails(player) || isSonicActivelyCarriedByTails();
    }

    private boolean isTails(AbstractPlayableSprite player) {
        return player instanceof Tails || "tails".equalsIgnoreCase(player.getCode());
    }

    private boolean isSonicActivelyCarriedByTails() {
        for (var sidekickEntity : services().sidekicks()) {
            if (sidekickEntity instanceof AbstractPlayableSprite sidekick && isTails(sidekick)) {
                SidekickCpuController controller = sidekick.getCpuController();
                if (controller != null && controller.isFlyingCarrying()) {
                    return true;
                }
            }
        }
        return false;
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
