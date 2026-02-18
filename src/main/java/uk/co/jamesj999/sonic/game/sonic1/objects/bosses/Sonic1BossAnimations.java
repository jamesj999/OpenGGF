package uk.co.jamesj999.sonic.game.sonic1.objects.bosses;

import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;

import java.util.List;

/**
 * Animation scripts for all Sonic 1 bosses (Eggman).
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
final class Sonic1BossAnimations {
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

    // Ani_SEgg (FZ/SBZ2 Eggman) animation IDs
    static final int ANIM_SEGG_STAND = 0;
    static final int ANIM_SEGG_LAUGH = 1;
    static final int ANIM_SEGG_JUMP1 = 2;
    static final int ANIM_SEGG_INTUBE = 3;
    static final int ANIM_SEGG_RUNNING = 4;
    static final int ANIM_SEGG_JUMP2 = 5;
    static final int ANIM_SEGG_STARJUMP = 6;

    // Ani_PLaunch (Plasma Launcher) animation IDs
    static final int ANIM_PLAUNCH_RED = 0;
    static final int ANIM_PLAUNCH_RED_SPARKING = 1;
    static final int ANIM_PLAUNCH_WHITE_SPARKING = 2;

    // Ani_Plasma (Energy Balls) animation IDs
    static final int ANIM_PLASMA_FULL = 0;
    static final int ANIM_PLASMA_SHORT = 1;

    // Ani_FZEgg (Post-defeat Eggman in damaged ship) animation IDs
    static final int ANIM_FZEGG_DEFAULT = 0;

    private static final SpriteAnimationSet EGGMAN_ANIMATIONS = createEggmanAnimations();
    private static final SpriteAnimationSet SEGG_ANIMATIONS = createSEggAnimations();
    private static final SpriteAnimationSet PLAUNCH_ANIMATIONS = createPLaunchAnimations();
    private static final SpriteAnimationSet PLASMA_ANIMATIONS = createPlasmaAnimations();
    private static final SpriteAnimationSet FZEGG_ANIMATIONS = createFZEggAnimations();

    private Sonic1BossAnimations() {
    }

    static SpriteAnimationSet getEggmanAnimations() {
        return EGGMAN_ANIMATIONS;
    }

    static SpriteAnimationSet getSEggAnimations() {
        return SEGG_ANIMATIONS;
    }

    static SpriteAnimationSet getPLaunchAnimations() {
        return PLAUNCH_ANIMATIONS;
    }

    static SpriteAnimationSet getPlasmaAnimations() {
        return PLASMA_ANIMATIONS;
    }

    static SpriteAnimationSet getFZEggAnimations() {
        return FZEGG_ANIMATIONS;
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

    /**
     * Ani_SEgg - FZ/SBZ2 Eggman animations.
     * 7 animation scripts:
     *  0  stand     $7E, 0, afEnd
     *  1  laugh     6, 1, 2, afEnd
     *  2  jump1     $E, 3, 4, 4, 0, 0, 0, afEnd
     *  3  intube    0, 5, 9, afEnd
     *  4  running   6, 7, 4, 8, 4, afEnd
     *  5  jump2     $F, 4, 3, 3, afEnd
     *  6  starjump  $7E, 6, afEnd
     */
    private static SpriteAnimationSet createSEggAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // 0: stand - dc.b $7E, 0, afEnd
        set.addScript(ANIM_SEGG_STAND, new SpriteAnimationScript(
                0x7E, List.of(0), SpriteAnimationEndAction.LOOP, 0));

        // 1: laugh - dc.b 6, 1, 2, afEnd
        set.addScript(ANIM_SEGG_LAUGH, new SpriteAnimationScript(
                0x06, List.of(1, 2), SpriteAnimationEndAction.LOOP, 0));

        // 2: jump1 - dc.b $E, 3, 4, 4, 0, 0, 0, afEnd
        set.addScript(ANIM_SEGG_JUMP1, new SpriteAnimationScript(
                0x0E, List.of(3, 4, 4, 0, 0, 0), SpriteAnimationEndAction.LOOP, 0));

        // 3: intube - dc.b 0, 5, 9, afEnd
        set.addScript(ANIM_SEGG_INTUBE, new SpriteAnimationScript(
                0x00, List.of(5, 9), SpriteAnimationEndAction.LOOP, 0));

        // 4: running - dc.b 6, 7, 4, 8, 4, afEnd
        set.addScript(ANIM_SEGG_RUNNING, new SpriteAnimationScript(
                0x06, List.of(7, 4, 8, 4), SpriteAnimationEndAction.LOOP, 0));

        // 5: jump2 - dc.b $F, 4, 3, 3, afEnd
        set.addScript(ANIM_SEGG_JUMP2, new SpriteAnimationScript(
                0x0F, List.of(4, 3, 3), SpriteAnimationEndAction.LOOP, 0));

        // 6: starjump - dc.b $7E, 6, afEnd
        set.addScript(ANIM_SEGG_STARJUMP, new SpriteAnimationScript(
                0x7E, List.of(6), SpriteAnimationEndAction.LOOP, 0));

        return set;
    }

    /**
     * Ani_PLaunch - Plasma Launcher animations.
     * 3 animation scripts:
     *  0  red           $7E, 0, afEnd
     *  1  redsparking   1, 0, 2, 0, 3, afEnd
     *  2  whitesparking 1, 1, 2, 1, 3, afEnd
     */
    private static SpriteAnimationSet createPLaunchAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // 0: red - dc.b $7E, 0, afEnd
        set.addScript(ANIM_PLAUNCH_RED, new SpriteAnimationScript(
                0x7E, List.of(0), SpriteAnimationEndAction.LOOP, 0));

        // 1: redsparking - dc.b 1, 0, 2, 0, 3, afEnd
        set.addScript(ANIM_PLAUNCH_RED_SPARKING, new SpriteAnimationScript(
                0x01, List.of(0, 2, 0, 3), SpriteAnimationEndAction.LOOP, 0));

        // 2: whitesparking - dc.b 1, 1, 2, 1, 3, afEnd
        set.addScript(ANIM_PLAUNCH_WHITE_SPARKING, new SpriteAnimationScript(
                0x01, List.of(1, 2, 1, 3), SpriteAnimationEndAction.LOOP, 0));

        return set;
    }

    /**
     * Ani_Plasma - Energy Ball animations.
     * 2 animation scripts:
     *  0  full  1, 0, $A, 8, $A, 1, $A, 9, $A, 6, $A, 7, $A, 0, $A, 8, $A, 1, $A, 9, $A, 6, $A, 7, $A, 2, $A, 3, $A, 4, $A, 5, afEnd
     *  1  short 0, 6, 5, 1, 5, 7, 5, 1, 5, afEnd
     */
    private static SpriteAnimationSet createPlasmaAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // 0: full - dc.b 1, 0, $A, 8, $A, 1, $A, 9, $A, 6, $A, 7, $A, 0, $A, 8, $A, 1, $A, 9, $A, 6, $A, 7, $A, 2, $A, 3, $A, 4, $A, 5, afEnd
        set.addScript(ANIM_PLASMA_FULL, new SpriteAnimationScript(
                0x01, List.of(0, 10, 8, 10, 1, 10, 9, 10, 6, 10, 7, 10,
                        0, 10, 8, 10, 1, 10, 9, 10, 6, 10, 7, 10,
                        2, 10, 3, 10, 4, 10, 5),
                SpriteAnimationEndAction.LOOP, 0));

        // 1: short - dc.b 0, 6, 5, 1, 5, 7, 5, 1, 5, afEnd
        set.addScript(ANIM_PLASMA_SHORT, new SpriteAnimationScript(
                0x00, List.of(6, 5, 1, 5, 7, 5, 1, 5), SpriteAnimationEndAction.LOOP, 0));

        return set;
    }

    /**
     * Ani_FZEgg - Post-defeat Eggman in damaged ship animation.
     * 1 animation script:
     *  0  default  3, 0, 1, afEnd
     */
    private static SpriteAnimationSet createFZEggAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // 0: default - dc.b 3, 0, 1, afEnd
        set.addScript(ANIM_FZEGG_DEFAULT, new SpriteAnimationScript(
                0x03, List.of(0, 1), SpriteAnimationEndAction.LOOP, 0));

        return set;
    }
}
