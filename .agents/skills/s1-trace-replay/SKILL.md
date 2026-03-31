---
title: S1 Trace Replay
description: Workflow for recording Sonic 1 BizHawk traces, replaying them in tests, and interpreting divergence reports.
---

# S1 Trace Replay

Record a Sonic 1 BizHawk trace, copy it to the test resources, run the trace replay tests, and interpret the divergence results.

## Inputs

$ARGUMENTS: Optional zone name or action. Examples:
- `mz1` — record and test MZ1 only
- `ghz1` — record and test GHZ1 only
- `all` or empty — record and test both GHZ1 and MZ1
- `test-only` — skip recording, just run the trace tests on existing data
- `interpret` — skip recording/testing, just interpret the latest report

## Prerequisites

- BizHawk 2.11 at: `docs/BizHawk-2.11-win-x64/EmuHawk.exe`
- Sonic 1 REV01 ROM at project root: `Sonic The Hedgehog (W) (REV01) [!].gen`
- BK2 movie files at: `docs/BizHawk-2.11-win-x64/Movies/`
  - GHZ1: `Sonic The Hedgehog (W) (REV01) [!].bk2`
  - MZ1: `s1-mz1.bk2`
- Lua script: `tools/bizhawk/s1_trace_recorder.lua` (v2.2+)

## Step 1: Record Trace with BizHawk

BizHawk must be launched from its own directory. The Lua script writes to `tools/bizhawk/trace_output/` (relative to the script's location).

### Recording commands

**MZ1:**
```bash
cd "C:/Users/farre/IdeaProjects/sonic-engine/docs/BizHawk-2.11-win-x64" && \
./EmuHawk.exe --chromeless \
  --lua "../../tools/bizhawk/s1_trace_recorder.lua" \
  --movie "Movies/s1-mz1.bk2" \
  "../../Sonic The Hedgehog (W) (REV01) [!].gen" 2>&1 | tail -3
```

**GHZ1:**
```bash
cd "C:/Users/farre/IdeaProjects/sonic-engine/docs/BizHawk-2.11-win-x64" && \
./EmuHawk.exe --chromeless \
  --lua "../../tools/bizhawk/s1_trace_recorder.lua" \
  --movie "Movies/Sonic The Hedgehog (W) (REV01) [!].bk2" \
  "../../Sonic The Hedgehog (W) (REV01) [!].gen" 2>&1 | tail -3
```

### Verify recording succeeded

After each recording, check that the output was written with today's date:

```bash
cat "C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output/metadata.json"
```

Verify:
- `recording_date` matches today
- `zone` matches expected zone (`ghz` or `mz`)
- `csv_version` is 4 (v2.2 format)
- `trace_frame_count` is reasonable (GHZ1 ~3905, MZ1 ~7936)

### Important notes

- BizHawk does NOT print Lua output to stdout in chromeless mode. Verify success by checking file timestamps and metadata, not console output.
- The trace output directory is `tools/bizhawk/trace_output/` (NOT `docs/BizHawk-2.11-win-x64/Lua/trace_output/`). BizHawk sets the Lua working directory to the script's parent folder.
- Each recording OVERWRITES the previous trace_output. Record and copy one zone at a time.

## Step 2: Copy Trace to Test Resources

After each recording, copy the three output files to the correct test resource directory:

**MZ1:**
```bash
SRC="C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output"
DST="C:/Users/farre/IdeaProjects/sonic-engine/src/test/resources/traces/s1/mz1_fullrun"
cp "$SRC/physics.csv" "$DST/physics.csv" && \
cp "$SRC/aux_state.jsonl" "$DST/aux_state.jsonl" && \
cp "$SRC/metadata.json" "$DST/metadata.json"
```

**GHZ1:**
```bash
SRC="C:/Users/farre/IdeaProjects/sonic-engine/tools/bizhawk/trace_output"
DST="C:/Users/farre/IdeaProjects/sonic-engine/src/test/resources/traces/s1/ghz1_fullrun"
cp "$SRC/physics.csv" "$DST/physics.csv" && \
cp "$SRC/aux_state.jsonl" "$DST/aux_state.jsonl" && \
cp "$SRC/metadata.json" "$DST/metadata.json"
```

## Step 3: Run Trace Replay Tests

```bash
cd "C:/Users/farre/IdeaProjects/sonic-engine" && mvn test -Dtest="*Trace*"
```

Expected output pattern:
- `MSE:TESTS total=15 passed=N failed=M errors=0 skipped=0`
- GHZ1 (`TestS1Ghz1TraceReplay`) should PASS
- MZ1 (`TestS1Mz1TraceReplay`) current baseline: **224 errors, 329 warnings**

### Test class mapping

| Test class | Zone | Trace directory |
|---|---|---|
| `TestS1Ghz1TraceReplay` | GHZ Act 1 | `src/test/resources/traces/s1/ghz1_fullrun/` |
| `TestS1Mz1TraceReplay` | MZ Act 1 | `src/test/resources/traces/s1/mz1_fullrun/` |

## Step 4: Interpret Results

### Divergence report location

Reports are written to `target/trace-reports/`:
- `s1_ghz1_report.json` — GHZ1 divergence report
- `s1_mz1_report.json` — MZ1 divergence report
- `s1_mz1_context.txt` — context window around first MZ1 error (only if errors exist)

### Reading the report JSON

```bash
cat target/trace-reports/s1_mz1_report.json | python -m json.tool | head -30
```

Key fields in each divergence group:
- `field` — which physics field diverged (x, y, x_speed, y_speed, g_speed, angle, air, rolling, ground_mode)
- `severity` — `ERROR` or `WARNING` (warnings are within tolerance)
- `start_frame` / `end_frame` — frame range of the divergence
- `cascading` — true if this divergence follows an earlier error (cascade effect)

### Interpreting with auxiliary trace data

The `aux_state.jsonl` file contains rich event data for debugging divergences. Key event types:

**`slot_dump`** — Full snapshot of all occupied SST slots when any object appears:
```bash
grep "slot_dump" src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl | head -3
```
Use this to compare ROM slot allocation vs engine allocation at specific frames.

**`routine_change`** — Player routine transitions with full Sonic state:
```bash
grep "routine_change" src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl
```
S1 routines: 0=init, 2=control, **4=hurt**, 6=death, 8=reset. NOT the same as S2.

**`object_appeared` / `object_removed`** — Object lifecycle with slot number:
```bash
grep '"slot":75' src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl | grep "appeared\|removed"
```

**`object_near`** — Per-frame proximity log of objects within 160px of Sonic:
```bash
grep '"frame":3193' src/test/resources/traces/s1/mz1_fullrun/aux_state.jsonl | grep "object_near"
```

### Common divergence patterns

1. **y_speed sign flip** (e.g., expected=-04C0, actual=+04C0): Enemy bounce missed. The ROM bounced off a badnik but the engine didn't. Check `slot_dump` and `object_near` for the badnik's slot — slot differences cause timing gate differences in objects like Batbrain.

2. **Position drift after hurt**: Ring loss events (object type 0x37 = `RingLoss` scattered rings) confirm damage. Check `routine_change` events for the 2→4 transition and what object Sonic was standing on.

3. **Cascading errors**: Once one divergence occurs, subsequent errors often cascade. Focus on the FIRST error — fixing it may resolve many downstream issues.

4. **Warning-only 1px Y differences**: Often terrain collision rounding. Usually not actionable unless they precede errors.

### CSV column reference (v2.2, csv_version=4)

| Column | Index | Description |
|---|---|---|
| frame | 0 | Trace frame number (hex) |
| input | 1 | Joypad bitmask |
| x | 2 | Sonic centre X |
| y | 3 | Sonic centre Y |
| x_speed | 4 | X velocity (signed) |
| y_speed | 5 | Y velocity (signed) |
| g_speed | 6 | Ground speed (signed) |
| angle | 7 | Terrain angle |
| air | 8 | Airborne flag (0/1) |
| rolling | 9 | Rolling flag (0/1) |
| ground_mode | 10 | Ground mode (0-3) |
| x_sub | 11 | X subpixel |
| y_sub | 12 | Y subpixel |
| routine | 13 | obRoutine raw byte |
| camera_x | 14 | Camera X pixel |
| camera_y | 15 | Camera Y pixel |
| rings | 16 | Ring count (binary word) |
| status_byte | 17 | Status flags byte |
| v_framecount | 18 | ROM frame counter (currently reads 0 — address needs verification) |
| stand_on_obj | 19 | SST slot Sonic is standing on (0=none) |

### S1 object ID quick reference (common in MZ)

| ID | Name |
|---|---|
| 0x25 | Ring |
| 0x2F | MZ Large Grassy Platform |
| 0x30 | MZ Glass Block |
| 0x33 | Push Block |
| 0x36 | Spikes |
| 0x37 | RingLoss (scattered rings after damage) |
| 0x46 | MZ Brick |
| 0x54 | Lava Tag |
| 0x55 | Batbrain |
| 0x78 | Caterkiller |
| 0x79 | Lamppost |

## Workflow Summary

For a full re-record and test cycle:

1. Record zone (BizHawk headless, ~10-30 seconds)
2. Verify metadata.json has correct date/zone/csv_version
3. Copy 3 files to test resources
4. Repeat for second zone if doing both
5. Run `mvn test -Dtest="*Trace*"`
6. Compare error count against baseline (GHZ1=0, MZ1=224)
7. If errors changed: read report JSON and cross-reference aux events at first error frame
