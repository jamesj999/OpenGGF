package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss napalm controller child (Knuckles-only).
 *
 * ROM: When character_id == 2 (Knuckles), the boss sets bit 1 of $38,
 * which activates this controller to spawn napalm projectiles.
 * For Sonic/Tails, bit 1 is never set so this stays permanently idle.
 *
 * State machine: IDLE -> DELAY -> FIRE -> IDLE (loops while bit is set)
 */
public class AizMinibossNapalmController extends AbstractBossChild {
    private static final int FLAG_PARENT_BITS = 0x38;
    private static final int NAPALM_ACTIVATE_BIT = 1 << 4; // bit 4 (dedicated napalm bit)

    /** Base delay before firing, scaled by subtype. */
    private static final int BASE_DELAY = 0x20;

    private enum State {
        IDLE,
        DELAY,
        FIRE
    }

    private final int subtype;
    private State state = State.IDLE;
    private int timer;

    /**
     * @param parent  the AIZ miniboss parent
     * @param subtype delay multiplier (0 = base delay, higher = longer)
     */
    public AizMinibossNapalmController(AbstractBossInstance parent, int subtype) {
        super(parent, "AIZMinibossNapalm", 4, 0x91);
        this.subtype = subtype;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }

        syncPositionWithParent();

        if (parent == null || parent.getState().defeated) {
            state = State.IDLE;
            updateDynamicSpawn();
            return;
        }

        switch (state) {
            case IDLE -> updateIdle();
            case DELAY -> updateDelay();
            case FIRE -> updateFire();
        }

        updateDynamicSpawn();
    }

    private void updateIdle() {
        int flags = parent.getCustomFlag(FLAG_PARENT_BITS);
        if ((flags & NAPALM_ACTIVATE_BIT) == 0) {
            return;
        }
        // Clear the activation bit on parent
        parent.setCustomFlag(FLAG_PARENT_BITS, flags & ~NAPALM_ACTIVATE_BIT);
        timer = BASE_DELAY + (subtype * BASE_DELAY);
        state = State.DELAY;
    }

    private void updateDelay() {
        timer--;
        if (timer <= 0) {
            state = State.FIRE;
        }
    }

    private void updateFire() {
        AizMinibossNapalmProjectile projectile = new AizMinibossNapalmProjectile(
                currentX, currentY);
        spawnDynamicObject(projectile);
        services().playSfx(Sonic3kSfx.PROJECTILE.id);
        state = State.IDLE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Controller is invisible — no rendering needed.
    }
}
