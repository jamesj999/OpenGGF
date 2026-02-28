package com.openggf.game.sonic2;

import com.openggf.game.AbstractLevelInitProfile;
import com.openggf.game.InitStep;
import com.openggf.game.LevelLoadContext;
import com.openggf.level.LevelManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Sonic 2 level initialization profile.
 * <p>
 * Aligned to the S2 {@code Level:} routine at {@code s2.asm:4753} (57 steps
 * across phases A-J). The teardown steps undo the state set up by that routine.
 * <p>
 * S2-specific characteristics:
 * <ul>
 *   <li>Single PLC queue ({@code LoadPLC}/{@code RunPLC_RAM})</li>
 *   <li>Dual-path collision model ({@code LoadCollisionIndexes} → PRIMARY + SECONDARY)</li>
 *   <li>Zone-specific setup: CPZ pylon ({@code ObjID_CPZPylon}), OOZ oil surface</li>
 *   <li>Player spawn BEFORE game state init (phases G→H)</li>
 *   <li>{@code Level_started_flag} set AFTER title card exit (phase J, step 56)</li>
 * </ul>
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      Design doc: Sonic 2 Level Init Profile (57 steps)</a>
 */
public class Sonic2LevelInitProfile extends AbstractLevelInitProfile {

    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        LevelManager lm = LevelManager.getInstance();
        return List.of(
            new InitStep("InitGameModule",
                "S2 Phase A (#1-4): Pal_FadeToBlack, ClearPLC, clear variables, create Game instance",
                () -> { try { lm.initGameModule(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitAudio",
                "S2 Phase C (#21): Level_SetPlayerMode, PlayMusic — configure audio manager and play level music",
                () -> { try { lm.initAudio(ctx.getLevelIndex()); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("LoadLevelData",
                "S2 Phase E-F (#26-35): LevelDataLoad, LoadZoneTiles, LoadCollisionIndexes",
                () -> { try { ctx.setLevel(lm.loadLevelData(ctx.getLevelIndex())); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitAnimatedContent",
                "S2 Phase E (#32): LoadAnimatedBlocks (pattern animation scripts + palette cycling)",
                lm::initAnimatedContent),
            new InitStep("InitObjectManager",
                "S2 Phase G (#36-38): InitPlayers, WaterEffects, create ObjectManager, wire CollisionSystem",
                () -> { try { lm.initObjectManager(); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitCameraBounds",
                "S2 Phase E (#26): LevelSizeLoad — reset camera bounds from level geometry",
                lm::initCameraBounds),
            new InitStep("InitGameplayState",
                "S2 Phase H (#41-43): OscillateNumInit, clear rings/time/lives, HUD update flags",
                lm::initGameplayState),
            new InitStep("InitRings",
                "S2 Phase I (#45): RingsManager — initial ring placement and pattern caching",
                lm::initRings),
            new InitStep("InitZoneFeatures",
                "S2 Phase G (#39-40): WaterSurface, CPZ Pylon, OOZ Oil, CNZ bumpers",
                () -> { try { lm.initZoneFeatures(); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitArt",
                "S2 Phase C (#8-9): ObjectArt (zone PLC), PlayerSpriteArt",
                lm::initArt),
            new InitStep("InitPlayerAndCheckpoint",
                "S2 Phase G (#37) + H (#41): ResetPlayerState, checkpoint clear, LevelGamestate",
                lm::initPlayerAndCheckpoint),
            new InitStep("InitWater",
                "S2 Phase B (#13,18): WaterSystem loading for water zones (LZ, HPZ, CPZ)",
                () -> { try { lm.initWater(); } catch (IOException e) { throw new UncheckedIOException(e); } }),
            new InitStep("InitBackgroundRenderer",
                "Engine-specific: Pre-allocate BG FBO at maximum required size",
                lm::initBackgroundRenderer)
        );
    }

    @Override
    protected InitStep levelEventTeardownStep() {
        return new InitStep("ResetS2LevelEvents",
            "Undoes S2 zone event handlers (HTZ earthquake, boss arenas, CPZ/ARZ/CNZ events)",
            () -> Sonic2LevelEventManager.getInstance().resetState());
    }

    @Override
    protected InitStep perTestLeadStep() {
        return new InitStep("ResetS2LevelEvents",
            "Undoes S2 zone event handlers (HTZ earthquake, boss arenas, CPZ/ARZ/CNZ events)",
            () -> Sonic2LevelEventManager.getInstance().resetState());
    }
}
