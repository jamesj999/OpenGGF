package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestDoorObjectInstance {

    @AfterEach
    void resetCameraBounds() {
        AbstractObjectInstance.resetCameraBoundsForTests();
    }

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

    @Test
    public void horizontalDoorReportsRomWidthPixelsAsOnScreenHalfWidth() {
        // ROM byte_30FCE (sonic3k.asm:66167) sets width_pixels = $20 for the
        // horizontal CNZ door. The engine's solid-contact gate must use that
        // value when testing camera overlap, otherwise the CNZ horizontal
        // door's right edge (0x1940) is rejected as off-screen the moment
        // the camera reaches 0x1928 even though Tails is still standing on
        // the door's top in the ROM (sonic3k.asm:36336-36370 Render_Sprites
        // computes bit 7 from x_pos +/- width_pixels).
        DoorObjectInstance horizontal = new DoorObjectInstance(
                new ObjectSpawn(0x1940, 0x0548, 0x3C, 0x80, 0, false, 0));
        assertEquals(0x20, horizontal.getOnScreenHalfWidth());
    }

    @Test
    public void horizontalDoorIsOnScreenAtCnzF6304BoundaryWhenCameraIsRomAccurate() {
        // CNZ trace replay frame 6304 boundary: camera at 0x17E8 (per
        // s3k_cnz1_context.txt F6303 ROM cam=(17E8,04EA)). The door is at
        // 0x1940. ROM Render_Sprites computes
        //   d3 = (door.x - cam.x) - width_pixels
        //   = 0x1940 - 0x17E8 - 0x20 = 0x138 (= 312)
        // and branches off-screen when d3 >= 320; 312 < 320 keeps the door
        // on-screen and bit 7 set, so SolidObjectFull on the next frame
        // catches Tails landing.
        DoorObjectInstance horizontal = new DoorObjectInstance(
                new ObjectSpawn(0x1940, 0x0548, 0x3C, 0x80, 0, false, 0));
        // Two updateCameraBounds calls so the previous-frame snapshot has
        // settled to the F6303 ROM camera (mirrors ROM bit 7 being set by
        // the prior Render_Sprites pass before SolidObjectFull executes).
        AbstractObjectInstance.updateCameraBounds(0x17E8, 0x04EA, 0x17E8 + 320, 0x04EA + 224, 0);
        AbstractObjectInstance.updateCameraBounds(0x17EE, 0x04E9, 0x17EE + 320, 0x04E9 + 224, 0);
        assertTrue(horizontal.isWithinSolidContactBounds(),
                "Door at (0x1940,0x0548) with previous-frame camera 0x17E8 must be on-screen "
                        + "(ROM Render_Sprites at sonic3k.asm:36336-36370 sets bit 7).");
    }

    @Test
    public void horizontalDoorIsOffScreenWhenCameraTrailsBeyondOnScreenWidth() {
        // Sanity check: the gate must reject the door when the camera is too
        // far to the left. Pick a camera position where door.x exceeds
        // cam.x + 320 + width_pixels = cam.x + 0x160. cam_x = 0x17C0 ->
        // 0x17C0 + 0x160 = 0x1920, door at 0x1940 is past that boundary.
        DoorObjectInstance horizontal = new DoorObjectInstance(
                new ObjectSpawn(0x1940, 0x0548, 0x3C, 0x80, 0, false, 0));
        // First call seeds both current and previous to the off-screen position;
        // second call rolls so previous = the off-screen position used by the gate.
        AbstractObjectInstance.updateCameraBounds(0x17C0, 0x04EA, 0x17C0 + 320, 0x04EA + 224, 0);
        AbstractObjectInstance.updateCameraBounds(0x17C0, 0x04EA, 0x17C0 + 320, 0x04EA + 224, 0);
        assertFalse(horizontal.isWithinSolidContactBounds(),
                "Door at (0x1940,0x0548) must be off-screen for camera 0x17C0 "
                        + "(door.x > cam.x + 320 + width_pixels = 0x1920).");
    }

    private static TestPlayableSprite createPlayerAtCentre(int centreX, int centreY) {
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) centreX);
        player.setCentreY((short) centreY);
        return player;
    }
}


