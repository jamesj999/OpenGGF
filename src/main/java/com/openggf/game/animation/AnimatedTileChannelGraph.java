package com.openggf.game.animation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime-owned coordinator for animated tile channels.
 *
 * <p>The graph holds the active channel set for the current zone/runtime and
 * remembers the last resolved phase for each channel id. That lets channels use
 * phase-based caching without keeping their own mutable frame state.
 */
public final class AnimatedTileChannelGraph {

    private List<AnimatedTileChannel> channels = List.of();
    private final Map<String, Integer> lastPhaseByChannel = new HashMap<>();

    /**
     * Replaces the current channel set and clears any cached per-channel phase.
     */
    public void install(List<AnimatedTileChannel> channels) {
        List<AnimatedTileChannel> installed = List.copyOf(Objects.requireNonNull(channels, "channels"));
        Set<String> seenChannelIds = new HashSet<>();
        for (AnimatedTileChannel channel : installed) {
            if (!seenChannelIds.add(channel.channelId())) {
                throw new IllegalArgumentException("Duplicate animated tile channelId: " + channel.channelId());
            }
        }
        this.channels = installed;
        lastPhaseByChannel.clear();
    }

    /** Returns the currently installed channel definitions. */
    public List<AnimatedTileChannel> channels() {
        return channels;
    }

    /** Removes all channels and any cached phase history. */
    public void clear() {
        channels = List.of();
        lastPhaseByChannel.clear();
    }

    /**
     * Resolves and applies each active channel for the current frame.
     *
     * <p>Each channel receives its own {@link ChannelContext}, derived from the
     * shared frame context plus the concrete channel being evaluated.
     */
    public void update(ChannelContext baseContext) {
        Objects.requireNonNull(baseContext, "baseContext");
        for (AnimatedTileChannel channel : channels) {
            if (!channel.guard().allows()) {
                continue;
            }
            ChannelContext channelContext = new ChannelContext(
                    this,
                    channel,
                    baseContext.level(),
                    baseContext.runtimeState(),
                    baseContext.zoneIndex(),
                    baseContext.actIndex(),
                    baseContext.frameCounter());
            int phase = channel.phaseSource().resolve(channelContext);
            Integer previousPhase = lastPhaseByChannel.get(channel.channelId());
            if (channel.cachePolicy() == AnimatedTileCachePolicy.ON_PHASE_CHANGE
                    && previousPhase != null
                    && previousPhase.intValue() == phase) {
                continue;
            }
            lastPhaseByChannel.put(channel.channelId(), phase);
            channel.applyStrategy().apply(channelContext);
        }
    }
}
