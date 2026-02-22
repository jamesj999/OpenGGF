---
name: plc-system
description: Cross-game Pattern Load Cue (PLC) system reference — shared binary format, PlcParser API, per-game integration
---

# Cross-Game Pattern Load Cue (PLC) System

Reference for the shared PLC binary format used across S1, S2, and S3K, and the `PlcParser` utility.

## Shared Binary Format

All three Sonic games use an identical PLC table format:

### Offset Table
A list of 2-byte word offsets, one per PLC ID, relative to the table start.

### Per-PLC Data Block
- **Header word** (2 bytes): count-1 (for `dbf` loop). Negative (bit 15 set) = empty PLC.
- **Entries** (6 bytes each): `dc.l nemesis_rom_addr` (4 bytes), `dc.w vram_dest_bytes` (2 bytes)

VRAM destination stores `tile_index * 32`. To recover tile index: `vramDest / 32`.

## Per-Game Table Addresses

| Game | Constant | Address | Entry Count |
|------|----------|---------|-------------|
| S1 | `Sonic1Constants.ART_LOAD_CUES_ADDR` | `0x01DD86` | ~16 PLC IDs |
| S2 | `Sonic2Constants.ART_LOAD_CUES_ADDR` | TBD (see Future Path below) | ~67 PLC IDs |
| S3K | `Sonic3kConstants.OFFS_PLC_ADDR` | `0x09238C` | 124 PLC IDs (0x00-0x7B) |

## PlcParser API

Located in `resources.com.openggf.level.PlcParser`.

### Records
- `PlcParser.PlcEntry(int romAddr, int tileIndex)` -- single PLC entry
- `PlcParser.PlcDefinition(int plcId, List<PlcEntry> entries)` -- parsed PLC with all entries

### Methods
```java
// Parse a PLC definition from any game's ROM
PlcParser.PlcDefinition parse(Rom rom, int tableAddr, int plcId)

// Convert entries to LoadOps for LevelResourcePlan
List<LoadOp> toPatternOps(PlcParser.PlcDefinition definition)
```

## Per-Game Integration

### S1 (Level Init)
PLCs are parsed during level loading in `Sonic1.readPatternLoadCues()` via `PlcParser.parse()`. Both primary and secondary ArtLoadCues are loaded and passed to `Sonic1Level.loadPatterns()`.

### S3K (Level Init + Runtime)
- **Level load:** PLCs converted to `LoadOp` entries via `Sonic3kPlcLoader.toPatternOps()` -> `LevelResourcePlan`
- **Runtime:** Zone events call `Sonic3kPlcLoader.applyToLevel()` for act transitions and boss art
- **Pre-decompression:** `Sonic3kPlcLoader.preDecompress()` for hitch-free transitions (AIZ intro)
- See `s3k-plc-system` skill for S3K-specific PLC ID catalog and runtime patterns

### S2 (Future)
S2 has ArtLoadCues in ROM (~67 PLC IDs). Currently hardcoded in `Sonic2ObjectArt` with `ART_NEM_*` constants. Future refactor will use `PlcParser` -- see S2 PLC future path documentation.

## Key Files

| File | Purpose |
|------|---------|
| `level/resources/PlcParser.java` | Shared PLC format parser |
| `game/sonic3k/Sonic3kPlcLoader.java` | S3K-specific PLC application, GPU refresh |
| `game/sonic1/Sonic1.java` | S1 PLC parsing via `PlcParser` |
| `game/sonic1/Sonic1Level.java` | S1 pattern loading from PLC entries |
