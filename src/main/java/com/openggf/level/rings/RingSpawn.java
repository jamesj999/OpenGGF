package com.openggf.level.rings;

import com.openggf.level.spawn.SpawnPoint;

/**
 * Immutable ring placement record expanded to individual ring positions.
 */
public record RingSpawn(int x, int y) implements SpawnPoint {

    public RingSpawn {
        x = x & 0xFFFF;
        y = y & 0xFFFF;
    }
}
