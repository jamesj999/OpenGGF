package com.openggf.game;

/**
 * Collision path model for the current game.
 *
 * <ul>
 *   <li>{@link #UNIFIED} — Sonic 1: single collision index per zone,
 *       solidity bits hardcoded to 0x0C/0x0D, no dynamic path switching.</li>
 *   <li>{@link #DUAL_PATH} — Sonic 2/3K: dual collision indices
 *       (primary/secondary), per-sprite {@code top_solid_bit}/{@code lrb_solid_bit}
 *       fields that plane switchers and springs can swap at runtime.</li>
 * </ul>
 */
public enum CollisionModel {
    UNIFIED,
    DUAL_PATH
}
