package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.level.SeamlessLevelTransitionRequest.TransitionType;
import com.openggf.level.WaterSystem;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * Documents and verifies the intentional omissions in
 * {@link LevelManager#executeActTransition}.
 *
 * <p>In the S3K ROM, seamless act transitions only reload layout and collision,
 * rebuild object/ring managers, apply coordinate offsets, restore camera
 * bounds, and reinitialize level events. They do not reinitialize unrelated
 * zone/game state such as water or game modules. Animated content is the
 * exception in this engine: our managers bind act-specific scripts at
 * construction time, so they must be refreshed when the act changes.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestActTransitionIntentionalSkips {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static final int ACT_2 = 1;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) {
            sharedLevel.dispose();
        }
    }

    private HeadlessTestFixture fixture;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 96, (short) 655)
                .build();

        Camera.getInstance().setFocusedSprite(fixture.sprite());
        Camera.getInstance().setFrozen(false);
    }

    private SeamlessLevelTransitionRequest transitionToAct2() {
        return SeamlessLevelTransitionRequest
                .builder(TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(ZONE_EHZ, ACT_2)
                .preserveMusic(true)
                .build();
    }

    @Test
    public void waterSystemInstancePreservedAcrossTransition() throws Exception {
        WaterSystem before = WaterSystem.getInstance();
        assertNotNull("WaterSystem should exist before transition", before);

        LevelManager.getInstance().executeActTransition(transitionToAct2());

        WaterSystem after = WaterSystem.getInstance();
        assertSame("WaterSystem instance must survive act transition", before, after);
    }

    @Test
    public void zoneFeatureProviderPreservedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        ZoneFeatureProvider before = lm.getZoneFeatureProvider();

        lm.executeActTransition(transitionToAct2());

        ZoneFeatureProvider after = lm.getZoneFeatureProvider();
        assertSame("ZoneFeatureProvider must survive act transition", before, after);
    }

    @Test
    public void gameModulePreservedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        GameModule beforeLm = lm.getGameModule();
        GameModule beforeRegistry = GameModuleRegistry.getCurrent();
        assertNotNull("GameModule should exist before transition", beforeLm);
        assertNotNull("GameModuleRegistry should have a module before transition", beforeRegistry);

        lm.executeActTransition(transitionToAct2());

        GameModule afterLm = lm.getGameModule();
        GameModule afterRegistry = GameModuleRegistry.getCurrent();
        assertSame("LevelManager's GameModule must survive act transition", beforeLm, afterLm);
        assertSame("GameModuleRegistry's module must survive act transition", beforeRegistry, afterRegistry);
    }

    @Test
    public void gameInstancePreservedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        Object beforeGame = lm.getGame();
        assertNotNull("Game should exist before transition", beforeGame);

        lm.executeActTransition(transitionToAct2());

        Object afterGame = lm.getGame();
        assertSame("Game instance must survive act transition", beforeGame, afterGame);
    }

    @Test
    public void animatedPatternManagerReinitializedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        AnimatedPatternManager before = lm.getAnimatedPatternManager();
        assertNotNull("AnimatedPatternManager should exist before transition", before);

        lm.executeActTransition(transitionToAct2());

        AnimatedPatternManager after = lm.getAnimatedPatternManager();
        assertNotNull("AnimatedPatternManager should exist after transition", after);
        assertNotSame("AnimatedPatternManager must refresh for the new act", before, after);
    }

    @Test
    public void animatedPaletteManagerReinitializedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        AnimatedPaletteManager before = lm.getAnimatedPaletteManager();
        assertNotNull("AnimatedPaletteManager should exist before transition", before);

        lm.executeActTransition(transitionToAct2());

        AnimatedPaletteManager after = lm.getAnimatedPaletteManager();
        assertNotNull("AnimatedPaletteManager should exist after transition", after);
        assertNotSame("AnimatedPaletteManager must refresh for the new act", before, after);
    }

    @Test
    public void objectManagerRebuiltAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        ObjectManager before = lm.getObjectManager();
        assertNotNull("ObjectManager should exist before transition", before);

        lm.executeActTransition(transitionToAct2());

        ObjectManager after = lm.getObjectManager();
        assertNotNull("ObjectManager should exist after transition", after);
        assertNotSame("ObjectManager must be a new instance", before, after);
    }

    @Test
    public void levelEventsReinitializedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        LevelEventProvider provider = GameModuleRegistry.getCurrent().getLevelEventProvider();
        assertNotNull("LevelEventProvider should exist", provider);

        lm.executeActTransition(transitionToAct2());

        assertEquals("Zone should be EHZ after transition", ZONE_EHZ, lm.getCurrentZone());
        assertEquals("Act should be 2 (index 1) after transition", ACT_2, lm.getCurrentAct());

        LevelEventProvider afterProvider = GameModuleRegistry.getCurrent().getLevelEventProvider();
        assertSame("LevelEventProvider instance is reused", provider, afterProvider);
    }
}
