package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.EngineContext;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestMGZSwingingPlatformObjectInstance {

    private Camera camera;

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
        camera = GameServices.camera();
        camera.resetState();
        camera.setX((short) 0x0100);
        camera.setY((short) 0);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void registryCreatesMgzSwingingPlatformInstance() {
        ObjectInstance instance = new Sonic3kObjectRegistry().create(
                new ObjectSpawn(0x1200, 0x0600, Sonic3kObjectIds.MGZ_SWINGING_PLATFORM, 0x00, 0x00, false, 0));

        assertInstanceOf(MGZSwingingPlatformObjectInstance.class, instance);
    }

    @Test
    void outOfRangeCheckUsesPivotXInsteadOfSwingingEndpoint() {
        ObjectSpawn spawn = new ObjectSpawn(
                0x037F, 0x0600, Sonic3kObjectIds.MGZ_SWINGING_PLATFORM, 0x00, 0x00, false, 0);
        MGZSwingingPlatformObjectInstance instance = new MGZSwingingPlatformObjectInstance(spawn);

        assertEquals(0x03CF, instance.getX(), "Sanity check: angle 0 places endpoint 80 px right of pivot");
        assertEquals(0x037F, instance.getOutOfRangeReferenceX(),
                "ROM Obj_MGZSwingingPlatform passes saved pivot $30(a0) to Sprite_OnScreen_Test2");
    }

    @Test
    void objectManagerKeepsPlatformAliveWhenEndpointLeavesRangeButPivotDoesNot() {
        ObjectSpawn spawn = new ObjectSpawn(
                0x037F, 0x0600, Sonic3kObjectIds.MGZ_SWINGING_PLATFORM, 0x00, 0x00, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, camera, services());
        objectManager.reset(0x0100);

        assertNotNull(registry.instance, "Sanity check: right-edge platform should load into the initial window");
        objectManager.update(0x0100, null, List.of(), 1, false);

        assertTrue(objectManager.getActiveObjects().contains(registry.instance),
                "ROM Sprite_OnScreen_Test2 uses the pivot/origin X, so a swinging endpoint crossing the "
                        + "offscreen bucket must not unload the whole platform");
    }

    private ObjectServices services() {
        return new StubObjectServices() {
            @Override
            public Camera camera() {
                return camera;
            }
        };
    }

    private static final class TrackingRegistry implements ObjectRegistry {
        private MGZSwingingPlatformObjectInstance instance;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            instance = new MGZSwingingPlatformObjectInstance(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "MGZSwingingPlatform";
        }
    }
}
