package uk.co.jamesj999.sonic.game.sonic1.objects.bosses;

import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;

import java.util.List;

/**
 * Animation scripts for the GHZ boss (Eggman).
 * From docs/s1disasm/_anim/Eggman.asm (Ani_Eggman).
 *
 * 12 animation scripts:
 *  0  ship         $F, 0, afEnd
 *  1  facenormal1  5, 1, 2, afEnd
 *  2  facenormal2  3, 1, 2, afEnd
 *  3  facenormal3  1, 1, 2, afEnd
 *  4  facelaugh    4, 3, 4, afEnd
 *  5  facehit      $1F, 5, 1, afEnd
 *  6  facepanic    3, 6, 1, afEnd
 *  7  blank        $F, $A, afEnd
 *  8  flame1       3, 8, 9, afEnd
 *  9  flame2       1, 8, 9, afEnd
 * 10  facedefeat   $F, 7, afEnd
 * 11  escapeflame  2, 9, 8, $B, $C, $B, $C, 9, 8, afBack, 2
 */
final class GHZBossAnimations {
    // Animation IDs matching the disassembly order
    static final int ANIM_SHIP = 0;
    static final int ANIM_FACE_NORMAL_1 = 1;
    static final int ANIM_FACE_NORMAL_2 = 2;
    static final int ANIM_FACE_NORMAL_3 = 3;
    static final int ANIM_FACE_LAUGH = 4;
    static final int ANIM_FACE_HIT = 5;
    static final int ANIM_FACE_PANIC = 6;
    static final int ANIM_BLANK = 7;
    static final int ANIM_FLAME_1 = 8;
    static final int ANIM_FLAME_2 = 9;
    static final int ANIM_FACE_DEFEAT = 10;
    static final int ANIM_ESCAPE_FLAME = 11;

    private static final SpriteAnimationSet EGGMAN_ANIMATIONS = createEggmanAnimations();

    private GHZBossAnimations() {
    }

    static SpriteAnimationSet getEggmanAnimations() {
        return EGGMAN_ANIMATIONS;
    }

    private static SpriteAnimationSet createEggmanAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // 0: ship - dc.b $F, 0, afEnd
        set.addScript(ANIM_SHIP, new SpriteAnimationScript(
                0x0F, List.of(0), SpriteAnimationEndAction.LOOP, 0));

        // 1: facenormal1 - dc.b 5, 1, 2, afEnd
        set.addScript(ANIM_FACE_NORMAL_1, new SpriteAnimationScript(
                0x05, List.of(1, 2), SpriteAnimationEndAction.LOOP, 0));

        // 2: facenormal2 - dc.b 3, 1, 2, afEnd
        set.addScript(ANIM_FACE_NORMAL_2, new SpriteAnimationScript(
                0x03, List.of(1, 2), SpriteAnimationEndAction.LOOP, 0));

        // 3: facenormal3 - dc.b 1, 1, 2, afEnd
        set.addScript(ANIM_FACE_NORMAL_3, new SpriteAnimationScript(
                0x01, List.of(1, 2), SpriteAnimationEndAction.LOOP, 0));

        // 4: facelaugh - dc.b 4, 3, 4, afEnd
        set.addScript(ANIM_FACE_LAUGH, new SpriteAnimationScript(
                0x04, List.of(3, 4), SpriteAnimationEndAction.LOOP, 0));

        // 5: facehit - dc.b $1F, 5, 1, afEnd
        set.addScript(ANIM_FACE_HIT, new SpriteAnimationScript(
                0x1F, List.of(5, 1), SpriteAnimationEndAction.LOOP, 0));

        // 6: facepanic - dc.b 3, 6, 1, afEnd
        set.addScript(ANIM_FACE_PANIC, new SpriteAnimationScript(
                0x03, List.of(6, 1), SpriteAnimationEndAction.LOOP, 0));

        // 7: blank - dc.b $F, $A, afEnd (frame 10 = blank)
        set.addScript(ANIM_BLANK, new SpriteAnimationScript(
                0x0F, List.of(10), SpriteAnimationEndAction.LOOP, 0));

        // 8: flame1 - dc.b 3, 8, 9, afEnd
        set.addScript(ANIM_FLAME_1, new SpriteAnimationScript(
                0x03, List.of(8, 9), SpriteAnimationEndAction.LOOP, 0));

        // 9: flame2 - dc.b 1, 8, 9, afEnd
        set.addScript(ANIM_FLAME_2, new SpriteAnimationScript(
                0x01, List.of(8, 9), SpriteAnimationEndAction.LOOP, 0));

        // 10: facedefeat - dc.b $F, 7, afEnd
        set.addScript(ANIM_FACE_DEFEAT, new SpriteAnimationScript(
                0x0F, List.of(7), SpriteAnimationEndAction.LOOP, 0));

        // 11: escapeflame - dc.b 2, 9, 8, $B, $C, $B, $C, 9, 8, afBack, 2
        set.addScript(ANIM_ESCAPE_FLAME, new SpriteAnimationScript(
                0x02, List.of(9, 8, 11, 12, 11, 12, 9, 8),
                SpriteAnimationEndAction.LOOP_BACK, 2));

        return set;
    }
}
