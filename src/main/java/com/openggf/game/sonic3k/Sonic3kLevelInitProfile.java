package com.openggf.game.sonic3k;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.StaticFixup;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.LevelManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Sonic 3&K level initialization profile.
 * <p>
 * Aligned to the S3K {@code Level:} routine at {@code sonic3k.asm:7505} (65 steps
 * across phases A-Q). The teardown steps undo the state set up by that routine.
 * <p>
 * S3K-specific characteristics:
 * <ul>
 *   <li>Triple decompression queues: Nemesis PLC + Kosinski + KosinskiM</li>
 *   <li>Two-phase level data loading: async KosM art queue, then sync blocks/chunks</li>
 *   <li>Dual-path collision with non-interleaved/interleaved flag ({@code LoadSolids})</li>
 *   <li>Full per-zone {@code LevelSetupArray} dispatch with plane drawing</li>
 *   <li>Player spawn AFTER game state init (phases N→O) — opposite of S1/S2</li>
 *   <li>{@code Level_started_flag} set BEFORE first object frame (phase N, step 45)</li>
 *   <li>Water palette loaded AFTER first object frame (phase Q, step 55)</li>
 *   <li>AIZ intro PLC override and starpost zone restoration (phases B-B2)</li>
 *   <li>{@link AizPlaneIntroInstance} sidekick suppression flag (persists across loads)</li>
 * </ul>
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      Design doc: Sonic 3&amp;K Level Init Profile (65 steps)</a>
 */
public class Sonic3kLevelInitProfile extends AbstractLevelInitProfile {

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        LevelManager lm = LevelManager.getInstance();
        return List.of(
            new InitStep("InitGameModule",
                "S3K Phase A-D (#1-20): cmd_FadeOut, Pal_FadeToBlack, Clear_Nem_Queue, clearRAM, create Game instance",
                () -> { try { lm.initGameModule(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitAudio",
                "S3K Phase F (#25): Play_Music from LevelMusic_Playlist — AIZ1 lamppost 3 music override",
                () -> { try { lm.initAudio(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("LoadLevelData",
                "S3K Phase H-K (#30-38): Get_LevelSizeStart, DeformBgLayer, LoadLevelLoadBlock, LoadLevelLoadBlock2, j_LevelSetup, LoadSolids",
                () -> { try { ctx.setLevel(lm.loadLevelData(ctx.getLevelIndex())); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitAnimatedContent",
                "S3K Phase J (#37): Animate_Init (zone-specific animation counter initialization)",
                lm::initAnimatedContent),
            new InitStep("InitObjectManager",
                "S3K Phase O (#47-48): SpawnLevelMainSprites, Load_Sprites — create ObjectManager, wire CollisionSystem",
                () -> { try { lm.initObjectManager(); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitCameraBounds",
                "S3K Phase H (#32): Get_LevelSizeStart — reset camera bounds from level geometry",
                lm::initCameraBounds),
            new InitStep("InitGameplayState",
                "S3K Phase N (#43-45): Clear game state, OscillateNumInit, Level_started_flag set BEFORE first object frame",
                lm::initGameplayState),
            new InitStep("InitRings",
                "S3K Phase O (#49): Load_Rings — initial ring placement",
                lm::initRings),
            new InitStep("InitZoneFeatures",
                "S3K Phase J (#36): j_LevelSetup → LevelSetupArray per-zone dispatch, HCZ water surface, MHZ pollen",
                () -> { try { lm.initZoneFeatures(); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitArt",
                "S3K Phase C (#12-14): Load_PLC zone, character PLCs, standard PLCs",
                lm::initArt),
            new InitStep("InitPlayerAndCheckpoint",
                "S3K Phase O (#47): SpawnLevelMainSprites — player spawn AFTER game state init (opposite of S1/S2)",
                lm::initPlayerAndCheckpoint),
            new InitStep("InitWater",
                "S3K Phase E (#22): CheckLevelForWater, StartingWaterHeights",
                () -> { try { lm.initWater(); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitBackgroundRenderer",
                "Engine-specific: Pre-allocate BG FBO for AIZ intro ocean-to-beach transition",
                lm::initBackgroundRenderer)
        );
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS3kLevelEvents",
            "Undoes S3K LevelSetupArray dispatch and per-zone event handlers (AIZ, HCZ, etc.)",
            () -> Sonic3kLevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        // Note: S3K level event manager is NOT reset here. The old
        // TestEnvironment.resetPerTest() only called
        // Sonic2LevelEventManager.resetState(), which was a no-op for S3K.
        // Resetting the S3K event manager would destroy zone event handlers
        // (e.g. AIZ events) initialized during the @BeforeClass level load.
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
