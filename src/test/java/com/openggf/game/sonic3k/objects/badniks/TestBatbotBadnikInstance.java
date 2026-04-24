package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestBatbotBadnikInstance {

    @Test
    void registryCreatesBatbotForSkSetOneSlotA5() {
        Sonic3kObjectRegistry registry = new Sonic3kObjectRegistry();

        ObjectInstance instance = registry.create(new ObjectSpawn(0x1AF0, 0x0638,
                Sonic3kObjectIds.BATBOT, 0, 0, false, 0));

        assertTrue(instance instanceof BatbotBadnikInstance);
    }

    @Test
    void waitsUntilPlayerIsWithinFortyPixelsBeforeMoving() {
        putBatbotOnScreen();
        BatbotBadnikInstance batbot = new BatbotBadnikInstance(new ObjectSpawn(0x1AF0,
                0x0638, Sonic3kObjectIds.BATBOT, 0, 0, false, 0));
        batbot.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x1A00);
        player.setCentreY((short) 0x0638);
        batbot.update(0, player);
        batbot.update(1, player);

        assertEquals(0x1AF0, batbot.getX());
        assertEquals(0x0638, batbot.getY());

        player.setCentreX((short) 0x1B10);
        batbot.update(2, player);

        assertEquals(0x1AF0, batbot.getX(),
                "ROM activation sets x_vel but does not call MoveSprite2 until the chase routine");
        batbot.update(3, player);

        assertEquals(0x1AF2, batbot.getX());
        assertEquals(0x0638, batbot.getY());
    }

    @Test
    void activationUsesNearestPlayersHorizontalDistance() {
        putBatbotOnScreen();
        BatbotBadnikInstance batbot = new BatbotBadnikInstance(new ObjectSpawn(0x1AF0,
                0x0638, Sonic3kObjectIds.BATBOT, 0, 0, false, 0));
        batbot.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x1B10);
        player.setCentreY((short) 0x0738);
        batbot.update(0, player);
        batbot.update(1, player);

        assertEquals(0x1AF0, batbot.getX(),
                "Obj_Batbot compares only d2 from Find_SonicTails, the nearest player's absolute X distance");
        batbot.update(2, player);

        assertEquals(0x1AF2, batbot.getX());
    }

    @Test
    void chaseObjectAccelerationClampsAtRomMaximum() {
        putBatbotOnScreen();
        BatbotBadnikInstance batbot = new BatbotBadnikInstance(new ObjectSpawn(0x1AF0,
                0x0638, Sonic3kObjectIds.BATBOT, 0, 0, false, 0));
        batbot.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) 0x1B10);
        player.setCentreY((short) 0x0648);

        batbot.update(0, player);
        batbot.update(1, player);
        batbot.update(2, player);

        assertEquals(0x1AF2, batbot.getX(),
                "x_vel starts at $200 and Chase_Object refuses to store $208");
        assertEquals(0x0638, batbot.getY(),
                "y_vel is only $08 on the first MoveSprite2 frame");
        batbot.update(3, player);

        assertEquals(0x1AF4, batbot.getX());
        assertEquals(0x0638, batbot.getY());
    }

    @Test
    void objWaitOffscreenSuppressesLogicAndCollisionUntilVisible() {
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
        BatbotBadnikInstance batbot = new BatbotBadnikInstance(new ObjectSpawn(0x1AF0,
                0x0638, Sonic3kObjectIds.BATBOT, 0, 0, false, 0));
        batbot.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) 0x1B10);
        player.setCentreY((short) 0x0638);

        batbot.update(0, player);

        assertEquals(0x1AF0, batbot.getX());
        assertEquals(0, batbot.getCollisionFlags());

        putBatbotOnScreen();
        batbot.update(1, player);

        assertEquals(0x0D, batbot.getCollisionFlags());
        assertEquals(0x1AF0, batbot.getX(),
                "First visible frame initializes and arms x_vel; movement starts in the chase routine");
    }

    @Test
    void objWaitOffscreenRequiresVerticalVisibilityBeforeLogicStarts() {
        AbstractObjectInstance.updateCameraBounds(0x1000, 0x0100, 0x1140, 0x01E0, 0);
        BatbotBadnikInstance batbot = new BatbotBadnikInstance(new ObjectSpawn(0x1078,
                0x0428, Sonic3kObjectIds.BATBOT, 0, 0, false, 0));
        batbot.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) 0x103A);
        player.setCentreY((short) 0x0322);

        batbot.update(0, player);

        assertEquals(0, batbot.getCollisionFlags(),
                "Obj_WaitOffscreen waits for Draw_Sprite to set render_flags bit 7, not just X range");
        assertEquals(0x1078, batbot.getX());
        assertEquals(0x0428, batbot.getY());
    }

    private static void putBatbotOnScreen() {
        AbstractObjectInstance.updateCameraBounds(0x1A00, 0, 0x1B40, 0x1000, 0);
    }
}
