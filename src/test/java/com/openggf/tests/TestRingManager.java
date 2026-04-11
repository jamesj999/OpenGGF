package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.rings.RingFrame;
import com.openggf.level.rings.RingFramePiece;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRingManager {
    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void testRingCollectionAndSparkleLifecycle() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);
        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.update(0, player, 0);

        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
        assertEquals(0, ringManager.getSparkleStartFrame(spawn));
        assertTrue(ringManager.isRenderable(spawn, 1));

        assertFalse(ringManager.isRenderable(spawn, 2));
        assertEquals(-1, ringManager.getSparkleStartFrame(spawn));
    }

    @Test
    public void testCollectedRingsPersistOffscreen() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);
        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.update(0, player, 0);

        assertTrue(ringManager.isCollected(spawn));

        ringManager.update(10000, player, 1);
        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());

        ringManager.update(0, player, 2);
        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
    }

    private RingManager buildRingManager(List<RingSpawn> spawns) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);

        RingFramePiece piece = new RingFramePiece(0, 0, 1, 1, 0, false, false, 0);
        RingFrame frame = new RingFrame(List.of(piece));
        List<RingFrame> frames = new ArrayList<>();
        frames.add(frame);
        frames.add(frame);
        frames.add(frame);

        RingSpriteSheet spriteSheet = new RingSpriteSheet(new Pattern[] { pattern }, frames, 1, 1, 1, 2);
        RingManager ringManager = new RingManager(spawns, spriteSheet, null, null);
        ringManager.ensurePatternsCached(GraphicsManager.getInstance(), 0);
        return ringManager;
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite(short x, short y) {
            super("TEST", x, y);
            setWidth(20);
            setHeight(20);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 0;
            runHeight = 0;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        private int ringCount = 0;

        @Override
        public void addRings(int delta) {
            ringCount += delta;
        }

        @Override
        public int getRingCount() {
            return ringCount;
        }

        @Override
        public void draw() {

        }
    }
}


