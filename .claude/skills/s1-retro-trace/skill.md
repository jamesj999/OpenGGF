---
name: s1-retro-trace
description: Use when recording Sonic 1 physics traces using stable-retro for trace replay tests.
---

# S1 Retro Trace

Record a Sonic 1 physics trace using stable-retro (cross-platform, headless Genesis emulation via Python). Produces identical output to the BizHawk Lua trace recorder: physics.csv, aux_state.jsonl, and metadata.json in the same format consumed by the Java trace replay tests.

## Inputs

$ARGUMENTS: Zone name, action, or recording mode. Examples:
- `ghz1` — record GHZ1 trace (requires movie or savestate)
- `mz1` — record MZ1 trace
- `test-only` — skip recording, just run the trace tests on existing data
- `credits` — record credits demos (ROM-internal input, no movie needed)
- `credits 3` — record a single credits demo (LZ3)

## Prerequisites

- Python 3.8+ with `stable-retro` and `numpy` installed
- Sonic 1 REV01 ROM imported into stable-retro
- For full-run traces: a BK2 movie file (stable-retro or BizHawk format)
- For credits demos: no movie needed (ROM provides input data)

### One-time setup

```bash
pip install stable-retro numpy
python -m stable_retro.import /path/to/directory/containing/rom/
```

The ROM's SHA-1 must match `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` (Sonic 1 REV01 World). Run `shasum` on your ROM if import fails.

## Step 1: Record Trace with stable-retro

Scripts are in `tools/retro/`. Run from the project root.

### Full-run recording (with movie replay)

**Replay a stable-retro BK2 movie:**
```bash
python tools/retro/s1_trace_recorder.py \
  --movie path/to/my_recording.bk2 \
  --output-dir tools/retro/trace_output/
```

**Replay a BizHawk BK2 movie (auto-parsed):**
```bash
python tools/retro/s1_trace_recorder.py \
  --bizhawk-bk2 path/to/bizhawk_movie.bk2 \
  --output-dir tools/retro/trace_output/
```

**Boot from a savestate (no movie — for testing or interactive recording):**
```bash
python tools/retro/s1_trace_recorder.py \
  --state GreenHillZone.Act1 \
  --output-dir tools/retro/trace_output/
```

### Credits demo recording

Records the ROM's built-in ending demo replays (no external input needed):

**All 8 credits demos:**
```bash
python tools/retro/s1_credits_trace_recorder.py \
  --output-dir tools/retro/trace_output/credits_demos/
```

**Single demo (e.g. LZ3 = index 3):**
```bash
python tools/retro/s1_credits_trace_recorder.py \
  --target 3 \
  --output-dir tools/retro/trace_output/credits_demos/
```

### Verify recording succeeded

Check `metadata.json` in the output directory:
```bash
cat tools/retro/trace_output/metadata.json
```

Verify:
- `recording_date` matches today
- `zone` matches expected zone
- `csv_version` is 4
- `trace_frame_count` is reasonable (GHZ1 ~3905, MZ1 ~7936)

## Step 2: Copy Trace to Test Resources

After recording, copy the three output files to the correct test resource directory:

**GHZ1:**
```bash
SRC="tools/retro/trace_output"
DST="src/test/resources/traces/s1/ghz1_fullrun"
cp "$SRC/physics.csv" "$DST/physics.csv"
cp "$SRC/aux_state.jsonl" "$DST/aux_state.jsonl"
cp "$SRC/metadata.json" "$DST/metadata.json"
```

**MZ1:**
```bash
SRC="tools/retro/trace_output"
DST="src/test/resources/traces/s1/mz1_fullrun"
cp "$SRC/physics.csv" "$DST/physics.csv"
cp "$SRC/aux_state.jsonl" "$DST/aux_state.jsonl"
cp "$SRC/metadata.json" "$DST/metadata.json"
```

## Step 3: Run Trace Replay Tests

```bash
mvn test -Dtest="*Trace*"
```

Expected output:
- GHZ1 (`TestS1Ghz1TraceReplay`) should PASS
- MZ1 (`TestS1Mz1TraceReplay`) current baseline: **224 errors, 329 warnings**

## Step 4: Interpret Results

See the `s1-trace-replay` skill for full divergence report interpretation, aux_state.jsonl cross-referencing, and common divergence patterns. The output format is identical between BizHawk and stable-retro recorders.

## Available Savestates

stable-retro ships with savestates for every Sonic 1 zone/act:

| State name | Zone |
|------------|------|
| `GreenHillZone.Act1/2/3` | GHZ |
| `MarbleZone.Act1/2/3` | MZ |
| `SpringYardZone.Act1/2/3` | SYZ |
| `LabyrinthZone.Act1/2/3` | LZ |
| `StarLightZone.Act1/2/3` | SLZ |
| `ScrapBrainZone.Act1/2` | SBZ |

## Credits Demo Index

| Index | Slug | Zone/Act |
|-------|------|----------|
| 0 | `ghz1_credits_demo_1` | GHZ1 |
| 1 | `mz2_credits_demo` | MZ2 |
| 2 | `syz3_credits_demo` | SYZ3 |
| 3 | `lz3_credits_demo` | LZ3 |
| 4 | `slz3_credits_demo` | SLZ3 |
| 5 | `sbz1_credits_demo` | SBZ1 |
| 6 | `sbz2_credits_demo` | SBZ2 |
| 7 | `ghz1_credits_demo_2` | GHZ1 |

## Reference Files

| Purpose | Location |
|---------|----------|
| Main trace recorder | `tools/retro/s1_trace_recorder.py` |
| Credits trace recorder | `tools/retro/s1_credits_trace_recorder.py` |
| Shared tracing engine | `tools/retro/trace_core.py` |
| Dependencies | `tools/retro/requirements.txt` |
| BizHawk equivalent (Lua) | `tools/bizhawk/s1_trace_recorder.lua` |
| BizHawk credits (Lua) | `tools/bizhawk/s1_credits_trace_recorder.lua` |
| GHZ1 test traces | `src/test/resources/traces/s1/ghz1_fullrun/` |
| MZ1 test traces | `src/test/resources/traces/s1/mz1_fullrun/` |
| BizHawk trace skill | `s1-trace-replay` skill |

## BizHawk vs stable-retro

Both recorders produce byte-identical output format. The difference is the emulation platform:

| | BizHawk (Lua) | stable-retro (Python) |
|---|---|---|
| **Platform** | Windows only | Windows, macOS, Linux |
| **GUI** | Requires GLFW window | Fully headless |
| **Core** | Genesis Plus GX | Genesis Plus GX |
| **Input** | BK2 movie (native) | BK2 movie (native or BizHawk-parsed) |
| **RAM writes** | `mainmemory.write_*()` | `env.data.set_value()` via extended data.json |

Both use the same emulator core (Genesis Plus GX), so identical inputs should produce identical results. Traces from either platform can be used interchangeably with the Java test infrastructure.
