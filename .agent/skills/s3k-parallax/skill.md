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

#### 6.2 Deform Index Tables

For scatter-fill zones, the deform index table can be hardcoded as a Java array. Read the binary data from the disassembly:

```bash
grep -A 30 "{ZONE}_BGDeformIndex" docs/skdisasm/sonic3k.asm
```

Convert `dc.b` entries to a Java int array, preserving the exact byte values.

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
| MGZ handler (reference) | `src/.../game/sonic3k/scroll/SwScrlMgz.java` |
| Default handler | `src/.../game/sonic3k/scroll/SwScrlS3kDefault.java` |
| AIZ test (reference) | `src/test/.../game/sonic3k/scroll/SwScrlAizTest.java` |
| AIZ events (dynamic BG) | `src/.../game/sonic3k/events/Sonic3kAIZEvents.java` |
| ParallaxManager | `src/.../level/ParallaxManager.java` |
| BG renderer | `src/.../level/render/BackgroundRenderer.java` |
| HScroll GPU buffer | `src/.../graphics/HScrollBuffer.java` |
| S3K disassembly | `docs/skdisasm/sonic3k.asm` |
| S3K constants (asm) | `docs/skdisasm/sonic3k.constants.asm` |
| Zone misc data | `docs/skdisasm/Levels/{ZONE}/Misc/` |

## S3K Zone Deform Summary

Quick reference for all 13 S3K zones:

| Zone | Abbr | Deform Label | Act-Split | Notable Features |
|------|------|-------------|-----------|------------------|
| Angel Island | AIZ | (implemented) | Yes (1/2) | 3 modes, fire transition, waterline, shake |
| Hydrocity | HCZ | HCZ1_Deform, HCZ2_Deform | Yes | Waterline scroll data (binary), scatter-fill |
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
