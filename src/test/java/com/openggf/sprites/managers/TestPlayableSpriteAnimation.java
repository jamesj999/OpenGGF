package com.openggf.sprites.managers;

import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SingletonResetExtension.class)
@FullReset
public class TestPlayableSpriteAnimation {

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void s3kIdleToWalkAnimationChangeClearsGroundPush() {
        TestablePlayableSprite sprite = createSprite(PhysicsFeatureSet.SONIC_3K);
        sprite.setAnimationId(5);
        sprite.setMovementInputActive(true);
        sprite.setPushing(true);

        sprite.getAnimationManager().update(0);

        assertFalse(sprite.getPushing(),
                "S3K Animate_Tails2P clears Status_Push when MoveRight changes anim from idle to walk");
        assertEquals(0, sprite.getAnimationId(),
                "After the push clear, animation resolution should choose walk instead of push");
    }

    @Test
    public void s3kRunToPushDoesNotUseIdleToWalkClear() {
        TestablePlayableSprite sprite = createSprite(PhysicsFeatureSet.SONIC_3K);
        sprite.setAnimationId(1);
        sprite.setMovementInputActive(true);
        sprite.setPushing(true);
        sprite.setGSpeed((short) 0x0600);

        sprite.getAnimationManager().update(0);

        assertTrue(sprite.getPushing(),
                "Ground push should remain when the previous animation was not idle");
        assertEquals(4, sprite.getAnimationId(),
                "The existing push script should still render for non-idle previous animations");
    }

    @Test
    public void s1IdleToWalkDoesNotClearPush() {
        TestablePlayableSprite sprite = createSprite(PhysicsFeatureSet.SONIC_1);
        sprite.setAnimationId(5);
        sprite.setMovementInputActive(true);
        sprite.setPushing(true);

        sprite.getAnimationManager().update(0);

        assertTrue(sprite.getPushing(),
                "S1 keeps the existing FixBugs-gated behavior for animation-change push clears");
        assertEquals(4, sprite.getAnimationId(),
                "S1 should still select the push animation when Status_Push is set");
    }

    private static TestablePlayableSprite createSprite(PhysicsFeatureSet featureSet) {
        TestablePlayableSprite sprite = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        sprite.setPhysicsFeatureSetForTest(featureSet);
        sprite.setAnimationProfile(new ScriptedVelocityAnimationProfile()
                .setIdleAnimId(5)
                .setWalkAnimId(0)
                .setRunAnimId(1)
                .setRollAnimId(2)
                .setPushAnimId(4)
                .setAirAnimId(0)
                .setRunSpeedThreshold(0x600));
        sprite.setAnimationSet(createAnimationSet());
        sprite.setAir(false);
        sprite.setRolling(false);
        sprite.setGSpeed((short) 0);
        return sprite;
    }

    private static SpriteAnimationSet createAnimationSet() {
        SpriteAnimationSet set = new SpriteAnimationSet();
        SpriteAnimationScript script = new SpriteAnimationScript(0, List.of(0), SpriteAnimationEndAction.LOOP, 0);
        set.addScript(0, script);
        set.addScript(1, script);
        set.addScript(2, script);
        set.addScript(4, script);
        set.addScript(5, script);
        return set;
    }
}
