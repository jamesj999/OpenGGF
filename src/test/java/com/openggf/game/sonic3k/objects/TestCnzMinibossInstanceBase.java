package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.boss.AbstractBossInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCnzMinibossInstanceBase {

    private static ObjectSpawn spawn() {
        return new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS,
                0, 0, false, 0);
    }

    @Test
    void extendsAbstractBossInstance() {
        CnzMinibossInstance boss = new CnzMinibossInstance(spawn());
        assertTrue(boss instanceof AbstractBossInstance,
                "CnzMinibossInstance must extend AbstractBossInstance after workstream-D T3");
    }

    @Test
    void reportsRomHitCount() {
        CnzMinibossInstance boss = new CnzMinibossInstance(spawn());
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_HIT_COUNT,
                boss.getRemainingHits(),
                "state.hitCount initialised from getInitialHitCount() (ROM sonic3k.asm:144888)");
    }

    @Test
    void preservesArenaChunkHook() {
        CnzMinibossInstance boss = new CnzMinibossInstance(spawn());
        int originalY = boss.getCentreY();
        boss.onArenaChunkDestroyed();
        assertEquals(originalY + 0x20, boss.getCentreY(),
                "onArenaChunkDestroyed must advance centreY by one arena row (0x20 px)");
    }

    @Test
    void initialStateMatchesSpawn() {
        CnzMinibossInstance boss = new CnzMinibossInstance(spawn());
        assertEquals(0x3240, boss.getCentreX(),
                "centreX mirrors spawn x (0x3240)");
        assertEquals(0x02B8, boss.getCentreY(),
                "centreY mirrors spawn y (0x02B8)");
    }
}
