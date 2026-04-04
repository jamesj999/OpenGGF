package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;

import java.util.List;
import com.openggf.game.GameServices;

/**
 * Sonic 3&K level initialization profile.
 * <p>
 * Aligned to the S3K {@code Level:} routine at {@code sonic3k.asm:7505} (65 steps
 * across phases A-Q). The teardown steps undo the state set up by that routine.
 * <p>
 * S3K-specific post-load characteristics:
 * <ul>
 *   <li>Sidekick X offset: -32px (ROM: {@code $20}), not S2's -40px</li>
 *   <li>Title card skipped on checkpoint resume (ROM: {@code tst.b (Last_star_post_hit)})</li>
 * </ul>
 */
public class Sonic3kLevelInitProfile extends AbstractLevelInitProfile {

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        List<InitStep> steps = buildCoreSteps(ctx);
        // Post-load assembly: only when requested
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(initCameraStep());
            steps.add(initLevelEventsStep());
            steps.add(spawnSidekickStep());
            steps.add(initZonePlayerStateStep());
            steps.add(requestTitleCardStep(ctx));
        }
        return List.copyOf(steps);
    }

    /**
     * ROM: SpawnLevelMainSprites zone-specific player state (sonic3k.asm:8132).
     * Runs after sidekick spawn so both main player and sidekicks exist.
     * Sets falling animation, airborne flag, and jumping for zone intros
     * (HCZ1, MGZ1, LRZ1/Knuckles, SSZ).
     */
    protected InitStep initZonePlayerStateStep() {
        return new InitStep("InitZonePlayerState",
            "S3K: SpawnLevelMainSprites — zone-specific player animation/air state",
            () -> Sonic3kLevelEventManager.getInstance().applyZonePlayerState());
    }

    /** S3K sidekick: -32px X, +4px Y (ROM: {@code player_pos - $20}, {@code player_pos + 4}). */
    @Override
    protected InitStep spawnSidekickStep() {
        return new InitStep("SpawnSidekick",
            "S3K: SpawnLevelMainSprites_SpawnPlayers — Tails at player_pos - $20, +4 Y",
            () -> GameServices.level().spawnSidekicks(-32, 4));
    }

    /** S3K: skip title card on checkpoint resume (ROM: {@code tst.b (Last_star_post_hit)}). */
    @Override
    protected InitStep requestTitleCardStep(LevelLoadContext ctx) {
        return new InitStep("RequestTitleCard",
            "S3K: Obj_TitleCard — skipped on checkpoint resume",
            () -> {
                if (!ctx.hasCheckpoint()) {
                    GameServices.level().requestTitleCardIfNeeded(ctx);
                }
            });
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS3kLevelEvents",
                "Undoes S3K LevelSetupArray dispatch and per-zone event handlers (AIZ, HCZ, etc.)",
                () -> Sonic3kLevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetAizSidekickSuppression",
                "Resets all AizPlaneIntroInstance static phase state (scroll, terrain swap, decompression, sidekick suppression)",
                AizPlaneIntroInstance::resetIntroPhaseState);
    }

    @Override
    protected List<StaticFixup> gameSpecificFixups() {
        return List.of(
                new StaticFixup("ResetAizSidekickSuppression",
                        "AIZ intro sets sidekick suppression flag that persists across level loads",
                        () -> AizPlaneIntroInstance.setSidekickSuppressed(false))
        );
    }
}
