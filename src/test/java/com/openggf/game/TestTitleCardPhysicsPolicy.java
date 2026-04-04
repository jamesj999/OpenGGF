package com.openggf.game;

import com.openggf.game.sonic1.titlecard.Sonic1TitleCardManager;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Documents the per-game title-card physics policy from the disassemblies.
 */
public class TestTitleCardPhysicsPolicy {

    @Test
    public void sonic1BlocksPlayerPhysicsDuringLockedTitleCardPhase() {
        assertFalse(Sonic1TitleCardManager.getInstance().shouldRunPlayerPhysics());
    }

    @Test
    public void sonic2RunsPlayerPhysicsDuringLockedTitleCardPhase() {
        assertTrue(TitleCardManager.getInstance().shouldRunPlayerPhysics());
    }

    @Test
    public void sonic3kBlocksPlayerPhysicsDuringLockedTitleCardPhase() {
        assertFalse(Sonic3kTitleCardManager.getInstance().shouldRunPlayerPhysics());
    }
}
