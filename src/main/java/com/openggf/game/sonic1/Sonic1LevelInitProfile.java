package com.openggf.game.sonic1;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.level.LevelManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Sonic 1 level initialization profile.
 * <p>
 * Aligned to the S1 {@code Level:} routine at {@code sonic.asm:2956} (44 steps
 * across phases A-L). The teardown steps undo the state set up by that routine.
 * <p>
 * S1-specific characteristics:
 * <ul>
 *   <li>Direct Nemesis decompression for title card art (no PLC queue)</li>
 *   <li>Unified collision model (single index, no dual-path switching)</li>
 *   <li>Single-phase data load ({@code LevelDataLoad} — all geometry in one call)</li>
 *   <li>Water only in LZ/SBZ3 ({@code LZWaterFeatures})</li>
 *   <li>Object placement BEFORE game state clear (phases J→K)</li>
 *   <li>Unique 4-frame VBla delay before fade-in (phase L, step 41)</li>
 *   <li>No sidekick (Tails) — step 19 (SpawnSidekick) omitted</li>
 * </ul>
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      Design doc: Sonic 1 Level Init Profile (44 steps)</a>
 */
public class Sonic1LevelInitProfile extends AbstractLevelInitProfile {

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        LevelManager lm = LevelManager.getInstance();
        List<InitStep> steps = new ArrayList<>(19);
        steps.add(new InitStep("InitGameModule",
                "S1 Phase A (#1-4): bgm_Fade, ClearPLC, PaletteFadeOut, create Game instance",
                () -> { try { lm.initGameModule(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitAudio",
                "S1 Phase D (#15): QueueSound1 from MusicList — SBZ3/FZ music overrides",
                () -> { try { lm.initAudio(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("LoadLevelData",
                "S1 Phase G-H (#20-26): LevelSizeLoad, DeformLayers, LevelDataLoad, ConvertCollisionArray, ColIndexLoad",
                () -> { try { ctx.setLevel(lm.loadLevelData(ctx.getLevelIndex())); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitAnimatedContent",
                "S1 Phase G: LoadAnimatedBlocks (S1 loads animation scripts during LevelDataLoad)",
                lm::initAnimatedContent));
        steps.add(new InitStep("InitObjectManager",
                "S1 Phase I (#28-32): Spawn Sonic, HUD, create ObjectManager, wire CollisionSystem",
                () -> { try { lm.initObjectManager(); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitCameraBounds",
                "S1 Phase G (#20): LevelSizeLoad — reset camera bounds from level geometry",
                lm::initCameraBounds));
        steps.add(new InitStep("InitGameplayState",
                "S1 Phase K (#36-38): OscillateNumInit, clear game state, HUD update flags",
                lm::initGameplayState));
        steps.add(new InitStep("InitRings",
                "S1 Phase J (#33): ObjPosLoad — rings are objects in S1, placed via ObjectManager",
                lm::initRings));
        steps.add(new InitStep("InitZoneFeatures",
                "S1 Phase I (#32): LZ water surface objects, zone-specific features",
                () -> { try { lm.initZoneFeatures(); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitArt",
                "S1 Phase A (#6-7): Zone PLC, plcid_Main2 (shared HUD/ring/monitor patterns)",
                lm::initArt));
        steps.add(new InitStep("InitPlayerAndCheckpoint",
                "S1 Phase I (#28): Spawn Sonic, reset player state, checkpoint clear",
                lm::initPlayerAndCheckpoint));
        steps.add(new InitStep("InitWater",
                "S1 Phase C (#11): LZ water check, WaterHeight table, Water_flag",
                () -> { try { lm.initWater(); } catch (IOException e) { throw new UncheckedIOException(e); } }));
        steps.add(new InitStep("InitBackgroundRenderer",
                "Engine-specific: Pre-allocate BG FBO",
                lm::initBackgroundRenderer));
        // Post-load assembly: 6 steps (no SpawnSidekick — S1 has no Tails)
        if (ctx.isIncludePostLoadAssembly()) {
            steps.add(restoreCheckpointStep(ctx));
            steps.add(spawnPlayerStep(ctx));
            steps.add(resetPlayerStateStep(ctx));
            steps.add(initCameraStep());
            steps.add(initLevelEventsStep());
            steps.add(requestTitleCardStep(ctx));
        }
        return List.copyOf(steps);
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS1LevelEvents",
            "Undoes S1 per-zone event handlers (GHZ/MZ/SYZ/LZ/SLZ/SBZ/FZ events)",
            () -> Sonic1LevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS1LevelEvents",
            "Undoes S1 per-zone event handlers (GHZ/MZ/SYZ/LZ/SLZ/SBZ/FZ events)",
            () -> Sonic1LevelEventManager.getInstance().resetState());
    }
}
