package com.openggf.graphics;

/**
 * Replay role for SAT-collected sprite pieces.
 *
 * <p>NORMAL pieces stay in their collected order. PRE_MASK_FRONT pieces are
 * replayed immediately before an active sprite-mask helper so front shell/glass
 * layers can remain visible while later interior content is masked.</p>
 */
public enum SpriteMaskReplayRole {
    NORMAL,
    PRE_MASK_FRONT
}
