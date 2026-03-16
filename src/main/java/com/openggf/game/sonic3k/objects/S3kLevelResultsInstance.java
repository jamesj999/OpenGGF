package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Stub results screen object for S3K end-of-act transitions.
 *
 * <p>ROM: Obj_ResultsScreen (sonic3k.asm) — displays score tally after the
 * signpost lands and the player touches the ground. This is a minimal stub
 * that counts down a timer, then signals the act transition.
 *
 * <p>When the timer expires:
 * <ul>
 *   <li>Clears {@code endOfLevelActive} (results screen done)</li>
 *   <li>Sets {@code endOfLevelFlag} (trigger act transition)</li>
 *   <li>Self-destructs</li>
 * </ul>
 */
public class S3kLevelResultsInstance extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(S3kLevelResultsInstance.class.getName());

    private static final int RESULTS_TIMER = 60;

    private int timer;

    public S3kLevelResultsInstance() {
        super(null, "S3kLevelResults");
        this.timer = RESULTS_TIMER;
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        timer--;
        if (timer <= 0) {
            GameServices.gameState().setEndOfLevelActive(false);
            GameServices.gameState().setEndOfLevelFlag(true);
            setDestroyed(true);
            LOG.fine("S3K results stub complete — act transition triggered");
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Stub: no visual rendering yet.
    }
}
