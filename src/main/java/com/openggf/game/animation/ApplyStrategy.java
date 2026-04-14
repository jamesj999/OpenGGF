package com.openggf.game.animation;

@FunctionalInterface
public interface ApplyStrategy {
    void apply(ChannelContext context);
}
