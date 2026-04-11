package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestDoorObjectInstance {

    @Test
    public void verticalCnzVariantUsesNarrowCollisionWidth() {
        DoorObjectInstance door = new DoorObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x3C, 0x01, 0, false, 0));

        SolidObjectParams params = door.getSolidParams();

        assertEquals(8 + 0x0B, params.halfWidth());
        assertEquals(0x20, params.airHalfHeight());
        assertEquals(0x21, params.groundHalfHeight());
    }

    @Test
    public void horizontalDoorUsesWideCollisionAndMovesOnXAxis() {
        DoorObjectInstance door = new DoorObjectInstance(
                new ObjectSpawn(0x200, 0x180, 0x3C, 0x80, 0, false, 0));
        TestPlayableSprite player = createPlayerAtCentre(0x200, 0x100);

        SolidObjectParams params = door.getSolidParams();
        door.update(0, player);

        assertEquals(0x20 + 0x0B, params.halfWidth());
        assertEquals(8, params.airHalfHeight());
        assertEquals(9, params.groundHalfHeight());
        assertEquals(0x208, door.getX());
        assertEquals(0x180, door.getY());
    }

    @Test
    public void horizontalDoorXFlipReversesSlideDirection() {
        DoorObjectInstance door = new DoorObjectInstance(
                new ObjectSpawn(0x200, 0x180, 0x3C, 0x80, 0x01, false, 0));
        TestPlayableSprite player = createPlayerAtCentre(0x200, 0x100);

        door.update(0, player);

        assertEquals(0x1F8, door.getX());
    }

    @Test
    public void horizontalDoorDoesNotTriggerFromWideSideWindow() {
        DoorObjectInstance door = new DoorObjectInstance(
                new ObjectSpawn(0x200, 0x180, 0x3C, 0x80, 0, false, 0));
        TestPlayableSprite player = createPlayerAtCentre(0x240, 0x100);

        door.update(0, player);

        assertEquals(0x200, door.getX());
    }

    @Test
    public void horizontalDoorYFlipTriggersFromBelow() {
        DoorObjectInstance door = new DoorObjectInstance(
                new ObjectSpawn(0x200, 0x180, 0x3C, 0x80, 0x02, false, 0));
        TestPlayableSprite player = createPlayerAtCentre(0x200, 0x190);

        door.update(0, player);

        assertEquals(0x208, door.getX());
    }

    private static TestPlayableSprite createPlayerAtCentre(int centreX, int centreY) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) centreX);
        player.setCentreY((short) centreY);
        return player;
    }
}


