package com.openggf;

import com.openggf.camera.Camera;
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
 * Order mirrors the Mega Drive ROM: ZoneFeatures &rarr; ExecuteObjects &rarr;
 * PlayerPhysics &rarr; LevelEvents &rarr; Camera &rarr; LevelScroll.
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
        // 1. Zone features pre-physics — wind tunnels, water slides set
        //    f_slidemode / obInertia before ExecuteObjects and Sonic_Move.
        //    ROM: LZWaterFeatures runs before ExecuteObjects (sonic.asm:3042).
        levelManager.updateZoneFeaturesPrePhysics();

        // 2. Object positions — must update BEFORE player physics so
        //    SolidContacts sees new positions (fixes 1-frame platform lag).
        //    NOTE: In the ROM, Sonic (slot 0) runs first within ExecuteObjects,
        //    so objects see his post-physics position. Our engine separates
        //    physics from objects, creating an ordering difference. To compensate,
        //    SolidContacts applies a velocity offset to the player position for
        //    contact classification, simulating the ROM's post-physics check.
        wrapper.wrap("objects", levelManager::updateObjectPositions);

        // 3. Sprite / player physics update (caller-provided).
        wrapper.wrap("physics", spriteUpdate);

        // 4. Dynamic level events — boss arenas, boundary changes, zone transitions.
        LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.update();
        }

        // 5. Camera — ease boundaries toward targets, then reposition.
        wrapper.wrap("camera", () -> {
            camera.updateBoundaryEasing();
            camera.updatePosition();
        });

        // 6. Level scroll / parallax / animation update.
        wrapper.wrap("level", levelManager::update);
    }
}
