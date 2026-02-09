package uk.co.jamesj999.sonic.tests;

import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1ObjectPlacement;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies Sonic 1 object placement parsing against known-good positions
 * from the disassembly (docs/s1disasm/objpos/*.bin).
 */
@RequiresRom(SonicGame.SONIC_1)
public class Sonic1ObjectPlacementTest {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    @Test
    public void parsesGhz1ObjectCount() throws Exception {
        List<ObjectSpawn> spawns = loadGhz1();
        // GHZ1 has 214 object records (verified from disassembly binary)
        assertEquals("GHZ1 object count", 214, spawns.size());
    }

    @Test
    public void parsesFirstGhz1Object() throws Exception {
        List<ObjectSpawn> spawns = loadGhz1();
        assertFalse(spawns.isEmpty());
        // First object in GHZ1 sorted by X (verified from objpos/ghz1.bin)
        ObjectSpawn first = spawns.get(0);
        assertTrue("First object X should be small", first.x() < 0x0200);
    }

    /**
     * Verifies all three Crabmeat spawn positions in GHZ Act 1 match the
     * disassembly data (docs/s1disasm/objpos/ghz1.bin).
     * <p>
     * Known-good positions from the binary:
     * <ul>
     *   <li>Record 20: X=0x08B0, Y=0x0350</li>
     *   <li>Record 21: X=0x0960, Y=0x02FA</li>
     *   <li>Record 189: X=0x2180, Y=0x048C</li>
     * </ul>
     */
    @Test
    public void crabmeatPositionsMatchDisassembly() throws Exception {
        List<ObjectSpawn> spawns = loadGhz1();
        List<ObjectSpawn> crabmeats = spawns.stream()
                .filter(s -> s.objectId() == Sonic1ObjectIds.CRABMEAT)
                .toList();

        assertEquals("GHZ1 should have 3 Crabmeats", 3, crabmeats.size());

        // Crabmeat #1: on lower platform area
        assertSpawnPosition("Crabmeat #1", crabmeats.get(0),
                0x08B0, 0x0350, Sonic1ObjectIds.CRABMEAT, 0x00, true, 0);

        // Crabmeat #2: on elevated platform
        assertSpawnPosition("Crabmeat #2", crabmeats.get(1),
                0x0960, 0x02FA, Sonic1ObjectIds.CRABMEAT, 0x00, true, 0);

        // Crabmeat #3: later in the level
        assertSpawnPosition("Crabmeat #3", crabmeats.get(2),
                0x2180, 0x048C, Sonic1ObjectIds.CRABMEAT, 0x00, true, 0);
    }

    @Test
    public void crabmeatRenderFlagsAreZeroInGhz1() throws Exception {
        List<ObjectSpawn> spawns = loadGhz1();
        List<ObjectSpawn> crabmeats = spawns.stream()
                .filter(s -> s.objectId() == Sonic1ObjectIds.CRABMEAT)
                .toList();

        for (int i = 0; i < crabmeats.size(); i++) {
            assertEquals("Crabmeat " + i + " renderFlags should be 0",
                    0, crabmeats.get(i).renderFlags());
        }
    }

    @Test
    public void objectsAreSortedByX() throws Exception {
        List<ObjectSpawn> spawns = loadGhz1();
        for (int i = 1; i < spawns.size(); i++) {
            assertTrue("Objects must be sorted by X: index " + i,
                    spawns.get(i).x() >= spawns.get(i - 1).x());
        }
    }

    @Test
    public void parsesGhz2Crabmeats() throws Exception {
        List<ObjectSpawn> spawns = loadZone(Sonic1Constants.ZONE_GHZ, 1);
        List<ObjectSpawn> crabmeats = spawns.stream()
                .filter(s -> s.objectId() == Sonic1ObjectIds.CRABMEAT)
                .toList();

        assertEquals("GHZ2 should have 4 Crabmeats", 4, crabmeats.size());
        // All Crabmeats should have valid positions (Y < 0x1000, X > 0)
        for (ObjectSpawn crab : crabmeats) {
            assertTrue("Crabmeat X > 0", crab.x() > 0);
            assertTrue("Crabmeat Y < 0x1000", crab.y() < 0x1000);
        }
    }

    @Test
    public void pointerTableFirstEntryIsValid() throws Exception {
        RomByteReader reader = RomByteReader.fromRom(romRule.rom());
        int firstOffset = reader.readU16BE(Sonic1Constants.OBJ_POS_INDEX_ADDR);
        // First entry should be a reasonable offset (not zero, not huge)
        assertTrue("First pointer offset should be > 0", firstOffset > 0);
        assertTrue("First pointer offset should be < 0x2000", firstOffset < 0x2000);
    }

    private List<ObjectSpawn> loadGhz1() throws Exception {
        return loadZone(Sonic1Constants.ZONE_GHZ, 0);
    }

    private List<ObjectSpawn> loadZone(int zone, int act) throws Exception {
        RomByteReader reader = RomByteReader.fromRom(romRule.rom());
        Sonic1ObjectPlacement placement = new Sonic1ObjectPlacement(reader);
        return placement.load(zone, act);
    }

    private static void assertSpawnPosition(
            String label,
            ObjectSpawn spawn,
            int expectedX,
            int expectedY,
            int expectedObjectId,
            int expectedSubtype,
            boolean expectedRespawn,
            int expectedRenderFlags) {
        assertEquals(label + " X", expectedX, spawn.x());
        assertEquals(label + " Y", expectedY, spawn.y());
        assertEquals(label + " objectId", expectedObjectId, spawn.objectId());
        assertEquals(label + " subtype", expectedSubtype, spawn.subtype());
        assertEquals(label + " respawnTracked", expectedRespawn, spawn.respawnTracked());
        assertEquals(label + " renderFlags", expectedRenderFlags, spawn.renderFlags());
    }
}
