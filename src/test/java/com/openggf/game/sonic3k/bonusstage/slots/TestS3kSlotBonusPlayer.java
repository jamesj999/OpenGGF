package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.sprites.playable.CustomPlayablePhysics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusPlayer {

    @Test
    void slotPlayerKeepsMainSpriteCode() {
        S3kSlotBonusPlayer player = new S3kSlotBonusPlayer("sonic", (short) 0x460, (short) 0x430, null);
        assertEquals("sonic", player.getCode());
        assertTrue(player instanceof CustomPlayablePhysics);
    }
}
