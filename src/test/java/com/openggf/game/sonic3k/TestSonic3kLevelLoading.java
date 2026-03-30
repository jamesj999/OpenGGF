package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.scroll.Sonic3kZoneConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomCondition;
import com.openggf.tests.rules.SonicGame;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that all Sonic 3&K zones can be loaded without errors.
 * Verifies resource plans, level boundaries, and start positions.
 */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(RequiresRomCondition.class)
class TestSonic3kLevelLoading {

    private LevelManager levelManager;
    private String mainCharacter;
    private Object oldSkipIntros;

    @BeforeEach
    void setUp() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        mainCharacter = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        GraphicsManager.getInstance().initHeadless();
        levelManager = GameServices.level();
    }

    @AfterEach
    void tearDown() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
    }

    @Test
    void playerSpriteArtLoadsSuccessfully() throws Exception {
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        GameServices.sprites().addSprite(sprite);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(Sonic3kZoneConstants.ZONE_AIZ, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        assertNotNull(sprite.getSpriteRenderer(),
                "Sprite renderer should be set after loading S3K level");
    }

    @Test
    void aizIntroLayoutDimensionsMatchHeader() throws Exception {
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        GameServices.sprites().addSprite(sprite);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(Sonic3kZoneConstants.ZONE_AIZ, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        Level level = levelManager.getCurrentLevel();
        assertNotNull(level, "AIZ1 level should not be null");
        assertInstanceOf(Sonic3kLevel.class, level, "AIZ1 should load as Sonic3kLevel");

        // skdisasm AIZ intro layout header: FG 97x13, BG 66x11
        assertEquals(97, level.getLayerWidthBlocks(0), "AIZ1 FG layout width");
        assertEquals(13, level.getLayerHeightBlocks(0), "AIZ1 FG layout height");
        assertEquals(66, level.getLayerWidthBlocks(1), "AIZ1 BG layout width");
        assertEquals(11, level.getLayerHeightBlocks(1), "AIZ1 BG layout height");
    }

    @Test
    void aizIntroBlockZeroIsNotSanitizedEmpty() throws Exception {
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        GameServices.sprites().addSprite(sprite);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(Sonic3kZoneConstants.ZONE_AIZ, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        Level level = levelManager.getCurrentLevel();
        assertNotNull(level, "AIZ1 level should not be null");

        Block block0 = level.getBlock(0);
        assertNotNull(block0, "Block 0 should exist");
        assertNotEquals(0, block0.getChunkDesc(0, 0).get(),
                "S3K AIZ intro uses non-empty block 0 in layout data");
    }

    @Test
    void aizIntroChunkZeroIsValidAndReferenced() throws Exception {
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        GameServices.sprites().addSprite(sprite);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(Sonic3kZoneConstants.ZONE_AIZ, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        Level level = levelManager.getCurrentLevel();
        assertNotNull(level, "AIZ1 level should not be null");

        Chunk chunk0 = level.getChunk(0);
        assertNotNull(chunk0, "Chunk 0 should exist");

        boolean chunk0HasPatternData = false;
        for (int py = 0; py < 2 && !chunk0HasPatternData; py++) {
            for (int px = 0; px < 2; px++) {
                if (chunk0.getPatternDesc(px, py).get() != 0) {
                    chunk0HasPatternData = true;
                    break;
                }
            }
        }
        assertTrue(chunk0HasPatternData, "S3K AIZ intro uses non-empty chunk 0 data");

        boolean chunk0ReferencedByBlocks = false;
        for (int blockIndex = 0; blockIndex < level.getBlockCount() && !chunk0ReferencedByBlocks; blockIndex++) {
            Block block = level.getBlock(blockIndex);
            if (block == null) {
                continue;
            }
            for (int cy = 0; cy < 8 && !chunk0ReferencedByBlocks; cy++) {
                for (int cx = 0; cx < 8; cx++) {
                    if (block.getChunkDesc(cx, cy).getChunkIndex() == 0) {
                        chunk0ReferencedByBlocks = true;
                        break;
                    }
                }
            }
        }
        assertTrue(chunk0ReferencedByBlocks, "S3K AIZ intro block data references chunk index 0");
    }

    static Stream<Arguments> zoneActProvider() {
        return Stream.of(
                Arguments.of(Sonic3kZoneConstants.ZONE_AIZ, 0, "Angel Island Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_AIZ, 1, "Angel Island Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_HCZ, 0, "Hydrocity Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_HCZ, 1, "Hydrocity Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_MGZ, 0, "Marble Garden Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_MGZ, 1, "Marble Garden Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_CNZ, 0, "Carnival Night Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_CNZ, 1, "Carnival Night Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_FBZ, 0, "Flying Battery Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_FBZ, 1, "Flying Battery Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_ICZ, 0, "IceCap Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_ICZ, 1, "IceCap Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_LBZ, 0, "Launch Base Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_LBZ, 1, "Launch Base Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_MHZ, 0, "Mushroom Hill Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_MHZ, 1, "Mushroom Hill Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_SOZ, 0, "Sandopolis Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_SOZ, 1, "Sandopolis Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_LRZ, 0, "Lava Reef Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_LRZ, 1, "Lava Reef Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_SSZ, 0, "Sky Sanctuary Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_SSZ, 1, "Sky Sanctuary Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_DEZ, 0, "Death Egg Act 1"),
                Arguments.of(Sonic3kZoneConstants.ZONE_DEZ, 1, "Death Egg Act 2"),
                Arguments.of(Sonic3kZoneConstants.ZONE_DDZ, 0, "Doomsday")
        );
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("zoneActProvider")
    void zoneLoads(int zone, int act, String label) throws Exception {
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        GameServices.sprites().addSprite(sprite);
        Camera camera = GameServices.camera();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(zone, act);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        Level level = levelManager.getCurrentLevel();
        assertNotNull(level, label + ": level should not be null");

        int minX = level.getMinX();
        int maxX = level.getMaxX();
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        assertTrue(maxX > minX,
                label + ": maxX (" + maxX + ") should be > minX (" + minX + ")");
        assertTrue(maxY > minY,
                label + ": maxY (" + maxY + ") should be > minY (" + minY + ")");

        Sonic3kZoneRegistry registry = Sonic3kZoneRegistry.getInstance();
        int[] startPos = registry.getStartPosition(zone, act);
        // DDZ (Doomsday) has ROM-accurate start X=0
        if (zone != Sonic3kZoneConstants.ZONE_DDZ) {
            assertTrue(startPos[0] != 0, label + ": start X should be non-zero");
        }
        assertTrue(startPos[1] != 0, label + ": start Y should be non-zero");
    }
}
