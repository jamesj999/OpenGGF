package com.openggf.sprites.animation;

import com.openggf.game.AnimationId;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Chooses animation script IDs based on simple movement state.
 */
public class ScriptedVelocityAnimationProfile implements SpriteAnimationProfile {
    private int idleAnimId;
    private int walkAnimId;
    private int runAnimId;
    private int rollAnimId;
    private int roll2AnimId = -1;
    private int pushAnimId = -1;
    private int duckAnimId = -1;
    private int lookUpAnimId = -1;
    private int spindashAnimId = -1;
    private int springAnimId = -1;
    private int deathAnimId = -1;
    private int hurtAnimId = -1;
    private int skidAnimId = -1;
    private int slideAnimId = -1;
    private int drownAnimId = -1;
    private int airAnimId;
    // Balance animations (ROM s2.asm:36246-36373)
    private int balanceAnimId = -1;   // 0x06 - facing toward edge, safe distance
    private int balance2AnimId = -1;  // 0x0C - facing toward edge, closer to falling
    private int balance3AnimId = -1;  // 0x1D - facing away from edge, safe distance
    private int balance4AnimId = -1;  // 0x1E - facing away from edge, closer to falling
    private int runSpeedThreshold;
    private int walkSpeedThreshold;
    private int fallbackFrame;
    // S2 adjusts the angle by -1 for positive values before computing the slope frame
    // offset (subq.b #1,d0 at s2.asm:38080). S1 does not do this.
    private boolean anglePreAdjust;
    // S2 Super Run uses compact slope layout (lsr.b #1,d0 = d0/2), while S3K Super Run
    // uses standard run spacing (add.b d0,d0 = d0*2). ROM: s2.asm:38159 vs s3.asm:22323.
    private boolean compactSuperRunSlope;

    public ScriptedVelocityAnimationProfile() {
    }

    public ScriptedVelocityAnimationProfile setIdleAnimId(int idleAnimId) { this.idleAnimId = idleAnimId; return this; }
    public ScriptedVelocityAnimationProfile setIdleAnimId(AnimationId id) { return setIdleAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setWalkAnimId(int walkAnimId) { this.walkAnimId = walkAnimId; return this; }
    public ScriptedVelocityAnimationProfile setWalkAnimId(AnimationId id) { return setWalkAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setRunAnimId(int runAnimId) { this.runAnimId = runAnimId; return this; }
    public ScriptedVelocityAnimationProfile setRunAnimId(AnimationId id) { return setRunAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setRollAnimId(int rollAnimId) { this.rollAnimId = rollAnimId; return this; }
    public ScriptedVelocityAnimationProfile setRollAnimId(AnimationId id) { return setRollAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setRoll2AnimId(int roll2AnimId) { this.roll2AnimId = roll2AnimId; return this; }
    public ScriptedVelocityAnimationProfile setRoll2AnimId(AnimationId id) { return setRoll2AnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setPushAnimId(int pushAnimId) { this.pushAnimId = pushAnimId; return this; }
    public ScriptedVelocityAnimationProfile setPushAnimId(AnimationId id) { return setPushAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setDuckAnimId(int duckAnimId) { this.duckAnimId = duckAnimId; return this; }
    public ScriptedVelocityAnimationProfile setDuckAnimId(AnimationId id) { return setDuckAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setLookUpAnimId(int lookUpAnimId) { this.lookUpAnimId = lookUpAnimId; return this; }
    public ScriptedVelocityAnimationProfile setLookUpAnimId(AnimationId id) { return setLookUpAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setSpindashAnimId(int spindashAnimId) { this.spindashAnimId = spindashAnimId; return this; }
    public ScriptedVelocityAnimationProfile setSpindashAnimId(AnimationId id) { return setSpindashAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setSpringAnimId(int springAnimId) { this.springAnimId = springAnimId; return this; }
    public ScriptedVelocityAnimationProfile setSpringAnimId(AnimationId id) { return setSpringAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setDeathAnimId(int deathAnimId) { this.deathAnimId = deathAnimId; return this; }
    public ScriptedVelocityAnimationProfile setDeathAnimId(AnimationId id) { return setDeathAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setHurtAnimId(int hurtAnimId) { this.hurtAnimId = hurtAnimId; return this; }
    public ScriptedVelocityAnimationProfile setHurtAnimId(AnimationId id) { return setHurtAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setSkidAnimId(int skidAnimId) { this.skidAnimId = skidAnimId; return this; }
    public ScriptedVelocityAnimationProfile setSkidAnimId(AnimationId id) { return setSkidAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setSlideAnimId(int slideAnimId) { this.slideAnimId = slideAnimId; return this; }
    public ScriptedVelocityAnimationProfile setSlideAnimId(AnimationId id) { return setSlideAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setDrownAnimId(int drownAnimId) { this.drownAnimId = drownAnimId; return this; }
    public ScriptedVelocityAnimationProfile setDrownAnimId(AnimationId id) { return setDrownAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setAirAnimId(int airAnimId) { this.airAnimId = airAnimId; return this; }
    public ScriptedVelocityAnimationProfile setAirAnimId(AnimationId id) { return setAirAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setBalanceAnimId(int balanceAnimId) { this.balanceAnimId = balanceAnimId; return this; }
    public ScriptedVelocityAnimationProfile setBalanceAnimId(AnimationId id) { return setBalanceAnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setBalance2AnimId(int balance2AnimId) { this.balance2AnimId = balance2AnimId; return this; }
    public ScriptedVelocityAnimationProfile setBalance2AnimId(AnimationId id) { return setBalance2AnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setBalance3AnimId(int balance3AnimId) { this.balance3AnimId = balance3AnimId; return this; }
    public ScriptedVelocityAnimationProfile setBalance3AnimId(AnimationId id) { return setBalance3AnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setBalance4AnimId(int balance4AnimId) { this.balance4AnimId = balance4AnimId; return this; }
    public ScriptedVelocityAnimationProfile setBalance4AnimId(AnimationId id) { return setBalance4AnimId(id.id()); }
    public ScriptedVelocityAnimationProfile setRunSpeedThreshold(int runSpeedThreshold) { this.runSpeedThreshold = runSpeedThreshold; return this; }
    public ScriptedVelocityAnimationProfile setWalkSpeedThreshold(int walkSpeedThreshold) { this.walkSpeedThreshold = walkSpeedThreshold; return this; }
    public ScriptedVelocityAnimationProfile setFallbackFrame(int fallbackFrame) { this.fallbackFrame = fallbackFrame; return this; }
    public ScriptedVelocityAnimationProfile setAnglePreAdjust(boolean anglePreAdjust) { this.anglePreAdjust = anglePreAdjust; return this; }
    public ScriptedVelocityAnimationProfile setCompactSuperRunSlope(boolean compactSuperRunSlope) { this.compactSuperRunSlope = compactSuperRunSlope; return this; }

    @Override
    public Integer resolveAnimationId(AbstractPlayableSprite sprite, int frameCounter, int scriptCount) {
        // ROM: when f_playerctrl is set, Sonic_Move and normal movement routines don't run,
        // so they never overwrite obAnim. Let the controlling object's animation stick.
        if (sprite.isObjectControlled()) {
            return null;
        }
        // ROM: when move_lock > 0, Sonic_Move doesn't run and doesn't overwrite obAnim.
        // This lets externally-set animations (e.g., bubble-breathing id_GetAir/id_Bubble)
        // persist for the duration of the lock. Without this, resolveAnimationId would
        // immediately override the animation with idle/walk based on current state.
        if (sprite.getMoveLockTimer() > 0) {
            return null;
        }
        // Drowning uses its own animation (0x17) throughout both pre-death and dead phases
        if (sprite.isDrowningDeath() && drownAnimId >= 0) {
            return drownAnimId;
        }
        if (sprite.getDead() && deathAnimId >= 0) {
            return deathAnimId;
        }
        // Hurt state uses separate hurt animation (animation 0x19)
        if (sprite.isHurt() && hurtAnimId >= 0) {
            return hurtAnimId;
        }
        if (sprite.getSpringing() && sprite.getAir() && springAnimId >= 0) {
            return springAnimId;
        }
        if (sprite.getSpindash() && spindashAnimId >= 0) {
            return spindashAnimId;
        }
        // ROM: slide animation only applies on ground. When airborne (e.g. jumping
        // off a water slide), the jump/roll mode directly overwrites obAnim with
        // id_Roll, so the slide animation never shows in the air.
        if (sprite.isSliding() && !sprite.getAir() && slideAnimId >= 0) {
            return slideAnimId;
        }
        if (sprite.getRolling()) {
            return rollAnimId;
        }
        if (sprite.getAir()) {
            return airAnimId;
        }
        if (sprite.getLookingUp() && lookUpAnimId >= 0) {
            return lookUpAnimId;
        }
        if (sprite.getCrouching() && duckAnimId >= 0) {
            return duckAnimId;
        }
        // ROM-accurate: Pushing state takes priority over speed-based animations
        if (sprite.getPushing() && pushAnimId >= 0) {
            return pushAnimId;
        }
        // ROM-accurate: Skidding state (braking at speed >= 0x400)
        if (sprite.getSkidding() && skidAnimId >= 0) {
            return skidAnimId;
        }

        // ROM-accurate animation selection (s2.asm:36558, 36619, 36242-36245):
        // - Sonic_MoveLeft/MoveRight set anim = Walk unconditionally when direction pressed
        // - Idle animation only set when inertia == 0 AND no direction pressed
        // This means: walk plays when pressing direction OR when still moving (coasting)
        // Use isMovementInputActive() which reflects EFFECTIVE input (after control lock filtering),
        // not raw button state, to match ROM behavior where animation is only set in movement routines.
        boolean pressingDirection = sprite.isMovementInputActive();
        int speed = Math.abs(sprite.getGSpeed());

        // Run animation at high speeds (ROM: cmpi.w #$600,d2)
        if (speed >= runSpeedThreshold) {
            return runAnimId;
        }

        // Walk animation when pressing direction OR when still moving (inertia != 0)
        if (pressingDirection || speed > 0) {
            return walkAnimId;
        }

        // Balance animation when standing at edge of ledge/platform (ROM s2.asm:36246-36373)
        // Balance state is set by movement code based on terrain/object edge detection
        int balanceState = sprite.getBalanceState();
        if (balanceState > 0 && balanceAnimId >= 0) {
            return switch (balanceState) {
                case 1 -> balanceAnimId;      // Balance - facing edge, safe distance
                case 2 -> balance2AnimId >= 0 ? balance2AnimId : balanceAnimId;  // Balance2 - facing edge, closer
                case 3 -> balance3AnimId >= 0 ? balance3AnimId : balanceAnimId;  // Balance3 - facing away, safe
                case 4 -> balance4AnimId >= 0 ? balance4AnimId : (balance3AnimId >= 0 ? balance3AnimId : balanceAnimId); // Balance4 - facing away, closer
                default -> balanceAnimId;
            };
        }

        // Idle only when not pressing direction AND completely stopped (inertia == 0)
        return idleAnimId;
    }

    @Override
    public int resolveFrame(AbstractPlayableSprite sprite, int frameCounter, int frameCount) {
        if (frameCount <= 0) {
            return 0;
        }
        return Math.min(fallbackFrame, frameCount - 1);
    }

    public int getIdleAnimId() {
        return idleAnimId;
    }

    public int getWalkAnimId() {
        return walkAnimId;
    }

    public int getRunAnimId() {
        return runAnimId;
    }

    public int getRollAnimId() {
        return rollAnimId;
    }

    public int getRoll2AnimId() {
        return roll2AnimId;
    }

    public int getPushAnimId() {
        return pushAnimId;
    }

    public int getDuckAnimId() {
        return duckAnimId;
    }

    public int getLookUpAnimId() {
        return lookUpAnimId;
    }

    public int getSpindashAnimId() {
        return spindashAnimId;
    }

    public int getSpringAnimId() {
        return springAnimId;
    }

    public int getDeathAnimId() {
        return deathAnimId;
    }

    public int getSkidAnimId() {
        return skidAnimId;
    }

    public int getDrownAnimId() {
        return drownAnimId;
    }

    public int getAirAnimId() {
        return airAnimId;
    }

    public int getBalanceAnimId() {
        return balanceAnimId;
    }

    public int getBalance2AnimId() {
        return balance2AnimId;
    }

    public int getBalance3AnimId() {
        return balance3AnimId;
    }

    public int getBalance4AnimId() {
        return balance4AnimId;
    }

    public int getRunSpeedThreshold() {
        return runSpeedThreshold;
    }

    public int getWalkSpeedThreshold() {
        return walkSpeedThreshold;
    }

    public int getFallbackFrame() {
        return fallbackFrame;
    }

    public boolean isAnglePreAdjust() {
        return anglePreAdjust;
    }

    public boolean isCompactSuperRunSlope() {
        return compactSuperRunSlope;
    }

    public ScriptedVelocityAnimationProfile withRunSpeedThreshold(int newThreshold) {
        ScriptedVelocityAnimationProfile copy = new ScriptedVelocityAnimationProfile();
        copy.idleAnimId = this.idleAnimId;
        copy.walkAnimId = this.walkAnimId;
        copy.runAnimId = this.runAnimId;
        copy.rollAnimId = this.rollAnimId;
        copy.roll2AnimId = this.roll2AnimId;
        copy.pushAnimId = this.pushAnimId;
        copy.duckAnimId = this.duckAnimId;
        copy.lookUpAnimId = this.lookUpAnimId;
        copy.spindashAnimId = this.spindashAnimId;
        copy.springAnimId = this.springAnimId;
        copy.deathAnimId = this.deathAnimId;
        copy.hurtAnimId = this.hurtAnimId;
        copy.skidAnimId = this.skidAnimId;
        copy.slideAnimId = this.slideAnimId;
        copy.drownAnimId = this.drownAnimId;
        copy.airAnimId = this.airAnimId;
        copy.balanceAnimId = this.balanceAnimId;
        copy.balance2AnimId = this.balance2AnimId;
        copy.balance3AnimId = this.balance3AnimId;
        copy.balance4AnimId = this.balance4AnimId;
        copy.walkSpeedThreshold = this.walkSpeedThreshold;
        copy.runSpeedThreshold = newThreshold;
        copy.fallbackFrame = this.fallbackFrame;
        copy.anglePreAdjust = this.anglePreAdjust;
        copy.compactSuperRunSlope = this.compactSuperRunSlope;
        return copy;
    }
}
