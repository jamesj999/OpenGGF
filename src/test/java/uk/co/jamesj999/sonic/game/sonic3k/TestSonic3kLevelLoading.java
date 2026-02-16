package uk.co.jamesj999.sonic.game.sonic3k;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic3k.scroll.Sonic3kZoneConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import org.junit.After;

import static org.junit.Assert.*;

/**
 * Tests that all Sonic 3&K zones can be loaded without errors.
 * Verifies resource plans, level boundaries, and start positions.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kLevelLoading {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private LevelManager levelManager;
    private String mainCharacter;
    private Object oldSkipIntros;

    @Before
    public void setUp() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        mainCharacter = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        GraphicsManager.getInstance().initHeadless();
        levelManager = LevelManager.getInstance();
    }

    @After
    public void tearDown() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
    }

    @Test
    public void playerSpriteArtLoadsSuccessfully() throws Exception {
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        SpriteManager.getInstance().addSprite(sprite);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(Sonic3kZoneConstants.ZONE_AIZ, 0);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        assertNotNull("Sprite renderer should be set after loading S3K level",
                sprite.getSpriteRenderer());
    }

    @Test
    public void aizLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_AIZ, 0, "Angel Island Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_AIZ, 1, "Angel Island Act 2");
    }

    @Test
    public void hczLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_HCZ, 0, "Hydrocity Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_HCZ, 1, "Hydrocity Act 2");
    }

    @Test
    public void mgzLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_MGZ, 0, "Marble Garden Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_MGZ, 1, "Marble Garden Act 2");
    }

    @Test
    public void cnzLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_CNZ, 0, "Carnival Night Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_CNZ, 1, "Carnival Night Act 2");
    }

    @Test
    public void fbzLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_FBZ, 0, "Flying Battery Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_FBZ, 1, "Flying Battery Act 2");
    }

    @Test
    public void iczLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_ICZ, 0, "IceCap Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_ICZ, 1, "IceCap Act 2");
    }

    @Test
    public void lbzLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_LBZ, 0, "Launch Base Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_LBZ, 1, "Launch Base Act 2");
    }

    @Test
    public void mhzLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_MHZ, 0, "Mushroom Hill Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_MHZ, 1, "Mushroom Hill Act 2");
    }

    @Test
    public void sozLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_SOZ, 0, "Sandopolis Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_SOZ, 1, "Sandopolis Act 2");
    }

    @Test
    public void lrzLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_LRZ, 0, "Lava Reef Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_LRZ, 1, "Lava Reef Act 2");
    }

    @Test
    public void sszLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_SSZ, 0, "Sky Sanctuary Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_SSZ, 1, "Sky Sanctuary Act 2");
    }

    @Test
    public void dezLoads() throws Exception {
        assertZoneLoads(Sonic3kZoneConstants.ZONE_DEZ, 0, "Death Egg Act 1");
        assertZoneLoads(Sonic3kZoneConstants.ZONE_DEZ, 1, "Death Egg Act 2");
    }

    @Test
    public void ddzLoads() throws Exception {
        // Doomsday is a single-act zone
        assertZoneLoads(Sonic3kZoneConstants.ZONE_DDZ, 0, "Doomsday");
    }

    /**
     * Loads a zone/act and verifies:
     * - Level object is non-null
     * - Boundaries are valid (maxX > minX, maxY > minY)
     * - Start position has non-zero components
     */
    private void assertZoneLoads(int zone, int act, String label) throws Exception {
        // Create a fresh sprite for each load
        Sonic sprite = new Sonic(mainCharacter, (short) 100, (short) 400);
        SpriteManager.getInstance().addSprite(sprite);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager.loadZoneAndAct(zone, act);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        Level level = levelManager.getCurrentLevel();
        assertNotNull(label + ": level should not be null", level);

        int minX = level.getMinX();
        int maxX = level.getMaxX();
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        assertTrue(label + ": maxX (" + maxX + ") should be > minX (" + minX + ")",
                maxX > minX);
        assertTrue(label + ": maxY (" + maxY + ") should be > minY (" + minY + ")",
                maxY > minY);

        Sonic3kZoneRegistry registry = Sonic3kZoneRegistry.getInstance();
        int[] startPos = registry.getStartPosition(zone, act);
        assertTrue(label + ": start X should be non-zero", startPos[0] != 0);
        assertTrue(label + ": start Y should be non-zero", startPos[1] != 0);
    }
}
