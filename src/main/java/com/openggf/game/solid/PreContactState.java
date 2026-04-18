package com.openggf.game.solid;

public record PreContactState(
        short xSpeed,
        short ySpeed,
        boolean rolling) {

    public static final PreContactState ZERO =
            new PreContactState((short) 0, (short) 0, false);
}
