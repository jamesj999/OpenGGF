# S3K HCZ Zone Analysis

## Summary

- **Zone:** Hydrocity Zone (HCZ)
- **Zone Index:** 0x01
- **Zone Set:** S3KL
- **Acts:** 1 and 2
- **Water:** Yes (both acts, with dynamic water height tables and waterline deformation)
- **Palette Cycling:** Yes (1 channel, Act 1 only; Act 2 is rts)
- **Animated Tiles:** Yes (2 AniPLC scripts per act + complex custom AnimateTiles routines for waterline/BG deformation)
- **Character Branching:** Yes -- Knuckles has different end boss arena bounds and different dynamic water heights in Act 2

## Events

### Act 1 (HCZ1_Resize)

**Disassembly location:** `sonic3k.asm` line 39244

**State machine (eventRoutineFg):**

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|
| 0 | Camera X < $360 AND Camera Y >= $3E0 | Write palette mutation to Normal_palette_line_4+$10: $680, $240, $220 (3 colors); advance to stage 2 | Underwater palette correction. Note: ROM has a bug writing $B80 instead of $680, `FixBugs` corrects it |
| 2 | Camera X >= $360 AND Camera Y < $3E0 | Revert to stage 0, write palette restore: $CEE, $ACE, $08A | Player moved back above water threshold |
| 2 | Camera Y >= $500 AND Camera X >= $900 | Write palette restore: $CEE, $ACE, $08A; advance to stage 4 | Player progressed past the initial underwater area |
| 4 | (none) | rts -- idle | Terminal state |

**State machine (eventRoutineBg):** Used for the Act 1 to Act 2 seamless transition.

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|
| 0 (HCZ1BGE_Normal) | Events_fg_5 set (by HCZ2_Resize stage 0) | Queue Kos decompression of HCZ2 secondary blocks/chunks/art; Load PLC $10 and $11 (HCZ2 PLCs); set Events_bg+$16; advance to stage 4 | Seamless act transition prep |
| 0 (normal) | (each frame) | Run HCZ1_Deform, Draw_TileRow, ApplyDeformation | Normal BG scrolling |
| 4 (HCZ1BGE_DoTransition) | Kos_modules_left == 0 | Change zone to $101 (HCZ2), clear Dynamic_resize_routine/Object_load_routine/Rings_manager_routine/Boss_flag, Load_Level (HCZ2 layout), LoadSolids, CheckLevelForWater, set water to $6A0, LoadPalette_Immediate #$D (HCZ2 palette), offset all objects/camera by -$3600 X | Full seamless transition to Act 2 |
| 4 (waiting) | Kos_modules_left != 0 | Run HCZ1_Deform, draw BG | Wait for Kos queue to clear |

**Palette mutations:**
- Stage 0: Writes `$0680, $0240, $0220` to palette line 4, colors 8-10 (Normal_palette_line_4+$10) when underwater area entered
- Stage 2 (revert): Writes `$0CEE, $0ACE, $008A` to same location when player moves back above water or progresses past

**Knuckles differences:** None in Act 1 events -- same state machine for all characters.

### Act 2 (HCZ2_Resize)

**Disassembly location:** `sonic3k.asm` line 39306

**State machine (eventRoutineFg):**

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|
| 0 | Camera X >= $C00 | Set Events_fg_5 (triggers BG transition from wall-move to normal); advance to stage 2 | Signals end of the wall-chase section |
| 2 | (none) | rts -- idle | Terminal state |

**State machine (eventRoutineBg):** Complex, manages the collapsing-wall chase sequence and BG transitions.

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|
| 0 (HCZ2BGE_WallMoveInit) | Level start (BackgroundInit branches here when Camera X < $C00 and Camera Y >= $500) | Allocate Obj_HCZ2Wall, advance to stage 4 | Creates the animated wall object |
| 4 (HCZ2BGE_WallMove) | Events_fg_5 set | Clear Screen_shake_flag if constant; set Draw_delayed values; advance to stage 8 | Wall chase ended, begin normal BG transition |
| 4 (normal) | Each frame | Run HCZ2_WallMove (wall chase logic); DrawBGAsYouMove; PlainDeformation; ShakeScreen_Setup; enable Background_collision_flag when player is within X=$3F0-$C10 and Y=$600-$840 | Wall is chasing the player |
| 8 (HCZ2BGE_NormalTransition) | Draw_PlaneVertSingleBottomUp completes | Run HCZ2_Deform; Reset_TileOffsetPositionEff; set up bottom-up refresh; advance to stage 12 | Transition to normal parallax |
| 12 (HCZ2BGE_NormalRefresh) | Draw_PlaneVertBottomUp completes | Advance to stage 16 | BG fully refreshed |
| 16 (HCZ2BGE_Normal) | Each frame | Run HCZ2_Deform; Draw_TileRow; ApplyDeformation; ShakeScreen_Setup | Normal scrolling with screen shake support |

**Palette mutations:** None in Act 2 event routines.

**Knuckles differences:** None in Act 2 event routines directly, but the end boss uses different arena bounds (see Boss Sequences).

## Boss Sequences

### Act 1 Miniboss

- **Object:** `Obj_HCZMiniboss` at line 139224
- **Spawn trigger:** `Check_CameraInRange` with bounds Y=$300-$400, X=$3500-$3700
- **PLC load:** PLC ID $5B loaded at spawn time
- **Arena boundaries:** Initially Camera_min_Y_pos=$300; then when Camera Y >= $638, locks Y min/max to $638; when Camera X >= $3680, locks X min/max to $3680
- **Music:** Fades out current music, then plays `mus_Miniboss`; loads `Pal_HCZMiniboss` palette to line 1
- **Defeat behavior:** Creates child objects (ChildObjDat_6AD6E), operates as a water-based boss with rockets and engine sub-objects (Obj_HCZMiniboss_Rockets, Obj_HCZMiniboss_Engine)
- **Confidence:** MEDIUM -- entry/arena logic clear, but the boss loop (Obj_HCZ_MinibossLoop, line 139291) and child structure need deeper reading for full implementation

### Act 2 End Boss

- **Object:** `Obj_HCZEndBoss` at line 140778
- **Spawn trigger:** `Check_CameraInRange` with character-dependent bounds:
  - **Sonic/Tails:** Y=$438-$838, X=$3F00-$4100 (tight Y, tighter X at $4000-$4050)
  - **Knuckles:** Y=$000-$3B8, X=$44E0-$4640 (wider arena at $4540-$4590)
- **PLC load:** PLC ID $6C loaded at spawn time
- **Arena boundaries:** Set by `Check_CameraInRange` data (two 4-word entries per character: first = camera trigger region, second = locked arena bounds)
- **Music:** `mus_EndBoss`; loads `Pal_HCZEndBoss` palette to line 1; clears Normal_palette+$1E
- **Defeat behavior:** Complex multi-phase boss with 7 routine stages; creates child objects (ChildObjDat_6BDEC, ChildObjDat_6BD8A); uses MoveSprite2 and Obj_Wait patterns
- **Confidence:** MEDIUM -- entry points and arena setup clear, but full boss AI (7 routine stages at off_6AF24) and sub-object roles need detailed reading

## Parallax

### Act 1 (HCZ1_Deform)

**Disassembly location:** `sonic3k.asm` line 105796

This is a highly complex deformation routine that simulates the waterline extending into the background. It is NOT a simple band-based parallax.

**Core mechanic:** The BG camera Y is computed from `Camera_Y_pos_copy - $610` (water equilibrium point), then divided by 4 and offset by $190. The variable `Events_bg+$10` tracks the difference between effective BG Y and actual camera offset, which drives the waterline tile art swapping.

**Top bands (HScroll_table $00-$18):** 7 bands written individually at decreasing speeds:
- Each band: Camera_X_copy / 4, then successively subtracting Camera_X_copy / 32
- Written in pairs to mirror positions (e.g., offset $00 and $18, $02 and $16, etc.)
- Fastest speed at top, slowest at middle

**Water transition zone (HScroll_table $1A-$19A):** 48 ($30) lines of water deformation
- When BG is above water: fills from $1A downward with progressively faster scroll
- When BG is below water: fills from $19A upward with progressively faster scroll
- Uses `HCZ_WaterlineScroll_Data` binary lookup table for waterline distortion effect
- Per-line scroll using data-driven index into the deformation bands

**Bottom band (HScroll_table $19A):** Single value from the deepest parallax speed

**BG height array:** `HCZ1_BGDeformArray` (defined in `Lockon S3/Screen Events.asm` line 972):
```
$40, 8, 8, 5, 5, 6, $F0, 6, 5, 5, 8, 8, $30, $80C0, $7FFF
```
(Used by ApplyDeformation for the standard parallax bands outside the custom waterline logic)

**Vertical scroll:** BG Y = `(Camera_Y_copy - $610) / 4 + $190`

**Auto-scroll accumulators:** None (no persistent per-frame accumulation)

**Confidence:** LOW -- This is one of the most complex deformation routines in S3K. The waterline scroll logic interweaves per-scanline distortion, dynamic tile art swapping (via AnimateTiles_HCZ1), and camera-relative band calculations. The HCZ_WaterlineScroll_Data binary is opaque without dumping it. Full implementation will require careful study of how the 48-line transition zone is computed.

### Act 1 Background Events (HCZ1_BackgroundEvent)

**Disassembly location:** `sonic3k.asm` line 105702

Two states:
- **Normal (stage 0):** Runs HCZ1_Deform, draws BG tile rows, applies deformation array. When `Events_fg_5` is set, queues HCZ2 art/blocks/chunks via Kos, loads PLCs $10/$11, and transitions to stage 4.
- **Transition (stage 4):** Waits for Kos queue to clear, then performs seamless Act 1->2 transition (loads HCZ2 layout, sets water to $6A0, offsets camera/objects by -$3600 X).

### Act 2 (HCZ2_Deform)

**Disassembly location:** `sonic3k.asm` line 106179

**Core mechanic:** Simpler than Act 1. BG Y = `(Camera_Y_copy - Screen_shake_offset) / 4 + Screen_shake_offset`. BG X = `Camera_X_copy / 2`, then further divided by 8 for slower bands.

**Band layout:** Uses scatter-fill via `HCZ2_BGDeformIndex` (defined in `Lockon S3/Screen Events.asm` line 977):
```
Band 0 (4 entries): HScroll offsets $A, $14, $1E, $2C  -- fastest
Band 1 (3 entries): $C, $16, $20
Band 2 (6 entries): $0, $8, $E, $18, $22, $2A          -- medium
Band 3 (4 entries): $2, $10, $1A, $24
Band 4 (2 entries): $12, $1C
Band 5 (2 entries): $6, $28
Band 6 (2 entries): $4, $26                              -- slowest
```

Each band is written at successively slower speeds (each subtracting `BG_X / 8` per step). The index table maps non-contiguous HScroll_table entries to create an interleaved parallax effect.

After writing bands, computes three difference values stored in Events_bg+$10/12/14 which drive the AnimateTiles_HCZ2 animated tile channels.

**BG height array:** `HCZ2_BGDeformArray`:
```
8, 8, $90, $10, 8, $30, $18, 8, 8, $A8, $30, $18, 8, 8, $B0, $10, 8, $7FFF
```

**Vertical scroll:** BG Y = `(Camera_Y_copy - shake_offset) / 4 + shake_offset`

**Confidence:** MEDIUM -- scatter-fill index pattern is well understood, but the interaction with AnimateTiles_HCZ2 (which reads Events_bg+$10/12/14) adds complexity.

### Act 2 Wall Chase (HCZ2_WallMove)

**Disassembly location:** `sonic3k.asm` line 106129

During the collapsing wall section (BackgroundEvent stages 0-4):
- BG Y = Camera_Y_copy - $500
- BG X = Camera_X_copy - $200 + wall_movement_offset
- Wall starts moving when player X >= $680 (constant screen shake begins)
- Wall speed: $E000 subpixels/frame normally, $14000 if player X >= $A88
- When wall has moved $600 pixels: switches from constant to timed shake ($E frames), plays `sfx_Crash`
- Plays `sfx_Rumble2` every 16th frame during movement
- **Background collision** enabled only when player is within X=$3F0-$C10, Y=$600-$840

**Confidence:** HIGH -- well-commented in disassembly, straightforward state machine.

### Act 2 Screen Shake

**Disassembly location:** `sonic3k.asm` line 105989 (HCZ2_ScreenEvent)

HCZ2_ScreenEvent adds `Screen_shake_offset` to `Camera_Y_pos_copy` before calling `DrawTilesAsYouMove`, and `ShakeScreen_Setup` (line 104183) is called during HCZ2 BackgroundEvent. ShakeScreen supports two modes:
- **Timed** (positive flag): Decrements counter, reads offset from ScreenShakeArray
- **Constant** (negative flag): Uses `Level_frame_counter & $3F` as index into ScreenShakeArray2

## Animated Tiles

### Script: Act 1 Water Surface (AniPLC_HCZ1, channel 0)

- **Disassembly location:** `sonic3k.asm` line 55646
- **Declaration:** `zoneanimdecl -1, ArtUnc_AniHCZ1_0, $30C, 3, $24`
- **Destination VRAM tile:** $30C
- **Frame count:** 3
- **Frame duration:** Variable (0=2 frames, $24=1 frame, $48=2 frames)
- **Tiles per frame:** $24 / $20 = ~1.125 tiles (36 bytes per frame = 1.125 patterns)
- **Confidence:** HIGH

### Script: Act 1/2 Shared Water Animation (AniPLC_HCZ, channel 1)

- **Disassembly location:** `sonic3k.asm` line 55652 (Act 1), 55679 (Act 2)
- **Declaration:** `zoneanimdecl -1, ArtUnc_AniHCZ__1, $115, $10, 6`
- **Destination VRAM tile:** $115
- **Frame count:** 16 ($10)
- **Frame duration:** Variable ping-pong pattern (4,3,2,1,0,1,2,3,4,3,2,1,0,1,2,3)
- **Tiles per frame:** 6 bytes per frame (< 1 pattern; likely 6 bytes = partial tile update)
- **Notes:** Shared between Act 1 and Act 2 (same art label `ArtUnc_AniHCZ__1`)
- **Confidence:** HIGH

### Script: Act 2 Water Surface (AniPLC_HCZ2, channel 0)

- **Disassembly location:** `sonic3k.asm` line 55672
- **Declaration:** `zoneanimdecl 3, ArtUnc_AniHCZ2_0, $25E, 4, $15`
- **Destination VRAM tile:** $25E
- **Frame count:** 4
- **Frame duration:** 3 frames each (constant)
- **Tiles per frame:** $15 / $20 = ~0.66 patterns (21 bytes per frame)
- **Confidence:** HIGH

### Custom: Act 1 Waterline Tile Swapping (AnimateTiles_HCZ1)

- **Disassembly location:** `sonic3k.asm` line 53969
- **Description:** Dynamically loads different tile art based on whether the BG camera is above or below the water equilibrium point. Uses `Events_bg+$10` (computed by HCZ1_Deform) to determine waterline position and swaps between `ArtUnc_AniHCZ1_WaterlineBelow` and `ArtUnc_AniHCZ1_WaterlineAbove` art, plus `HCZ_WaterlineScroll_Data` for per-line distortion. Also fixes lower/upper BG art via `AniHCZ_FixLowerBG`/`AniHCZ_FixUpperBG`.
- **Gating:** Disabled when `Events_bg+$16` is set (boss flag / act transition)
- **VRAM destinations:** Tiles $2DC (below waterline), $2F4 (above waterline), $2E8/$300 (fix BG art)
- **Confidence:** LOW -- highly complex, interleaves DMA queue operations with waterline scroll data lookups. The binary data file `HCZ Waterline Scroll Data.bin` is opaque.

### Custom: Act 2 Multi-Channel BG Animation (AnimateTiles_HCZ2)

- **Disassembly location:** `sonic3k.asm` line 54158
- **Description:** Four independent animated tile channels driven by Events_bg+$10/12/14 (computed by HCZ2_Deform). Each channel loads different BG art at different scroll rates:
  - **Channel 0** (Events_bg+$12 & $1F): Small BG line art (`ArtUnc_AniHCZ2_SmallBGLine`) -> VRAM $2D2
  - **Channel 1** (Events_bg+$12 & $1F): Medium BG art (`ArtUnc_AniHCZ2_2`) -> VRAM $2D6
  - **Channel 2** (Events_bg+$14 & $1F): Large BG art (`ArtUnc_AniHCZ2_3`) -> VRAM $2DE
  - **Channel 3** (Events_bg+$14 & $3F): Extra large BG art (`ArtUnc_AniHCZ2_4`) -> VRAM $2EE, with reversed direction
- **Notes:** Each channel uses a split-DMA pattern (two DMA transfers per update, with a data table selecting the split point). The art sources contain pre-rendered parallax frames at different scroll offsets.
- **Confidence:** MEDIUM -- the DMA split pattern and data tables are understood, but the actual visual effect and art layout need verification.

## Palette Cycling

### Channel 0: Act 1 Water Shimmer (AnPal_HCZ1)

- **Disassembly location:** `sonic3k.asm` line 3287
- **Counter:** `Palette_cycle_counter0`
- **Step:** 8 bytes (4 colors per tick)
- **Limit:** $20 (4 frames in cycle)
- **Timer:** 7 game frames between ticks (Palette_cycle_counter1), BUT when `Palette_cycle_counters+$00` != 0, timer is forced to 0 (every-frame updates)
- **Table:** `AnPal_PalHCZ1` at line 4087
- **Data:** `$EC8, $EC0, $EA0, $E80 | $EC0, $EA0, $E80, $EC8 | $EA0, $E80, $EC8, $EC0 | $E80, $EC8, $EC0, $EA0` (4 rotating blue shades)
- **Destination:** Palette line 3, colors 3-6 (Normal_palette_line_3+$06, 4 bytes + 4 bytes = 8 bytes covering 4 colors); also mirrored to Water_palette_line_3+$06
- **Conditional:** No (always active in Act 1)
- **Confidence:** HIGH

### Act 2 (AnPal_HCZ2)

- **Disassembly location:** `sonic3k.asm` line 3315
- **Content:** `rts` -- Act 2 has NO palette cycling
- **Confidence:** HIGH

### Engine Implementation Status

HCZ palette cycling is listed as **Implemented** in AGENTS_S3K.md zone inventory, but `Sonic3kPaletteCycler.loadCycles()` only has a branch for zoneIndex == 0 (AIZ). HCZ (zoneIndex == 1) has **no implementation** in the cycler. The inventory status appears to be aspirational or reflects a different code path. Needs verification.

## Notable Objects

| Object ID | Name | Description | Zone-Specific? | Notes |
|-----------|------|-------------|----------------|-------|
| -- | Obj_HCZBreakableBar | Breakable barrier/bar | Yes | Line 42726 |
| -- | Obj_HCZWaveSplash | Water surface wave splash effect | Yes | Line 43161, loaded at level start |
| -- | Obj_HCZBlock | Pushable/intractable block | Yes | Line 43233 |
| -- | Obj_HCZSnakeBlocks | Snake block platform path | Yes | Line 50869 |
| -- | Obj_HCZWaterRush | Water rush/current object | Yes | Line 64743 |
| -- | Obj_HCZWaterWall | Water wall barrier | Yes | Line 64835 |
| -- | Obj_HCZCGZFan | Fan (shared with CGZ?) | Shared? | Line 65309 |
| -- | Obj_HCZLargeFan | Large fan | Yes | Line 65583 |
| -- | Obj_HCZHandLauncher | Hand launcher mechanism | Yes | Line 65730 |
| -- | Obj_HCZConveyorBelt | Conveyor belt | Yes | Line 66306 |
| -- | Obj_HCZConveryorSpike | Conveyor belt with spikes | Yes | Line 66626 (note: typo in original disasm label) |
| -- | Obj_HCZSpinningColumn | Spinning column/pole | Yes | Line 68108 |
| -- | Obj_HCZWaterSplash | Water splash effect | Yes | Line 75247, loaded during transitions |
| -- | Obj_HCZTwistingLoop | Twisting tube/loop transport | Yes | Line 76425 |
| -- | Obj_HCZ2Wall | Collapsing wall (Act 2 chase) | Yes | Line 106226 |
| -- | Obj_HCZMiniboss | Act 1 miniboss (underwater) | Yes | Line 139224 |
| -- | Obj_HCZEndBoss | Act 2 end boss | Yes | Line 140778 |

**Note:** Object IDs are not captured here because S3K uses the zone-set dual pointer table system. These would need to be looked up in the Sonic3kObjectRegistry for their S3KL IDs.

## Cross-Cutting Concerns

- **Water:** Present in both acts. HCZ is one of the primary water zones. Water level is dynamic:
  - Act 1: `DynamicWaterHeight_HCZ1` uses a 4-entry table -- water at $500 until X>$900, $680 until X>$2A00, $680 until X>$3500, then $6A0. High bit set on first entry ($8500) forces immediate Mean_water_level write.
  - Act 2: `DynamicWaterHeight_HCZ2` -- Sonic/Tails: water at $700 until X>$3E00, then $7E0. Knuckles: water at $700 until X>$4100, then $360 (high bit set = immediate). Gated by `_unkFAA2` flag.
  - HInt handler is set to `HInt2` specifically for HCZ (zone == 1 check at line 9789).
  - Water palette transition data: `WaterTransition_AIZ1` is loaded as default (line 9797).
  - Act 1->2 transition sets water to $6A0.
- **Screen shake:** Present in Act 2 during the wall chase section. Two modes: constant shake (wall moving) and timed shake (wall stopped). ShakeScreen_Setup called in HCZ2 BackgroundEvent.
- **Act transition:** HCZ1 ends with a **seamless transition** to HCZ2. No boss in Act 1 triggers the transition; instead, when Camera X >= $C00 in HCZ2_Resize stage 0, it sets Events_fg_5 which triggers the BG event to queue HCZ2 art loading and perform the layout swap. Camera/object positions are offset by -$3600 X. This is one of the most complex act transitions in S3K.
- **Character paths:** Knuckles has different dynamic water heights in Act 2 (different table at `word_6EC2`). The end boss uses completely different arena bounds for Knuckles ($44E0-$4640 X vs $3F00-$4100 X for Sonic/Tails), indicating a separate boss arena location.
- **Dynamic tilemap:** The Act 1->2 transition loads entirely new level layout, blocks, chunks, and patterns via Kos queue. The AnimateTiles_HCZ1 routine dynamically swaps waterline tile art based on camera position.
- **PLC loading:**
  - Act transition: PLC $10 and $11 loaded during HCZ1BGE_Normal (line 105729-105730)
  - Miniboss: PLC $5B loaded at spawn (line 139231)
  - End boss: PLC $6C loaded at spawn (line 140791)
- **Unique mechanics:**
  - **Water tube transport** (Obj_HCZTwistingLoop): Players ride through transparent water tubes
  - **Collapsing wall chase** (Act 2): Timed chase sequence with screen shake, background collision switching, and speed-dependent wall velocity
  - **Spinning columns** (Obj_HCZSpinningColumn): Player grabs and spins around vertical poles
  - **Hand launchers** (Obj_HCZHandLauncher): Mechanical hands that launch the player
  - **Water rush/currents** (Obj_HCZWaterRush): Water flow that pushes the player
  - **Conveyor belts** with spike variants
  - **Background tile collision** in Act 2 wall-chase area (Background_collision_flag toggles based on player position)

## Implementation Notes

### Priority Order
1. **Palette cycling (AnPal_HCZ1)** -- Simple, 1 channel, already has engine infrastructure from AIZ. Quick win for visual accuracy in Act 1.
2. **Events (HCZ1_Resize + HCZ2_Resize)** -- Relatively simple state machines (3 stages Act 1, 2 stages Act 2). The palette mutations in Act 1 stage 0/2 are critical for correct underwater colors.
3. **Parallax -- Act 2 normal mode (HCZ2_Deform)** -- Scatter-fill index pattern is well-established. Implement this before the more complex Act 1 deform.
4. **AniPLC scripts** -- Standard zoneanimdecl entries for both acts. Should work with existing Sonic3kPatternAnimator.
5. **Events background (HCZ1_BackgroundEvent)** -- The seamless act transition is complex but architecturally important.
6. **Parallax -- Act 1 (HCZ1_Deform)** -- The waterline deformation is the most complex parallax routine in S3K. Defer until Act 2 is working.
7. **Custom AnimateTiles** -- The waterline tile swapping (Act 1) and multi-channel BG animation (Act 2) are tightly coupled to the deform routines. Implement alongside parallax.
8. **Boss implementations** -- Depend on PLC art loading infrastructure and arena event logic.

### Dependencies
- Palette cycling requires `AnPal_PalHCZ1` ROM address to be added to `Sonic3kConstants.java`
- Events require the `AbstractLevelEventManager` dual-routine framework (already exists)
- Act transition (HCZ1BGE_DoTransition) requires Kos queue support and seamless level reload capability
- Act 2 wall chase requires screen shake infrastructure (`ShakeScreen_Setup`)
- AnimateTiles_HCZ1/HCZ2 require DMA queue simulation and the corresponding uncompressed art ROM addresses
- End boss Knuckles path requires `character_id` checking in boss spawn code

### Known Risks
- **LOW confidence: HCZ1_Deform waterline logic** -- The 48-line transition zone with HCZ_WaterlineScroll_Data binary lookup is the hardest piece. May need to dump and analyze the binary data file manually.
- **LOW confidence: AnimateTiles_HCZ1** -- Tightly coupled to HCZ1_Deform via Events_bg+$10. The waterline tile art swapping and DMA queue interaction are non-trivial.
- **Seamless act transition** -- The HCZ1->HCZ2 seamless transition is one of the most complex in S3K (layout reload, camera offset, object offset, Kos queue dependency). May need engine-level support for mid-gameplay level reloads.
- **Background collision flag** -- The Act 2 wall chase enables BG collision only in a specific player position range. The engine's collision system may need to support toggling BG collision dynamically.
- **Palette cycling status discrepancy** -- AGENTS_S3K.md lists HCZ as "Implemented" but the code only implements AIZ. Needs clarification before work begins.
