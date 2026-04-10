package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.LevelEventProvider;
import com.openggf.level.LevelManager;

/**
 * Canonical level-mode frame update sequence.
 * <p>
 * This is the <b>single source of truth</b> for level tick ordering.
 * Both {@link GameLoop} and the headless test runner ({@code HeadlessTestRunner})
 * MUST delegate to this class rather than duplicating the step sequence.
 * <p>
 * Order mirrors the Mega Drive ROM, but differs by collision model:
 * S1 runs ExecuteObjects before PlayerPhysics in this engine's unified path,
 * while S2/S3K run PlayerPhysics first, then ExecuteObjects with inline solid
 * resolution. Both flows converge before level events, camera, and scroll.
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
            // 2. S2/S3K player physics first. Touch responses run per-player inside
            //    tickPlayablePhysics after movement, matching Sonic's slot ordering.
            wrapper.wrap("physics", spriteUpdate);

            // 3. S2/S3K object execution after player physics, with inline solid
            //    resolution so later objects see earlier contact adjustments.
            wrapper.wrap("objects", levelManager::updateObjectPositionsPostPhysicsWithoutTouches);
        } else {
            // 2. S1 unified path keeps objects before physics. Touch responses are
            //    still deferred to tickPlayablePhysics after movement.
            wrapper.wrap("objects", levelManager::updateObjectPositionsWithoutTouches);

            // 3. Sprite / player physics update (caller-provided).
            wrapper.wrap("physics", spriteUpdate);
        }

        // 4. Dynamic level events — boss arenas, boundary changes, zone transitions.
        LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.update();
        }

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
    }
}
