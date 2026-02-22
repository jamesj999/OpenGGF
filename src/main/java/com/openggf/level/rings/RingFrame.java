package com.openggf.level.rings;

import com.openggf.level.render.SpriteFrame;

import java.util.List;

/**
 * A single ring animation frame.
 */
public record RingFrame(List<RingFramePiece> pieces) implements SpriteFrame<RingFramePiece> {
}
