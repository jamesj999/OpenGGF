package com.openggf.game.animation;

public record DestinationPlan(int primaryTile, Integer secondaryTile) {

    public static DestinationPlan single(int primaryTile) {
        return new DestinationPlan(primaryTile, null);
    }
}
