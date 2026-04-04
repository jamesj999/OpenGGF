---
name: s3k-zone-events
description: Use when implementing S3K zone event handlers — camera locks, boss arenas, cutscenes, act transitions, palette mutations. Ports Dynamic_Resize routines from the disassembly.
---

# Implement Sonic 3&K Zone Events

Implement a zone-specific event handler for Sonic 3 & Knuckles with ROM accuracy. This skill covers porting Dynamic_Resize routines from the disassembly to Java, including camera boundary manipulation, boss arena setup, cutscene coordination, act transitions, and palette mutations.

## Inputs

$ARGUMENTS: Zone abbreviation (e.g., "HCZ", "MGZ", "CNZ1", "LBZ Act 2") and optionally a path to a zone analysis spec file.

## Related Skills

- **s3k-disasm-guide** (`.claude/skills/s3k-disasm-guide/skill.md`) for disassembly navigation, label conventions, RomOffsetFinder commands, and zone abbreviations.
- **s3k-zone-analysis** (`.agent/skills/s3k-zone-analysis/skill.md`) for generating the analysis spec that feeds into this skill's Phase 1.
- **s3k-plc-system** (`.claude/skills/s3k-plc-system/skill.md`) for Pattern Load Cue operations triggered during boss spawns and act transitions.

## Architecture

### Class Hierarchy

```
AbstractLevelEventManager (game/)
    |  - Shared state machine: eventRoutineFg, eventRoutineBg, frameCounter, timerFrames
    |  - Camera boundary helpers: lockCameraX(), lockCameraY(), setBottomBoundaryTarget()
    |  - Player control: lockPlayerInput(), setForcedInput(), forcePlayerRight()
    |  - Audio: playMusic(), fadeMusic(), playSfx()
    |  - Object spawning: spawnObject()
    |  - Zone transition: transitionToZone()
    |
    v
Sonic3kLevelEventManager (game/sonic3k/)
    |  - S3K-specific: routineStride=4, dual FG/BG routines
    |  - Bootstrap resolution via Sonic3kBootstrapResolver
    |  - Zone handler creation in onInitLevel()
    |  - Zone handler dispatch in onUpdate()
    |
    v
Sonic3kZoneEvents (game/sonic3k/events/)
    |  - Per-zone base class: camera, eventRoutine, bossSpawnDelay
    |  - init(act), update(act, frameCounter)
    |  - loadPalette(paletteLine, romAddr)
    |  - loadPaletteFromPalPointers(palPointersIndex)
    |  - applyPlc(plcId)
    |  - spawnObject(object)
    |
    v
Sonic3k{Zone}Events (game/sonic3k/events/)
    - Zone-specific implementation (e.g., Sonic3kAIZEvents)
```

### Base Class API (Sonic3kZoneEvents)

These are the methods available to all zone event handlers:

| Method | ROM Equivalent | Description |
|--------|----------------|-------------|
| `camera.getX()` / `camera.getY()` | `Camera_X_pos` / `Camera_Y_pos` | Current camera position |
| `camera.setMinX(short)` | `Camera_Min_X_pos` | Set left camera boundary (immediate) |
| `camera.setMaxX(short)` | `Camera_Max_X_pos` | Set right camera boundary (immediate) |
| `camera.setMinY(short)` | `Camera_Min_Y_pos` | Set top camera boundary (immediate) |
| `camera.setMaxY(short)` | `Camera_Max_Y_pos` | Set bottom camera boundary (immediate) |
| `camera.setMaxYTarget(short)` | `Camera_Max_Y_pos_now` | Set bottom boundary with easing (+2px/frame) |
| `camera.setMinXTarget(short)` | N/A | Set left boundary with easing |
| `camera.setFrozen(true/false)` | Scroll lock | Stop/resume camera following player |
| `eventRoutine` field | `Dynamic_Resize_routine` | State machine counter (advance by assignment) |
| `bossSpawnDelay` field | Boss countdown | Frames until boss spawn |
| `loadPalette(line, romAddr)` | `PalLoad_Now` | Load 32-byte palette from ROM address |
| `loadPaletteFromPalPointers(idx)` | `LoadPalette_Immediate` | Load via PalPointers table |
| `applyPlc(plcId)` | `Load_PLC` / `Load_PLC_2` | Parse and apply a Pattern Load Cue |
| `spawnObject(object)` | `SingleObjLoad` | Spawn a dynamic object into the level |

### AbstractLevelEventManager API

These are inherited through Sonic3kLevelEventManager and available when the zone event handler needs to call back into the manager:

| Method | ROM Equivalent | Description |
|--------|----------------|-------------|
| `lockCameraX(min, max)` | Direct Camera_Min/Max_X_pos writes | Lock both X boundaries |
| `lockCameraY(min, max)` | Direct Camera_Min/Max_Y_pos writes | Lock both Y boundaries |
| `setBottomBoundaryTarget(y)` | `Camera_Max_Y_pos_now` | Set bottom boundary target with easing |
| `preventBacktracking()` | `Camera_Min_X_pos = Camera_X_pos` | Lock left boundary to current camera X |
| `lockPlayerInput()` | `Ctrl_1_locked = 1` | Suppress all player input |
| `unlockPlayerInput()` | `Ctrl_1_locked = 0` | Resume player input |
| `setForcedInput(mask)` | Write to `Ctrl_1_Logical` | Inject forced button input |
| `forcePlayerRight()` | Force right walk | End-of-act walkoff |
| `playMusic(id)` | `PlayMusic` | Play music track |
| `fadeMusic()` | `FadeOutMusic` | Fade current music |
| `playSfx(id)` | `PlaySfx` | Play sound effect |
| `startTimer(frames)` | Timer countdown | Start countdown, check `isTimerExpired()` |
| `transitionToZone(zone, act)` | Level restart | Trigger zone/act transition |

**Note:** The zone event handler accesses these through the Sonic3kLevelEventManager instance, not directly. For camera boundaries, use the `camera` field directly (available from `Sonic3kZoneEvents`). For player control and audio, use the manager's methods or access singletons as needed.

## Implementation Process

### Phase 1: Read the Zone Analysis Spec

If a zone analysis spec exists (from the `s3k-zone-analysis` skill), read it first. Look for:

- **Events section:** Lists all Dynamic_Resize routines, their camera thresholds, and state transitions
- **Boss arena coordinates:** Camera lock boundaries for boss fights
- **Palette mutations:** Camera-triggered one-shot palette writes (NOT the same as palette cycling)
- **Character branching:** Whether Knuckles takes a different path
- **Act transition mechanism:** Seamless transition, fade, or zone restart

If no spec exists, proceed directly to Phase 2.

### Phase 2: Read the Disassembly

**Step 1: Find the Dynamic_Resize routine.**

Search for the zone's resize routine in the disassembly. S3K zones split events across two files:

```bash
# Search in sonic3k.asm (S&K zones: MHZ-DDZ)
grep -n "{ZONE}1_Resize\|{ZONE}2_Resize\|{ZONE}_Resize" docs/skdisasm/sonic3k.asm

# Search in s3.asm (S3 zones: AIZ-LBZ)
grep -n "{ZONE}1_Resize\|{ZONE}2_Resize\|{ZONE}_Resize" docs/skdisasm/s3.asm
```

S3K zones typically have separate per-act routines (e.g., `HCZ1_Resize` and `HCZ2_Resize`).

**Step 2: Read the full routine and identify the state machine.**

Dynamic_Resize routines use a `moveq`/`jmp` dispatch pattern:

```asm
{ZONE}1_Resize:
    moveq   #0,d0
    move.b  (Dynamic_Resize_routine).w,d0
    move.w  {ZONE}1_Resize_Index(pc,d0.w),d0
    jmp     {ZONE}1_Resize_Index(pc,d0.w)
; ---------------------------------------------------------------------------
{ZONE}1_Resize_Index:
    dc.w loc_xxxxx-{ZONE}1_Resize_Index  ; Routine 0
    dc.w loc_xxxxx-{ZONE}1_Resize_Index  ; Routine 2
    dc.w loc_xxxxx-{ZONE}1_Resize_Index  ; Routine 4
    ...
```

Each routine index offset = routine_value / 2 (since entries are words). The stride is 4 in S3K (the ROM uses `addq.b #4,(Dynamic_Resize_routine).w` to advance).

**Step 3: Find associated data.**

```bash
# Resize tables (camera X -> max Y mappings)
grep -n "{ZONE}.*_Resize_Table\|word_\|{ZONE}.*MaxY" docs/skdisasm/s3.asm

# Screen event routines (FG events, separate from BG resize)
grep -n "{ZONE}.*_ScreenEvent\|{ZONE}.*SE_" docs/skdisasm/s3.asm

# Background event routines
grep -n "{ZONE}.*_BackgroundEvent\|{ZONE}.*BGE_" docs/skdisasm/s3.asm
```

**Step 4: Check for character branching.**

```bash
grep -n "Player_mode\|Knuckles" docs/skdisasm/s3.asm | grep -i "{ZONE}"
```

The ROM uses `cmpi.b #3,(Player_mode).w` / `beq` to branch for Knuckles (Player_mode 3).

### Phase 3: Create the Zone Events Class

Create `src/main/java/com/openggf/game/sonic3k/events/Sonic3k{Zone}Events.java`.

**Template:**

```java
package com.openggf.game.sonic3k.events;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kLoadBootstrap;

import java.util.logging.Logger;

/**
 * {Zone Name} dynamic level events.
 * ROM: {ZONE}1_Resize / {ZONE}2_Resize (s3.asm or sonic3k.asm)
 *
 * <p>Act 1 state machine (Dynamic_Resize_routine):
 * <ul>
 *   <li>Routine 0: {describe}</li>
 *   <li>Routine 4: {describe}</li>
 *   ...
 * </ul>
 */
public class Sonic3k{Zone}Events extends Sonic3kZoneEvents {
    private static final Logger LOG = Logger.getLogger(Sonic3k{Zone}Events.class.getName());

    // --- ROM constants ---
    // Camera thresholds, palette indices, boundary values from disassembly.
    // Always document the ROM label or address in a comment.

    /** Camera X threshold for {description} (routine 0). */
    private static final int SOME_THRESHOLD_X = 0x1234;

    // --- Mutable state ---
    private final Sonic3kLoadBootstrap bootstrap;

    public Sonic3k{Zone}Events(Camera camera, Sonic3kLoadBootstrap bootstrap) {
        super(camera);
        this.bootstrap = bootstrap;
    }

    @Override
    public void init(int act) {
        super.init(act);
        // Reset all mutable state for a fresh level load.
    }

    @Override
    public void update(int act, int frameCounter) {
        switch (act) {
            case 0 -> updateAct1(frameCounter);
            case 1 -> updateAct2(frameCounter);
        }
    }

    // --- Act 1 ---

    private void updateAct1(int frameCounter) {
        int cameraX = camera.getX();

        switch (eventRoutine) {
            case 0 -> {
                // Routine 0 logic: check camera threshold, apply boundaries
                if (cameraX >= SOME_THRESHOLD_X) {
                    // Apply effect
                    eventRoutine += 4; // S3K stride is 4
                }
            }
            case 4 -> {
                // Routine 4 logic
            }
            // Add more routines as needed
        }
    }

    // --- Act 2 ---

    private void updateAct2(int frameCounter) {
        // Similar structure for act 2
    }
}
```

**Key patterns from Sonic3kAIZEvents:**

1. **Constructor** takes `Camera` and `Sonic3kLoadBootstrap` parameters.
2. **`init(int act)`** calls `super.init(act)` first, then resets all zone-specific mutable state.
3. **`update(int act, int frameCounter)`** dispatches to per-act methods.
4. **Per-act methods** read `camera.getX()` / `camera.getY()` and branch on `eventRoutine`.
5. **State tracking** uses boolean guards for one-shot operations (e.g., `paletteSwapped`, `boundariesUnlocked`).
6. **Resize tables** are `int[][]` constants with `{maxY, triggerX}` pairs, scanned by a helper method.

### Phase 4: Register in Sonic3kLevelEventManager

Edit `src/main/java/com/openggf/game/sonic3k/Sonic3kLevelEventManager.java`:

**Step 1: Add a field for the zone events handler.**

```java
private Sonic3k{Zone}Events {zone}Events;
```

**Step 2: Create the handler in `onInitLevel`.**

Add a case to the zone dispatch in `onInitLevel(int zone, int act)`:

```java
if (zone == Sonic3kZoneIds.ZONE_{ZONE}) {
    {zone}Events = new Sonic3k{Zone}Events(camera, bootstrap);
    {zone}Events.init(act);
} else {
    {zone}Events = null;
}
```

**Step 3: Dispatch in `onUpdate`.**

Add the dispatch in `onUpdate()`:

```java
if ({zone}Events != null && currentZone == Sonic3kZoneIds.ZONE_{ZONE}) {
    {zone}Events.update(currentAct, frameCounter);
}
```

**Step 4: (Optional) Add accessor.**

If other code needs to reference the zone events (boss objects, transition objects):

```java
public Sonic3k{Zone}Events get{Zone}Events() {
    return {zone}Events;
}
```

**Note:** `Sonic3kLevelEventManager` does NOT have a `resetState()` override. The base class `AbstractLevelEventManager.resetState()` calls `initLevel(-1, -1)` which flows through `onInitLevel`, setting zone handler fields to `null`. No additional cleanup is needed.

### Phase 5: Build and Verify

```bash
mvn package
```

If the zone has existing tests, run them:

```bash
mvn test -Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading
```

## Assembly-to-Java Translation Reference

| 68000 Assembly | Java Equivalent | Notes |
|----------------|-----------------|-------|
| `(Camera_X_pos).w` | `camera.getX()` | Camera X position |
| `(Camera_Y_pos).w` | `camera.getY()` | Camera Y position |
| `(Camera_Min_X_pos).w` | `camera.setMinX((short) val)` | Left boundary (immediate) |
| `(Camera_Max_X_pos).w` | `camera.setMaxX((short) val)` | Right boundary (immediate) |
| `(Camera_Min_Y_pos).w` | `camera.setMinY((short) val)` | Top boundary (immediate) |
| `(Camera_Max_Y_pos).w` | `camera.setMaxY((short) val)` | Bottom boundary (immediate) |
| `(Camera_Max_Y_pos_now).w` | `camera.setMaxYTarget((short) val)` | Bottom boundary (eased +2px/frame) |
| `(Dynamic_Resize_routine).w` | `eventRoutine` field | State machine counter |
| `addq.b #4,(Dynamic_Resize_routine).w` | `eventRoutine += 4` | Advance state (S3K stride = 4) |
| `(Boss_flag).w` | `bossFlag` field or `bossActive` in manager | Gates FG events during boss |
| `(Player_mode).w` | `PlayerCharacter` enum via manager | Character branching (see below) |
| `jsr (SingleObjLoad).l` | `spawnObject(objectInstance)` | Spawn dynamic object |
| `jsr (PalLoad_Now).l` | `loadPalette(paletteLine, romAddr)` | Load palette from ROM |
| `jsr (LoadPalette_Immediate).l` | `loadPaletteFromPalPointers(index)` | Load via PalPointers table |
| `jsr (Load_PLC).l` | `applyPlc(plcId)` | Apply Pattern Load Cue |
| `jsr (PlayMusic).l` | `playMusic(musicId)` | Play music (use `Sonic3kMusic` enum) |
| `jsr (PlaySfx).l` | `playSfx(sfxId)` | Play sound effect |
| `(Events_fg_0).w` etc. | Local fields in zone events class | Per-zone scratch data |
| `(Events_bg+$00).w` etc. | Local fields in zone events class | BG event scratch data |
| `move.w #$xxxx,(Camera_Min_X_pos).w` | `camera.setMinX((short) 0xXXXX)` | Cast to short for signed 16-bit |
| `tst.b (Boss_flag).w / bne` | `if (bossFlag) return;` | Skip FG events during boss |

## Character Branching Pattern

S3K zones frequently branch on `Player_mode` to give Knuckles different paths:

```asm
; ROM pattern:
    cmpi.b  #3,(Player_mode).w
    beq.w   .knuckles_path
    ; ... Sonic/Tails logic ...
.knuckles_path:
    ; ... Knuckles logic ...
```

**Java equivalent:**

```java
// In Sonic3kLevelEventManager:
public PlayerCharacter getPlayerCharacter() {
    // Returns SONIC_AND_TAILS, SONIC_ALONE, TAILS_ALONE, or KNUCKLES
}

// In zone events class:
private void updateAct1(int frameCounter) {
    Sonic3kLevelEventManager manager = Sonic3kLevelEventManager.getInstance();
    PlayerCharacter character = manager.getPlayerCharacter();

    if (character == PlayerCharacter.KNUCKLES) {
        updateAct1Knuckles(frameCounter);
    } else {
        updateAct1SonicTails(frameCounter);
    }
}
```

**PlayerCharacter enum values and ROM mapping:**

| Enum Value | `Player_mode` | Description |
|------------|---------------|-------------|
| `SONIC_AND_TAILS` | 0 | Sonic + CPU Tails |
| `SONIC_ALONE` | 1 | Sonic alone |
| `TAILS_ALONE` | 2 | Tails alone |
| `KNUCKLES` | 3 | Knuckles (different level paths) |

Most ROM branches only distinguish Knuckles (`== 3`) from everyone else. A few zones have Tails-specific paths (`== 2`).

## Palette Mutations vs. Palette Cycling

**CRITICAL DISTINCTION:** These are two separate systems. Getting them confused is a common mistake.

### Palette Mutations (belong in zone events)

- **Trigger:** Camera position threshold (one-shot or conditional)
- **Mechanism:** Direct palette writes in `_Resize` or `_ScreenEvent` routines
- **Example:** AIZ1 writes `palette[2][15]` from bright red to dark when cameraX >= `$2B00`
- **Implementation:** Call `loadPalette()` or `loadPaletteFromPalPointers()` from the zone events class
- **Guard:** Use a boolean flag to ensure one-shot writes only happen once

```java
// CORRECT: Palette mutation in zone events
private boolean paletteSwapped;

private void updateAct1(int frameCounter) {
    if (!paletteSwapped && camera.getX() >= PALETTE_SWAP_X) {
        loadPaletteFromPalPointers(PAL_INDEX);
        paletteSwapped = true;
    }
}
```

### Palette Cycling (belongs in Sonic3kPaletteCycler)

- **Trigger:** Timer-based, every N frames (runs continuously)
- **Mechanism:** `AnPal_*` routines cycle colors through ROM data tables
- **Example:** AIZ waterfall shimmer cycles 4 colors every 8 frames
- **Implementation:** `Sonic3kPaletteCycler` (called via `Sonic3kLevelAnimationManager`)
- **Never implement cycling in zone events!**

See `AGENTS_S3K.md` section "Per-Frame Palette Animation" for the cycling system details.

### How to Tell Them Apart in the Disassembly

- **Mutations:** Found inside `_Resize` / `_ScreenEvent` / `_BackgroundEvent` routines, guarded by camera position checks, use `PalLoad_Now` or direct `Normal_palette` writes
- **Cycling:** Found in `AnPal_*` routines (dispatched from `AnimatePalettes`), use counter/step/limit pattern, reference `Palette_cycle_counter` variables

## Boss Spawn Coordination Pattern

Boss fights follow a consistent multi-phase pattern in S3K:

### Phase 1: Camera Lock

The resize routine detects the player crossing a camera threshold and locks the arena:

```java
case 0 -> {
    if (camera.getX() >= BOSS_ARENA_LEFT_X) {
        // Lock camera to boss arena
        camera.setMinX((short) BOSS_ARENA_LEFT_X);
        camera.setMaxX((short) (BOSS_ARENA_LEFT_X + SCREEN_WIDTH));
        camera.setMinY((short) BOSS_ARENA_TOP_Y);
        camera.setMaxY((short) BOSS_ARENA_BOTTOM_Y);
        eventRoutine += 4;
    }
}
```

### Phase 2: PLC + Music

Load boss art via PLC, then switch to boss music:

```java
case 4 -> {
    applyPlc(BOSS_PLC_ID);
    // Music change often happens after a short delay
    bossSpawnDelay = BOSS_SPAWN_DELAY_FRAMES;
    eventRoutine += 4;
}
case 8 -> {
    if (bossSpawnDelay > 0) {
        bossSpawnDelay--;
        return;
    }
    playMusic(Sonic3kMusic.MINI_BOSS.id);  // or BOSS music
    eventRoutine += 4;
}
```

### Phase 3: Spawn Boss Object

```java
case 12 -> {
    ObjectSpawn spawn = new ObjectSpawn(bossObjectId, bossX, bossY, 0, 0, false, 0);
    spawnObject(new BossObjectInstance(spawn));
    bossFlag = true;
    eventRoutine += 4;
}
```

### Phase 4: Wait for Boss Defeat

```java
case 16 -> {
    // Boss object clears bossFlag when defeated
    if (!bossFlag) {
        // Boss defeated -- unlock camera, play zone music, etc.
        eventRoutine += 4;
    }
}
```

### Phase 5: Post-Boss Cleanup

```java
case 20 -> {
    // Unlock camera for act transition / walkoff
    camera.setMaxX((short) LEVEL_END_X);
    // Optionally trigger act transition or seamless reload
}
```

## Common Mistakes

### 1. Forgetting to Advance eventRoutine

Every state that should transition must explicitly advance:

```java
// WRONG: state machine stalls
case 0 -> {
    if (camera.getX() >= THRESHOLD) {
        doSomething();
        // Missing: eventRoutine += 4;
    }
}

// CORRECT:
case 0 -> {
    if (camera.getX() >= THRESHOLD) {
        doSomething();
        eventRoutine += 4;
    }
}
```

The S3K stride is **4**, not 2 (unlike S1/S2). Use `eventRoutine += 4` or set directly.

### 2. Coordinate System Confusion

The ROM uses VDP coordinates in some contexts (offset by 128). The engine uses direct screen/world coordinates. When porting camera thresholds from the disassembly:

- `Camera_X_pos`, `Camera_Y_pos` values are **world coordinates** -- use directly
- `Sprite_X_pos`, `Sprite_Y_pos` for VDP sprites have the +128 offset -- subtract 128 when converting
- Player position: use `getCentreX()` / `getCentreY()`, NOT `getX()` / `getY()` (which are top-left render coordinates)

### 3. Not Handling Bootstrap Skip

Zones with intros (AIZ, ICZ, etc.) must handle the skip-intro bootstrap. When `bootstrap.isSkipIntro()` is true, the event routine should start at a post-intro state:

```java
@Override
public void init(int act) {
    super.init(act);
    if (bootstrap.isSkipIntro() && act == 0) {
        // Skip directly to post-intro state
        eventRoutine = POST_INTRO_ROUTINE;
        // Apply palette/boundaries that the intro would have set
    }
}
```

### 4. Implementing Palette Cycling in Zone Events

**Never** put timer-based palette cycling in a zone events class. That belongs in `Sonic3kPaletteCycler`. Zone events only handle one-shot palette mutations triggered by camera position or boss state. See the "Palette Mutations vs. Palette Cycling" section above.

### 5. Not Resetting State in init()

Every mutable field must be reset in `init(int act)`. Failure to reset causes stale state on level reload (death restart, special stage return):

```java
@Override
public void init(int act) {
    super.init(act);  // Resets eventRoutine, bossSpawnDelay
    // Reset ALL zone-specific state:
    paletteSwapped = false;
    bossFlag = false;
    boundariesUnlocked = false;
    // etc.
}
```

### 6. Accessing Camera Before Init

The `camera` field is refreshed in `Sonic3kZoneEvents.init()` via `Camera.getInstance()`. Never cache camera position in the constructor -- it may not be valid yet.

### 7. Using Wrong Music Constants

S3K music IDs are in the `Sonic3kMusic` enum (`game/sonic3k/audio/Sonic3kMusic.java`), not raw hex values. Use `Sonic3kMusic.AIZ1.id`, `Sonic3kMusic.MINI_BOSS.id`, etc.

### 8. Forgetting Boss_flag Gating

The ROM gates FG screen events behind `Boss_flag`. If the zone has a boss, FG events (boundary updates, screen events) should check the boss flag:

```java
private void updateAct2(int frameCounter) {
    // ROM: tst.b (Boss_flag).w / bne.s .skip_fg_events
    if (!bossFlag) {
        updateAct2ScreenEvents();
    }
    updateAct2BgEvents();
}
```
