package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzMinibossInstance;
import com.openggf.game.sonic3k.objects.CnzMinibossScrollControlInstance;
import com.openggf.game.sonic3k.objects.CnzMinibossTopInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlaceholderObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless coverage for Task 7's CNZ Act 1 miniboss slice.
 *
 * <p>These tests intentionally exercise the narrow ROM seams called out in the
 * plan:
 * {@code Obj_CNZMinibossTop} must publish arena-destruction writes,
 * {@code Obj_CNZMinibossScrollControl} must be the producer for the same
 * {@code Events_fg_5} path Task 2 first covered through direct hooks, and the
 * registry must stop returning placeholders for the Act 1 boss slot while
 * leaving Task 8's end-boss slot untouched.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzMinibossArenaHeadless {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    /**
     * ROM anchors:
     * {@code Obj_CNZMinibossTop} writes the impact coordinates through
     * {@code Events_bg+$00/$02} before calling {@code CNZMiniboss_BlockExplosion},
     * while {@code CNZMiniboss_CheckTopHit} increments the defeat/lowering path on
     * the base. The engine keeps those responsibilities explicit by having the top
     * piece publish the chunk-removal request through the CNZ bridge and by having
     * the base accumulate the lowering rows that later slices consume.
     */
    @Test
    void minibossTopHitQueuesArenaChunkRemovalAndLowersBossBase() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        CnzMinibossTopInstance top = new CnzMinibossTopInstance(
                new ObjectSpawn(0x3240, 0x0300, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        top.setServices(services);
        top.attachBossForTest(boss);

        int originalBossCentreY = boss.getCentreY();

        top.forceArenaCollisionForTest(0x3200, 0x0300);
        top.update(0, fixture.sprite());

        Sonic3kCNZEvents events = getCnzEvents();
        assertEquals(0x3200, events.getPendingArenaChunkX());
        assertEquals(0x0300, events.getPendingArenaChunkY());
        assertTrue(events.getDestroyedArenaRows() >= 0x20,
                "Each top-piece collision should publish at least one 0x20-pixel arena row removal");
        assertTrue(boss.getCentreY() > originalBossCentreY,
                "The miniboss base should start lowering once the top piece reports an arena hit");
    }

    /**
     * ROM anchor: {@code Obj_CNZMinibossScrollControl} consumes the boss-defeat
     * signal, waits for the scroll accumulator to reach {@code $1C0}, then sets
     * {@code Events_fg_5}. Task 2 covered the consumer side directly by forcing
     * the event flag in {@code Sonic3kCNZEvents}; this test locks the producer
     * path by requiring the scroll-control helper to drive that same transition
     * through the bridge instead of through direct test-only hooks.
     */
    @Test
    void scrollControlBridgeSignalAdvancesCnzEventState() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());
        Sonic3kCNZEvents events = getCnzEvents();
        events.forceBackgroundRoutine(Sonic3kCNZEvents.BG_AFTER_BOSS);
        events.forceBossBackgroundMode(Sonic3kCNZEvents.BossBackgroundMode.ACT1_POST_BOSS);

        CnzMinibossScrollControlInstance control = new CnzMinibossScrollControlInstance(
                new ObjectSpawn(0x3200, 0x0280, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        control.setServices(services);
        control.forceBossDefeatSignalForTest();
        control.forceAccumulatedOffsetForTest(0x01C0_0000);

        control.update(0, fixture.sprite());
        events.update(0, 1);

        assertEquals(Sonic3kCNZEvents.BG_FG_REFRESH, events.getBackgroundRoutine());
        assertEquals(0x01C0, events.getBossScrollOffsetY(),
                "The scroll-control object should publish the same threshold-crossing offset that gates the handoff");
    }

    /**
     * Task 7 owns the Act 1 boss slot only. The registry should stop returning
     * placeholders for {@code Obj_CNZMiniboss}, but it must keep
     * {@code Obj_CNZEndBoss} placeholder-backed so Task 8 remains bounded.
     */
    @Test
    void registryPromotesCnzMinibossButLeavesEndBossForTask8() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance miniboss = registry.create(
                new ObjectSpawn(0x3240, 0x02B8, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        ObjectInstance endBoss = registry.create(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));

        assertInstanceOf(CnzMinibossInstance.class, miniboss);
        assertInstanceOf(PlaceholderObjectInstance.class, endBoss);
        assertNotEquals(PlaceholderObjectInstance.class, miniboss.getClass(),
                "Task 7 should replace the miniboss placeholder-backed slot with the real Act 1 boss object");
    }

    private Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return manager.getCnzEvents();
    }
}
