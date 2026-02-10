package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.GameStateManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Sonic1SpecialStageResultsScreenTest {

    private GameStateManager gameState;

    @Before
    public void setUp() {
        gameState = GameServices.gameState();
        gameState.configureSpecialStageProgress(6, 6);
        gameState.resetSession();
    }

    @After
    public void tearDown() {
        gameState.resetSession();
    }

    @Test
    public void testFailedScenarioUsesSpecialStageMessage() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(0, false, 0, 0);

        assertEquals("Failed run should show SPECIAL STAGE message frame", 11,
                screen.getScenarioFrameForTesting());
    }

    @Test
    public void testGotEmeraldScenarioUsesChaosEmeraldsMessage() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(12, true, 1, 3);

        assertEquals("Emerald run should show CHAOS EMERALDS message frame", 10,
                screen.getScenarioFrameForTesting());
    }

    @Test
    public void testGotAllEmeraldsScenarioUsesGotThemAllMessage() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(20, true, 5, 6);

        assertEquals("All-emerald run should show SONIC GOT THEM ALL frame", 12,
                screen.getScenarioFrameForTesting());
    }

    @Test
    public void testRingBonusTalliesIntoScoreAndCompletes() {
        Sonic1SpecialStageResultsScreen screen =
                new Sonic1SpecialStageResultsScreen(5, true, 0, 1);

        int startScore = gameState.getScore();

        int frame = 0;
        while (!screen.isComplete() && frame < 1000) {
            frame++;
            screen.update(frame, null);
        }

        assertTrue("Results flow should complete within expected frame budget", screen.isComplete());
        assertEquals("Ring bonus should fully count down", 0, screen.getRingBonus());
        assertEquals("Score should increase by ring bonus total", startScore + 50, gameState.getScore());
    }
}
