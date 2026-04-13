# S3K LBZ Zone Analysis

## Summary

- **Zone:** Launch Base Zone (LBZ)
- **Zone Index:** 0x05
- **Zone Set:** S3KL
- **Acts:** 1 and 2
- **Water:** Yes (Act 1 dynamic height table, Act 2 Knuckles-only water + Death Egg sequence water; waterline deformation, water palette transitions)
- **Palette Cycling:** Yes (1 channel per act, shared logic, different palette data)
- **Animated Tiles:** Yes (custom `AnimateTiles_LBZ1/LBZ2` plus the shared declarative `AniPLC_LBZ1/LBZ2` scripts; Act 2 also swaps waterline art at runtime)
- **Character Branching:** Yes -- extensive. Knuckles has different BG layout (no Death Egg), different boss arena, different level size adjustments, separate cutscene objects, and Act 2 water behavior
- **Seamless Transition:** Yes -- Act 1 to Act 2 transition with layout reload, camera offset -$3A00 X, water palette setup
- **Screen Shake:** Yes (both acts use ShakeScreen_Setup; Act 1 building collapse sequence, Act 2 Death Egg rise/detach sequence)
- **Special VInt Routines:** Yes (Act 2 uses 4 custom VInt routines for Death Egg platform window/scroll-A manipulation)
- **Layout Modification:** Yes (Act 1 has 4 dynamic layout mod regions; Act 2 has 1 layout mod + Death Egg layout adjustment)
- **VScroll Deformation:** Yes (Act 1 FG uses cell-based VScroll for building collapse effect)

## Events

### Act 1 (LBZ1_Resize)

**Disassembly location:** `sonic3k.asm` line 39481, `s3.asm` line 32635

**State machine (eventRoutineFg):** None -- `LBZ1_Resize` is a bare `rts`. All Act 1 event logic is handled by the ScreenEvent/BackgroundEvent system instead.

### Act 2 (LBZ2_Resize)

**Disassembly location:** `sonic3k.asm` line 39485, `s3.asm` line 32639

**State machine (eventRoutineFg):**

| Stage | Trigger | Action | Notes |
|-------|---------|--------|-------|
| 0 | Camera X >= $3BC0 AND Camera Y >= $500 | Queue Kos decompression: LBZ2_16x16_DeathEgg_Kos -> Block_table, LBZ2_128x128_DeathEgg_Kos -> RAM_start, LBZ2_8x8_DeathEgg_KosM -> VRAM $000, ArtKosM_LBZ2DeathEgg2_8x8 -> VRAM ArtTile_Explosion; advance to stage 2 | Loads Death Egg art/blocks/chunks when player reaches far right of Act 2 |
| 2 | (none) | rts -- idle | Terminal state, art loaded |

**Palette mutations:** None in Resize routine.

**Knuckles differences:** None -- same Resize trigger for all characters.

### Act 1 Screen Events (LBZ1_ScreenInit / LBZ1_ScreenEvent)

**Disassembly location:** `sonic3k.asm` line 110833 (init), line 110880 (event); `s3.asm` line 74671 (init), line 74713 (event)

**LBZ1_ScreenInit:**
1. Initializes VScroll offsets at HScroll_table+$70/$74/$78/$7C relative to BG Y - $76
2. Modifies chunk byte at layout position to $DB (rotates chunks via LBZ1_RotateChunks)
3. Clears VScroll entries at HScroll_table+$148 (14 longwords)
4. Runs LBZ1_EventVScroll to set initial VScroll state
5. Sets up tile offsets for VScroll array (12 entries at HScroll_table+$100)
6. If not Knuckles and Camera X >= $3B60: allocates Obj_LBZ1InvisibleBarrier
7. Calls LBZ1_ModEndingLayout (clears building area for end-of-level boss setup)
8. Runs LBZ1_CheckLayoutMod for initial layout state
9. Refreshes full plane

**LBZ1_ScreenEvent:**
1. Adds Screen_shake_offset to Camera_Y_pos_copy
2. Calls LBZ1_KnuxLevelSizeAdjust (Knuckles-only camera min Y adjustment)
3. Checks Events_fg_4 == $55: if so, modifies layout chunks for boss arena (different chunk writes for Sonic vs Knuckles - Sonic gets single chunk at row $7D, Knuckles gets 3 chunks at rows $78-$7A)
4. Normal operation: checks player position against 4 layout mod regions, applies/reverts layout modifications as player enters/exits
5. When no layout change needed: runs LBZ1_EventVScroll, then DrawTilesVDeform with FGVScrollArray

**Layout Mod System (LBZ1_CheckLayoutMod):**

4 rectangular regions that dynamically modify the FG layout when the player enters/exits:

| Mod | X Range | Y Range | Description |
|-----|---------|---------|-------------|
| 1 | $13E0-$16A0 | $100-$580 | Layout row at offset $26, 9 rows, 8 bytes wide |
| 2 | $2160-$2520 | $000-$700 | Layout row at offset $42, 14 rows, 10 bytes wide |
| 3 | $3A60-$3BA0 | $000-$600 | Layout row at offset $74, 12 rows, 4 bytes wide |
| 4 | $3DE0-$3FA0 | $000-$300 | Layout row at offset $7A, 6 rows, 6 bytes wide |

Exit ranges (slightly wider than entry ranges to prevent toggling):

| Mod | Exit X Range |
|-----|-------------|
| 1 | $1376-$170A |
| 2 | $20F6-$258A |
| 3 | $39F6-$3C0A |
| 4 | $3D76-$400A |

**Note:** Mod 3 checks `Events_bg+$02` (set by LBZ1_ModEndingLayout) to disable itself after the ending layout is applied.

**LBZ1_EventVScroll (Building Collapse Sequence):**

Triggered when `Events_fg_4` is set (non-zero). This is the dramatic building collapse at the end of Act 1.

- When Events_fg_4 becomes negative: sets to 1, enables Special_V_int_routine $04 (VScroll on), spawns Obj_LBZ1InvisibleBarrier
- Each frame: applies collapse scroll speeds from LBZ1_CollapseScrollSpeed array (8 words: $1EE, $1F2, $C7, $1B3, $1B7, $198, $E, $139) to 10 VScroll columns at HScroll_table+$14C
- Columns sink at different rates based on speed table, capped at -$300 pixels
- Every 16th frame while columns still moving: plays sfx_BigRumble
- When all 10 columns reach -$300: clears Screen_shake_flag, clears Events_fg_4, sets Special_V_int_routine $0C (VScroll off), calls LBZ1_ModEndingLayout, clears all VScroll offsets, plays sfx_Crash

**LBZ1_KnuxLevelSizeAdjust:**

Knuckles-only (Player_mode == 3). When Knuckles is at X=$37C0-$3800 and Y=$600-$680:
- If Y >= $640: Camera_min_Y_pos = 0
- If Y < $640: Camera_min_Y_pos = $40C

**Obj_LBZ1InvisibleBarrier:**

Solid wall object at X=$3BC0, Y=$100, width=$40. Prevents backtracking during boss area. Deleted when Camera X >= $3D80.

**FGVScrollArray:**
```
$3B60, $10, $10, $10, $10, $10, $10, $10, $10, $10, $10, $7FFF
```
Activates at X=$3B60, 10 columns of $10 pixels each (160 pixels total). Used for the cell-based VScroll deformation of the building collapse.

### Act 2 Screen Events (LBZ2_ScreenInit / LBZ2_ScreenEvent)

**Disassembly location:** `sonic3k.asm` line 111290 (init), line 111302 (event)

**LBZ2_ScreenInit:**
1. Calls Adjust_LBZ2Layout (modifies BG layout chunks, rotates chunks, sets specific chunk values at various layout positions)
2. Sets Events_routine_fg = 4 (starts at Normal state, skipping FromTransition)
3. Clears Events_bg+$14, _unkEEA0, Event_LBZ2_DeathEgg, Events_fg_3
4. Calls LBZ2_LayoutMod (single layout modification at offset $94, 6 rows)
5. Refreshes full plane

**LBZ2_ScreenEvent:**
1. Adds Screen_shake_offset to Camera_Y_pos_copy
2. Dispatches via Events_routine_fg:

| Stage | Label | Action | Notes |
|-------|-------|--------|-------|
| 0 | LBZ2SE_FromTransition | Knuckles: immediately apply LBZ2_LayoutMod, advance to Normal. Sonic: wait until Player X >= $60A, then apply layout mod, advance to Normal, refresh screen | Only runs when coming from Act 1 seamless transition |
| 4 | LBZ2SE_Normal | DrawTilesAsYouMove | Standard tile drawing |

**Adjust_LBZ2Layout:**

Modifies multiple layout positions:
- Copies chunk byte from one layout row to another
- Sets chunk $DB at row offset 5
- Rotates chunks via LBZ1_RotateChunks (reuses Act 1 routine)
- Sets chunk $58 at layout position $8A
- Sets chunk $55 at layout position $8A of adjacent row

### Act 1 Background Events (LBZ1_BackgroundInit / LBZ1_BackgroundEvent)

**Disassembly location:** `sonic3k.asm` line 111163 (init), line 111190 (event); `s3.asm` line 75086 (init), line 75099 (event)

**LBZ1_BackgroundInit:**
1. If Knuckles: modifies BG layout rows to remove Death Egg from background (writes chunk IDs $E4, $E6, $E0, $EC, $EE, $E8 to first two BG rows)
2. Calls LBZ1_Deform (S3 code, see Parallax section)
3. Resets tile offset positions
4. Sets HScroll_table+$002 and clears HScroll_table+$004
5. Draws full BG plane using LBZ1_BGDrawArray, then applies LBZ1_BGDeformArray

**LBZ1_BackgroundEvent (Events_routine_bg):**

| Stage | Label | Action | Notes |
|-------|-------|--------|-------|
| 0 | LBZ1BGE_Normal | Normal BG update: run LBZ1_Deform, Draw_BG with LBZ1_BGDrawArray, ApplyDeformation with LBZ1_BGDeformArray, Apply_FGVScroll with LBZ1_FGVScrollArray, ShakeScreen_Setup. When Events_fg_5 is set: queue LBZ2 art (128x128, 16x16, 8x8 secondary), advance to stage 4 | Seamless transition prep |
| 4 | LBZ1BGE_DoTransition | Wait for Kos_modules_left == 0, then: change zone to $601 (LBZ2), clear routines, Load_Level, LoadSolids, CheckLevelForWater, set VDP register $8014 (water enable), LoadPalette_Immediate #$17 (LBZ2 palette), offset all objects/camera by -$3A00 X, clear events, set Events_bg+$10 = $40 (or negate if Camera Y >= $540), start LBZ2 tile animation, clear Events_routine_bg | Full seamless transition to Act 2 |

### Act 2 Background Events (LBZ2_BackgroundInit / LBZ2_BackgroundEvent)

**Disassembly location:** `sonic3k.asm` line 111383 (init), line 111405 (event)

**State ownership:**
- `Events_bg+$10` is the waterline equilibrium value and the only input to the waterline art swap helper.
- `Events_bg+$12` is the Knuckles-area art reload offset consumed by the Act 2 animated-tile routine.
- `Events_bg+$16` is the detach scroll counter.
- `Events_bg+$02` and `Events_bg+$06` are the Death Egg motion integrators.
- `Events_fg_3` is the vertical bias latch used only by the Death Egg deformation path.

**LBZ2_BackgroundInit:**
1. Sets `Events_routine_bg = 8` so the handler starts in the normal state.
2. Runs `LBZ2_Deform`, resets tile offsets, and refreshes the full BG plane.
3. Clamps `Events_bg+$10` to the +/- `$40` range so the waterline art swap starts from a legal offset.
4. Applies `LBZ2_BGDeformArray`.

**LBZ2_BackgroundEvent (Events_routine_bg):**

This is a 6-state machine. The background event owns the Death Egg sequence; the robotnik-ship object only feeds movement and player lockout into the shared state.

| State | Label | Behavior |
|-------|-------|----------|
| 0 | `LBZ2BGE_FromTransition` | Calls `LBZ2_Deform`, resets tile-offset state, seeds `Draw_delayed_position` from the current BG Y, sets `Draw_delayed_rowcount = $F`, then advances to state 4. |
| 4 | `LBZ2BGE_Refresh` | Calls `LBZ2_Deform` and finishes the bottom-up plane refresh via `Draw_PlaneVertBottomUp`. When the refresh completes, advances to state 8. |
| 8 | `LBZ2BGE_Normal` | Normal scrolling path. If `Events_fg_5` is clear, it runs `LBZ2_Deform`, draws one tile row, applies `LBZ2_BGDeformArray`, and shakes the screen. If `Events_fg_5` is set, it jumps into the Death Egg setup block. |
| 12 | `LBZ2BGE_DeathEgg` | Handles the rising Death Egg. Plays `sfx_DeathEggRiseLoud` while `Event_LBZ2_DeathEgg` and `Screen_shake_flag` are both active, then waits for `Events_fg_5` to kick off the falling sequence. |
| 16 | `LBZ2BGE_PlatformDetach` | Waits for the special VInt copy to finish. Then it increments `Events_bg+$16` once every 4 frames, scrolls Plane A upward by that amount, and after `$28` ticks swaps to the restore VInt and clears the 3x2 chunk patch under the platform. |
| 20 | `LBZ2BGE_Falling` | While `Events_fg_5` remains set, it locks scroll and nudges the camera upward by 2 pixels per frame. Otherwise it keeps running the Death Egg deformation path. |

**Death Egg setup block:**
- Clears `Events_fg_5`.
- Copies `8(a3) -> (a3)` and `$C(a3) -> 4(a3)` to slightly reframe the background data.
- Locks the camera to `Camera_max_X_pos = $4390` and `Camera_max_Y_pos = Camera_target_max_Y_pos = $668`.
- Sets `Events_bg+### Act 2 Background Events (LBZ2_BackgroundInit / LBZ2_BackgroundEvent)

**Disassembly location:** `sonic3k.asm` line 111383 (init), line 111405 (event)

**State ownership:**
- `Events_bg+$10` is the waterline equilibrium value and the only input to the waterline art swap helper.
- `Events_bg+$12` is the Knuckles-area art reload offset consumed by the Act 2 animated-tile routine.
- `Events_bg+$16` is the detach scroll counter.
- `Events_bg+$02` and `Events_bg+$06` are the Death Egg motion integrators.
- `Events_fg_3` is the vertical bias latch used only by the Death Egg deformation path.

**LBZ2_BackgroundInit:**
1. Sets `Events_routine_bg = 8` so the handler starts in the normal state.
2. Runs `LBZ2_Deform`, resets tile offsets, and refreshes the full BG plane.
3. Clamps `Events_bg+$10` to the +/- `$40` range so the waterline art swap starts from a legal offset.
4. Applies `LBZ2_BGDeformArray`.

**LBZ2_BackgroundEvent (Events_routine_bg):**

This is the most complex BG event handler in the zone, managing the entire Death Egg rise sequence.

| Stage | Label | Action | Notes |
|-------|-------|--------|-------|
| 0 | LBZ2BGE_FromTransition | Run LBZ2_Deform, set up bottom-up BG refresh ($E0 row offset, 15 rows), advance to stage 4 | Only used during seamless transition from Act 1 |
| 4 | LBZ2BGE_Refresh | Run LBZ2_Deform, draw BG rows bottom-up until complete, then advance to stage 8 | BG refresh during transition |
| 8 | LBZ2BGE_Normal | Normal operation. When Events_fg_5 set: begin Death Egg sequence. Otherwise: run LBZ2_Deform, Draw_TileRow, ApplyDeformation with LBZ2_BGDeformArray, ShakeScreen_Setup | Standard scrolling |
| 8->12 | Death Egg init | Set Camera_max X=$4390, Camera_max Y=$668, set scroll lock, set Event_LBZ2_DeathEgg, set timer $3C, set BG speed $1E00, set BG accel $6200, advance to stage 12 | Locks camera for Death Egg sequence |
| 12 | LBZ2BGE_DeathEgg | When Event_LBZ2_DeathEgg and Screen_shake_flag: play sfx_DeathEggRiseLoud every 16 frames. When Events_fg_5 set again: run LBZ2_EndFallingAccel; when BG accel goes negative, set up window copy (Draw_delayed_rowcount=$1B, Special_V_int_routine=$10), clear Water_flag, advance to stage 16 | Death Egg rising with rumble |
| 16 | LBZ2BGE_PlatformDetach | Wait for Special_V_int_routine to clear. Every 4 frames: increment Events_bg+$16 counter. When counter reaches $28: clear counter, set Special_V_int_routine=$18, clear 3x2 chunk area at $580Y/$4380X in layout, advance to stage 20. While counter active: modify V_scroll_value by counter offset | Platform scrolls off screen |
| 20 | LBZ2BGE_Falling | When Events_fg_5 set: set scroll lock, copy Camera_Y_pos to copy, decrement Camera_Y_pos by 2 (scroll up). Otherwise: continue Death Egg deformation | Camera scrolls up as Death Egg falls |

**Death Egg Deform routines:**

During stages 12-20, the system uses `LBZ2_DeathEggDeform` (line 111847) instead of `LBZ2_Deform`. This variant:
- Subtracts `Events_bg+$02` and `Events_bg+$06` from the water offset calculation
- Subtracts `Events_fg_3` from the BG Y
- Tracks `_unkEEA0` for additional BG scroll offset wrapping
- Uses `LBZ2_DEBGDeformArray` instead of `LBZ2_BGDeformArray`

**LBZ2_DeathEggMoveScreen (line 112060):**
- Moves camera X to follow player in the Robotnik ship (only rightward, up to $4390)
- When Camera X >= $4390 or Camera Y >= $668: locks camera min to max
- Manages BG movement via `Events_bg+$02` (Y offset, speed `_unkEEA2`) and `Events_bg+$04` (X offset, speed `_unkEE9C`)
- Updates Target_water_level by Y+X offsets, capped at $F80
- When scroll locked: adds Y movement to both the player ship object and Camera_Y_pos

**LBZ2_EndFallingAccel (line 112146):**
- Decreases `_unkEEA2` by $100 until 0
- Then decreases `_unkEE9C` (long) by $100 until <= $FFFE8000
- This creates a deceleration -> reverse acceleration effect for the falling Death Egg platform

**Special VInt Routines ($10/$14/$18/$1C):**

| ID | Label | Purpose |
|----|-------|---------|
| $10 | SpecialVInt_LBZ2WindowCopy | Copies Death Egg platform nametable data from Scroll A to Window plane, row by row (Draw_delayed_rowcount times) |
| $14 | SpecialVInt_LBZ2ScrollAClear | Clears 6 cell lines from VRAM Scroll A at $C900, sets Window base $8000, Window starts 5 cells down |
| $18 | SpecialVInt_LBZ2ScrollAClear2 | Clears VRAM Scroll A at $C600 (remainder of upper area) |
| $1C | SpecialVInt_LBZ2WindowClear | Copies window data back to Scroll A at $C900 (6 lines), then clears window position register |

These routines use VDP Window plane manipulation to keep the Death Egg platform stationary on screen while the rest of the level scrolls.

## Boss SequencesE = $3C`, `_unkEEA2 = $1E00`, `_unkEE9C = $6200`, `Scroll_lock`, and `Event_LBZ2_DeathEgg`.
- Advances to state 12.

**Death Egg motion and VInt coordination:**
- `LBZ2_DeathEggMoveScreen` owns the ship/camera coupling. It only moves the camera to the right, caps the arena at `$4390/$668`, accumulates vertical motion in `Events_bg+$02` and horizontal motion in `Events_bg+$06`, and adds the current Y delta back into the ship object and camera when scroll is locked.
- `LBZ2_EndFallingAccel` is the deceleration routine. It subtracts `$100` from `_unkEEA2` until that integrator reaches 0, then subtracts `$100` from `_unkEE9C` until the long value drops to `#$FFFE8000` or lower.
- When `_unkEE9C` turns negative in `LBZ2BGE_DeathEgg`, the routine clears `Events_fg_5`, sets `Draw_delayed_rowcount = $1B`, starts special VInt `$10`, clears `Water_flag`, and moves to state 16.

**Special VInt routines (`$10/$14/$18/$1C`):**

| ID | Label | Behavior |
|----|-------|----------|
| `$10` | `SpecialVInt_LBZ2WindowCopy` | Reads the Death Egg platform strip from Plane A and writes the same rows into the Window plane. The row source is derived from `Draw_delayed_rowcount`, so the transfer repeats once per platform slice. |
| `$14` | `SpecialVInt_LBZ2ScrollAClear` | Clears 6 cell lines at VRAM `$C900`, then switches the Window base to `$8000` and positions the Window 5 cells down from the top. |
| `$18` | `SpecialVInt_LBZ2ScrollAClear2` | Clears the remainder of the upper Plane A area at `$C600`. |
| `$1C` | `SpecialVInt_LBZ2WindowClear` | Reads the Window copy back, restores the 6 Plane A lines at `$C900`, and clears the Window position register. |

This is the full VDP choreography for the platform detach: copy the platform into the Window plane, clear the exposed Scroll A strip as it moves, then restore the platform strip after the platform has finished scrolling away.
## Boss Sequences

### Act 1 Miniboss

- **Object:** `Obj_LBZMiniboss` at `sonic3k.asm` line 151366
- **Spawn:** Created as child of `Obj_LBZMinibossBox` (line 192357) which handles camera lock and approach detection
- **Arena boundaries:** Camera min X = $3C00, max X = $3EA0 (set by MinibossBox)
- **PLC load:** None explicit -- art loaded via LBZMinibossBox setup
- **Art:** `Map_LBZMiniboss`, `ArtTile_LBZMiniboss` palette line 1, `Pal_LBZMiniboss`
- **HP:** 6 hits (collision_property = 6)
- **Routine stages:** 6 stages (init, wait, approach, attack phases, defeat)
- **Child objects:** 3 child object sets (ChildObjDat_7296E, 72976, 7297C)
- **Knuckles variant:** `Obj_LBZMinibossBoxKnux` (line 192537) -- different spawn box behavior
- **Confidence:** MEDIUM -- entry/arena logic clear, but full AI needs deeper reading

### Act 2 Final Boss 1 (Sonic/Tails path)

- **Object:** `Obj_LBZFinalBoss1` at line 151927
- **Spawn:** Created by screen event or by `Obj_LBZFinalBossKnux` converting to this
- **Arena boundaries:** Not set in boss init -- managed by screen events (Death Egg sequence locks camera)
- **PLC load:** PLC ID $71; also loads `Obj_Song_Fade_Transition` with `mus_EndBoss`
- **Art:** `ObjDat_LBZFinalBoss1`; `Pal_LBZFinalBoss1`
- **HP:** 9 hits (collision_property = 9)
- **Routine stages:** 6 stages (init, wait, rise, attack phases, defeat)
- **Child objects:** 4 child sets (MakeRoboHead3, ChildObjDat_737F0, 73766, 737E8)
- **Confidence:** MEDIUM

### Act 2 Final Boss (Knuckles path)

- **Object:** `Obj_LBZFinalBossKnux` at line 152493
- **Spawn trigger:** `Check_CameraInRange` with bounds Y=$000-$428, X=$4280-$4380; arena locks at Y=$328, X=$4300
- **Music:** `mus_EndBoss`
- **PLC load:** PLC ID $71 + extra PLC (ArtNem_RobotnikShip, ArtNem_BossExplosion)
- **Behavior:** After initial wait, converts to `Obj_LBZFinalBoss1` (reuses Sonic's boss logic)
- **Confidence:** MEDIUM

### Act 2 End Boss

- **Object:** `Obj_LBZEndBoss` at line 153341
- **Spawn trigger:** `Check_CameraInRange` with bounds Y=$460-$6A0, X=$3900-$3B20; arena locks at Y=$5A0, X=$3A20
- **PLC load:** PLC ID $77; loads `ArtKosM_LBZEndBoss` via Queue_Kos_Module
- **Art:** `ObjDat_LBZEndBoss`; `Pal_LBZEndBoss`
- **HP:** 8 hits (collision_property = 8)
- **Music:** `mus_EndBoss`
- **Routine stages:** 7 stages
- **Arena lock:** Camera min X = Camera max X = $39F0, Scroll_lock enabled
- **Child objects:** Multiple child sets (ChildObjDat_7414C, 7418A, 7415A)
- **Confidence:** MEDIUM

## Parallax

### Act 1 (LBZ1_Deform)

**Disassembly location:** `s3.asm` line 75194 (S3 code, called from sonic3k.asm via `jsr (LBZ1_Deform).l`)

This is a relatively straightforward parallax deformation for the cityscape/building background.

**BG Y calculation:**
```
BG_Y = (Camera_Y_copy - Screen_shake_offset) / 16 + Screen_shake_offset
```

**BG X calculation:**
```
base_X = Camera_X_copy / 16
slower_X = base_X / 2
```

**Band structure (HScroll):**

| HScroll Offset | Content | Speed |
|----------------|---------|-------|
| $000 | Camera_X_BG_copy (base_X) | 1/16 |
| $008 | Same as $000 | 1/16 |
| $00A | Band 1 | base_X + 2*slower_X |
| $00C | Band 2 | band1 + slower_X/4 |
| $00E | Band 3 | band2 + slower_X/4 |
| $010 | Band 4 | band3 + slower_X/4 |
| $012 | Band 5 | band4 + slower_X/4 |

After computing, adds constant offsets:
- Events_bg+$10 += $A
- Camera_X_BG_copy += $A
- HScroll_table+$000 += $A
- HScroll_table+$008 += $A
- HScroll_table+$00A += 4
- HScroll_table+$00C -= 2
- HScroll_table+$00E += 7

**BGDeformArray:**
```
$D0, $18, 8, 8, $7FFF
```
Meaning: first band = $D0 lines (208px), then $18 (24px), 8, 8, end.

**BGDrawArray:**
```
$D0, $7FFF
```
Single draw region of $D0 lines.

**FGVScrollArray (for building collapse):**
```
$3B60, $10, $10, $10, $10, $10, $10, $10, $10, $10, $10, $7FFF
```
Starting at X=$3B60, 10 columns of 16 pixels each. Used with DrawTilesVDeform for the cell-based VScroll effect during the building collapse sequence.

**Confidence:** HIGH -- relatively simple BG parallax with fixed band speeds.

### Act 2 Normal (LBZ2_Deform)

**Disassembly location:** `sonic3k.asm` line 111581

This is a highly complex deformation routine managing waterline effects, clouds, and underwater background scrolling. It is similar in complexity to HCZ's waterline deformation.

**BG Y calculation:**
```
raw = Camera_Y_copy - Screen_shake_offset - $5F0
BG_Y = raw * 7/16 + $2C0 + Screen_shake_offset
waterline_equilibrium = BG_Y_offset - raw  (stored in Events_bg+$10)
```

**Cloud layer (top of BG):**

Uses `LBZ2_CloudDeformArray` (13 entries specifying HScroll offsets):
```
$16, $E, $A, $14, $C, $6, $18, $10, $12, $2, $8, $4, $0
```

Each cloud band: `base = Camera_X / 64`, auto-scrolled by accumulator at HScroll_table+$1E2 (incremented by $E00 per frame). Cloud positions are scatter-filled to non-contiguous HScroll_table entries for interleaved parallax.

**Mid-ground bands (HScroll $01A-$01E):**

Two bands at Camera_X / 16 and Camera_X / 16 + Camera_X / 32, covering the building/structure area between clouds and waterline.

**Waterline transition zone ($40 lines):**

When `Events_bg+$10` (waterline equilibrium) is between -$40 and +$40:
- Uses `LBZ_WaterlineScroll_Data` binary (4160 bytes = 65 positions x 64 bytes each)
- Each byte in the data indexes into the existing HScroll_table band values
- Below water (positive equilibrium): reads forward from data table, writes to HScroll_table upward from $09E
- Above water (negative equilibrium): reads forward, writes to HScroll_table downward from $09E
- This creates a smooth visual transition where each scanline blends between BG bands above/below the waterline

**Underwater area (below waterline):**

Bottom section uses `LBZ2_BGUWDeformRange` array (5 entries: 7, 1, 3, 1, 7) specifying how many 4-pixel groups to fill per band. Total = (7+1+3+1+7+5) * 4 = ~92 lines. Written from HScroll_table+$1E2 downward at progressively slower speeds.

**Water wave effect:**

When waterline is visible (equilibrium < $40): applies `LBZ_WaterWaveArray` (64 words of sine-like offsets: values -2 to +2) to HScroll_table entries near the waterline. Indexed by `Level_frame_counter / 2`, creating an animated ripple.

**BGDeformArray:**
```
$C0, $40, $38, $18, $28, $10, $10, $10, $18, $40, $20, $10, $20, $70, $30, $80E0, $20, $7FFF
```
(The $80E0 entry has bit 15 set, indicating some special handling in ApplyDeformation.)

**Confidence:** LOW -- this is one of the most complex deformation routines in S3K. The waterline scroll data interaction, underwater band system, and cloud scatter-fill all interweave. Full implementation requires careful study of the binary data file and the exact HScroll_table layout.

### Act 2 Death Egg (LBZ2_DeathEggDeform)

**Disassembly location:** `sonic3k.asm` line 111847

Variant of LBZ2_Deform used during the Death Egg rise/detach sequence. Key differences:

- Subtracts additional offsets `Events_bg+$02` and `Events_bg+$06` from waterline calculation
- Subtracts `Events_fg_3` from BG Y
- Adds `_unkEEA0` to BG Y (wrapping offset for scrolling BG)
- Forces waterline equilibrium to $7FFF when `_unkEE9C` is negative (disables waterline during falling)
- Uses `LBZ2_DEBGDeformArray` instead of `LBZ2_BGDeformArray`
- Cloud system identical to normal deform
- Mid-ground has 3 bands instead of 2 (slightly different layout)
- Waterline uses same `LBZ_WaterlineScroll_Data` but only the below-water path
- Water wave uses `LBZ_WaterWaveArray2` (different phase offset in the wave data)

**DEBGDeformArray:**
```
$38, $18, $28, $10, $10, $10, $18, $40, $38, $18, $28, $10, $10, $10, $18, $40, $20, $10, $20, $70, $60, $10, $805F, $7FFF
```
Notably larger than the normal array -- the Death Egg BG has additional repeated band groups.

**Confidence:** LOW -- inherits all complexity from LBZ2_Deform and adds Death Egg movement offsets.

## Animated Tiles

### Script: Act 1 Conveyor/Machine Animation (AniPLC_LBZ1)

- **Disassembly location:** `sonic3k.asm` line 55900
- **Declaration:** `zoneanimdecl 2, ArtUnc_AniLBZ1_0, $365, 4, 8`
- **Destination VRAM tile:** $365
- **Frame count:** 4
- **Frame duration:** 2 frames each (constant)
- **Tiles per frame:** 8 bytes = 0.25 patterns (likely 8 bytes of tile data per frame)
- **Frame offsets:** 0, 8, $10, $18
- **Confidence:** HIGH

### Script: Act 1 + Act 2 Shared (AniPLC_LBZSpec)

Used by both acts (Act 1 references it, Act 2 includes it in AniPLC_LBZ2):

**Channel 0:**
- **Declaration:** `zoneanimdecl 7, ArtUnc_AniLBZ2_0, $170, 4, 5`
- **Destination VRAM tile:** $170
- **Frame count:** 4
- **Frame duration:** 7 frames each (constant)
- **Tiles per frame:** 5 bytes per frame
- **Frame offsets:** 0, 5, $A, $F

**Channel 1:**
- **Declaration:** `zoneanimdecl 7, ArtUnc_AniLBZ2_1, $175, 6, 4`
- **Destination VRAM tile:** $175
- **Frame count:** 6
- **Frame duration:** 7 frames each (constant)
- **Tiles per frame:** 4 bytes per frame
- **Frame offsets:** 0, 4, 8, $C, $10, $14

### Script: Act 2 (AniPLC_LBZ2)

- **Disassembly location:** `sonic3k.asm` line 55926
- **Identical to AniPLC_LBZSpec** (same two channels with same parameters)
- **Confidence:** HIGH

### Custom: Act 1 Complex Tile Animation (AnimateTiles_LBZ1)

**Disassembly location:** `sonic3k.asm` line 54539

Three independent channels:

**Channel 0 (Basic cycle):**
- Counter at Anim_Counters+$00, period = 3 frames
- 16-frame cycle (index ANDed with $F)
- Each frame: $100 bytes from ArtUnc_AniLBZ__0 at offset `(frame << 9)` -> VRAM tile $160
- Shared art between Act 1 and Act 2

**Channel 1 (BG position-dependent):**
- Counter at Anim_Counters+$02, tracks `(Events_bg+$10 - Camera_X_BG_copy) & $1F`
- Only updates when position changes
- Complex split-DMA based on position within 32-pixel cycle:
  - Position bits 0-2: select sub-frame (0-7), each is 128+512 = 640 bytes of art from ArtUnc_AniLBZ1_1
  - Position bits 3-4: select split point from word_27EE0 table ($140, 0, $F0, $50, $A0, $A0, $50, $F0)
  - Two DMA transfers with split, targeting VRAM starting at tile $350
  - Plus 1 additional DMA of $10 bytes from ArtUnc_AniLBZ1_2

**Channel 2 (Shared with AniPLC_LBZSpec):**
- Calls the standard zoneanimdecl-based animation via `loc_286E8`
- Also runs AniPLC_LBZSpec channel at Anim_Counters+$C

**Confidence:** MEDIUM -- Channel 0 and 2 are straightforward, Channel 1 is complex with position-dependent split-DMA.

### Custom: Act 2 Complex Tile Animation (AnimateTiles_LBZ2)

**Disassembly location:** `sonic3k.asm` line 54634

Three main channels:

**Channel 0 (Same as Act 1 Channel 0):**
- 16-frame cycle, period 3, $100 bytes from ArtUnc_AniLBZ__0 -> VRAM tile $160
- **Gated:** Disabled when Anim_Counters+$F is non-zero (set during Death Egg sequence)

**Channel 1 (Underwater BG position-dependent):**
- Tracks `(Events_bg+$12 - Camera_X_BG_copy) & $F`
- Each position: 64 bytes from ArtUnc_AniLBZ2_2 -> VRAM tile $2E3

**Channel 2 (Waterline tile swapping, sub_27F66):**
- Driven by `Events_bg+$10` (waterline equilibrium from LBZ2_Deform)
- When equilibrium is negative (above water): uses `LBZ_WaterlineScroll_Data` to look up indices into `ArtUnc_AniLBZ2_WaterlineBelow`, building 64 tiles of art -> VRAM tile $2C3
- When equilibrium is positive (below water): same logic with `ArtUnc_AniLBZ2_WaterlineAbove` -> VRAM tile $2D3
- When equilibrium is exactly 0: triggers fix-up art load
- Fix-up routines load `ArtUnc_AniLBZ2_LowerBG` -> VRAM $2C3 and `ArtUnc_AniLBZ2_UpperBG` -> VRAM $2D3

**Confidence:** MEDIUM -- Channel 0 and 1 are clear, Channel 2 waterline logic mirrors HCZ waterline approach but uses different art sources.

### Enemy Art PLC

**PLCKosM_LBZ** (shared both acts, `sonic3k.asm` line 64392):
- SnaleBlaster: ArtKosM_SnaleBlaster -> ArtTile_SnaleBlaster
- Orbinaut: ArtKosM_Orbinaut -> ArtTile_Orbinaut
- Ribot: ArtKosM_Ribot -> ArtTile_Ribot
- Corkey: ArtKosM_Corkey -> ArtTile_Corkey

## Palette Cycling

### Channel 0: Act 1 Conveyor Light Cycle (AnPal_LBZ1)

- **Disassembly location:** `sonic3k.asm` line 3440
- **Counter:** `Palette_cycle_counter1` (delay), `Palette_cycle_counter0` (index)
- **Delay:** 3 frames between updates
- **Step:** 6 bytes per tick
- **Limit:** $12 (3 frames in cycle, wraps at index $12)
- **Target:** `Normal_palette_line_3+$10` (palette line 3, colors 8-10: 4 bytes + 2 bytes = 3 colors)
- **Palette data (AnPal_PalLBZ1):**

| Frame | Color 8 | Color 9 | Color 10 |
|-------|---------|---------|----------|
| 0 | $08E0 | $00C0 | $0080 |
| 1 | $00C0 | $0080 | $08E0 |
| 2 | $0080 | $08E0 | $00C0 |

This creates a 3-color rotation cycle (green tones: $8E0, $C0, $80) for machinery/conveyor lights.

### Channel 0: Act 2 Platform Light Cycle (AnPal_LBZ2)

- **Disassembly location:** `sonic3k.asm` line 3445
- **Same logic as Act 1** (shares code path at loc_2516)
- **Counter/delay/step/limit:** Identical to Act 1
- **Target:** Same (Normal_palette_line_3+$10, 3 colors)
- **Palette data (AnPal_PalLBZ2):**

| Frame | Color 8 | Color 9 | Color 10 |
|-------|---------|---------|----------|
| 0 | $0EEA | $0EA4 | $0C62 |
| 1 | $0EA4 | $0C62 | $0EEA |
| 2 | $0C62 | $0EEA | $0EA4 |

This creates a 3-color rotation cycle (warm tones: $EEA, $EA4, $C62) for Act 2 platform/machinery lights.

## Water System

### Act 1 (DynamicWaterHeight_LBZ1)

**Disassembly location:** `sonic3k.asm` line 8746

Dynamic water height based on Camera X position (table-driven):

| X Threshold | Water Height | Flags |
|-------------|-------------|-------|
| < $E00 | $B00 | Bit 15 set (immediate set) |
| < $1980 | $A00 | Bit 15 set |
| < $2340 | $A00 | Bit 15 set |
| < $2C00 | $AC8 | Bit 15 set |
| >= $2C00 | $FF0 | End marker ($FFFF) |

The bit 15 flag means the value is written to both `Mean_water_level` and `Target_water_level` (immediate water level change rather than gradual).

**Water palette:** Uses `WaterTransition_HCZLBZ1` (shared with HCZ1). Palette ID $22 for both FG and water surface.

### Act 2 (DynamicWaterHeight_LBZ2)

**Disassembly location:** `sonic3k.asm` line 8758

**Knuckles-only water system!** For Sonic/Tails, this is a bare `rts` -- no dynamic water height.

For Knuckles (Player_mode == 3):
- When `_unkF7C2` is clear: table-driven water height:
  - X < $D80: height $FF0 (bit 15 set)
  - X >= $D80: height $B20 (bit 15 set)
- When `_unkF7C2` is set: if Camera Y >= Mean_water_level, set Mean_water_level = $660

**Water palette:** Uses `WaterTransition_LBZ2` (LBZ2-specific). Palette ID $24/$25 for FG/water.

**Water palette transition data (WaterTransition_LBZ2):** 20 color indices for the underwater palette fade, starting with value 2.

### Water During Act 1->2 Transition

During the seamless transition (LBZ1BGE_DoTransition):
- `CheckLevelForWater` is called
- VDP register $8014 is set (enable H-interrupt for water)
- Palette $17 is loaded (LBZ2 water palette)

### Water During Death Egg Sequence

During LBZ2BGE_DeathEgg:
- `Water_flag` is cleared when the Death Egg platform detaches (stage 16 setup)
- `Target_water_level` is updated by the Death Egg movement routine, capped at $F80

## Cutscene Objects

### Knuckles LBZ1 Cutscene (CutsceneKnux_LBZ1)

**Disassembly location:** `s3.asm` line 80476

8 routine stages. Triggers when Player 1 X reaches Knuckles' X position. Loads cutscene Knuckles palette, creates child objects. Sets Camera_min_Y_pos = $A0 during cutscene. Uses standard CutsceneKnux DPLC system.

### Knuckles LBZ2 Cutscene (CutsceneKnux_LBZ2)

**Disassembly location:** `s3.asm` line 80680

7 routine stages. Loads cutscene Knuckles palette, creates child objects via ChildObjDat_4574E. Responds to Screen_shake_flag. Uses animation sequence `byte_4579E` and `byte_45766`.

## Level Art Resources

### Act 1
- **Palette ID:** $22 (FG + water), $16 (BG)
- **Primary tiles:** LBZ_8x8_Primary_KosM (shared)
- **Secondary tiles:** LBZ1_8x8_Secondary_KosM
- **Primary blocks:** LBZ_16x16_Primary_Kos (shared)
- **Secondary blocks:** LBZ1_16x16_Secondary_Kos
- **Chunks:** LBZ1_128x128_Kos

### Act 2
- **Palette IDs:** $24 (FG), $25 (water), $17 (BG)
- **Primary tiles:** LBZ_8x8_Primary_KosM (shared)
- **Secondary tiles:** LBZ2_8x8_Secondary_KosM
- **Primary blocks:** LBZ_16x16_Primary_Kos (shared)
- **Secondary blocks:** LBZ2_16x16_Secondary_Kos
- **Chunks:** LBZ2_128x128_Kos

### Death Egg Resources (loaded at runtime by LBZ2_Resize)
- **Tiles:** LBZ2_8x8_DeathEgg_KosM -> VRAM $000, ArtKosM_LBZ2DeathEgg2_8x8 -> VRAM ArtTile_Explosion
- **Blocks:** LBZ2_16x16_DeathEgg_Kos -> Block_table
- **Chunks:** LBZ2_128x128_DeathEgg_Kos -> RAM_start

### Animated Tile Art Sources
- `ArtUnc_AniLBZ__0` -- Shared rotating machinery (16 frames, $100 bytes each)
- `ArtUnc_AniLBZ1_0` -- Act 1 conveyor (4 frames)
- `ArtUnc_AniLBZ1_1` -- Act 1 BG position-dependent art
- `ArtUnc_AniLBZ1_2` -- Act 1 BG supplement
- `ArtUnc_AniLBZ2_0` -- Act 2 channel 0 (4 frames)
- `ArtUnc_AniLBZ2_1` -- Act 2 channel 1 (6 frames)
- `ArtUnc_AniLBZ2_2` -- Act 2 underwater BG (16 positions, 64 bytes each)
- `ArtUnc_AniLBZ2_WaterlineBelow` -- Waterline art (below water view)
- `ArtUnc_AniLBZ2_WaterlineAbove` -- Waterline art (above water view)
- `ArtUnc_AniLBZ2_LowerBG` -- Fix-up art for lower BG
- `ArtUnc_AniLBZ2_UpperBG` -- Fix-up art for upper BG

### Binary Data Files
- `LBZ Waterline Scroll Data.bin` -- 4160 bytes (65 x 64), per-scanline index into BG bands for waterline transition effect

## Implementation Priority

### Phase 1: Core Playability
1. **Palette cycling** (AnPal_LBZ1/LBZ2) -- simple 3-color rotation, shared code path
2. **Basic parallax** (LBZ1_Deform) -- relatively simple BG parallax
3. **Dynamic resize** (LBZ2_Resize) -- simple camera threshold trigger for Death Egg art loading
4. **Water system** (DynamicWaterHeight_LBZ1) -- table-driven water heights

### Phase 2: Screen Events
5. **LBZ1_ScreenEvent** -- layout mod system (4 regions), screen shake integration
6. **LBZ2_ScreenEvent** -- layout mod + transition setup
7. **LBZ1_BackgroundEvent** -- BG scrolling + seamless Act 1->2 transition
8. **LBZ1_EventVScroll** -- building collapse sequence with cell-based VScroll

### Phase 3: Act 2 Advanced
9. **LBZ2_Deform** -- complex waterline/cloud/underwater BG parallax (most difficult)
10. **LBZ2_BackgroundEvent** -- 6-state machine managing Death Egg sequence
11. **AnimateTiles_LBZ1/LBZ2** -- custom tile animation with waterline art swapping
12. **AniPLC scripts** -- standard zoneanimdecl-based animations

### Phase 4: Death Egg Sequence
13. **LBZ2_DeathEggDeform** -- variant parallax during Death Egg rise
14. **LBZ2_DeathEggMoveScreen** -- camera/object movement during sequence
15. **Special VInt routines** -- VDP Window plane manipulation (may need engine-level support)

### Phase 5: Boss Sequences
16. **Miniboss** (Obj_LBZMiniboss + MinibossBox)
17. **End Boss** (Obj_LBZEndBoss)
18. **Final Boss 1/2** (Obj_LBZFinalBoss1 + LBZFinalBoss2)
19. **Knuckles Final Boss** (Obj_LBZFinalBossKnux)

## Key Implementation Notes

1. **LBZ1_Deform lives in S3 ROM code** -- called via `jsr (LBZ1_Deform).l` from sonic3k.asm. The routine is at `s3.asm` line 75194 in the Sonic 3 portion of the lock-on ROM. This is a common S3K pattern for S3-era zones.

2. **Act 1->2 seamless transition** offsets all positions by -$3A00 X (not -$3600 like HCZ). The transition also changes water behavior (LBZ2 has water for Knuckles only by default, vs LBZ1 which has water for all characters).

3. **The building collapse (LBZ1_EventVScroll)** requires cell-based VScroll support. It uses Special_V_int_routine $04/$08/$0C to switch VDP register $8B between cell-based ($8B07) and full ($8B03) VScroll modes. The collapse effect uses 10 independently-scrolling VScroll columns.

4. **The Death Egg sequence (LBZ2_BackgroundEvent stages 12-20)** is the most complex event sequence in the zone, using VDP Window plane manipulation to keep the platform stationary while the world scrolls. This requires custom VInt routines that copy nametable data between Scroll A and Window planes.

5. **LBZ2_Deform shares architecture with HCZ waterline deformation** -- both use `LBZ_WaterlineScroll_Data` (4160-byte binary lookup table) for per-scanline blending between above/below-water BG bands. The LBZ version adds cloud parallax and underwater band subdivision on top.

6. **Multiple layout modification systems** -- Act 1 has the most complex layout mod system of any S3K zone (4 rectangular regions with enter/exit tracking via Events_bg+$00). Act 2 has a simpler single layout mod.

7. **Knuckles differences are extensive** -- different BG (no Death Egg), different boss arena, different water behavior (Act 2 water only for Knuckles), different camera limits, different cutscenes. The `Player_mode == 3` checks appear throughout both screen events and background events.
