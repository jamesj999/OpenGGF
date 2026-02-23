# s2disasm Navigation Guide

This skill provides guidance on finding, identifying, and interpreting items in the Sonic 2 disassembly (`docs/s2disasm/`).

## Directory Structure

The disassembly is organized into these major directories:

| Directory | Contents | Notes |
|-----------|----------|-------|
| `art/` | Graphics data | Compressed sprite/tile art |
| `art/kosinski/` | Kosinski-compressed level tiles | Zone pattern data |
| `art/nemesis/` | Nemesis-compressed sprite art | Objects, badniks, HUD |
| `art/enigma/` | Enigma-compressed mappings | Block mappings |
| `art/palettes/` | Uncompressed palette files | .bin files, 32 bytes each |
| `collision/` | Collision data | Height arrays, collision indices |
| `level/` | Level layouts | Foreground/background layouts |
| `mappings/` | Tile arrangement data | 16x16 and 128x128 mappings |
| `mappings/16x16/` | Block (chunk) mappings | .bin files |
| `mappings/128x128/` | Metatile (block) mappings | .bin files |
| `mappings/sprite/` | Sprite frame mappings | Object animation frames |
| `sound/` | Audio data | SMPS music and SFX |
| `startpos/` | Player start positions | Per-zone starting coordinates |
| `misc/` | Miscellaneous data | Demo data, credits, etc. |

## Compression Types

| Extension | Type | Tool Flag | Description |
|-----------|------|-----------|-------------|
| `.nem` | Nemesis | `nem` | Sprite art, HUD graphics |
| `.kos` | Kosinski | `kos` | Level tiles, large graphics |
| `.eni` | Enigma | `eni` | Block/chunk mappings |
| `.sax` | Saxman | `sax` | Special stage data |
| `.bin` | Uncompressed | `bin` | Palettes, collision data |

### Compression Selection Guidelines

- **Nemesis**: Best for sprite art with many repeated patterns
- **Kosinski**: Best for level tiles, larger data sets
- **Enigma**: Optimized for tile mappings with incremental pattern IDs
- **Saxman**: Used specifically for special stage track data

## Finding Items with RomOffsetFinder

The RomOffsetFinder tool searches the disassembly and calculates ROM offsets:

### Search Command (Most Common)

```bash
# Search by partial name - finds labels and calculates ROM offset
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Ring" -q

# Search for zone-specific items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search EHZ" -q

# Search for palettes
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Pal_" -q
```

### List Command

```bash
# List all Nemesis-compressed items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="list nem" -q

# List all palettes (uncompressed)
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="list bin" -q

# List all compression types
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="list" -q
```

### Test Decompression

```bash
# Test if data at offset is Nemesis-compressed
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="test 0xDD8CE nem" -q

# Auto-detect compression type
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="test 0x3000 auto" -q
```

### Verify and Export

```bash
# Verify a single offset
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="verify ArtNem_SpecialHUD" -q

# Batch verify all Nemesis items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="verify-batch nem" -q

# Export as Java constants
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="export nem ART_" -q
```

### Search ROM Binary

Use `search-rom` to find inline assembly data (pointer tables, animation scripts, `dc.w`/`dc.b` directives) that have no binary file — the `search` and `find` commands only work with `binclude` items.

```bash
# Search for known hex byte pattern (spaces optional)
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search-rom \"07 72 73 26 15 08\"" -q

# Restrict search to a specific ROM range
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search-rom \"0002\" 0x28000 0x29000" -q
```

## Label Naming Conventions

Labels follow consistent prefixes that indicate data type and compression:

| Prefix | Meaning | Example |
|--------|---------|---------|
| `ArtNem_` | Nemesis art | `ArtNem_Buzzer` |
| `ArtKos_` | Kosinski art | `ArtKos_EHZ` |
| `ArtUnc_` | Uncompressed art | `ArtUnc_Ings` |
| `Pal_` | Palette | `Pal_EHZ` |
| `MapEni_` | Enigma mappings | `MapEni_EHZ` |
| `MapUnc_` | Uncompressed mappings | `MapUnc_Sonic` |
| `Obj_` | Object code | `Obj_Monitor` |
| `loc_` | Code location | `loc_1B4A2` |
| `off_` | Offset table | `off_Rings` |
| `word_` | Word data | `word_1B4A0` |
| `byte_` | Byte data | `byte_1B4A1` |
| `PLC_` | Pattern Load Cue | `PLC_EHZ` |

## Zone Abbreviations

| Abbrev | Full Name | Zone ID |
|--------|-----------|---------|
| EHZ | Emerald Hill Zone | 0x00 |
| CPZ | Chemical Plant Zone | 0x0D |
| ARZ | Aquatic Ruin Zone | 0x0F |
| CNZ | Casino Night Zone | 0x0C |
| HTZ | Hill Top Zone | 0x07 |
| MCZ | Mystic Cave Zone | 0x0B |
| OOZ | Oil Ocean Zone | 0x0A |
| MTZ | Metropolis Zone | 0x04 |
| SCZ | Sky Chase Zone | 0x10 |
| WFZ | Wing Fortress Zone | 0x11 |
| DEZ | Death Egg Zone | 0x12 |
| HPZ | Hidden Palace Zone | 0x02 (unused) |
| GHZ | Green Hill Zone | 0x16 (S1 leftover) |

## File Parsing Patterns

### BINCLUDE Directive

Used to include binary data files:

```asm
ArtNem_Buzzer:  BINCLUDE "art/nemesis/Buzzer.bin"
```

Format: `LABEL: BINCLUDE "path/to/file.ext"`

### Palette Macro

Palettes use a special macro format:

```asm
Pal_EHZ:    palette Emerald Hill Zone.bin
```

These are located in `art/palettes/` as uncompressed .bin files (32 bytes each = 16 colors × 2 bytes).

### Include Directives

Assembly includes for code:

```asm
    include "s2.macrosetup.asm"
```

## Object System Reference

### Object Status Table Offsets

Common offsets used in object code (`a0` = object pointer):

| Offset | Name | Size | Description |
|--------|------|------|-------------|
| 0x00 | id | long | Object ID |
| 0x08 | x_pos | word | X position (center) |
| 0x0C | y_pos | word | Y position (center) |
| 0x10 | x_vel | word | X velocity |
| 0x12 | y_vel | word | Y velocity |
| 0x22 | mapping_frame | byte | Current frame |
| 0x24 | routine | byte | Current routine |
| 0x28 | subtype | byte | Object subtype |

### Object Routine Pattern

Objects typically follow this routine structure:

```asm
Obj_Example:
    moveq   #0,d0
    move.b  routine(a0),d0
    move.w  Obj_Example_Index(pc,d0.w),d1
    jmp     Obj_Example_Index(pc,d1.w)

Obj_Example_Index:
    dc.w Obj_Example_Init - Obj_Example_Index   ; routine 0
    dc.w Obj_Example_Main - Obj_Example_Index   ; routine 2
```

## Sprite Mappings Format

Sprite mappings define how patterns are arranged into frames:

```
Frame header:
  dc.w  <piece_count>

Per piece:
  dc.b  <y_offset>
  dc.b  <size>       ; bits 0-1: width, bits 2-3: height (in 8px units - 1)
  dc.w  <pattern>    ; pattern index + flags (priority, palette, flip)
  dc.w  <x_offset>
```

Size byte encoding:
- `0x00` = 1×1 (8×8 pixels)
- `0x05` = 2×2 (16×16 pixels)
- `0x0F` = 4×4 (32×32 pixels)

## Resource Overlay System

Some zones share base resources with overlays. HTZ is the primary example:

| Resource | Base | Overlay | Offset |
|----------|------|---------|--------|
| Patterns | EHZ_HTZ.bin | HTZ_Supp.bin | 0x3F80 |
| Blocks | EHZ.bin | HTZ.bin | 0x0980 |
| Chunks | EHZ_HTZ.bin | (shared) | - |
| Collision | EHZ and HTZ*.bin | (shared) | - |

The overlay is applied at the specified byte offset after decompressing the base data.

## Common Search Patterns

### Finding Badnik Art

```bash
# Search for specific badnik
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Buzzer" -q

# Find all badnik mappings
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search MapUnc_" -q
```

### Finding Zone Data

```bash
# All EHZ resources
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search EHZ" -q

# Zone palette
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Pal_CPZ" -q
```

### Finding Object Code

```bash
# Monitor object
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Obj_Monitor" -q

# Spring object
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="search Obj_Spring" -q
```

## Troubleshooting

### "Item not found"

- Check spelling and case sensitivity
- Try partial names (e.g., "Buzz" instead of "Buzzer")
- Use `list` command to see all items of a type

### Offset Mismatch

- ROM revision matters (tool expects REV01)
- Use `verify` to check calculated vs actual offset
- Some items have multiple definitions

### Decompression Fails

- Verify the offset is correct
- Try `auto` detection to identify compression type
- Check if data is actually compressed (some .bin files are raw)

## RAM Address Reference

Key RAM addresses for understanding disassembly:

| Address | Name | Description |
|---------|------|-------------|
| 0xFFB000 | Object_RAM | Object status table (64 slots × 64 bytes) |
| 0xFFFE10 | Camera_X_pos | Camera X position |
| 0xFFFE14 | Camera_Y_pos | Camera Y position |
| 0xFFFE20 | Level_Layout | Pointer to level layout |
| 0xFFFFD0 | Vint_routine | V-int routine counter |
| 0xFFFFF0 | Demo_mode_flag | Demo mode indicator |

## Quick Reference Card

```
Search:  search <pattern>      Find items by name
List:    list [type]           List items (nem/kos/eni/sax/bin)
Test:    test <offset> <type>  Test decompression
Verify:  verify <label>        Check calculated offset
Export:  export <type> [prefix] Generate Java constants
```
