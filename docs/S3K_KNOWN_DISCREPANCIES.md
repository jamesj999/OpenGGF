# Known Discrepancies from Original S3K ROM

This document tracks intentional deviations from the original Sonic 3 & Knuckles ROM implementation. These are cases where we've chosen a different approach for cleaner architecture, better maintainability, or other engineering reasons, while preserving identical runtime behavior.

## Table of Contents

1. [AIZ Intro Object Spawn Source](#aiz-intro-object-spawn-source)
2. [Obj_Wait Timer Pattern](#obj_wait-timer-pattern)
3. [Immediate Art Loading](#immediate-art-loading)
4. [Knuckles DPLC Pre-Loading](#knuckles-dplc-pre-loading)
5. [Save System](#save-system)

---

## AIZ Intro Object Spawn Source

**Location:** `Sonic3kAIZEvents.java`
**ROM Reference:** `sonic3k.asm` line 8111+ (`SpawnLevelMainSprites`)

### Original Implementation

The ROM creates `Obj_AIZPlaneIntro` inside `SpawnLevelMainSprites`, which runs during the main level initialization sprite pass:

```asm
    cmpi.w  #0,(Current_zone_and_act).w     ; AIZ Act 1?
    bne.s   loc_6834
    cmpi.w  #2,(Player_mode).w              ; Not 2-player?
    bhs.s   locret_6832
    move.l  #Obj_AIZPlaneIntro,(Dynamic_object_RAM+(object_size*2)).w
    clr.b   (Level_started_flag).w
```

### Our Implementation

We spawn the intro object from `Sonic3kAIZEvents.init()`, the zone-specific level event handler:

```java
@Override
public void init(int act) {
    if (act == 0 && !bootstrap.isAiz1GameplayAfterIntro()) {
        spawnObject(new AizPlaneIntroInstance(...));
    }
}
```

### Rationale

1. **Consistent with engine architecture** - All dynamic object spawning for cutscenes goes through level event handlers (e.g., `Sonic2CNZEvents` spawns the CNZ boss). No separate `SpawnLevelMainSprites` equivalent exists.
2. **Object exists from frame 1 either way** - Both paths create the object before the first `update()` call.
3. **Cleaner init flow** - Zone-specific behavior belongs in zone event handlers, not in a monolithic sprite spawning routine.

### Verification

The intro object is active on the first frame of level execution, identical to the ROM's timing.

---

## Obj_Wait Timer Pattern

**Location:** `AizPlaneIntroInstance.java`, `CutsceneKnucklesAiz1Instance.java`
**ROM Reference:** `sonic3k.asm` `Obj_Wait` subroutine, SST offsets `$2E`/`$34`

### Original Implementation

The ROM uses a convention where SST offset `$2E` is a countdown timer and `$34` is a 32-bit pointer to a callback routine. `Obj_Wait` decrements `$2E` each frame and calls the routine at `$34` when it reaches zero:

```asm
Obj_Wait:
    subq.w  #1,$2E(a0)
    bpl.s   locret
    movea.l $34(a0),a1
    jmp     (a1)
```

### Our Implementation

We use explicit named fields (`waitTimer`, `waitCallback`) or inline timer logic within each routine method, rather than raw SST offset conventions:

```java
if (--waitTimer < 0) {
    onWaitExpired();  // or direct routine advance
}
```

### Rationale

1. **Named fields are self-documenting** - `waitTimer` is clearer than `$2E(a0)` when reading Java code.
2. **No function pointer indirection needed** - Java's method dispatch and routine switch make callbacks unnecessary; the expired handler is just the next case in the state machine.
3. **Same timing behavior** - The countdown interval and frame-exact trigger points are identical.

### Verification

Timer-driven routine transitions fire on the exact same frame as the ROM's `Obj_Wait` pattern.

---

## Immediate Art Loading

**Location:** `AizPlaneIntroInstance.java`, `AizIntroPlaneChild.java`, `AizIntroTerrainSwap.java`
**ROM Reference:** `sonic3k.asm` `Queue_Kos_Module` calls at `loc_6777A`, `Kos_decomp_queue_count` gate in `AIZ1_Resize`

### Original Implementation

The ROM queues KosinskiM-compressed art for deferred DMA transfer during V-blank:

```asm
    lea     (ArtKosM_AIZIntroPlane).l,a1
    move.w  #tiles_to_bytes(ArtTile_AIZIntroPlane),d2
    jsr     (Queue_Kos_Module).l
    lea     (ArtKosM_AIZIntroEmeralds).l,a1
    move.w  #tiles_to_bytes(ArtTile_AIZIntroEmeralds),d2
    jsr     (Queue_Kos_Module).l
```

This queues the decompression work to be spread across multiple V-blank intervals, avoiding frame drops from large decompressions. Downstream, `AIZ1_Resize` routine 2 gates the transition to routine 4 (Y boundary unlock, dynamic maxY) on `Kos_decomp_queue_count` reaching 0 — the BG event handler stays in intro deformation mode until the queue drains.

### Our Implementation

We decompress and load art immediately during the object's init phase:

```java
byte[] planeArt = ResourceLoader.decompress(romAddr, CompressionType.KOSINSKI_MODULED);
graphicsManager.writePatterns(ART_TILE_AIZ_INTRO_PLANE, planeArt);
```

Since there is no decompression queue to poll, the `AIZ1_Resize` routine 2→4 gate uses an `introWasPlayed` flag (from `Sonic3kAIZEvents.shouldSpawnIntro()`) instead of a queue count. When the intro was played, a 30-frame countdown simulates the queue drain delay. When the intro was skipped, `mainLevelPhaseActive` is set immediately — matching the ROM where `Kos_decomp_queue_count` is already 0 at level start.

### Rationale

1. **No V-blank constraint** - The engine doesn't have a V-blank DMA budget. Decompression during init has no frame timing impact.
2. **Art available before first draw** - Immediate loading guarantees patterns are ready when the object first renders, eliminating any possibility of a blank-frame glitch.
3. **Simpler code path** - No deferred queue management needed.
4. **Intro check is equivalent to queue count** - When the intro wasn't played, no Kos data was queued, so the count would be 0. Checking `introWasPlayed` produces the same result.

### Verification

All art tiles are present from the first frame the object renders. `TestS3kAiz1SkipHeadless` and `TestS3kAiz1LoopRegression` verify skip-intro correctly unlocks Y boundaries. `TestS3kAiz1SpindashLoopTraversal` verifies Sonic is not killed by premature pit death on the approach to the first loop.

---

## Knuckles DPLC Pre-Loading

**Location:** `CutsceneKnucklesAiz1Instance.java`
**ROM Reference:** `sonic3k.asm` `Perform_DPLC` calls in `CutsceneKnux_AIZ1`

### Original Implementation

The ROM uses Dynamic Pattern Loading Cues (DPLC) to transfer only the patterns needed for the current animation frame into VRAM each frame:

```asm
CutsceneKnux_AIZ1:
    ...
    lea     DPLCPtr_CutsceneKnux(pc),a2
    jsr     (Perform_DPLC).l
    jmp     (Draw_Sprite).l
```

This minimizes VRAM usage by loading only the active frame's tiles, reusing the same VRAM region as the frame changes.

### Our Implementation

We pre-load all Knuckles cutscene frames at init time, assigning each frame's patterns to distinct tile indices:

```java
// Load all DPLC frames at init
for (int frame = 0; frame < frameCount; frame++) {
    loadDplcFrame(frame, baseArtTile + frameOffset);
}
```

### Rationale

1. **No VRAM scarcity** - Modern systems have abundant texture memory; the ~64KB VDP limit doesn't apply.
2. **Eliminates per-frame pattern transfer** - No need to track which frame was last loaded or detect frame changes.
3. **Simpler rendering** - Each mapping frame references stable tile indices, making the draw path straightforward.

### Verification

Every Knuckles animation frame displays the correct patterns at the correct positions, matching the ROM's per-frame DPLC result.

---

## Save System

**Location:** `com.openggf.game.save`, `com.openggf.game.dataselect`, `com.openggf.game.sonic3k.dataselect`
**ROM Reference:** `sonic3k.asm` SRAM routines (`ReadSaveGame`, `WriteSaveGame`)

### Original Implementation

The ROM stores save data directly in battery-backed SRAM at fixed offsets. Each of the 8 slots occupies a contiguous region with zone/act, character, emerald, and clear flags packed into specific byte positions. Data integrity relies on a simple checksum.

### Our Implementation

OpenGGF uses JSON save slots plus a stored SHA-256 hash instead of the original SRAM layout. Key differences:

- **Per-slot JSON files** stored at `saves/{game}/slotN.json` wrapped in a `SaveEnvelope` with version, game code, slot number, payload, and hash.
- **SHA-256 integrity** rather than SRAM checksum. Hash mismatches log warnings during Data Select scan but do not block otherwise valid saves.
- **Corrupt quarantine** — malformed or wrong-game save files are renamed to `.corrupt` and treated as empty slots.
- **No-op unsaved sessions** — save requests route through `SaveSessionContext`; when no slot is active (e.g., "No Save" mode), they silently no-op.
- **Snapshot providers** — game-specific payload capture is handled by `SaveSnapshotProvider` implementations (`S3kSaveSnapshotProvider`, `S2SaveSnapshotProvider`, `S1SaveSnapshotProvider`) rather than direct SRAM-style writes.
- **Character teams** — `SelectedTeam` records hold the main character and sidekick list, supporting custom team combos via `DATA_SELECT_EXTRA_PLAYER_COMBOS` config.

### Rationale

1. **Platform independence** — JSON files work on any OS without SRAM hardware emulation.
2. **Human-readable** — save files can be inspected and manually edited for debugging.
3. **Extensible** — the envelope format supports versioning and per-game payload schemas.
4. **Multi-game sharing** — the same `SaveManager` serves S1, S2, and S3K through game-specific profiles and providers.

### Verification

`TestSaveManager` verifies round-trip write/read, hash validation, corrupt quarantine, wrong-game detection, and no-op unsaved sessions. `TestS3kSaveSnapshotProvider` verifies payload capture includes team, zone, act, lives, and emerald count.

