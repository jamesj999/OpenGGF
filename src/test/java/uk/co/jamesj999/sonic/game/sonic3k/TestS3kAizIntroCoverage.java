package uk.co.jamesj999.sonic.game.sonic3k;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Block;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Map;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.HeadlessTestRunner;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizIntroCoverage {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Object oldSkipIntros;
    private Object oldMainCharacter;

    @Before
    public void setUp() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GraphicsManager.getInstance().initHeadless();
    }

    @After
    public void tearDown() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
    }

    @Test
    public void aizIntroTransitionLoadsMainLevelChunkCoverage() throws Exception {
        Sonic sonic = new Sonic("sonic", (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(sonic);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);

        LevelManager levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(0, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        Level level = levelManager.getCurrentLevel();
        assertNotNull("AIZ1 level should be loaded", level);
        Map map = level.getMap();
        assertNotNull("AIZ1 map should be loaded", map);
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);

        int fgWidth = Math.max(1, level.getLayerWidthBlocks(0));
        int fgHeight = Math.max(1, level.getLayerHeightBlocks(0));
        int bgWidth = Math.max(1, level.getLayerWidthBlocks(1));
        int bgHeight = Math.max(1, level.getLayerHeightBlocks(1));

        int initialChunkCount = level.getChunkCount();
        int initialInvalidRefs = 0;
        int maxUsedChunkIndex = 0;
        TreeSet<Integer> invalidColumns = new TreeSet<>();

        initialInvalidRefs += scanLayer(level, map, 0, fgWidth, fgHeight, initialChunkCount, invalidColumns);
        initialInvalidRefs += scanLayer(level, map, 1, bgWidth, bgHeight, initialChunkCount, invalidColumns);

        for (int y = 0; y < fgHeight; y++) {
            for (int x = 0; x < fgWidth; x++) {
                int blockIndex = Byte.toUnsignedInt(map.getValue(0, x, y));
                Block block = level.getBlock(blockIndex);
                for (int cy = 0; cy < 8; cy++) {
                    for (int cx = 0; cx < 8; cx++) {
                        int idx = block.getChunkDesc(cx, cy).getChunkIndex();
                        if (idx > maxUsedChunkIndex) {
                            maxUsedChunkIndex = idx;
                        }
                    }
                }
            }
        }
        for (int y = 0; y < bgHeight; y++) {
            for (int x = 0; x < bgWidth; x++) {
                int blockIndex = Byte.toUnsignedInt(map.getValue(1, x, y));
                Block block = level.getBlock(blockIndex);
                for (int cy = 0; cy < 8; cy++) {
                    for (int cx = 0; cx < 8; cx++) {
                        int idx = block.getChunkDesc(cx, cy).getChunkIndex();
                        if (idx > maxUsedChunkIndex) {
                            maxUsedChunkIndex = idx;
                        }
                    }
                }
            }
        }

        assertTrue("Expected intro startup profile to have unresolved later-map chunk references",
                initialInvalidRefs > 0);

        // Run until the intro reaches the ROM transition point (camera X >= $1400),
        // then allow a short settle period for overlay application.
        int guard = 5000;
        while (guard-- > 0 && (camera.getX() & 0xFFFF) < 0x1400) {
            runner.stepFrame(false, false, false, false, false);
        }
        for (int i = 0; i < 32; i++) {
            runner.stepFrame(false, false, false, false, false);
        }

        Level postTransitionLevel = levelManager.getCurrentLevel();
        assertNotNull("AIZ1 level should remain loaded after intro transition", postTransitionLevel);
        int postChunkCount = postTransitionLevel.getChunkCount();
        int postInvalidRefs = 0;
        invalidColumns.clear();
        postInvalidRefs += scanLayer(postTransitionLevel, map, 0, fgWidth, fgHeight, postChunkCount, invalidColumns);
        postInvalidRefs += scanLayer(postTransitionLevel, map, 1, bgWidth, bgHeight, postChunkCount, invalidColumns);

        System.out.println("AIZ intro coverage: initialChunkCount=" + initialChunkCount
                + " postChunkCount=" + postChunkCount
                + " maxUsedChunkIndex=" + maxUsedChunkIndex
                + " initialInvalidRefs=" + initialInvalidRefs
                + " postInvalidRefs=" + postInvalidRefs
                + " invalidColumns=" + invalidColumns);

        assertEquals("Post-transition AIZ map should not reference missing 16x16 chunks", 0, postInvalidRefs);
    }

    private static int scanLayer(Level level,
                                 Map map,
                                 int layer,
                                 int width,
                                 int height,
                                 int chunkCount,
                                 TreeSet<Integer> invalidColumns) {
        int invalid = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int blockIndex = Byte.toUnsignedInt(map.getValue(layer, x, y));
                Block block = level.getBlock(blockIndex);
                for (int cy = 0; cy < 8; cy++) {
                    for (int cx = 0; cx < 8; cx++) {
                        int idx = block.getChunkDesc(cx, cy).getChunkIndex();
                        if (idx >= chunkCount) {
                            invalid++;
                            invalidColumns.add(x);
                        }
                    }
                }
            }
        }
        return invalid;
    }
}
