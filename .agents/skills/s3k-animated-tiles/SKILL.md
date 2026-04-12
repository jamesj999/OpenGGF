---
name: s3k-animated-tiles
description: Use when implementing S3K animated tile triggers for a zone -- AniPLC script registration, gating conditions, dynamic art overrides in Sonic3kPatternAnimator.
---

# Implement S3K Animated Tile Triggers

Implement zone-specific animated tile triggers for Sonic 3 & Knuckles in `Sonic3kPatternAnimator`. This skill covers finding AniPLC script addresses in the ROM/disassembly, adding constants, wiring the zone case into both `resolveAniPlcAddr()` and `update()`, and handling gating conditions (boss active, intro state, camera position).

## Inputs

$ARGUMENTS: Zone abbreviation (e.g., "HCZ", "MGZ", "CNZ1", "LBZ Act 2") and optionally a path to a zone analysis spec file containing the animated tiles section.

## Related Skills

- **s3k-disasm-guide** (`.agents/skills/s3k-disasm-guide/SKILL.md`) for disassembly navigation, label conventions, RomOffsetFinder commands, and zone abbreviations.
- **s3k-zone-analysis** for generating the analysis spec that feeds this skill Phase 1.
- **s3k-plc-system** (`.agents/skills/s3k-plc-system/SKILL.md`) for PLC-driven art loading during act transitions and boss arenas. PLCs load Nemesis-compressed art; AniPLC loads uncompressed art. They target different VRAM regions but can overlap if not coordinated.

## Architecture

The animated tile system has a clear delegation chain:

```
Sonic3kLevelAnimationManager (implements AnimatedPatternManager + AnimatedPaletteManager)
    |
    v  delegates to
Sonic3kPatternAnimator.update()
    |
    v  zone switch on zoneIndex
Zone-specific trigger method (e.g., updateAiz1(), updateHcz1(), or inline)
    |
    v  calls when conditions are met
runAllScripts()  or  individual script.tick()
    |
    v  per script
AniPlcScriptState.tick(level, graphicsManager)
    |
    v  copies pattern art into level Pattern[] and updates GPU textures
```

The ROM `Animate_Tiles` routine uses a dual offset table (`Offs_AniFunc` for the trigger function, `Offs_AniPLC` for the script data address) indexed by `(zone << 1 | act)`. Our engine splits this into:
- `resolveAniPlcAddr(zoneIndex, actIndex)` -- returns the AniPLC data address (or -1)
- `update()` zone switch -- runs the trigger logic

Both must be updated when adding a new zone.

## AniPLC Script Format

The binary format is shared between S2 and S3K (`zoneanimstart`/`zoneanimdecl` macros). Parsed by `AniPlcParser.parseScripts()`.

### Script List Header

```
dc.w count-1              ; number of scripts minus 1 (0xFFFF = empty list)
```

### Per-Script Entry

```
dc.l (duration & 0xFF) << 24 | artAddr   ; byte 0: global frame duration, bytes 1-3: 24-bit ROM art address
dc.w tiles_to_bytes(destTile)             ; VRAM destination = tile index * 32
dc.b frameCount, tilesPerFrame            ; frame count, tiles copied per frame
; frame data follows (variable length):
;   if duration >= 0 (global): frameCount bytes of tile IDs
;   if duration < 0 (per-frame): frameCount pairs of (tileId, frameDuration)
; padded to even alignment
```

### Example: AIZ1 waterfall cascade (AniPLC_AIZ1 script #0)

```asm
zoneanimdecl  -1, ArtUnc_AniAIZ1_0, $2E6,  9, $C
    dc.b  $3C, $4F    ; tile $3C, duration $4F
    dc.b  $30,   5    ; tile $30, duration 5
    dc.b  $18,   5    ; ...
    dc.b   $C,   5
    dc.b    0, $4F
    dc.b   $C,   3
    dc.b  $18,   3
    dc.b  $24,   1
    dc.b  $30,   1
```

- `duration = -1` (0xFF) means per-frame durations (each frame has its own duration byte)
- `ArtUnc_AniAIZ1_0` = uncompressed art ROM address
- `$2E6` = destination VRAM tile index
- `9` = 9 animation frames
- `$C` = 12 tiles per frame (tiles $2E6-$2F1 replaced each frame)

## Key Classes

| Class | File | Purpose |
|-------|------|---------|
| `Sonic3kPatternAnimator` | `game/sonic3k/Sonic3kPatternAnimator.java` | Zone switch, gating logic, runs scripts |
| `Sonic3kLevelAnimationManager` | `game/sonic3k/Sonic3kLevelAnimationManager.java` | Delegates `update()` to pattern animator + palette cycler |
| `AniPlcParser` | `level/animation/AniPlcParser.java` | Parses AniPLC binary scripts from ROM |
| `AniPlcScriptState` | `level/animation/AniPlcScriptState.java` | Per-script runtime state, `tick()` applies frames |
| `Sonic3kConstants` | `game/sonic3k/constants/Sonic3kConstants.java` | ROM address constants for AniPLC data |
| `Sonic3kLevelEventManager` | `game/sonic3k/Sonic3kLevelEventManager.java` | Boss flag and event state (used for gating) |

## Implementation Process

### Phase 1: Read the Analysis Spec

If a zone analysis spec file was provided, read its **Animated Tiles** section. This section lists:
- Which `AnimateTiles_{ZONE}` handler the zone uses (from the `Offs_AniFunc` table)
- Whether it falls through to `AnimateTiles_DoAniPLC` (simple) or has custom logic
- The `AniPLC_{ZONE}` label and script count
- Gating conditions (boss flag, camera position, intro state, etc.)
- Any dynamic art overrides outside the AniPLC scripts

If no spec is provided, proceed to Phase 2 and find the information directly from the disassembly.

### Phase 2: Find AniPLC Addresses

**Step 1: Find the AnimateTiles handler and AniPLC label in the disassembly.**

Search `docs/skdisasm/s3.asm` for the zone handler and script data:

```bash
grep -n "AnimateTiles_{ZONE}\|AniPLC_{ZONE}" docs/skdisasm/s3.asm
```

The `Offs_AniFunc` / `Offs_AniPLC` dual table at line ~45952 shows the mapping. Each zone+act pair has two entries:
- `AnimateTiles_{ZONE}` -- the trigger function
- `AniPLC_{ZONE}` -- the script data label

Some zones share the same AniPLC data across both acts (e.g., `AniPLC_MGZ` for both MGZ1 and MGZ2) but have different trigger functions. Others have distinct per-act data (e.g., `AniPLC_AIZ1` vs `AniPLC_AIZ2`).

**Step 2: Read the trigger function.** Examine the `AnimateTiles_{ZONE}` routine starting from its label. Look for:
- `tst.b (Boss_flag).w` / `bne` -- boss gating
- `tst.b (Dynamic_resize_routine).w` -- intro/resize gating
- `cmpi.w #$xxx,(Camera_X_pos).w` -- camera position gating
- Direct fall-through to `AnimateTiles_DoAniPLC` -- simple case
- Custom DMA logic before/after the AniPLC call -- complex case (HCZ, LBZ)

**Step 3: Use RomOffsetFinder to find the ROM address of the AniPLC data.**

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.disasm.RomOffsetFinder" "-Dexec.args=--game s3k search AniPLC_{ZONE}" -q
```

If the tool does not find it directly, search the disassembly for the label definition:
```bash
grep -n "^AniPLC_{ZONE}:" docs/skdisasm/s3.asm
```

Then find the address by examining surrounding verified addresses or using `search-rom` with known data patterns from the script first entry.

**Step 4: Verify the address.** Read the ROM at the candidate address and confirm:
- First word is `count-1` (matches the number of `zoneanimdecl` entries minus 1)
- The 24-bit art addresses in each entry point to valid uncompressed art data
- Frame counts and tile counts match the disassembly

### Phase 3: Add ROM Constants to Sonic3kConstants.java

Add the AniPLC address constant(s) following the established naming pattern:

```java
// AniPLC_{ZONE}{ACT}: N scripts (brief description of what they animate)
// Verified by [verification method]
public static final int ANIPLC_{ZONE}{ACT}_ADDR = 0x0XXXXX;
```

Naming conventions observed in the codebase:
- `ANIPLC_AIZ1_ADDR` -- per-act when acts have different scripts
- `ANIPLC_AIZ2_ADDR`

If both acts share the same AniPLC data (e.g., MGZ), use a single constant:
```java
public static final int ANIPLC_MGZ_ADDR = 0x0XXXXX;
```

If the zone has dynamic art loaded outside AniPLC (like AIZ2 FirstTree), add those constants too:
```java
public static final int ART_UNC_{ZONE}_SOMETHING_ADDR = 0x0XXXXX;
public static final int ART_UNC_{ZONE}_SOMETHING_SIZE = 0xNNN;
public static final int ART_UNC_{ZONE}_SOMETHING_DEST_TILE = 0xNNN;
```

### Phase 4: Add Zone Case to Sonic3kPatternAnimator

You must update **two** methods:

#### 4a. Update `resolveAniPlcAddr()`

Add the zone case to the static address resolution method. This determines which AniPLC script data to parse at construction time.

```java
private static int resolveAniPlcAddr(int zoneIndex, int actIndex) {
    if (zoneIndex == 0) {
        return actIndex == 0
                ? Sonic3kConstants.ANIPLC_AIZ1_ADDR
                : Sonic3kConstants.ANIPLC_AIZ2_ADDR;
    }
    // Per-act AniPLC example:
    if (zoneIndex == N) {
        return actIndex == 0
                ? Sonic3kConstants.ANIPLC_{ZONE}1_ADDR
                : Sonic3kConstants.ANIPLC_{ZONE}2_ADDR;
    }
    // Shared AniPLC example (both acts use same data):
    if (zoneIndex == M) {
        return Sonic3kConstants.ANIPLC_{ZONE}_ADDR;
    }
    return -1;
}
```

**Zone index values** (from `Current_zone_and_act`):
| Index | Zone | Index | Zone |
|-------|------|-------|------|
| 0 | AIZ | 7 | MHZ |
| 1 | HCZ | 8 | SOZ |
| 2 | MGZ | 9 | LRZ |
| 3 | CNZ | 10 | SSZ |
| 4 | FBZ | 11 | DEZ |
| 5 | ICZ | 12 | TDZ/DDZ |
| 6 | LBZ | 13 | Outro |

#### 4b. Update `update()` zone switch

Add the zone trigger logic to the `update()` method switch statement. There are three common patterns:

**Pattern 1: Simple (no gating, e.g., MGZ boss-only gate)**

The ROM `AnimateTiles_MGZ` simply checks `Boss_flag` and falls through to `AnimateTiles_DoAniPLC`:

```java
case 2 -> { // MGZ
    if (!isBossActive()) {
        runAllScripts();
    }
}
```

For zones that use `AnimateTiles_DoAniPLC` directly with no custom trigger function (shown as `AnimateTiles_NULL` in the table), just call `runAllScripts()`:

```java
case N -> runAllScripts();
```

**Pattern 2: Boss-gated with additional conditions (e.g., AIZ1)**

The ROM checks multiple conditions before allowing animation:

```java
case 0 -> {
    if (actIndex == 0) {
        updateAiz1();   // Boss_flag + Dynamic_resize_routine gating
    } else {
        updateAiz2();   // Boss_flag + camera X threshold + FirstTree art
    }
}
```

For boss gating, query the level event manager. The AIZ example uses a dedicated `isAizBossActive()` method. Other zones may need a more generic boss flag check via `Sonic3kLevelEventManager`.

**Pattern 3: Custom logic with partial script execution (e.g., AIZ2, HCZ, LBZ)**

Some zones run only specific scripts under certain conditions, or perform manual DMA art loads alongside or instead of AniPLC:

```java
private void updateAiz2() {
    if (isAizBossActive()) {
        return;
    }
    int cameraX = GameServices.camera().getX();
    if (cameraX >= 0x1C0) {
        runAllScripts();          // All 5 scripts
        firstTreeApplied = false;
    } else {
        scripts.get(0).tick(level, graphicsManager);  // Only script #0
        if (!firstTreeApplied && firstTreePatterns != null) {
            applyFirstTreeArt();   // Static art override
            firstTreeApplied = true;
        }
    }
}
```

**Pattern 4: Zones with manual tile animation (HCZ2, CNZ, ICZ, LBZ, LRZ, DPZ)**

Some zones do NOT use AniPLC scripts at all -- they compute tile art offsets manually based on `Events_bg` scroll values or frame counters, then DMA art directly. These zones have custom `AnimateTiles_{ZONE}` functions that never call `AnimateTiles_DoAniPLC`.

For these zones, implementing the custom logic requires:
1. Still register the AniPLC address in `resolveAniPlcAddr()` (the ROM loads it via `Offs_AniPLC` even if the trigger function uses custom logic, and some zones call `AnimateTiles_DoAniPLC` conditionally after their custom work -- see HCZ1 which does waterline logic THEN falls through to DoAniPLC at the end)
2. Implement the custom DMA logic as Java methods, loading uncompressed art patterns and writing them to level tiles + GPU textures
3. The custom update method may still call `runAllScripts()` at the end if the ROM falls through to `AnimateTiles_DoAniPLC` after its custom work

### Phase 5: Build and Verify

```bash
mvn package -q
```

Verify:
1. No compilation errors
2. The zone animated tiles display correctly in-game
3. Gating conditions work (tiles do not animate during boss fights, during intro sequences, etc.)
4. If the zone has act-specific behavior, test both acts

Run existing S3K tests to ensure no regressions:
```bash
mvn test -Dtest=TestS3kAiz1SkipHeadless,TestSonic3kLevelLoading,TestSonic3kBootstrapResolver,TestSonic3kDecodingUtils -q
```

## Gating Conditions Reference

Common gating conditions found in `AnimateTiles_{ZONE}` routines, with their assembly checks and Java equivalents:

| Condition | Assembly | Java Equivalent | Zones |
|-----------|----------|-----------------|-------|
| Boss not active | `tst.b (Boss_flag).w` / `bne` | Check via `Sonic3kLevelEventManager` boss flag | AIZ1, AIZ2, MGZ |
| After intro | `tst.b (Dynamic_resize_routine).w` / `bne` | `isSkipIntro \|\| AizPlaneIntroInstance.isMainLevelPhaseActive()` | AIZ1 |
| Camera past threshold | `cmpi.w #$xxx,(Camera_X_pos).w` / `bhs` | `GameServices.camera().getX() >= 0xNNN` | AIZ2 |
| Events_bg flag set | `tst.b (Events_bg+$16).w` / `beq` | Check zone event manager state | HCZ1 |
| LBZ2 special state | `tst.b $F(a3)` / `bne` | Zone-specific event flag | LBZ2 |

**Important:** Always wrap `GameServices.camera()` calls in try/catch when accessing from the pattern animator, since the camera may not be initialized during headless tests or early construction. See the `updateAiz2()` method for the established pattern.

## Dynamic Art Overrides

Some zones load art outside the AniPLC script system that targets the same VRAM tiles. These are "dynamic art overrides" -- one-shot or event-driven art loads that conflict with or supplement AniPLC scripts.

### How They Work in the ROM

The ROM `AnimateTiles_{ZONE}` function may:
1. Skip certain AniPLC scripts when conditions are not met
2. Instead DMA static art to the tiles those scripts would normally animate
3. Resume the scripts once conditions change

### Example: AIZ2 FirstTree

When `Camera_X_pos < 0x1C0`, the ROM:
- Runs only AniPLC script #0 (fire/explosion)
- Skips scripts #1-4 (waterfall, fire variants) -- those target tiles starting at $0CA
- Instead loads `ArtUnc_AniAIZ2_FirstTree` (35 tiles, 0x460 bytes) to VRAM tile $0CA
- This shows the tree art at the start of the level before the camera scrolls right

When `Camera_X_pos >= 0x1C0`:
- Runs all 5 AniPLC scripts (which overwrite the FirstTree art with animated tiles)
- Resets `firstTreeApplied` so the tree art can be re-applied if camera scrolls back

### Implementation Pattern

In `Sonic3kPatternAnimator`:
1. Pre-load the override art in the constructor (call `loadXxxArt(reader)`)
2. Store as `Pattern[]` field + boolean `xxxApplied` flag
3. In the zone update method, apply once when conditions require the static art
4. Reset the applied flag when conditions change back to normal animation

```java
// Constructor
if (zoneIndex == N && actIndex == M) {
    this.overridePatterns = loadOverrideArt(reader);
} else {
    this.overridePatterns = null;
}
this.overrideApplied = false;

// Update method
private void updateZoneN() {
    if (conditionForOverride) {
        // Run subset of scripts
        if (!scripts.isEmpty()) {
            scripts.get(0).tick(level, graphicsManager);
        }
        // Apply static override once
        if (!overrideApplied && overridePatterns != null) {
            applyOverrideArt();
            overrideApplied = true;
        }
    } else {
        runAllScripts();
        overrideApplied = false;
    }
}
```

## Common Mistakes

1. **Wrong AniPLC address.** The disassembly label `AniPLC_{ZONE}` resolves to a different ROM offset than you might expect. Always verify the address by checking the first word (script count - 1) matches the number of `zoneanimdecl` entries in the assembly, and that the art addresses point to valid data. Use RomOffsetFinder or binary pattern search.

2. **Missing gating condition.** Every zone `AnimateTiles_{ZONE}` function has specific conditions that must be met before scripts run. Omitting the boss flag check causes tiles to animate during the boss fight (when the ROM freezes them). Omitting the `Dynamic_resize_routine` check causes AIZ1 tiles to animate during the intro (overwriting beach shoreline art).

3. **Act asymmetry.** Many zones have different behavior per act even when sharing the same AniPLC data. For example, FBZ acts 1 and 2 both use `AnimateTiles_NULL` (no custom trigger), but ICZ act 1 also uses `AnimateTiles_NULL` while ICZ act 2 uses `AnimateTiles_ICZ` (custom logic). Always check both act entries in the `Offs_AniFunc` table.

4. **Forgetting `resolveAniPlcAddr()`.** Adding a case to `update()` without adding the corresponding case to `resolveAniPlcAddr()` means `scripts` will be empty (the constructor gets -1 and returns `List.of()`). Both methods must be updated together.

5. **Not priming scripts for the new zone.** The constructor already calls `AniPlcParser.primeScripts()` for all non-AIZ1-intro zones. You generally do not need to add priming logic unless your zone has special priming requirements. But verify the constructor existing priming logic does not skip your zone erroneously.

6. **Confusing AniPLC with PLC.** AniPLC (`zoneanimstart`/`zoneanimdecl`) animates tiles every frame from uncompressed art. PLC (`plrlistheader`/`plreq`) decompresses Nemesis art into VRAM once (at level load or triggered by events). They are different systems. AniPLC scripts live in `AniPLC_{ZONE}` labels and are parsed by `AniPlcParser`. PLCs live in `PLC_XX` entries and are parsed by `PlcParser`.

7. **Capacity not ensured for dynamic art.** `AniPlcParser.ensurePatternCapacity()` handles the AniPLC scripts automatically, but if your zone has dynamic art overrides (like AIZ2 FirstTree), you must call `level.ensurePatternCapacity()` separately for the override destination tiles. See the AIZ2 block in the constructor.

8. **Custom tile logic zones.** Some zones (HCZ2, CNZ, ICZ, LBZ, LRZ, DPZ) compute art offsets dynamically from scroll/event values rather than using AniPLC frame sequences. These require manual implementation of the ROM DMA math, not just wiring up `runAllScripts()`. Check whether the zone `AnimateTiles_{ZONE}` ever calls `AnimateTiles_DoAniPLC` -- if not, it is entirely custom.
