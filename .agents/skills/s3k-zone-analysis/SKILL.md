---
name: s3k-zone-analysis
description: Use when starting work on a new S3K zone to catalogue its features from the disassembly. Reads Dynamic_Resize, Deform, AniPLC, and AnPal routines and produces a structured zone analysis spec.
---

# S3K Zone Analysis

Analyse the Sonic 3&K disassembly to produce a structured feature catalogue for a given zone. This skill reads the zone's event routines (`Dynamic_Resize`), parallax handlers (`Deform`), animated tile scripts (`AniPLC`), palette cycling (`AnPal`), and notable objects, then outputs a specification that other implementation skills consume.

The output is a **zone analysis spec** -- a structured document listing every feature, its disassembly location, confidence level, and implementation notes. This is the prerequisite step before implementing any zone feature.

## Inputs

$ARGUMENTS: Zone abbreviation (e.g., "AIZ", "HCZ", "MGZ", "CNZ", "FBZ", "ICZ", "LBZ", "MHZ", "SOZ", "LRZ", "HPZ", "SSZ", "DEZ")

## Related Skills

- **s3k-disasm-guide** (`.agents/skills/s3k-disasm-guide/SKILL.md`) for disassembly navigation, label conventions, directory structure, RomOffsetFinder commands, and the S&K vs S3 ROM half address selection rule.

## Zone Reference

| Index | Abbreviation | Full Name | Zone Set |
|-------|-------------|-----------|----------|
| 0x00 | AIZ | Angel Island Zone | S3KL |
| 0x01 | HCZ | Hydrocity Zone | S3KL |
| 0x02 | MGZ | Marble Garden Zone | S3KL |
| 0x03 | CNZ | Carnival Night Zone | S3KL |
| 0x04 | FBZ | Flying Battery Zone | S3KL |
| 0x05 | ICZ | IceCap Zone | S3KL |
| 0x06 | LBZ | Launch Base Zone | S3KL |
| 0x07 | MHZ | Mushroom Hill Zone | SKL |
| 0x08 | SOZ | Sandopolis Zone | SKL |
| 0x09 | LRZ | Lava Reef Zone | SKL |
| 0x0A | HPZ | Hidden Palace Zone | SKL |
| 0x0B | SSZ | Sky Sanctuary Zone | SKL |
| 0x0C | DEZ | Death Egg Zone | SKL |
| 0x0D | DDZ | Doomsday Zone | SKL |

Zone set determines which object pointer table is active: **S3KL** (zones 0-6: AIZ through LBZ) or **SKL** (zones 7-13: MHZ through DDZ). See `S3kZoneSet` enum in the engine.

## Analysis Process

### Phase 1: Find Zone Routines

Search the disassembly for each of the five routine categories. All searches target `docs/skdisasm/sonic3k.asm` as the primary source, but some routines may also appear in `docs/skdisasm/s3.asm` (the Sonic 3 standalone half). **Always prefer `sonic3k.asm` labels and addresses** per the S&K address selection rule in `s3k-disasm-guide`.

#### 1.1 Events (Dynamic_Resize)

These are the level event routines -- camera locks, boss arenas, act transitions, dynamic boundary changes.

```bash
grep -n "{ZONE}_Resize\|{ZONE}1_Resize\|{ZONE}2_Resize\|{ZONE}_Dynamic_Resize\|{ZONE}1_Dynamic\|{ZONE}2_Dynamic" docs/skdisasm/sonic3k.asm
```

Also search for the stage-numbered sub-routines:
```bash
grep -n "{ZONE}.*_Resize_\|{ZONE}.*_Stage\|{ZONE}.*_Routine" docs/skdisasm/sonic3k.asm | head -40
```

Look for the `OffsResize` dispatch table entry to confirm the zone's slot:
```bash
grep -n "OffsResize\|Offs_Resize\|Dynamic_Resize_Pointers" docs/skdisasm/sonic3k.asm | head -20
```

#### 1.2 Parallax (Deform)

Background scrolling handlers -- multi-layer parallax, auto-scrolling clouds, water deformation.

```bash
grep -n "{ZONE}_Deform\|{ZONE}1_Deform\|{ZONE}2_Deform\|{ZONE}_BGDeform" docs/skdisasm/sonic3k.asm
```

Also search for associated data:
```bash
grep -n "{ZONE}.*DeformArray\|{ZONE}.*DeformIndex\|{ZONE}.*DeformOffset\|{ZONE}.*BGScroll" docs/skdisasm/sonic3k.asm
```

And background init/event routines:
```bash
grep -n "{ZONE}.*BackgroundInit\|{ZONE}.*BackgroundEvent\|{ZONE}.*ScreenInit\|{ZONE}.*ScreenEvent" docs/skdisasm/sonic3k.asm
```

#### 1.3 Animated Tiles (AniPLC)

Pattern animation scripts that cycle tile art in VRAM (waterfalls, conveyor belts, lava flows).

```bash
grep -n "{ZONE}.*AniPLC\|{ZONE}.*AnimPatterns\|{ZONE}.*AnimTiles\|AniPLC_{ZONE}" docs/skdisasm/sonic3k.asm
```

Also look for the `OffsAniPLC` dispatch table:
```bash
grep -n "OffsAniPLC\|AniPLC_Pointers\|AnimatedPatterns" docs/skdisasm/sonic3k.asm | head -20
```

Search for the binary script data files:
```bash
find docs/skdisasm/Levels/{ZONE}/ -name "*Anim*" -o -name "*PLC*" 2>/dev/null
```

#### 1.4 Palette Cycling (AnPal)

Per-frame palette color cycling -- water shimmer, torch glow, electrical sparks.

```bash
grep -n "AnPal_{ZONE}\|{ZONE}.*AnPal\|{ZONE}.*PalCycle\|AnPal_Pal{ZONE}" docs/skdisasm/sonic3k.asm
```

Look for the `OffsAnPal` dispatch table:
```bash
grep -n "OffsAnPal\|AnPal_Pointers\|AnimatePalettes" docs/skdisasm/sonic3k.asm | head -20
```

Search for palette data tables:
```bash
grep -n "AnPal_Pal{ZONE}" docs/skdisasm/sonic3k.asm
```

Reference `AGENTS_S3K.md` section "Per-Frame Palette Animation" for the counter/step/limit pattern and the zone inventory showing which zones have cycling vs. rts stubs.

#### 1.5 Notable Objects

Objects unique to this zone or with zone-specific behavior.

```bash
grep -n "Obj_{ZONE}\|{ZONE}_Object\|{ZONE}.*_obj\|Obj_.*{ZONE}" docs/skdisasm/sonic3k.asm | head -40
```

Check the zone's object placement directory for object IDs in use:
```bash
ls docs/skdisasm/Levels/{ZONE}/Object\ Pos/ 2>/dev/null
```

Cross-reference with the object pointer tables to identify zone-set-specific objects:
```bash
grep -n "{ZONE}" docs/skdisasm/sonic3k.asm | grep -i "Obj_\|_Index\|Object_Pointers" | head -20
```

### Phase 2: Read and Extract Features

For each routine found in Phase 1, read the actual assembly code and extract the feature details.

#### 2.1 Events (Dynamic_Resize) -- What to Look For

Read the full `{ZONE}1_Resize` and `{ZONE}2_Resize` routines. These are state machines driven by `eventRoutineFg` / `eventRoutineBg` counters. Extract:

- **Camera thresholds:** `cmpi.w #$XXXX,(Camera_X_pos).w` or `(Camera_Y_pos).w` comparisons that trigger state transitions.
- **Boundary changes:** `move.w #$XXXX,(Camera_Max_X_pos).w` etc. -- dynamic level boundary updates that create camera locks or open new areas.
- **Boss spawns:** Look for object spawn calls (`jsr (SingleObjLoad).l` or `jsr (AllocateObjectAfterCurrent).l`) followed by `move.l #Obj_XXX,address(a1)` -- these create boss objects.
- **Character branching:** `cmpi.w #2,(Player_mode).w` tests for Knuckles. S3K events frequently have entirely different paths for Knuckles vs. Sonic/Tails.
- **Music changes:** `move.w #MusicID,(Level_music).w` or `jsr (PlayMusic).l` calls.
- **Palette mutations:** Direct writes to palette RAM (`Palette_000`, etc.) triggered by camera position -- these are NOT part of AnPal cycling. See AGENTS_S3K.md for the distinction.
- **Water level changes:** `move.w #$XXXX,(Water_Level_2).w` -- dynamic water surface movement.
- **Screen shake:** Writes to `(Screen_shake_flag).w` or references to earthquake/rumble effects.

**Dual routine tracking:** S3K events use TWO routine counters (`eventRoutineFg` and `eventRoutineBg`) that advance independently. Foreground events handle boss arenas and act transitions; background events handle concurrent environmental changes. Map both counters' state machines.

#### 2.2 Parallax (Deform) -- What to Look For

Read the `{ZONE}_Deform` or `{ZONE}{ACT}_Deform` routine. Extract:

- **Height arrays:** `{ZONE}_BGDeformArray` or similar -- lists of byte values giving the pixel height of each parallax band. Bit 15 (0x80 in byte, 0x8000 in word) marks per-line scroll bands.
- **Speed divisors:** How the camera X/Y is divided to produce each band's scroll rate (e.g., `asr.l #1,d0` = half speed, `asr.l #3,d0` = 1/8 speed).
- **Auto-scrolling accumulators:** Persistent values incremented each frame for clouds, water surface ripple, etc. Look for `add.w #$XXX,(addr).w` patterns.
- **Scatter-fill index tables:** `{ZONE}_BGDeformIndex` -- these map HScroll_table entries to non-contiguous scanline ranges (used when background layers interleave).
- **Vertical scroll:** Writes to `(VScroll_factor_BG).w` or the per-column VScroll buffer. Note whether VScroll is simple (single value) or per-column.
- **Act-specific differences:** Some zones share a deform routine across acts, others have `{ZONE}1_Deform` and `{ZONE}2_Deform` with different band counts or speeds.
- **Dynamic background changes:** BackgroundEvent routines that swap background layout or tilemap mid-level (e.g., MHZ season change, LBZ interior/exterior transitions).

#### 2.3 Animated Tiles (AniPLC) -- What to Look For

Read the `AniPLC_{ZONE}` routine and its script data. The AniPLC system uses script entries with:

- **Script format:** Each entry specifies: source art (ROM address), destination (VRAM tile index), frame count, frame duration (in game frames), tile count per frame.
- **Multiple scripts:** Zones often have several independent animation channels (e.g., waterfall + lava + conveyor belt).
- **Act-specific scripts:** Check if Act 1 and Act 2 use different scripts or the same one.
- **Trigger conditions:** Some animations are conditional (e.g., only active when a flag is set, or only in a specific act).

#### 2.4 Palette Cycling (AnPal) -- What to Look For

Read the `AnPal_{ZONE}` or `AnPal_{ZONE}{ACT}` routine. Each channel follows the counter/step/limit pattern documented in AGENTS_S3K.md:

- **Counter RAM address:** Which `Palette_cycle_counter` or `Palette_cycle_counters+N` variable.
- **Step size:** Bytes advanced per tick (2 = 1 color, 4 = 2 colors, 6 = 3 colors, 8 = 4 colors per frame).
- **Limit:** Wrap point (total table size in bytes). Counter resets to 0 when it reaches this value.
- **Timer:** Frames between ticks. Some channels share a timer, others have independent timers.
- **Table label:** The `AnPal_Pal{ZONE}_N` data label containing the color values.
- **Destination:** Palette line and color offset where the cycling writes. Convert from `Palette_000 + $XX` to palette index and color index.
- **Conditional channels:** Channels gated by flags (e.g., AIZ1 fire mode flag) or camera position.

**Important:** If the `AnPal` routine for this zone is just `rts`, the zone has NO palette cycling. Note this in the spec. Refer to the zone inventory in AGENTS_S3K.md.

### Phase 3: Assess Confidence and Flag Concerns

For each feature extracted, assign a confidence level:

| Level | Meaning | Criteria |
|-------|---------|----------|
| **HIGH** | Fully understood | Assembly read in full, all constants identified, pattern matches known engine utilities |
| **MEDIUM** | Mostly understood | Assembly read but some branches unclear, or references external data not yet located |
| **LOW** | Partially understood | Only entry point found, routine is complex or uses unfamiliar patterns, needs deeper investigation |

#### Cross-Cutting Concerns Checklist

Check for each of these and note presence/absence:

- [ ] **Water:** Does the zone have water? Check for `(Water_flag).w` references, water level changes in `_Resize`, and water-specific deform logic.
- [ ] **Screen shake:** Any `(Screen_shake_flag).w` writes or earthquake-like effects in events.
- [ ] **Act transitions:** How does Act 1 end? Boss defeat? Walk off screen? Seamless scroll? Check the final `_Resize` stage.
- [ ] **Character paths:** Does Knuckles have a different route? Look for `Player_mode` checks creating branch paths in `_Resize`.
- [ ] **Dynamic tilemap changes:** Any `move.b #$XX,(Level_trigger_array+N).w` or `move.b` to `Dynamic_Resize_Routine` that causes mid-level layout changes.
- [ ] **PLC loading:** Boss art loads via PLC (`jsr (LoadPLC).l` or `jsr (NewPLC).l` calls in `_Resize`). Note which PLC IDs are loaded and when.
- [ ] **Unique mechanics:** Zone-specific physics or gimmicks (e.g., CNZ gravity flip, SOZ quicksand, HCZ water currents, ICZ snowboard). Note these even if not implementing them -- they affect event sequencing.

### Phase 4: Shared State Trace (Cross-Category Dependencies)

After reading all routines individually, trace shared mutable state across category boundaries. This catches dependencies that single-category analysis misses (e.g., events setting a flag that animated tiles check, or events loading art to a VRAM region that AniPLC scripts also target).

#### 4.1 Catalogue Writes Per Category

For each category, list every RAM variable, VRAM destination, and palette entry it WRITES:

**Events writes:** (from `_Resize`, `ScreenEvent`, `BackgroundEvent`)
- `Dynamic_Resize_routine` values (which stages are set)
- `Boss_flag` (when set/cleared)
- `Level_trigger_array+N` entries (which indices, what values)
- `Target_water_level` / `Water_Level_2` changes
- `Screen_shake_flag` writes
- Palette RAM writes (palette mutations — which line, which colors)
- PLC loads (which IDs — these load art to VRAM)
- Kos/KosinskiM queue loads (destination VRAM tile addresses)
- Any custom flags (e.g., `AIZ1_palette_cycle_flag`, zone-specific RAM)

**Animated Tiles writes:** (from `Animated_Tiles_{ZONE}` AND `AniPLC` scripts)
- VRAM tile destinations per script (from AniPLC entries)
- Any non-AniPLC art loads in custom `Animated_Tiles_{ZONE}` routines
- Note: custom routines often contain camera checks, flag reads, and direct art loads that straddle the events/tiles boundary

**Palette Cycling writes:** (from `AnPal_{ZONE}`)
- Palette line and color indices per channel

**Parallax reads:** (from `_Deform`)
- `Water_Level_1` / `Water_Level_2` (for water split)
- `Screen_shake_offset` / `Screen_shake_flag`
- Any event flags that change deform behaviour (e.g., `Events_bg+$04` for battleship mode)

#### 4.2 Cross-Reference: Find Shared State

Search for each variable written by one category in the routines of OTHER categories:

```bash
# For each flag/variable found in events, search animated tiles and palette cycling:
grep -n "Dynamic_Resize_routine\|Boss_flag\|Level_trigger_array" docs/skdisasm/sonic3k.asm | grep -i "Animated_Tiles_{ZONE}\|AnPal_{ZONE}\|{ZONE}_Deform"
```

Also search the custom `Animated_Tiles_{ZONE}` routine (NOT just the AniPLC script data) for:
```bash
grep -n "Animated_Tiles_{ZONE}" docs/skdisasm/sonic3k.asm
```
Then read the FULL custom routine — it often contains `cmpi`/`tst` checks on event state, camera position checks, and direct art loads that are NOT part of the AniPLC script system.

#### 4.3 Detect VRAM/Palette Conflicts

Compare VRAM destinations:
- List all VRAM tile destinations from AniPLC scripts
- List all VRAM tile destinations from event PLC/Kos loads
- List all VRAM tile destinations from custom `Animated_Tiles` art loads
- Flag any overlaps — these indicate art ownership handoffs (one system must yield to another at certain game states)

Compare palette targets:
- List palette entries written by AnPal cycling channels
- List palette entries written by event palette mutations
- Flag any overlaps — these are potential conflicts where cycling and mutation target the same colors

#### 4.4 Output the Dependency Map

Record all cross-category dependencies found. Each dependency should state:
- **Source:** which category and routine writes the state
- **Consumer:** which category and routine reads the state
- **Variable:** the specific RAM address, VRAM tile, or palette entry
- **Effect:** what changes when the state changes (gating, art swap, mode switch)

### Phase 5: Write the Analysis Spec

Produce the zone analysis spec using the template below. Save it to `docs/s3k-zones/{zone}-analysis.md` where `{zone}` is the lowercase zone abbreviation (e.g., `docs/s3k-zones/hcz-analysis.md`).

---

**Output Template:**

```markdown
# S3K {ZONE} Zone Analysis

## Summary

- **Zone:** {Full Name} ({ZONE})
- **Zone Index:** 0x{XX}
- **Zone Set:** {S3KL or SKL}
- **Acts:** {1 and 2, or special}
- **Water:** {Yes/No}
- **Palette Cycling:** {Yes/No (N channels)}
- **Animated Tiles:** {Yes/No (N scripts)}
- **Character Branching:** {Yes/No -- brief description}

## Events

### Act 1 ({ZONE}1_Resize)

**Disassembly location:** `sonic3k.asm` line {NNNN}

**State machine (eventRoutineFg):**

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|
| 0 | Level start | {initial setup} | |
| 2 | Camera X >= $XXXX | {boundary change} | |
| ... | ... | ... | |

**State machine (eventRoutineBg):** {if applicable}

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|

**Palette mutations:** {list any direct palette writes with trigger conditions}

**Knuckles differences:** {describe alternate path or "None"}

### Act 2 ({ZONE}2_Resize)

{Same structure as Act 1}

## Boss Sequences

### Act 1 Boss

- **Object:** `Obj_{name}` at line {NNNN}
- **Spawn trigger:** {Camera threshold or event stage}
- **PLC load:** PLC ID $XX loaded at stage {N}
- **Arena boundaries:** Left=$XXXX, Right=$XXXX, Top=$XXXX, Bottom=$XXXX
- **Defeat behavior:** {transition type}
- **Confidence:** {HIGH/MEDIUM/LOW}

### Act 2 Boss

{Same structure}

## Parallax

### Act 1 ({ZONE}1_Deform or {ZONE}_Deform)

**Disassembly location:** `sonic3k.asm` line {NNNN}

**Bands:**

| Band | Height (px) | Speed | Per-line? | Description |
|------|-------------|-------|-----------|-------------|
| 0 | {N} | Camera/2 | No | {sky/clouds/etc} |
| 1 | {N} | Camera/4 | No | {mountains/etc} |
| ... | ... | ... | ... | ... |

**Auto-scroll accumulators:** {list any persistent scrolling effects}

**Vertical scroll:** {simple or per-column, formula}

**Confidence:** {HIGH/MEDIUM/LOW}

### Act 2

{Same structure, or "Same as Act 1" if shared}

## Animated Tiles

### Script: {description}

- **Disassembly location:** `sonic3k.asm` line {NNNN} / data at `Levels/{ZONE}/...`
- **Destination VRAM tile:** $XXX
- **Frame count:** {N}
- **Frame duration:** {N} frames
- **Tiles per frame:** {N}
- **Confidence:** {HIGH/MEDIUM/LOW}

{Repeat for each script}

## Palette Cycling

### Channel {N}: {description}

- **Disassembly location:** `sonic3k.asm` line {NNNN}
- **Counter:** `Palette_cycle_counters+{N}`
- **Step:** {N} bytes ({N} colors per tick)
- **Limit:** $XX ({N} frames in cycle)
- **Timer:** {N} game frames between ticks
- **Table:** `AnPal_Pal{ZONE}_{N}` at line {NNNN}
- **Destination:** Palette line {N}, colors {N}-{N}
- **Conditional:** {Yes/No -- condition}
- **Confidence:** {HIGH/MEDIUM/LOW}

{Repeat for each channel}

## Notable Objects

| Object ID | Name | Description | Zone-Specific? | Notes |
|-----------|------|-------------|----------------|-------|
| $XX | {name} | {brief} | Yes/No | {any concerns} |

## Cross-Cutting Concerns

- **Water:** {details or "Not present"}
- **Screen shake:** {details or "Not present"}
- **Act transition:** {how Act 1 ends and Act 2 begins}
- **Character paths:** {Knuckles route differences}
- **Dynamic tilemap:** {any mid-level layout changes}
- **PLC loading:** {list all PLC loads with trigger conditions}
- **Unique mechanics:** {zone-specific gimmicks}

## Dependency Map

### Events → Animated Tiles
- {variable/flag}: {what events writes} → {what animated tiles checks/does differently}
- {VRAM conflict}: Events loads art to tile $XXX via PLC/Kos → AniPLC script N targets same tile range
- {art override}: Events/custom routine loads static art to tile $XXX when {condition} → scripts must not overwrite

### Events → Palette Cycling
- {flag}: {what events sets} → {how cycling mode changes}
- {palette conflict}: Events writes palette line N color M at {trigger} → cycling channel N also targets line N colors M-P

### Events → Parallax
- {water level}: Events sets Target_water_level → parallax splits deformation at water boundary
- {shake}: Events sets Screen_shake_flag → parallax applies shake offset
- {mode change}: Events sets {flag} → parallax switches to alternate deform mode

### Animated Tiles → Parallax
- {VRAM}: Animated tiles update tile $XXX → parallax renders those tiles in band N
(Usually no direct dependency — note only if custom interaction exists)

### VRAM Ownership Table

| VRAM Tile Range | Owner | Condition | Notes |
|-----------------|-------|-----------|-------|
| $XXX-$YYY | AniPLC script N | Always / when flag set | |
| $XXX-$YYY | Events PLC $NN | Loaded at stage N | Overwrites script range |
| $XXX-$YYY | Custom art load | Camera X < $XXXX | Conflicts with script M |

### Palette Ownership Table

| Palette Entry | Owner | Condition | Notes |
|---------------|-------|-----------|-------|
| Line N, color M | AnPal channel N | Timer-driven | |
| Line N, color M | Events mutation | Camera X >= $XXXX | One-shot, same entry as cycling |

## Implementation Notes

### Priority Order
1. {First feature to implement and why}
2. {Second feature}
3. {Third feature}

### Dependencies
- {Feature X requires Feature Y to be implemented first}
- {Feature Z requires PLC ID $XX to be registered}

### Known Risks
- {Any areas of LOW confidence that need deeper investigation}
- {Any features that interact in complex ways}
```

---

## Common Mistakes

1. **Searching only `sonic3k.asm`:** Some data tables and object code are in separate `.asm` files under `Levels/{ZONE}/Misc Object Data/` or `General/Sprites/`. Always check the zone's directory structure as well as the main assembly file.

2. **Missing character branching:** S3K events extensively branch on `Player_mode`. A `_Resize` routine that looks simple for Sonic may have an entirely different state machine for Knuckles (different camera thresholds, different boss, different arena boundaries). Always search for `Player_mode` checks within the routine.

3. **Confusing palette mutation with palette cycling:** Palette _cycling_ (`AnPal_*`) is timer-driven and runs every N frames through a color table. Palette _mutation_ is a one-shot write in a `_Resize` routine triggered by camera position. They are separate systems. Cycling goes in `Sonic3kPaletteCycler`; mutations go in the zone's event handler class. See AGENTS_S3K.md for the full distinction.

4. **Assuming act symmetry:** Act 1 and Act 2 of the same zone often have completely different event routines, parallax band counts, palette cycling channels, and even animated tile scripts. Never assume Act 2 is "the same as Act 1" without verifying.

5. **Missing boss art loading:** Boss objects need their art loaded via PLC before they can render. The PLC load typically happens in a `_Resize` stage just before or during boss spawn. If you note the boss spawn but miss the PLC load, the boss will render with garbage tiles. Always trace PLC loads (`LoadPLC`, `NewPLC`, `Queue_Kos_Module`) in the `_Resize` routine.

6. **Ignoring `s3.asm` for S3KL zones:** While `sonic3k.asm` is the primary source, some S3KL zone routines (AIZ through LBZ) may have additional context or comments in `s3.asm`. Cross-reference when a routine seems incomplete or references undefined labels.

7. **Not checking BackgroundInit/BackgroundEvent:** These routines run at level load and during gameplay to manage background state. They can swap entire background layouts (e.g., MHZ autumn-to-winter, LBZ interior-to-exterior), affecting what the Deform routine scrolls. Missing these means the parallax implementation may break during gameplay transitions.

8. **Only reading AniPLC script data, not the custom `Animated_Tiles_{ZONE}` routine:** Many zones have a custom wrapper around AniPLC that contains camera threshold checks, flag reads, and direct art loads. These custom routines straddle the events/tiles boundary — they read event state (`Dynamic_Resize_routine`, `Boss_flag`, `Level_trigger_array`) and load non-AniPLC art (e.g., AIZ2 FirstTree override). Always read the full `Animated_Tiles_{ZONE}` routine, not just the AniPLC script entries.

9. **Skipping Phase 4 (Shared State Trace):** The most common source of implementation bugs is cross-category dependencies — events loading art to VRAM regions that AniPLC scripts also target, or events setting flags that animated tile routines check. Phase 4 exists specifically to catch these. Skipping it produces specs that look complete but miss critical gating conditions and art ownership conflicts.
