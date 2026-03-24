package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Builder-pattern test fixture that encapsulates the per-test boilerplate
 * found in headless test classes (sprite creation, camera setup, level event
 * initialization, HeadlessTestRunner wiring).
 */
public final class HeadlessTestFixture {

    private final GameRuntime runtime;
    private final HeadlessTestRunner runner;
    private final AbstractPlayableSprite sprite;

    private HeadlessTestFixture(GameRuntime runtime, HeadlessTestRunner runner,
                                AbstractPlayableSprite sprite) {
        this.runtime = runtime;
        this.runner = runner;
        this.sprite = sprite;
    }

    /** Returns a new builder for constructing a fixture. */
    public static Builder builder() {
        return new Builder();
    }

    // ---- Convenience delegates ----

    public void stepFrame(boolean up, boolean down, boolean left,
                          boolean right, boolean jump) {
        runner.stepFrame(up, down, left, right, jump);
    }

    public void stepIdleFrames(int count) {
        runner.stepIdleFrames(count);
    }

    /** Returns the playable sprite managed by this fixture. */
    public AbstractPlayableSprite sprite() {
        return sprite;
    }

    /** Returns the camera from the runtime. */
    public Camera camera() {
        return runtime.getCamera();
    }

    /** Returns the game runtime. */
    public GameRuntime runtime() {
        return runtime;
    }

    /** Returns the underlying headless test runner. */
    public HeadlessTestRunner runner() {
        return runner;
    }

    /** Returns the number of frames stepped so far. */
    public int frameCount() {
        return runner.getFrameCounter();
    }

    // ---- Builder ----

    public static final class Builder {

        private SharedLevel sharedLevel;
        private int zone = -1;
        private int act = -1;
        private short startX;
        private short startY;

        private Builder() {}

        public Builder withSharedLevel(SharedLevel level) {
            this.sharedLevel = level;
            return this;
        }

        public Builder withZoneAndAct(int zone, int act) {
            this.zone = zone;
            this.act = act;
            return this;
        }

        public Builder startPosition(short x, short y) {
            this.startX = x;
            this.startY = y;
            return this;
        }

        public HeadlessTestFixture build() {
            if (sharedLevel == null && zone < 0) {
                throw new IllegalStateException(
                        "HeadlessTestFixture.Builder requires either withSharedLevel() or withZoneAndAct() before build()");
            }

            // 1. Reset transient per-test state
            TestEnvironment.resetPerTest();

            // 2. If withZoneAndAct was used, load the level now (full production path)
            if (sharedLevel == null) {
                try {
                    GameServices.level().loadZoneAndAct(zone, act);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Failed to load zone " + zone + " act " + act, e);
                }
            }

            // 3. Determine character code
            String charCode;
            if (sharedLevel != null) {
                charCode = sharedLevel.mainCharCode();
            } else {
                charCode = SonicConfigurationService.getInstance()
                        .getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            }

            // 4. Create sprite at start position
            Sonic sprite = new Sonic(charCode, startX, startY);

            // 5. Register with SpriteManager
            GameServices.sprites().addSprite(sprite);

            // 6. Wire GroundSensor
            GroundSensor.setLevelManager(GameServices.level());

            // 7. Initialize camera via production path
            GameServices.level().initCameraForLevel();

            // 8. Initialize level events via production path
            GameServices.level().initLevelEventsForLevel();

            // 9. Get runtime and create runner
            GameRuntime runtime = RuntimeManager.getCurrent();
            HeadlessTestRunner runner = new HeadlessTestRunner(sprite);

            return new HeadlessTestFixture(runtime, runner, sprite);
        }
    }
}
