package com.openggf.game.sonic2;

import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonPlacementParser;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/**
 * Parses Sonic 2 Rev01 object placement data into {@link ObjectSpawn} records.
 */
public class Sonic2ObjectPlacement {
    // Off_Objects pointer table in s2.asm (Rev01) – label resolves to this ROM address
    // Verified against the disassembly and s2.txt split list (Off_Objects -> object BINCLUDE stream).
    public static final int OFF_OBJECTS_REV01 = 0x0E6800;
    private static final int ACTS_PER_ZONE = 2; // zoneOrderedOffsetTable 2,2 in s2.asm (SCZ/WFZ/DEZ are 1-act, MTZ is 3-act)

    private final RomByteReader rom;

    public Sonic2ObjectPlacement(RomByteReader rom) {
        this.rom = rom;
    }

    public List<ObjectSpawn> load(ZoneAct zoneAct) {
        int pointerIndex = zoneAct.pointerIndex(ACTS_PER_ZONE);
        if (isSingleActZone(zoneAct.zone())) {
            pointerIndex = zoneAct.zone() * ACTS_PER_ZONE;
        }
        int listAddr = rom.readPointer16(OFF_OBJECTS_REV01, pointerIndex);
        return CommonPlacementParser.parseObjectRecords(rom, listAddr);
    }

    public String toCsv(List<ObjectSpawn> spawns) {
        StringBuilder sb = new StringBuilder("x,y,id,subtype,renderFlags,respawn,rawYWord\n");
        for (ObjectSpawn spawn : spawns) {
            sb.append(String.format(
                    "0x%04X,0x%04X,0x%02X,0x%02X,0x%02X,%s,0x%04X%n",
                    spawn.x(), spawn.y(), spawn.objectId(), spawn.subtype(),
                    spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord()));
        }
        return sb.toString();
    }

    private static boolean isSingleActZone(int zone) {
        // ROM zone IDs: WFZ=$06(6), DEZ=$0E(14), SCZ=$10(16)
        return zone == 6 || zone == 14 || zone == 16;
    }
}
