package com.openggf.tests;

import org.junit.Test;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertEquals;

public class TestScriptedVelocityAnimationProfile {

    @Test
    public void resolvesSlideAnimationWhenSlidingOnGround() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setAir(false);
        sprite.setGSpeed((short) 0x0800); // would normally choose run

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(13, animId.intValue());
    }

    @Test
    public void keepsHurtAnimationPriorityOverSlide() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setAir(false);
        sprite.setHurt(true);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(11, animId.intValue());
    }

    @Test
    public void usesRollAnimationWhenSlidingAndAirborne() {
        // ROM: when airborne (e.g. jumping off water slide), the jump/roll mode
        // overwrites obAnim with id_Roll. Slide animation only applies on ground.
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setRolling(true);
        sprite.setAir(true);
        sprite.setGSpeed((short) 0x0800);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(3, animId.intValue()); // rollAnimId, not slideAnimId
    }

    private static ScriptedVelocityAnimationProfile createProfile() {
        return new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(0)
                .setWalkAnimId(1)
                .setRunAnimId(2)
                .setRollAnimId(3)
                .setRoll2AnimId(4)
                .setPushAnimId(5)
                .setDuckAnimId(6)
                .setLookUpAnimId(7)
                .setSpindashAnimId(8)
                .setSpringAnimId(9)
                .setDeathAnimId(10)
                .setHurtAnimId(11)
                .setSkidAnimId(12)
                .setSlideAnimId(13)
                .setAirAnimId(14)
                .setWalkSpeedThreshold(0x40)
                .setRunSpeedThreshold(0x600)
                .setFallbackFrame(0)
                .setAnglePreAdjust(true);
    }

    private static class TestSprite extends AbstractPlayableSprite {
        TestSprite() {
            super("test", (short) 0, (short) 0);
        }

        @Override
        public void draw() {
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 12;
            runDecel = 128;
            friction = 12;
            max = 1536;
            jump = 1664;
        }

        @Override
        protected void createSensorLines() {
        }
    }
}
