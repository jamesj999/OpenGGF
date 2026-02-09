package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parses Sonic 3&amp;K object placement data into {@link ObjectSpawn} records.
 *
 * <p>S3K uses a 6-byte record format identical to Sonic 2:
 * <pre>
 *   Bytes 0-1: X position (16-bit, big-endian)
 *   Bytes 2-3: Y word: R0FF YYYY YYYY YYYY
 *              R = respawn tracked (bit 15)
 *              FF = render flags (bits 13-14)
 *              Y = 12-bit Y position (bits 0-11)
 *   Byte 4:   Object ID
 *   Byte 5:   Subtype
 * </pre>
 *
 * <p>The pointer table at {@link Sonic3kConstants#SPRITE_LOC_PTRS_ADDR} uses
 * 32-bit absolute addresses, indexed as {@code zone * 2 + act}.
 */
public class Sonic3kObjectPlacement {

    private static final int RECORD_SIZE = 6;
    private static final int TERMINATOR = 0xFFFF;

    private final RomByteReader rom;

    public Sonic3kObjectPlacement(RomByteReader rom) {
        this.rom = rom;
    }

    /**
     * Loads object spawns for the given zone and act.
     *
     * @param zone Zone index (0-based, e.g. 0=AIZ, 1=HCZ, ...)
     * @param act  Act index (0-based)
     * @return Sorted, immutable list of object spawns
     */
    public List<ObjectSpawn> load(int zone, int act) {
        int ptrIndex = zone * 2 + act;
        int listAddr = rom.readU32BE(Sonic3kConstants.SPRITE_LOC_PTRS_ADDR + ptrIndex * 4);

        if (listAddr == 0 || listAddr >= rom.size()) {
            return List.of();
        }

        List<ObjectSpawn> spawns = new ArrayList<>();
        int cursor = listAddr;

        while (cursor + RECORD_SIZE <= rom.size()) {
            int x = rom.readU16BE(cursor);
            if (x == TERMINATOR) {
                break;
            }

            int yWord = rom.readU16BE(cursor + 2);
            int y = yWord & 0x0FFF;
            int renderFlags = (yWord >> 13) & 0x3;
            boolean respawnTracked = (yWord & 0x8000) != 0;
            int objectId = rom.readU8(cursor + 4);
            int subtype = rom.readU8(cursor + 5);

            spawns.add(new ObjectSpawn(x, y, objectId, subtype, renderFlags, respawnTracked, yWord));
            cursor += RECORD_SIZE;
        }

        spawns.sort(Comparator.comparingInt(ObjectSpawn::x));
        return List.copyOf(spawns);
    }
}
