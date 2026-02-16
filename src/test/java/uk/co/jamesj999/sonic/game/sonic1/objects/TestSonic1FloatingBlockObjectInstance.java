package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1SwitchManager;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSonic1FloatingBlockObjectInstance {

    private static final int ORIG_X = 0x600;
    private static final int ORIG_Y = 0x1A0;

    @Before
    public void resetSwitchState() {
        Sonic1SwitchManager.resetInstance();
    }

    @Test
    public void verticalDoorDoesNotStartOpenJustBecauseItIsRespawnTracked() {
        Sonic1FloatingBlockObjectInstance door = createLzDoor(0xE0, true);

        door.update(1, null);

        // LZ vertical door: halfHeight=0x20, initial fb_height=0x40, y=origY+0x40 when closed.
        assertEquals(ORIG_Y + 0x40, door.getY());
    }

    @Test
    public void verticalDoorType05ChecksOnlySwitchBit0() {
        Sonic1FloatingBlockObjectInstance door = createLzDoor(0xE0, true);
        Sonic1SwitchManager switches = Sonic1SwitchManager.getInstance();

        // Set bit 7 only: ROM type 05 uses btst #0, so this must not open the door.
        switches.setBit(0, 7);
        door.update(1, null);
        assertEquals(ORIG_Y + 0x40, door.getY());

        // Set bit 0: door starts opening by 2 px this frame.
        switches.setBit(0, 0);
        door.update(2, null);
        assertEquals(ORIG_Y + 0x3E, door.getY());
    }

    @Test
    public void horizontalDoorType0CChecksOnlySwitchBit0() {
        Sonic1FloatingBlockObjectInstance door = createLzDoor(0xF0, true);
        Sonic1SwitchManager switches = Sonic1SwitchManager.getInstance();

        // LZ large horizontal door starts at x=origX+0x80 when closed.
        door.update(1, null);
        assertEquals(ORIG_X + 0x80, door.getX());

        // Bit 7 alone must not activate type 0C.
        switches.setBit(0, 7);
        door.update(2, null);
        assertEquals(ORIG_X + 0x80, door.getX());

        // Bit 0 activates type 0C; door moves left by 2.
        switches.setBit(0, 0);
        door.update(3, null);
        assertEquals(ORIG_X + 0x7E, door.getX());
        assertTrue(door.getX() < ORIG_X + 0x80);
    }

    private static Sonic1FloatingBlockObjectInstance createLzDoor(int subtype, boolean respawnTracked) {
        ObjectSpawn spawn = new ObjectSpawn(
                ORIG_X,
                ORIG_Y,
                Sonic1ObjectIds.FLOATING_BLOCK,
                subtype,
                0,
                respawnTracked,
                0
        );
        return new Sonic1FloatingBlockObjectInstance(spawn, Sonic1Constants.ZONE_LZ);
    }
}
