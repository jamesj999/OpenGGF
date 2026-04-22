package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzMinibossSwingPhase {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void initSetsDescentVelocityAndWait() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x0100, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        boss.update(0, fixture.sprite());

        // Routine 0 -> 2 (advance to Lower after Init). Init runs once, sets y_vel and wait.
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_INIT_Y_VEL,
                boss.getCurrentYVel(), "Init must write y_vel = 0x80");
        assertTrue(boss.getCurrentRoutine() >= 2,
                "Init must advance state.routine past 0");
    }

    @Test
    void lowerAdvancesPositionUntilWaitExpires() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x0100, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        int initialY = boss.getCentreY();
        for (int i = 0; i < Sonic3kConstants.CNZ_MINIBOSS_INIT_WAIT + 5; i++) {
            boss.update(i, fixture.sprite());
        }
        assertTrue(boss.getCentreY() > initialY,
                "Lower must move the boss downward (y_vel 0x80 positive)");
    }

    @Test
    void moveAssignsSwingVelocityAfterGo3() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        DefaultObjectServices services = new DefaultObjectServices(RuntimeManager.getCurrent());

        CnzMinibossInstance boss = new CnzMinibossInstance(
                new ObjectSpawn(0x3240, 0x0100, Sonic3kObjectIds.CNZ_MINIBOSS, 0, 0, false, 0));
        boss.setServices(services);

        // Fast-forward through Init+Lower+Go2 so we reach routine 4 (Move)
        int totalWait = Sonic3kConstants.CNZ_MINIBOSS_INIT_WAIT
                + Sonic3kConstants.CNZ_MINIBOSS_GO2_WAIT + 10;
        for (int i = 0; i < totalWait; i++) {
            boss.update(i, fixture.sprite());
        }
        assertEquals(4, boss.getCurrentRoutine() & 0xFF,
                "Boss must be in routine 4 (Move) after Init+Lower+Go2 waits");
        assertEquals(Sonic3kConstants.CNZ_MINIBOSS_SWING_X_VEL,
                Math.abs(boss.getCurrentXVel()),
                "Go3 sets x_vel magnitude = 0x100 (sign depends on swing direction)");
    }
}
