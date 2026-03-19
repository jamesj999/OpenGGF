package com.openggf.sprites.playable;

/**
 * Character-specific double-jump ability, matching the ROM's per-character_id branching.
 * <p>
 * ROM: Sonic (character_id 0) uses insta-shield, Tails (1) uses flight,
 * Knuckles (2) uses glide. These are mutually exclusive — each character
 * has exactly one secondary ability.
 */
public enum SecondaryAbility {
    /** Sonic's insta-shield: momentary hitbox expansion (sonic3k.asm:23473). S3K feature only. */
    INSTA_SHIELD,
    /** Tails' flight: sustained vertical movement. */
    FLY,
    /** Knuckles' glide: horizontal glide + wall climb. */
    GLIDE,
    /** No secondary ability (S1/S2 characters without donated features). */
    NONE
}
