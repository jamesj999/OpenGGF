package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.resources.LevelResourcePlan;
import uk.co.jamesj999.sonic.level.resources.LoadOp;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Game implementation for Sonic 3 &amp; Knuckles.
 *
 * <p>Handles level loading by reading the LevelLoadBlock table to determine
 * ROM addresses for all level resources, then using LevelResourcePlan to
 * compose them via the standard resource loading pipeline.
 *
 * <p>Phase 1 supports terrain, collision, and basic palettes. No objects,
 * rings, or zone-specific features.
 */
public class Sonic3k extends Game {
    private static final Logger LOG = Logger.getLogger(Sonic3k.class.getName());

    private final Rom rom;

    // S3K SFX IDs (from sonic3k.constants.asm)
    private static final int SFX_JUMP = 0x62;
    private static final int SFX_RING_RIGHT = 0x33;
    private static final int SFX_RING_LEFT = 0x34;
    private static final int SFX_RING_LOSS = 0xB9;
    private static final int SFX_SPINDASH = 0xAB;
    private static final int SFX_SKID = 0x36;
    private static final int SFX_SPRING = 0xB1;
    private static final int SFX_ROLL = 0x3C;
    private static final int SFX_SPLASH = 0x39;
    private static final int SFX_DROWN = 0x3B;
    private static final int SFX_STARPOST = 0x63;
    private static final int SFX_BOSS_HIT = 0x6E;
    private static final int SFX_BUMPER = 0xAA;
    private static final int SFX_SPIKE_HIT = 0x37;

    public Sonic3k(Rom rom) throws IOException {
        this.rom = rom;
        // Ensure ROM has been scanned for table addresses
        new Sonic3kRomScanner(rom).scan();
    }

    @Override
    public Rom getRom() {
        return rom;
    }

    @Override
    public boolean isCompatible() {
        return true; // Detection already handled by Sonic3kRomDetector
    }

    @Override
    public String getIdentifier() {
        return "Sonic3k";
    }

    @Override
    public List<String> getTitleCards() {
        return List.of(
                "Angel Island Zone - Act 1", "Angel Island Zone - Act 2",
                "Hydrocity Zone - Act 1", "Hydrocity Zone - Act 2",
                "Marble Garden Zone - Act 1", "Marble Garden Zone - Act 2",
                "Carnival Night Zone - Act 1", "Carnival Night Zone - Act 2",
                "Flying Battery Zone - Act 1", "Flying Battery Zone - Act 2",
                "IceCap Zone - Act 1", "IceCap Zone - Act 2",
                "Launch Base Zone - Act 1", "Launch Base Zone - Act 2",
                "Mushroom Hill Zone - Act 1", "Mushroom Hill Zone - Act 2",
                "Sandopolis Zone - Act 1", "Sandopolis Zone - Act 2",
                "Lava Reef Zone - Act 1", "Lava Reef Zone - Act 2",
                "Sky Sanctuary Zone - Act 1", "Sky Sanctuary Zone - Act 2",
                "Death Egg Zone - Act 1", "Death Egg Zone - Act 2",
                "The Doomsday Zone");
    }

    @Override
    public int getMusicId(int levelIdx) throws IOException {
        // Convert levelIdx to zone/act
        int s3kIdx = levelIdx;
        if (levelIdx >= 0xC0) {
            s3kIdx = levelIdx - 0xC0;
        }

        int zone = s3kIdx / 2;
        int act = s3kIdx % 2;

        return Sonic3kZoneRegistry.getInstance().getMusicId(zone, act);
    }

    @Override
    public Map<GameSound, Integer> getSoundMap() {
        Map<GameSound, Integer> map = new HashMap<>();
        map.put(GameSound.JUMP, SFX_JUMP);
        map.put(GameSound.RING_LEFT, SFX_RING_LEFT);
        map.put(GameSound.RING_RIGHT, SFX_RING_RIGHT);
        map.put(GameSound.RING_SPILL, SFX_RING_LOSS);
        map.put(GameSound.SPINDASH_CHARGE, SFX_SPINDASH);
        map.put(GameSound.SPINDASH_RELEASE, SFX_SPINDASH);
        map.put(GameSound.SKID, SFX_SKID);
        map.put(GameSound.HURT, SFX_SPIKE_HIT);
        map.put(GameSound.HURT_SPIKE, SFX_SPIKE_HIT);
        map.put(GameSound.BADNIK_HIT, SFX_BOSS_HIT);
        map.put(GameSound.CHECKPOINT, SFX_STARPOST);
        map.put(GameSound.SPRING, SFX_SPRING);
        map.put(GameSound.BUMPER, SFX_BUMPER);
        map.put(GameSound.ROLLING, SFX_ROLL);
        map.put(GameSound.SPLASH, SFX_SPLASH);
        map.put(GameSound.DROWN, SFX_DROWN);
        return map;
    }

    @Override
    public Level loadLevel(int levelIdx) throws IOException {
        // Convert levelIdx to zone/act
        int s3kIdx = levelIdx;
        if (levelIdx >= 0xC0) {
            s3kIdx = levelIdx - 0xC0;
        }

        int zone = s3kIdx / 2;
        int act = s3kIdx % 2;

        LOG.info(String.format("Loading S3K level: zone=%d act=%d (levelIdx=0x%X)", zone, act, levelIdx));

        // Read LevelLoadBlock entry for this zone/act
        int llbIndex = zone * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act;
        int llbAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + llbIndex * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        // Parse 24-byte LevelLoadBlock entry
        int word0 = rom.read32BitAddr(llbAddr);       // (plc1 << 24) | primaryArtAddr
        int word1 = rom.read32BitAddr(llbAddr + 4);   // (plc2 << 24) | secondaryArtAddr
        int word2 = rom.read32BitAddr(llbAddr + 8);   // (palette << 24) | primaryBlocksAddr
        int word3 = rom.read32BitAddr(llbAddr + 12);  // (palette << 24) | secondaryBlocksAddr
        int word4 = rom.read32BitAddr(llbAddr + 16);  // primaryChunksAddr
        int word5 = rom.read32BitAddr(llbAddr + 20);  // secondaryChunksAddr

        int primaryArtAddr = word0 & 0x00FFFFFF;
        int secondaryArtAddr = word1 & 0x00FFFFFF;
        int paletteIndex = (word2 >> 24) & 0xFF;
        int primaryBlocksAddr = word2 & 0x00FFFFFF;
        int secondaryBlocksAddr = word3 & 0x00FFFFFF;
        int primaryChunksAddr = word4;
        int secondaryChunksAddr = word5;

        LOG.info(String.format("  LLB entry: art1=0x%06X art2=0x%06X blocks1=0x%06X blocks2=0x%06X " +
                        "chunks1=0x%06X chunks2=0x%06X pal=%d",
                primaryArtAddr, secondaryArtAddr, primaryBlocksAddr, secondaryBlocksAddr,
                primaryChunksAddr, secondaryChunksAddr, paletteIndex));

        // Build resource plan
        LevelResourcePlan.Builder planBuilder = LevelResourcePlan.builder();

        // Patterns (KosM)
        // Read primary art's KosinskiM header to get its uncompressed size.
        // The original ROM loads secondary art at VRAM offset = primary art uncompressed size
        // (i.e. concatenated after primary art, not overlaid at offset 0).
        int primaryArtSize = rom.read16BitAddr(primaryArtAddr);
        LOG.info(String.format("  Primary art KosinskiM header: uncompressed size = 0x%04X (%d bytes, %d tiles)",
                primaryArtSize, primaryArtSize, primaryArtSize / 32));

        planBuilder.addPatternOp(LoadOp.kosinskiMBase(primaryArtAddr));
        if (secondaryArtAddr != primaryArtAddr && secondaryArtAddr > 0) {
            int secondaryArtSize = rom.read16BitAddr(secondaryArtAddr);
            LOG.info(String.format("  Secondary art KosinskiM header: uncompressed size = 0x%04X (%d bytes, %d tiles)",
                    secondaryArtSize, secondaryArtSize, secondaryArtSize / 32));
            planBuilder.addPatternOp(LoadOp.kosinskiMOverlay(secondaryArtAddr, primaryArtSize));
        }

        // Blocks (16x16, Kosinski) - "chunks" in engine terminology
        planBuilder.addChunkOp(LoadOp.kosinskiBase(primaryBlocksAddr));
        if (secondaryBlocksAddr != primaryBlocksAddr && secondaryBlocksAddr > 0) {
            planBuilder.addChunkOp(LoadOp.kosinskiOverlay(secondaryBlocksAddr, 0));
        }

        // Chunks (128x128, Kosinski) - "blocks" in engine terminology
        planBuilder.addBlockOp(LoadOp.kosinskiBase(primaryChunksAddr));
        if (secondaryChunksAddr != primaryChunksAddr && secondaryChunksAddr > 0) {
            planBuilder.addBlockOp(LoadOp.kosinskiOverlay(secondaryChunksAddr, 0));
        }

        // Collision indices (loaded directly, not through resource plan)
        // Returns [primaryAddr, secondaryAddr, interleavedFlag]
        int[] collisionInfo = getCollisionAddresses(zone, act);
        int primaryCollisionAddr = collisionInfo[0];
        int secondaryCollisionAddr = collisionInfo[1];
        boolean interleavedCollision = collisionInfo[2] != 0;

        LevelResourcePlan plan = planBuilder.build();

        // Get layout address from LevelPtrs
        int layoutAddr = getLayoutAddr(zone, act);

        // Get level boundaries address from LevelSizes
        int boundariesAddr = getLevelBoundariesAddr(zone, act);

        // Get palette address
        int levelPaletteAddr = getLevelPaletteAddr(paletteIndex);

        // Character palette
        int characterPaletteAddr = Sonic3kConstants.SONIC_PALETTE_ADDR;

        return new Sonic3kLevel(rom, zone, plan,
                primaryCollisionAddr, secondaryCollisionAddr, interleavedCollision,
                layoutAddr, boundariesAddr,
                characterPaletteAddr, levelPaletteAddr);
    }

    @Override
    public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
        // Simple parallax: BG at half camera speed
        return new int[]{cameraX / 2, cameraY / 2};
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
    public boolean relocateLevels(boolean unsafe) {
        return false;
    }

    @Override
    public boolean save(int levelIdx, Level level) {
        return false;
    }

    // ===== Private helpers =====

    /**
     * Gets the primary and secondary collision data addresses for a zone.
     *
     * <p>SolidIndexes entries are 32-bit pointers to collision index data, one per act
     * (indexed as zone*2+act). The format is determined by address comparison, matching
     * the original LoadSolids routine (sonic3k.asm:9549-9558):
     * <ul>
     *   <li>Address >= S3_LEVEL_SOLID_DATA (0x260000): non-interleaved (S3 zones)</li>
     *   <li>Address < S3_LEVEL_SOLID_DATA: interleaved (SK zones)</li>
     * </ul>
     *
     * <p>Non-interleaved: primary 0x600 bytes, then secondary 0x600 bytes.
     * Interleaved: primary and secondary alternate bytes in 0xC00 block.
     *
     * @return int[3]: [primaryCollisionAddr, secondaryCollisionAddr, interleavedFlag (0 or 1)]
     */
    private int[] getCollisionAddresses(int zone, int act) throws IOException {
        int solidIndexesAddr = Sonic3kConstants.SOLID_INDEXES_ADDR;
        if (solidIndexesAddr == 0) {
            LOG.warning("SolidIndexes address not set - no collision data");
            return new int[]{0, 0, 0};
        }

        // SolidIndexes has one entry per act: index = zone*2 + act
        int entryAddr = solidIndexesAddr + (zone * 2 + act) * Sonic3kConstants.SOLID_INDEXES_ENTRY_SIZE;
        int rawPtr = rom.read32BitAddr(entryAddr);

        // Mask to 24-bit address (strip bit 31 for S3Complete compatibility)
        int address = rawPtr & 0x00FFFFFF;

        // Format detection by address comparison (matches original LoadSolids routine)
        boolean nonInterleaved = (address >= Sonic3kConstants.S3_LEVEL_SOLID_DATA);

        LOG.info(String.format("  Collision: raw=0x%08X addr=0x%06X nonInterleaved=%b",
                rawPtr, address, nonInterleaved));

        if (nonInterleaved) {
            // S3 zones: primary 0x600 bytes followed by secondary 0x600 bytes
            return new int[]{address, address + Sonic3kConstants.COLLISION_INDEX_SIZE, 0};
        } else {
            // SK zones: interleaved format - both layers in same 0xC00 byte block
            return new int[]{address, address + 1, 1};
        }
    }

    /**
     * Gets the layout data ROM address for a zone/act from the LevelPtrs table.
     */
    private int getLayoutAddr(int zone, int act) throws IOException {
        int levelPtrsAddr = Sonic3kConstants.LEVEL_PTRS_ADDR;
        if (levelPtrsAddr == 0) {
            LOG.warning("LevelPtrs address not set");
            return 0;
        }

        int index = zone * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act;
        int ptr = rom.read32BitAddr(levelPtrsAddr + index * Sonic3kConstants.LEVEL_PTRS_ENTRY_SIZE);

        LOG.info(String.format("  Layout pointer: zone=%d act=%d -> 0x%06X", zone, act, ptr));
        return ptr;
    }

    /**
     * Gets the level boundaries address from the LevelSizes table.
     */
    private int getLevelBoundariesAddr(int zone, int act) {
        int levelSizesAddr = Sonic3kConstants.LEVEL_SIZES_ADDR;
        if (levelSizesAddr == 0) {
            return 0;
        }
        int index = zone * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act;
        return levelSizesAddr + index * Sonic3kConstants.LEVEL_SIZES_ENTRY_SIZE;
    }

    /**
     * Gets the level palette ROM address from the palette index.
     *
     * <p>S3K palette pointers are stored in PalPointers table, format:
     * dc.l sourceAddr, dc.w ramDest, dc.w (count-1)
     * (8 bytes per entry, same as S2).
     *
     * <p>If PalPointers address is not yet scanned, returns 0.
     */
    private int getLevelPaletteAddr(int paletteIndex) throws IOException {
        int palPointersAddr = Sonic3kConstants.PAL_POINTERS_ADDR;
        if (palPointersAddr == 0) {
            LOG.fine("PalPointers address not set - using default palette");
            return 0;
        }
        int entryAddr = palPointersAddr + paletteIndex * 8;
        return rom.read32BitAddr(entryAddr);
    }
}
