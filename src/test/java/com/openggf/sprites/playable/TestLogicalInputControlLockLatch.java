package com.openggf.sprites.playable;

import com.openggf.game.session.EngineContext;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies the ROM-faithful Ctrl_1_locked latch on
 * {@link AbstractPlayableSprite#setLogicalInputState} is gated by
 * {@link PhysicsFeatureSet#controlLockLatchesLogicalInput()}.
 *
 * <p>ROM ref (sonic3k.asm:21541-21545 {@code loc_10760}, S2 s2.asm:35933-35935
 * {@code Obj01_Control}):
 * <pre>
 *   tst.b   (Ctrl_1_locked).w     ; Control_Locked for S2
 *   bne.s   loc_10780             ; if locked, SKIP the copy
 *   move.w  (Ctrl_1).w,(Ctrl_1_logical).w
 * </pre>
 *
 * <p>The latch is currently active for S3K only ({@code SONIC_3K} sets
 * {@code controlLockLatchesLogicalInput=true}). S1/S2 keep the latch off
 * to preserve their existing trace baselines and {@code setControlLocked}
 * call-site semantics; the previous universal-latch attempt
 * (commit f3347ea89, REVERTED in 9793e4617) regressed S2 EHZ trace replay
 * from PASS to F5121.
 */
class TestLogicalInputControlLockLatch {

    private Sonic2GameModule module;

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        module = new Sonic2GameModule();
        GameModuleRegistry.setCurrent(module);
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void s3kFlagSetSkipsLogicalInputWriteSoPreviousValuePersists() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sprite.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

        // Frame N-1: no lock, RIGHT pressed -> logical input recorded as RIGHT.
        sprite.setControlLocked(false);
        sprite.setLogicalInputState(false, false, false, true, false);
        sprite.endOfTick();
        short historyBeforeLock = sprite.getInputHistory(0);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, historyBeforeLock,
                "S3K frame N-1: unlocked RIGHT must record INPUT_RIGHT");

        // Frame N: lock engages, publishInputState passes filtered (zeroed) inputs.
        // Latch must skip the write so logicalInputState retains RIGHT.
        sprite.setControlLocked(true);
        sprite.setLogicalInputState(false, false, false, false, false);
        sprite.endOfTick();
        short historyDuringLock = sprite.getInputHistory(0);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, historyDuringLock,
                "S3K frame N: while controlLocked + latch flag, logicalInputState must persist");
    }

    @Test
    void s3kFlagSetClearedRestoresFreshWrites() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sprite.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_3K);

        // Seed RIGHT while unlocked.
        sprite.setLogicalInputState(false, false, false, true, false);
        sprite.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sprite.getInputHistory(0));

        // Lock + zero attempt: latched value persists.
        sprite.setControlLocked(true);
        sprite.setLogicalInputState(false, false, false, false, false);
        sprite.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sprite.getInputHistory(0));

        // Unlock: fresh writes resume normally.
        sprite.setControlLocked(false);
        sprite.setLogicalInputState(false, false, true, false, false);
        sprite.endOfTick();
        short afterUnlock = sprite.getInputHistory(0);
        assertEquals(AbstractPlayableSprite.INPUT_LEFT, afterUnlock,
                "S3K after unlock: logical input must update again");
        assertNotEquals(AbstractPlayableSprite.INPUT_RIGHT, afterUnlock);
    }

    @Test
    void s2FlagClearedDoesNotLatchLogicalInput() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sprite.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_2);

        // Frame N-1: no lock, RIGHT pressed.
        sprite.setControlLocked(false);
        sprite.setLogicalInputState(false, false, false, true, false);
        sprite.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sprite.getInputHistory(0));

        // Frame N: lock engages, zero inputs pushed; S2 must NOT latch.
        // The post-lock zero state is what existing S2 setControlLocked sites
        // (FlipperObjectInstance, CPZSpinTubeObjectInstance,
        // Sonic2DeathEggRobotInstance, SignpostObjectInstance) expect for
        // animation gating. The previous universal-latch attempt regressed
        // S2 EHZ trace replay from PASS to F5121.
        sprite.setControlLocked(true);
        sprite.setLogicalInputState(false, false, false, false, false);
        sprite.endOfTick();
        assertEquals((short) 0, sprite.getInputHistory(0),
                "S2 frame N: latch flag is false, lock must zero logicalInputState");
    }

    @Test
    void s1FlagClearedDoesNotLatchLogicalInput() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sprite.setPhysicsFeatureSetForTest(PhysicsFeatureSet.SONIC_1);

        // Frame N-1: unlocked RIGHT.
        sprite.setControlLocked(false);
        sprite.setLogicalInputState(false, false, false, true, false);
        sprite.endOfTick();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, sprite.getInputHistory(0));

        // Frame N: locked + zero inputs; S1 must NOT latch.
        sprite.setControlLocked(true);
        sprite.setLogicalInputState(false, false, false, false, false);
        sprite.endOfTick();
        assertEquals((short) 0, sprite.getInputHistory(0),
                "S1 frame N: latch flag is false, lock must zero logicalInputState");
    }
}
