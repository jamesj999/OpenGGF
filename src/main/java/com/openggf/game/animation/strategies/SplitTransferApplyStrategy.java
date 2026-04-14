package com.openggf.game.animation.strategies;

import com.openggf.game.animation.ApplyStrategy;
import com.openggf.game.animation.ChannelContext;

import java.util.Objects;

public final class SplitTransferApplyStrategy implements ApplyStrategy {
    private final Runnable transfer;

    public SplitTransferApplyStrategy(Runnable transfer) {
        this.transfer = Objects.requireNonNull(transfer, "transfer");
    }

    @Override
    public void apply(ChannelContext context) {
        transfer.run();
    }
}
