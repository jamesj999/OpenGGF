package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.PatternAtlas;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_1)
public class Sonic1SpecialStageManagerTest {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private GraphicsManager graphicsManager;
    private Sonic1SpecialStageManager manager;

    @Before
    public void setUp() {
        GraphicsManager.resetInstance();
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
        manager = new Sonic1SpecialStageManager();
    }

    @After
    public void tearDown() {
        if (manager != null) {
            manager.reset();
        }
        if (graphicsManager != null) {
            graphicsManager.cleanup();
        }
        GraphicsManager.resetInstance();
    }

    @Test
    public void testInitializeLoadsZonePatternBases() throws Exception {
        manager.initialize(0);
        assertTrue("Special stage manager should initialize", manager.isInitialized());

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull("Renderer should be created during initialization", renderer);

        Field zoneBasesField = Sonic1SpecialStageRenderer.class.getDeclaredField("zonePatternBases");
        zoneBasesField.setAccessible(true);
        int[] zoneBases = (int[]) zoneBasesField.get(renderer);
        assertNotNull("Zone pattern bases should be set", zoneBases);
        assertTrue("Should have 6 zone pattern bases", zoneBases.length == 6);

        int previousBase = -1;
        for (int i = 0; i < zoneBases.length; i++) {
            int base = zoneBases[i];
            assertTrue("Zone pattern base should be positive for zone " + (i + 1), base > 0);
            assertTrue("Zone pattern bases should increase monotonically", base > previousBase);
            previousBase = base;

            PatternAtlas.Entry entry = graphicsManager.getPatternAtlasEntry(base);
            assertNotNull("Pattern atlas entry missing for zone " + (i + 1) + " base " + base, entry);
        }
    }

    @Test
    public void testInitialDrawRendersStageBlocks() throws Exception {
        manager.initialize(0);
        manager.draw();

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull("Renderer should be created during initialization", renderer);

        assertTrue("Special stage should render at least one block on first draw",
                renderer.getLastRenderedBlocks() > 0);
        assertTrue("Special stage should detect valid block cells on first draw",
                renderer.getLastValidBlockCells() > 0);
    }

    @Test
    public void testUpdateDoesNotRunAwayFromSpawnImmediately() throws Exception {
        manager.initialize(0);
        for (int i = 0; i < 120; i++) {
            manager.update();
        }
        manager.draw();

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull("Renderer should be created during initialization", renderer);
        assertTrue("Special stage should still render blocks after updates",
                renderer.getLastRenderedBlocks() > 0);
    }
}
