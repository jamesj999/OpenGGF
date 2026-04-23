package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kHeadlessInLevelTitleCardProgression {

    @Test
    void headlessRunnerAdvancesInLevelTitleCardOverlay() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();

        TitleCardProvider provider = GameServices.module().getTitleCardProvider();
        provider.reset();
        provider.initializeInLevel(0, 0);

        assertTrue(provider.isOverlayActive(), "In-level title card should start active.");

        fixture.stepIdleFrames(200);

        assertFalse(provider.isOverlayActive(),
                "Headless stepping should advance the in-level S3K title card until it completes.");
    }
}
