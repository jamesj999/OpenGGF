package com.openggf.level.render;

import java.util.List;

/**
 * Common sprite frame contract for pattern-based renderers.
 */
public interface SpriteFrame<P extends SpriteFramePiece> {
    List<P> pieces();
}
