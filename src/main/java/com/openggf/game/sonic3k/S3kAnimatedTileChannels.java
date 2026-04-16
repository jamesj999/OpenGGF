package com.openggf.game.sonic3k;

import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.DestinationPlan;
import com.openggf.game.animation.strategies.ComposedTransferApplyStrategy;
import com.openggf.game.animation.strategies.SplitTransferApplyStrategy;
import com.openggf.level.animation.AniPlcScriptState;

import java.util.ArrayList;
import java.util.List;

final class S3kAnimatedTileChannels {
    private S3kAnimatedTileChannels() {
    }

    static List<AnimatedTileChannel> buildMgzChannels(Sonic3kPatternAnimator owner,
                                                      List<AniPlcScriptState> scripts) {
        List<AnimatedTileChannel> channels = new ArrayList<>(scripts.size());
        for (int i = 0; i < scripts.size(); i++) {
            AniPlcScriptState script = scripts.get(i);
            channels.add(new AnimatedTileChannel(
                    "s3k.mgz.script." + i,
                    owner::shouldRunMgzScriptChannels,
                    ctx -> ctx.frameCounter(),
                    scriptDestination(script),
                    AnimatedTileCachePolicy.ALWAYS,
                    ctx -> owner.tickScript(script)
            ));
        }
        return channels;
    }

    static List<AnimatedTileChannel> buildHczChannels(Sonic3kPatternAnimator owner,
                                                      List<AniPlcScriptState> scripts,
                                                      int actIndex) {
        List<AnimatedTileChannel> channels = new ArrayList<>(scripts.size() + 1);
        for (int i = 0; i < scripts.size(); i++) {
            AniPlcScriptState script = scripts.get(i);
            channels.add(new AnimatedTileChannel(
                    "s3k.hcz.script." + i,
                    owner::shouldRunScriptChannels,
                    ctx -> ctx.frameCounter(),
                    scriptDestination(script),
                    AnimatedTileCachePolicy.ALWAYS,
                    ctx -> owner.tickScript(script)
            ));
        }

        if (actIndex == 0) {
            channels.add(new AnimatedTileChannel(
                    "s3k.hcz1.waterline",
                    owner::shouldRunHcz1CustomChannels,
                    ctx -> owner.computeHcz1WaterlineDelta(),
                    new DestinationPlan(0x2DC, 0x30B),
                    AnimatedTileCachePolicy.ON_PHASE_CHANGE,
                    new ComposedTransferApplyStrategy(owner::updateHcz1BackgroundStripsForGraph)
            ));
        } else {
            channels.add(new AnimatedTileChannel(
                    "s3k.hcz2.strips",
                    owner::shouldRunHcz2CustomChannels,
                    ctx -> owner.computeHcz2CompositePhase(),
                    new DestinationPlan(0x2D2, 0x31D),
                    AnimatedTileCachePolicy.ON_PHASE_CHANGE,
                    new SplitTransferApplyStrategy(owner::updateHcz2BackgroundStripsForGraph)
            ));
        }

        return channels;
    }

    static List<AnimatedTileChannel> buildSozChannels(Sonic3kPatternAnimator owner,
                                                      List<AniPlcScriptState> scripts) {
        List<AnimatedTileChannel> channels = new ArrayList<>(scripts.size() + 1);
        for (int i = 0; i < scripts.size(); i++) {
            AniPlcScriptState script = scripts.get(i);
            channels.add(new AnimatedTileChannel(
                    "s3k.soz.script." + i,
                    owner::shouldRunScriptChannels,
                    ctx -> ctx.frameCounter(),
                    scriptDestination(script),
                    AnimatedTileCachePolicy.ALWAYS,
                    ctx -> owner.tickScript(script)
            ));
        }

        channels.add(new AnimatedTileChannel(
                "s3k.soz1.scroll",
                owner::shouldRunSoz1CustomChannels,
                ctx -> owner.computeSoz1Phase(),
                new DestinationPlan(0x330, 0x33E),
                AnimatedTileCachePolicy.ON_PHASE_CHANGE,
                new SplitTransferApplyStrategy(owner::updateSoz1BackgroundTilesForGraph)
        ));

        return channels;
    }

    private static DestinationPlan scriptDestination(AniPlcScriptState script) {
        int startTile = script.destinationTileIndex();
        if (script.tilesPerFrame() <= 1) {
            return DestinationPlan.single(startTile);
        }
        return new DestinationPlan(startTile, startTile + script.tilesPerFrame() - 1);
    }
}
