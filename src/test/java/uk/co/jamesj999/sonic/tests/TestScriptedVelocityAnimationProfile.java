package uk.co.jamesj999.sonic.tests;

import org.junit.Test;
import uk.co.jamesj999.sonic.sprites.animation.ScriptedVelocityAnimationProfile;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

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
    public void keepsSlideAnimationWhenTransientAirFlagIsSet() {
        ScriptedVelocityAnimationProfile profile = createProfile();
        TestSprite sprite = new TestSprite();
        sprite.setSliding(true);
        sprite.setAir(true);
        sprite.setGSpeed((short) 0x0800);

        Integer animId = profile.resolveAnimationId(sprite, 0, 32);

        assertEquals(13, animId.intValue());
    }

    private static ScriptedVelocityAnimationProfile createProfile() {
        return new ScriptedVelocityAnimationProfile(
                0,   // idle
                1,   // walk
                2,   // run
                3,   // roll
                4,   // roll2
                5,   // push
                6,   // duck
                7,   // lookUp
                8,   // spindash
                9,   // spring
                10,  // death
                11,  // hurt
                12,  // skid
                13,  // slide
                14,  // air
                -1,  // balance
                -1,  // balance2
                -1,  // balance3
                -1,  // balance4
                0x40,
                0x600,
                0,
                true
        );
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
