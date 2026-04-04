# Implement Sonic 3&K Parallax Background

Implement a zone-specific parallax scroll handler for Sonic 3 & Knuckles with ROM accuracy. This skill covers finding deform data in the disassembly, porting the 68000 scroll math to Java, handling dynamic background changes during gameplay, and integrating with the rendering pipeline.

## Inputs

$ARGUMENTS: Zone name or abbreviation (e.g., "HCZ", "Hydrocity Zone", "CNZ1", "FBZ Act 2")

## Zone Analysis Spec (Optional)

If a zone analysis spec exists at `docs/s3k-zones/{zone}-analysis.md`, read it first. The **Parallax** section provides:
- Band count and deform type
- Data table labels and disassembly line numbers
- Water split information
- Act-specific differences

This saves time on Phase 1 (finding the deform routine) but the disassembly remains the source of truth for implementation details.

This spec is produced by the **s3k-zone-analysis** skill (`.claude/skills/s3k-zone-analysis/skill.md`).

## Related Skills

- **s3k-disasm-guide** (`.claude/skills/s3k-disasm-guide/skill.md`) for disassembly navigation, label conventions, RomOffsetFinder commands, and zone abbreviations.
- **s3k-plc-system** (`.claude/skills/s3k-plc-system/skill.md`) for PLC-driven art loading during act transitions and boss arenas (PLCs can trigger mid-level background art changes).
- **s3k-zone-analysis** (`.claude/skills/s3k-zone-analysis/skill.md`) for producing a zone analysis spec that pre-identifies deform routine locations and band counts.

## Architecture Overview

The parallax system has three layers:

```
ParallaxManager (singleton, game-agnostic)
    |
    v
ScrollHandlerProvider (per-game, from GameModule)
    |
    v
SwScrl{Zone} extends AbstractZoneScrollHandler
    |
    v  fills 224-entry int[] horizScrollBuf with packed (FG<<16 | BG) values
    |
    v  sets vscrollFactorBG (BG vertical scroll)
    |
    v  optionally sets perLineVScrollBG / perColumnVScrollBG
```

The GPU shader reads the 224-entry HScroll buffer (one value per visible scanline) and applies per-line horizontal scroll offsets to the background tilemap. This is how the Mega Drive's VDP HScroll works: each scanline can have an independent scroll position, creating the parallax effect.

## General Lessons From Recent S3K Work

Do not treat "parallax background" as meaning only the scroll handler. In S3K, a visually correct background can depend on four cooperating systems:

- `SwScrl{Zone}` for HScroll / BG Y math
- Runtime background art uploads in `Sonic3kPatternAnimator`
- Zone/event startup logic that seeds initial background strips or alternate rows
- Water/renderer boundary logic in the level renderer

If the scene is partly correct but still has static rows, garbage tiles, seams, or waterline mismatches, the missing piece is often outside the deform routine.

## Implementation Process

### Phase 1: Find the Deform Routine in the Disassembly

Every S3K zone has a `{ZONE}_Deform` routine (sometimes `{ZONE}{ACT}_Deform`) in `docs/skdisasm/sonic3k.asm`. This is the function that produces per-line scroll values.

**Step 1: Search for the deform routine:**

```bash
grep -n "{ZONE}_Deform\|{ZONE}1_Deform\|{ZONE}2_Deform" docs/skdisasm/sonic3k.asm
```

Known deform routines in the S3K disassembly:

| Label | Line (approx) | Zone |
|-------|---------------|------|
| `HCZ1_Deform` | 105796 | Hydrocity Act 1 |
| `HCZ2_Deform` | 106179 | Hydrocity Act 2 |
| `CNZ1_Deform` | 107660 | Carnival Night Act 1 |
| `FBZ_Deform` | 108854 | Flying Battery |
| `ICZ1_Deform` | 110416 | Ice Cap Act 1 |
| `LBZ2_Deform` | 111581 | Launch Base Act 2 |
| `MHZ_Deform` | 112317 | Mushroom Hill |
| `LRZ1_Deform` | 115384 | Lava Reef Act 1 |

AIZ and MGZ are already implemented. If a zone is not listed, search more broadly:
```bash
grep -n "Deform\|BGDeform\|BG_Deform" docs/skdisasm/sonic3k.asm | grep -i "{zone}"
```

**Step 2: Find associated data tables.** Deform routines reference:
- **DeformArray / BGDeformArray** - heights of each parallax band (in pixels)
- **BGDeformIndex** - scatter-fill index tables (offsets into HScroll_table)
- **BGDeformOffset** - per-band pixel offsets
- **Waterline Scroll Data** - binary files for water deformation (HCZ, LBZ)

Search for these around the deform routine:
```bash
grep -n "{ZONE}.*DeformArray\|{ZONE}.*DeformIndex\|{ZONE}.*DeformOffset" docs/skdisasm/sonic3k.asm
```

Also check for external binary data files:
```bash
find docs/skdisasm/Levels/{ZONE}/Misc/ -name "*Waterline*" -o -name "*Scroll*" -o -name "*Deform*"
```

**Step 3: Find the BackgroundInit and BackgroundEvent routines.** These set up the background on level load and handle dynamic background changes:
```bash
grep -n "{ZONE}.*BackgroundInit\|{ZONE}.*BackgroundEvent\|{ZONE}.*ScreenInit\|{ZONE}.*ScreenEvent" docs/skdisasm/sonic3k.asm
```

Do one more search pass for direct background-art movement code. Many S3K zones update background tiles with direct DMA/VRAM uploads in parallel with the deform routine.

Useful searches:
```bash
grep -n "{ZONE}.*DMA\|{ZONE}.*QueueDMATransfer\|{ZONE}.*Add_To_DMA_Queue\|{ZONE}.*ArtUnc" docs/skdisasm/sonic3k.asm
grep -n "{ZONE}.*Fix\|{ZONE}.*Background\|{ZONE}.*Waterline" docs/skdisasm/sonic3k.asm
```

If the disassembly is rebuilding strips, copying partial rows, or swapping alternate background art, that work belongs in a runtime art loader such as `Sonic3kPatternAnimator`, not in the scroll handler.

### Phase 2: Understand the Deform Routine

S3K deform routines all follow common patterns. Understanding these is essential.

#### 2.1 The HScroll_table

The deform routine writes to a 32-word (64-byte) array called `HScroll_table`. Each word is a BG scroll X position. The `ApplyDeformation` routine then maps these words to the 224 visible scanlines using a height array.

**HScroll_table is NOT the final per-line buffer.** It's an intermediate lookup table. The deform array heights determine how many scanlines each entry covers.

#### 2.2 ApplyDeformation (the shared dispatcher)

`ApplyDeformation` (line ~103659 in sonic3k.asm) reads a deform height array and distributes HScroll_table values to 224 scanlines. Its algorithm:

1. Read camera BG Y position
2. Walk the height array, skipping entries that are above the visible area
3. For remaining entries, copy the corresponding HScroll_table word to `count` scanlines
4. **Per-line flag (bit 15 / 0x8000):** When a height entry has bit 15 set, the lower 15 bits give the count, but each scanline gets its own unique HScroll_table word (consumed sequentially) instead of one word repeated

In Java, this maps to the `writeDeformBands()` / `applyBgDeformation()` pattern used in SwScrlAiz and SwScrlMgz.

#### 2.3 Common 68000 Scroll Math Patterns

**Fixed-point camera-to-BG conversion:**
```asm
move.w  (Camera_X_pos_copy).w,d0   ; Load camera X (16-bit signed)
swap    d0                          ; Move to high word (= value << 16)
clr.w   d0                          ; Clear low word (now 16.16 fixed-point)
asr.l   #1,d0                      ; Divide by 2 (BG at half speed)
```
Java equivalent:
```java
int d0 = ((short) cameraX) << 16;  // Sign-extend to 16.16
d0 >>= 1;                           // Arithmetic shift right = /2
short bgScroll = (short)(d0 >> 16); // Extract integer part
```

**Cascaded speed levels (subtract step each band):**
```asm
move.l  d0,d1       ; d1 = step (copy of base)
asr.l   #3,d1       ; step = base/8
swap    d0           ; Extract integer
move.w  d0,(a1)+     ; Write BG value for this band
swap    d0
sub.l   d1,d0        ; Next band is slower by 1/8
```
Java equivalent:
```java
int d0 = ((short) cameraX) << 16;
d0 >>= 1;          // Start at half speed
int d1 = d0 >> 3;  // Step = 1/8 of current
for (int i = 0; i < bandCount; i++) {
    hScrollTable[i] = (short)(d0 >> 16);
    d0 -= d1;       // Each band slower
}
```

**Auto-scrolling clouds (persistent accumulator):**
```asm
add.w  #$500,(accumulator)   ; Advance cloud phase each frame
```
Java:
```java
private int cloudAccumulator; // persists across frames
cloudAccumulator += 0x500;    // 16.16 fixed-point sub-pixel increment
```

#### 2.4 BG Vertical Scroll Patterns

The BG Y position is usually a fraction of camera Y:
```asm
move.w  (Camera_Y_pos_copy).w,d0
asr.w   #2,d0                      ; BG Y = camera Y / 4
move.w  d0,(Camera_Y_pos_BG_copy).w
```
Java:
```java
vscrollFactorBG = asrWord(cameraY, 2);  // cameraY / 4
```

Some zones incorporate screen shake:
```asm
move.w  (Screen_shake_offset).w,d2
sub.w   d2,d0               ; Remove shake from camera Y
asr.w   #2,d0               ; Apply BG ratio
add.w   d2,d0               ; Add shake back at full strength
```

#### 2.5 Scatter-Fill Deformation (HCZ2, FBZ Pattern)

Some zones don't fill HScroll_table sequentially. Instead they use an index table that specifies which HScroll_table slot each band writes to:

```asm
lea     (HCZ2_BGDeformIndex).l,a5
loc_5109C:
    move.b  (a5)+,d3        ; count-1 (0xFF = end marker)
    bmi.s   end             ; Negative = done
    ext.w   d3
    swap    d0              ; Get integer part
loc_510A4:
    move.b  (a5)+,d2        ; Byte offset into HScroll_table
    move.w  d0,(a1,d2.w)    ; Write BG value at that offset
    dbf     d3,loc_510A4
    swap    d0
    sub.l   d1,d0           ; Next speed level
    bra.s   loc_5109C
```

The index table format is: `[count-1, offset, offset, ..., count-1, offset, ..., 0xFF]`

Each group shares the same BG scroll speed. The offsets are byte offsets into HScroll_table (divide by 2 for word index).

#### 2.6 Waterline Deformation (HCZ, LBZ)

Some zones have water and split the deformation at the water surface:
- Above water: normal parallax bands
- Below water: sinusoidal ripple from a lookup table

Binary waterline data files:
- `docs/skdisasm/Levels/HCZ/Misc/HCZ Waterline Scroll Data.bin` (9312 bytes)
- `docs/skdisasm/Levels/LBZ/Misc/LBZ Waterline Scroll Data.bin` (4160 bytes)

These are indexed by the water level position relative to the camera.

#### 2.7 Per-Line Deformation Deltas (Heat Haze, Water Shimmer)

Many S3K zones apply fine per-line deltas on top of the base parallax. These are small (+/-2px) cyclic tables that create heat shimmer or water ripple:

```java
// Example: AIZ2 FG heat haze (32-word cycle, 0/1px shimmer)
private static final int[] FINE_HAZE_FG = {
    0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0
};

// Applied per-line after base parallax:
int deformIndex = (cameraY + line + frameCounter) & 0x1F; // 32-entry cycle
fgScroll += FINE_HAZE_FG[deformIndex];
```

These tables come from the disassembly. Search for `DeformDelta` or `FGDeform` labels near the zone's deform code.

### Phase 3: Implement the Java Scroll Handler

#### 3.1 Create the Handler Class

Create `SwScrl{Zone}.java` in `com.openggf.game.sonic3k.scroll`:

```java
package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * {Zone Name} scroll handler for Sonic 3K.
 *
 * Ports {ZONE}_Deform from the S3K disassembly (line ~XXXXX in sonic3k.asm).
 */
public class SwScrl{Zone} extends AbstractZoneScrollHandler {

    // ---- Deform height arrays (from {ZONE}_BGDeformArray) ----
    // 0x7FFF = terminator
    private static final int[] DEFORM_HEIGHTS = {
        // ... heights from disassembly ...
        0x7FFF
    };

    // ---- HScroll intermediate table ----
    private static final int HSCROLL_WORD_COUNT = 32;
    private final short[] hScrollTable = new short[HSCROLL_WORD_COUNT];

    // ---- Persistent state ----
    private int cloudAccumulator; // if zone has auto-scrolling clouds

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX, int cameraY,
                       int frameCounter, int actId) {
        resetScrollTracking();

        short fgScroll = negWord(cameraX);

        // Step 1: Build HScroll_table from camera position
        buildHScroll(cameraX);

        // Step 2: Compute BG vertical scroll
        vscrollFactorBG = computeBgY(cameraY);

        // Step 3: Apply deformation (map HScroll_table to 224 scanlines)
        applyDeformation(horizScrollBuf, fgScroll, vscrollFactorBG, DEFORM_HEIGHTS);

        // Step 4 (optional): Apply per-line FG/BG deltas (haze, water)
        // applyFineDeformation(horizScrollBuf, cameraY, frameCounter);
    }
    // ... (see sections below for method implementations)
}
```

#### 3.2 The HScroll Table Builder

Port the zone's deform routine. This fills the intermediate `hScrollTable[]`:

**Sequential fill pattern** (most zones):
```java
private void buildHScroll(int cameraX) {
    int d0 = ((short) cameraX) << 16; // 16.16 fixed-point
    d0 >>= 1;                          // Half speed
    int d1 = d0 >> 3;                  // Step = 1/8

    // Fill from bottom band up (or top down, match disassembly direction)
    for (int i = 0; i < bandCount; i++) {
        hScrollTable[startIndex - i] = (short)(d0 >> 16);
        d0 -= d1;
    }
}
```

**Scatter-fill pattern** (HCZ2, FBZ):
```java
// Index table: [count-1, byteOff, byteOff, ..., 0xFF]
private static final int[] DEFORM_INDEX = { /* from disassembly */ };

private void buildHScroll(int cameraX) {
    int d0 = ((short) cameraX) << 16;
    d0 >>= 1;
    int d1 = d0 >> 3;

    int pos = 0;
    while (pos < DEFORM_INDEX.length) {
        int count = DEFORM_INDEX[pos++];
        if ((count & 0x80) != 0) break; // 0xFF terminator (signed negative)
        for (int i = 0; i <= count; i++) {
            int byteOffset = DEFORM_INDEX[pos++];
            int wordIndex = byteOffset >> 1;
            hScrollTable[wordIndex] = (short)(d0 >> 16);
        }
        d0 -= d1;
    }
}
```

#### 3.3 The Deformation Applier

Map HScroll_table entries to 224 scanlines using the height array:

```java
private void applyDeformation(int[] horizScrollBuf, short fgScroll,
                               int bgY, int[] deformHeights) {
    int segIdx = 0;
    int tableIdx = 0;
    int y = (short) bgY;

    // Skip bands above the visible area
    int height = deformHeights[segIdx++] & 0x7FFF;
    while ((y - height) >= 0) {
        y -= height;
        tableIdx++;
        height = deformHeights[segIdx++] & 0x7FFF;
    }

    int line = 0;
    int firstCount = height - y;

    // Write first partial band
    line = writeBand(horizScrollBuf, line, firstCount, fgScroll, tableIdx);

    // Write remaining bands
    while (line < VISIBLE_LINES) {
        int raw = deformHeights[segIdx++];
        boolean perLine = (raw & 0x8000) != 0;
        int count = raw & 0x7FFF;
        tableIdx++;

        if (perLine) {
            // Each scanline gets its own unique HScroll_table value
            for (int i = 0; i < count && line < VISIBLE_LINES; i++, line++) {
                short bg = (short) -hScrollTable[Math.min(tableIdx + i, hScrollTable.length - 1)];
                horizScrollBuf[line] = packScrollWords(fgScroll, bg);
                trackOffset(fgScroll, bg);
            }
            tableIdx += count - 1; // consumed count entries
        } else {
            line = writeBand(horizScrollBuf, line, count, fgScroll, tableIdx);
        }
    }
}

private int writeBand(int[] buf, int startLine, int count,
                      short fgScroll, int tableIdx) {
    int clampedIdx = Math.max(0, Math.min(tableIdx, hScrollTable.length - 1));
    short bgScroll = (short) -hScrollTable[clampedIdx];
    int packed = packScrollWords(fgScroll, bgScroll);
    trackOffset(fgScroll, bgScroll);

    int endLine = Math.min(VISIBLE_LINES, startLine + count);
    for (int line = startLine; line < endLine; line++) {
        buf[line] = packed;
    }
    return endLine;
}
```

#### 3.4 M68KMath Utilities

Always use the `M68KMath` static imports for 68000-accurate arithmetic:

```java
import static com.openggf.level.scroll.M68KMath.*;
```

| Method | 68k Equivalent | Notes |
|--------|---------------|-------|
| `negWord(v)` | `neg.w` | Negate, return as signed 16-bit |
| `asrWord(v, n)` | `asr.w #n` | Arithmetic shift right (signed, 16-bit) |
| `packScrollWords(fg, bg)` | `move.l d0,(a1)+` | Pack FG (high 16) and BG (low 16) |
| `wordOf(v)` | `move.w` | Extract low 16 bits as signed |
| `divsWord(a, b)` | `divs.w` | Signed 16-bit division |
| `divuWord(a, b)` | `divu.w` | Unsigned 16-bit division |

#### 3.5 Optional: Per-Column VScroll

For fire transitions, wave effects, or boss arena distortion, return per-column vertical scroll offsets:

```java
private short[] perColumnVScroll = new short[20]; // 20 H40 columns
private boolean hasPerColumnVScroll = false;

@Override
public short[] getPerColumnVScrollBG() {
    return hasPerColumnVScroll ? perColumnVScroll : null;
}
```

#### 3.6 Optional: Screen Shake

Zones with screen shake override `getShakeOffsetX()` / `getShakeOffsetY()`:

```java
private int shakeOffsetX;
private int shakeOffsetY;

@Override
public int getShakeOffsetX() { return shakeOffsetX; }

@Override
public int getShakeOffsetY() { return shakeOffsetY; }
```

Screen shake values typically come from the zone's event handler (e.g., `Sonic3kAIZEvents` provides shake offsets for the battleship bombing sequence).

#### 3.7 Decide Whether the Zone Also Needs Runtime Background Art Uploads

Before considering the background complete, answer these questions:

1. Does the original code update VRAM destinations outside the deform routine?
2. Are there startup-only "repair" strips or alternate background rows loaded before gameplay begins?
3. Are those uploads keyed off camera Y, waterline delta, scroll deltas, or event state?

If yes, implement that path separately from the scroll handler.

Rules:

- Use AniPLC only for actual AniPLC scripts.
- Use direct raw ROM byte copies when the original code DMAs slices, rows, or partial strips rather than full pre-decoded animation frames.
- Preserve ROM addressing semantics exactly. If the original code indexes one contiguous art block and then jumps by a fixed offset, model that directly instead of inventing smaller independent source arrays.
- Cache the last selector/delta values so the engine only rewrites pattern slots when the source state changes.

### Phase 4: Register the Handler

**In `Sonic3kScrollHandlerProvider.java`:**

1. Add the handler field:
```java
private SwScrl{Zone} {zone}Handler;
```

2. Instantiate in `load()`:
```java
{zone}Handler = new SwScrl{Zone}();
```

3. Add to the `getHandler()` switch:
```java
case Sonic3kZoneConstants.ZONE_{ZONE} -> {zone}Handler;
```

4. If the zone needs `init()` called with specific parameters, add an `initForZone()` override or call `init()` from the handler's first `update()` frame.

If the handler needs auxiliary ROM tables or binary resources, wire them here and pass them through the constructor. Keep resource ownership in the provider rather than having the handler reach into the ROM layer directly.

Be defensive when the data is optional. Provider-routing tests often use lightweight or stub `Rom` instances. If an auxiliary read fails, fall back to constructing the handler without that optional effect instead of aborting provider initialization.

### Phase 5: Dynamic Background Changes

Many S3K zones change their background during gameplay. This is handled by the zone's **event handler** (in `game/sonic3k/events/`) working in coordination with the scroll handler.

#### 5.1 Types of Dynamic Background Changes

| Type | Example | Mechanism |
|------|---------|-----------|
| **Act transition** | AIZ1 fire -> AIZ2 | Event handler sets a flag/phase; scroll handler checks it and switches deform mode |
| **Boss arena** | CNZ1 boss scroll | Event handler changes `Events_routine_bg`; deform routine branches to boss-specific scroll |
| **Terrain swap** | AIZ1 intro -> main | Camera X threshold triggers palette/terrain/scroll mode change |
| **Water level change** | HCZ, LBZ | Water system updates; deform routine splits at water surface |
| **Indoor/outdoor** | FBZ | Event flag toggles between two deform index tables |

#### 5.2 Coordinating Scroll Handler with Event Handler

The scroll handler and event handler communicate through shared state. There are two patterns:

**Pattern A: Direct method calls (preferred)**

The event handler holds a reference to the scroll handler and calls methods on it:

```java
// In the event handler:
public class Sonic3k{Zone}Events extends Sonic3kZoneEvents {
    private SwScrl{Zone} scrollHandler;

    public void setScrollHandler(SwScrl{Zone} handler) {
        this.scrollHandler = handler;
    }

    private void onBossArenaSetup() {
        scrollHandler.setBossScrollMode(true);
        scrollHandler.setShakeOffset(shakeX, shakeY);
    }
}
```

**Pattern B: Phase/flag fields on the scroll handler**

The scroll handler tracks phases internally, and the event handler sets them:

```java
// In the scroll handler:
public enum ScrollPhase { NORMAL, BOSS_SCROLL, TRANSITION }
private ScrollPhase phase = ScrollPhase.NORMAL;

public void setPhase(ScrollPhase phase) { this.phase = phase; }

@Override
public void update(...) {
    switch (phase) {
        case NORMAL -> computeNormalDeform(...);
        case BOSS_SCROLL -> computeBossDeform(...);
        case TRANSITION -> computeTransitionDeform(...);
    }
}
```

#### 5.3 AIZ Fire Transition (Reference Implementation)

AIZ is the most complex dynamic background example. Study `SwScrlAiz.java` and `Sonic3kAIZEvents.java` to understand the pattern:

1. **AIZ1 intro mode** - Simplified parallax while ocean is visible (camera X < 0x1400)
2. **AIZ1 normal mode** - Full multi-band parallax with wave animation
3. **Fire transition** - Event handler triggers fire curtain; scroll handler switches to flat "plain deformation" mode with fire-specific BG X and per-column VScroll wave
4. **AIZ2 mode** - Different band heights and speed distribution after fire completes

The fire transition uses `FireSequencePhase` enum to track progress through 8 stages. The scroll handler checks `isFireTransitionScrollActive()` to decide which deform mode to use.

#### 5.4 Boss Scroll Mode (CNZ Pattern)

When a boss arena activates, the background scroll changes from camera-relative parallax to fixed-offset scrolling:

```asm
; CNZ1_BossLevelScroll - BG tracks a fixed offset from camera
move.w  (Camera_X_pos_copy).w,d0
subi.w  #$2F80,d0
move.w  d0,(Camera_X_pos_BG_copy).w
```

Implement this as a separate method called when the boss phase is active:

```java
private void computeBossScroll(int[] horizScrollBuf, int cameraX, int cameraY) {
    short fgScroll = negWord(cameraX);
    short bgScroll = negWord(cameraX - 0x2F80);
    vscrollFactorBG = /* boss-specific Y */;
    int packed = packScrollWords(fgScroll, bgScroll);
    Arrays.fill(horizScrollBuf, 0, VISIBLE_LINES, packed);
    trackOffset(fgScroll, bgScroll);
}
```

### Phase 5b: Companion Visual Effects (Water Zones)

A complete parallax background for water zones involves more than just the scroll handler. Several companion systems work alongside it to produce the full water surface appearance. **When implementing a zone with water (HCZ, LBZ, AIZ), investigate all of the following and note which are already implemented and which need wiring up.**

#### 5b.1 Palette Animation / Cycling

Most water zones cycle palette colours to animate the water surface without changing tile patterns. These are driven by `AnPal_{ZONE}` routines in sonic3k.asm.

**How to find:**
```bash
grep -n "AnPal_{ZONE}\|AnPal_Pal{ZONE}" docs/skdisasm/sonic3k.asm
```

**HCZ1 reference (implemented):**
- `AnPal_HCZ1` (sonic3k.asm ~line 3287): cycles 4 colours on palette line 3 (colours 3-6) every 8 game frames through a 4-frame table (`AnPal_PalHCZ1`, ~line 4087). Creates a brown/tan water shimmer.
- HCZ2 has **no palette cycling** (its `AnPal_HCZ2` just returns).
- Java: handled by `Sonic3kLevelAnimationManager` / level event system. Test: `TestS3kHczPaletteCycling.java`.

**What to check for a new zone:**
1. Does the zone have an `AnPal_{ZONE}` entry? How many frames, what timing?
2. Which palette line(s) and colour indices are cycled?
3. Are there separate Normal_palette and Water_palette cycles? (HCZ cycles both.)
4. Is it act-specific? (HCZ1 has cycling, HCZ2 does not.)

#### 5b.2 Animated Pattern Tiles (Water Surface Tiles)

Water surfaces use animated tiles — the VDP pattern data is swapped on a timer to create rippling water. These are defined by `AniPLC_{ZONE}` / `zoneanimdecl` entries in sonic3k.asm.

**How to find:**
```bash
grep -n "AniPLC_{ZONE}\|zoneanimdecl.*{ZONE}\|ArtUnc_Ani{ZONE}" docs/skdisasm/sonic3k.asm
```

**HCZ reference:**
- `AniPLC_HCZ1` (~line 55646): Two animation scripts:
  1. Water surface ripple: 3 frames, $24 bytes each, targeting VRAM tile $30C. This is the primary water surface tile animation.
  2. Water pulse/breathing: 16-entry bidirectional cycle (0→$2A→0), shared between HCZ1 and HCZ2 (`ArtUnc_AniHCZ__1`).
- `AniPLC_HCZ2` (~line 55672): Tighter 4-frame water animation at 3-frame hold, plus the shared pulse.
- Look for `ArtUnc_AniHCZ1_WaterlineBelow`, `ArtUnc_AniHCZ1_WaterlineAbove` — separate tile sets for above/below waterline rendering.

**What to check for a new zone:**
1. How many animation scripts does `AniPLC_{ZONE}` define?
2. Which VRAM tile addresses are targets? (The hex value after the art label in `zoneanimdecl`.)
3. Are there waterline-specific tile sets (above vs below water)?
4. Is animation timing act-specific?

#### 5b.3 Water Surface Sprites (Wave Splash Objects)

Water zones typically have sprite objects that render the visible water surface line — a shimmering/rippling strip drawn as sprites on top of the tilemap.

**How to find:**
```bash
grep -n "Obj_{ZONE}Wave\|Obj_{ZONE}Water\|WaveSplash\|WaterSplash" docs/skdisasm/sonic3k.asm
```

**HCZ reference (implemented):**
- `Obj_HCZWaveSplash` (~line 43161 in sonic3k.asm): The primary water surface sprite.
  - Positioned at Y = Water_level, X = (Camera_X & 0xFFE0) + 0x60, alternating +0x20 on odd frames.
  - 7 mapping frames, cycles frames 1→2→3 at 10-frame timing.
  - Spawns a child sprite at X+0xC0 for extended horizontal coverage.
  - Priority 0x100 (above normal water).
- `Obj_HCZWaterSplash` (~line 75286): Two subtypes:
  - Subtype 0: Simple 4-frame splash (8-frame delay), priority 0x300.
  - Subtype 1: Large splash with 2 child sub-sprites, 5-frame animation, plays `sfx_WaterSkid`.
- Java: implemented in `Sonic3kWaterSurfaceManager.java` (loads art, parses mappings, renders 2 sprites, animates).

**What to check for a new zone:**
1. Does the zone have a dedicated wave/splash object? What art tile base does it use?
2. How many mapping frames and what animation timing?
3. Does it spawn child sprites for horizontal coverage?
4. Is it purely cosmetic or does it interact with gameplay (e.g., splash SFX on player entry)?

#### 5b.4 Dynamic Water Level Tracking

Water zones track the current water level position, which affects both the scroll handler (waterline deformation split) and the water surface sprites.

**How to find:**
```bash
grep -n "DynamicWaterHeight_{ZONE}\|Water_level\|{ZONE}.*water" docs/skdisasm/sonic3k.asm
```

**HCZ reference:**
- `DynamicWaterHeight_HCZ1`: Threshold table defining water level at different camera X positions.
- Java: `ThresholdTableWaterHandler.java` mirrors this ROM pattern. `WaterSystem.getWaterLevelY()` provides the current level.
- The scroll handler receives water level indirectly through the BG Y / waterline offset (d2 in HCZ1).

**What to check for a new zone:**
1. Does the zone use `DynamicWaterHeight` or a fixed water level?
2. Does the water level change during gameplay (rising water, draining)?
3. How does the scroll handler learn about the water position — via camera Y offset (HCZ1 equilibrium system) or via direct `Water_level` query (AIZ pattern)?

#### 5b.5 Screen Shake + PlainDeformation Modes

Some zones switch between normal parallax and a flat `PlainDeformation` mode during gameplay sequences (wall chases, boss arenas, transitions). The scroll handler must support these alternate modes.

**HCZ2 reference:**
- `HCZ2_BackgroundEvent_Index` defines 5 states:
  - States 0-1: **Wall-chase mode** — `PlainDeformation` (flat BG, no per-line variation), screen shake via `ShakeScreen_Setup`, BG offset tracks wall movement via `Events_bg+$00`.
  - State 2: **Transition** — gradual vertical tile refresh from bottom-up.
  - States 3-4: **Normal mode** — full `HCZ2_Deform` + `ApplyDeformation`.
- `HCZ2_ScreenEvent` adds `Screen_shake_offset` to Camera_Y before tile drawing.
- `HCZ2_WallMove` advances wall position ($E000 speed, $14000 past end), triggers timed/constant screen shake, plays `sfx_Crash` / `sfx_Rumble2`.

**What to check for a new zone:**
1. Does `{ZONE}_BackgroundEvent_Index` have multiple states? List all of them.
2. Which states use `PlainDeformation` (flat) vs `ApplyDeformation` (parallax)?
3. Does the zone use screen shake? Is it constant or timed?
4. Is there a gradual tile-refresh transition between modes?
5. Does the scroll handler need a `setPhase()` / mode enum to support switching?

#### 5b.6 Water Zone Checklist

When implementing parallax for any zone with water, use this checklist to ensure nothing is missed:

| Item | Where to look | Scroll handler? | Companion system? |
|------|--------------|-----------------|-------------------|
| HScroll deformation (parallax bands) | `{ZONE}_Deform` | **Yes** — core handler | — |
| Waterline binary data (refraction) | `Levels/{ZONE}/Misc/*Waterline*` | **Yes** — optional ROM data | — |
| BG Y calculation + equilibrium | `{ZONE}_Deform` top | **Yes** — `vscrollFactorBG` | — |
| Per-line FG/BG deformation deltas | `{ZONE}.*DeformDelta` labels | **Yes** — `applyFgDeformation()` | — |
| Palette cycling | `AnPal_{ZONE}` | No | `Sonic3kLevelAnimationManager` |
| Animated water tiles | `AniPLC_{ZONE}` / `zoneanimdecl` | No | `AnimatedPatternManager` |
| Water surface sprites | `Obj_{ZONE}Wave*` / `Obj_{ZONE}Water*` | No | `Sonic3kWaterSurfaceManager` |
| Dynamic water level | `DynamicWaterHeight_{ZONE}` | Indirect (via camera Y) | `WaterSystem` / `ThresholdTableWaterHandler` |
| Screen shake | `{ZONE}_ScreenEvent`, `ShakeScreen_Setup` | **Yes** — `getShakeOffsetY()` | Level event handler |
| PlainDeformation mode | `{ZONE}_BackgroundEvent` states | **Yes** — mode/phase enum | Level event handler |
| Water transition palettes | `WaterTransition_{ZONE}*` | No | Palette system |

#### 5b.7 Renderer Boundary Sanity Check

For water zones, always validate the renderer split as part of the parallax task.

Symptoms to watch for:

- The above-water view appears to shift or garble while moving vertically.
- A black gap or seam appears just above or below the water surface.
- The visible water surface appears to bob independently, breaking alignment with the background layer.

These are not always scroll-handler bugs. They can come from:

- A mismatched water shader split in `LevelManager`
- Runtime background strip updates using the wrong waterline reference
- A decorative surface sprite/effect moving independently from the ROM's actual boundary

For any water zone, verify that the background art updates, the water surface effect, and the renderer boundary all agree on the same screen-space waterline.

### Phase 6: ROM Data Loading (If Needed)

Most S3K deform data is small enough to hardcode as Java constants (matching the disassembly values exactly). However, some zones reference external binary data.

#### 6.1 Waterline Scroll Data (HCZ, LBZ)

These binary files are too large to hardcode. Load them from ROM:

1. Find the ROM offset with RomOffsetFinder:
   ```bash
   mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" \
       -Dexec.args="--game s3k search HCZ_Waterline" -q
   ```

2. Add the offset to `Sonic3kConstants.java`:
   ```java
   public static final int HCZ_WATERLINE_SCROLL_DATA_ADDR = 0xXXXXX;
   ```

3. Load in the scroll handler provider's `load(Rom rom)`:
   ```java
   byte[] waterlineData = rom.readBytes(Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_ADDR, dataLength);
   hczHandler = new SwScrlHcz(waterlineData);
   ```

If the table is optional for tests or partial fallback behavior, wrap the read so the provider still returns the zone handler when the ROM cannot supply the data.

#### 6.2 Deform Index Tables

For scatter-fill zones, the deform index table can be hardcoded as a Java array. Read the binary data from the disassembly:

```bash
grep -A 30 "{ZONE}_BGDeformIndex" docs/skdisasm/sonic3k.asm
```

Convert `dc.b` entries to a Java int array, preserving the exact byte values.

### Phase 6b: Verify Background Art Renders Correctly

**This step is critical.** A working scroll handler is pointless if the background tiles show garbage instead of actual art. S3K zones are particularly prone to this because of the multi-stage pattern loading pipeline.

#### 6b.1 The "Hex Numbers" Symptom

If the background shows a grid of hex digits (like "E1A", "412", "0C0", "713") instead of actual cave/sky/terrain art, the BG **patterns** are not loaded into the PatternAtlas. The shader falls back to atlas position (0,0), which contains HUD font glyphs from PLC 0x01. The tiles scroll correctly (parallax works) but their content is wrong.

#### 6b.2 How S3K BG Art Loads

The pipeline is: ROM → KosM decompression → Pattern[] array → PatternAtlas (GPU) → shader lookup.

```
LEVEL_LOAD_BLOCK table (ROM offset 0x091F0C)
    ↓  24-byte entry per zone/act (llbIndex = zone*2 + act)
    ↓  Word 0 lower 24 bits = primary KosM art address
    ↓  Word 1 lower 24 bits = secondary KosM art address (overlay)
Sonic3k.loadLevel()
    ↓  Builds LevelResourcePlan with LoadOp entries
    ↓  Adds PLC overlay operations (character sprites, objects)
ResourceLoader.loadWithOverlays()
    ↓  Decompresses KosM data, applies overlays
Sonic3kLevel.loadPatternsWithPlan()
    ↓  patternCount = decompressedBytes / 32
    ↓  Caches each pattern to PatternAtlas via graphicsMan.cachePatternTexture()
LevelTilemapManager.ensurePatternLookupData()
    ↓  Builds GPU lookup table: pattern ID → atlas (tileX, tileY, atlasIndex)
    ↓  NULL entries → (0,0,0) → shader reads HUD font at atlas origin
```

#### 6b.3 Common Failure Modes

| Symptom | Cause | How to diagnose |
|---------|-------|----------------|
| All BG tiles show hex digits | Patterns not loaded or wrong ROM address | Check log for `patternCount` vs `maxChunkPatternIndex` |
| Some tiles correct, some garbled | Pattern overlay not applied or partial PLC | Check `secondaryArtAddr` and PLC operations |
| BG correct initially, then corrupts | Runtime PLC not updating patterns | Check `AnimatedPatternManager` / PLC system |
| Correct art but wrong colours | Palette not loaded for BG | Check palette line assignment in chunk descriptors |

#### 6b.4 Diagnostic Steps

1. **Check the warning log.** On level load, `Sonic3kLevel` logs:
   ```
   S3K chunks reference pattern X but patternCount is Y
   ```
   If X > Y, chunks reference unloaded patterns. This is the documented `maxChunkPatternIndex > patternCount` limitation (CLAUDE.md).

2. **Verify LEVEL_LOAD_BLOCK entry.** Use RomOffsetFinder or read the ROM directly:
   ```bash
   mvn exec:java -Dexec.mainClass="com.openggf.tools.disasm.RomOffsetFinder" \
       -Dexec.args="--game s3k search {ZONE}.*8x8" -q
   ```
   Compare the address from the search against what `Sonic3k.loadLevel()` reads from the LEVEL_LOAD_BLOCK table.

3. **Count patterns.** Add temporary logging in `Sonic3kLevel.loadPatternsWithPlan()`:
   ```java
   LOG.info("Zone patterns: loaded=" + patternCount + " bytes=" + result.length);
   ```
   For a typical S3K zone, expect 500-1500 patterns (16,000-48,000 bytes).

4. **Check PLC overlays.** PLC operations append additional patterns (character sprites, zone objects). If the primary pattern count is correct but PLCs fail, object sprites will be garbled while the BG could still render.

5. **Visual test.** Run the engine and navigate to the zone. If the BG is garbled but the FG (level geometry) renders correctly, the issue is specifically with BG pattern loading — the FG and BG share the same pattern pool but BG chunks may reference higher pattern indices that weren't loaded.

#### 6b.5 Known S3K Limitation

From CLAUDE.md: *"Some S3K levels log `maxChunkPatternIndex > patternCount` (dynamic art/PLC parity incomplete)."*

This means the engine doesn't yet replicate the ROM's full runtime PLC (Pattern Load Cue) system for all zones. On real hardware, the VDP loads art progressively during gameplay — initial patterns at level start, then additional art via PLCs during gameplay (boss art, zone transitions, animated tiles). The engine loads an initial batch at level start but may not apply all necessary PLC overlays, causing high pattern IDs in chunk data to reference empty VRAM slots.

**When implementing parallax for a new zone:** always run the zone visually and check for this issue. If the BG shows garbage, the fix is in the level loading pipeline (`Sonic3k.loadLevel()` / `Sonic3kLevelResourcePlans`), not in the scroll handler.

Also remember that "BG shows some correct art, some static art, and some garbage" usually means the issue is not the initial level-art load alone. That symptom more often indicates one of these:

- A missing runtime AniPLC hookup
- A missing direct background DMA/upload path
- A startup repair-strip load that never ran

Treat those as separate from the base level-art load path.

### Phase 7: Testing

#### 7.1 Unit Tests

Create `SwScrl{Zone}Test.java` in `src/test/java/com/openggf/game/sonic3k/scroll/`:

```java
@Test
void normalMode_producesExpectedParallaxBands() {
    SwScrl{Zone} handler = new SwScrl{Zone}();
    int[] buf = new int[224];
    handler.update(buf, 0x1000, 0x200, 10, 0);

    // Verify BG scroll values differ across bands
    short bg0 = M68KMath.unpackBG(buf[0]);
    short bg100 = M68KMath.unpackBG(buf[100]);
    assertNotEquals(bg0, bg100, "Different bands should have different BG scroll");

    // Verify BG Y
    assertEquals(expectedBgY, handler.getVscrollFactorBG());
}

@Test
void scrollTracking_boundsAreReasonable() {
    SwScrl{Zone} handler = new SwScrl{Zone}();
    int[] buf = new int[224];
    handler.update(buf, 0x800, 0x100, 0, 0);

    assertTrue(handler.getMinScrollOffset() <= handler.getMaxScrollOffset());
    assertTrue(handler.getMinScrollOffset() > -2000, "BG-FG offset too extreme");
}
```

See `SwScrlAizTest.java` for comprehensive test examples.

Add extra tests when the zone depends on more than deform math:

- A provider-routing test that confirms `Sonic3kScrollHandlerProvider` returns the dedicated handler and tolerates missing optional auxiliary ROM data.
- A runtime pattern/DMA regression that asserts the affected destination tiles change over time or match expected ROM-sourced art.
- For water zones, keep a short checklist of required in-engine seam/alignment checks even if those are not yet automated.

#### 7.2 Visual Validation

Run the engine and navigate to the zone:
```bash
java -jar target/sonic-engine-0.4.prerelease-jar-with-dependencies.jar
```

Check:
- Parallax bands are visible and move at different speeds
- No visible seam or jump at band boundaries
- Cloud/sky layers scroll smoothly (no jitter)
- BG Y scroll matches expected ratio
- If water present: deformation splits correctly at water surface
- If dynamic change: background transitions smoothly on trigger

**Water zone visual checks (if applicable):**
- Water surface sprite visible at the waterline (shimmering strip)
- Palette cycling animates water colours smoothly (no flicker or stall)
- Animated water tiles ripple at correct cadence
- Waterline refraction effect visible when camera crosses equilibrium (if binary data loaded)
- Crossing the water surface doesn't cause BG scroll jumps
- Screen shake (if wall-chase or boss) doesn't break the parallax split
- The above-water and below-water background sections remain aligned while moving vertically
- No black gap appears near the water surface
- The visible surface effect does not bob independently unless the ROM actually does that

### Phase 8: Cross-Validation

Delegate a review agent to compare the implementation against the disassembly:

```
Review the parallax implementation for {Zone} against the S3K disassembly.

Use the s3k-disasm-guide skill for disassembly navigation guidance.

Files to review:
- SwScrl{Zone}.java (scroll handler)
- Sonic3kScrollHandlerProvider.java (registration)

Disassembly reference:
- docs/skdisasm/sonic3k.asm (search for {ZONE}_Deform, line ~XXXXX)

Validation checklist:
1. All HScroll_table values match disassembly math (shift amounts, step sizes)
2. Deform height array matches disassembly exactly
3. BG Y calculation matches (shift amount, shake handling)
4. Per-line flag (0x8000) handled correctly if present
5. Auto-scroll accumulators have correct increment values
6. Scatter-fill index table matches if applicable
7. Boss scroll mode implemented if zone has boss events
8. Fine deformation deltas (haze/water) match if present
9. Screen shake integration correct if applicable
10. All magic numbers have disassembly-reference comments
11. Waterline binary data indexing correct (if water zone)
12. PlainDeformation/mode-switching states catalogued (if BackgroundEvent has multiple states)

Background art verification:
B1. BG renders actual zone art (not hex digits or garbage) — see Phase 6b
B2. No `maxChunkPatternIndex > patternCount` warning in logs
B3. Pattern count from LEVEL_LOAD_BLOCK matches expected zone art size

Companion effects audit (report status only — not part of scroll handler):
C1. Palette cycling: AnPal_{ZONE} routine found? Already implemented?
C2. Animated pattern tiles: AniPLC_{ZONE} entries found? How many scripts?
C3. Water surface sprites: Obj_{ZONE}Wave*/WaterSplash found? Already implemented?
C4. Dynamic water level: DynamicWaterHeight / Water_level mechanism identified?
C5. Screen shake / wall-chase modes: {ZONE}_ScreenEvent and BackgroundEvent states catalogued?
```

## ZoneScrollHandler Interface Reference

Methods that every scroll handler inherits or can override:

| Method | Required | Purpose |
|--------|----------|---------|
| `update(horizScrollBuf, cameraX, cameraY, frameCounter, actId)` | **Yes** | Fill 224-entry buffer with packed (FG\|BG) scroll values |
| `getVscrollFactorBG()` | Inherited | BG vertical scroll (set `vscrollFactorBG` in update) |
| `getMinScrollOffset()` / `getMaxScrollOffset()` | Inherited | BG-FG delta bounds for tile loading |
| `getVscrollFactorFG()` | Override | FG vertical offset (default 0; use for screen shake) |
| `getPerLineVScrollBG()` | Override | 224-entry per-line BG VScroll deltas (water, heat haze) |
| `getPerColumnVScrollBG()` | Override | 20-entry per-column BG VScroll deltas (fire wave) |
| `getShakeOffsetX()` / `getShakeOffsetY()` | Override | Screen shake pixel offsets |
| `getBgCameraX()` | Override | For wide BG maps (> 512px) that don't tile |
| `getBgPeriodWidth()` | Override | Required BG FBO width (default 512) |
| `init(actId, cameraX, cameraY)` | Override | One-time setup on level load |

## Existing Implementations (Reference)

Study these for working patterns:

| File | Zone | Complexity | Key Features |
|------|------|------------|--------------|
| `SwScrlAiz.java` | Angel Island | High | 3 modes (intro/main/act2), fire transition, per-column VScroll, wave accumulator, waterline split, fine deformation deltas, screen shake |
| `SwScrlHcz.java` | Hydrocity | High | 2 acts with entirely different routines, water equilibrium system (Y=$610), 192-line per-scanline section, waterline binary data (9312 bytes), mirrored cave bands, scatter-fill (act 2) |
| `SwScrlMgz.java` | Marble Garden | Medium | 2 acts with different deform arrays, cloud accumulator, scatter-fill offset table, act transition |
| `SwScrlS3kDefault.java` | Fallback | Low | Flat 1/4 speed parallax (placeholder for unimplemented zones) |

For S2 reference (similar architecture, simpler zones):

| File | Zone | Key Features |
|------|------|--------------|
| `SwScrlEhz.java` | Emerald Hill | Banded parallax with water ripple |
| `SwScrlCpz.java` | Chemical Plant | Dual BG cameras, seam ripple |
| `SwScrlOoz.java` | Oil Ocean | Bottom-up fill, heat haze |

## Key Source Files

| Purpose | Location |
|---------|----------|
| Scroll handler interface | `src/.../level/scroll/ZoneScrollHandler.java` |
| Abstract base class | `src/.../level/scroll/AbstractZoneScrollHandler.java` |
| 68k math utilities | `src/.../level/scroll/M68KMath.java` |
| S3K scroll provider | `src/.../game/sonic3k/scroll/Sonic3kScrollHandlerProvider.java` |
| S3K zone constants | `src/.../game/sonic3k/scroll/Sonic3kZoneConstants.java` |
| AIZ handler (reference) | `src/.../game/sonic3k/scroll/SwScrlAiz.java` |
| HCZ handler (reference) | `src/.../game/sonic3k/scroll/SwScrlHcz.java` |
| MGZ handler (reference) | `src/.../game/sonic3k/scroll/SwScrlMgz.java` |
| Default handler | `src/.../game/sonic3k/scroll/SwScrlS3kDefault.java` |
| AIZ test (reference) | `src/test/.../game/sonic3k/scroll/SwScrlAizTest.java` |
| HCZ test (reference) | `src/test/.../game/sonic3k/scroll/SwScrlHczTest.java` |
| AIZ events (dynamic BG) | `src/.../game/sonic3k/events/Sonic3kAIZEvents.java` |
| ParallaxManager | `src/.../level/ParallaxManager.java` |
| BG renderer | `src/.../level/render/BackgroundRenderer.java` |
| HScroll GPU buffer | `src/.../graphics/HScrollBuffer.java` |
| Water surface manager | `src/.../game/sonic3k/Sonic3kWaterSurfaceManager.java` |
| Water system | `src/.../level/WaterSystem.java` |
| Water level handler | `src/.../level/ThresholdTableWaterHandler.java` |
| S3K disassembly | `docs/skdisasm/sonic3k.asm` |
| S3K disassembly (s3 ROM) | `docs/skdisasm/s3.asm` |
| S3K constants (asm) | `docs/skdisasm/sonic3k.constants.asm` |
| Zone misc data | `docs/skdisasm/Levels/{ZONE}/Misc/` |

## S3K Zone Deform Summary

Quick reference for all 13 S3K zones:

| Zone | Abbr | Deform Label | Act-Split | Notable Features |
|------|------|-------------|-----------|------------------|
| Angel Island | AIZ | (implemented) | Yes (1/2) | 3 modes, fire transition, waterline, shake |
| Hydrocity | HCZ | (implemented) | Yes (1/2) | Water equilibrium, waterline binary data, scatter-fill, palette cycling, wave splash sprites |
| Marble Garden | MGZ | (implemented) | Yes (1/2) | Cloud accumulator, scatter-fill offsets |
| Carnival Night | CNZ | CNZ1_Deform | Shared | 5-band cascaded speed, boss scroll mode |
| Flying Battery | FBZ | FBZ_Deform | Shared | Indoor/outdoor switch, scatter-fill index |
| Ice Cap | ICZ | ICZ1_Deform | Act 1 only? | Half/quarter speed layers |
| Launch Base | LBZ | LBZ2_Deform | Act 2 | Waterline scroll data (binary) |
| Mushroom Hill | MHZ | MHZ_Deform | Shared | Mushroom parallax |
| Sandopolis | SOZ | (none found) | Unknown | May use simple parallax or be in Screen Events |
| Lava Reef | LRZ | LRZ1_Deform | Act 1 | 8 strips + 5 extra layers, complex |
| Sky Sanctuary | SSZ | (none found) | Unknown | Check Screen Events for cloud scrolling |
| Death Egg | DEZ | (none found) | Unknown | May be in Screen Events |
| Doomsday | DDZ | (none found) | N/A | Special stage, likely unique |

Zones without explicit `_Deform` labels may have their scroll logic in `{ZONE}_BackgroundEvent` or use the generic `ApplyDeformation` with just a height array and no custom HScroll_table fill. Search more broadly:

```bash
grep -n "{ZONE}" docs/skdisasm/sonic3k.asm | grep -i "scroll\|deform\|hscroll\|bg.*init\|bg.*event"
```
