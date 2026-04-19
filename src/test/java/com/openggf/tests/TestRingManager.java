package com.openggf.tests;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.rings.LostRing;
import com.openggf.level.rings.RingFrame;
import com.openggf.level.rings.RingFramePiece;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Field;
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

    @Test
    public void testS1RingCollectionUsesTouchWindowInsteadOfSpriteBounds() {
        RingSpawn spawn = new RingSpawn(0x0098, 0x0248);
        RingManager ringManager = buildRingManagerWithSpinPiece(List.of(spawn),
                new RingFramePiece(-16, -16, 4, 4, 0, false, false, 0));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 0x0087, (short) 0x025B);
        player.setRolling(false);

        ringManager.update(0, player, 0);

        assertFalse(ringManager.isCollected(spawn),
                "Ring should not collect at the MZ1 frame-71 trace position");
        assertEquals(0, player.getRingCount());

        player.setCentreX((short) 0x008B);
        player.setCentreY((short) 0x024F);

        ringManager.update(0, player, 1);

        assertTrue(ringManager.isCollected(spawn),
                "Ring should collect once the ROM touch window overlaps");
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testLostRingCollectionUsesTouchPhaseInvulnerabilityThreshold() throws Exception {
        RingManager ringManager = buildRingManager(List.of());
        TestPlayableSprite player = new TestPlayableSprite((short) 0x03B7, (short) 0x025A);
        player.setRolling(true);

        player.setInvulnerableFrames(90);
        configureSingleLostRing(ringManager, 0x03AE, 0x0261);
        ringManager.checkLostRingCollection(player);
        assertEquals(0, player.getRingCount(),
                "Lost ring recollection should stay blocked while the ROM threshold is still active");

        player.setInvulnerableFrames(89);
        configureSingleLostRing(ringManager, 0x03AE, 0x0261);
        ringManager.checkLostRingCollection(player);
        assertEquals(1, player.getRingCount(),
                "Lost ring recollection should award as soon as the touch-phase timer drops below the ROM threshold");
    }

    private RingManager buildRingManager(List<RingSpawn> spawns) {
        return buildRingManagerWithSpinPiece(spawns, new RingFramePiece(0, 0, 1, 1, 0, false, false, 0));
    }

    private RingManager buildRingManagerWithSpinPiece(List<RingSpawn> spawns, RingFramePiece piece) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);

        RingFrame frame = new RingFrame(List.of(piece));
        List<RingFrame> frames = new ArrayList<>();
        frames.add(frame);
        frames.add(frame);
        frames.add(frame);

        Pattern[] patterns = new Pattern[16];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = pattern;
        }

        RingSpriteSheet spriteSheet = new RingSpriteSheet(patterns, frames, 1, 1, 1, 2);
        RingManager ringManager = new RingManager(spawns, spriteSheet, null, null);
        ringManager.ensurePatternsCached(GraphicsManager.getInstance(), 0);
        return ringManager;
    }

    private void configureSingleLostRing(RingManager ringManager, int x, int y) throws Exception {
        Field lostRingsField = RingManager.class.getDeclaredField("lostRings");
        lostRingsField.setAccessible(true);
        Object lostRings = lostRingsField.get(ringManager);

        Field ringPoolField = lostRings.getClass().getDeclaredField("ringPool");
        ringPoolField.setAccessible(true);
        LostRing[] ringPool = (LostRing[]) ringPoolField.get(lostRings);
        ringPool[0].reset(0, x, y, 0, 0, 0xFF);

        Field activeRingCountField = lostRings.getClass().getDeclaredField("activeRingCount");
        activeRingCountField.setAccessible(true);
        activeRingCountField.setInt(lostRings, 1);
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite(short x, short y) {
            super("TEST", x, y);
            setWidth(16);
            setHeight(32);
            setCentreX(x);
            setCentreY(y);
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


