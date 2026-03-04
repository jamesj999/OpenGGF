package com.openggf.game.sonic2.objects.bosses;

import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.util.List;

/**
 * Animation scripts for the EHZ boss (Obj56).
 * Mirrors Ani_obj56_a/b/c from s2.asm.
 */
final class EHZBossAnimations {
    private static final SpriteAnimationSet PROPELLER_ANIMATIONS = createPropellerAnimations();
    private static final SpriteAnimationSet VEHICLE_ANIMATIONS = createVehicleAnimations();
    private static final SpriteAnimationSet TOP_ANIMATIONS = createTopAnimations();

    private EHZBossAnimations() {
    }

    static SpriteAnimationSet getPropellerAnimations() {
        return PROPELLER_ANIMATIONS;
    }

    static SpriteAnimationSet getVehicleAnimations() {
        return VEHICLE_ANIMATIONS;
    }

    static SpriteAnimationSet getTopAnimations() {
        return TOP_ANIMATIONS;
    }

    private static SpriteAnimationSet createPropellerAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        // Ani_obj56_a script 0: dc.b 1,5,6,$FF
        set.addScript(0, new SpriteAnimationScript(
                0x01,
                List.of(5, 6),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj56_a script 1: dc.b 1,1,1,1,2,2,2,3,3,3,4,4,4,0,0,0,0,0,0,0,0,0,$FF
        set.addScript(1, new SpriteAnimationScript(
                0x01,
                List.of(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj56_a script 2: dc.b 1,0,0,0,0,0,0,0,0,4,4,4,3,3,3,2,2,2,1,1,1,5,6,$FE,2
        set.addScript(2, new SpriteAnimationScript(
                0x01,
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 4, 3, 3, 3, 2, 2, 2, 1, 1, 1, 5, 6),
                SpriteAnimationEndAction.LOOP_BACK,
                2));
        return set;
    }

    private static SpriteAnimationSet createVehicleAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        // Ani_obj56_b script 0: dc.b 5,1,2,3,$FF (spike)
        set.addScript(0, new SpriteAnimationScript(
                0x05,
                List.of(1, 2, 3),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj56_b script 1: dc.b 1,4,5,$FF (foreground wheel)
        set.addScript(1, new SpriteAnimationScript(
                0x01,
                List.of(4, 5),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj56_b script 2: dc.b 1,6,7,$FF (background wheel)
        set.addScript(2, new SpriteAnimationScript(
                0x01,
                List.of(6, 7),
                SpriteAnimationEndAction.LOOP,
                0));
        return set;
    }

    private static SpriteAnimationSet createTopAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        // Ani_obj56_c script 0: dc.b $F,0,$FF (bottom)
        set.addScript(0, new SpriteAnimationScript(
                0x0F,
                List.of(0),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj56_c script 1: dc.b 7,1,2,$FF (top normal)
        set.addScript(1, new SpriteAnimationScript(
                0x07,
                List.of(1, 2),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj56_c script 2: dc.b 7,5,5,5,5,5,5,$FD,1 (top hit)
        set.addScript(2, new SpriteAnimationScript(
                0x07,
                List.of(5, 5, 5, 5, 5, 5),
                SpriteAnimationEndAction.SWITCH,
                1));
        // Ani_obj56_c script 3: dc.b 7,3,4,3,4,3,4,$FD,1 (top laugh)
        set.addScript(3, new SpriteAnimationScript(
                0x07,
                List.of(3, 4, 3, 4, 3, 4),
                SpriteAnimationEndAction.SWITCH,
                1));
        // Ani_obj56_c script 4: dc.b $F,6,6,6,6,6,6,6,6,6,6,6,$FD,1 (top flying off)
        set.addScript(4, new SpriteAnimationScript(
                0x0F,
                List.of(6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6),
                SpriteAnimationEndAction.SWITCH,
                1));
        return set;
    }
}
