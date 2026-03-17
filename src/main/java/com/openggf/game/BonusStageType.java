package com.openggf.game;

/**
 * Defines the types of bonus stages available in different Sonic games.
 *
 * <p>Bonus stages are typically accessed via checkpoints and provide
 * rewards like rings, shields, or extra lives (not Chaos Emeralds).
 *
 * <p>Sonic 3&K bonus stage selection uses a cyclic formula:
 * {@code remainder = ((rings - 20) / 15) % divisor} where divisor=3 for S3K.
 * <ul>
 *   <li>remainder 0 (20-34, 65-79, ...): Gumball Machine</li>
 *   <li>remainder 1 (35-49, 80-94, ...): Glowing Spheres</li>
 *   <li>remainder 2 (50-64, 95-109, ...): Slot Machine</li>
 * </ul>
 */
public enum BonusStageType {
    /**
     * No bonus stage available.
     */
    NONE,

    /**
     * Sonic 3&K Gumball Machine bonus stage.
     * Accessed with 20-34 rings at checkpoint.
     */
    GUMBALL,

    /**
     * Sonic 3&K Glowing Spheres bonus stage.
     * Accessed with 35-49 rings at checkpoint.
     */
    GLOWING_SPHERE,

    /**
     * Sonic 3&K / Sonic 1 style Slot Machine bonus stage.
     * Accessed with 50+ rings at checkpoint in S3&K.
     */
    SLOT_MACHINE
}
