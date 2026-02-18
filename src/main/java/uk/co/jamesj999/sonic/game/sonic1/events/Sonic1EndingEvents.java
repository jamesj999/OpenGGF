package uk.co.jamesj999.sonic.game.sonic1.events;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1AnimationIds;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Sonic 1 ending sequence bootstrap events.
 * ROM: End_MoveSonic in sonic.asm (GM_Ending main loop).
 *
 * <p>This implements the transition choreography right after entering id_EndZ:
 * force Sonic to run left, switch to right at X < 0x90, then stop at X >= 0xA0.
 * The later Obj87/Obj88/Obj89 ending object chain is intentionally out of scope.
 */
class Sonic1EndingEvents extends Sonic1ZoneEvents {
    private static final int END_MOVE_RUN_LEFT = 0;
    private static final int END_MOVE_RUN_RIGHT = 2;
    private static final int END_MOVE_STOP = 4;
    private static final int END_MOVE_DONE = 6;

    private boolean bootstrapApplied;

    Sonic1EndingEvents(Camera camera) {
        super(camera);
    }

    @Override
    void init() {
        super.init();
        bootstrapApplied = false;
    }

    @Override
    void update(int act) {
        AbstractPlayableSprite player = camera.getFocusedSprite();
        if (player == null) {
            return;
        }

        if (!bootstrapApplied) {
            applyBootstrap(player);
            bootstrapApplied = true;
        }

        switch (eventRoutine) {
            case END_MOVE_RUN_LEFT -> updateRunLeft(player);
            case END_MOVE_RUN_RIGHT -> updateRunRight(player);
            case END_MOVE_STOP, END_MOVE_DONE -> updateStopped(player);
            default -> { }
        }
    }

    private void applyBootstrap(AbstractPlayableSprite player) {
        // ROM GM_Ending init: face left, lock controls, force LEFT input, inertia = -$800.
        player.setDirection(Direction.LEFT);
        player.setControlLocked(true);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_LEFT);
        player.setGSpeed((short) -0x800);
    }

    private void updateRunLeft(AbstractPlayableSprite player) {
        int playerX = player.getCentreX() & 0xFFFF;
        if (playerX >= 0x90) {
            return;
        }

        // ROM End_MoveSonic state 0 -> 2
        eventRoutine = END_MOVE_RUN_RIGHT;
        player.setControlLocked(true);
        player.setForcedInputMask(AbstractPlayableSprite.INPUT_RIGHT);
    }

    private void updateRunRight(AbstractPlayableSprite player) {
        int playerX = player.getCentreX() & 0xFFFF;
        if (playerX < 0xA0) {
            return;
        }

        // ROM End_MoveSonic state 2 -> 4:
        // stop movement and lock controls before handoff to ending object.
        eventRoutine = END_MOVE_STOP;
        player.clearForcedInputMask();
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setControlLocked(true);
        player.setAnimationId(Sonic1AnimationIds.WAIT);
        player.setAnimationFrameIndex(0);
    }

    private void updateStopped(AbstractPlayableSprite player) {
        // Hold at the ROM handoff point until Obj87 parity is implemented.
        player.setCentreX((short) 0x00A0);
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.clearForcedInputMask();
        player.setControlLocked(true);

        if (eventRoutine == END_MOVE_STOP) {
            eventRoutine = END_MOVE_DONE;
        }
    }
}
