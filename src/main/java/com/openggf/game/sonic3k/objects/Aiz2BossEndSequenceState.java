package com.openggf.game.sonic3k.objects;

/**
 * Shared state for the AIZ2 post-boss bridge / button / Knuckles cutscene.
 *
 * <p>The original game uses a handful of global work RAM flags and object
 * pointers (_unkFAA3/_unkFAA4/_unkFAA9). This class keeps the Java-side
 * coordination equally small without coupling these objects back to the boss.
 */
public final class Aiz2BossEndSequenceState {

    private static volatile boolean bridgeDropTriggered;
    private static volatile boolean buttonPressed;
    private static volatile boolean eggCapsuleReleased;
    private static volatile boolean cutsceneOverrideObjectsActive;
    private static volatile CutsceneKnucklesAiz2Instance activeKnuckles;

    private Aiz2BossEndSequenceState() {
    }

    public static void reset() {
        bridgeDropTriggered = false;
        buttonPressed = false;
        eggCapsuleReleased = false;
        cutsceneOverrideObjectsActive = false;
        activeKnuckles = null;
    }

    public static boolean isBridgeDropTriggered() {
        return bridgeDropTriggered;
    }

    public static void triggerBridgeDrop() {
        bridgeDropTriggered = true;
    }

    public static boolean isButtonPressed() {
        return buttonPressed;
    }

    public static void pressButton() {
        buttonPressed = true;
    }

    public static boolean isEggCapsuleReleased() {
        return eggCapsuleReleased;
    }

    public static void releaseEggCapsule() {
        eggCapsuleReleased = true;
    }

    public static boolean isCutsceneOverrideObjectsActive() {
        return cutsceneOverrideObjectsActive;
    }

    public static void activateCutsceneOverrideObjects() {
        cutsceneOverrideObjectsActive = true;
    }

    public static CutsceneKnucklesAiz2Instance getActiveKnuckles() {
        return activeKnuckles;
    }

    public static void setActiveKnuckles(CutsceneKnucklesAiz2Instance knuckles) {
        activeKnuckles = knuckles;
    }
}
