package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.AbstractPointsObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;

/**
 * Sonic 2 floating points display object (Obj29).
 * <p>
 * Frame indices based on obj29.asm mappings:
 * Frame 0: "100", Frame 1: "200", Frame 2: "500",
 * Frame 3: "1000", Frame 4: "10", Frame 5: "1000" alt (chain bonus max).
 */
public class PointsObjectInstance extends AbstractPointsObjectInstance {

    public PointsObjectInstance(ObjectSpawn spawn, ObjectServices services, int points) {
        super(spawn, "Points", services, points);
    }

    @Override
    protected int getFrameForScore(int score) {
        return switch (score) {
            case 10 -> 4;      // "10" display
            case 20 -> 0;      // No dedicated "20" graphic; use "100"
            case 50 -> 2;      // No dedicated "50" graphic; use "500"
            case 100 -> 0;     // "100"
            case 200 -> 1;     // "200"
            case 500 -> 2;     // "500"
            case 1000 -> 5;    // "1000" (large version)
            default -> 0;      // Default to "100"
        };
    }
}
