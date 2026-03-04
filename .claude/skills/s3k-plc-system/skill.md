# S3K Pattern Load Cue (PLC) System

> **Cross-game note:** The PLC binary format is shared across S1, S2, and S3K. The game-agnostic parser is `PlcParser` in `level.resources`. See the `plc-system` skill for the cross-game reference. This file covers S3K-specific PLC IDs, runtime loading, and GPU texture refresh.

Reference for the S3K Pattern Load Cue system: ROM format, runtime loading, GPU texture refresh, and zone event integration.

## PLC Table Format

### Offs_PLC Offset Table

Located at `Sonic3kConstants.OFFS_PLC_ADDR` (0x09238C). Contains 124 entries (IDs 0x00-0x7B), each a 2-byte word offset relative to the table start.

```
Offs_PLC:
    dc.w PLC_00-Offs_PLC    ; offset to PLC_00 data
    dc.w PLC_01-Offs_PLC    ; offset to PLC_01 data
    ...
```

### Per-PLC Data Block

Each PLC data block has:
- **Header word** (2 bytes): count-1 (for `dbf` loop). Value 0xFFFF = empty PLC.
- **Entries** (6 bytes each): `dc.l nemesis_rom_addr`, `dc.w vram_dest_bytes`

The VRAM destination word stores `tile_index * 32` (byte offset in VRAM). To recover the tile index: `tileIndex = vramDest / 32`.

### Example: PLC_0B (AIZ1 zone art)

```
PLC_0B: plrlistheader              ; dc.w 5 (count-1 = 5, so 6 entries)
    plreq ArtTile_AIZSwingVine,      ArtNem_AIZSwingVine
    plreq ArtTile_AIZSlideRope,      ArtNem_AIZSlideRope
    plreq ArtTile_AIZMisc1,          ArtNem_AIZMisc1
    plreq ArtTile_AIZFallingLog,     ArtNem_AIZFallingLog
    plreq ArtTile_Bubbles,           ArtNem_Bubbles
    plreq ArtTile_AIZFloatingPlatform, ArtNem_AIZCorkFloor
```

Each `plreq` expands to: `dc.l ArtNem_xxx` (4 bytes), `dc.w ArtTile_xxx * $20` (2 bytes).

## Load_PLC vs Load_PLC_2

| Routine | Behavior | Use Case |
|---------|----------|----------|
| `Load_PLC` | Appends entries to decompression queue | Zone art, boss art, act transitions |
| `Load_PLC_2` | Clears queue first, then loads | Level startup (character PLC), scene changes |

Both take PLC ID in `d0`, resolve through the offset table, and queue Nemesis decompressions for VBlank processing.

## PLC ID Catalog

### Universal (0x00-0x09)
| ID | Contents | Notes |
|----|----------|-------|
| 0x00 | Sonic life icon, Ring/HUD, Starpost, Monitors | Unused |
| 0x01 | Sonic life icon, Monitors, Ring/HUD, Starpost, Spikes/Springs | Sonic character PLC |
| 0x02 | Explosion, Squirrel, Flicky | Unused |
| 0x03 | Game Over text | |
| 0x04 | Signpost art | Unused |
| 0x05 | Knuckles life icon, Monitors, Ring/HUD, Starpost, Spikes/Springs | Knuckles character PLC |
| 0x06 | 2P mode art | |
| 0x07 | Tails life icon, Monitors, Ring/HUD, Starpost, Spikes/Springs | Tails character PLC |
| 0x08 | Monitor art only | |
| 0x09 | Monitor art only | (duplicate of 0x08) |

### Zone Art (0x0A-0x41)
| ID Range | Zone | Notes |
|----------|------|-------|
| 0x0A | AIZ intro sprites | Wave/spray art |
| 0x0B | AIZ1 | Vine, rope, misc, log, bubbles, cork floor |
| 0x0C-0x0D | AIZ2 | Misc2, vine, tree, bubbles, button, cork floor 2 |
| 0x0E-0x0F | HCZ1 | Part 1 + Part 2 |
| 0x10-0x11 | HCZ2 | Part 1 + Part 2 |
| 0x12-0x13 | MGZ1 | |
| 0x14-0x15 | MGZ2 | |
| 0x16-0x19 | CNZ | Shared across acts |
| 0x1A-0x1B | FBZ1 | |
| 0x1C-0x1D | FBZ2 | |
| 0x1E-0x1F | ICZ1 | |
| 0x20-0x21 | ICZ2 | |
| 0x22-0x23 | LBZ1 | |
| 0x24-0x25 | LBZ2 | |
| 0x26-0x29 | MHZ | Shared across acts |
| 0x2A-0x2B | SOZ1 | |
| 0x2C-0x2D | SOZ2 | |
| 0x2E-0x2F | LRZ1 | |
| 0x30-0x31 | LRZ2 | |
| 0x32-0x35 | SSZ | Shared across acts |
| 0x36-0x37 | DEZ1 | |
| 0x38-0x39 | DEZ2 | |
| 0x3A-0x3F | DDZ | Shared across acts + endings |
| 0x40-0x41 | Ending | Blank |

### Bonus/Special (0x42-0x51)
| ID | Contents |
|----|----------|
| 0x42-0x46 | ALZ, BPZ, DPZ, CGZ, EMZ (competition zones) |
| 0x47 | Gumball bonus |
| 0x48-0x4B | HPZ |
| 0x4C-0x4D | DEZ3 |
| 0x4E-0x4F | Spikes and springs (unused) |
| 0x50 | Glowing bonus |
| 0x51 | Slots bonus |
| 0x52 | Miles (Tails) life icon |

### Boss Art (0x53-0x7B)
| ID Range | Boss |
|----------|------|
| 0x53-0x5A | AIZ1 boss |
| 0x5B | HCZ1 boss |
| 0x5C-0x5D | CNZ1 boss |
| 0x5E | FBZ1 boss (unused) |
| 0x5F | ICZ1 boss |
| 0x60 | LBZ1 Eggman |
| 0x61 | Boss explosion (unused) |
| 0x62-0x6A | FBZ2 subboss |
| 0x6B | AIZ2 boss |
| 0x6C | HCZ2 boss |
| 0x6D | MGZ2 boss |
| 0x6E | CNZ2 boss |
| 0x6F | FBZ2 end boss |
| 0x70 | ICZ2 boss |
| 0x71 | LBZ2 final boss 1 |
| 0x72-0x76 | DEZ2 boss |
| 0x77 | LBZ2 Eggman |
| 0x78-0x7B | Boss ship and explosion |

## Runtime PLC Loading Pattern

### Level-Load PLCs (via LevelResourcePlan)

During level load, PLCs are converted to `LoadOp` entries and applied as pattern overlays:

```java
// In Sonic3k.appendPlcPatternOps():
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, plcIndex);
List<LoadOp> ops = Sonic3kPlcLoader.toPatternOps(plc);
for (LoadOp op : ops) {
    planBuilder.addPatternOp(op);
}
```

### Runtime PLCs (zone events, act transitions)

During gameplay, PLCs are applied directly to the level and GPU textures are refreshed:

```java
// In Sonic3kZoneEvents.applyPlc():
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, plcId);
List<TileRange> modified = Sonic3kPlcLoader.applyToLevel(plc, level);
Sonic3kPlcLoader.refreshAffectedRenderers(modified, levelManager);
```

### Pre-Decompression (avoiding frame hitches)

For transitions that must be seamless (AIZ intro), pre-decompress during level load:

```java
// During level load:
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, 0x0B);
List<PreDecompressedEntry> cached = Sonic3kPlcLoader.preDecompress(plc);

// Later at transition frame:
List<TileRange> modified = Sonic3kPlcLoader.applyPreDecompressed(cached, level);
Sonic3kPlcLoader.refreshAffectedRenderers(modified, levelManager);
```

## Standalone Art Loading from PLCs (Object/Boss Art)

Boss and object art is often delivered via PLCs but must NOT be written into the level's shared pattern buffer, because PLC tile ranges can overlap (e.g., boss fire art at 0x0482 overwrites spike/spring art at 0x0494). Use standalone decompression via the shared `PlcParser` API:

```java
// Parse PLC to discover art ROM addresses and tile destinations
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, PLC_ID);

// Decompress all entries into standalone Pattern[] arrays (no level buffer writes)
List<Pattern[]> artArrays = PlcParser.decompressAll(rom, plc);

// Pair each entry's patterns with its ROM-parsed mappings
ObjectSpriteSheet sheet = new ObjectSpriteSheet(
    artArrays.get(entryIndex),
    S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr),
    paletteIndex, 1);
```

**Benefits over hardcoded art addresses:**
- PLC ID is the single source of truth — no need for separate `ART_NEM_*` and `ARTTILE_*` constants
- Only mapping addresses and palette indices need constants (these aren't in PLCs)
- Avoids VRAM overlap conflicts that happen with level buffer application
- The ROM restores overwritten tiles via `Load_PLC(PLC_Monitors)` after boss defeat; standalone arrays make this unnecessary

**Example: AIZ miniboss (PLC 0x5A)**
```java
// PLC 0x5A has 4 entries: main boss, small debris, flame, boss explosion
PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, Sonic3kConstants.PLC_AIZ_MINIBOSS);
List<Pattern[]> decompressed = PlcParser.decompressAll(rom, plc);

registerSheet(KEY_MAIN,  new ObjectSpriteSheet(decompressed.get(0), mainMappings,  1, 1));
registerSheet(KEY_SMALL, new ObjectSpriteSheet(decompressed.get(1), smallMappings, 0, 1));
registerSheet(KEY_FLAME, new ObjectSpriteSheet(decompressed.get(2), flameMappings, 1, 1));
```

**When to use standalone vs level buffer:**

| Use Case | Method | Writes to Level Buffer? |
|----------|--------|------------------------|
| Level init PLCs (zone art) | `toPatternOps()` → `LevelResourcePlan` | Yes |
| Runtime act transitions | `applyToLevel()` + `refreshAffectedRenderers()` | Yes |
| Boss/object art | `PlcParser.decompressAll()` → `ObjectSpriteSheet` | No |
| Hitch-free preload | `preDecompress()` → `applyPreDecompressed()` | Yes (deferred) |

## Integration with Zone Event Handlers

Zone event handlers inherit `applyPlc(int plcId)` from `Sonic3kZoneEvents`. Future zone handlers become one-liners:

```java
// HCZ act 2 transition:
applyPlc(0x10);
applyPlc(0x11);

// AIZ2 boss arena:
applyPlc(0x6B);
```

## GPU Texture Refresh

After PLC application, object renderer GPU textures must be re-uploaded for any renderers whose backing Pattern data was modified. The system uses tile-range overlap detection:

1. `Sonic3kObjectArtProvider` tracks which level tile indices each sheet depends on (via `registerLevelArtSheet`)
2. `Sonic3kPlcLoader.applyToLevel()` returns the tile ranges it modified
3. `Sonic3kPlcLoader.refreshAffectedRenderers()` finds overlapping renderers and calls `updatePatternRange()`

## Key Engine Files

| File | Purpose |
|------|---------|
| `Sonic3kPlcLoader.java` | PLC parsing, application, pre-decompression, GPU refresh |
| `Sonic3kConstants.java` | `OFFS_PLC_ADDR`, `PLC_ENTRY_SIZE`, tile index constants |
| `Sonic3kObjectArtProvider.java` | Tile range tracking, `getAffectedRendererKeys()` |
| `Sonic3kZoneEvents.java` | `applyPlc()` convenience method for zone handlers |
| `AizIntroTerrainSwap.java` | Runtime PLC application example (PLC 0x0B) |
| `Sonic3k.java` | Level-load PLC application via `appendPlcPatternOps()` |
| `level/resources/PlcParser.java` | Shared PLC format parser (game-agnostic) |
