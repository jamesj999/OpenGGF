package com.openggf.game;

/**
 * Shield types available across all games.
 * <p>
 * S1/S2 only use {@link #BASIC}. S3K adds three elemental shields
 * (Fire, Lightning, Bubble) that each grant special abilities.
 */
public enum ShieldType {
    BASIC,
    FIRE,
    LIGHTNING,
    BUBBLE
}
