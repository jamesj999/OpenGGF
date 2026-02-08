package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Chunk;
import uk.co.jamesj999.sonic.level.ChunkDesc;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

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

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_AIZ1_INTRO, true);
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
