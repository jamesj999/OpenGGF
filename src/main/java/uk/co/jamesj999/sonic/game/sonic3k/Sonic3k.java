package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.AnimatedPaletteProvider;
import uk.co.jamesj999.sonic.data.AnimatedPatternProvider;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.PlayerSpriteArtProvider;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.DynamicStartPositionProvider;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kAudioProfile;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.animation.AnimatedPaletteManager;
import uk.co.jamesj999.sonic.level.animation.AnimatedPatternManager;
import uk.co.jamesj999.sonic.level.resources.LevelResourcePlan;
import uk.co.jamesj999.sonic.level.resources.LoadOp;
import uk.co.jamesj999.sonic.level.resources.ResourceLoader;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizIntroTerrainSwap;

import java.util.LinkedHashSet;
import java.io.IOException;
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
public class Sonic3k extends Game implements PlayerSpriteArtProvider, DynamicStartPositionProvider,
        AnimatedPatternProvider, AnimatedPaletteProvider {
    private static final Logger LOG = Logger.getLogger(Sonic3k.class.getName());

    private final Rom rom;
    private Sonic3kPlayerArt playerArt;
    private Sonic3kRingArt ringArt;
    private Sonic3kLevelAnimationManager levelAnimationManager;
    private Level levelAnimationLevel;
    private int levelAnimationZone = -1;

    public Sonic3k(Rom rom) throws IOException {
        this.rom = rom;
        ensureAddressTablesReady();
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
        return new Sonic3kAudioProfile().getSoundMap();
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        if (playerArt == null) {
            playerArt = new Sonic3kPlayerArt(RomByteReader.fromRom(rom));
        }
        return playerArt.loadForCharacter(characterCode);
    }

    @Override
    public AnimatedPatternManager loadAnimatedPatternManager(Level level, int zoneIndex) throws IOException {
        if (level == null) {
            return null;
        }
        return getOrCreateLevelAnimationManager(level, zoneIndex);
    }

    @Override
    public AnimatedPaletteManager loadAnimatedPaletteManager(Level level, int zoneIndex) throws IOException {
        if (level == null) {
            return null;
        }
        return getOrCreateLevelAnimationManager(level, zoneIndex);
    }

    private Sonic3kLevelAnimationManager getOrCreateLevelAnimationManager(Level level, int zoneIndex) throws IOException {
        if (levelAnimationManager != null
                && levelAnimationLevel == level
                && levelAnimationZone == zoneIndex) {
            return levelAnimationManager;
        }
        int actIndex = LevelManager.getInstance().getCurrentAct();
        Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(zoneIndex, actIndex);
        levelAnimationManager = new Sonic3kLevelAnimationManager(
                RomByteReader.fromRom(rom), level, zoneIndex, actIndex, bootstrap.isSkipIntro());
        levelAnimationLevel = level;
        levelAnimationZone = zoneIndex;
        return levelAnimationManager;
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
        Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(zone, act);

        LOG.info(String.format("Loading S3K level: zone=%d act=%d (levelIdx=0x%X)", zone, act, levelIdx));

        // Read LevelLoadBlock entry for this zone/act
        int llbIndex = resolveLevelLoadBlockIndex(zone, act, bootstrap);
        if (llbIndex != zone * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act) {
            LOG.info(String.format("  Using alternate LevelLoadBlock index %d for bootstrap mode %s",
                    llbIndex, bootstrap.mode()));
        }
        int llbAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + llbIndex * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        // Parse 24-byte LevelLoadBlock entry
        int word0 = rom.read32BitAddr(llbAddr);       // (plc1 << 24) | primaryArtAddr
        int word1 = rom.read32BitAddr(llbAddr + 4);   // (plc2 << 24) | secondaryArtAddr
        int word2 = rom.read32BitAddr(llbAddr + 8);   // (palette << 24) | primaryBlocksAddr
        int word3 = rom.read32BitAddr(llbAddr + 12);  // (palette << 24) | secondaryBlocksAddr
        int word4 = rom.read32BitAddr(llbAddr + 16);  // primaryChunksAddr
        int word5 = rom.read32BitAddr(llbAddr + 20);  // secondaryChunksAddr

        int plcPrimary = (word0 >>> 24) & 0xFF;
        int plcSecondary = (word1 >>> 24) & 0xFF;
        int primaryArtAddr = word0 & 0x00FFFFFF;
        int secondaryArtAddr = word1 & 0x00FFFFFF;
        int paletteIndex = (word2 >> 24) & 0xFF;
        int primaryBlocksAddr = word2 & 0x00FFFFFF;
        int secondaryBlocksAddr = word3 & 0x00FFFFFF;
        int primaryChunksAddr = word4 & 0x00FFFFFF;
        int secondaryChunksAddr = word5 & 0x00FFFFFF;

        boolean applyAiz1OverlayBridge = zone == 0
                && act == 0
                && bootstrap != null
                && llbIndex != Sonic3kConstants.LEVEL_LOAD_BLOCK_AIZ1_INTRO_INDEX
                && bootstrap.isSkipIntro();
        if (applyAiz1OverlayBridge) {
            Aiz1GameplayOverlay override = readAiz1GameplayOverlayFromIntroEntry();
            if (override != null) {
                // AIZ1 parity bridge:
                // Skip-intro bootstrap should immediately use AIZ1 "main level" overlays
                // for 8x8/16x16 terrain resources.
                secondaryArtAddr = override.secondaryArtAddr();
                secondaryBlocksAddr = override.secondaryBlocksAddr();
                LOG.info(String.format("  AIZ1 overlay bridge active: art2=0x%06X blocks2=0x%06X",
                        secondaryArtAddr, secondaryBlocksAddr));
            } else {
                LOG.warning("  AIZ1 overlay bridge requested but intro overlay entry was invalid.");
            }
        }

        LOG.info(String.format("  LLB entry: plc1=0x%02X plc2=0x%02X art1=0x%06X art2=0x%06X " +
                        "blocks1=0x%06X blocks2=0x%06X chunks1=0x%06X chunks2=0x%06X pal=%d",
                plcPrimary, plcSecondary,
                primaryArtAddr, secondaryArtAddr, primaryBlocksAddr, secondaryBlocksAddr,
                primaryChunksAddr, secondaryChunksAddr, paletteIndex));

        // Build resource plan
        LevelResourcePlan.Builder planBuilder = LevelResourcePlan.builder();
        ResourceLoader resourceLoader = new ResourceLoader(rom);

        // Patterns (KosM)
        // Use decompressed lengths for KosM overlay offsets.
        // Header-only size reads are not reliable across all module streams.
        int primaryArtSize = resourceLoader.loadSingle(LoadOp.kosinskiMBase(primaryArtAddr)).length;
        LOG.info(String.format("  Primary art KosinskiM decompressed size = 0x%04X (%d bytes, %d tiles)",
                primaryArtSize, primaryArtSize, primaryArtSize / 32));

        planBuilder.addPatternOp(LoadOp.kosinskiMBase(primaryArtAddr));
        if (secondaryArtAddr != primaryArtAddr && secondaryArtAddr > 0) {
            int secondaryArtSize = resourceLoader.loadSingle(LoadOp.kosinskiMBase(secondaryArtAddr)).length;
            LOG.info(String.format("  Secondary art KosinskiM decompressed size = 0x%04X (%d bytes, %d tiles)",
                    secondaryArtSize, secondaryArtSize, secondaryArtSize / 32));
            planBuilder.addPatternOp(LoadOp.kosinskiMOverlay(secondaryArtAddr, primaryArtSize));
        }
        addLevelPlcPatternOps(planBuilder, zone, act, bootstrap, plcPrimary, plcSecondary);

        // Blocks (16x16, Kosinski) - "chunks" in engine terminology.
        // ROM LoadLevelLoadBlock2 reuses a1 across both Kos_Decomp calls, so the
        // second stream appends immediately after the first in Block_table.
        planBuilder.addChunkOp(LoadOp.kosinskiBase(primaryBlocksAddr));
        if (secondaryBlocksAddr != primaryBlocksAddr && secondaryBlocksAddr > 0) {
            int primaryBlocksSize = resourceLoader.loadSingle(LoadOp.kosinskiBase(primaryBlocksAddr)).length;
            int secondaryBlocksSize = resourceLoader.loadSingle(LoadOp.kosinskiBase(secondaryBlocksAddr)).length;
            LOG.info(String.format("  16x16 sizes: primary=0x%04X (%d bytes) secondary=0x%04X (%d bytes) append@0x%04X",
                    primaryBlocksSize, primaryBlocksSize, secondaryBlocksSize, secondaryBlocksSize, primaryBlocksSize));
            planBuilder.addChunkOp(LoadOp.kosinskiOverlay(secondaryBlocksAddr, primaryBlocksSize));
        }

        // Chunks (128x128, Kosinski) - "blocks" in engine terminology.
        // Same as above: destination pointer is preserved by Kos_Decomp, so
        // the secondary stream appends after primary in RAM_start.
        planBuilder.addBlockOp(LoadOp.kosinskiBase(primaryChunksAddr));
        if (secondaryChunksAddr != primaryChunksAddr && secondaryChunksAddr > 0) {
            int primaryChunksSize = resourceLoader.loadSingle(LoadOp.kosinskiBase(primaryChunksAddr)).length;
            int secondaryChunksSize = resourceLoader.loadSingle(LoadOp.kosinskiBase(secondaryChunksAddr)).length;
            LOG.info(String.format("  128x128 sizes: primary=0x%04X (%d bytes) secondary=0x%04X (%d bytes) append@0x%04X",
                    primaryChunksSize, primaryChunksSize, secondaryChunksSize, secondaryChunksSize, primaryChunksSize));
            planBuilder.addBlockOp(LoadOp.kosinskiOverlay(secondaryChunksAddr, primaryChunksSize));
        }

        // Collision indices (loaded directly, not through resource plan)
        // Returns [primaryAddr, secondaryAddr, interleavedFlag]
        CollisionAddressInfo collisionInfo = getCollisionAddresses(zone, act);
        int primaryCollisionAddr = collisionInfo.primaryAddress();
        int secondaryCollisionAddr = collisionInfo.secondaryAddress();
        boolean interleavedCollision = collisionInfo.interleaved();

        LevelResourcePlan plan = planBuilder.build();

        // Get layout address from LevelPtrs
        int layoutAddr = getLayoutAddr(zone, act);

        // Get level boundaries address from LevelSizes
        int boundariesAddr = getLevelBoundariesAddr(zone, act, bootstrap);
        Integer boundariesMinXOverride = null;
        if (zone == 0 && act == 0
                && bootstrap != null
                && bootstrap.mode() == Sonic3kLoadBootstrap.Mode.INTRO) {
            // ROM Get_LevelSizeStart uses AIZ intro LevelSizes profile, then overrides min X to 0
            // for the intro start (x=$40, y=$420).
            boundariesMinXOverride = 0;
        }

        // Get palette address
        int levelPaletteAddr = getLevelPaletteAddr(paletteIndex);

        // Character palette
        int characterPaletteAddr = Sonic3kConstants.SONIC_PALETTE_ADDR;

        // Load objects and rings
        RomByteReader romReader = RomByteReader.fromRom(rom);
        var objectPlacement = new Sonic3kObjectPlacement(romReader);
        var objectSpawns = objectPlacement.load(zone, act);

        var ringPlacement = new Sonic3kRingPlacement(romReader);
        var ringSpawns = ringPlacement.load(zone, act);

        if (ringArt == null) {
            ringArt = new Sonic3kRingArt(rom);
        }
        var ringSpriteSheet = ringArt.load();

        LOG.info(String.format("  S3K loaded %d objects, %d rings for zone=%d act=%d",
                objectSpawns.size(), ringSpawns.size(), zone, act));

        Sonic3kLevel level = new Sonic3kLevel(rom, zone, plan,
                primaryCollisionAddr, secondaryCollisionAddr, interleavedCollision,
                layoutAddr, boundariesAddr,
                characterPaletteAddr, levelPaletteAddr,
                boundariesMinXOverride,
                objectSpawns, ringSpawns, ringSpriteSheet);

        // Pre-decompress AIZ intro overlay data during level load so the
        // terrain swap at camera X=0x1400 doesn't cause a frame hitch.
        boolean isAizIntro = zone == 0 && act == 0
                && bootstrap != null
                && bootstrap.mode() == Sonic3kLoadBootstrap.Mode.INTRO;
        if (isAizIntro) {
            AizIntroTerrainSwap.preloadOverlayData();
            AizIntroTerrainSwap.precomputeTransitionTilemaps();
        }

        return level;
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

    @Override
    public int[] getStartPosition(int zoneIndex, int actIndex) throws IOException {
        Sonic3kLoadBootstrap bootstrap = Sonic3kBootstrapResolver.resolve(zoneIndex, actIndex);
        if (bootstrap.hasIntroStartPosition()) {
            return bootstrap.introStartPosition();
        }

        int startTableAddr = getCharacterStartTableAddr();
        if (!isReadable(startTableAddr, Sonic3kConstants.START_LOCATION_ENTRY_SIZE)) {
            return null;
        }

        int act = Math.max(0, Math.min(actIndex, Sonic3kConstants.ACTS_PER_ZONE_STRIDE - 1));
        int entryIndex = zoneIndex * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act;
        int entryAddr = startTableAddr + entryIndex * Sonic3kConstants.START_LOCATION_ENTRY_SIZE;

        if (!isReadable(entryAddr, Sonic3kConstants.START_LOCATION_ENTRY_SIZE)) {
            return null;
        }

        int x = rom.read16BitAddr(entryAddr);
        int y = rom.read16BitAddr(entryAddr + 2);
        return new int[]{x, y};
    }

    // ===== Private helpers =====

    private void ensureAddressTablesReady() throws IOException {
        if (hasValidCoreTables()) {
            LOG.info("S3K core tables validated using verified constants.");
            return;
        }

        LOG.warning("S3K constants failed sanity checks; running ROM scanner fallback.");
        Sonic3kConstants.setScanned(false);
        new Sonic3kRomScanner(rom).scan();

        if (!hasValidCoreTables()) {
            throw new IOException("S3K address table validation failed after scanner fallback.");
        }
    }

    private boolean hasValidCoreTables() throws IOException {
        return hasValidLevelLoadBlock()
                && hasValidLevelPointers()
                && hasValidSolidIndexes()
                && hasValidStartLocations();
    }

    private boolean hasValidLevelLoadBlock() throws IOException {
        int llbAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR;
        if (!isReadable(llbAddr, Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE)) {
            return false;
        }

        int word0 = rom.read32BitAddr(llbAddr);
        int word2 = rom.read32BitAddr(llbAddr + 8);
        int word4 = rom.read32BitAddr(llbAddr + 16);

        int primaryArtAddr = word0 & 0x00FFFFFF;
        int primaryBlocksAddr = word2 & 0x00FFFFFF;
        int primaryChunksAddr = word4 & 0x00FFFFFF;

        return isReadable(primaryArtAddr, 2)
                && isReadable(primaryBlocksAddr, 2)
                && isReadable(primaryChunksAddr, 2);
    }

    private boolean hasValidLevelPointers() throws IOException {
        int levelPtrsAddr = Sonic3kConstants.LEVEL_PTRS_ADDR;
        if (!isReadable(levelPtrsAddr, Sonic3kConstants.LEVEL_PTRS_ENTRY_SIZE)) {
            return false;
        }

        int layoutAddr = rom.read32BitAddr(levelPtrsAddr);
        if (!isReadable(layoutAddr, Sonic3kConstants.LEVEL_LAYOUT_HEADER_SIZE)) {
            return false;
        }

        int fgCols = rom.read16BitAddr(layoutAddr);
        int bgCols = rom.read16BitAddr(layoutAddr + 2);
        int fgRows = rom.read16BitAddr(layoutAddr + 4);
        int bgRows = rom.read16BitAddr(layoutAddr + 6);
        return fgCols > 0 && fgCols <= Sonic3kConstants.MAP_WIDTH
                && bgCols > 0 && bgCols <= Sonic3kConstants.MAP_WIDTH
                && fgRows > 0 && fgRows <= Sonic3kConstants.MAP_HEIGHT
                && bgRows > 0 && bgRows <= Sonic3kConstants.MAP_HEIGHT;
    }

    private boolean hasValidSolidIndexes() throws IOException {
        int solidIndexesAddr = Sonic3kConstants.SOLID_INDEXES_ADDR;
        if (!isReadable(solidIndexesAddr, Sonic3kConstants.SOLID_INDEXES_ENTRY_SIZE)) {
            return false;
        }

        int rawPtr = rom.read32BitAddr(solidIndexesAddr);
        CollisionAddressInfo decoded = decodeCollisionPointer(rawPtr);
        int primarySize = decoded.interleaved()
                ? Sonic3kConstants.COLLISION_INDEX_SIZE * 2
                : Sonic3kConstants.COLLISION_INDEX_SIZE;
        int secondarySize = primarySize;
        return isReadable(decoded.primaryAddress(), primarySize)
                && isReadable(decoded.secondaryAddress(), secondarySize);
    }

    private boolean hasValidStartLocations() throws IOException {
        int startAddr = Sonic3kConstants.SONIC_START_LOCATIONS_ADDR;
        if (!isReadable(startAddr, Sonic3kConstants.START_LOCATION_ENTRY_SIZE)) {
            return false;
        }
        int x = rom.read16BitAddr(startAddr);
        int y = rom.read16BitAddr(startAddr + 2);
        return x > 0 && y > 0;
    }

    private boolean isReadable(int addr, int size) throws IOException {
        if (addr <= 0 || size <= 0) {
            return false;
        }
        long romSize = rom.getSize();
        long start = Integer.toUnsignedLong(addr);
        long end = start + size;
        return start < romSize && end <= romSize;
    }

    private int getCharacterStartTableAddr() {
        String mainCharacterCode = SonicConfigurationService.getInstance()
                .getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if ("knuckles".equalsIgnoreCase(mainCharacterCode)) {
            return Sonic3kConstants.KNUX_START_LOCATIONS_ADDR;
        }
        return Sonic3kConstants.SONIC_START_LOCATIONS_ADDR;
    }

    private Aiz1GameplayOverlay readAiz1GameplayOverlayFromIntroEntry() throws IOException {
        int introIndex = Sonic3kConstants.LEVEL_LOAD_BLOCK_AIZ1_INTRO_INDEX;
        int entryAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + introIndex * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        if (!isReadable(entryAddr, Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE)) {
            return null;
        }

        int word1 = rom.read32BitAddr(entryAddr + 4);   // (plc2 << 24) | secondaryArtAddr
        int word3 = rom.read32BitAddr(entryAddr + 12);  // (palette << 24) | secondaryBlocksAddr

        int secondaryArtAddr = word1 & 0x00FFFFFF;
        int secondaryBlocksAddr = word3 & 0x00FFFFFF;
        if (!isReadable(secondaryArtAddr, 2) || !isReadable(secondaryBlocksAddr, 2)) {
            return null;
        }

        return new Aiz1GameplayOverlay(secondaryArtAddr, secondaryBlocksAddr);
    }

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
     * @return CollisionAddressInfo with decoded addresses and format
     */
    private CollisionAddressInfo getCollisionAddresses(int zone, int act) throws IOException {
        int solidIndexesAddr = Sonic3kConstants.SOLID_INDEXES_ADDR;
        if (solidIndexesAddr == 0) {
            LOG.warning("SolidIndexes address not set - no collision data");
            return new CollisionAddressInfo(0, 0, false);
        }

        // SolidIndexes has one entry per act: index = zone*2 + act
        int entryAddr = solidIndexesAddr + (zone * 2 + act) * Sonic3kConstants.SOLID_INDEXES_ENTRY_SIZE;
        int rawPtr = rom.read32BitAddr(entryAddr);
        CollisionAddressInfo decoded = decodeCollisionPointer(rawPtr);

        LOG.info(String.format("  Collision: raw=0x%08X primary=0x%06X secondary=0x%06X interleaved=%b",
                rawPtr, decoded.primaryAddress(), decoded.secondaryAddress(), decoded.interleaved()));

        return decoded;
    }

    static CollisionAddressInfo decodeCollisionPointer(int rawPtr) {
        int pointerNoHighBit = rawPtr & 0x7FFFFFFF;
        int address = pointerNoHighBit & 0x00FFFFFF;
        boolean highBitMarker = (rawPtr & 0x80000000) != 0;
        boolean lowBitMarker = (address & 1) != 0;

        // Matches both S3Complete and non-S3Complete pointer encodings:
        // - low bit marker (+1) for non-interleaved entries
        // - high bit marker for S3Complete builds
        // - address threshold fallback for standard combined ROMs
        boolean nonInterleaved = highBitMarker
                || lowBitMarker
                || address >= Sonic3kConstants.S3_LEVEL_SOLID_DATA;

        if (nonInterleaved) {
            return new CollisionAddressInfo(
                    address,
                    address + Sonic3kConstants.COLLISION_INDEX_SIZE,
                    false
            );
        }
        return new CollisionAddressInfo(address, address + 1, true);
    }

    static record CollisionAddressInfo(int primaryAddress, int secondaryAddress, boolean interleaved) {}
    private record Aiz1GameplayOverlay(int secondaryArtAddr, int secondaryBlocksAddr) {}

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
        int ptr = rom.read32BitAddr(levelPtrsAddr + index * Sonic3kConstants.LEVEL_PTRS_ENTRY_SIZE) & 0x00FFFFFF;

        LOG.info(String.format("  Layout pointer: zone=%d act=%d -> 0x%06X", zone, act, ptr));
        return ptr;
    }

    static int resolveLevelLoadBlockIndex(int zone, int act, Sonic3kLoadBootstrap bootstrap) {
        int baseIndex = zone * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act;

        // ROM parity (LoadLevelLoadBlock / LoadLevelLoadBlock2):
        // For AIZ1 Sonic/Tails with no starpost (intro path), d0 is NOT overridden
        // to $0D00, so resources come from normal AIZ1 (zone/act entry 0).
        // The $0D00 override (entry 26) applies to post-intro/skip-intro paths.
        if (zone == 0
                && act == 0
                && bootstrap != null
                && bootstrap.mode() == Sonic3kLoadBootstrap.Mode.SKIP_INTRO) {
            return Sonic3kConstants.LEVEL_LOAD_BLOCK_AIZ1_INTRO_INDEX;
        }
        return baseIndex;
    }

    /**
     * Gets the level boundaries address from the LevelSizes table.
     */
    private int getLevelBoundariesAddr(int zone, int act, Sonic3kLoadBootstrap bootstrap) {
        int levelSizesAddr = Sonic3kConstants.LEVEL_SIZES_ADDR;
        if (levelSizesAddr == 0) {
            return 0;
        }

        int index = zone * Sonic3kConstants.ACTS_PER_ZONE_STRIDE + act;
        if (zone == 0 && act == 0 && bootstrap != null) {
            if (bootstrap.mode() == Sonic3kLoadBootstrap.Mode.SKIP_INTRO) {
                // Intro-skip bootstrap parity bridge:
                // keep the wider intro profile so gameplay-after-intro bootstrap
                // remains valid without full intro transition state.
                index = Sonic3kConstants.LEVEL_SIZES_AIZ1_INTRO_INDEX;
            }
            // Mode.INTRO uses the normal AIZ1 LevelSizes bounds (already set above)
            // and then overrides min X to 0 via boundariesMinXOverride.
        }
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
        return rom.read32BitAddr(entryAddr) & 0x00FFFFFF;
    }

    private void addLevelPlcPatternOps(LevelResourcePlan.Builder planBuilder,
                                       int zone,
                                       int act,
                                       Sonic3kLoadBootstrap bootstrap,
                                       int plcPrimary,
                                       int plcSecondary) {
        boolean aizIntro = zone == 0
                && act == 0
                && bootstrap != null
                && bootstrap.mode() == Sonic3kLoadBootstrap.Mode.INTRO;
        if (aizIntro) {
            // ROM Level startup sequence for AIZ intro:
            // 1) Level PLC is requested from LevelLoadBlock (PLC_0B for AIZ1),
            // 2) then Load_PLC_2 #1 clears the queue and loads common HUD art,
            // 3) then Load_PLC #$0A appends intro-sprite art.
            // So effective startup overlays are PLC_01 + PLC_0A only.
            //
            // PLC 0x0A (intro waves, 344 tiles at $03D1-$0528) overlaps with
            // spikes/springs ($0494-$04C3). In the ROM, PLCs process
            // incrementally over VBlanks so the overwrite is staggered. In our
            // engine, all overlays apply at once, so we load intro sprites FIRST
            // and spikes/springs PLC LAST to keep them correct for gameplay.
            appendPlcPatternOps(planBuilder, 0x0A);
            appendPlcPatternOps(planBuilder, 0x01);
            appendPlcPatternOps(planBuilder, 0x4E);
            return;
        }

        LinkedHashSet<Integer> plcOrder = new LinkedHashSet<>();
        if (plcPrimary != 0) {
            plcOrder.add(plcPrimary);
        }
        if (plcSecondary != 0 && plcSecondary != plcPrimary) {
            plcOrder.add(plcSecondary);
        }
        // ROM startup path always loads active-character PLC after level PLC setup.
        plcOrder.add(resolveStartupCharacterPlcIndex());
        // PLC 0x4E (spikes/springs art). In the ROM this is loaded at runtime by
        // specific objects (signpost, boss defeat) via Load_PLC_Raw, but since those
        // aren't implemented yet, load it at startup so spikes/springs render.
        plcOrder.add(0x4E);

        for (int plcIndex : plcOrder) {
            appendPlcPatternOps(planBuilder, plcIndex);
        }
    }

    private int resolveStartupCharacterPlcIndex() {
        String mainCharacterCode = SonicConfigurationService.getInstance()
                .getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if ("knuckles".equalsIgnoreCase(mainCharacterCode)) {
            return 0x05;
        }
        if ("tails".equalsIgnoreCase(mainCharacterCode)) {
            return 0x07;
        }
        return 0x01;
    }

    private void appendPlcPatternOps(LevelResourcePlan.Builder planBuilder, int plcIndex) {
        try {
            var plc = Sonic3kPlcLoader.parsePlc(rom, plcIndex);
            List<LoadOp> ops = Sonic3kPlcLoader.toPatternOps(plc);
            for (LoadOp op : ops) {
                planBuilder.addPatternOp(op);
            }
        } catch (IOException e) {
            LOG.warning(String.format("Failed to parse PLC 0x%02X from ROM: %s", plcIndex, e.getMessage()));
        }
    }
}
