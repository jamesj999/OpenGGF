package com.openggf.game.rewind.snapshot;

import com.openggf.level.Block;
import com.openggf.level.Chunk;

/**
 * Reference-only level snapshot. Block/Chunk arrays are shallow-cloned at
 * capture time (cheap — a few thousand pointers) so future mutations to
 * the live blocks[]/chunks[] don't touch the snapshot. mapData is the
 * Map's underlying byte[]; CoW in Map.cowEnsureWritable clones it on the
 * first gameplay-path mutation per epoch, so snapshot keeps the old ref.
 *
 * Pattern bytes are derived state and not snapshotted (animator counters
 * regenerate them on the next forward step).
 */
public record LevelSnapshot(
        long epochAtCapture,
        Block[] blocks,
        Chunk[] chunks,
        byte[] mapData) {}
