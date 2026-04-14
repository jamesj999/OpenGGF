package com.openggf.game;

import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.animation.ApplyStrategy;
import com.openggf.game.animation.ChannelContext;
import com.openggf.game.animation.DestinationPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestAnimatedTileChannelGraph {

    @Test
    void installRejectsDuplicateChannelIds() {
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        AnimatedTileChannel first = new AnimatedTileChannel(
                "duplicate",
                () -> true,
                ctx -> 1,
                DestinationPlan.single(0x120),
                AnimatedTileCachePolicy.ALWAYS,
                ctx -> { });
        AnimatedTileChannel second = new AnimatedTileChannel(
                "duplicate",
                () -> true,
                ctx -> 2,
                DestinationPlan.single(0x121),
                AnimatedTileCachePolicy.ALWAYS,
                ctx -> { });

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> graph.install(List.of(first, second)));
        assertTrue(ex.getMessage().contains("duplicate"));
    }

    @Test
    void phaseSourceAndApplyStrategySeeTheSamePerChannelContextShape() {
        AtomicReference<ChannelContext> phaseContext = new AtomicReference<>();
        AtomicReference<ChannelContext> applyContext = new AtomicReference<>();
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        AnimatedTileChannel channel = new AnimatedTileChannel(
                "shape",
                () -> true,
                ctx -> {
                    phaseContext.set(ctx);
                    return 7;
                },
                DestinationPlan.single(0x120),
                AnimatedTileCachePolicy.ALWAYS,
                ctx -> applyContext.set(ctx));

        graph.install(List.of(channel));
        graph.update(new ChannelContext(null, null, null, null, 3, 4, 5));

        assertNotNull(phaseContext.get(), "phase context");
        assertNotNull(applyContext.get(), "apply context");
        assertNotNull(phaseContext.get().graph(), "phase graph");
        assertNotNull(phaseContext.get().channel(), "phase channel");
        assertSame(applyContext.get().graph(), phaseContext.get().graph(), "graph");
        assertSame(applyContext.get().channel(), phaseContext.get().channel(), "channel");
        assertSame(channel, phaseContext.get().channel(), "expected per-channel context");
    }

    @Test
    void onPhaseChangeChannelSkipsWhenPhaseIsStable() {
        RecordingStrategy strategy = new RecordingStrategy();
        AnimatedTileChannel channel = new AnimatedTileChannel(
                "stable",
                () -> true,
                ctx -> 7,
                DestinationPlan.single(0x120),
                AnimatedTileCachePolicy.ON_PHASE_CHANGE,
                strategy);

        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(List.of(channel));

        ChannelContext ctx = new ChannelContext(null, null, null, null, 0, 0, 0);
        graph.update(ctx);
        graph.update(ctx);

        assertEquals(1, strategy.invocationCount());
    }

    @Test
    void channelsRunInRegistrationOrder() {
        StringBuilder log = new StringBuilder();
        AnimatedTileChannelGraph graph = new AnimatedTileChannelGraph();
        graph.install(List.of(
                new AnimatedTileChannel(
                        "first",
                        () -> true,
                        c -> 1,
                        DestinationPlan.single(0x100),
                        AnimatedTileCachePolicy.ALWAYS,
                        c -> log.append("A")),
                new AnimatedTileChannel(
                        "second",
                        () -> true,
                        c -> 1,
                        DestinationPlan.single(0x101),
                        AnimatedTileCachePolicy.ALWAYS,
                        c -> log.append("B"))));

        graph.update(new ChannelContext(null, null, null, null, 0, 0, 0));
        assertEquals("AB", log.toString());
    }

    private static final class RecordingStrategy implements ApplyStrategy {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void apply(ChannelContext context) {
            invocations.incrementAndGet();
        }

        int invocationCount() {
            return invocations.get();
        }
    }
}
