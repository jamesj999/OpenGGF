package com.openggf.game.solid;

public record PreContactState(
        short xSpeed,
        short ySpeed,
        boolean rolling,
        int animationId) {

    public static final PreContactState ZERO =
            new PreContactState((short) 0, (short) 0, false, 0);
}
