# Known Discrepancies from Original ROM

This document tracks intentional deviations from the original Sonic 2 ROM implementation. These are cases where we've chosen a different approach for cleaner architecture, better maintainability, or other engineering reasons, while preserving identical runtime behavior.

## Table of Contents

1. [Gloop Sound Toggle](#gloop-sound-toggle)
2. [Spindash Release Transpose Fix](#spindash-release-transpose-fix)
3. [Pattern ID Ranges](#pattern-id-ranges-for-guiresults-screen)
4. [HTZ Cloud Scroll Precision Fix](#htz-cloud-scroll-precision-fix)
5. [MCZ Rotating Platforms Child Cleanup](#mcz-rotating-platforms-child-cleanup)
6. [Multi-Sidekick Daisy Chain](#multi-sidekick-daisy-chain)
7. [Bonus Stage Game Mode](#bonus-stage-game-mode)
8. [HCZ Conveyor Belt Rolling State Clear](#hcz-conveyor-belt-rolling-state-clear)

---

## Gloop Sound Toggle

**Location:** `BlueBallsObjectInstance.java`
**ROM Reference:** `s2.sounddriver.asm` lines 2142-2149

### Original Implementation

The ROM implements the Gloop sound toggle in the Z80 sound driver itself:

```asm
zPlaySound_CheckGloop:
    cp    SndID_Gloop           ; Is this the gloop sound?
    jr    nz,zPlaySound_CheckSpindash
    ld    a,(zGloopFlag)
    cpl                         ; Toggle the flag
    ld    (zGloopFlag),a
    or    a
    ret   z                     ; Return WITHOUT playing if flag is 0
    jp    zPlaySound            ; Only play every other call
```

This hardcodes a specific sound ID check into the driver, causing the Gloop sound to only play every other time it's requested.

### Our Implementation

We implement the toggle in `BlueBallsObjectInstance.playGloopSound()` instead:

```java
private static boolean gloopToggle = false;

private void playGloopSound() {
    if (!isOnScreen()) {
        return;
    }
    // Toggle flag - only play every other call (ROM: zGloopFlag)
    gloopToggle = !gloopToggle;
    if (!gloopToggle) {
        return;
    }
    AudioManager.getInstance().playSfx(SND_ID_GLOOP);
}
```

### Rationale

1. **Gloop is exclusively used by BlueBalls** - A search of the disassembly confirms `SndID_Gloop` (0xDA) is only referenced in `Obj1D` (BlueBalls). No other object uses this sound.

2. **Keeps SMPS driver generic** - Hardcoding sound-specific behavior in the driver would make it less reusable and harder to maintain. The driver should ideally just play what it's told.

3. **Encapsulates behavior** - The toggle is really a BlueBalls-specific feature to prevent sound spam when multiple balls are active. Keeping it in the object makes the relationship explicit.

4. **Identical runtime behavior** - The end result is the same: Gloop plays every other call, preventing audio spam from staggered sibling balls.

### Verification

Both implementations result in the Gloop sound playing at 50% frequency, which prevents overwhelming audio when multiple BlueBalls objects are bouncing with staggered timers.

---

## Spindash Release Transpose Fix

**Location:** `Sonic2SfxData.java`
**ROM Reference:** `docs/s2disasm/sound/sfx/BC - Spin Dash Release.asm`

### Original Implementation

The ROM SFX header for Spindash Release (0xBC) uses an invalid transpose value for FM5:

```asm
    smpsHeaderSFXChannel cFM5, Sound3C_SpindashRelease_FM5, $90, $00
```

This value is called out in the disasm as a bug. Some SMPS drivers interpret `$90` as a large negative transpose, which can underflow the note calculation and skip the initial FM burst.

### Our Implementation

We patch only this invalid FM transpose value when parsing SFX headers:

```java
int transpose = (byte) data[pos + 4];
if ((channelId & 0x80) == 0 && transpose == (byte) 0x90) {
    transpose = 0x10;
}
```

### Rationale

1. **Targets a known bad data value** - The disasm explicitly documents the `$90` transpose as invalid for this SFX.
2. **Preserves other SFX behavior** - We do not mask or normalize all transposes, only this exact FM case.
3. **Improves fidelity** - Restores the missing initial FM burst for 0xBC that is audible in hardware/driver-correct playback.

### Verification

Spindash Release now includes the initial FM5 hit before the delayed PSG noise, matching expected playback.

---

## Pattern ID Ranges

**Location:** `LevelManager.java`, `ObjectRenderManager.java`, `PatternAtlas.java`
**ROM Reference:** VDP VRAM tile management

### Original Implementation

The Mega Drive VDP has limited VRAM (~64KB), so the original game dynamically loads and overwrites pattern data. When displaying the results screen after completing an act, the game overwrites level tile patterns that are no longer needed with results screen graphics (score tallies, continue icons, etc.). Pattern indices directly correspond to VRAM tile addresses (0x0000-0x07FF typical range).

From `s2.asm`, results screen art is loaded into VRAM locations previously used by level tiles:
```asm
; Load results screen patterns, overwriting level data
lea     (ArtNem_TitleCard).l,a0
lea     (vdp_control_port).l,a4
move.w  #tiles_to_bytes(ArtTile_Title_Card),d0
```

### Our Implementation

We use **extended pattern ID ranges** with fixed bases that don't overlap:

| Base | Category | Notes |
|------|----------|-------|
| `0x00000` | Level tiles | Corresponds to VRAM tile indices (0-~2047) |
| `0x01000` | Special Stage | Track, objects, HUD for special stages |
| `0x10000` | Results Screen | End-of-act results screen patterns |
| `0x20000` | Objects | Monitors, springs, badniks, zone-specific objects |
| `0x28000` | HUD | Score, time, rings display (fixed base) |
| `0x30000` | Water surface | Underwater palette transition patterns |
| `0x38000+` | Sidekick DPLC banks | Extra banks for duplicate-character sidekicks (global running offset) |
| `0x39000+` | Sidekick tail appendages | Extra banks for duplicate Tails tail sprites (Obj05) |
| `0x40000` | Title Card | Zone/act title card patterns |

```java
// LevelManager.java
private static final int OBJECT_PATTERN_BASE = 0x20000;
private static final int HUD_PATTERN_BASE = 0x28000;
```

The `PatternAtlas` stores all patterns in a HashMap keyed by pattern ID. Each category has a fixed base to prevent collisions when new sheets are dynamically registered (e.g., zone-specific objects like SmashableGround in HTZ).

### Rationale

1. **Level patterns remain cached** - No need to reload level tiles after results screen, enabling instant transitions.

2. **Simpler state management** - No need to track which tiles were overwritten or restore them later.

3. **Easier debugging** - Level and UI patterns coexist without interference; inspecting the atlas shows all patterns.

4. **No VRAM constraints** - Modern systems have abundant texture memory; emulating the 64KB limit adds complexity with no benefit.

### Verification

The rendered output is identical to the original - the same graphics appear at the same screen positions. Only the internal storage differs.

---

## HTZ Cloud Scroll Precision Fix

**Location:** `SwScrlHtz.java`
**ROM Reference:** `s2.asm` lines 15823-15831 (fixBugs path) vs line 15833 (original path)

### Original Implementation

The original ROM uses `asr.w #4,d1` to initialize the cloud layer scroll delta. This word-sized shift loses the fractional bits that were set up via `swap d1`, causing a visible 2-frame jerkiness in cloud scrolling as the fractional accumulator repeatedly underflows and corrects.

```asm
; Original (buggy) path:
    asr.w   #4,d1          ; word shift discards upper 16 bits (fractional part)
```

### Our Implementation

We use the `fixBugs` path from the disassembly, which preserves fractional precision:

```asm
; fixBugs path:
    swap    d1
    asr.l   #4,d1          ; long shift preserves fractional bits across the swap
```

This is implemented in `SwScrlHtz.java` using a 32-bit arithmetic shift after swapping the high/low words, matching the corrected assembly path.

### Rationale

1. **Known bug in original ROM** - The disassembly explicitly marks this as a bug with a `fixBugs` conditional path.
2. **Smoother cloud animation** - The fractional bits produce smooth per-frame cloud movement instead of the original's periodic stutter.
3. **Matches disassembly intent** - The `fixBugs` path represents what the original developers intended before the word/long shift mistake.

### Verification

Cloud layer scrolling in HTZ is smooth across all frames, without the 2-frame jitter visible in the original ROM.

---

## MCZ Rotating Platforms Child Cleanup

**Location:** `MCZRotPformsObjectInstance.java`
**ROM Reference:** `s2.asm` lines 53707-53726 (child spawn), lines 53801-53803 / 53826-53828 (`MarkObjGone2` calls)

### Original Implementation

In the ROM, all three objects (parent + 2 children) live in the same flat object RAM table. Children are allocated via `AllocateObjectAfterCurrent` into adjacent SST slots. Each object independently calls `MarkObjGone2` using `objoff_32` (base X = the parent's original spawn X):

```asm
; routine 2 exit (MTZ path / parent):
loc_27C5E:
    move.w  objoff_32(a0),d0
    jmpto   JmpTo3_MarkObjGone2

; routine 4 exit (MCZ path / children):
loc_27C9A:
    move.w  objoff_32(a0),d0
    jmpto   JmpTo3_MarkObjGone2
```

`Obj6A_InitSubObject` copies the parent's `x_pos` to each child's `objoff_32`:

```asm
    move.w  x_pos(a0),objoff_32(a1)
```

So all three share the same base X and self-destruct at the same camera threshold.

### Our Implementation

Our engine uses a two-tier object system: placement-windowed objects (`activeObjects`) and unwindowed dynamic objects (`dynamicObjects`). The parent is placement-managed, but children are dynamic and have no off-screen removal.

Instead of independent self-cleanup, the parent's `onUnload()` explicitly destroys its children:

```java
@Override
public void onUnload() {
    for (MCZRotPformsObjectInstance child : children) {
        child.setDestroyed(true);
    }
    children.clear();
}
```

### Rationale

1. **Architectural mismatch** - The ROM's flat object table lets every object run `MarkObjGone2` against a base X. Our dynamic objects have no equivalent windowing mechanism.

2. **Parent-driven cleanup is idiomatic** - This matches the pattern used by other parent-child objects in the engine (`AizGiantRideVineObjectInstance`, `Sonic1CaterkillerBadnikInstance`, `SolBadnikInstance`).

3. **Same trigger point** - The parent's Placement window check uses the same spawn X that the ROM's `MarkObjGone2` checks via `objoff_32`, so cleanup occurs at the same camera position.

### Verification

When the camera leaves the MCZ crate area, all three objects are removed. On return, the parent is re-spawned by Placement and creates fresh children — no accumulation.

---

## Multi-Sidekick Daisy Chain

**Location:** `Engine.java`, `SidekickCpuController.java`, `SpriteManager.java`, `LevelManager.java`
**ROM Reference:** Sonic 2 supports exactly one CPU-controlled Tails at `$FFFFB040` (Sidekick). S3K adds Knuckles as a playable character but still has at most one CPU follower.

### Original Implementation

The ROM allocates a single fixed RAM slot for the sidekick character. `Tails_CPU` routines follow Sonic with a 17-frame position/input history delay. There is no support for multiple sidekicks, sidekick chains, duplicate characters, or the main player character also appearing as a sidekick.

### Our Implementation

The engine supports an arbitrary number of CPU-controlled sidekicks configured via comma-separated `SIDEKICK_CHARACTER_CODE` (e.g. `"tails,knuckles,sonic,sonic"`). Key divergences:

**Daisy chain following.** Each sidekick follows the one in front of it rather than all following Sonic. The chain uses the same 17-frame history delay per link. When a middle sidekick despawns, downstream sidekicks self-heal to the nearest settled leader via `getEffectiveLeader()`.

**Duplicate characters.** The same character can appear multiple times (e.g. five Sonics). Each duplicate gets a separate DPLC pattern bank in the virtual `0x38000+` range to prevent atlas corruption. The `PlayerSpriteRenderer` uses `renderPatternWithId()` to bypass the VDP's 11-bit pattern index limit.

**Same character as player.** The main player's character can also be used as a sidekick (e.g. Sonic main + Sonic sidekick). VRAM bank slot allocation ensures each instance has independent DPLC storage.

**Per-character respawn strategies.** The ROM's Tails CPU respawn is a single fly-in-from-above behavior. The engine generalizes this via `SidekickRespawnStrategy`:
- **Tails**: flies in from above (ROM-accurate)
- **Knuckles**: glides in from the screen edge opposite to the leader's movement direction
- **Sonic**: walks or spindashes in from the nearest floor at the screen edge

**Parallel respawn.** When multiple sidekicks despawn simultaneously, they respawn in parallel using chain healing rather than cascading one-by-one.

**P2 input routing.** Only the first sidekick in the chain receives Player 2 controller input, matching the ROM's single-sidekick model.

### VRAM Bank Limits

Duplicate-character sidekick DPLC banks occupy pattern ID ranges above the VDP's native 11-bit (2048 tile) limit:

| Range | Purpose | Capacity |
|-------|---------|----------|
| `0x38000+` | Sidekick body DPLC banks | ~512 Sonic-type or ~64 before hitting tail range |
| `0x39000+` | Tails tail appendage (Obj05) banks | ~1,170 tail appendages |
| `0x40000` | Title Card (next range) | Collision boundary |

Practical limits before title card pattern corruption:
- **All-Sonic sidekicks** (no tail appendages): ~512
- **All-Tails sidekicks** (body + tail): ~65
- **Mixed configurations**: proportional; typical setups (≤10 sidekicks) are well within budget

### Rationale

1. **Fun/novelty** — Multiple characters running together in a chain looks great and extends the ROM's single-sidekick concept naturally.
2. **Architecture exercise** — The `SidekickRespawnStrategy` interface and chain healing demonstrate clean extension of ROM-accurate systems without if/else chains.
3. **No impact on accuracy** — Single-sidekick configurations behave identically to the ROM. Multi-sidekick behavior only activates when explicitly configured.

### Configuration

```json
{
  "SIDEKICK_CHARACTER_CODE": "tails,knuckles,sonic"
}
```

Empty string disables sidekicks (default). Single value preserves ROM-accurate single-sidekick behavior.

---

## Bonus Stage Game Mode

**Location:** `GameLoop.java`, `GameMode.java`
**ROM Reference:** `sonic3k.asm` Level: routine (line 7504)

### Original Implementation

S3K bonus stages (Gumball, Pachinko, Slots) are loaded through the normal `Level()` routine. The zone ID changes to a bonus zone ($1300/$1400/$1500), the level loads, and the game loop runs identically to any other level. No separate game mode exists.

### Engine Implementation

Bonus stages use a distinct `GameMode.BONUS_STAGE` that runs the same level rendering/physics/object pipeline as `LEVEL` mode, but with an explicit `AbstractBonusStageCoordinator` managing entry/exit lifecycle, state persistence, and ring gains.

### Reason

The engine's `GameLoop` dispatches behavior based on `GameMode`. Overloading `LEVEL` with conditional bonus stage logic would scatter bonus-specific checks across the codebase (timer suppression, death plane disable, exit detection, state save/restore). A dedicated mode keeps the lifecycle explicit and contained in the coordinator.

### Impact

None on gameplay. The level pipeline (rendering, physics, objects, collision) is identical between `LEVEL` and `BONUS_STAGE` modes. The only difference is the coordinator managing transitions.

---

## HCZ Conveyor Belt Rolling State Clear

**Location:** `HCZConveyorBeltObjectInstance.java` (`capturePlayer()`)
**ROM Reference:** `sonic3k.asm` lines 66490-66511 (standing capture), 66528-66547 (hanging capture)

### Original Implementation

The ROM's capture sequences for the HCZ conveyor belt (Obj 0x3E) do not contain an explicit `bclr #Status_Roll,status(a1)`. The capture code clears velocities, sets `object_control` to 3, snaps the player's Y position, and sets the mapping frame, but never directly modifies `Status_Roll`:

```asm
; Standing capture (sonic3k.asm:66490-66503):
    clr.w   x_vel(a1)
    clr.w   y_vel(a1)
    clr.w   ground_vel(a1)
    andi.b  #$FC,render_flags(a1)   ; clears render flip bits, NOT status
    move.w  d0,y_pos(a1)
    move.b  #0,anim(a1)
    move.b  #3,object_control(a1)
    move.b  #$63,mapping_frame(a1)
    ; ... state init, DPLC call
```

On the original hardware, `Status_Roll` is effectively neutralised during capture through side-effects of `object_control = 3` altering the player's main update path (skipping `Sonic_CheckRoll` and related routines). The release path unconditionally sets `Status_Roll` via `bset #Status_Roll,status(a1)` (sonic3k.asm:66454).

### Our Implementation

We explicitly clear the rolling state during capture:

```java
private void capturePlayer(AbstractPlayableSprite player, PlayerBeltState state,
                           int snapY, int initialFrame, int initialPhase) {
    player.setXSpeed((short) 0);
    player.setYSpeed((short) 0);
    player.setGSpeed((short) 0);
    player.setRenderFlips(false, false);

    // Explicit roll clear — not present in ROM capture sequence
    if (player.getRolling()) {
        player.setRolling(false);
    }

    player.setCentreY((short) snapY);
    // ...
}
```

### Rationale

1. **Touch responses run while object-controlled** — `object_control = 3` suppresses solid object collisions and animation, but does NOT suppress touch response (enemy/badnik) collision checks.

2. **Rolling = attacking** — `ObjectManager.isPlayerAttacking()` returns `true` when `getRolling()` is true. If `Status_Roll` persists from a jump into belt capture, the player incorrectly destroys enemies on contact (e.g. Chopper in HCZ) instead of taking damage.

3. **Observed gameplay confirms vulnerability** — On original hardware, Chopper can grab and hurt Sonic while he is on the conveyor belt, proving the player is NOT in an attacking state during capture.

4. **ROM clears implicitly, engine needs explicit** — The ROM achieves this through `object_control = 3` altering the player update path in ways our engine doesn't replicate as a side-effect. The explicit clear produces identical gameplay behavior.

5. **Must clear before Y snap** — `setRolling(false)` restores standing radii, which changes sprite height. Clearing after `setCentreY()` would shift the centre by half the height delta (5px for Sonic). Clearing before the snap ensures the snap uses standing-height coordinates.

### Verification

With the fix, the player is vulnerable to enemy touch responses while on the conveyor belt, matching original hardware behavior. The release path still unconditionally sets `Status_Roll`, so belt exit behavior is unaffected.
