package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.LevelManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

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
        LevelManager lm = LevelManager.getInstance();
        List<InitStep> steps = new ArrayList<>(20);
        steps.add(new InitStep("InitGameModule",
                "S3K Phase A-D (#1-20): cmd_FadeOut, Pal_FadeToBlack, Clear_Nem_Queue, clearRAM, create Game instance",
                () -> { try { lm.initGameModule(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitAudio",
                "S3K Phase F (#25): Play_Music from LevelMusic_Playlist - AIZ1 lamppost 3 music override",
                () -> { try { lm.initAudio(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("LoadLevelData",
                "S3K Phase H-K (#30-38): Get_LevelSizeStart, DeformBgLayer, LoadLevelLoadBlock, LoadLevelLoadBlock2, j_LevelSetup, LoadSolids",
                () -> { try { ctx.setLevel(lm.loadLevelData(ctx.getLevelIndex())); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitAnimatedContent",
                "S3K Phase J (#37): Animate_Init (zone-specific animation counter initialization)",
                lm::initAnimatedContent));
        steps.add(new InitStep("InitObjectManager",
                "S3K Phase O (#47-48): SpawnLevelMainSprites, Load_Sprites - create ObjectManager, wire CollisionSystem",
                () -> { try { lm.initObjectManager(); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitCameraBounds",
                "S3K Phase H (#32): Get_LevelSizeStart - reset camera bounds from level geometry",
                lm::initCameraBounds));
        steps.add(new InitStep("InitGameplayState",
                "S3K Phase N (#43-45): Clear game state, OscillateNumInit, Level_started_flag set before first object frame",
                lm::initGameplayState));
        steps.add(new InitStep("InitRings",
                "S3K Phase O (#49): Load_Rings - initial ring placement",
                lm::initRings));
        steps.add(new InitStep("InitZoneFeatures",
                "S3K Phase J (#36): j_LevelSetup -> LevelSetupArray per-zone dispatch, HCZ water surface, MHZ pollen",
                () -> { try { lm.initZoneFeatures(); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitArt",
                "S3K Phase C (#12-14): Load_PLC zone, character PLCs, standard PLCs",
                lm::initArt));

        steps.add(new InitStep("InitPlayerAndCheckpoint",
                "S3K Phase O (#47): SpawnLevelMainSprites - player spawn after game state init",
                lm::initPlayerAndCheckpoint));

        steps.add(new InitStep("InitWater",
                "S3K Phase E (#22): CheckLevelForWater, StartingWaterHeights",
                () -> { try { lm.initWater(); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitBackgroundRenderer",
                "Engine-specific: Pre-allocate BG FBO for AIZ intro ocean-to-beach transition",
                lm::initBackgroundRenderer));

        // Post-load assembly: only when requested
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(initCameraStep());
            steps.add(initLevelEventsStep());
            steps.add(spawnSidekickStep());
            steps.add(requestTitleCardStep(ctx));
        }

        return List.copyOf(steps);
    }

    /** S3K sidekick: -32px X, +4px Y (ROM: {@code player_pos - $20}, {@code player_pos + 4}). */
    @Override
    protected InitStep spawnSidekickStep() {
        return new InitStep("SpawnSidekick",
            "S3K: SpawnLevelMainSprites_SpawnPlayers — Tails at player_pos - $20, +4 Y",
            () -> LevelManager.getInstance().spawnSidekick(-32, 4));
    }

    /** S3K: skip title card on checkpoint resume (ROM: {@code tst.b (Last_star_post_hit)}). */
    @Override
    protected InitStep requestTitleCardStep(LevelLoadContext ctx) {
        return new InitStep("RequestTitleCard",
            "S3K: Obj_TitleCard — skipped on checkpoint resume",
            () -> {
                if (!ctx.hasCheckpoint()) {
                    LevelManager.getInstance().requestTitleCardIfNeeded(ctx);
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
                "Undoes AizPlaneIntroInstance.setSidekickSuppressed(true) from AIZ intro",
                () -> AizPlaneIntroInstance.setSidekickSuppressed(false));
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
