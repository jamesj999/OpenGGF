package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.credits.Sonic1EndingProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.credits.Sonic2EndingProvider;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Cross-game tests verifying the {@link EndingProvider} contract is
 * satisfied by all game modules that provide an ending implementation.
 * <p>
 * Tests here are ROM-free and verify:
 * <ul>
 *   <li>Each GameModule returns the correct provider type</li>
 *   <li>EndingPhase enum covers all expected values</li>
 *   <li>Pre-initialize safety (no exceptions from accessors)</li>
 * </ul>
 */
public class TestEndingProviderContract {

    // ========================================================================
    // Sonic 1 GameModule returns Sonic1EndingProvider
    // ========================================================================

    @Test
    public void testSonic1GameModuleHasEndingProvider() {
        Sonic1GameModule module = new Sonic1GameModule();
        EndingProvider provider = module.getEndingProvider();
        assertNotNull("Sonic1GameModule should provide an EndingProvider", provider);
        assertTrue("Sonic1GameModule EndingProvider should be Sonic1EndingProvider",
                provider instanceof Sonic1EndingProvider);
    }

    // ========================================================================
    // Sonic 2 GameModule returns Sonic2EndingProvider
    // ========================================================================

    @Test
    public void testSonic2GameModuleHasEndingProvider() {
        Sonic2GameModule module = new Sonic2GameModule();
        EndingProvider provider = module.getEndingProvider();
        assertNotNull("Sonic2GameModule should provide an EndingProvider", provider);
        assertTrue("Sonic2GameModule EndingProvider should be Sonic2EndingProvider",
                provider instanceof Sonic2EndingProvider);
    }

    // ========================================================================
    // GameModule default returns null (for games without endings)
    // ========================================================================

    @Test
    public void testDefaultEndingProviderIsNull() {
        // The default implementation in GameModule interface returns null
        // Verify this contract by checking that the method exists and is accessible
        EndingProvider defaultResult = new GameModule() {
            @Override
            public String getIdentifier() { return "test"; }
            @Override
            public com.openggf.data.Game createGame(com.openggf.data.Rom rom) { return null; }
            @Override
            public com.openggf.level.objects.ObjectRegistry createObjectRegistry() { return null; }
            @Override
            public com.openggf.audio.GameAudioProfile getAudioProfile() { return null; }
            @Override
            public com.openggf.level.objects.TouchResponseTable createTouchResponseTable(
                    com.openggf.data.RomByteReader r) { return null; }
            @Override
            public int getPlaneSwitcherObjectId() { return 0; }
            @Override
            public com.openggf.level.objects.PlaneSwitcherConfig getPlaneSwitcherConfig() { return null; }
            @Override
            public LevelEventProvider getLevelEventProvider() { return null; }
            @Override
            public RespawnState createRespawnState() { return null; }
            @Override
            public LevelState createLevelState() { return null; }
            @Override
            public ZoneRegistry getZoneRegistry() { return null; }
            @Override
            public ScrollHandlerProvider getScrollHandlerProvider() { return null; }
            @Override
            public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
            @Override
            public RomOffsetProvider getRomOffsetProvider() { return null; }
            @Override
            public DebugModeProvider getDebugModeProvider() { return null; }
            @Override
            public DebugOverlayProvider getDebugOverlayProvider() { return null; }
            @Override
            public ZoneArtProvider getZoneArtProvider() { return null; }
            @Override
            public ObjectArtProvider getObjectArtProvider() { return null; }
        }.getEndingProvider();

        assertNull("Default GameModule.getEndingProvider() should return null", defaultResult);
    }

    // ========================================================================
    // EndingPhase enum completeness
    // ========================================================================

    @Test
    public void testEndingPhaseHasFiveValues() {
        EndingPhase[] phases = EndingPhase.values();
        assertEquals("EndingPhase should have 5 values", 5, phases.length);
    }

    @Test
    public void testEndingPhaseValuesExist() {
        // Verify all expected enum constants exist (compilation would fail if not,
        // but this documents the contract explicitly)
        assertNotNull(EndingPhase.CUTSCENE);
        assertNotNull(EndingPhase.CREDITS_TEXT);
        assertNotNull(EndingPhase.CREDITS_DEMO);
        assertNotNull(EndingPhase.POST_CREDITS);
        assertNotNull(EndingPhase.FINISHED);
    }

    @Test
    public void testEndingPhaseOrdinalOrder() {
        // Verify phases are in the expected sequential order
        assertTrue("CUTSCENE should come before CREDITS_TEXT",
                EndingPhase.CUTSCENE.ordinal() < EndingPhase.CREDITS_TEXT.ordinal());
        assertTrue("CREDITS_TEXT should come before CREDITS_DEMO",
                EndingPhase.CREDITS_TEXT.ordinal() < EndingPhase.CREDITS_DEMO.ordinal());
        assertTrue("CREDITS_DEMO should come before POST_CREDITS",
                EndingPhase.CREDITS_DEMO.ordinal() < EndingPhase.POST_CREDITS.ordinal());
        assertTrue("POST_CREDITS should come before FINISHED",
                EndingPhase.POST_CREDITS.ordinal() < EndingPhase.FINISHED.ordinal());
    }

    // ========================================================================
    // Pre-initialize safety
    // ========================================================================

    @Test
    public void testSonic1ProviderSafeBeforeInitialize() {
        Sonic1EndingProvider provider = new Sonic1EndingProvider();
        // These should not throw before initialize() is called
        assertFalse(provider.isComplete());
        assertNotNull(provider.getCurrentPhase());
        assertEquals(EndingPhase.CREDITS_TEXT, provider.getCurrentPhase());
    }

    @Test
    public void testSonic2ProviderSafeBeforeInitialize() {
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        // These should not throw before initialize() is called
        assertFalse(provider.isComplete());
        assertNotNull(provider.getCurrentPhase());
        assertEquals(EndingPhase.CUTSCENE, provider.getCurrentPhase());
    }

    // ========================================================================
    // S1 initial phase differs from S2
    // ========================================================================

    @Test
    public void testSonic1InitialPhaseIsCreditsText() {
        // S1 has no cutscene, goes straight to credits text
        Sonic1EndingProvider provider = new Sonic1EndingProvider();
        assertEquals("S1 initial phase should be CREDITS_TEXT (no cutscene)",
                EndingPhase.CREDITS_TEXT, provider.getCurrentPhase());
    }

    @Test
    public void testSonic2InitialPhaseIsCutscene() {
        // S2 starts with a cutscene
        Sonic2EndingProvider provider = new Sonic2EndingProvider();
        assertEquals("S2 initial phase should be CUTSCENE",
                EndingPhase.CUTSCENE, provider.getCurrentPhase());
    }
}
