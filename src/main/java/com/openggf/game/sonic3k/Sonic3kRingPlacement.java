package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.rings.RingSpawn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parses Sonic 3&amp;K ring placement data into {@link RingSpawn} records.
 *
 * <p>S3K uses the same 4-byte ring record format as Sonic 2:
 * <pre>
 *   Bytes 0-1: X position (16-bit, big-endian)
 *   Bytes 2-3: Y word: CCCC YYYY YYYY YYYY
 *              C = count nibble (bits 12-15): &lt;8 = horizontal, &gt;=8 = vertical
 *              Y = 12-bit Y position (bits 0-11)
 *   Terminator: 0xFFFF
 * </pre>
 *
 * <p>The pointer table at {@link Sonic3kConstants#RING_LOC_PTRS_ADDR} uses
 * 32-bit absolute addresses, indexed as {@code zone * 2 + act}.
 */
public class Sonic3kRingPlacement {

    private static final int RECORD_SIZE = 4;
    private static final int TERMINATOR = 0xFFFF;
    private static final int RING_SPACING = 0x18;

    private final RomByteReader rom;

    public Sonic3kRingPlacement(RomByteReader rom) {
        this.rom = rom;
    }

    /**
     * Loads ring spawns for the given zone and act.
     *
     * @param zone Zone index (0-based, e.g. 0=AIZ, 1=HCZ, ...)
     * @param act  Act index (0-based)
     * @return Sorted, immutable list of ring spawns
     */
    public List<RingSpawn> load(int zone, int act) {
        int ptrIndex = zone * 2 + act;
        int listAddr = rom.readU32BE(Sonic3kConstants.RING_LOC_PTRS_ADDR + ptrIndex * 4);

        if (listAddr == 0 || listAddr >= rom.size()) {
            return List.of();
        }

        List<RingSpawn> spawns = new ArrayList<>();
        int cursor = listAddr;

        while (cursor + RECORD_SIZE <= rom.size()) {
            int x = rom.readU16BE(cursor);
            if (x == TERMINATOR) {
                break;
            }

            int yWord = rom.readU16BE(cursor + 2);
            int y = yWord & 0x0FFF;
            int countNibble = (yWord >> 12) & 0xF;
            boolean vertical = countNibble >= 0x8;
            int extra = vertical ? (countNibble - 0x8) : countNibble;
            int total = extra + 1;

            for (int i = 0; i < total; i++) {
                int ringX = x + (vertical ? 0 : i * RING_SPACING);
                int ringY = y + (vertical ? i * RING_SPACING : 0);
                spawns.add(new RingSpawn(ringX, ringY));
            }
            cursor += RECORD_SIZE;
        }

        spawns.sort(Comparator.comparingInt(RingSpawn::x));
        return List.copyOf(spawns);
    }
}
