package com.openggf.game.animation;

import com.openggf.level.Level;

public record ChannelContext(
        AnimatedTileChannelGraph graph,
        AnimatedTileChannel channel,
        Level level,
        Object runtimeState,
        int zoneIndex,
        int actIndex,
        int frameCounter) {
}
