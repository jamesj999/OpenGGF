package uk.co.jamesj999.sonic.game.sonic2;

import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;

import uk.co.jamesj999.sonic.audio.GameSound;
import uk.co.jamesj999.sonic.data.AnimatedPaletteProvider;
import uk.co.jamesj999.sonic.data.AnimatedPatternProvider;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.ZoneAwareObjectArtProvider;
import uk.co.jamesj999.sonic.data.PlayerSpriteArtProvider;
import uk.co.jamesj999.sonic.data.SpindashDustArtProvider;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.animation.AnimatedPaletteManager;
import uk.co.jamesj999.sonic.level.animation.AnimatedPatternManager;
import uk.co.jamesj999.sonic.level.objects.ObjectArtData;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.resources.LevelResourcePlan;
import uk.co.jamesj999.sonic.level.rings.RingSpawn;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Music;
import uk.co.jamesj999.sonic.game.sonic2.audio.Sonic2Sfx;

import static uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants.*;

public class Sonic2 extends Game implements PlayerSpriteArtProvider, SpindashDustArtProvider,
        ZoneAwareObjectArtProvider, AnimatedPatternProvider, AnimatedPaletteProvider {

    private final Rom rom;
    private RomByteReader romReader;
    private Sonic2ObjectPlacement objectPlacement;
    private Sonic2RingPlacement ringPlacement;
    private Sonic2RingArt ringArt;
    private Sonic2PlayerArt playerArt;
    private Sonic2DustArt dustArt;
    private Sonic2ObjectArt objectArt;
    private static final int BG_SCROLL_TABLE_ADDR = 0x00C296;

    public Sonic2(Rom rom) {
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
            return name.contains("SONIC THE") && name.contains("HEDGEHOG 2");
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String getIdentifier() {
        return "Sonic2";
    }

    @Override
    public List<String> getTitleCards() {
        return List.of(
                "Emerald Hill Zone - Act 1", "Emerald Hill Zone - Act 2",
                "Chemical Plant Zone - Act 1", "Chemical Plant Zone - Act 2",
                "Aquatic Ruins Zone - Act 1", "Aquatic Ruins Zone - Act 2",
                "Casino Night Zone - Act 1", "Casino Night Zone - Act 2",
                "Hill Top Zone - Act 1", "Hill Top Zone - Act 2",
                "Mystic Cave Zone - Act 1", "Mystic Zone - Act 2",
                "Oil Ocean Zone - Act 1", "Oil Ocean Zone - Act 2",
                "Metropolis Zone - Act 1", "Metropolis Zone - Act 2", "Metropolis Zone - Act 3",
                "Sky Chase Zone - Act 1", "Wing Fortress Zone - Act 1",
                "Death Egg Zone - Act 1");
    }

    @Override
    public int getMusicId(int levelIdx) throws IOException {
        switch (levelIdx) {
            case 0: // Emerald Hill 1
            case 1: // Emerald Hill 2
                return Sonic2Music.EMERALD_HILL.id;
            case 2: // Chemical Plant 1
            case 3: // Chemical Plant 2
                return Sonic2Music.CHEMICAL_PLANT.id;
            case 4: // Aquatic Ruin 1
            case 5: // Aquatic Ruin 2
                return Sonic2Music.AQUATIC_RUIN.id;
            case 6: // Casino Night 1
            case 7: // Casino Night 2
                return Sonic2Music.CASINO_NIGHT.id;
            case 8: // Hill Top 1
            case 9: // Hill Top 2
                return Sonic2Music.HILL_TOP.id;
            case 10: // Mystic Cave 1
            case 11: // Mystic Cave 2
                return Sonic2Music.MYSTIC_CAVE.id;
            case 12: // Oil Ocean 1
            case 13: // Oil Ocean 2
                return Sonic2Music.OIL_OCEAN.id;
            case 14: // Metropolis 1
            case 15: // Metropolis 2
            case 16: // Metropolis 3
                return Sonic2Music.METROPOLIS.id;
            case 17: // Sky Chase
                return Sonic2Music.SKY_CHASE.id;
            case 18: // Wing Fortress
                return Sonic2Music.WING_FORTRESS.id;
            case 19: // Death Egg
                return Sonic2Music.DEATH_EGG.id;
            default:
                // Fallback to original logic for unknown levels (e.g. 2P)
                int zoneIdx = rom.readByte(LEVEL_SELECT_ADDR + levelIdx * 2) & 0xFF;
                return rom.readByte(MUSIC_PLAYLIST_ADDR + zoneIdx) & 0xFF;
        }
    }

    @Override
    public Map<GameSound, Integer> getSoundMap() {
        Map<GameSound, Integer> map = new HashMap<>();
        map.put(GameSound.JUMP, Sonic2Sfx.JUMP.id);
        map.put(GameSound.RING_LEFT, Sonic2Sfx.RING_LEFT.id);
        map.put(GameSound.RING_RIGHT, Sonic2Sfx.RING_RIGHT.id);
        map.put(GameSound.RING_SPILL, Sonic2Sfx.RING_SPILL.id);
        map.put(GameSound.SPINDASH_CHARGE, Sonic2Sfx.SPINDASH_CHARGE.id);
        map.put(GameSound.SPINDASH_RELEASE, Sonic2Sfx.SPINDASH_RELEASE.id);
        map.put(GameSound.SKID, Sonic2Sfx.SKIDDING.id);
        map.put(GameSound.HURT, Sonic2Sfx.HURT.id);
        map.put(GameSound.HURT_SPIKE, Sonic2Sfx.HURT_BY_SPIKES.id);
        map.put(GameSound.DROWN, Sonic2Sfx.DROWN.id);
        map.put(GameSound.BADNIK_HIT, Sonic2Sfx.EXPLOSION.id);
        map.put(GameSound.CHECKPOINT, Sonic2Sfx.CHECKPOINT.id);
        map.put(GameSound.SPRING, Sonic2Sfx.SPRING.id);
        map.put(GameSound.BUMPER, Sonic2Sfx.BUMPER.id);
        map.put(GameSound.BONUS_BUMPER, Sonic2Sfx.BONUS_BUMPER.id);
        map.put(GameSound.LARGE_BUMPER, Sonic2Sfx.LARGE_BUMPER.id);
        map.put(GameSound.FLIPPER, Sonic2Sfx.FLIPPER.id);
        map.put(GameSound.CNZ_LAUNCH, Sonic2Sfx.CNZ_LAUNCH.id);
        map.put(GameSound.CNZ_ELEVATOR, Sonic2Sfx.CNZ_ELEVATOR.id);
        map.put(GameSound.ROLLING, Sonic2Sfx.ROLL.id);
        map.put(GameSound.ERROR, Sonic2Sfx.ERROR.id);
        map.put(GameSound.SPLASH, Sonic2Sfx.SPLASH.id);
        map.put(GameSound.AIR_DING, Sonic2Sfx.WATER_WARNING.id);
        map.put(GameSound.SLOW_SMASH, Sonic2Sfx.SLOW_SMASH.id);
        map.put(GameSound.CASINO_BONUS, Sonic2Sfx.CASINO_BONUS.id);
        map.put(GameSound.OIL_SLIDE, Sonic2Sfx.OIL_SLIDE.id);
        return map;
    }

    @Override
    public Level loadLevel(int levelIdx) throws IOException {
        ZoneAct zoneAct = getZoneAct(levelIdx);
        ensurePlacementHelpers();
        int characterPaletteAddr = getCharacterPaletteAddr();

        int[] levelPaletteInfo = getLevelPaletteInfo(zoneAct);
        int levelPalettesAddr = levelPaletteInfo[0];
        int levelPalettesSize = levelPaletteInfo[1];

        int mapAddr = getTilesAddr(zoneAct);
        int solidTileHeightsAddr = getSolidTileHeightsAddr();
        int solidTileWidthsAddr = getSolidTileWidthsAddr();
        int solidTileAngleAddr = getSolidTileAngleAddr();
        int levelBoundariesAddr = getLevelBoundariesAddr(zoneAct);
        List<ObjectSpawn> objectSpawns = objectPlacement.load(zoneAct);
        List<RingSpawn> ringSpawns = ringPlacement.load(zoneAct);
        var ringSpriteSheet = ringArt.load();

        // Check if this zone requires custom resource plan loading (e.g., HTZ overlays)
        LevelResourcePlan resourcePlan = Sonic2LevelResourcePlans.getPlanForZone(zoneAct.zone());

        if (resourcePlan != null) {
            // Zone uses custom resource plan with overlay composition
            return new Sonic2Level(rom, zoneAct.zone(), characterPaletteAddr, levelPalettesAddr, levelPalettesSize,
                    resourcePlan, mapAddr, solidTileHeightsAddr, solidTileWidthsAddr,
                    solidTileAngleAddr, objectSpawns, ringSpawns, ringSpriteSheet, levelBoundariesAddr);
        }

        // Standard loading via ROM directory tables
        int patternsAddr = getPatternsAddr(zoneAct);
        int chunksAddr = getChunksAddr(zoneAct);
        int blocksAddr = getBlocksAddr(zoneAct);
        int collisionAddr = getCollisionMapAddr(zoneAct);
        int altCollisionAddr = getAltCollisionMapAddr(zoneAct);

        return new Sonic2Level(rom, zoneAct.zone(), characterPaletteAddr, levelPalettesAddr, levelPalettesSize,
                patternsAddr,
                chunksAddr,
                blocksAddr, mapAddr, collisionAddr, altCollisionAddr, solidTileHeightsAddr, solidTileWidthsAddr,
                solidTileAngleAddr, objectSpawns, ringSpawns, ringSpriteSheet, levelBoundariesAddr);
    }

    @Override
    public int[] getBackgroundScroll(int levelIdx, int cameraX, int cameraY) {
        try {
            int zoneIdx = rom.readByte(LEVEL_SELECT_ADDR + levelIdx * 2) & 0xFF;
            int actIdx = rom.readByte(LEVEL_SELECT_ADDR + levelIdx * 2 + 1) & 0xFF;

            if (zoneIdx > 16) {
                return new int[] { 0, 0 };
            }

            int offset = rom.read16BitAddr(BG_SCROLL_TABLE_ADDR + zoneIdx * 2);
            int routineAddr = BG_SCROLL_TABLE_ADDR + offset;

            int d0 = cameraX;
            int d1 = cameraY;

            int ee08 = 0; // Y
            int ee0c = 0; // X

            switch (routineAddr) {
                case 0x00C2B8: // Clear/Reset
                case 0x00C2F4: // Clear/Reset
                    ee08 = 0;
                    ee0c = 0;
                    break;

                case 0x00C2E4: // Common parallax scaling
                    d0 = (d0 >>> 2) & 0xFFFF;
                    ee0c = d0;
                    d1 = (d1 >>> 3) & 0xFFFF;
                    ee08 = d1;
                    break;

                case 0x00C2F2:
                case 0x00C320:
                case 0x00C38A:
                    // RTS only. Using 0 as we start fresh.
                    break;

                case 0x00C322: // Scaled + constant, then clear base
                    d0 = (d0 >>> 3) & 0xFFFF;
                    d0 = (d0 + 0x0050) & 0xFFFF;
                    ee0c = d0;
                    ee08 = 0;
                    break;

                case 0x00C332: // Act-dependent baseline shift
                    ee08 = 0;
                    if (actIdx != 0) {
                        // Act 2: RTS, so use 0 (or previous? but we assume init so 0)
                    } else {
                        d0 = (d0 | 0x0003) & 0xFFFF;
                        d0 = (d0 - 0x0140) & 0xFFFF;
                        ee0c = d0;
                    }
                    break;

                case 0x00C364: // Clear specific bases
                    ee08 = 0;
                    ee0c = 0;
                    break;

                case 0x00C372: // MTZ - Multi-layer vertical bases
                    // Camera_BG_X_pos = cameraX >> 2
                    d0 = (d0 >>> 2) & 0xFFFF;
                    ee0c = d0;
                    // Camera_BG_Y_pos = cameraY >> 2 (NOT cameraY >> 3!)
                    // Note: Original also sets Camera_BG2_X_pos = cameraY >> 1 (separate var, not
                    // used here)
                    ee08 = (d1 >>> 2) & 0xFFFF;
                    break;

                case 0x00C3C6: // Clear primary bases
                    ee08 = 0;
                    ee0c = 0;
                    break;

                case 0x00C38C: // ARZ - Act-dependent Y offset
                    // Camera_BG_X_pos = (Camera_X_pos * $0119) >> 8
                    long mulRes = (d0 & 0xFFFFL) * 0x0119L;
                    ee0c = (int) ((mulRes >> 8) & 0xFFFF);

                    // Camera_BG_Y_pos is act-dependent
                    if (actIdx != 0) {
                        // Act 2: Camera_BG_Y_pos = (Camera_Y_pos - $E0) >> 1
                        ee08 = ((d1 - 0x00E0) >>> 1) & 0xFFFF;
                    } else {
                        // Act 1: Camera_BG_Y_pos = Camera_Y_pos - $180
                        ee08 = (d1 - 0x0180) & 0xFFFF;
                    }
                    break;

                default:
                    // Unknown routine, default to 0
                    break;
            }

            return new int[] { ee0c, ee08 };
        } catch (IOException e) {
            return new int[] { 0, 0 };
        }
    }

    @Override
    public SpriteArtSet loadPlayerSpriteArt(String characterCode) throws IOException {
        ensurePlacementHelpers();
        if (playerArt == null) {
            return null;
        }
        return playerArt.loadForCharacter(characterCode);
    }

    @Override
    public SpriteArtSet loadSpindashDustArt(String characterCode) throws IOException {
        ensurePlacementHelpers();
        if (dustArt == null) {
            return null;
        }
        return dustArt.loadForCharacter(characterCode);
    }

    @Override
    public ObjectArtData loadObjectArt() throws IOException {
        return loadObjectArt(-1);
    }

    @Override
    public ObjectArtData loadObjectArt(int zoneIndex) throws IOException {
        ensurePlacementHelpers();
        if (objectArt == null) {
            return null;
        }
        return objectArt.loadForZone(zoneIndex);
    }

    private Sonic2LevelAnimationManager levelAnimationManager;
    private Level levelAnimationLevel;
    private int levelAnimationZone = -1;

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

    private Sonic2LevelAnimationManager getOrCreateLevelAnimationManager(Level level, int zoneIndex) throws IOException {
        if (levelAnimationManager == null || levelAnimationLevel != level || levelAnimationZone != zoneIndex) {
            levelAnimationManager = new Sonic2LevelAnimationManager(rom, level, zoneIndex);
            levelAnimationLevel = level;
            levelAnimationZone = zoneIndex;
        }
        return levelAnimationManager;
    }

    private int getSolidTileHeightsAddr() {
        return SOLID_TILE_VERTICAL_MAP_ADDR;
    }

    private int getSolidTileWidthsAddr() {
        return SOLID_TILE_HORIZONTAL_MAP_ADDR;
    }

    private int getSolidTileAngleAddr() {
        return SOLID_TILE_ANGLE_ADDR;
    }

    @Override
    public boolean canRelocateLevels() {
        return true;
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

    private ZoneAct getZoneAct(int levelIdx) throws IOException {
        int zoneIdx = Byte.toUnsignedInt(rom.readByte(LEVEL_SELECT_ADDR + levelIdx * 2));
        int actIdx = Byte.toUnsignedInt(rom.readByte(LEVEL_SELECT_ADDR + levelIdx * 2 + 1));
        return new ZoneAct(zoneIdx, actIdx);
    }

    private void ensurePlacementHelpers() throws IOException {
        if (romReader == null) {
            romReader = RomByteReader.fromRom(rom);
        }
        if (objectPlacement == null) {
            objectPlacement = new Sonic2ObjectPlacement(romReader);
        }
        if (ringPlacement == null) {
            ringPlacement = new Sonic2RingPlacement(romReader);
        }
        if (ringArt == null) {
            ringArt = new Sonic2RingArt(rom, romReader);
        }
        if (playerArt == null) {
            playerArt = new Sonic2PlayerArt(romReader);
        }
        if (dustArt == null) {
            dustArt = new Sonic2DustArt(romReader);
        }
        if (objectArt == null) {
            objectArt = new Sonic2ObjectArt(rom, romReader);
        }
    }

    private int getDataAddress(int zoneIdx, int entryOffset) throws IOException {
        return rom.read32BitAddr(LEVEL_DATA_DIR + zoneIdx * LEVEL_DATA_DIR_ENTRY_SIZE + entryOffset);
    }

    private int getCharacterPaletteAddr() {
        return SONIC_TAILS_PALETTE_ADDR;
    }

    private int getLevelPalettesAddr(ZoneAct zoneAct) throws IOException {
        return getLevelPaletteInfo(zoneAct)[0];
    }

    /**
     * Returns an array containing { address, size } for the level palettes.
     */
    private int[] getLevelPaletteInfo(ZoneAct zoneAct) throws IOException {
        int dataAddr = getDataAddress(zoneAct.zone(), 8);
        int paletteIndex = dataAddr >> 24;

        int entryAddr = LEVEL_PALETTE_DIR + paletteIndex * 8;
        int address = rom.read32BitAddr(entryAddr);
        int countMinus1 = rom.read16BitAddr(entryAddr + 6);
        int size = (countMinus1 + 1) * 4;

        return new int[] { address, size };
    }

    private int getBlocksAddr(ZoneAct zoneAct) throws IOException {
        return getDataAddress(zoneAct.zone(), 8) & 0xFFFFFF;
    }

    private int getChunksAddr(ZoneAct zoneAct) throws IOException {
        return getDataAddress(zoneAct.zone(), 4) & 0xFFFFFF;
    }

    private int getPatternsAddr(ZoneAct zoneAct) throws IOException {
        return getDataAddress(zoneAct.zone(), 0) & 0xFFFFFF;
    }

    /*
     * FIXME: Level Layout, not 'tiles'
     */
    private int getTilesAddr(ZoneAct zoneAct) throws IOException {
        // The address at LEVEL_LAYOUT_DIR_ADDR_LOC points to another pointer table.
        // We read this base address first.
        int levelLayoutDirAddr = rom.read32BitAddr(LEVEL_LAYOUT_DIR_ADDR_LOC);

        // Then, we use the zone and act to find an offset within that table.
        // The table is structured with 4 bytes per zone, and 2 bytes per act.
        int levelOffsetAddr = levelLayoutDirAddr + (zoneAct.zone() * 4) + (zoneAct.act() * 2);

        // The value at this address is a 16-bit offset relative to the start of the
        // table.
        int levelOffset = rom.read16BitAddr(levelOffsetAddr);

        // The final address is the base address of the table plus the offset.
        return levelLayoutDirAddr + levelOffset;
    }

    private int getCollisionMapAddr(ZoneAct zoneAct) throws IOException {
        int zoneIdxLoc = COLLISION_LAYOUT_DIR_ADDR + zoneAct.zone() * 4;
        return rom.read32BitAddr(zoneIdxLoc);
    }

    private int getAltCollisionMapAddr(ZoneAct zoneAct) throws IOException {
        int zoneIdxLoc = ALT_COLLISION_LAYOUT_DIR_ADDR + zoneAct.zone() * 4;
        return rom.read32BitAddr(zoneIdxLoc);
    }

    private int getLevelBoundariesAddr(ZoneAct zoneAct) {
        // 8 bytes per entry. 2 entries per zone (usually 2 acts, sometimes 1).
        // It seems standard Sonic 2 stride is based on 2 acts per zone for this table.
        // Or it's a linear table indexed by (Zone * 2 + Act).
        return LEVEL_BOUNDARIES_ADDR + ((zoneAct.zone() * 2) + zoneAct.act()) * 8;
    }
}
