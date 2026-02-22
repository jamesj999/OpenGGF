package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import org.junit.After;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAiz1SpawnStability {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private AbstractPlayableSprite sprite;
    private HeadlessTestRunner runner;
    private LevelManager levelManager;
    private Object oldSkipIntros;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        String mainCharacter = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        GraphicsManager.getInstance().initHeadless();

        // LevelManager expects the playable sprite to exist before loadZoneAndAct.
        sprite = new Sonic(mainCharacter, (short) 100, (short) 624);
        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0); // AIZ1
        GroundSensor.setLevelManager(levelManager);
        assertNotNull("Main sprite should exist after level load",
                SpriteManager.getInstance().getSprite(mainCharacter));

        runner = new HeadlessTestRunner(sprite);
    }

    @After
    public void tearDown() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
    }

    @Test
    public void aiz1IntroSkipSpawnHasCollisionAndNoImmediatePitDeath() {
        int centreX = sprite.getCentreX();
        int centreY = sprite.getCentreY();
        assertTrue("Expected collidable primary terrain below AIZ1 intro-skip spawn",
                hasPrimaryCollisionBelow(centreX, centreY, 512));

        runner.stepIdleFrames(30);
        assertFalse("AIZ1 intro-skip spawn should not immediately enter death state", sprite.getDead());
    }

    private boolean hasPrimaryCollisionBelow(int worldX, int worldY, int rangePixels) {
        Level level = levelManager.getCurrentLevel();
        if (level == null) {
            return false;
        }
        int endY = worldY + Math.max(0, rangePixels);
        for (int y = worldY; y <= endY; y += 16) {
            ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, worldX, y);
            if (chunkDesc == null || !chunkDesc.hasPrimarySolidity()) {
                continue;
            }
            int chunkIndex = chunkDesc.getChunkIndex();
            if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
                continue;
            }
            Chunk chunk = level.getChunk(chunkIndex);
            if (chunk.getSolidTileIndex() != 0) {
                return true;
            }
        }
        return false;
    }
}
