package com.openggf.game;

import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestSidekickArtBankAllocation {

    @Test
    void differentCharactersGetSlotZero() {
        // sonic main, tails sidekick — no conflict
        Map<Integer, Integer> slots = LevelManager.computeVramSlots("sonic", List.of("tails"));
        assertEquals(0, slots.get(0));
    }

    @Test
    void sameCharacterGetsShiftedSlot() {
        // sonic main, sonic sidekick — conflict, sidekick gets slot 1
        Map<Integer, Integer> slots = LevelManager.computeVramSlots("sonic", List.of("sonic"));
        assertEquals(1, slots.get(0));
    }

    @Test
    void multipleSameCharacterGetIncrementingSlots() {
        // sonic main, sonic+sonic+sonic sidekicks
        Map<Integer, Integer> slots = LevelManager.computeVramSlots("sonic", List.of("sonic", "sonic", "sonic"));
        assertEquals(1, slots.get(0));
        assertEquals(2, slots.get(1));
        assertEquals(3, slots.get(2));
    }

    @Test
    void mixedCharacterSlots() {
        // sonic main, tails+sonic+sonic sidekicks
        Map<Integer, Integer> slots = LevelManager.computeVramSlots("sonic", List.of("tails", "sonic", "sonic"));
        assertEquals(0, slots.get(0)); // tails: no conflict
        assertEquals(1, slots.get(1)); // first sonic sidekick: slot 1
        assertEquals(2, slots.get(2)); // second sonic sidekick: slot 2
    }
}
