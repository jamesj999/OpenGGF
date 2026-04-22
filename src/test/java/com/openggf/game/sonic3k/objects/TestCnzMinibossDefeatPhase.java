package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6 defeat-phase tests for CNZ1 miniboss.
 *
 * <p>Plan-vs-ROM divergence note: the plan text for T6 describes
 * "routine C = Lower2" and "routine E = End", but the ROM dispatch
 * table at {@code CNZMiniboss_Index} (sonic3k.asm:144874-144882)
 * places {@code Obj_CNZMinibossClosing} at slot C (144968) and
 * {@code Obj_CNZMinibossLower2} at slot E (144972). {@code
 * Obj_CNZMinibossEnd} (144984) is not in the dispatch table at all;
 * it is installed via {@code $34(a0)} during the defeat chain
 * (CNZMiniboss_BossDefeated -> Wait_FadeToLevelMusic -> Obj_CNZMinibossEnd
 * -> Obj_CNZMinibossEndGo). The tests below follow the ROM.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestCnzMinibossDefeatPhase {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void lower2AdvancesOnePixelPerFrameForCounter() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x0200, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);
        boss.forceRoutineForTest(0x0E);  // Lower2 is slot E (ROM: 144972), not C
        boss.setLower2CounterForTest(4); // $43(a0) = 4, decremented per frame
        int startY = boss.getCentreY();

        for (int i = 0; i < 6; i++) boss.update(i, fixture.sprite());
        assertTrue(boss.getCentreY() >= startY + 4,
                "Lower2 must add #1 to y_pos each frame until $43 expires");
    }

    @Test
    void hitsReduceCounterAndSixthHitTriggersDefeat() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x0200, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        for (int i = 0; i < Sonic3kConstants.CNZ_MINIBOSS_HIT_COUNT; i++) {
            boss.simulateHitForTest();
        }
        assertEquals(0, boss.getRemainingHits());
        assertTrue(boss.isDefeated(),
                "After the 6th hit the boss must be in defeat state");
    }

    @Test
    void defeatClearsBossFlagAndReleasesWallGrab() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        // Arm the arena-entry flags so the defeat path has something to clear.
        Sonic3kCNZEvents cnz = getCnzEvents();
        cnz.setBossFlag(true);
        cnz.setWallGrabSuppressed(true);

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x0200, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        for (int i = 0; i < Sonic3kConstants.CNZ_MINIBOSS_HIT_COUNT; i++) {
            boss.simulateHitForTest();
        }
        // Let the defeat sequencer advance Obj_CNZMinibossEnd -> Obj_CNZMinibossEndGo.
        for (int i = 0; i < 180; i++) boss.update(i, fixture.sprite());

        assertFalse(cnz.isBossFlag(),
                "Obj_CNZMinibossEndGo must clr.b Boss_flag (sonic3k.asm:144998)");
    }

    private static Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager events =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return events.getCnzEvents();
    }
}
