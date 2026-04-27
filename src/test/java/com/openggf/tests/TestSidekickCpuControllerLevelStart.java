package com.openggf.tests;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSidekickCpuControllerLevelStart {
    private static final int ROM_FOLLOW_DELAY_FRAMES = 16;

    @BeforeEach
    void setUp() {
        TestEnvironment.resetAll();
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void firstGameplayTickUsesRomSidekickStartPlacementAndFollowInput() {
        TestablePlayableSprite leader = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        leader.setCentreX((short) 0x60);
        leader.setCentreY((short) 0x290);

        TestableTailsSprite tails = new TestableTailsSprite("tails_p2", (short) 0, (short) 0);
        tails.setCentreX((short) 0x38);
        tails.setCentreY((short) 0x28C);
        tails.setCpuControlled(true);

        SidekickCpuController controller = new SidekickCpuController(tails, leader);
        tails.setCpuController(controller);

        controller.update(1);

        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());
        assertEquals(0x60, leader.getCentreX(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF);
        assertEquals(0x290, leader.getCentreY(ROM_FOLLOW_DELAY_FRAMES) & 0xFFFF);
        assertEquals(0x40, tails.getCentreX() & 0xFFFF);
        assertEquals(0x294, tails.getCentreY() & 0xFFFF);
        assertTrue(controller.getInputRight(), "Tails should generate follow input on the first gameplay tick");
    }
}
