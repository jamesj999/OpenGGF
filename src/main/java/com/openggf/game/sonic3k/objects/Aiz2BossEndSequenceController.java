package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ2 post-boss controller for the Sonic/Tails route.
 *
 * <p>ROM reference: loc_694D4 onward.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Wait for egg capsule release (results screen finished)</li>
 *   <li>Play level music, force Sonic right until X &ge; stop coordinate</li>
 *   <li>Stop Sonic, spawn cutscene Knuckles</li>
 *   <li>Wait for Knuckles to finish his laugh/jump/button sequence</li>
 *   <li>Bridge collapses, Sonic falls in hurt animation</li>
 *   <li>Transition to HCZ when Sonic falls past Y threshold</li>
 * </ol>
 */
public class Aiz2BossEndSequenceController extends AbstractObjectInstance {

    // ROM: Camera_stored_max_X_pos = _unkFA84 + $158
    private static final int MAX_X_TARGET_OFFSET = 0x158;
    // ROM: loc_69526 — stop walking when x_pos >= _unkFA84 + $1F8
    private static final int PLAYER_STOP_X_OFFSET = 0x1F8;
    // ROM: loc_695A8 — transition when y_pos >= _unkFA86 + $1E6
    private static final int NEXT_LEVEL_Y_OFFSET = 0x1E6;

    private final int arenaMaxX;
    private final int arenaBaseY;
    private boolean initialized;
    private boolean postCapsuleSequenceStarted;
    private boolean knucklesSpawned;
    private boolean buttonHandled;
    private boolean transitionRequested;

    public Aiz2BossEndSequenceController(int arenaMaxX, int arenaBaseY) {
        super(new ObjectSpawn(arenaMaxX, arenaBaseY, Sonic3kObjectIds.EGG_CAPSULE, 0, 0, false, 0),
                "AIZ2BossEndSequence");
        this.arenaMaxX = arenaMaxX;
        this.arenaBaseY = arenaBaseY;
    }

    @Override
    public int getX() {
        return arenaMaxX;
    }

    @Override
    public int getY() {
        return arenaBaseY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }

        if (!initialized) {
            initialize(player);
        }

        // Wait for results screen to finish (egg capsule sets this flag)
        if (!Aiz2BossEndSequenceState.isEggCapsuleReleased()) {
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            return;
        }

        // Start post-capsule sequence (music + walk right)
        if (!postCapsuleSequenceStarted) {
            startPostCapsuleSequence(player);
        }

        // Phase: Walk right until reaching stop coordinate
        if (!knucklesSpawned) {
            int stopX = arenaMaxX + PLAYER_STOP_X_OFFSET;
            if (player.getCentreX() < stopX) {
                // ROM: loc_69526 — force right until x_pos >= threshold
                player.setControlLocked(true);
                player.clearForcedInputMask();
                player.setForceInputRight(true);
                setSidekickControlLocked(true);
                return;
            }

            // ROM: loc_69546 — Stop_Object and spawn Knuckles
            knucklesSpawned = true;
            player.setControlLocked(true);
            player.setForceInputRight(false);
            player.clearForcedInputMask();
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            // ROM: force look-up input while waiting for Knuckles
            player.setForcedInputMask(AbstractPlayableSprite.INPUT_UP);
            setSidekickControlLocked(true);
            spawnDynamicObject(CutsceneKnucklesAiz2Instance.createDefault());
        }

        // Phase: Wait for button press (triggered by Knuckles animation)
        if (!buttonHandled && Aiz2BossEndSequenceState.isButtonPressed()) {
            buttonHandled = true;
            // ROM: Player controls stay locked — bridge collapse handles the fall.
            // Set hurt/falling animation on player.
            player.clearForcedInputMask();
            player.setForceInputRight(false);
            player.setAnimationId(Sonic3kAnimationIds.HURT_FALL);
            player.setControlLocked(true);
            services().camera().setMaxYTarget((short) 0x1000);
        }

        // Phase: Wait for player to fall past Y threshold, then transition
        if (buttonHandled && !transitionRequested) {
            int transitionY = arenaBaseY + NEXT_LEVEL_Y_OFFSET;
            if ((player.getY() & 0xFFFF) >= transitionY) {
                transitionRequested = true;
                services().requestZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0);
            }
        }
    }

    private void initialize(AbstractPlayableSprite player) {
        initialized = true;
        Aiz2BossEndSequenceState.triggerBridgeDrop();
        player.clearForcedInputMask();
        player.setForceInputRight(false);
    }

    private void startPostCapsuleSequence(AbstractPlayableSprite player) {
        postCapsuleSequenceStarted = true;
        services().camera().setMaxXTarget((short) (arenaMaxX + MAX_X_TARGET_OFFSET));
        player.setControlLocked(true);
        player.clearForcedInputMask();
        player.setForceInputRight(true);
        setSidekickControlLocked(true);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }

    private void setSidekickControlLocked(boolean locked) {
        for (PlayableEntity sidekick : services().sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                sprite.setControlLocked(locked);
                if (!locked) {
                    sprite.clearForcedInputMask();
                }
            }
        }
    }
}
