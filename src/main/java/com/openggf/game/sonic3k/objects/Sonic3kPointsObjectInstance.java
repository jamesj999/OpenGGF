package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.AbstractPointsObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sonic 3&amp;K floating points display object (Obj_EnemyScore).
 * <p>
 * Uses Map_EnemyScore mappings (7 frames):
 * Frame 0: "100", Frame 1: "200", Frame 2: "500", Frame 3: "1000",
 * Frame 4: "10" (single tile), Frame 5: "10000", Frame 6: "50000".
 * <p>
 * Art shared with StarPost via ArtNem_EnemyPtsStarPost.
 * Score tiles occupy the first 8 tiles of the combined art blob.
 */
public class Sonic3kPointsObjectInstance extends AbstractPointsObjectInstance {

    public Sonic3kPointsObjectInstance(ObjectSpawn spawn, ObjectServices services, int points) {
        super(spawn, "S3KPoints", services, points);
    }

    @Override
    protected int getFrameForScore(int score) {
        return switch (score) {
            case 10 -> 4;      // "10"
            case 100 -> 0;     // "100"
            case 200 -> 1;     // "200"
            case 500 -> 2;     // "500"
            case 1000 -> 3;    // "1000"
            default -> 0;      // Default to "100"
        };
    }
}
