package com.openggf.game;

/**
 * Player character selection, matching the ROM's Player_mode variable.
 * Used by the level event system for character-specific branching
 * (e.g., S3K Knuckles takes different level paths than Sonic/Tails).
 */
public enum PlayerCharacter {
    /** Player_mode 0: Sonic as main player, Tails follows as CPU. */
    SONIC_AND_TAILS,

    /** Player_mode 1: Sonic alone, no Tails. */
    SONIC_ALONE,

    /** Player_mode 2: Tails as main player. */
    TAILS_ALONE,

    /** Player_mode 3: Knuckles (different level paths, no cutscene Knuckles). */
    KNUCKLES
}
