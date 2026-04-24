package com.openggf.physics;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestCollisionSystemAirLanding {

    private GameModule previousModule;

    @BeforeEach
    void setUp() {
        previousModule = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        RuntimeManager.destroyCurrent();
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        if (previousModule != null) {
            GameModuleRegistry.setCurrent(previousModule);
        } else {
            GameModuleRegistry.reset();
        }
    }

    @Test
    void thresholdedAirLandingIgnoresExactSurfaceContact() throws Exception {
        AbstractPlayableSprite sprite = newTestSprite();
        sprite.setAir(true);
        sprite.setXSpeed((short) 0x0397);
        sprite.setYSpeed((short) 0x04D0);
        sprite.setCentreX((short) 0x0A83);
        sprite.setCentreY((short) 0x0272);

        CollisionSystem collisionSystem = new CollisionSystem(new TerrainCollisionManager());
        AtomicBoolean landed = new AtomicBoolean(false);

        Method method = CollisionSystem.class.getDeclaredMethod(
                "doTerrainCollisionAir",
                AbstractPlayableSprite.class,
                SensorResult[].class,
                Consumer.class);
        method.setAccessible(true);
        method.invoke(collisionSystem, sprite, exactSurfaceContactResults(), landingHandler(landed));

        assertFalse(landed.get(), "Exact floor contact should not count as an air landing");
        assertTrue(sprite.getAir(), "Exact surface contact should leave the sprite airborne");
    }

    @Test
    void thresholdedAirLandingAcceptsNegativeSurfaceContact() throws Exception {
        AbstractPlayableSprite sprite = newTestSprite();
        sprite.setAir(true);
        sprite.setXSpeed((short) 0x0397);
        sprite.setYSpeed((short) 0x04D0);
        sprite.setCentreX((short) 0x0A83);
        sprite.setCentreY((short) 0x0272);

        CollisionSystem collisionSystem = new CollisionSystem(new TerrainCollisionManager());
        AtomicBoolean landed = new AtomicBoolean(false);

        Method method = CollisionSystem.class.getDeclaredMethod(
                "doTerrainCollisionAir",
                AbstractPlayableSprite.class,
                SensorResult[].class,
                Consumer.class);
        method.setAccessible(true);
        method.invoke(collisionSystem, sprite, negativeSurfaceContactResults(), landingHandler(landed));

        assertTrue(landed.get(), "Negative floor distance should count as an air landing");
        assertFalse(sprite.getAir(), "Landing handler should have cleared airborne state");
        assertEquals((byte) 0x08, sprite.getAngle(), "Landing should preserve the slope angle");
    }

    @Test
    void directAirLandingIgnoresExactSurfaceContact() throws Exception {
        AbstractPlayableSprite sprite = newTestSprite();
        sprite.setAir(true);
        sprite.setXSpeed((short) 0x0200);
        sprite.setYSpeed((short) 0x0300);
        sprite.setCentreX((short) 0x0200);
        sprite.setCentreY((short) 0x0100);

        CollisionSystem collisionSystem = new CollisionSystem(new TerrainCollisionManager());
        AtomicBoolean landed = new AtomicBoolean(false);

        Method method = CollisionSystem.class.getDeclaredMethod(
                "doTerrainCollisionAirDirect",
                AbstractPlayableSprite.class,
                SensorResult[].class,
                Consumer.class,
                boolean.class);
        method.setAccessible(true);
        method.invoke(collisionSystem, sprite, exactSurfaceContactResults(), landingHandler(landed), false);

        assertFalse(landed.get(), "Direct air floor checks should not land on exact surface contact");
        assertTrue(sprite.getAir(), "Exact surface contact should leave the sprite airborne");
    }

    @Test
    void staleOnObjectFlagDoesNotSuppressTerrainWalkOffWhenUnsupported() {
        AbstractPlayableSprite sprite = newTestSprite();
        sprite.setAir(false);
        sprite.setOnObject(true);
        sprite.setPushing(true);

        CollisionSystem collisionSystem = new CollisionSystem(new StubTerrainCollisionManager(null, null));
        collisionSystem.resolveGroundAttachment(sprite, 14, () -> false);

        assertTrue(sprite.getAir(), "Unsupported stale on-object state must still allow terrain walk-off");
        assertFalse(sprite.getPushing(), "Walk-off should clear pushing just like the normal terrain path");
    }

    @Test
    void supportedOnObjectStillSkipsTerrainAttachment() {
        AbstractPlayableSprite sprite = newTestSprite();
        sprite.setAir(false);
        sprite.setOnObject(true);

        StubTerrainCollisionManager terrain = new StubTerrainCollisionManager(null, null);
        CollisionSystem collisionSystem = new CollisionSystem(terrain);
        collisionSystem.resolveGroundAttachment(sprite, 14, () -> true);

        assertFalse(sprite.getAir(), "Supported object riders should not be detached by terrain probes");
        assertEquals(0, terrain.probeCount, "Supported object riders should skip terrain attachment probes");
    }

    private static AbstractPlayableSprite newTestSprite() {
        return new AbstractPlayableSprite("sonic", (short) 0, (short) 0) {
            @Override
            protected void defineSpeeds() {
            }

            @Override
            protected void createSensorLines() {
            }

            @Override
            public void draw() {
            }
        };
    }

    private static SensorResult[] exactSurfaceContactResults() {
        return new SensorResult[] {
                new SensorResult((byte) 0x08, (byte) 0x00, 14, Direction.DOWN),
                new SensorResult((byte) 0xFF, (byte) 0x02, 164, Direction.DOWN)
        };
    }

    private static SensorResult[] negativeSurfaceContactResults() {
        return new SensorResult[] {
                new SensorResult((byte) 0x08, (byte) 0xFF, 14, Direction.DOWN),
                new SensorResult((byte) 0xFF, (byte) 0x02, 164, Direction.DOWN)
        };
    }

    private static Consumer<AbstractPlayableSprite> landingHandler(AtomicBoolean landed) {
        return sprite -> {
            landed.set(true);
            sprite.setAir(false);
        };
    }

    private static final class StubTerrainCollisionManager extends TerrainCollisionManager {
        private final SensorResult[] results;
        private int probeCount;

        private StubTerrainCollisionManager(SensorResult left, SensorResult right) {
            results = new SensorResult[] {left, right};
        }

        @Override
        public SensorResult[] getSensorResult(Sensor[] sensors) {
            probeCount++;
            return results;
        }
    }
}
