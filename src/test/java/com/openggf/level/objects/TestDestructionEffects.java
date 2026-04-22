package com.openggf.level.objects;

import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic1.objects.Sonic1AnimalsObjectInstance;
import com.openggf.game.sonic1.objects.badniks.Sonic1DestructionConfig;
import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDestructionEffects {

    private ObjectManager objectManager;
    private ObjectServices services;

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();

        services = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }

            @Override
            public com.openggf.camera.Camera camera() {
                return GameServices.camera();
            }
        };

        objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null,
                null, GameServices.camera(), services);
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void sonic1BadnikDestructionDefersAnimalUntilExplosionExecutes() {
        DestructionConfig config = Sonic1DestructionConfig.S1_DESTRUCTION_CONFIG;
        config = new DestructionConfig(
                config.sfxId(),
                config.animalFactory(),
                config.useRespawnTracking(),
                null,
                config.explosionFactory());

        DestructionEffects.destroyBadnik(
                0x0100,
                0x0120,
                new ObjectSpawn(0x0100, 0x0120, 0x78, 0, 0, false, 0),
                40,
                null,
                services,
                config);

        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(ExplosionObjectInstance.class::isInstance),
                "S1 badnik destruction should immediately replace the badnik with an explosion");
        assertFalse(objectManager.getActiveObjects().stream()
                        .anyMatch(Sonic1AnimalsObjectInstance.class::isInstance),
                "S1 should not spawn the animal until ExplosionItem routine 0 runs");

        objectManager.update(0, null, List.of(), 1);

        assertTrue(objectManager.getActiveObjects().stream()
                        .anyMatch(Sonic1AnimalsObjectInstance.class::isInstance),
                "S1 explosion processing should spawn the ROM-ported Sonic1 animals object");
        assertEquals(40, objectManager.getActiveObjects().stream()
                        .filter(ExplosionObjectInstance.class::isInstance)
                        .map(ExplosionObjectInstance.class::cast)
                        .mapToInt(ExplosionObjectInstance::getSlotIndex)
                        .findFirst()
                        .orElse(-1),
                "S1 explosion should inherit the destroyed badnik slot");
    }

    private static final class NoOpObjectRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "noop";
        }
    }
}
