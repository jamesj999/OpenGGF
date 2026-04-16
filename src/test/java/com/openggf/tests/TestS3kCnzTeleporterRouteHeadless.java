package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.game.sonic3k.objects.CnzEggCapsuleInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterBeamInstance;
import com.openggf.game.sonic3k.objects.CnzTeleporterInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless Task 8 coverage for CNZ's Knuckles teleporter route and bounded
 * end-boss defeat handoff.
 *
 * <p>These tests deliberately stop at the Task 8 scope the plan and ROM notes
 * agreed on:
 * <ul>
 *   <li>{@code Obj_CNZTeleporter} must clamp and lock the player, queue the
 *   teleporter handoff, and spawn the shared {@code Obj_TeleporterBeam}</li>
 *   <li>The teleporter route must <strong>not</strong> spawn the egg capsule</li>
 *   <li>{@code Obj_CNZEndBoss} only owns startup/defeat handoff for now:
 *   clear the boss flag, widen camera bounds, spawn the CNZ capsule wrapper,
 *   and restore player control</li>
 * </ul>
 *
 * <p>Full CNZ end-boss attack-state parity is intentionally deferred.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzTeleporterRouteHeadless {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    /**
     * ROM anchors:
     * {@code Obj_CNZTeleporter} arms when Knuckles reaches the late Act 2 route,
     * clamps airborne overshoot back to {@code x=$4A40}, zeroes the movement
     * speeds, locks control, queues {@code ArtKosM_CNZTeleport}, then hands off
     * to {@code Obj_CNZTeleporterMain}. That main phase waits for the art load
     * and for Knuckles to be grounded before spawning the shared
     * {@code Obj_TeleporterBeam}.
     *
     * <p>Task 8 must preserve one critical boundary from the verified ROM notes:
     * the teleporter route does <strong>not</strong> spawn the egg capsule. That
     * handoff belongs only to the later {@code Obj_CNZEndBoss} defeat path.
     */
    @Test
    void knucklesTeleporterRouteLocksControlClampsCameraAndSpawnsBeamWithoutCapsule() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        Sonic3kCNZEvents events = getCnzEvents();
        events.beginKnucklesTeleporterRoute();

        CnzTeleporterInstance teleporter = new CnzTeleporterInstance(
                new ObjectSpawn(0x4A40, 0x0A38, 0, 0, 0, false, 0));
        teleporter.setServices(new DefaultObjectServices(RuntimeManager.getCurrent()));
        GameServices.level().getObjectManager().addDynamicObject(teleporter);

        fixture.sprite().setCentreX((short) 0x4A50);
        fixture.sprite().setCentreY((short) 0x0A30);
        fixture.sprite().setAir(true);
        fixture.sprite().setJumping(true);
        fixture.sprite().setXSpeed((short) 0x180);
        fixture.sprite().setGSpeed((short) 0x200);

        fixture.stepIdleFrames(1);
        assertTrue(fixture.sprite().isControlLocked(),
                "Obj_CNZTeleporter should mirror Ctrl_1_locked by immediately removing player control");
        assertEquals(0x4A40, fixture.sprite().getCentreX(),
                "Airborne overshoot past $4A40 should clamp back to the teleporter beam X");
        assertEquals(0, fixture.sprite().getXSpeed(),
                "The teleporter arming frame should clear x_vel before the beam sequence begins");
        assertEquals(0, fixture.sprite().getGSpeed(),
                "The teleporter arming frame should also clear ground_vel");
        assertEquals(0x4750, fixture.camera().getMinX(),
                "The Knuckles-only route should publish the late Act 2 left camera clamp");
        assertEquals(0x48E0, fixture.camera().getMaxX(),
                "The Knuckles-only route should publish the late Act 2 right camera clamp");

        fixture.sprite().setAir(false);
        fixture.sprite().setJumping(false);
        fixture.stepIdleFrames(1);

        assertTrue(isObjectPresent(CnzTeleporterBeamInstance.class),
                "Obj_CNZTeleporterMain should spawn the shared beam once the player is grounded");
        assertTrue(events.isTeleporterBeamSpawned(),
                "The CNZ event bridge should record the explicit beam-handoff seam");
        assertFalse(isObjectPresent(CnzEggCapsuleInstance.class),
                "The teleporter route must not spawn the capsule; that belongs to Obj_CNZEndBoss");
    }

    /**
     * ROM anchors:
     * Task 8 only claims the defeat handoff from {@code Obj_CNZEndBoss}. When
     * the boss sequence is finished, it must clear {@code Boss_flag}, widen the
     * boss camera clamp, spawn the egg capsule, and restore level music/control.
     *
     * <p>This test intentionally does not claim attack-state parity, hit timing,
     * or the full cutscene choreography.
     */
    @Test
    void cnzEndBossDefeatHandoffClearsBossFlagWidensCameraAndSpawnsCapsule() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();

        Sonic3kCNZEvents events = getCnzEvents();
        events.setBossFlag(true);
        GameServices.gameState().setCurrentBossId(Sonic3kObjectIds.CNZ_END_BOSS);

        fixture.camera().setMaxX((short) 0x48E0);
        fixture.sprite().setControlLocked(true);
        fixture.sprite().setObjectControlled(true);
        fixture.sprite().setHidden(true);

        CnzEndBossInstance boss = spawnCnzEndBossForTest();
        boss.forceDefeatForTest();

        fixture.stepIdleFrames(1);

        assertTrue(isObjectPresent(CnzEggCapsuleInstance.class),
                "The bounded Task 8 defeat handoff should spawn the CNZ-local egg capsule wrapper");
        assertFalse(fixture.sprite().isControlLocked(),
                "Defeat handoff should return player control once the capsule is released");
        assertFalse(fixture.sprite().isObjectControlled(),
                "Defeat handoff should clear object control instead of leaving the player frozen");
        assertFalse(fixture.sprite().isHidden(),
                "If the teleporter route hid the player earlier, the defeat handoff must reveal them again");
        assertFalse(events.isBossFlag(),
                "Task 8 owns clearing Boss_flag so later CNZ event logic can leave boss mode");
        assertEquals(0, GameServices.gameState().getCurrentBossId(),
                "Defeat handoff should clear Current_Boss_ID alongside Boss_flag");
        assertTrue(fixture.camera().getMaxX() > 0x48E0,
                "Task 8 should widen the CNZ boss camera clamp during the capsule handoff");
    }

    /**
     * Task 7 still left the CNZ end-boss slot placeholder-backed. Task 8 should
     * promote that explicit registry slot to the bounded CNZ end-boss wrapper so
     * the route and defeat handoff can be exercised without touching unrelated
     * zones that also reuse object ID {@code $A7}.
     */
    @Test
    void registryPromotesCnzEndBossSlotForTask8() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance endBoss = registry.create(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));

        assertInstanceOf(CnzEndBossInstance.class, endBoss);
    }

    private Sonic3kCNZEvents getCnzEvents() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return manager.getCnzEvents();
    }

    private CnzEndBossInstance spawnCnzEndBossForTest() {
        CnzEndBossInstance boss = new CnzEndBossInstance(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(new DefaultObjectServices(RuntimeManager.getCurrent()));
        GameServices.level().getObjectManager().addDynamicObject(boss);
        return boss;
    }

    private boolean isObjectPresent(Class<?> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(type::isInstance);
    }
}
