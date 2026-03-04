package com.openggf.level.objects;

import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteSheet;

import java.util.List;

/**
 * Simple sprite sheet for static object art (patterns + mapping frames).
 */
public class ObjectSpriteSheet implements SpriteSheet<SpriteMappingFrame> {
    private final Pattern[] patterns;
    private final List<SpriteMappingFrame> frames;
    private final int paletteIndex;
    private final int frameDelay;

    public ObjectSpriteSheet(
            Pattern[] patterns,
            List<SpriteMappingFrame> frames,
            int paletteIndex,
            int frameDelay
    ) {
        this.patterns = patterns;
        this.frames = frames;
        this.paletteIndex = paletteIndex;
        this.frameDelay = frameDelay;
    }

    @Override
    public Pattern[] getPatterns() {
        return patterns;
    }

    @Override
    public int getFrameCount() {
        return frames != null ? frames.size() : 0;
    }

    @Override
    public SpriteMappingFrame getFrame(int index) {
        return frames.get(index);
    }

    @Override
    public int getPaletteIndex() {
        return paletteIndex;
    }

    @Override
    public int getFrameDelay() {
        return frameDelay;
    }
}
