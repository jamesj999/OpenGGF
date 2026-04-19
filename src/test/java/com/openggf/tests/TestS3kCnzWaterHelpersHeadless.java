package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzWaterLevelButtonInstance;
import com.openggf.game.sonic3k.objects.CnzWaterLevelCorkFloorInstance;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Headless coverage for the CNZ water-helper objects added in Task 7.
 *
 * <p>CNZ Act 2 is the only Sonic/Tails route with live water state, so these
 * tests deliberately load Act 2 and assert against the real
 * {@link com.openggf.level.WaterSystem} target values instead of only checking
 * event-local mirrors.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzWaterHelpersHeadless {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    /**
     * ROM anchors:
     * {@code Obj_CNZWaterLevelCorkFloor} raises {@code Target_water_level} to
     * {@code $0958} once the linked cork floor is gone, while
     * {@code Obj_CNZWaterLevelButton} checks the armed flag and then raises the
     * same target to {@code $0A58}. Task 7 keeps those writes explicit through
     * the CNZ bridge so later teleporter/boss work can observe the same state.
     */
    @Test
    void waterHelpersRaiseTargetWaterToRomHeights() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzWaterLevelCorkFloorInstance corkFloor = new CnzWaterLevelCorkFloorInstance(
                new ObjectSpawn(0x4880, 0x0A20, Sonic3kObjectIds.CNZ_WATER_LEVEL_CORK_FLOOR, 0, 0, false, 0));
        corkFloor.setServices(services);
        corkFloor.forceFloorReleasedForTest();
        corkFloor.update(0, fixture.sprite());

        assertEquals(0x0958,
                GameServices.water().getWaterLevelTarget(Sonic3kZoneIds.ZONE_CNZ, 1));

        CnzWaterLevelButtonInstance button = new CnzWaterLevelButtonInstance(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_WATER_LEVEL_BUTTON, 0, 0, false, 0));
        button.setServices(services);
        button.forcePressedForTest();
        button.update(1, fixture.sprite());

        assertEquals(0x0A58,
                GameServices.water().getWaterLevelTarget(Sonic3kZoneIds.ZONE_CNZ, 1));
    }

    /**
     * Task 7 replaces Task 6's placeholder-backed water-helper factories with
     * real CNZ helper objects. Keeping this assertion in the same targeted test
     * command means the registry contract goes red before production changes are
     * written.
     */
    @Test
    void registryCreatesConcreteCnzWaterHelpers() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance cork = registry.create(
                new ObjectSpawn(0x4880, 0x0A20, Sonic3kObjectIds.CNZ_WATER_LEVEL_CORK_FLOOR, 0, 0, false, 0));
        ObjectInstance button = registry.create(
                new ObjectSpawn(0x4A40, 0x0A38, Sonic3kObjectIds.CNZ_WATER_LEVEL_BUTTON, 0, 0, false, 0));

        assertInstanceOf(CnzWaterLevelCorkFloorInstance.class, cork);
        assertInstanceOf(CnzWaterLevelButtonInstance.class, button);
    }
}
