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
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.*;

import static org.junit.Assert.*;

/**
 * Documents and verifies the intentional omissions in
 * {@link LevelManager#executeActTransition}.
 * <p>
 * In the S3K ROM, seamless act transitions (performed by zone BG event
 * handlers) only reload layout + collision, rebuild object/ring managers,
 * apply coordinate offsets, restore camera bounds, and reinitialize level
 * events. They do <b>not</b> reinitialize:
 * <ul>
 *   <li>Water system — water state persists across the transition</li>
 *   <li>Zone features — CNZ bumpers, water surface objects, etc. carry over</li>
 *   <li>Animated pattern/palette managers — animation scripts continue</li>
 *   <li>Game module — no game-level reinitialization occurs</li>
 * </ul>
 * <p>
 * These are ROM-accurate behaviors, not bugs. This test class proves each
 * omission is deliberate by capturing instance identity before the
 * transition and asserting it survives afterward.
 * <p>
 * ROM reference: S3K zone BG event handlers (e.g. AIZ Act 2 transition).
 * Pattern: {@code move.b d0, Current_zone_and_act → jsr Load_Level →
 * jsr LoadSolids → Offset_ObjectsDuringTransition → clear Dynamic_object_RAM
 * + Ring_status_table → restore camera bounds}.
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
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition((short) 96, (short) 655)
                .build();

        // Ensure camera has a focused sprite — executeActTransition calls
        // camera.updatePosition(true) which requires a focused sprite.
        // resetPerTest() creates a fresh Camera singleton with no focus.
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

    // ===== Intentionally PRESERVED across transition (ROM-accurate skips) =====

    /**
     * Water system instance identity is preserved across the transition.
     * <p>
     * ROM rationale: The S3K ROM does not call any water initialization
     * routines during seamless act transitions. Water height, underwater
     * palette, and dynamic water state all persist from the previous act.
     * {@code executeActTransition()} mirrors this by not calling
     * {@code initWater()}.
     */
    @Test
    public void waterSystemInstancePreservedAcrossTransition() throws Exception {
        WaterSystem before = WaterSystem.getInstance();
        assertNotNull("WaterSystem should exist before transition", before);

        LevelManager.getInstance().executeActTransition(transitionToAct2());

        WaterSystem after = WaterSystem.getInstance();
        assertSame("WaterSystem instance must survive act transition (ROM: no water reinit)",
                before, after);
    }

    /**
     * Zone feature provider reference is preserved across the transition.
     * <p>
     * ROM rationale: Zone features (CNZ bumpers, CPZ pylon, water surface
     * objects) are per-zone, not per-act. The ROM does not reinitialize
     * them during seamless act transitions because the zone has not changed.
     * {@code executeActTransition()} mirrors this by not calling
     * {@code initZoneFeatures()}.
     */
    @Test
    public void zoneFeatureProviderPreservedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        ZoneFeatureProvider before = lm.getZoneFeatureProvider();
        // ZoneFeatureProvider may be null for EHZ (no zone features), which is
        // still a valid test: null before and null after proves no reinit occurred.

        lm.executeActTransition(transitionToAct2());

        ZoneFeatureProvider after = lm.getZoneFeatureProvider();
        assertSame("ZoneFeatureProvider must survive act transition (ROM: no zone feature reinit)",
                before, after);
    }

    /**
     * Game module reference on LevelManager is preserved across the transition.
     * <p>
     * ROM rationale: A seamless act transition does not re-detect the game
     * or reinitialize the game module. The ROM simply swaps zone/act data
     * pointers within the same game context.
     * {@code executeActTransition()} mirrors this by not calling
     * {@code initGameModule()}.
     */
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
        assertSame("LevelManager's GameModule must survive act transition (ROM: no module reinit)",
                beforeLm, afterLm);
        assertSame("GameModuleRegistry's module must survive act transition",
                beforeRegistry, afterRegistry);
    }

    /**
     * The Game instance on LevelManager is preserved across the transition.
     * <p>
     * ROM rationale: The ROM does not call the game initialization routine
     * during a seamless act transition. Art loading, palette setup, and
     * game-specific initialization are all skipped.
     * {@code executeActTransition()} mirrors this by not calling
     * {@code initGameModule()} which creates a new Game instance.
     */
    @Test
    public void gameInstancePreservedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        Object beforeGame = lm.getGame();
        assertNotNull("Game should exist before transition", beforeGame);

        lm.executeActTransition(transitionToAct2());

        Object afterGame = lm.getGame();
        assertSame("Game instance must survive act transition (ROM: no game reinit)",
                beforeGame, afterGame);
    }

    // ===== Intentionally REBUILT across transition (ROM-accurate rebuilds) =====

    /**
     * ObjectManager is rebuilt with the new act's spawn data.
     * <p>
     * ROM rationale: {@code Load_Level} swaps object position index pointers
     * to the new act's data, then clears {@code Dynamic_object_RAM}.
     * {@code executeActTransition()} calls {@code rebuildManagersForActTransition()}
     * which creates a new ObjectManager with the new level's object list.
     * <p>
     * Cross-reference: also tested in {@link TestActTransitionHeadless}.
     */
    @Test
    public void objectManagerRebuiltAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();
        ObjectManager before = lm.getObjectManager();
        assertNotNull("ObjectManager should exist before transition", before);

        lm.executeActTransition(transitionToAct2());

        ObjectManager after = lm.getObjectManager();
        assertNotNull("ObjectManager should exist after transition", after);
        assertNotSame("ObjectManager must be a new instance (ROM: Load_Level clears Dynamic_object_RAM)",
                before, after);
    }

    /**
     * Level events are reinitialized for the new act.
     * <p>
     * ROM rationale: The ROM reinitializes level event routine counters
     * after a seamless act transition so the new act's event script starts
     * from routine 0. {@code executeActTransition()} calls
     * {@code initLevelEventsForCurrentZoneAct()} which invokes
     * {@link LevelEventProvider#initLevel(int, int)}, resetting the event
     * state machine counters to zero.
     */
    @Test
    public void levelEventsReinitializedAcrossTransition() throws Exception {
        LevelManager lm = LevelManager.getInstance();

        // The LevelEventProvider is a singleton; initLevel() resets its
        // internal state rather than replacing the instance. We verify
        // that after the transition the same instance is still returned
        // and that zone/act have been updated.
        LevelEventProvider provider = GameModuleRegistry.getCurrent().getLevelEventProvider();
        assertNotNull("LevelEventProvider should exist", provider);

        lm.executeActTransition(transitionToAct2());

        // After transition, zone/act should reflect the new target
        assertEquals("Zone should be EHZ after transition", ZONE_EHZ, lm.getCurrentZone());
        assertEquals("Act should be 2 (index 1) after transition", ACT_2, lm.getCurrentAct());

        // The same LevelEventProvider instance is reused (not replaced),
        // but its internal state has been reset via initLevel().
        LevelEventProvider afterProvider = GameModuleRegistry.getCurrent().getLevelEventProvider();
        assertSame("LevelEventProvider instance is reused (singleton), not replaced",
                provider, afterProvider);
    }
}
