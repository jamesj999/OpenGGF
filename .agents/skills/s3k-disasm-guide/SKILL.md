---
title: s3k-disasm Navigation Guide
---

# s3k-disasm Navigation Guide

This skill provides guidance on finding, identifying, and interpreting items in the Sonic 3 & Knuckles disassembly (`docs/skdisasm/`).

## S&K vs S3 ROM Halves — Address Selection Rule

The locked-on ROM ("Sonic and Knuckles & Sonic 3") contains **two halves**:
- **S&K half** (0x000000–0x1FFFFF): The primary S&K code and data — this is what the engine runs
- **S3 half** (0x200000–0x3FFFFF): The Sonic 3 standalone code and data

Many shared assets (art, mappings, palettes) exist in **both halves** with identical binary content. **Always use S&K-side addresses (< 0x200000)** for all ROM constants. The S3 copies at >= 0x200000 are not referenced by the S3KL code path.

When RomOffsetFinder returns multiple results for the same label — one from `sonic3k.asm` (S&K) and one from `s3.asm` (S3) — always use the `sonic3k.asm` result. Similarly, when reading disassembly source, prefer `sonic3k.asm` over `s3.asm` for object code, as the S3KL versions may contain zone-specific overrides (e.g., FBZ art tile) absent from the S3 version.

## Directory Structure

S3K is organized very differently from S1/S2 — per-zone directories under `Levels/`:

| Directory | Contents | Notes |
|-----------|----------|-------|
| `Levels/{ZONE}/` | Per-zone data | AIZ, HCZ, MGZ, CNZ, FBZ, ICZ, LBZ, MHZ, SOZ, LRZ, HPZ, SSZ, DEZ, DDZ |
| `Levels/{ZONE}/KosinskiM Art/` | Kosinski Moduled compressed art | Primary art compression type |
| `Levels/{ZONE}/Nemesis Art/` | Nemesis compressed art | Secondary art compression |
| `Levels/{ZONE}/Tiles/` | Raw tile patterns | Uncompressed tile data |
| `Levels/{ZONE}/Blocks/` | 16x16 block mappings | Per-zone chunk definitions |
| `Levels/{ZONE}/Chunks/` | 128x128 chunk mappings | Per-zone block definitions |
| `Levels/{ZONE}/Layout/` | Level layouts | `1.bin`, `2.bin` per act |
| `Levels/{ZONE}/Object Pos/` | Object placement | Per-act object positions |
| `Levels/{ZONE}/Ring Pos/` | Ring placement | Per-act ring positions |
| `Levels/{ZONE}/Palettes/` | Zone palettes | Zone-specific color data |
| `Levels/{ZONE}/Collision/` | Collision data | Zone collision indices |
| `Levels/{ZONE}/Misc Object Data/` | Zone-specific object maps/anims | `Map - Name.asm`, `Anim - Name.asm` |
| `General/Sprites/{NAME}/` | Shared sprite data | `Art/`, `Map`, `DPLC`, `Anim` files |
| `Sound/` | Z80 sound driver | SMPS audio data |

### Key Source Files

| File | Contents |
|------|----------|
| `sonic3k.asm` | Main assembly source (~203K lines, all object code inline) |
| `sonic3k.constants.asm` | RAM addresses, object field names |
| `sonic3k.macros.asm` | Assembly macros |
| `s3.asm` | Sonic 3 standalone variant |

## Compression Types

| Type | Directory/Label Suffix | Tool Flag | Description |
|------|----------------------|-----------|-------------|
| Kosinski Moduled | `KosinskiM Art/`, `_KosM` suffix | `kosm` | **Primary** art compression (dominant in S3K) |
| Nemesis | `Nemesis Art/`, `ArtNem_` prefix | `nem` | Secondary art compression |
| Kosinski | `ArtKos_` prefix | `kos` | Used for some shared data |
| Uncompressed | Various `.bin` | `bin` | Raw tile data, palettes |

**Note:** S3K encodes compression type in the label suffix (e.g., `AIZ1_8x8_Primary_KosM`) rather than the file extension. The RomOffsetFinder tool auto-infers the correct type from the label when the file extension is `.bin`.

## Finding Items with RomOffsetFinder

**Important:** All RomOffsetFinder commands for Sonic 3 & Knuckles require the `--game s3k` flag.

### Search Command (Most Common)

```bash
# Search by partial name
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search AIZ" -q

# Search for zone-specific items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search MHZ" -q

# Search for palettes
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search Pal_" -q

# Search for specific object art
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ArtKosM_" -q
```

### List Command

```bash
# List all Nemesis-compressed items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k list nem" -q

# List all Kosinski Moduled items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k list kosm" -q

# List all compression types
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k list" -q
```

### Verify and Export

```bash
# Verify a single offset
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k verify ArtNem_TitleScreenText" -q

# Batch verify all Nemesis items
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k verify-batch nem" -q

# Export as Java constants
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k export nem ART_" -q
```

### Search ROM Binary

Use `search-rom` to find inline assembly data (pointer tables, animation scripts, AniPLC tables, `dc.w`/`dc.b` directives) that have no binary file — the `search` and `find` commands only work with `binclude` items. This is especially useful for S3K where many data structures are inline.

```bash
# Search for known hex byte pattern (spaces optional)
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search-rom \"0002 FF2A 2940 5CC0\"" -q

# Restrict search to a specific ROM range
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search-rom \"0002\" 0x28000 0x29000" -q
```

## Label Naming Conventions

S3K uses different label prefixes from S1 and S2:

| S3K Prefix | Meaning | Example |
|------------|---------|---------|
| `ArtKosM_` | Kosinski Moduled art | `ArtKosM_AIZEndBoss` |
| `ArtNem_` | Nemesis art | `ArtNem_TitleScreenText` |
| `ArtKos_` | Kosinski art | `ArtKos_LevelResults` |
| `Pal_` | Palette | `Pal_AIZ1`, `Pal_Sonic` |
| `AnPal_` | Animated palette | `AnPal_AIZ1` |
| `Obj_` | Object code | `Obj_AIZEndBoss` |
| `Map_` / `Map -` | Sprite mappings | In `General/Sprites/` or `Misc Object Data/` |
| `DPLC_` | Dynamic Pattern Load Cue | In `General/Sprites/` |
| `Anim_` / `Anim -` | Animation scripts | In `General/Sprites/` or `Misc Object Data/` |
| `PLC_` | Pattern Load Cue | `PLC_AIZ` |

### Comparison with S1 and S2

| S3K Prefix | S2 Equivalent | S1 Equivalent |
|------------|---------------|---------------|
| `ArtKosM_` | (no equivalent) | (no equivalent) |
| `ArtNem_` | `ArtNem_` | `Nem_` |
| `ArtKos_` | `ArtKos_` | `Kos_` |
| `Pal_` | `Pal_` | `Pal_` |
| `AnPal_` | (inline cycling) | (inline cycling) |
| `Obj_` | `Obj_` | `ObjXX` / `Obj_` |
| `Map_` | `MapUnc_` | `Map_` |
| `DPLC_` | (inline) | `DPLC_` |

## Zone Abbreviations

| Abbrev | Full Name | Zone ID |
|--------|-----------|---------|
| AIZ | Angel Island Zone | 0x00 |
| HCZ | Hydrocity Zone | 0x01 |
| MGZ | Marble Garden Zone | 0x02 |
| CNZ | Carnival Night Zone | 0x03 |
| FBZ | Flying Battery Zone | 0x04 |
| ICZ | Icecap Zone | 0x05 |
| LBZ | Launch Base Zone | 0x06 |
| MHZ | Mushroom Hill Zone | 0x07 |
| SOZ | Sandopolis Zone | 0x08 |
| LRZ | Lava Reef Zone | 0x09 |
| SSZ | Sky Sanctuary Zone | 0x0A |
| DEZ | Death Egg Zone | 0x0B |
| DDZ | Doomsday Zone | 0x0C |
| HPZ | Hidden Palace Zone | 0x0D |

### Bonus/Special Zones

| Abbrev | Full Name | Notes |
|--------|-----------|-------|
| Gumball | Gumball Machine | Bonus stage |
| Pachinko | Pachinko | Bonus stage |
| Slots | Slot Machine | Bonus stage |
| CGZ | Chrome Gadget Zone | S3-only |
| BPZ | Balloon Park Zone | S3-only |
| DPZ | Desert Palace Zone | S3-only |
| EMZ | Endless Mine Zone | S3-only |
| ALZ | Azure Lake Zone | S3-only competition |

## Dual Object Pointer Tables (Zone-Set System)

S3K uses **two separate object pointer tables** that remap many IDs depending on the zone:

| Set | Disasm Name | Engine Name | Zones | File |
|-----|------------|-------------|-------|------|
| SK Set 1 | `Sprite_Listing3` | S3KL (S3K-Level Object Set) | 0-6 (AIZ through LBZ) | `Levels/Misc/Object pointers - SK Set 1.asm` (256 entries) |
| SK Set 2 | `Sprite_ListingK` | SKL (SK-Level Object Set) | 7-13 (MHZ through DDZ) | `Levels/Misc/Object pointers - SK Set 2.asm` (185 entries) |

**Selection logic** (sonic3k.asm line ~37411): Purely zone-based, NOT game-mode-based. Zones 0-6 use S3KL, zones 7+ use SKL.

The same numeric object ID can map to completely different objects:
- 0x03: AIZHollowTree (S3KL) vs MHZTwistedVine (SKL)
- 0x8F: CaterKillerJr (S3KL) vs Butterdroid (SKL)
- 0x9C: Spiker (S3KL) vs LRZRockCrusher (SKL)

Many IDs are shared between both sets (Ring, Monitor, PathSwap, Spring, Spikes, etc.).

**Engine support:** `S3kZoneSet` enum (`S3KL`/`SKL`), `Sonic3kObjectRegistry.getPrimaryName(id, zoneSet)`, and `Sonic3kObjectProfile` with per-level name/badnik/boss resolution.

## Object Code Organization

Unlike S1 (separate files per object) and S2 (inline in `s2.asm`), S3K objects are:
- **Inline in `sonic3k.asm`** as `Obj_` routines
- Zone-specific objects appear near their zone's code section
- Shared sprites have data in `General/Sprites/{Name}/` (Art, Map, DPLC, Anim)
- Zone-specific maps/anims are in `Levels/{ZONE}/Misc Object Data/`

### Finding Object Code

```bash
# Search for object by name in the main ASM
grep -n "Obj_AIZEndBoss" docs/skdisasm/sonic3k.asm

# Search for all objects in a zone
grep -n "Obj_AIZ" docs/skdisasm/sonic3k.asm

# Find object sprite data
ls docs/skdisasm/General/Sprites/
ls "docs/skdisasm/Levels/AIZ/Misc Object Data/"
```

### Object File Locations

| Data Type | Location | Example |
|-----------|----------|---------|
| Object code | `sonic3k.asm` (inline) | `Obj_AIZEndBoss:` |
| Shared sprite art | `General/Sprites/{Name}/Art/` | `General/Sprites/Rhinobot/Art/` |
| Shared sprite maps | `General/Sprites/{Name}/Map` | `General/Sprites/Rhinobot/Map - Rhinobot.asm` |
| Shared sprite DPLC | `General/Sprites/{Name}/DPLC` | `General/Sprites/Rhinobot/DPLC - Rhinobot.asm` |
| Shared sprite anim | `General/Sprites/{Name}/Anim` | `General/Sprites/Rhinobot/Anim - Rhinobot.asm` |
| Zone-specific maps | `Levels/{ZONE}/Misc Object Data/` | `Levels/AIZ/Misc Object Data/Map - Zipline.asm` |
| Zone-specific anim | `Levels/{ZONE}/Misc Object Data/` | `Levels/AIZ/Misc Object Data/Anim - Zipline.asm` |

## Object System Reference

### Object Status Table Offsets

S3K uses **S2-style field names** (same as S2, different from S1):

| Field | Offset | Size | Description |
|-------|--------|------|-------------|
| `code` | 0x00 | long | Object code pointer |
| `render_flags` | 0x04 | byte | Render flags |
| `routine` | 0x05 | byte | Current routine |
| `height_pixels` | 0x06 | byte | Sprite height |
| `width_pixels` | 0x07 | byte | Sprite width |
| `x_pos` | 0x10 | word/long | X position |
| `y_pos` | 0x14 | word/long | Y position |
| `x_vel` | 0x18 | word | X velocity |
| `y_vel` | 0x1A | word | Y velocity |
| `mapping_frame` | 0x22 | byte | Current frame |
| `routine` | 0x24 | byte | Current routine (alternate ref) |
| `collision_flags` | 0x28 | byte | TT SSSSSS (type + size) |
| `collision_property` | 0x29 | byte | Hit count for bosses |
| `shield_reaction` | 0x2B | byte | Shield-specific reactions (S3K-only) |
| `subtype` | 0x2C | byte | Object subtype |
| `routine_secondary` | 0x3C | byte | Secondary routine |
| `parent` | 0x42 | word | Parent object address |

**Object size:** `$4A` bytes (same as S2).

### S3K-Specific Features

**Shield reactions** (`shield_reaction` at offset 0x2B):
| Bit | Shield | Effect |
|-----|--------|--------|
| 3 | Bounce | Bounce shield attack reaction |
| 4 | Fire | Fire shield immunity/reaction |
| 5 | Lightning | Lightning shield reaction |
| 6 | Bubble | Bubble shield negation |

**Child sprite system** via `mainspr_childsprites` at offset +$16:
- Up to 8 inline child sprites per object
- Children share the parent object's RAM slot
- Used for multi-piece visual objects (e.g., boss segments)

**Character ID** (`character_id` field):
| Value | Character |
|-------|-----------|
| 0 | Sonic |
| 1 | Tails |
| 2 | Knuckles |

### Object Routine Pattern

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

## Sprite Mappings Format (S2/S3K Format)

S3K uses the **same 6-byte per piece** format as S2 (not S1's 5-byte):

```
Frame header:
  dc.w  <piece_count>              ; Word header (same as S2)

Per piece (6 bytes):
  dc.b  <y_offset>                 ; Signed byte
  dc.b  <size>                     ; bits 0-1: width-1, bits 2-3: height-1
  dc.w  <pattern>                  ; Pattern index + flags (priority, palette, flip)
  dc.w  <x_offset>                 ; Signed word (S1 uses byte)
```

Size byte encoding (same as S1/S2):
- `0x00` = 1x1 (8x8 pixels)
- `0x05` = 2x2 (16x16 pixels)
- `0x0F` = 4x4 (32x32 pixels)

## Common Search Patterns

### Finding Badnik Art

```bash
# Search for specific badnik
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search Rhinobot" -q

# Find all Kosinski Moduled art
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ArtKosM_" -q

# Find all Nemesis art
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search ArtNem_" -q
```

### Finding Zone Data

```bash
# All AIZ resources
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search AIZ" -q

# Zone palette
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search Pal_AIZ" -q

# Animated palette
mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" -Dexec.args="--game s3k search AnPal_AIZ" -q
```

### Finding Object Code

```bash
# Search for object in main ASM
grep -n "Obj_Rhinobot" docs/skdisasm/sonic3k.asm

# Find all boss objects
grep -n "Obj_.*Boss" docs/skdisasm/sonic3k.asm

# Find sprite data directory
ls "docs/skdisasm/General/Sprites/Rhinobot/"
```

## RAM Address Reference

Key RAM addresses from `sonic3k.constants.asm`:

| Address | Name | Description |
|---------|------|-------------|
| (dynamic) | Object_RAM | Object status table (dynamically allocated) |
| `Current_zone` | Current zone | Current zone ID |
| `Current_zone_and_act` | Zone and act | Combined zone/act value |
| `Camera_X_pos` | Camera X | Camera X position |
| `Camera_Y_pos` | Camera Y | Camera Y position |
| `Ring_count` | Rings | Current ring count |

Zone-specific variables exist for special mechanics:
- `AIZ1_palette_cycle_flag` - AIZ fire palette cycling
- `SOZ_darkness_level` - SOZ Act 2 darkness
- `MHZ_pollen_counter` - MHZ pollen effect

## Quick Reference Card

```
Search:  --game s3k search <pattern>      Find items by name
List:    --game s3k list [type]           List items (nem/kos/kosm/bin)
Test:    --game s3k test <offset> <type>  Test decompression
Verify:  --game s3k verify <label>        Check calculated offset
Export:  --game s3k export <type> [prefix] Generate Java constants
```

## Troubleshooting

### Address Verification

Like S1, addresses derived from skdisasm labels may not always match the compiled ROM binary exactly. Use `verify` to check calculated offsets against the actual ROM data.

### "Item not found"

- Check spelling and case sensitivity
- Try partial names (e.g., "Rhino" instead of "Rhinobot")
- S3K labels use `ArtKosM_` for Kosinski Moduled, `ArtNem_` for Nemesis
- Compression type is encoded in the label suffix for some items
- Use `list` command to see all items of a type

### Compression Detection

- S3K primarily uses Kosinski Moduled (`kosm`) rather than regular Kosinski
- Label suffix `_KosM` indicates Kosinski Moduled compression
- `ArtNem_` prefix indicates Nemesis compression
- `ArtKos_` prefix indicates regular Kosinski compression
- When the file extension is `.bin`, the tool infers compression from the label