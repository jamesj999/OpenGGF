package com.openggf.game.animation;

import java.util.Objects;

public final class AnimatedTileChannel {

    private final String channelId;
    private final ChannelGuard guard;
    private final PhaseSource phaseSource;
    private final DestinationPlan destinationPlan;
    private final AnimatedTileCachePolicy cachePolicy;
    private final ApplyStrategy applyStrategy;

    public AnimatedTileChannel(String channelId,
                               ChannelGuard guard,
                               PhaseSource phaseSource,
                               DestinationPlan destinationPlan,
                               AnimatedTileCachePolicy cachePolicy,
                               ApplyStrategy applyStrategy) {
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.phaseSource = Objects.requireNonNull(phaseSource, "phaseSource");
        this.destinationPlan = Objects.requireNonNull(destinationPlan, "destinationPlan");
        this.cachePolicy = Objects.requireNonNull(cachePolicy, "cachePolicy");
        this.applyStrategy = Objects.requireNonNull(applyStrategy, "applyStrategy");
    }

    public String channelId() {
        return channelId;
    }

    public ChannelGuard guard() {
        return guard;
    }

    public PhaseSource phaseSource() {
        return phaseSource;
    }

    public DestinationPlan destinationPlan() {
        return destinationPlan;
    }

    public AnimatedTileCachePolicy cachePolicy() {
        return cachePolicy;
    }

    public ApplyStrategy applyStrategy() {
        return applyStrategy;
    }
}
