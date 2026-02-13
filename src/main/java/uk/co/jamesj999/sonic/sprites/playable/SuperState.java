package uk.co.jamesj999.sonic.sprites.playable;

/**
 * States for the Super Sonic transformation lifecycle.
 */
public enum SuperState {
    /** Normal gameplay. Checks transformation trigger each frame. */
    NORMAL,
    /** Playing transformation animation, fading palette, starting music. */
    TRANSFORMING,
    /** Super mode active. Ring drain, palette cycling, invincibility. */
    SUPER,
    /** Reverting to normal. Restoring palette, physics, music. */
    REVERTING
}
