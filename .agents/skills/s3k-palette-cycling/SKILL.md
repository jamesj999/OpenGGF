---
name: s3k-palette-cycling
description: Use when implementing or validating S3K palette cycling (AnPal handlers) for a zone -- counter/step/limit cycling, palette line targets, ROM data verification in Sonic3kPaletteCycler.
---

# S3K Palette Cycling (AnPal Handlers)

Implement or validate per-zone palette cycling for Sonic 3 & Knuckles. This skill covers the `AnPal_*` routines from the disassembly: timer-based color table cycling driven by the counter/step/limit pattern, ROM data extraction, and integration into `Sonic3kPaletteCycler`.

## Inputs

$ARGUMENTS: Zone abbreviation (e.g., "AIZ", "HCZ", "CNZ", "LRZ1") + mode: `implement` (add new cycling for an unimplemented zone) or `validate` (audit existing implementation against the disassembly and fix discrepancies).

## Related Skills

- **s3k-disasm-guide** (`.agents/skills/s3k-disasm-guide/SKILL.md`) -- disassembly navigation, label conventions, RomOffsetFinder commands, S&K vs S3 address selection.
- **s3k-zone-analysis** -- zone-level analysis spec generation (the palette cycling section of a zone analysis spec feeds directly into this skill).

## Architecture

The palette cycling pipeline flows through four stages:

```
Sonic3kLevelAnimationManager (constructed per level load)
    |
    |  constructor: new Sonic3kPaletteCycler(reader, level, zoneIndex, actIndex)
    v
Sonic3kPaletteCycler.loadCycles(reader, zoneIndex, actIndex)
    |
    |  switch (zoneIndex) -> zone-specific loader method
    v
loadXxxCycles(reader, list)  -- reads ROM data via safeSlice(), builds PaletteCycle instances
    |
    |  adds one or more PaletteCycle subclass instances to the list
    v
List<PaletteCycle>  -- stored in Sonic3kPaletteCycler.cycles
    |
    |  called every frame from update() -> cycle.tick(level, graphicsManager)
    v
PaletteCycle.tick(level, gm)  -- counter/step/limit logic, writes colors, marks dirty flags
    |
    v
gm.cachePaletteTexture(palette, lineIndex)  -- uploads modified palette to GPU
```

**Key classes:**

| Class | File | Role |
|-------|------|------|
| `Sonic3kLevelAnimationManager` | `game/sonic3k/Sonic3kLevelAnimationManager.java` | Owns the `Sonic3kPaletteCycler`, delegates `update()` |
| `Sonic3kPaletteCycler` | `game/sonic3k/Sonic3kPaletteCycler.java` | Zone dispatch (`loadCycles` switch), ROM data loading, hosts all `PaletteCycle` inner classes |
| `PaletteCycle` | Inner abstract class in `Sonic3kPaletteCycler` | Base for all zone cycles; single method: `abstract void tick(Level level, GraphicsManager gm)` |
| `Sonic3kConstants` | `game/sonic3k/constants/Sonic3kConstants.java` | ROM addresses and sizes: `ANPAL_{ZONE}_{N}_ADDR`, `ANPAL_{ZONE}_{N}_SIZE` |
| `Palette` | `level/Palette.java` | 16-color palette line; `getColor(index).fromSegaFormat(byte[], offset)` writes a Mega Drive color word |
| `GraphicsManager` | `graphics/GraphicsManager.java` | `cachePaletteTexture(palette, lineIndex)` uploads palette to GPU; guarded by `isGlInitialized()` |

## The Counter/Step/Limit Pattern

Every AnPal channel in the ROM follows the same structure. Understanding this pattern is essential.

### Assembly (68000)

```asm
; AnPal_PalXXX -- typical channel
    subq.b  #1,(Palette_cycle_counter1).w     ; decrement timer
    bpl.s   return                              ; if timer >= 0, skip
    move.b  #7,(Palette_cycle_counter1).w      ; reset timer (period = 8 frames)

    move.w  (Palette_cycle_counter0).w,d0       ; load counter (byte offset into table)
    addq.w  #6,(Palette_cycle_counter0).w       ; advance by step (6 bytes = 3 colors)
    cmpi.w  #$30,(Palette_cycle_counter0).w     ; compare to limit (wrap point)
    blo.s   .nowrap
    move.w  #0,(Palette_cycle_counter0).w       ; wrap to 0
.nowrap:
    lea     (AnPal_PalXXX).l,a0                 ; ROM data table base
    move.l  (a0,d0.w),(Normal_palette_line_3+$18).w  ; write 2 colors (4 bytes)
    move.w  4(a0,d0.w),(Normal_palette_line_3+$1C).w ; write 1 more color (2 bytes)
```

### Java Equivalent

```java
// Inside a PaletteCycle.tick() method:
if (timer > 0) {
    timer--;
} else {
    timer = 7;                            // period - 1 (ROM uses subq.b + bpl)

    int d0 = counter;                     // snapshot current offset
    counter += 6;                         // step: 6 bytes = 3 colors x 2 bytes each
    if (counter >= 0x30) {                // limit: total table size in bytes
        counter = 0;
    }

    Palette pal2 = level.getPalette(2);   // palette line 3 = engine index 2
    pal2.getColor(12).fromSegaFormat(data, d0);      // +$18 = color 12
    pal2.getColor(13).fromSegaFormat(data, d0 + 2);  // +$1A = color 13
    pal2.getColor(14).fromSegaFormat(data, d0 + 4);  // +$1C = color 14
    dirty2 = true;
}
```

### Terminology Mapping

| ROM Term | Java Field | Meaning |
|----------|-----------|---------|
| `Palette_cycle_counter1` | `timer` | Frames remaining until next tick (decremented each frame) |
| `Palette_cycle_counter0` | `counter0` / `counter` | Byte offset into the ROM color table |
| `Palette_cycle_counters+$02` | `counter2` | Second channel byte offset (when zone has multiple channels) |
| `Palette_cycle_counters+$04` | `counter4` | Third channel byte offset |
| `Palette_cycle_counters+$08` | `timer2` / `timerC` | Independent timer for a second timer group |
| Timer reload value | `timer = N` | Period minus 1 (ROM: `move.b #N, (counter1)` then `subq.b #1` + `bpl`) |
| Step | `counter += step` | Bytes advanced per tick (2 per color written) |
| Limit | `if (counter >= limit)` | Total table size in bytes; counter wraps to 0 |
| `AnPal_PalXXX` label | `byte[] xxxData` | ROM color data table, loaded via `safeSlice()` |
| `Normal_palette_line_N+$XX` | `level.getPalette(N-1).getColor(XX/2)` | Palette destination (see address conversion below) |

### Special Counter Patterns

Some channels use a mask instead of a compare-and-wrap:

```asm
move.w  (Palette_cycle_counter0).w,d0
andi.w  #$18,d0          ; mask: cycles through 0, 8, 16, 24
```

Java equivalent:
```java
int d0 = counter & 0x18;
counter += 8;
// No explicit wrap needed -- the mask handles it
// But counter itself is typically byte-width: counter = (counter + 8) & 0xFF;
```

## Palette Cycling vs Palette Mutation -- CRITICAL DISTINCTION

These are **two separate systems** that both modify palette colors at runtime. They must NOT be conflated.

| Aspect | Palette Cycling (AnPal) | Palette Mutation (_Resize) |
|--------|------------------------|---------------------------|
| **Trigger** | Timer-based, every N frames | Camera-position threshold (one-shot) |
| **ROM routine** | `AnPal_{ZONE}` in `AnimatePalettes` dispatch | `{ZONE}{ACT}_Resize` in level event handler |
| **Engine location** | `Sonic3kPaletteCycler` (PaletteCycle inner classes) | Zone event handler (e.g., `Sonic3kAIZEvents`) |
| **Example** | AIZ waterfall shimmer, CNZ bumper glow | AIZ1 hollow tree darkening at X >= $2B00 |
| **Behavior** | Continuous cycling through a color table | Write specific fixed values once on threshold |

**Rule:** If the disassembly code is in `AnPal_*` (dispatched from `AnimatePalettes`), implement it in `Sonic3kPaletteCycler`. If it is in `{ZONE}_Resize` or `{ZONE}_ScreenEvent`, implement it in the zone event handler class. Do NOT put mutations in the cycler or cycling in the event handler.

Some zones have BOTH systems active simultaneously (e.g., AIZ has cycling for waterfall shimmer AND a mutation for hollow tree darkening). The HCZ `HczCycle` inner class is an exception that also handles `HCZ1_Resize` secondary cave-lighting behavior because it is tightly coupled to the water cycle state.

## Implementation Process

### Phase 1: Read the Analysis Spec

If a zone analysis spec exists (in `docs/superpowers/specs/`), read the palette cycling section first. It will list:
- Number of channels
- Timer periods
- Counter step/limit values
- Palette destinations
- ROM table labels and addresses
- Per-act differences

If no spec exists, proceed directly to Phase 2.

### Phase 2: Read the Disassembly

Find the zone AnPal routine in the disassembly:

```bash
grep -n "AnPal_{ZONE}\\|AnPal_Pal{ZONE}" docs/skdisasm/sonic3k.asm
```

For zones that may only exist in the S3 half:
```bash
grep -n "AnPal_{ZONE}\\|AnPal_Pal{ZONE}" docs/skdisasm/s3.asm
```

From the routine, extract for each channel:
1. **Timer reload value** -- the constant in `move.b #N, (Palette_cycle_counterX).w`
2. **Counter step** -- the constant in `addq.w #N` or `addi.w #N`
3. **Counter limit** -- the constant in `cmpi.w #$XX` (wrap point)
4. **Palette destination** -- the `Normal_palette_line_N+$XX` or `Water_palette_line_N+$XX` target
5. **ROM table label** -- the `lea (AnPal_PalXXX).l,a0` source
6. **Table size** -- limit value in bytes (equals step x frame_count)
7. **Colors per step** -- step / 2 (each Mega Drive color is 2 bytes)
8. **Gating conditions** -- any `tst.b` / `bne.s` checks before the channel runs

Also check whether channels share a timer or have independent timers.

### Phase 3: Find ROM Addresses

Use RomOffsetFinder to locate the palette data tables:

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.disasm.RomOffsetFinder" \
    "-Dexec.args=--game s3k search AnPal_Pal{ZONE}" -q
```

If the label is not found as an include, search for it as inline data by finding the label definition in the assembly and computing the offset manually, or by searching for the first few bytes of the data table:

```bash
mvn exec:java "-Dexec.mainClass=com.openggf.tools.disasm.RomOffsetFinder" \
    "-Dexec.args=--game s3k search-rom {first_hex_bytes}" -q
```

### Phase 4: Add ROM Constants

Add address and size constants to `Sonic3kConstants.java` following the naming convention:

```java
// {Zone} palette animation data -- AnPal_Pal{ZONE}_{N}
public static final int ANPAL_{ZONE}_{N}_ADDR = 0xXXXXXX;
public static final int ANPAL_{ZONE}_{N}_SIZE = NN;
```

**Naming examples from existing constants:**
- `ANPAL_AIZ1_1_ADDR` / `ANPAL_AIZ1_1_SIZE` -- AIZ Act 1, table 1
- `ANPAL_AIZ2_3_ADDR` / `ANPAL_AIZ2_3_SIZE` -- AIZ Act 2, table 3
- `ANPAL_HCZ1_ADDR` / `ANPAL_HCZ1_SIZE` -- HCZ (single table, act in name)
- `ANPAL_CNZ_1_ADDR` / `ANPAL_CNZ_1_SIZE` -- CNZ (shared across acts, numbered tables)
- `ANPAL_LRZ12_1_ADDR` / `ANPAL_LRZ12_1_SIZE` -- LRZ shared between acts 1 and 2
- `ANPAL_LRZ1_3_ADDR` / `ANPAL_LRZ1_3_SIZE` -- LRZ Act 1 only, table 3
- `ANPAL_CGZ_ADDR` / `ANPAL_CGZ_SIZE` -- CGZ (single table, no numbering needed)

Place them in the existing palette animation section of `Sonic3kConstants.java`, grouped by zone.

### Phase 5: Implement or Validate

#### 5a: New Implementation

**Step 1: Add the loader method** in `Sonic3kPaletteCycler`:

```java
private void loadZoneCycles(RomByteReader reader, List<PaletteCycle> list) {
    byte[] data1 = safeSlice(reader, Sonic3kConstants.ANPAL_ZONE_1_ADDR,
                                     Sonic3kConstants.ANPAL_ZONE_1_SIZE);
    // ... load additional tables as needed ...

    if (data1.length >= Sonic3kConstants.ANPAL_ZONE_1_SIZE
            /* && additional size checks */) {
        list.add(new ZoneCycle(data1 /*, data2, ... */));
    }
}
```

**Step 2: Add the switch case** in `loadCycles()`:

```java
case 0xNN: // {ZONE} -- AnPal_{ZONE}
    loadZoneCycles(reader, list);
    break;
```

Zone IDs for the switch: AIZ=0x00, HCZ=0x01, MGZ=0x02, CNZ=0x03, FBZ=0x04, ICZ=0x05, LBZ=0x06, MHZ=0x07, SOZ=0x08, LRZ=0x09, SSZ=0x0A, DEZ=0x0B, DDZ=0x0C, BPZ=0x0E, CGZ=0x10, EMZ=0x11.

If the zone has per-act differences, pass `actIndex` to the loader:
```java
case 0xNN:
    loadZoneCycles(reader, list, actIndex);
    break;
```

**Step 3: Create the PaletteCycle inner class.** Follow this template:

```java
// ========== {ZONE} Cycle ==========
// ROM: AnPal_{ZONE} (sonic3k.asm line XXXXX)
//
// Channel 1 (description): timer period N (reset to N-1)
//   AnPal_Pal{ZONE}_1 -> palette X colors A-B
//   counter0 step +S, wrap at 0xLL (F frames x S bytes)
//
// Channel 2 (description): ...
private static class ZoneCycle extends PaletteCycle {
    private final byte[] data1;  // AnPal_Pal{ZONE}_1: NN bytes (F frames x S bytes)
    // ... additional data arrays ...

    // Channel 1 state
    private int timer;     // Palette_cycle_counter1, period N
    private int counter0;  // step +S, wrap 0xLL

    // Channel 2 state (if applicable)
    // private int timer2;
    // private int counter2;

    // Dirty flags -- one per palette line modified
    private boolean dirty2;  // palette line 3 (engine index 2)
    private boolean dirty3;  // palette line 4 (engine index 3)

    ZoneCycle(byte[] data1 /*, byte[] data2, ... */) {
        this.data1 = data1;
        // ...
    }

    @Override
    void tick(Level level, GraphicsManager gm) {
        // Timer check
        if (timer > 0) {
            timer--;
        } else {
            timer = N - 1;  // ROM reload value

            // Channel 1: read counter, advance, wrap, write colors
            int d0 = counter0;
            counter0 += S;
            if (counter0 >= LIMIT) {
                counter0 = 0;
            }

            Palette palX = level.getPalette(X);  // X = palette line index (0-based)
            palX.getColor(A).fromSegaFormat(data1, d0);
            palX.getColor(A + 1).fromSegaFormat(data1, d0 + 2);
            // ... for each color in the step ...
            dirtyX = true;
        }

        // GPU upload (always at end of tick, guarded by isGlInitialized)
        if (gm.isGlInitialized()) {
            if (dirty2) {
                gm.cachePaletteTexture(level.getPalette(2), 2);
                dirty2 = false;
            }
            if (dirty3) {
                gm.cachePaletteTexture(level.getPalette(3), 3);
                dirty3 = false;
            }
        }
    }
}
```

**Key patterns from existing implementations:**

- **Single timer, single channel** (simplest): `LbzCycle`, `CgzCycle`
- **Single timer, multiple channels sharing it**: `Aiz1Cycle` (gameplay mode), `Lrz1Cycle` (channels A+B)
- **Multiple independent timers**: `CnzCycle` (3 timers), `IczCycle` (3 timers), `BpzCycle` (2 timers)
- **Camera-dependent table switching**: `Aiz2WaterCycle` (pre/post data at X >= 0x3800), `Aiz2TorchCycle`
- **Mode switching (flag-based)**: `Aiz1Cycle` (introFlag switches between intro/gameplay modes)
- **Counter with mask instead of compare**: `Aiz1Cycle.tickGameplay()` uses `counter0 & 0x18`
- **Runs every frame (no timer)**: `CnzCycle` channel 2 (background)
- **ROM bug reproduction**: `Lrz2Cycle` channel D (same offset written twice)

#### 5b: Validation

For an already-implemented zone:

1. Read the existing `PaletteCycle` inner class in `Sonic3kPaletteCycler.java`
2. Read the corresponding `AnPal_*` routine in the disassembly
3. Compare each channel against the disassembly, checking:
   - Timer reload value matches
   - Counter step matches
   - Counter limit (wrap point) matches
   - Palette destination (line + color index) matches
   - Number of colors written per step matches
   - ROM data table address and size match constants in `Sonic3kConstants.java`
   - Any gating conditions are correctly implemented
   - Channel independence (shared vs independent timers) matches
   - Camera-dependent table switching thresholds match
4. Fix any discrepancies found
5. Check for missing channels (water palette sync is a common TODO)

### Phase 6: Build and Verify

```bash
mvn package -q
```

If the build succeeds, run the engine and navigate to the zone to visually verify:
- Colors cycle at the expected rate
- Correct palette positions are modified (no wrong colors changing)
- Per-act differences work correctly
- No visual artifacts or static colors where cycling should occur

## Palette RAM Layout Reference

The Mega Drive VDP has 4 palette lines of 16 colors each (128 bytes total, 64 colors):

```
Line 1 (Normal_palette_line_1): colors 0-15   -> engine: level.getPalette(0)
Line 2 (Normal_palette_line_2): colors 0-15   -> engine: level.getPalette(1)
Line 3 (Normal_palette_line_3): colors 0-15   -> engine: level.getPalette(2)
Line 4 (Normal_palette_line_4): colors 0-15   -> engine: level.getPalette(3)
```

**Address conversion formula** (ROM offset to engine palette index + color):

```
Normal_palette_line_N+$XX
  -> palette line index = N - 1    (engine uses 0-based)
  -> color index = XX / 2          (each color is 2 bytes; $00=color 0, $02=color 1, etc.)
```

Examples:
- `Normal_palette_line_3+$16` = `level.getPalette(2).getColor(11)` -- line 3, offset 0x16 = color 11
- `Normal_palette_line_4+$02` = `level.getPalette(3).getColor(1)` -- line 4, offset 0x02 = color 1
- `Normal_palette_line_3+$1C` = `level.getPalette(2).getColor(14)` -- line 3, offset 0x1C = color 14

**Water palette lines** (`Water_palette_line_N`) are a separate set of 4 lines used when the player is underwater. Cycling routines often write to both Normal and Water lines simultaneously. The engine currently lacks Water palette support for most zones (marked as TODO in existing implementations).

## Already-Implemented Zones

| Zone | Inner Class(es) | Channels | Validation Notes |
|------|-----------------|----------|------------------|
| AIZ Act 1 | `Aiz1Cycle` | 4 (2 per mode) | introFlag one-shot logic, byte-width counter mask, level-started gate |
| AIZ Act 2 | `Aiz2WaterCycle`, `Aiz2TorchCycle` | 3 | Camera-X table switching at 0x3800, fixed color 0x0A0E override |
| HCZ | `HczCycle` | 1 + cave lighting | Water palette sync TODO; cave lighting is a mutation embedded in cycle class |
| CNZ | `CnzCycle` | 3 | Channel 2 runs every frame (no timer); water palette tables (CNZ_2, CNZ_4) loaded but unused |
| ICZ | `IczCycle` | 4 | Channels 2+3 gated by Events_bg+$16 flag (currently always enabled, TODO) |
| LBZ | `LbzCycle` | 1 | Per-act table selection (LBZ1 vs LBZ2 addr); simplest implementation |
| LRZ Act 1 | `Lrz1Cycle` | 3 (A+B shared, C independent) | Channels A+B share timer (period 16), channel C has own timer (period 8) |
| LRZ Act 2 | `Lrz2Cycle` | 3 (A+B shared, D independent) | ROM bug: channel D writes same 2 colors twice (d0, not d0+4) |
| BPZ | `BpzCycle` | 2 | Competition zone; balloon + background channels |
| CGZ | `CgzCycle` | 1 | Competition zone; simplest multi-color cycle |
| EMZ | `EmzCycle` | 2 | Competition zone; glow channel uses 4-byte offset (`4 + d0`) from table base |

**FBZ (zone 0x04)** is intentionally a no-op -- the S3K version toggles a flicker bit, not a color table animation. The S3 version is `rts`.

## Common Mistakes

1. **Wrong palette line index.** The ROM uses 1-based line numbering (`Normal_palette_line_3` = third palette line), but the engine uses 0-based (`getPalette(2)`). Off-by-one here causes colors to cycle in the wrong palette line entirely.

2. **Wrong color index from byte offset.** ROM offsets are in bytes (2 bytes per color). `+$16` means color index 11, not 22. Always divide the hex offset by 2.

3. **Wrong cycling rate.** The ROM timer pattern is `subq.b #1` then `bpl.s skip`. This means the timer counts down from N to 0, then fires -- giving a period of N+1 frames. In Java, set `timer = N` (the reload value from the ROM) and check `if (timer > 0) { timer--; } else { timer = N; /* do work */ }`.

4. **Missing per-act split.** Some zones use different routines or data tables per act (AIZ, LBZ, LRZ). Check the disassembly for `tst.w (Current_act).w` / `bne` branching. The `loadCycles()` switch should pass `actIndex` to the loader when acts differ.

5. **Confusing color count with byte count.** Step of 6 means 3 colors (6 bytes / 2 bytes per color), not 6 colors. Limit of 0x30 means 48 bytes = 8 frames of 6-byte steps, not 48 colors.

6. **Implementing palette mutations in the cycler.** Camera-threshold writes from `_Resize` routines belong in the zone event handler (e.g., `Sonic3kAIZEvents`), not in `Sonic3kPaletteCycler`. The cycler is exclusively for `AnPal_*` timer-based cycling. The one exception is `HczCycle`, which embeds cave-lighting mutations because they share state with the water cycle.

7. **Forgetting the GPU upload.** Every `tick()` must end with the `if (gm.isGlInitialized()) { if (dirtyN) { gm.cachePaletteTexture(...); dirtyN = false; } }` block. Without it, palette changes are computed but never rendered.

8. **Using `Camera.getInstance()` instead of `GameServices.camera()`.** The main repo implementations use `GameServices.camera()` for camera access. Be consistent with the existing pattern.

9. **Not handling byte-width counter overflow.** Some ROM counters are byte-width (`move.b` / `add.b`), meaning they wrap at 256 implicitly. When the counter is masked (e.g., `& 0x18`), the mask handles cycling, but the underlying counter should still wrap: `counter = (counter + step) & 0xFF`.

10. **Ignoring ROM bugs.** Some ROM routines have bugs (e.g., LRZ2 channel D writing the same offset twice). The engine must reproduce these bugs exactly for accuracy. Always note ROM bugs in comments.

## Key Source Files

| Purpose | Location |
|---------|----------|
| Palette cycler (all zones) | `src/.../game/sonic3k/Sonic3kPaletteCycler.java` |
| Animation manager (owner) | `src/.../game/sonic3k/Sonic3kLevelAnimationManager.java` |
| ROM constants | `src/.../game/sonic3k/constants/Sonic3kConstants.java` |
| Palette class | `src/.../level/Palette.java` |
| Graphics manager | `src/.../graphics/GraphicsManager.java` |
| AIZ events (mutation example) | `src/.../game/sonic3k/events/Sonic3kAIZEvents.java` |
| S3K disassembly | `docs/skdisasm/sonic3k.asm` (S&K half) |
| S3 disassembly | `docs/skdisasm/s3.asm` (S3 half, some zones) |
| AGENTS_S3K reference | `AGENTS_S3K.md` (palette animation section) |
