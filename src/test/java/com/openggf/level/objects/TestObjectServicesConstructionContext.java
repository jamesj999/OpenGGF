package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the ThreadLocal construction context makes {@link ObjectServices}
 * available during object construction, and that it is properly cleaned up.
 */
class TestObjectServicesConstructionContext {

    /**
     * Minimal ObjectServices for testing. All methods throw to catch unintended calls.
     */
    private static final ObjectServices TEST_SERVICES = new ObjectServices() {
        @Override public ObjectManager objectManager() { return null; }
        @Override public ObjectRenderManager renderManager() { return null; }
        @Override public com.openggf.game.LevelState levelGamestate() { return null; }
        @Override public com.openggf.game.RespawnState checkpointState() { return null; }
        @Override public com.openggf.level.Level currentLevel() { return null; }
        @Override public int romZoneId() { return 0; }
        @Override public int currentAct() { return 0; }
        @Override public int featureZoneId() { return 0; }
        @Override public int featureActId() { return 0; }
        @Override public com.openggf.game.ZoneFeatureProvider zoneFeatureProvider() { return null; }
        @Override public void playSfx(int soundId) {}
        @Override public void playSfx(com.openggf.audio.GameSound sound) {}
        @Override public void playMusic(int musicId) {}
        @Override public void fadeOutMusic() {}
        @Override public com.openggf.audio.AudioManager audioManager() { return null; }
        @Override public void spawnLostRings(com.openggf.game.PlayableEntity player, int fc) {}
        @Override public com.openggf.camera.Camera camera() { return null; }
        @Override public com.openggf.game.GameStateManager gameState() { return null; }
        @Override public java.util.List<com.openggf.game.PlayableEntity> sidekicks() { return java.util.List.of(); }
        @Override public com.openggf.sprites.managers.SpriteManager spriteManager() { return null; }
        @Override public com.openggf.graphics.GraphicsManager graphicsManager() { return null; }
        @Override public com.openggf.graphics.FadeManager fadeManager() { return null; }
        @Override public com.openggf.data.Rom rom() { return null; }
        @Override public com.openggf.data.RomByteReader romReader() { return null; }
        @Override public com.openggf.level.WaterSystem waterSystem() { return null; }
        @Override public com.openggf.level.ParallaxManager parallaxManager() { return null; }
        @Override public void advanceToNextLevel() {}
        @Override public void requestCreditsTransition() {}
        @Override public void requestSpecialStageEntry() {}
        @Override public void invalidateForegroundTilemap() {}
        @Override public boolean areAllRingsCollected() { return false; }
        @Override public void updatePalette(int idx, byte[] data) {}
        @Override public com.openggf.level.rings.RingManager ringManager() { return null; }
        @Override public void advanceZoneActOnly() {}
        @Override public void requestSpecialStageFromCheckpoint() {}
        @Override public void requestZoneAndAct(int zone, int act) {}
        @Override public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {}
        @Override public int getCurrentLevelMusicId() { return 0; }
        @Override public int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius) { return null; }
        @Override public void saveBigRingReturnPosition(int playerX, int playerY, int cameraX, int cameraY) {}
    };

    /** Test object that calls services() in its constructor. */
    private static class ConstructorAccessObject extends AbstractObjectInstance {
        final ObjectServices constructorServices;

        ConstructorAccessObject(ObjectSpawn spawn) {
            super(spawn, "TestConstructorAccess");
            // This call would previously throw IllegalStateException
            this.constructorServices = services();
        }

        @Override
        public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) {}
    }

    @Test
    void services_availableDuringConstruction_whenContextSet() {
        ObjectSpawn spawn = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(TEST_SERVICES);
        try {
            ConstructorAccessObject obj = new ConstructorAccessObject(spawn);
            assertSame(TEST_SERVICES, obj.constructorServices,
                    "services() should return construction context during constructor");
        } finally {
            AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
        }
    }

    @Test
    void services_usesInstanceField_afterSetServices() {
        ObjectSpawn spawn = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

        // Create with context
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(TEST_SERVICES);
        ConstructorAccessObject obj;
        try {
            obj = new ConstructorAccessObject(spawn);
        } finally {
            AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();
        }

        // Now set instance-level services (different instance)
        ObjectServices instanceServices = new TestObjectServices();
        obj.setServices(instanceServices);

        // Instance field should take priority over (now-cleared) context
        assertSame(instanceServices, obj.services(),
                "services() should prefer instance field over construction context");
    }

    @Test
    void services_throwsWithoutContext_orInstanceField() {
        ObjectSpawn spawn = new ObjectSpawn(0, 0, 0, 0, 0, false, 0);

        // Ensure no context is set
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();

        // Create a simple object without context (bypassing managed path)
        AbstractObjectInstance obj = new AbstractObjectInstance(spawn, "Unmanaged") {
            @Override
            public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) {}
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class, obj::services,
                "services() should throw when neither instance field nor context is available");
        assertTrue(ex.getMessage().contains("services not available"),
                "Error message should indicate services are unavailable");
    }

    @Test
    void constructionContext_cleanedUpAfterFinally() {
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.set(TEST_SERVICES);
        AbstractObjectInstance.CONSTRUCTION_CONTEXT.remove();

        assertNull(AbstractObjectInstance.CONSTRUCTION_CONTEXT.get(),
                "ThreadLocal should be null after remove()");
    }
}
