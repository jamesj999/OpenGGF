# How the Engine Reads ROMs

This page explains how OpenGGF turns a ROM binary into a running game. If you know the
Sonic disassemblies and want to understand where a particular piece of game data ends up
in the engine, start here.

## What the Engine Is Not

OpenGGF does not emulate the Mega Drive hardware. There is no 68000 CPU interpreter, no
VDP chip simulation, and no Z80 coprocessor running the sound driver. Every piece of
hardware behavior has been reimplemented at a higher level of abstraction:

| Original hardware | Engine equivalent |
|-------------------|-------------------|
| 68000 CPU executing object code | Per-object update methods called each frame |
| VDP pattern table + sprite table | GPU texture atlas + instanced quad rendering |
| VDP scroll planes A/B | Tilemap shader with per-line scroll offsets |
| Z80 running SMPS sound driver | Reimplemented SMPS sequencer in the main process |
| YM2612 FM synthesis chip | Software FM synthesis (based on Genesis Plus GX core) |
| SN76489 PSG chip | Software PSG emulation |
| Object RAM (64-byte slots) | Typed object instances with named fields |
| RAM variables (camera, state) | Dedicated manager classes |

The ROM is treated as a **read-only data source**. The engine reads bytes at known offsets,
decompresses them using the same algorithms the original hardware used, and interprets the
resulting data according to the same format definitions.

## A Note on Disassembly Differences

The three community disassemblies (s1disasm, s2disasm, skdisasm) are independent projects
with different maintainers and different conventions. They do not share a common structure:

| Aspect | s1disasm | s2disasm | skdisasm |
|--------|----------|----------|----------|
| Main source file | `sonic.asm` | `s2.asm` | `sonic3k.asm` + `s3.asm` |
| Object code | Separate files in `_incObj/` | Inline in `s2.asm` | Inline in main source |
| Object naming | `ObjXX` (hex ID as filename prefix) | `Obj__` labels with named aliases | `Obj__` labels |
| Art directories | `artnem/`, `artkos/`, `artunc/` | `art/nemesis/`, `art/kosinski/` | Various per-zone |
| Mapping directories | `_maps/` (5-byte format) | `mappings/sprite/` (6-byte format) | `mappings/sprite/` |
| Level layouts | `levels/` (uncompressed) | Kosinski-compressed includes | KosinskiM-compressed |
| Metatile size | 256x256 pixels | 128x128 pixels | 128x128 pixels |
| Level data tables | `LevelHeaders` | `MainLoadBlockX` / `Off_Level` | `LevelLoadBlock` (24-byte entries) |
| Compression types | Nemesis, Kosinski | Nemesis, Kosinski, Enigma, Saxman | Kosinski, KosinskiM, Nemesis |
| Constants file | `Constants.asm` | `s2.constants.asm` | Equates in main source |

The examples on this page and in the [Mapping Exercises](mapping-exercises.md) use Sonic 2
as the primary reference because it is the most complete engine module. The methods transfer
to S1 and S3K, but you will need to adjust for the structural differences. See
[Per-Game Notes](per-game-notes.md) for game-specific details.

## How Addresses Are Found

The engine needs to know where every piece of data lives in the ROM. It uses two strategies:

### Hardcoded Constants

Each game has a constants file that lists verified ROM addresses:

- `Sonic1Constants.java` -- Sonic 1 addresses
- `Sonic2Constants.java` -- Sonic 2 addresses
- `Sonic3kConstants.java` -- Sonic 3&K addresses

These are verified against specific ROM revisions (S1 REV01, S2 REV01, S3K combined).
A constant like `LEVEL_LAYOUT_DIR_ADDR = 0x045A80` means "the level layout directory
starts at byte offset 0x45A80 in the ROM." These values were confirmed by cross-referencing
the disassembly labels, checking the compiled ROM binary, and in some cases pattern-matching
the raw data.

### ROM Scanners

For data whose location may vary or needs to be discovered dynamically, the engine uses
pattern-matching scanners. For example, `Sonic3kRomScanner` searches the S3K ROM for
known byte signatures to locate tables and pointers. This is more resilient to minor ROM
variations than hardcoded offsets, but slower and more complex.

In practice, most addresses are hardcoded constants. The scanners are used for S3K (which
has a more complex ROM layout) and for cross-validation.

### What This Means for Cross-Referencing

When you see a disassembly label like `ArtNem_Buzzer` and want to find the corresponding
engine address, the path is:

1. Check the game's constants file for a named constant.
2. If not there, use the `RomOffsetFinder` tool (see [Tooling](tooling.md)) to search
   for the label and calculate its ROM offset.
3. The engine reads from that offset using `Rom.readBytes()`, `Rom.read16BitAddr()`, or
   similar methods.

Because addresses are tied to specific ROM revisions, using a different revision (e.g.,
Sonic 2 REV00 instead of REV01) will produce incorrect results.

## Level Data Pipeline

This is the path from ROM bytes to the tile grid you see on screen.

### 1. Level Layout Directory

Each game has a level layout directory: a table in the ROM that maps zone/act combinations
to the offsets of their layout data. In Sonic 2, this is a 68-byte table at
`LEVEL_LAYOUT_DIR_ADDR`.

In the disassembly, this corresponds to the `Off_Level` table and the `Level_` labels
that follow it.

### 2. Level Layout

The layout is a 2D grid of **chunk IDs**. Each cell says "put chunk number N here." The
grid is typically 128 or 256 cells wide (depending on the zone) and defines the foreground
and background planes separately.

In Sonic 1, layouts are uncompressed. In Sonic 2 and S3K, they are Kosinski-compressed.

In the disassembly, these are the `level/*.bin` files (S1) or the Kosinski-compressed
layout includes.

### 3. Chunks (Metatiles)

Each chunk is a grid of **block IDs**. In Sonic 2, chunks are 128x128 pixels (8x8 blocks
of 16x16 pixels each). In Sonic 1, they are 256x256 pixels.

Chunk definitions are loaded from Enigma-compressed mappings in Sonic 2, or from
`map256/*.bin` files in Sonic 1.

In the disassembly: `mappings/128x128/` (S2) or `map256/` (S1).

### 4. Blocks (16x16 Tiles)

Each block is a 2x2 grid of **pattern IDs** (8x8 pixel tiles). Block definitions include
the pattern index, palette line, flip flags, and priority bit -- the same information
that would go into a VDP nametable entry.

In the disassembly: `mappings/16x16/` (S2) or `map16/` (S1).

### 5. Patterns (8x8 Tiles)

Patterns are the raw pixel data: 8x8 grids of 4-bit palette indices. In the ROM, zone
patterns are typically Kosinski-compressed. The engine decompresses them and uploads them
to a GPU texture atlas.

In the disassembly: `art/kosinski/` (S2) or `artkos/` (S1).

### Summary

```
ROM offset  -->  Level Layout Directory  (table of pointers)
                      |
                      v
                 Level Layout            (grid of chunk IDs)
                      |
                      v
                 Chunk Definitions       (each chunk = grid of block IDs)
                      |
                      v
                 Block Definitions       (each block = 2x2 pattern references)
                      |
                      v
                 Pattern Data            (8x8 pixel tiles, uploaded to GPU)
                      |
                      v
                 Screen                  (tilemap shader composites everything)
```

## Art Pipeline (Sprites)

Sprite art follows a similar decompression path but uses a different format chain.

### 1. Pattern Load Cues (PLCs)

PLCs define which sprite art to load for a given zone or event. Each PLC entry specifies
a ROM address (pointing to compressed art) and a VRAM destination address (where the tiles
should be placed in the original hardware's video RAM).

The engine does not have a real VRAM, but it uses the destination address as a logical
tile index into its pattern atlas. This means PLC entries work the same way they do in
the disassembly.

In the disassembly: `ArtLoadCues` / `PLC_` tables.

### 2. Compressed Sprite Art

Sprite art is typically Nemesis-compressed (S1, S2) or Kosinski/KosinskiM-compressed (S3K).
The engine decompresses the data and converts the 4-bit-per-pixel tile data into atlas
entries.

In the disassembly: `art/nemesis/` (S2), `artnem/` (S1), or the Kosinski art includes (S3K).

### 3. Sprite Mappings

Mappings define how tiles are arranged into sprite frames. Each frame is a list of
**pieces**, where each piece specifies a tile offset, position, size, and flip flags.
Sonic 2 uses a 6-byte-per-piece format; Sonic 1 uses a 5-byte format (the X offset
is a single byte instead of a word).

In the disassembly: `mappings/sprite/obj__.asm`.

### 4. Rendering

The engine draws each sprite frame by looking up the mapping definition, finding the
corresponding tiles in the pattern atlas, and issuing draw commands. This happens on the
GPU via instanced rendering, but conceptually it is the same as the VDP reading sprite
table entries and compositing tiles onto the screen.

## Object Pipeline

### 1. Object Placement Data

Each act has an object placement list in the ROM: a sequence of records that say
"place object ID X at position (x, y) with subtype S." These are loaded when a level
starts.

In the disassembly: the object position data, referenced via `ObjPos_Index` (S1) or
embedded in the main assembly (S2).

### 2. Object Registry

The engine maps each object ID to a factory that creates the corresponding object
instance. When the camera scrolls an object's position into range, the engine looks up
the ID in the registry and creates an instance.

In the disassembly, the equivalent is the object pointer table (`ObjPtr_` labels) that
maps IDs to code entry points.

### 3. Object Instances

Each active object is an instance of a class that extends `AbstractObjectInstance`. The
instance has named fields for position, velocity, animation state, and any object-specific
data. This replaces the original's 64-byte object RAM slots, where fields were accessed
by numeric offset (e.g., `x_pos(a0)`, `routine(a0)`).

Every frame, each active object's `update()` method is called. This is the engine's
equivalent of the 68000 jumping to the object's routine entry point.

### 4. Lifecycle

Objects are spawned when they scroll into range, updated each frame, and destroyed when
they scroll out of range or are explicitly removed (e.g., defeated badniks, collected
rings). Dynamic objects like projectiles and explosions are spawned by other objects at
runtime and do not come from the placement list.

## Audio Pipeline

### 1. SMPS Music and SFX Data

Music and sound effects are stored in the ROM in SMPS (Sample Music Playback System)
format. The engine locates the music and SFX pointer tables via constants, then parses
each song or sound effect header to determine channel assignments, instrument voices,
and sequence data.

In the disassembly: the `sound/` directory and the music/SFX pointer tables.

### 2. SMPS Sequencer

The engine's `SmpsSequencer` reimplements the Z80 sound driver's playback logic. Each
frame, it processes sequence commands (note on/off, volume, tempo, loops) and sends
register writes to the software synthesisers.

Each game has a slightly different driver configuration (tempo mode, note mapping, PSG
envelope handling). These differences are captured in `SmpsSequencerConfig` rather than
in separate driver implementations.

### 3. YM2612 and SN76489

The YM2612 FM synthesiser and SN76489 PSG chip are emulated in software. The FM core is
based on the Genesis Plus GX reference implementation. The PSG core follows the SN76489
documentation from SMS Power. Both produce audio samples that are mixed and sent to the
system audio output.

### 4. DAC Samples

Drum and percussion samples (DAC) are stored as raw PCM in the ROM. The engine reads
them at the addresses specified by the DAC sample table, applies the correct playback
rate (derived from the Z80 driver's `djnz` loop timing), and mixes them into the audio
output.

## Collision Pipeline

### 1. Collision Arrays

Each zone has a set of collision height arrays and angle values stored in the ROM. A
height array is a 16-byte block defining the surface height at each pixel column within
a 16-pixel-wide tile. Angle values define the slope of each collision tile.

In the disassembly: `collision/` (S2) or `collide/` (S1).

### 2. Collision Index

Each block in the level has a collision index that maps it to a height array. Some games
have two collision layers (primary and secondary) for features like loops, where the
player switches between collision planes.

### 3. Terrain Checks

When the engine checks whether a character or object is touching the ground, it:
1. Determines which block the sensor point falls in.
2. Looks up the block's collision index.
3. Reads the corresponding height array to find the surface height at that X position.
4. Compares the sensor position to the surface height.

This is the same logic the original uses in routines like `FindFloor`, `FindWall`,
`ObjCheckFloorDist`, etc. -- reimplemented with the same lookup tables and the same
sensor positions.

## Next Steps

- [Mapping Exercises](mapping-exercises.md) -- Practice tracing specific features
- [68000 Primer](68000-primer.md) -- If you need help reading the assembly
- [Architecture Overview](architecture-overview.md) -- Where things live in the source code
- [Tooling](tooling.md) -- Built-in tools for ROM exploration
