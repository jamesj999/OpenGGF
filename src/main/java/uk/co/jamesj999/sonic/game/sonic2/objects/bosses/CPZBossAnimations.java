package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;

import java.util.List;

/**
 * Animation scripts for the CPZ boss (Obj5D).
 * Mirrors Ani_obj5D_b and Ani_Obj5D_Dripper from s2.asm.
 */
final class CPZBossAnimations {
    private static final SpriteAnimationSet EGGPOD_ANIMATIONS = createEggpodAnimations();
    private static final SpriteAnimationSet DRIPPER_ANIMATIONS = createDripperAnimations();

    private CPZBossAnimations() {
    }

    static SpriteAnimationSet getEggpodAnimations() {
        return EGGPOD_ANIMATIONS;
    }

    static SpriteAnimationSet getDripperAnimations() {
        return DRIPPER_ANIMATIONS;
    }

    private static SpriteAnimationSet createEggpodAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        // Ani_obj5D_b script 0: dc.b $F,0,$FF
        set.addScript(0, new SpriteAnimationScript(
                0x0F,
                List.of(0),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj5D_b script 1: dc.b 7,1,2,$FF
        set.addScript(1, new SpriteAnimationScript(
                0x07,
                List.of(1, 2),
                SpriteAnimationEndAction.LOOP,
                0));
        // Ani_obj5D_b script 2: dc.b 7,5,5,5,5,5,5,$FD,1
        set.addScript(2, new SpriteAnimationScript(
                0x07,
                List.of(5, 5, 5, 5, 5, 5),
                SpriteAnimationEndAction.SWITCH,
                1));
        // Ani_obj5D_b script 3: dc.b 7,3,4,3,4,3,4,$FD,1
        set.addScript(3, new SpriteAnimationScript(
                0x07,
                List.of(3, 4, 3, 4, 3, 4),
                SpriteAnimationEndAction.SWITCH,
                1));
        // Ani_obj5D_b script 4: dc.b $F,6,6,6,6,6,6,6,6,6,6,$FD,1
        set.addScript(4, new SpriteAnimationScript(
                0x0F,
                List.of(6, 6, 6, 6, 6, 6, 6, 6, 6, 6),
                SpriteAnimationEndAction.SWITCH,
                1));
        return set;
    }

    private static SpriteAnimationSet createDripperAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        // Ani_Obj5D_Dripper scripts 0..$1A
        set.addScript(0, new SpriteAnimationScript(
                0x0F, List.of(0), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(1, new SpriteAnimationScript(
                0x0F, List.of(1), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(2, new SpriteAnimationScript(
                0x05, List.of(2, 3, 2), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(3, new SpriteAnimationScript(
                0x05, List.of(2, 3), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(4, new SpriteAnimationScript(
                0x02, List.of(4, 5, 6, 7, 8), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(5, new SpriteAnimationScript(
                0x03, List.of(9), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(6, new SpriteAnimationScript(
                0x0F, List.of(0x0A), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(7, new SpriteAnimationScript(
                0x0F, List.of(0x1C), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(8, new SpriteAnimationScript(
                0x0F, List.of(0x1E), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(9, new SpriteAnimationScript(
                0x0F, List.of(0x0B), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x0A, new SpriteAnimationScript(
                0x03,
                List.of(0x0C, 0x0C, 0x0D, 0x0D, 0x0D, 0x0D, 0x0D, 0x0C, 0x0C),
                SpriteAnimationEndAction.SWITCH,
                0x09));
        set.addScript(0x0B, new SpriteAnimationScript(
                0x03,
                List.of(0x0E, 0x0E, 0x0F, 0x0F, 0x0F, 0x0F, 0x0F, 0x0E, 0x0E),
                SpriteAnimationEndAction.LOOP,
                0));
        set.addScript(0x0C, new SpriteAnimationScript(
                0x0F, List.of(0x10), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x0D, new SpriteAnimationScript(
                0x0F, List.of(0x11), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x0E, new SpriteAnimationScript(
                0x0F, List.of(0x12), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x0F, new SpriteAnimationScript(
                0x0F, List.of(0x13), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x10, new SpriteAnimationScript(
                0x0F, List.of(0x14), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x11, new SpriteAnimationScript(
                0x0F, List.of(0x15), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x12, new SpriteAnimationScript(
                0x0F, List.of(0x16), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x13, new SpriteAnimationScript(
                0x0F, List.of(0x17), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x14, new SpriteAnimationScript(
                0x0F, List.of(0x18), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x15, new SpriteAnimationScript(
                0x0F, List.of(0x19), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x16, new SpriteAnimationScript(
                0x0F, List.of(0x1A), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x17, new SpriteAnimationScript(
                0x0F, List.of(0x1B), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x18, new SpriteAnimationScript(
                0x0F, List.of(0x1C), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x19, new SpriteAnimationScript(
                0x01, List.of(0x1D, 0x1F), SpriteAnimationEndAction.LOOP, 0));
        set.addScript(0x1A, new SpriteAnimationScript(
                0x0F, List.of(0x1E), SpriteAnimationEndAction.LOOP, 0));
        return set;
    }
}
