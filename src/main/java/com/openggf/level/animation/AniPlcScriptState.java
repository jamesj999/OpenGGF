package com.openggf.level.animation;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;

/**
 * Runtime state for a single AniPLC (animated pattern load cue) script entry.
 *
 * <p>Both Sonic 2 and Sonic 3&K use an identical binary format for zone tile
 * animation scripts ({@code zoneanimstart}/{@code zoneanimdecl} macros).
 * This class holds the parsed data and mutable playback state for one script.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code globalDuration} — signed byte; if negative, per-frame durations are used</li>
 *   <li>{@code destTileIndex} — VRAM destination tile index</li>
 *   <li>{@code frameTileIds} — source tile index for each animation frame</li>
 *   <li>{@code frameDurations} — per-frame durations (null when globalDuration >= 0)</li>
 *   <li>{@code tilesPerFrame} — number of consecutive tiles copied per frame</li>
 *   <li>{@code artPatterns} — pre-loaded pattern art tiles</li>
 *   <li>{@code timer} — countdown timer for current frame</li>
 *   <li>{@code frameIndex} — current animation frame index</li>
 * </ul>
 */
public class AniPlcScriptState {
    private final byte globalDuration;
    private final int destTileIndex;
    private final int[] frameTileIds;
    private final int[] frameDurations;
    private final int tilesPerFrame;
    private final Pattern[] artPatterns;
    private int timer;
    private int frameIndex;

    public AniPlcScriptState(byte globalDuration,
                             int destTileIndex,
                             int[] frameTileIds,
                             int[] frameDurations,
                             int tilesPerFrame,
                             Pattern[] artPatterns) {
        this.globalDuration = globalDuration;
        this.destTileIndex = destTileIndex;
        this.frameTileIds = frameTileIds;
        this.frameDurations = frameDurations;
        this.tilesPerFrame = tilesPerFrame;
        this.artPatterns = artPatterns;
        this.timer = 0;
        this.frameIndex = 0;
    }

    public void tick(Level level, GraphicsManager graphicsManager) {
        if (frameTileIds.length == 0 || artPatterns.length == 0) {
            return;
        }
        if (timer > 0) {
            timer = (timer - 1) & 0xFF;
            return;
        }

        int currentFrame = frameIndex;
        if (currentFrame >= frameTileIds.length) {
            currentFrame = 0;
            frameIndex = 0;
        }
        frameIndex = currentFrame + 1;

        int duration = globalDuration & 0xFF;
        if (globalDuration < 0 && frameDurations != null) {
            duration = frameDurations[currentFrame];
        }
        timer = duration & 0xFF;

        int tileId = frameTileIds[currentFrame];
        applyFrame(level, graphicsManager, tileId);
    }

    public int requiredPatternCount() {
        return destTileIndex + Math.max(tilesPerFrame, 1);
    }

    public void prime(Level level, GraphicsManager graphicsManager) {
        if (frameTileIds.length == 0 || artPatterns.length == 0) {
            return;
        }
        applyFrame(level, graphicsManager, frameTileIds[0]);
    }

    public void applyFrame(Level level, GraphicsManager graphicsManager, int tileId) {
        int maxPatterns = level.getPatternCount();
        boolean canUpdateTextures = graphicsManager.isGlInitialized();
        for (int i = 0; i < tilesPerFrame; i++) {
            int srcIndex = tileId + i;
            int destIndex = destTileIndex + i;
            if (srcIndex < 0 || srcIndex >= artPatterns.length) {
                continue;
            }
            if (destIndex < 0 || destIndex >= maxPatterns) {
                continue;
            }
            Pattern dest = level.getPattern(destIndex);
            dest.copyFrom(artPatterns[srcIndex]);
            if (canUpdateTextures) {
                graphicsManager.updatePatternTexture(dest, destIndex);
            }
        }
    }
}
