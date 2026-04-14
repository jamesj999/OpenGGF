package com.openggf.game.animation;

@FunctionalInterface
public interface PhaseSource {
    int resolve(ChannelContext context);
}
