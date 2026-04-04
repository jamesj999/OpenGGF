---
title: S1 Retro Trace
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

### Platform Setup

stable-retro requires a compiled C extension. Pre-built wheels are available for macOS and Linux but **not Windows** — use WSL on Windows.

#### macOS (native)

```bash
pip install stable-retro numpy
python -m stable_retro.import /path/to/directory/containing/rom/
```

#### Windows (via WSL)

stable-retro does not build natively on Windows. Use Ubuntu WSL:

```bash
# 1. Create a persistent Python venv in WSL
wsl -d Ubuntu-24.04 -- bash -c 'python3 -m venv ~/retro-env'

# 2. Install stable-retro
wsl -d Ubuntu-24.04 -- bash -c 'source ~/retro-env/bin/activate && pip install stable-retro numpy'

# 3. Copy the ROM to a WSL-local path (avoids slow /mnt/ I/O)
wsl -d Ubuntu-24.04 -- bash -c 'mkdir -p /tmp/roms && cp "/mnt/c/Users/farre/IdeaProjects/sonic-engine/Sonic The Hedgehog (W) (REV01) [!].gen" /tmp/roms/'

# 4. Import the ROM
wsl -d Ubuntu-24.04 -- bash -c 'cd /home && source ~/retro-env/bin/activate && PYTHONPATH="" python3 -m stable_retro.import /tmp/roms'
```

**Critical WSL notes:**
- Always `cd /home` (or any non-project dir) before activating the venv. The project root contains `stable-retro-0.9.9/stable_retro/` which shadows the installed package.
- Always set `PYTHONPATH=""` to prevent the Windows source tree from being picked up.
- Use `~/retro-env` (not `/tmp/retro-env`) — WSL `/tmp` is volatile.
- The recorder scripts need `render_mode=None` (already set) since WSL has no display server.

#### Running recorder scripts on Windows (WSL wrapper)

All `python` commands in this skill should be run through WSL on Windows:

```bash
wsl -d Ubuntu-24.04 -- bash -c 'cd /home && source ~/retro-env/bin/activate && PYTHONPATH="" python3 -u "/mnt/c/Users/farre/IdeaProjects/sonic-engine/tools/retro/SCRIPT.py" ARGS'
```

#### ROM SHA-1

The ROM's SHA-1 must match `69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b` (Sonic 1 REV01 World). Run `shasum` on your ROM if import fails.

### RAM byte ordering

stable-retro's genesis_plus_gx core exposes 68K work RAM with **bytes swapped within each 16-bit word** (little-endian x86 order). The `GenesisRAM` class in `trace_core.py` handles this transparently. If reading RAM directly, remember: byte at 68K address `$FFxxxx` (even) is at `ram[xxxx ^ 1]`.

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

Records the ROM's built-in ending demo replays (no external input needed).
Uses `redirect_level` mode: boots ROM -> presses Start -> redirects to credits.

**All 8 credits demos:**
```bash
python tools/retro/s1_credits_trace_recorder.py \
  --target all --force-mode redirect_level \
  --output-dir tools/retro/trace_output/credits_demos/
```

**Single demo (e.g. LZ3 = index 3):**
```bash
python tools/retro/s1_credits_trace_recorder.py \
  --target 3 --force-mode redirect_level \
  --output-dir tools/retro/trace_output/credits_demos/
```

**Windows WSL example (all 8):**
```bash
wsl -d Ubuntu-24.04 -- bash -c 'cd /home && source ~/retro-env/bin/activate && PYTHONPATH="" python3 -u "/mnt/c/Users/farre/IdeaProjects/sonic-engine/tools/retro/s1_credits_trace_recorder.py" --target all --force-mode redirect_level --output-dir /tmp/credits_traces'
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

After recording, copy the three output files to the correct test resource directory.

**Full-run traces (GHZ1/MZ1):**
```bash
SRC="tools/retro/trace_output"
DST="src/test/resources/traces/s1/ghz1_fullrun"  # or mz1_fullrun
cp "$SRC"/{physics.csv,aux_state.jsonl,metadata.json} "$DST/"
```

**Credits demos (from WSL output to Windows test resources):**
```bash
DEST="src/test/resources/traces/s1"
for pair in \
  "00_ghz1_credits_demo_1:credits_00_ghz1" \
  "01_mz2_credits_demo:credits_01_mz2" \
  "02_syz3_credits_demo:credits_02_syz3" \
  "03_lz3_credits_demo:credits_03_lz3" \
  "04_slz3_credits_demo:credits_04_slz3" \
  "05_sbz1_credits_demo:credits_05_sbz1" \
  "06_sbz2_credits_demo:credits_06_sbz2" \
  "07_ghz1_credits_demo_2:credits_07_ghz1b"; do
    src_dir="${pair%%:*}"; dest_dir="${pair##*:}"
    mkdir -p "$DEST/$dest_dir"
    wsl -d Ubuntu-24.04 -- bash -c "cp /tmp/credits_traces/$src_dir/{metadata.json,physics.csv,aux_state.jsonl} '/mnt/c/Users/farre/IdeaProjects/sonic-engine/$DEST/$dest_dir/'"
done
```

## Step 3: Run Trace Replay Tests

```bash
mvn test -Dtest="*Trace*"
```

Expected output:
- GHZ1 (`TestS1Ghz1TraceReplay`) should PASS
- MZ1 (`TestS1Mz1TraceReplay`) current baseline: **57 errors, 27 warnings**
- Credits demo 6 / SBZ2 (`TestS1Credits06Sbz2TraceReplay`) should PASS
- Other credits demos: various error counts (see Credits Demo Baseline below)

### Credits Demo Baseline

| Test | Zone | Errors | Warnings | First Error |
|------|------|--------|----------|-------------|
| Credits00 | GHZ1 | 5 | 7 | f526: y_speed |
| Credits01 | MZ2 | 28 | 131 | f51: x_speed |
| Credits02 | SYZ3 | 26 | 17 | f155: g_speed |
| Credits03 | LZ3 | 28 | 31 | f23: x_speed |
| Credits04 | SLZ3 | 54 | 48 | f286: angle |
| Credits05 | SBZ1 | 38 | 74 | f69: air |
| **Credits06** | **SBZ2** | **0** | **0** | **PASS** |
| Credits07 | GHZ1b | 1 | 7 | f516: y_speed |

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
| Credits test traces | `src/test/resources/traces/s1/credits_00_ghz1/` through `credits_07_ghz1b/` |
| Credits test base class | `src/test/java/com/openggf/tests/trace/AbstractCreditsDemoTraceReplayTest.java` |
| BizHawk trace skill | `s1-trace-replay` skill |

## BizHawk vs stable-retro

Both recorders produce byte-identical output format. The difference is the emulation platform:

| | BizHawk (Lua) | stable-retro (Python) |
|---|---|---|
| **Platform** | Windows only | macOS, Linux, Windows (via WSL) |
| **GUI** | Requires GLFW window | Fully headless |
| **Core** | Genesis Plus GX | Genesis Plus GX |
| **Input** | BK2 movie (native) | BK2 movie (native or BizHawk-parsed) |
| **RAM writes** | `mainmemory.write_*()` | `env.data.set_value()` via extended data.json |
| **RAM read order** | Big-endian (native 68K) | Little-endian (word-swapped, handled by GenesisRAM) |

Both use the same emulator core (Genesis Plus GX), so identical inputs should produce identical results. Traces from either platform can be used interchangeably with the Java test infrastructure.
