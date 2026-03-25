package com.openggf.game.sonic2;

import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.level.rings.RingSpawn;

import java.util.List;

/**
 * Parses Sonic 2 Rev01 ring placement data into {@link RingSpawn} records.
 */
public class Sonic2RingPlacement {
    // Offset index of ring location lists in Rev01.
    public static final int OFF_RINGS_REV01 = 0x0E4300;
    private static final int ACTS_PER_ZONE = 2;
    private static final int TERMINATOR = 0xFFFF;

    private final RomByteReader rom;

    public Sonic2RingPlacement(RomByteReader rom) {
        this.rom = rom;
    }

    public List<RingSpawn> load(ZoneAct zoneAct) {
        int pointerIndex = zoneAct.pointerIndex(ACTS_PER_ZONE);
        int listAddr = rom.readPointer16(OFF_RINGS_REV01, pointerIndex);
        if (zoneAct.act() > 0 && isListEmpty(listAddr)) {
            listAddr = rom.readPointer16(OFF_RINGS_REV01, zoneAct.zone() * ACTS_PER_ZONE);
        }

        return CommonPlacementParser.parseRingRecords(rom, listAddr);
    }

    private boolean isListEmpty(int listAddr) {
        return rom.readU16BE(listAddr) == TERMINATOR;
    }
}
