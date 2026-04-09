package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * Exit sequence matching ROM loc_4BC1E / loc_4BC54 (lines 98959-99000).
 * Phase 1: Rotation wind-down — increment scalar by 0x40 per frame until 0x1800.
 * Phase 2: 60-frame palette fade to black, rotation continues.
 */
public final class S3kSlotExitSequence {

    static final int WIND_DOWN_INCREMENT = 0x40;
    static final int WIND_DOWN_TARGET = 0x1800;
    static final int FADE_FRAMES = 60;
    static final int FADE_CALL_INTERVAL = 3; // Pal_ToBlack every 3 frames

    private final S3kSlotStageController controller;
    private int currentScalar;
    private int fadeTimer;
    private int fadeDelay;
    private boolean inFade;
    private boolean complete;

    public S3kSlotExitSequence(S3kSlotStageController controller) {
        this.controller = controller;
        this.currentScalar = controller != null ? controller.scalarIndex() : 0;
    }

    /** Call once per frame during exit sequence */
    public void tick() {
        if (complete) return;

        if (!inFade) {
            // Phase 1: wind-down
            currentScalar += WIND_DOWN_INCREMENT;
            controller.setScalarIndex(currentScalar);
            controller.tick(); // continue rotating

            if (currentScalar >= WIND_DOWN_TARGET) {
                inFade = true;
                fadeTimer = FADE_FRAMES;
                fadeDelay = 0;
            }
        } else {
            // Phase 2: fade to black
            currentScalar += WIND_DOWN_INCREMENT;
            controller.setScalarIndex(currentScalar);
            controller.tick(); // rotation continues during fade
            fadeTimer--;

            if (fadeTimer <= 0) {
                complete = true;
            }
        }
    }

    public boolean isComplete() { return complete; }
    public boolean isFading() { return inFade && !complete; }
    public boolean isWindingDown() { return !inFade && !complete; }

    /** Returns fade progress 0.0 to 1.0 for rendering */
    public float fadeProgress() {
        if (!inFade || complete) return inFade ? 1f : 0f;
        return 1f - ((float) fadeTimer / FADE_FRAMES);
    }

    /** Returns true when Pal_ToBlack should be called this frame (every 3 frames during fade) */
    public boolean shouldFadeStep() {
        if (!inFade || complete) return false;
        fadeDelay++;
        if (fadeDelay >= FADE_CALL_INTERVAL) {
            fadeDelay = 0;
            return true;
        }
        return false;
    }
}
