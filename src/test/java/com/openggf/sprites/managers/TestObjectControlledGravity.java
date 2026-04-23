package com.openggf.sprites.managers;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestObjectControlledGravity {

    @Test
    void gravityIsSkippedWhenObjectControlled() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)  // AIZ1 — any S3K level works for this physics test
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        sonic.setAir(true);
        sonic.setObjectControlled(true);
        sonic.setYSpeed((short) 0);
        short before = sonic.getYSpeed();

        // 5 booleans: up, down, left, right, jump — all false = idle frame
        fixture.stepFrame(false, false, false, false, false);

        short after = sonic.getYSpeed();
        assertEquals(before, after,
                "y_speed must not change when object-controlled (gravity gated)");
    }

    @Test
    void gravityStillAppliedWhenNotObjectControlled() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0, 0)
                .build();
        AbstractPlayableSprite sonic = fixture.sprite();

        sonic.setAir(true);
        sonic.setObjectControlled(false);
        sonic.setYSpeed((short) 0);
        short before = sonic.getYSpeed();

        fixture.stepFrame(false, false, false, false, false);

        short after = sonic.getYSpeed();
        assertEquals((short) (before + (short) sonic.getGravity()), after,
                "y_speed must accumulate gravity when not object-controlled");
    }
}
