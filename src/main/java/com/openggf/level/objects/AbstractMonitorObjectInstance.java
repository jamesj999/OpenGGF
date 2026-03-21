package com.openggf.level.objects;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Shared base class for monitor (item box) objects across all games.
 * <p>
 * Encapsulates the icon-rise physics state machine that is identical across
 * S1, S2, and S3K: the icon rises with initial velocity {@code -0x300},
 * decelerates at {@code +0x18} per frame, waits {@code 0x1D} frames at
 * the apex (applying the power-up effect), then self-destructs.
 * <p>
 * Subclasses provide game-specific power-up logic via {@link #applyPowerup}.
 * <p>
 * ROM references: Pow_Move / Pow_ChkX (all three games share this code).
 */
public abstract class AbstractMonitorObjectInstance extends AbstractObjectInstance {

    // From disassembly: Pow_Main sets obVelY = -$300
    protected static final int ICON_INITIAL_VELOCITY = -0x300;

    // From disassembly: Pow_Move adds $18 to obVelY per frame
    protected static final int ICON_RISE_ACCEL = 0x18;

    // From disassembly: Pow_Move sets timer to $1D (29 frames)
    protected static final int ICON_WAIT_FRAMES = 0x1D;

    // Icon rising state (PowerUp object in disassembly)
    protected boolean iconActive;
    protected int iconSubY;
    protected int iconVelY;
    protected int iconWaitFrames;
    protected boolean effectApplied;
    protected AbstractPlayableSprite effectTarget;

    protected AbstractMonitorObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
    }

    /**
     * Starts the icon-rise sequence. Call from {@code breakMonitor()} in subclasses
     * after setting the monitor to broken state.
     *
     * @param originY the Y position (pixels) from which the icon rises
     * @param player  the player who broke the monitor (receives the power-up)
     */
    protected void startIconRise(int originY, AbstractPlayableSprite player) {
        iconActive = true;
        iconSubY = originY << 8;
        iconVelY = ICON_INITIAL_VELOCITY;
        iconWaitFrames = 0;
        effectApplied = false;
        effectTarget = player;
    }

    /**
     * Update the rising icon and apply power-up effect when it reaches the apex.
     * <p>
     * State machine: rise (velocity &lt; 0) -> apply effect -> wait -> deactivate.
     * <p>
     * ROM: Pow_Move, Pow_ChkX — identical across S1, S2, and S3K.
     */
    protected void updateIcon() {
        if (!iconActive) {
            return;
        }
        if (iconVelY < 0) {
            // Rising phase: apply velocity and deceleration
            iconSubY += iconVelY;
            iconVelY += ICON_RISE_ACCEL;
            if (iconVelY >= 0) {
                iconVelY = 0;
                iconWaitFrames = ICON_WAIT_FRAMES;
                if (!effectApplied && effectTarget != null) {
                    applyPowerup(effectTarget);
                    effectApplied = true;
                    effectTarget = null;
                }
            }
            return;
        }

        // Waiting phase: count down then deactivate icon
        if (iconWaitFrames > 0) {
            iconWaitFrames--;
            return;
        }
        iconActive = false;
        onIconDeactivated();
    }

    /**
     * Apply the game-specific power-up effect to the player.
     * Called exactly once when the icon reaches its apex.
     *
     * @param player the player who broke the monitor
     */
    protected abstract void applyPowerup(AbstractPlayableSprite player);

    /**
     * Hook called when the icon finishes its wait timer and deactivates.
     * Subclasses can override to clear rendering references (e.g. iconPlayer).
     * Default implementation does nothing.
     */
    protected void onIconDeactivated() {
        // Default no-op; subclasses may override.
    }
}
