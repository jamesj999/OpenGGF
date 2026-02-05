package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.rings.RingSpawn;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Game implementation for Sonic the Hedgehog 1 (Mega Drive/Genesis).
 *
 * <p>Loads levels from the original Sonic 1 ROM using per-zone level headers,
 * Nemesis/Enigma/Kosinski compressed art data, and plain binary layouts.
 *
 * <p>Level indices for Sonic 1 start at 0x80 (see {@link uk.co.jamesj999.sonic.level.LevelData}).
 * Zone = (levelIdx - 0x80) / 3, Act = (levelIdx - 0x80) % 3.
 */
public class Sonic1 extends Game {
    private static final Logger LOG = Logger.getLogger(Sonic1.class.getName());

    private static final int S1_LEVEL_INDEX_BASE = 0x80;
    private static final int ACTS_PER_ZONE = 3;
    // Number of act slots in ROM tables (Level_Index, LevelSizeArray, ObjPos_Index) per zone
    private static final int ACT_SLOTS_PER_ZONE = 4;

    private final Rom rom;
    private RomByteReader romReader;
    private Sonic1ObjectPlacement objectPlacement;

    public Sonic1(Rom rom) {
        this.rom = rom;
    }

    @Override
    public Rom getRom() {
        return rom;
    }

    @Override
    public boolean isCompatible() {
        try {
            String name = rom.readDomesticName();
            return name != null && name.contains("SONIC THE")
                    && !name.contains("HEDGEHOG 2") && !name.contains("HEDGEHOG 3");
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getIdentifier() {
        return "Sonic1";
    }

    @Override
    public List<String> getTitleCards() {
        return List.of(
                "Green Hill Zone - Act 1", "Green Hill Zone - Act 2", "Green Hill Zone - Act 3",
                "Labyrinth Zone - Act 1", "Labyrinth Zone - Act 2", "Labyrinth Zone - Act 3",
                "Marble Zone - Act 1", "Marble Zone - Act 2", "Marble Zone - Act 3",
                "Star Light Zone - Act 1", "Star Light Zone - Act 2", "Star Light Zone - Act 3",
                "Spring Yard Zone - Act 1", "Spring Yard Zone - Act 2", "Spring Yard Zone - Act 3",
                "Scrap Brain Zone - Act 1", "Scrap Brain Zone - Act 2", "Scrap Brain Zone - Act 3",
                "Final Zone");
    }

    @Override
    public Level loadLevel(int levelIdx) throws IOException {
        int zone, act;
        if (levelIdx >= S1_LEVEL_INDEX_BASE) {
            int s1Idx = levelIdx - S1_LEVEL_INDEX_BASE;
            zone = s1Idx / ACTS_PER_ZONE;
            act = s1Idx % ACTS_PER_ZONE;
        } else {
            zone = 0;
            act = 0;
        }

        ensureHelpers();

        // Read LevelHeaders (16 bytes per zone)
        int headerAddr = Sonic1Constants.LEVEL_HEADERS_ADDR + zone * 16;

        // Bytes 0-3: Pattern art address (mask off PLC byte in top bits)
        int patternAddr = rom.read32BitAddr(headerAddr) & 0xFFFFFF;

        // Bytes 4-7: 16x16 chunk mappings address (Enigma compressed)
        int chunksAddr = rom.read32BitAddr(headerAddr + 4) & 0xFFFFFF;

        // Bytes 8-11: 256x256 block mappings address (Kosinski compressed)
        int blocksAddr = rom.read32BitAddr(headerAddr + 8);

        // Byte 15: palette index
        int paletteId = rom.readByte(headerAddr + 15) & 0xFF;

        // Get collision index for this zone
        int collisionIndexAddr = getCollisionIndexAddr(zone);

        // Get layout addresses from Level_Index
        int[] layoutAddrs = getLayoutAddresses(zone, act);
        int fgLayoutAddr = layoutAddrs[0];
        int bgLayoutAddr = layoutAddrs[1];

        // Get boundaries
        int[] boundaries = loadBoundaries(zone, act);

        // Get palette addresses
        int sonicPaletteAddr = Sonic1Constants.SONIC_PALETTE_ADDR;
        int levelPaletteAddr = getLevelPaletteAddr(paletteId);

        // Load object spawns
        List<ObjectSpawn> objects = objectPlacement.load(zone, act);

        // Sonic 1 doesn't have separate ring placement - rings are objects
        List<RingSpawn> rings = List.of();

        LOG.info("Loading Sonic 1 level: zone=" + zone + " act=" + act +
                " patterns=0x" + Integer.toHexString(patternAddr) +
                " chunks=0x" + Integer.toHexString(chunksAddr) +
                " blocks=0x" + Integer.toHexString(blocksAddr));

        return new Sonic1Level(rom, zone, sonicPaletteAddr, levelPaletteAddr,
                patternAddr, chunksAddr, blocksAddr,
                fgLayoutAddr, bgLayoutAddr, collisionIndexAddr,
                Sonic1Constants.COLLISION_ARRAY_NORMAL_ADDR,
                Sonic1Constants.COLLISION_ARRAY_ROTATED_ADDR,
                Sonic1Constants.ANGLE_MAP_ADDR,
                objects, rings, boundaries);
    }

    @Override
    public int getMusicId(int levelIdx) throws IOException {
        // Stub - music IDs not yet mapped for Sonic 1
        return 0;
    }

    @Override
    public Map<GameSound, Integer> getSoundMap() {
        // Stub - sound map not yet mapped for Sonic 1
        return new HashMap<>();
    }

    @Override
    public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
        // Stub - BG scroll not yet implemented for Sonic 1
        return new int[]{0, 0};
    }

    @Override
    public boolean canRelocateLevels() {
        return false;
    }

    @Override
    public boolean canSave() {
        return false;
    }

    @Override
    public boolean relocateLevels(boolean unsafe) throws IOException {
        return false;
    }

    @Override
    public boolean save(int levelIdx, Level level) {
        return false;
    }

    // ===== Private helpers =====

    private void ensureHelpers() throws IOException {
        if (romReader == null) {
            romReader = RomByteReader.fromRom(rom);
        }
        if (objectPlacement == null) {
            objectPlacement = new Sonic1ObjectPlacement(romReader);
        }
    }

    /**
     * Gets layout addresses (FG and BG) from the Level_Index table.
     *
     * <p>The Level_Index table has word-offsets relative to its own base.
     * Each zone has 4 act slots, each act slot has 3 word entries (6 bytes):
     * FG offset, BG offset, and a third (unused).
     */
    private int[] getLayoutAddresses(int zone, int act) throws IOException {
        int baseAddr = Sonic1Constants.LEVEL_INDEX_ADDR;
        int entryOffset = (zone * ACT_SLOTS_PER_ZONE + act) * 6;

        int fgOffset = rom.read16BitAddr(baseAddr + entryOffset);
        int bgOffset = rom.read16BitAddr(baseAddr + entryOffset + 2);

        return new int[]{baseAddr + fgOffset, baseAddr + bgOffset};
    }

    /**
     * Gets the collision index address for a zone.
     * Sonic 1 uses per-zone uncompressed collision index tables.
     */
    private int getCollisionIndexAddr(int zone) {
        return switch (zone) {
            case Sonic1Constants.ZONE_GHZ -> Sonic1Constants.COL_GHZ_ADDR;
            case Sonic1Constants.ZONE_LZ -> Sonic1Constants.COL_LZ_ADDR;
            case Sonic1Constants.ZONE_MZ -> Sonic1Constants.COL_MZ_ADDR;
            case Sonic1Constants.ZONE_SLZ -> Sonic1Constants.COL_SLZ_ADDR;
            case Sonic1Constants.ZONE_SYZ -> Sonic1Constants.COL_SYZ_ADDR;
            case Sonic1Constants.ZONE_SBZ -> Sonic1Constants.COL_SBZ_ADDR;
            default -> Sonic1Constants.COL_GHZ_ADDR;
        };
    }

    /**
     * Loads level boundaries from LevelSizeArray.
     *
     * <p>The array has 4 act slots per zone. Each entry is 12 bytes (6 words):
     * unused, left boundary, right boundary, top boundary, bottom boundary, vshift.
     *
     * <p>Falls back to reasonable defaults if the read fails.
     */
    private int[] loadBoundaries(int zone, int act) {
        try {
            // 12 bytes per entry, 4 acts per zone
            int addr = Sonic1Constants.LEVEL_SIZE_ARRAY_ADDR
                    + (zone * ACT_SLOTS_PER_ZONE + act) * 12;
            // Skip word 0 (unused), read words 1-4
            int leftBound = rom.read16BitAddr(addr + 2);
            int rightBound = rom.read16BitAddr(addr + 4);
            int topBound = (short) rom.read16BitAddr(addr + 6);
            int bottomBound = (short) rom.read16BitAddr(addr + 8);
            return new int[]{leftBound, rightBound, topBound, bottomBound};
        } catch (Exception e) {
            LOG.warning("Failed to read boundaries for zone " + zone + " act " + act +
                    ", using defaults: " + e.getMessage());
            return new int[]{0, 0x3000, 0, 0x800};
        }
    }

    /**
     * Gets the level palette address for a given palette ID.
     *
     * <p>The palette pointer table (PalPointers) at 0x2168 has 8-byte entries:
     * {@code dc.l sourceAddress, dc.w ramDest, dc.w (count-1)}.
     * The first longword is an absolute ROM pointer to the palette data.
     */
    private int getLevelPaletteAddr(int paletteId) throws IOException {
        int tableAddr = Sonic1Constants.PALETTE_TABLE_ADDR;
        return rom.read32BitAddr(tableAddr + paletteId * 8);
    }
}
