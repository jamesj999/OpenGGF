package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;

/**
 * Canonical level-mode frame update sequence.
 * <p>
 * This is the <b>single source of truth</b> for level tick ordering.
 * Both {@link GameLoop} and the headless test runner ({@code HeadlessTestRunner})
 * MUST delegate to this class rather than duplicating the step sequence.
 * <p>
 * Order mirrors the Mega Drive ROM.
 * Inline solid-resolution modules run player physics first, then ExecuteObjects
 * with per-object solid checkpoints so object code sees the post-physics player
 * state during its own update. Legacy compatibility modules keep the older
 * objects-before-physics ordering.
 * <p>
 * ROM reference (sonic.asm:3042-3044): {@code LZWaterFeatures} runs before
 * {@code ExecuteObjects} so that wind tunnel / water slide state is visible
 * to objects and to the player physics that follows.
 */
public final class LevelFrameStep {

    /**
     * Optional wrapper around individual steps, allowing callers to add
     * profiling, logging, or other cross-cutting concerns without altering
     * the canonical ordering.
     */
    @FunctionalInterface
    public interface StepWrapper {
        void wrap(String sectionName, Runnable step);
    }

    private static final StepWrapper DIRECT = (name, step) -> step.run();

    private LevelFrameStep() {
        // Utility class
    }

    /**
     * Executes one frame of level-mode updates in the canonical production order,
     * without any step wrapping.
     *
     * @param levelManager the level manager
     * @param camera       the camera
     * @param spriteUpdate callback that runs the sprite/player physics update
     *                     (e.g. {@code SpriteManager.update()} or headless equivalent)
     */
    public static void execute(LevelManager levelManager, Camera camera,
                               Runnable spriteUpdate) {
        execute(levelManager, camera, spriteUpdate, DIRECT);
    }

    /**
     * Executes one frame of level-mode updates in the canonical production order.
     * <p>
     * Steps are wrapped with the provided {@link StepWrapper}, which receives a
     * section name suitable for profiler labels.
     *
     * @param levelManager the level manager
     * @param camera       the camera
     * @param spriteUpdate callback that runs the sprite/player physics update
     * @param wrapper      wraps individual steps (e.g. for profiling)
     */
    public static void execute(LevelManager levelManager, Camera camera,
                               Runnable spriteUpdate, StepWrapper wrapper) {
        // 0. Process dirty regions from MutableLevel (editor mutations).
        //    No-op when the level is not a MutableLevel — zero impact on gameplay.
        levelManager.processDirtyRegions();

        // 1. Zone features pre-physics — wind tunnels, water slides set
        //    f_slidemode / obInertia before ExecuteObjects and Sonic_Move.
        //    ROM: LZWaterFeatures runs before ExecuteObjects (sonic.asm:3042).
        levelManager.updateZoneFeaturesPrePhysics();

        boolean inlineSolidResolution = levelManager.usesInlineObjectSolidResolution();
        if (inlineSolidResolution) {
            // 2. Inline-order modules need a frame-start snapshot of object touch
            //    state because player-slot ReactToItem runs before ExecuteObjects.
            levelManager.prepareTouchResponseSnapshots();

            // 2. Inline solid-resolution path: player physics first. Touch responses
            //    run per-player inside tickPlayablePhysics after movement, matching
            //    the player-slot-first ROM ordering.
            wrapper.wrap("physics", spriteUpdate);

            // 3. Object execution after player physics, with inline solid checkpoints
            //    so later objects see earlier contact adjustments.
            wrapper.wrap("objects", levelManager::updateObjectPositionsPostPhysicsWithoutTouches);
        } else {
            // 2. Legacy compatibility path keeps objects before physics. Touch
            //    responses are still deferred to tickPlayablePhysics after movement.
            wrapper.wrap("objects", levelManager::updateObjectPositionsWithoutTouches);

            // 3. Sprite / player physics update (caller-provided).
            wrapper.wrap("physics", spriteUpdate);

            // 3b. Some later SST-slot scripts read Sonic after his movement has
            //     completed and only write globals for the next frame. Legacy
            //     object-order modules need an explicit post-player hook for
            //     those cases because regular object updates already ran.
            wrapper.wrap("post-player-hooks", levelManager::updateObjectPostPlayerHooks);
        }

        // 4. Dynamic level events — boss arenas, boundary changes, zone transitions.
        LevelEventProvider levelEvents = GameServices.module().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.update();
        }

        levelManager.flushQueuedLayoutMutations();

        BonusStageProvider bonusStageProvider = GameServices.bonusStage();
        boolean integratedBonusStageUpdate = bonusStageProvider != null
                && bonusStageProvider.updateDuringLevelFrame();
        boolean suppressDefaultCamera = bonusStageProvider != null
                && bonusStageProvider.suppressesDefaultCameraStep();

        if (integratedBonusStageUpdate) {
            bonusStageProvider.onFrameUpdate();
        }

        // 5. Camera — ease boundaries toward targets, then reposition.
        if (!suppressDefaultCamera) {
            wrapper.wrap("camera", () -> {
                camera.updateBoundaryEasing();
                camera.updatePosition();
            });
        }

        // 5b. Post-camera placement catch-up — extend the spawn window with the
        //     post-camera position. ROM: ObjPosLoad runs after DeformLayers
        //     (camera update), so spawns see the updated camera. When the camera
        //     crosses a chunk boundary between step 2 and step 5, this catches
        //     spawns that the pre-camera placement missed. No-op when the camera
        //     chunk hasn't changed.
        levelManager.postCameraObjectPlacementSync();

        // 6. Level scroll / parallax / animation update.
        wrapper.wrap("level", levelManager::update);

        // 7. Cache BuildSprites on-screen results for next frame's logic.
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (spriteManager != null) {
            spriteManager.refreshPlayableRenderFlags(camera);
        }
    }
}
