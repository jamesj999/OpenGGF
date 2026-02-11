---
name: s1disasm-guide
description: "This skill provides guidance on finding, identifying, and interpreting items in the Sonic 1 disassembly (`docs/s1disasm/`)."
---
# s1disasm Navigation Guide

This skill provides guidance on finding, identifying, and interpreting items in the Sonic 1 disassembly (`docs/s1disasm/`).

## Directory Structure

The disassembly is organized into these major directories:

| Directory | Contents | Notes |
|-----------|----------|-------|
| `_incObj/` | Object code | One file per object: `HEX Name.asm` (e.g., `1F Crabmeat.asm`) |
| `_anim/` | Animation scripts | Per-object animation data (e.g., `Crabmeat.asm`) |
| `_maps/` | Sprite mappings | Per-object frame definitions (e.g., `Crabmeat.asm`) |
| `artnem/` | Nemesis-compressed art | Sprite and tile art (`.nem` extension or `.bin`) |
| `artkos/` | Kosinski-compressed art | Level tile patterns |
| `artunc/` | Uncompressed art | Raw tile data (`.bin`) |
| `collide/` | Collision data | Height arrays and per-zone collision indices |
| `levels/` | Level layouts | Uncompressed FG/BG layouts (e.g., `ghz1.bin`) |
| `map16/` | 16x16 block mappings | Per-zone chunk definitions |
| `map256/` | 256x256 metatile mappings | Per-zone block definitions |
| `palette/` | Palette files | Uncompressed `.bin` files (32 bytes = 16 colors) |
| `objpos/` | Object placement data | Per-act object positions |
| `demodata/` | Demo recordings | Input playback data |
| `misc/` | Miscellaneous data | Credits, ending data, etc. |

### Key Source Files

| File | Contents |
|------|----------|
| `sonic.asm` | Main assembly source (equivalent of S2's `s2.asm`) |
| `Constants.asm` | RAM addresses, object field offsets, sound IDs |
| `Macros.asm` | Assembly macros |
| `MacroSetup.asm` | Macro setup and configuration |
| `s1.sounddriver.asm` | SMPS Z80 sound driver |

## Compression Types

| Extension | Type | Tool Flag | Description |
|-----------|------|-----------|-------------|
| `.nem` / `.bin` (in `artnem/`) | Nemesis | `nem` | Sprite art, HUD graphics |
| `.kos` / `.bin` (in `artkos/`) | Kosinski | `kos` | Level tiles |
| `.bin` (in `artunc/`) | Uncompressed | `bin` | Raw tile data, palettes |
| `.eni` | Enigma | `eni` | Block mappings (if present) |

**Note:** Sonic 1 disassembly often uses `.bin` extension for all types. The directory determines the compression: `artnem/` = Nemesis, `artkos/` = Kosinski, `artunc/` = Uncompressed.

## Finding Items with RomOffsetFinder

**Important:** All RomOffsetFinder commands for Sonic 1 require the `--game s1` flag.

### Search Command (Most Common)

```bash
# Search by partial name
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Crabmeat" -q

# Search for zone-specific items
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search GHZ" -q

# Search for palettes
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Pal_" -q
```

### List Command

```bash
# List all Nemesis-compressed items
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 list nem" -q

# List all uncompressed items
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 list bin" -q

# List all compression types
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 list" -q
```

### Verify and Export

```bash
# Verify a single offset
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 verify Nem_Crabmeat" -q

# Batch verify all Nemesis items
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 verify-batch nem" -q

# Export as Java constants
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 export nem ART_" -q
```

## Label Naming Conventions

Sonic 1 uses different label prefixes from Sonic 2:

| S1 Prefix | S2 Equivalent | Meaning | Example |
|-----------|---------------|---------|---------|
| `Nem_` | `ArtNem_` | Nemesis art | `Nem_Crabmeat`, `Nem_GHZ_1st` |
| `Kos_` | `ArtKos_` | Kosinski art | `Kos_GHZ`, `Kos_LZ` |
| `Unc_` | `ArtUnc_` | Uncompressed art | `Unc_Sonic` |
| `Map_` | `MapUnc_` | Sprite mappings | `Map_Crabmeat`, `Map_Sonic` |
| `Ani_` | (inline) | Animation scripts | `Ani_Crabmeat`, `Ani_Sonic` |
| `Blk16_` | (16x16 mappings) | 16x16 blocks | `Blk16_GHZ` |
| `Blk256_` | (128x128 mappings) | 256x256 metatiles | `Blk256_GHZ` |
| `Pal_` | `Pal_` | Palette (same as S2) | `Pal_GHZ`, `Pal_Sonic` |
| `PLC_` | `PLC_` | Pattern Load Cue | `PLC_GHZ`, `PLC_Main` |
| `Obj_` / `ObjXX` | `Obj_` | Object code entry | `Obj1F` (Crabmeat) |
| `SonAni_` | (inline) | Sonic animation ID | `SonAni_Walk`, `SonAni_Roll` |
| `DPLC_` | (inline) | Dynamic Pattern Load Cue | `DPLC_Sonic` |

## Zone Abbreviations

| Abbrev | Full Name | Zone ID |
|--------|-----------|---------|
| GHZ | Green Hill Zone | 0x00 |
| LZ | Labyrinth Zone | 0x01 |
| MZ | Marble Zone | 0x02 |
| SLZ | Star Light Zone | 0x03 |
| SYZ | Spring Yard Zone | 0x04 |
| SBZ | Scrap Brain Zone | 0x05 |
| FZ | Final Zone (Ending) | 0x06 |
| SS | Special Stage | 0x07 |

## Object File Organization

Unlike Sonic 2 where objects are defined inline in `s2.asm`, Sonic 1 has **separate files per object** in `_incObj/`:

### File Naming Pattern
```
_incObj/HEX Name.asm
```
Where `HEX` is the 2-digit hexadecimal object ID.

Examples:
- `_incObj/1F Crabmeat.asm` - Crabmeat badnik (ID 0x1F)
- `_incObj/22 Buzz Bomber.asm` - Buzz Bomber badnik (ID 0x22)
- `_incObj/41 Springs.asm` - Springs (ID 0x41)
- `_incObj/3D Boss - Green Hill (part 1).asm` - GHZ Boss

### Multi-Part Objects
Some objects span multiple files:
- `_incObj/11 Bridge (part 1).asm`, `_incObj/11 Bridge (part 2).asm`, etc.
- `_incObj/3D Boss - Green Hill (part 1).asm`, `_incObj/3D Boss - Green Hill (part 2).asm`

### Related Files

Each object typically has corresponding files in:
- `_anim/Name.asm` - Animation script definitions
- `_maps/Name.asm` - Sprite frame mappings (5-byte piece format)

## Object System Reference

### Object RAM Layout

S1 uses `$FFFFCF00` as `Object_RAM` base (vs S2's `$FFB000`).

### Object Status Table Offsets

S1 uses named field macros that differ from S2:

| S1 Field | S2 Equivalent | Offset | Size | Description |
|----------|---------------|--------|------|-------------|
| `obID` | `id` | 0x00 | byte | Object ID |
| `obRender` | `render_flags` | 0x01 | byte | Render flags |
| `obGfx` | `art_tile` | 0x02 | word | Art tile base |
| `obMap` | `mappings` | 0x04 | long | Mappings pointer |
| `obX` | `x_pos` | 0x08 | word+byte | X position (16.8 fixed) |
| `obScreenY` | `y_screen` | 0x0A | word | Screen-relative Y (unused in some objects) |
| `obY` | `y_pos` | 0x0C | word+byte | Y position (16.8 fixed) |
| `obVelX` | `x_vel` | 0x10 | word | X velocity |
| `obVelY` | `y_vel` | 0x12 | word | Y velocity |
| `obInertia` | `inertia` | 0x14 | word | Ground speed |
| `obHeight` | `y_radius` | 0x16 | byte | Y radius |
| `obWidth` | `x_radius` | 0x17 | byte | X radius |
| `obPriority` | `priority` | 0x18 | word | Display priority |
| `obActWid` | `width_pixels` | 0x19 | byte | Active width for display |
| `obFrame` | `mapping_frame` | 0x1A | byte | Current mapping frame |
| `obAnim` | `anim` | 0x1C | byte | Current animation |
| `obPrevAni` | `prev_anim` | 0x1D | byte | Previous animation |
| `obAniFrame` | `anim_frame` | 0x1E | byte | Animation frame index |
| `obTimeFrame` | `anim_frame_duration` | 0x1F | byte | Frame duration counter |
| `obColType` | `collision_flags` | 0x20 | byte | Collision type + size |
| `obColProp` | `collision_property` | 0x21 | byte | Collision property (e.g., hit count) |
| `obStatus` | `status` | 0x22 | byte | Status bits |
| `obRespawnNo` | `respawn_index` | 0x23 | byte | Respawn table index |
| `obRoutine` | `routine` | 0x24 | byte | Current routine |
| `ob2ndRout` | `routine_secondary` | 0x25 | byte | Secondary routine |
| `obAngle` | `angle` | 0x26 | byte | Angle |
| `obSubtype` | `subtype` | 0x28 | byte | Object subtype |

### Object Routine Pattern

```asm
ObjXX:                              ; Object entry point
    moveq   #0,d0
    move.b  obRoutine(a0),d0
    move.w  ObjXX_Index(pc,d0.w),d1
    jmp     ObjXX_Index(pc,d1.w)

ObjXX_Index:
    dc.w ObjXX_Init - ObjXX_Index   ; routine 0
    dc.w ObjXX_Main - ObjXX_Index   ; routine 2
    dc.w ObjXX_Delete - ObjXX_Index ; routine 4
```

## Sprite Mappings Format (S1 Format)

S1 uses a **5-byte per piece** format (S2 uses 6 bytes):

```
Frame header:
  dc.b  <piece_count>              ; Single byte (S2 uses dc.w)

Per piece (5 bytes):
  dc.b  <y_offset>                 ; Signed byte
  dc.b  <size>                     ; bits 0-1: width-1, bits 2-3: height-1
  dc.b  <pattern_hi>               ; High byte of pattern word
  dc.b  <pattern_lo>               ; Low byte of pattern word
  dc.b  <x_offset>                 ; Signed byte (S2 uses dc.w for X)
```

**Key difference from S2:** X offset is a single signed byte (-128 to +127), not a word.

Size byte encoding (same as S2):
- `0x00` = 1x1 (8x8 pixels)
- `0x05` = 2x2 (16x16 pixels)
- `0x0F` = 4x4 (32x32 pixels)

## Level Data Differences

| Aspect | Sonic 1 | Sonic 2 |
|--------|---------|---------|
| Block size | 256x256 pixels | 128x128 pixels |
| Block mappings | `map256/` dir | `mappings/128x128/` dir |
| Chunk mappings | `map16/` dir | `mappings/16x16/` dir |
| Level layouts | Uncompressed `levels/*.bin` | Kosinski compressed |
| Layout index | Word offsets from base | Word offsets from base |
| Object positions | `objpos/*.bin` | Inline in main ASM |
| Collision indices | Per-zone `.bin` in `collide/` | Per-zone `.bin` in `collision/` |

## Common Search Patterns

### Finding Badnik Art

```bash
# Search for specific badnik
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Crabmeat" -q

# Find all Nemesis art
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Nem_" -q
```

### Finding Zone Data

```bash
# All GHZ resources
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search GHZ" -q

# Zone palette
mvn exec:java -Dexec.mainClass="uk.co.jamesj999.sonic.tools.disasm.RomOffsetFinder" -Dexec.args="--game s1 search Pal_LZ" -q
```

### Finding Object Code

Look directly in the disassembly files:
```bash
# Crabmeat object code
cat docs/s1disasm/_incObj/"1F Crabmeat.asm"

# Crabmeat animation
cat docs/s1disasm/_anim/Crabmeat.asm

# Crabmeat mappings
cat docs/s1disasm/_maps/Crabmeat.asm
```

## RAM Address Reference

Key RAM addresses for understanding Sonic 1 disassembly:

| Address | Name | Description |
|---------|------|-------------|
| $FFFFCF00 | Object_RAM | Object status table base |
| $FFFFF700 | Camera_X_pos | Camera X position |
| $FFFFF704 | Camera_Y_pos | Camera Y position |
| $FFFFFB00 | v_lvllayout | Level layout pointer |
| $FFFFF600 | v_gamemode | Current game mode |
| $FFFFF628 | v_rings | Ring count |
| $FE00 | v_palette | Palette RAM |

## Quick Reference Card

```
Search:  --game s1 search <pattern>      Find items by name
List:    --game s1 list [type]           List items (nem/kos/bin)
Test:    --game s1 test <offset> <type>  Test decompression
Verify:  --game s1 verify <label>        Check calculated offset
Export:  --game s1 export <type> [prefix] Generate Java constants
```

## Troubleshooting

### Address Verification

**Critical:** Addresses derived from s1disasm labels may NOT match the compiled ROM binary exactly. The disassembly source uses labels that resolve to different offsets depending on assembly options and ROM revision. Always verify addresses by searching the actual ROM binary for known data patterns. See MEMORY.md for verified addresses.

### "Item not found"

- Check spelling and case sensitivity
- Try partial names (e.g., "Crab" instead of "Crabmeat")
- S1 labels use `Nem_` prefix, not `ArtNem_`
- Use `list` command to see all items of a type

### Compression Detection

- Files in `artnem/` are Nemesis regardless of `.bin` extension
- Files in `artkos/` are Kosinski regardless of extension
- Files in `artunc/` are raw uncompressed data

