package com.openggf.sprites.playable;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
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
 * {@link AbstractPlayableSprite#setLogicalInputState}.
 *
 * <p>ROM ref (sonic3k.asm:21541-21545 {@code loc_10760}, S2 s2.asm:35933-35935
 * {@code Obj01_Control}):
 * <pre>
 *   tst.b   (Ctrl_1_locked).w     ; Control_Locked for S2
 *   bne.s   loc_10780             ; if locked, SKIP the copy
 *   move.w  (Ctrl_1).w,(Ctrl_1_logical).w
 * </pre>
 * The previous frame's logical pad state must persist while controls are
 * locked so that {@code Sonic_RecordPos} (sonic3k.asm:22132) writes the
 * latched value into {@code Stat_table} for the sidekick CPU follower
 * delayed input read ({@code Tails_CPU_Control}, sonic3k.asm:26683-26689).
 */
class TestLogicalInputControlLockLatch {

    private Sonic2GameModule module;

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
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
    void controlLockedSkipsLogicalInputWriteSoPreviousValuePersists() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);

        // Frame N-1: no lock, RIGHT pressed -> logical input recorded as RIGHT.
        sprite.setControlLocked(false);
        sprite.setLogicalInputState(false, false, false, true, false);
        sprite.endOfTick();
        short historyBeforeLock = sprite.getInputHistory(0);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, historyBeforeLock,
                "frame N-1: unlocked RIGHT must record INPUT_RIGHT");

        // Frame N: lock engages, publishInputState passes filtered (zeroed) inputs.
        // The latch must skip the write so logicalInputState retains RIGHT.
        sprite.setControlLocked(true);
        sprite.setLogicalInputState(false, false, false, false, false);
        sprite.endOfTick();
        short historyDuringLock = sprite.getInputHistory(0);
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT, historyDuringLock,
                "frame N: while controlLocked, logicalInputState must persist");
    }

    @Test
    void controlLockedClearedRestoresFreshWrites() {
        TestablePlayableSprite sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);

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
                "after unlock: logical input must update again");
        assertNotEquals(AbstractPlayableSprite.INPUT_RIGHT, afterUnlock);
    }
}
