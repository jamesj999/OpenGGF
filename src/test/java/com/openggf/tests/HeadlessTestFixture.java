package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

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

    /** Step one frame using input from the loaded BK2 recording. Returns the input mask used. */
    public int stepFrameFromRecording() {
        return runner.stepFrameFromRecording();
    }

    /** Advance BK2 by one frame without processing physics (for lag frames). Returns the input mask. */
    public int skipFrameFromRecording() {
        return runner.skipFrameFromRecording();
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
        private Bk2Movie bk2Movie;
        private int bk2FrameOffset;
        private boolean startPositionIsCentre;
        private boolean customStartPositionProvided;

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
            this.customStartPositionProvided = true;
            return this;
        }

        /**
         * Treat start position as ROM centre coordinates (like $D008/$D00C).
         * Uses setCentreX/Y after construction, matching
         * LevelManager.spawnPlayerAtStartPosition() behaviour.
         */
        public Builder startPositionIsCentre() {
            this.startPositionIsCentre = true;
            return this;
        }

        public Builder withRecording(Path bk2Path) throws IOException {
            this.bk2Movie = new Bk2MovieLoader().load(bk2Path);
            return this;
        }

        public Builder withRecordingStartFrame(int bk2FrameOffset) {
            this.bk2FrameOffset = bk2FrameOffset;
            return this;
        }

        public HeadlessTestFixture build() {
            if (sharedLevel == null && zone < 0) {
                throw new IllegalStateException(
                        "HeadlessTestFixture.Builder requires either withSharedLevel() or withZoneAndAct() before build()");
            }

            // 1. Reset transient per-test state
            TestEnvironment.resetPerTest();

            // 2. Shared-level tests rely on the config snapshot that was active
            // when the level was originally loaded. @RequiresRom rebuilds the
            // runtime before each test method, which restores default config.
            if (sharedLevel != null) {
                SonicConfigurationService config = SonicConfigurationService.getInstance();
                config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, sharedLevel.skipIntros());
                config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, sharedLevel.mainCharCode());
                config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sharedLevel.sidekickCharCode());
            }

            // 3. Determine character code before any reload path that needs a
            // registered player sprite.
            String charCode;
            if (sharedLevel != null) {
                charCode = sharedLevel.mainCharCode();
            } else {
                charCode = SonicConfigurationService.getInstance()
                        .getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            }

            // 4. Shared-level tests rebuild the runtime before each method via
            // @RequiresRom. When the level has to be reloaded into that fresh
            // runtime, register the sprite first so loadZoneAndAct() executes
            // the normal spawn-time initialization path.
            boolean needsSharedLevelReload = sharedLevel != null
                    && GameServices.level().getCurrentLevel() == null;

            Sonic sprite = null;
            if (needsSharedLevelReload) {
                sprite = new Sonic(charCode, startX, startY);
                GameServices.sprites().addSprite(sprite);
            }

            // 5. Load or rewire the requested level.
            if (sharedLevel == null) {
                try {
                    GameServices.level().loadZoneAndAct(zone, act);
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Failed to load zone " + zone + " act " + act, e);
                }
            } else if (GameServices.level().getCurrentLevel() == null) {
                try {
                    GameServices.level().loadZoneAndAct(sharedLevel.zone(), sharedLevel.act());
                } catch (IOException e) {
                    throw new UncheckedIOException(
                            "Failed to reload shared level zone " + sharedLevel.zone()
                                    + " act " + sharedLevel.act(), e);
                }
            } else {
                // Re-wire CollisionSystem after per-test reset when using SharedLevel.
                // resetPerTest() clears CollisionSystem.objectManager, but the SharedLevel
                // path skips level reload (which normally restores the wiring).
                ObjectManager om = GameServices.level().getObjectManager();
                if (om != null) {
                    GameServices.collision().setObjectManager(om);
                }
            }

            // 6. Create/register the sprite if the reload path did not already do it.
            if (sprite == null) {
                sprite = new Sonic(charCode, startX, startY);
                GameServices.sprites().addSprite(sprite);
            }

            // 7. Preserve existing builder semantics for explicit custom starts by
            // reapplying the requested coordinates after any level load.
            if (customStartPositionProvided) {
                if (startPositionIsCentre) {
                    sprite.setCentreX(startX);
                    sprite.setCentreY(startY);
                } else {
                    sprite.setX(startX);
                    sprite.setY(startY);
                }
            }

            // 8. Wire GroundSensor
            GroundSensor.setLevelManager(GameServices.level());

            // 9. Initialize camera via production path
            GameServices.level().initCameraForLevel();

            // 10. Initialize level events via production path
            GameServices.level().initLevelEventsForLevel();

            // 11. Initial ground snap. ROM runs terrain probes during title card
            // frames (~120 frames) which snap the player to ground and set the
            // correct terrain angle. Tests skip the title card, so do one probe
            // to establish ground attachment. Uses threshold=14 (S1 always uses
            // 14; S2/S3K at speed=0 would use min(0+4,14)=4, but 14 is safe for
            // a static snap at spawn).
            GameServices.collision().resolveGroundAttachment(
                    sprite, 14, () -> false);

            // 12. Get runtime and create runner
            GameRuntime runtime = RuntimeManager.getCurrent();
            HeadlessTestRunner runner = new HeadlessTestRunner(sprite);

            // 13. Wire BK2 recording if provided
            if (bk2Movie != null) {
                runner.setBk2Movie(bk2Movie, bk2FrameOffset);
            }

            return new HeadlessTestFixture(runtime, runner, sprite);
        }
    }
}
