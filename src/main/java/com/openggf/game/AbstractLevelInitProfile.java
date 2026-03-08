package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared teardown and per-test reset logic for all game profiles.
 * <p>
 * Each Mega Drive Sonic game has a well-documented level initialization
 * routine ({@code Level:} in the disassembly). The teardown steps here
 * are the <em>inverse</em> of those ROM init phases — they undo what
 * the ROM's {@code Level:} routine sets up:
 * <table>
 *   <tr><th>Teardown step</th><th>Undoes ROM phase</th></tr>
 *   <tr><td>ResetAudio</td><td>Palette &amp; Music (S1 Phase D, S2 Phase C, S3K Phase F)</td></tr>
 *   <tr><td><em>levelEventTeardownStep</em></td><td>Zone-specific setup / title card state</td></tr>
 *   <tr><td>ResetParallax</td><td>DeformBgLayer / DeformLayers (S1 Phase G, S2 Phase E, S3K Phase H)</td></tr>
 *   <tr><td>ResetLevelManager</td><td>Level geometry loading (S1 Phase G, S2 Phase E, S3K Phase I)</td></tr>
 *   <tr><td>ResetSprites</td><td>Player &amp; object spawning (S1 Phase I-J, S2 Phase G, S3K Phase O)</td></tr>
 *   <tr><td>ResetCollision</td><td>Collision index loading (S1 Phase H, S2 Phase F, S3K Phase K)</td></tr>
 *   <tr><td>ResetCamera</td><td>Level boundaries / camera init (S1 Phase G, S2 Phase E, S3K Phase H)</td></tr>
 *   <tr><td>ResetGraphics</td><td>VDP / hardware setup (S1 Phase B, S2 Phase B, S3K Phase D)</td></tr>
 *   <tr><td>ResetFade</td><td>Palette fade state (S1 Phase A, S2 Phase A, S3K Phase A)</td></tr>
 *   <tr><td>ResetGameState</td><td>Game state init (S1 Phase K, S2 Phase H, S3K Phase N)</td></tr>
 *   <tr><td>ResetTimers</td><td>First-frame timing (S1 Phase J, S2 Phase I, S3K Phase P)</td></tr>
 *   <tr><td>ResetWater</td><td>Water initialization (S1 Phase C, S2 Phase B, S3K Phase E/L)</td></tr>
 * </table>
 * <p>
 * Per-test reset is a subset that clears transient gameplay state while
 * preserving loaded level data (geometry, art, collision indices).
 * <p>
 * Subclasses provide only the game-specific steps via three hooks:
 * {@link #levelEventTeardownStep()}, {@link #perTestLeadStep()}, and
 * {@link #gameSpecificFixups()}.
 *
 * @see <a href="docs/plans/2026-02-27-rom-driven-init-profiles-design.md">
 *      ROM-Driven Init Profiles Design (full ROM step-by-step reference)</a>
 */
public abstract class AbstractLevelInitProfile implements LevelInitProfile {

    /** Game-specific level event manager reset (teardown index 1). */
    protected abstract InitStep levelEventTeardownStep();

    /** Game-specific first step for per-test reset. */
    protected abstract InitStep perTestLeadStep();

    /** Additional fixups beyond WireGroundSensor. Override to add game-specific ones. */
    protected List<StaticFixup> gameSpecificFixups() {
        return List.of();
    }

    // Not final: subclasses provide game-specific level load steps.
    @Override
    public List<InitStep> levelLoadSteps(LevelLoadContext ctx) {
        return List.of();
    }

    // ── Post-load assembly step factories ────────────────────────────────
    // Steps 14-20: post-resource-load assembly (checkpoint, player spawn,
    // state reset, camera, level events, sidekick, title card).
    // Subclasses override individual factories for game-specific behavior.

    /** Step 14: Restore checkpoint state after loadLevel() clears it. */
    protected InitStep restoreCheckpointStep(LevelLoadContext ctx) {
        return new InitStep("RestoreCheckpoint",
            "S1: Lamp_LoadInfo, S2: Obj79_LoadData, S3K: Saved_zone_and_act restore",
            () -> LevelManager.getInstance().restoreCheckpointState(ctx));
    }

    /** Step 15: Set player position from checkpoint or level start. */
    protected InitStep spawnPlayerStep(LevelLoadContext ctx) {
        return new InitStep("SpawnPlayer",
            "S1/S2: StartLocations / Obj79_LoadData, S3K: Get_PlayerStart",
            () -> LevelManager.getInstance().spawnPlayerAtStartPosition(ctx));
    }

    /** Step 16: Reset player state for level start. */
    protected InitStep resetPlayerStateStep(LevelLoadContext ctx) {
        return new InitStep("ResetPlayerState",
            "S2: InitPlayers state clear, S3K: object constructor defaults",
            () -> LevelManager.getInstance().resetPlayerForLevelStart(ctx));
    }

    /** Step 17: Initialize camera for level start. */
    protected InitStep initCameraStep() {
        return new InitStep("InitCamera",
            "S1/S2: SetScreen/InitCameraValues, S3K: Get_LevelSizeStart",
            () -> LevelManager.getInstance().initCameraForLevel());
    }

    /** Step 18: Initialize level events for dynamic boundary updates. */
    protected InitStep initLevelEventsStep() {
        return new InitStep("InitLevelEvents",
            "All: LevelEventProvider.initLevel(zone, act)",
            () -> LevelManager.getInstance().initLevelEventsForLevel());
    }

    /** Step 19: Spawn sidekick near the main player. Override for game-specific offset. */
    protected InitStep spawnSidekickStep() {
        return new InitStep("SpawnSidekick",
            "S2: InitPlayers multi-char, S3K: SpawnLevelMainSprites_SpawnPlayers",
            () -> LevelManager.getInstance().spawnSidekick(-40, 0));
    }

    /** Step 20: Request title card display. */
    protected InitStep requestTitleCardStep(LevelLoadContext ctx) {
        return new InitStep("RequestTitleCard",
            "S1/S2: title card loop, S3K: Obj_TitleCard",
            () -> LevelManager.getInstance().requestTitleCardIfNeeded(ctx));
    }

    /**
     * Returns the standard 7 post-load assembly steps (14-20).
     * Subclasses can call this and filter/modify as needed.
     */
    protected List<InitStep> postLoadAssemblySteps(LevelLoadContext ctx) {
        return List.of(
            restoreCheckpointStep(ctx),
            spawnPlayerStep(ctx),
            resetPlayerStateStep(ctx),
            initCameraStep(),
            initLevelEventsStep(),
            spawnSidekickStep(),
            requestTitleCardStep(ctx)
        );
    }

    @Override
    public final List<InitStep> levelTeardownSteps() {
        return List.of(
            // Undoes S1:Phase D / S2:Phase C / S3K:Phase F (PlayMusic)
            new InitStep("ResetAudio", "Undoes PlayMusic / bgm_Fade",
                () -> AudioManager.getInstance().resetState()),

            // Game-specific: undoes zone event handlers, boss arena state
            levelEventTeardownStep(),

            // Undoes S1:Phase G / S2:Phase E / S3K:Phase H (DeformBgLayer/DeformLayers)
            new InitStep("ResetParallax", "Undoes DeformBgLayer init",
                () -> ParallaxManager.getInstance().resetState()),
            // Undoes S1:Phase G / S2:Phase E / S3K:Phase I (LevelDataLoad/LoadZoneTiles)
            new InitStep("ResetLevelManager", "Undoes LevelDataLoad / LoadZoneTiles / LoadLevelLoadBlock",
                () -> LevelManager.getInstance().resetState()),

            // Undoes S1:Phase I-J / S2:Phase G / S3K:Phase O (InitPlayers/SpawnLevelMainSprites)
            new InitStep("ResetSprites", "Undoes InitPlayers / SpawnLevelMainSprites",
                () -> SpriteManager.getInstance().resetState()),

            // Undoes S1:Phase H / S2:Phase F / S3K:Phase K (ConvertCollisionArray/LoadSolids)
            new InitStep("ResetCollision", "Undoes ConvertCollisionArray / LoadCollisionIndexes / LoadSolids",
                CollisionSystem::resetInstance),

            // Undoes S1:Phase G / S2:Phase E / S3K:Phase H (LevelSizeLoad/Get_LevelSizeStart)
            new InitStep("ResetCamera", "Undoes LevelSizeLoad / Get_LevelSizeStart",
                () -> Camera.getInstance().resetState()),
            // Undoes S1:Phase B / S2:Phase B / S3K:Phase D (VDP register config)
            new InitStep("ResetGraphics", "Undoes VDP register / ClearScreen / Clear_DisplayData",
                () -> GraphicsManager.getInstance().resetState()),
            // Undoes S1:Phase A / S2:Phase A / S3K:Phase A (PaletteFadeOut/Pal_FadeToBlack)
            new InitStep("ResetFade", "Undoes PaletteFadeOut / Pal_FadeToBlack",
                FadeManager::resetInstance),

            // Undoes S1:Phase K / S2:Phase H / S3K:Phase N (game state clear)
            new InitStep("ResetGameState", "Undoes ring/timer/lives init from Level:",
                () -> GameServices.gameState().resetSession()),
            // Undoes S1:Phase J / S2:Phase I / S3K:Phase P (first frame timing)
            new InitStep("ResetTimers", "Undoes Level_frame_counter / demo timer",
                () -> TimerManager.getInstance().resetState()),
            // Undoes S1:Phase C / S2:Phase B / S3K:Phase E,L (water init)
            new InitStep("ResetWater", "Undoes LZWaterFeatures / WaterEffects / Handle_Onscreen_Water_Height",
                () -> WaterSystem.getInstance().reset())
        );
    }

    @Override
    public final List<InitStep> perTestResetSteps() {
        return List.of(
            perTestLeadStep(),

            new InitStep("ResetParallax", "Undoes DeformBgLayer init",
                () -> ParallaxManager.getInstance().resetState()),
            new InitStep("ResetSprites", "Undoes InitPlayers / SpawnLevelMainSprites",
                () -> SpriteManager.getInstance().resetState()),
            new InitStep("ResetCollision", "Undoes ConvertCollisionArray / LoadSolids",
                CollisionSystem::resetInstance),
            new InitStep("ResetCamera", "Undoes LevelSizeLoad / Get_LevelSizeStart",
                () -> Camera.getInstance().resetState()),
            new InitStep("ResetFade", "Undoes PaletteFadeOut / Pal_FadeToBlack",
                FadeManager::resetInstance),
            new InitStep("ResetGameState", "Undoes ring/timer/lives init from Level:",
                () -> GameServices.gameState().resetSession()),
            new InitStep("ResetTimers", "Undoes Level_frame_counter / demo timer",
                () -> TimerManager.getInstance().resetState()),
            new InitStep("ResetWater", "Undoes LZWaterFeatures / WaterEffects / Handle_Onscreen_Water_Height",
                () -> WaterSystem.getInstance().reset())
        );
    }

    @Override
    public final List<StaticFixup> postTeardownFixups() {
        List<StaticFixup> extra = gameSpecificFixups();
        if (extra.isEmpty()) {
            return List.of(
                new StaticFixup("WireGroundSensor",
                    "GroundSensor uses static LevelManager ref that goes stale after resetState()",
                    () -> GroundSensor.setLevelManager(LevelManager.getInstance()))
            );
        }
        var fixups = new ArrayList<StaticFixup>(1 + extra.size());
        fixups.add(new StaticFixup("WireGroundSensor",
            "GroundSensor uses static LevelManager ref that goes stale after resetState()",
            () -> GroundSensor.setLevelManager(LevelManager.getInstance())));
        fixups.addAll(extra);
        return List.copyOf(fixups);
    }

    /** Empty profile used as safe default by {@link GameModule#getLevelInitProfile()}. */
    public static final LevelInitProfile EMPTY = new LevelInitProfile() {
        @Override public List<InitStep> levelLoadSteps(LevelLoadContext ctx) { return List.of(); }
        @Override public List<InitStep> levelTeardownSteps() { return List.of(); }
        @Override public List<InitStep> perTestResetSteps() { return List.of(); }
        @Override public List<StaticFixup> postTeardownFixups() { return List.of(); }
    };
}
