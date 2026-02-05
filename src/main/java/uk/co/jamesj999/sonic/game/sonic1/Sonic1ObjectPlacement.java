package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parses Sonic 1 object placement data into {@link ObjectSpawn} records.
 *
 * <p>Sonic 1 uses a 6-byte record format per object:
 * <pre>
 *   Bytes 0-1: X position (16-bit, big-endian)
 *   Bytes 2-3: Y word: AB00 YYYY YYYY YYYY
 *              A = vertical flip (bit 15)
 *              B = horizontal flip (bit 14)
 *              Y = 12-bit Y position (bits 0-11)
 *   Byte 4:   Object ID (bit 7 = respawn tracked, bits 0-6 = object type)
 *   Byte 5:   Subtype
 * </pre>
 *
 * <p>The object position index table (ObjPos_Index) has 2 word-offsets per act,
 * with 4 act slots per zone. Each word is a relative offset from the table base.
 */
public class Sonic1ObjectPlacement {

    private static final int RECORD_SIZE = 6;
    private static final int TERMINATOR = 0xFFFF;
    // 4 act slots per zone, 2 words (4 bytes) per act entry
    private static final int ACT_SLOTS_PER_ZONE = 4;
    private static final int BYTES_PER_ACT_ENTRY = 4;

    private final RomByteReader rom;

    public Sonic1ObjectPlacement(RomByteReader rom) {
        this.rom = rom;
    }

    /**
     * Loads object spawns for the given zone and act.
     *
     * @param zone Zone index (0-based)
     * @param act  Act index (0-based)
     * @return Sorted, immutable list of object spawns
     */
    public List<ObjectSpawn> load(int zone, int act) {
        int baseAddr = Sonic1Constants.OBJ_POS_INDEX_ADDR;
        int indexOffset = (zone * ACT_SLOTS_PER_ZONE + act) * BYTES_PER_ACT_ENTRY;
        int listOffset = rom.readU16BE(baseAddr + indexOffset);
        int listAddr = baseAddr + listOffset;

        List<ObjectSpawn> spawns = new ArrayList<>();
        int cursor = listAddr;

        while (true) {
            int x = rom.readU16BE(cursor);
            if (x == TERMINATOR) {
                break;
            }

            int yWord = rom.readU16BE(cursor + 2);
            int y = yWord & 0x0FFF;
            // Sonic 1: bits 15-14 of Y word = vflip, hflip
            int renderFlags = (yWord >> 14) & 0x3;

            int objIdByte = rom.readU8(cursor + 4);
            // Sonic 1: bit 7 of object ID byte = respawn tracked
            boolean respawnTracked = (objIdByte & 0x80) != 0;
            int objectId = objIdByte & 0x7F;

            int subtype = rom.readU8(cursor + 5);

            spawns.add(new ObjectSpawn(x, y, objectId, subtype, renderFlags, respawnTracked, yWord));
            cursor += RECORD_SIZE;
        }

        spawns.sort(Comparator.comparingInt(ObjectSpawn::x));
        return List.copyOf(spawns);
    }
}
