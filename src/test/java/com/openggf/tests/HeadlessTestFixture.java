package com.openggf.tests;

import com.openggf.GameContext;
import com.openggf.camera.Camera;
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
 * <p>
 * Typical usage with a {@link SharedLevel}:
 * <pre>
 * HeadlessTestFixture fixture = HeadlessTestFixture.builder()
 *         .withSharedLevel(shared)
 *         .startPosition((short) 96, (short) 655)
 *         .build();
 *
 * fixture.stepFrame(false, false, false, true, false); // walk right
 * assertEquals(expectedX, fixture.sprite().getX());
 * </pre>
 * <p>
 * This replaces approximately 20 lines of setup code per test method with
 * a concise fluent API.
 */
public final class HeadlessTestFixture {

    private final GameContext context;
    private final HeadlessTestRunner runner;
    private final AbstractPlayableSprite sprite;

    private HeadlessTestFixture(GameContext context, HeadlessTestRunner runner,
                                AbstractPlayableSprite sprite) {
        this.context = context;
        this.runner = runner;
        this.sprite = sprite;
    }

    /** Returns a new builder for constructing a fixture. */
    public static Builder builder() {
        return new Builder();
    }

    // ---- Convenience delegates ----

    /**
     * Steps one frame with the given input state.
     * Delegates to {@link HeadlessTestRunner#stepFrame}.
     */
    public void stepFrame(boolean up, boolean down, boolean left,
                          boolean right, boolean jump) {
        runner.stepFrame(up, down, left, right, jump);
    }

    /**
     * Steps multiple idle frames (no input).
     * Delegates to {@link HeadlessTestRunner#stepIdleFrames}.
     */
    public void stepIdleFrames(int count) {
        runner.stepIdleFrames(count);
    }

    /** Returns the playable sprite managed by this fixture. */
    public AbstractPlayableSprite sprite() {
        return sprite;
    }

    /** Returns the camera from the game context. */
    public Camera camera() {
        return context.camera();
    }

    /** Returns the game context. */
    public GameContext context() {
        return context;
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

    /**
     * Builder for {@link HeadlessTestFixture}. Collects configuration and
     * performs all wiring in {@link #build()}.
     */
    public static final class Builder {

        private SharedLevel sharedLevel;
        private int zone = -1;
        private int act = -1;
        private short startX;
        private short startY;

        private Builder() {}

        /**
         * Uses a previously loaded {@link SharedLevel} for level data.
         * This is the recommended path when level data is shared across
         * tests via {@code @BeforeClass}.
         */
        public Builder withSharedLevel(SharedLevel level) {
            this.sharedLevel = level;
            return this;
        }

        /**
         * Specifies zone and act for fresh level loading.
         * This is an alternative to {@link #withSharedLevel} for tests
         * that need a different zone/act.
         */
        public Builder withZoneAndAct(int zone, int act) {
            this.zone = zone;
            this.act = act;
            return this;
        }

        /** Sets the sprite's initial position (top-left coordinates). */
        public Builder startPosition(short x, short y) {
            this.startX = x;
            this.startY = y;
            return this;
        }

        /**
         * Builds the fixture, performing all setup:
         * <ol>
         *   <li>Reset per-test state via {@link TestEnvironment#resetPerTest()}</li>
         *   <li>If using {@link #withZoneAndAct}, load the level via
         *       {@link LevelManager#loadZoneAndAct} (full production path)</li>
         *   <li>Create a Sonic sprite at the start position</li>
         *   <li>Register sprite with SpriteManager</li>
         *   <li>Wire GroundSensor to LevelManager</li>
         *   <li>Initialize camera via production path ({@link LevelManager#initCameraForLevel()})</li>
         *   <li>Initialize level events via production path ({@link LevelManager#initLevelEventsForLevel()})</li>
         *   <li>Create GameContext and HeadlessTestRunner</li>
         * </ol>
         *
         * @return a fully wired fixture ready for frame stepping
         */
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
                    LevelManager.getInstance().loadZoneAndAct(zone, act);
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
            SpriteManager sm = SpriteManager.getInstance();
            sm.addSprite(sprite);

            // 6. Wire GroundSensor
            GroundSensor.setLevelManager(LevelManager.getInstance());

            // 7. Initialize camera via production path (bounds, snap, vertical wrap)
            LevelManager.getInstance().initCameraForLevel();

            // 8. Initialize level events via production path (always-on for parity)
            LevelManager.getInstance().initLevelEventsForLevel();

            // 9. Create context and runner
            GameContext context = GameContext.production();
            HeadlessTestRunner runner = new HeadlessTestRunner(sprite);

            return new HeadlessTestFixture(context, runner, sprite);
        }
    }
}
