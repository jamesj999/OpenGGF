package com.openggf.level.render;

/**
 * Describes a contiguous tile load from source art.
 */
public record TileLoadRequest(int startTile, int count) {
}
