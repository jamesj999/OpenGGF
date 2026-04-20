# Sonic 2 and Sonic 3&K Trace Recorder v3 Migration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Sonic 2 and Sonic 3&K onto the schema v3 trace-replay contract already landed for Sonic 1, so that future S2 and S3K BizHawk captures emit `gameplay_frame_counter`, `vblank_counter`, and `lag_counter` columns driven by real ROM counters rather than the legacy heuristic.

**Architecture:** The Java-side v3 schema, backward-compatible parser, and execution-model phase derivation already shipped under `docs/superpowers/plans/2026-04-19-trace-lag-model.md` (Tasks 2–5). This plan covers the deferred Tasks 6a (S2 recorder) and 6b (S3K recorder) only. It adds two new BizHawk Lua recorders, updates the launcher batch / docs, and closes out the `Trace Replay Recorder Coverage` known-discrepancy entry once both recorders can emit a round-trip-valid v3 trace.

**Tech Stack:** BizHawk 2.11 EmuHawk Lua scripting, existing Java 21 / JUnit 5 trace-replay harness (`TraceData`, `TraceFrame`, `TraceMetadata`, `TraceExecutionModel`), Sonic 2 and Sonic 3&K disassemblies under `docs/s2disasm/` and `docs/skdisasm/`.

**Reference:**
- `tools/bizhawk/s1_trace_recorder.lua` — canonical v3 recorder (Sonic 1)
- `docs/superpowers/research/2026-04-19-trace-lag-model-matrix.md` — cross-game execution matrix and counter addresses (S1/S2/S3K already resolved)
- `docs/superpowers/plans/2026-04-19-trace-lag-model.md` — parent plan; Tasks 6a/6b stubs
- `docs/guide/contributing/trace-replay.md` — contributor-facing documentation
- `docs/KNOWN_DISCREPANCIES.md` entry #10 — `Trace Replay Recorder Coverage`

**Out of scope:**
- Do not touch the Java-side parser, metadata, or execution model — v3 is already accepted for all games.
- Do not regenerate existing Sonic 1 trace fixtures.
- Do not add a synthetic S2/S3K lag counter where the ROM does not expose one. For S2, write `lag_counter = 0` as a diagnostic placeholder; do not derive one from `Vint_runcount - Level_frame_counter`.
- Do not modify replay tests to force-run against new fixtures until the recorders are validated end-to-end.
- Do not remove the legacy heuristic fallback in `TraceData` until a real v3 fixture from S2 and one from S3K have parsed and replayed successfully.

---

## Pre-Resolved Counter Addresses

The counter matrix is already frozen in `docs/superpowers/research/2026-04-19-trace-lag-model-matrix.md`. Recorder implementations must use these exact addresses and no others:

| Game | `gameplay_frame_counter` | `vblank_counter` | `lag_counter` |
|---|---:|---:|---|
| S1 | `0xFE04` (`v_framecount`) | `0xFE0E` (`v_vbla_word`) | n/a — emit `0` |
| S2 | `0xFE04` (`Level_frame_counter`) | `0xFE0C` (`Vint_runcount+2`) | n/a — emit `0` |
| S3K | `0xFE08` (`Level_frame_counter`) | `0xFE12` (`V_int_run_count+2`) | `0xF628` (`Lag_frame_count`) |

Any task that reports a different address for any of the values above must stop and update the research matrix first. Counter addresses are the part of this plan with the tightest ROM coupling, and the matrix is the single source of truth.

## Scope and Constraints

- Keep the v3 CSV schema byte-for-byte identical across S1/S2/S3K. The Java parser does not branch by game for the physics columns; any divergence must be fixed in the recorder, not the parser.
- Reuse the S1 recorder's output-file layout (`physics.csv`, `aux_state.jsonl`, `metadata.json`) so `TraceData.load(Path)` needs no per-game switching.
- Do not rewrite the S1 recorder as part of this work. If a shared helper is genuinely needed, the first step is to factor it out of S1 and add tests — but the default here is to duplicate rather than prematurely abstract three Lua files.
- Each recorder must start recording only once the game is in gameplay mode with controls unlocked, matching S1 behavior. This avoids "dead frame" leading rows where BK2 input is present but physics has not run yet.
- Each recorder must honor `--chromeless --lua` headless execution with auto-exit on movie end.

## File Map

| Path | Action | Responsibility |
|---|---|---|
| `tools/bizhawk/s2_trace_recorder.lua` | Create | Sonic 2 v3 recorder. |
| `tools/bizhawk/s3k_trace_recorder.lua` | Create | Sonic 3&K v3 recorder. |
| `tools/bizhawk/record_s2_trace.bat` | Create | Launch helper for S2 recorder (mirror of `record_trace.bat`). |
| `tools/bizhawk/record_s3k_trace.bat` | Create | Launch helper for S3K recorder. |
| `tools/bizhawk/README.md` | Modify | Add S2 and S3K recorder usage, keep canonical trace-replay guide pointer. |
| `docs/guide/contributing/trace-replay.md` | Modify | Document that S2 and S3K now emit v3 traces; update the recorder walkthrough. |
| `docs/KNOWN_DISCREPANCIES.md` | Modify | Close out entry #10 `Trace Replay Recorder Coverage` once both recorders land. |
| `docs/superpowers/research/2026-04-20-s2-trace-addresses.md` | Create | Per-game S2 RAM addresses used by the recorder beyond the counter matrix (player base, game mode, zone/act, camera, input, SST layout, status bits). |
| `docs/superpowers/research/2026-04-20-s3k-trace-addresses.md` | Create | Same, for Sonic 3&K. |

No changes are made to `src/test/**` in this plan. The Java side is already v3.

## Risk Register

- **Status bit divergence.** S1 `OFF_STATUS = 0x22` and its bit assignments do not automatically port to S2/S3K. The S2 player object extends to `$40` with a different `status_secondary` byte, and S3K adds elemental shield and Super-form bits. The recorder must read the game-appropriate status byte and emit the same semantic meaning (`air`, `rolling`, `ground_mode`) in the CSV; do not blindly copy S1 bit masks.
- **Player base divergence.** S1 uses `PLAYER_BASE = 0xD000` (SST slot 0). S2 uses `MainCharacter = $FFB000` with the SST starting there. S3K uses `$FFB000` as well but with a different SST layout and additional `Player2/Player3` slots for Tails/Knuckles partners.
- **Input latch divergence.** S1 exposes `v_jpadhold1` at `0xF604`. S2 uses `Ctrl_1_held`, S3K uses `Ctrl_1_held` at different RAM addresses. The recorder must write the same `input_mask` byte semantics as S1 across all games.
- **Zone/act name mapping.** The S1 `ZONE_NAMES` table is game-specific. Each recorder needs its own table derived from its constants file. Replay tests key fixtures by zone name, so consistency across runs matters.
- **Game-mode sentinel.** S1 uses `GAMEMODE_LEVEL = 0x0C`. S2 and S3K use different constants (`IDs_Level`, `GM_Level`). Each recorder must detect "in-level" correctly or it will capture title-screen / results-screen frames.

Each risk is addressed by an explicit step in Task 1 or Task 4 below.

---

## Task 1: Resolve Remaining Sonic 2 Addresses

**Files:**
- Create: `docs/superpowers/research/2026-04-20-s2-trace-addresses.md`

- [ ] **Step 1: Write the per-game address matrix**

Document every RAM address the S2 recorder needs beyond the counter matrix. At minimum:

```md
| Purpose | Label | Address | Evidence |
|---|---|---:|---|
| game mode | `Game_Mode` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| in-level game-mode value | `IDs_Level` / `id_Level` | `0x0C` | `docs/s2disasm/s2.asm:<line>` |
| player SST base | `MainCharacter` | `<fill>` | `docs/s2disasm/s2.constants.asm:1101` |
| SST slot size | `object_size` | `0x40` | `docs/s2disasm/s2.constants.asm:<line>` |
| total SST slots | see `Object_RAM_End - Object_RAM` | `<fill>` | `docs/s2disasm/s2.constants.asm:1096-1145` |
| camera X | `Camera_X_pos` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| camera Y | `Camera_Y_pos` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| current zone | `Current_zone` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| current act | `Current_act` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| ring count | `Ring_count` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| held input P1 | `Ctrl_1_held` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| held-logical input P1 | `Ctrl_1_logical` | `<fill>` | `docs/s2disasm/s2.constants.asm:<line>` |
| player status primary | offset `status` within SST slot | `<fill>` | `docs/s2disasm/_inc/Object RAM offsets.asm:<line>` |
| player status secondary | offset `status_secondary` | `<fill>` | `docs/s2disasm/_inc/Object RAM offsets.asm:<line>` |
| player routine | offset `routine` | `<fill>` | `docs/s2disasm/_inc/Object RAM offsets.asm:<line>` |
| stand-on-object | offset `interact` or `top_solid_bit` (whichever tracks rider) | `<fill>` | `docs/s2disasm/_inc/Object RAM offsets.asm:<line>` |
```

- [ ] **Step 2: Resolve the S2 status bit mapping**

Add a subsection naming each status bit used by the CSV (`air`, `rolling`, facing-left, on-object, underwater, pushing, roll-jump) with bit values and evidence:

```md
| Semantic | Bit | Evidence |
|---|---:|---|
| facing_left | 0x01 | `docs/s2disasm/_inc/Object RAM offsets.asm:<line>` |
| in_air | 0x02 | `...` |
| rolling | 0x04 | `...` |
| on_object | 0x08 | `...` |
| roll_jump | 0x10 | `...` |
| pushing | 0x20 | `...` |
| underwater | 0x40 | `...` |
```

Any S2-specific bits that do not exist in S1 (e.g. secondary status shields in S3K) must be marked explicitly and excluded from the v3 CSV — the CSV only carries the S1-compatible semantic set.

- [ ] **Step 3: Resolve the S2 zone/act name table**

Produce a table matching the `ZONE_NAMES` Lua table with S2 zone IDs. Use the canonical short names already used by `src/test/resources/traces/` (`ehz`, `cpz`, `arz`, ...).

- [ ] **Step 4: No code changes in this task**

This task is a research gate. Do not touch Lua or Java until the research doc has all three tables filled in.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/research/2026-04-20-s2-trace-addresses.md
git commit -m "docs: resolve sonic 2 trace recorder ram addresses"
```

## Task 2: Build the Sonic 2 Recorder

**Files:**
- Create: `tools/bizhawk/s2_trace_recorder.lua`

- [ ] **Step 1: Copy and re-parameterize `s1_trace_recorder.lua`**

Start from a verbatim copy of `tools/bizhawk/s1_trace_recorder.lua`. Change only the `Constants` section, the `ZONE_NAMES` table, the `metadata.json` `game` field, and the header comment. Do not reorganize the main loop, the aux-event format, or the file I/O path.

Set constants to match the Task 1 research doc:

```lua
-- Sonic 2 REV01 68K RAM addresses
local ADDR_GAME_MODE       = <from research>
local ADDR_CTRL1           = <from research>   -- Ctrl_1_held
local ADDR_CTRL1_DUP       = <from research>   -- Ctrl_1_logical
local ADDR_RING_COUNT      = <from research>
local ADDR_CAMERA_X        = <from research>
local ADDR_CAMERA_Y        = <from research>
local ADDR_ZONE            = <from research>
local ADDR_ACT             = <from research>
local PLAYER_BASE          = <from research>   -- MainCharacter
local OBJ_TABLE_START      = <from research>
local OBJ_SLOT_SIZE        = 0x40
local OBJ_TOTAL_SLOTS      = <from research>
local OBJ_DYNAMIC_START    = <from research>
-- Schema v3 counters
local ADDR_FRAMECOUNT      = 0xFE04  -- Level_frame_counter (S2)
local ADDR_VBLA_WORD       = 0xFE0C  -- Vint_runcount+2     (S2)
```

- [ ] **Step 2: Replace status bit masks with S2 values**

Update the `STATUS_*` locals to use the bits resolved in Task 1 Step 2. If the S2 "on object" state lives in `status_secondary` rather than the primary status byte, add a second status read and derive the bit from there, but still emit the same semantic `ground_mode` / `air` / `rolling` flags in the CSV so the shared parser does not need to know it was S2.

- [ ] **Step 3: Replace the zone name table**

Swap `ZONE_NAMES` for the S2 version from Task 1 Step 3.

- [ ] **Step 4: Update metadata emission**

In `write_metadata`, set:

```lua
meta_file:write('  "game": "s2",\n')
...
meta_file:write('  "lua_script_version": "3.0-s2",\n')
meta_file:write('  "trace_schema": 3,\n')
```

Keep `csv_version = 4` to match the S1 recorder — the CSV layout is identical by design.

- [ ] **Step 5: Lag counter placeholder**

Sonic 2 does not expose a per-frame lag counter. Leave:

```lua
local lag_counter = 0
```

Do not synthesize a counter from `Vint_runcount - Level_frame_counter`; the matrix explicitly records S2 as not having a lag counter.

- [ ] **Step 6: Chromeless launch contract**

Keep the existing `HEADLESS` / `client.speedmode(6400)` / `client.invisibleemulation(true)` sequence and the `while true do ... emu.frameadvance() end` main loop untouched. Only the per-game values change.

- [ ] **Step 7: Verify script loads**

Open BizHawk 2.11, load the Sonic 2 REV01 ROM, load the new `s2_trace_recorder.lua` in Lua Console. Expected console output: `"S2 Trace Recorder v3.0 loaded. Waiting for level gameplay..."` (or equivalent). The script should not error on load.

No commit yet — keep this paired with Task 3 validation.

## Task 3: Validate the Sonic 2 Recorder End-to-End

**Files:**
- Create: `tools/bizhawk/record_s2_trace.bat`

- [ ] **Step 1: Create the S2 launcher batch**

Copy `tools/bizhawk/record_trace.bat` to `tools/bizhawk/record_s2_trace.bat`. Change only:

- the header comment (Sonic 2 instead of Sonic 1)
- `set "LUA_SCRIPT=%~dp0s2_trace_recorder.lua"`

The ROM and BK2 arguments stay positional.

- [ ] **Step 2: Run a short recording**

Record a short EHZ1 or similar run using any available S2 BK2. The script should:

- produce `tools/bizhawk/trace_output/physics.csv` with the same header as S1
- produce `tools/bizhawk/trace_output/metadata.json` with `"game": "s2"` and `"trace_schema": 3`
- produce `tools/bizhawk/trace_output/aux_state.jsonl` with routine/mode-change events

If any of these fail, stop and fix the recorder before continuing.

- [ ] **Step 3: Round-trip through `TraceData`**

Point the existing S2-capable side of the parser at the captured output (either by copying it into a temporary `src/test/resources/traces/s2/<zone>/` directory or by running a small ad-hoc parser test). Verify:

- `TraceMetadata.traceSchema()` returns `3`
- `TraceFrame.gameplayFrameCounter()` is non-zero and strictly non-decreasing
- `TraceFrame.vblankCounter()` is non-zero and strictly non-decreasing
- `TraceFrame.lagCounter()` is `0` for every row
- `TraceExecutionModel.forGame("s2").phaseFor(previous, current)` returns `FULL_LEVEL_FRAME` for most frames, with occasional `VBLANK_ONLY` during genuine lag

If a synthetic fixture is easier than a full capture, add `src/test/resources/traces/synthetic/s2_execution_v3_2frames/` following the pattern of `synthetic/execution_v3_2frames/`.

- [ ] **Step 4: Commit the S2 recorder and launcher**

```bash
git add tools/bizhawk/s2_trace_recorder.lua tools/bizhawk/record_s2_trace.bat
git commit -m "feat: add sonic 2 v3 trace recorder"
```

## Task 4: Resolve Remaining Sonic 3&K Addresses

**Files:**
- Create: `docs/superpowers/research/2026-04-20-s3k-trace-addresses.md`

- [ ] **Step 1: Write the per-game address matrix**

Same layout as Task 1 Step 1, but for S3K:

```md
| Purpose | Label | Address | Evidence |
|---|---|---:|---|
| game mode | `Game_Mode` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| in-level game-mode value | `GM_Level` or `IDs_Level` | `<fill>` | `docs/skdisasm/sonic3k.asm:<line>` |
| player 1 SST base | `Player_1` / `MainCharacter` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| SST slot size | `object_size` | `0x40` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| total SST slots | from `Object_RAM` block | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| camera X | `Camera_X_pos` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| camera Y | `Camera_Y_pos` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| current zone | `Current_zone` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| current act | `Current_act` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| ring count | `Ring_count` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| held input P1 | `Ctrl_1_held` | `<fill>` | `docs/skdisasm/sonic3k.constants.asm:<line>` |
| player status primary | offset `status` | `<fill>` | `docs/skdisasm/_inc/Object RAM offsets.asm:<line>` |
| player status secondary | offset `status_secondary` | `<fill>` | `...` |
| player status tertiary (shields) | offset `<shield-tracking byte>` | `<fill>` | `...` |
| player routine | offset `routine` | `<fill>` | `...` |
| stand-on-object | rider-tracking offset | `<fill>` | `...` |
```

The counter addresses (`Level_frame_counter = 0xFE08`, `V_int_run_count+2 = 0xFE12`, `Lag_frame_count = 0xF628`) are pre-resolved — do not re-derive them.

- [ ] **Step 2: Resolve the S3K status bit mapping**

Produce the same semantic → bit table as Task 1 Step 2. Mark S3K-only bits (elemental shields, Super form, insta-shield in progress) as "not in v3 CSV" so the recorder does not leak them into the shared schema. They can still be captured as diagnostic aux events if useful for debugging, but they do not get their own physics.csv columns.

- [ ] **Step 3: Resolve the S3K zone/act name table**

Use the short names already used by `game.sonic3k` code (`aiz`, `hcz`, `mgz`, `cnz`, `fbz`, `icz`, `lbz`, `mhz`, `soz`, `lrz`, `ssz`, `ddz`). S3K has the SKL/S3KL zone-set split; the recorder only needs the level-short-name mapping — do not bake the zone-set split into the recorder.

- [ ] **Step 4: Document the lag-counter reset semantics**

Add a short subsection explaining that `Lag_frame_count` at `0xF628` is zeroed by `Do_Updates` during normal frames and incremented by `VInt_0_Main` during lag VBlanks. The recorder should treat it as a diagnostic counter that will read `0` on non-lag frames and a small positive integer on lag frames. Replay must still derive phase from `gameplay_frame_counter` / `vblank_counter` deltas, not from `Lag_frame_count` (the execution model is already locked in this way).

- [ ] **Step 5: No code changes in this task**

Research gate, mirror of Task 1 Step 4.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/research/2026-04-20-s3k-trace-addresses.md
git commit -m "docs: resolve sonic 3k trace recorder ram addresses"
```

## Task 5: Build the Sonic 3&K Recorder

**Files:**
- Create: `tools/bizhawk/s3k_trace_recorder.lua`

- [ ] **Step 1: Copy and re-parameterize**

Start from a verbatim copy of `tools/bizhawk/s1_trace_recorder.lua` (not the S2 recorder — start from the canonical S1 reference to avoid compounding any S2-specific drift). Update only the constants, zone table, metadata, and header comment.

- [ ] **Step 2: Use the pre-resolved S3K counter addresses**

```lua
-- Sonic 3&K counters (matrix-resolved; do not change)
local ADDR_FRAMECOUNT       = 0xFE08   -- Level_frame_counter (S3K)
local ADDR_VBLA_WORD        = 0xFE12   -- V_int_run_count+2    (S3K)
local ADDR_LAG_FRAME_COUNT  = 0xF628   -- Lag_frame_count      (S3K)
```

- [ ] **Step 3: Populate `lag_counter`**

```lua
local lag_counter = mainmemory.read_u16_be(ADDR_LAG_FRAME_COUNT)
```

Do not gate `lag_counter` behind `VBLANK_ONLY` — always emit the current value. Replay treats it as diagnostic.

- [ ] **Step 4: Adjust status bits and player base**

Swap in the S3K values from Task 4 Steps 1–2. If the S3K "on object" bit lives in `status_secondary`, read that byte too; emit the same semantic flags into the CSV.

- [ ] **Step 5: Update metadata emission**

```lua
meta_file:write('  "game": "s3k",\n')
...
meta_file:write('  "lua_script_version": "3.0-s3k",\n')
meta_file:write('  "trace_schema": 3,\n')
```

- [ ] **Step 6: Verify script loads**

Open BizHawk with a combined Sonic 3 & Knuckles locked-on ROM image. Load the recorder. It should print its banner and wait for `GM_Level` with controls unlocked.

No commit yet — paired with Task 6 validation.

## Task 6: Validate the Sonic 3&K Recorder End-to-End

**Files:**
- Create: `tools/bizhawk/record_s3k_trace.bat`

- [ ] **Step 1: Create the S3K launcher batch**

Copy `tools/bizhawk/record_trace.bat`, point at `s3k_trace_recorder.lua`.

- [ ] **Step 2: Run a short recording**

Record a short AIZ1 or HCZ1 run. Verify the same file triad as Task 3 Step 2.

- [ ] **Step 3: Round-trip through `TraceData`**

Verify:

- `TraceMetadata.traceSchema() == 3`
- `TraceMetadata.game().equals("s3k")`
- `TraceFrame.gameplayFrameCounter()` is non-zero and strictly non-decreasing
- `TraceFrame.vblankCounter()` is non-zero and strictly non-decreasing
- `TraceFrame.lagCounter()` is `0` on every non-lag frame and positive only when the gameplay counter did not advance
- `TraceExecutionModel.forGame("s3k").phaseFor(previous, current)` returns the expected phase using the `gameplay_frame_counter` delta rule, not `lag_counter`

- [ ] **Step 4: Commit the S3K recorder and launcher**

```bash
git add tools/bizhawk/s3k_trace_recorder.lua tools/bizhawk/record_s3k_trace.bat
git commit -m "feat: add sonic 3k v3 trace recorder"
```

## Task 7: Documentation and Known-Discrepancy Closeout

**Files:**
- Modify: `tools/bizhawk/README.md`
- Modify: `docs/guide/contributing/trace-replay.md`
- Modify: `docs/KNOWN_DISCREPANCIES.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Update `tools/bizhawk/README.md`**

Add new bullets next to the S1 recorder mention:

```md
- `s2_trace_recorder.lua` captures Sonic 2 ROM-side trace data using schema v3
- `s3k_trace_recorder.lua` captures Sonic 3&K ROM-side trace data using schema v3
- `record_s2_trace.bat` / `record_s3k_trace.bat` launch the corresponding headless recorders
```

Keep the pointer to `docs/guide/contributing/trace-replay.md` as the canonical doc.

- [ ] **Step 2: Update `docs/guide/contributing/trace-replay.md`**

Replace any "S1-only" qualifier in the recorder walkthrough with a per-game table mirroring the counter matrix. Spell out for each game:

- recorder script path
- launcher batch path
- expected `metadata.json["game"]` value
- counter addresses (reproduce the pre-resolved matrix for convenience)
- whether `lag_counter` is meaningful (S3K) or a placeholder zero (S1, S2)

- [ ] **Step 3: Close out `KNOWN_DISCREPANCIES.md` entry #10**

Entry 10 `Trace Replay Recorder Coverage` currently documents the deferred state. Once Tasks 3 and 6 are both committed and round-trip-validated, either:

- mark the entry as resolved with a date and link to this plan, and remove it from the table of contents in a subsequent bookkeeping commit, or
- tighten the remaining description to "historical note: S1 recorder landed 2026-04-19, S2/S3K landed 2026-04-20" and keep the entry as a migration audit trail.

Prefer the second option so the historical context is preserved.

- [ ] **Step 4: Update `CHANGELOG.md`**

In the `Unreleased → Performance, Parity, and Trace Replay` section, amend the existing bullet that currently says "The BizHawk v3 recorder upgrade is currently landed for Sonic 1; Sonic 2 and Sonic 3K recorder migration remains deferred." Replace with:

```md
- Trace replay now supports schema v3 execution counters
  (`gameplay_frame_counter`, `vblank_counter`, `lag_counter`). Sonic 1,
  Sonic 2, and Sonic 3&K BizHawk recorders all emit the v3 schema.
```

- [ ] **Step 5: Commit**

```bash
git add tools/bizhawk/README.md docs/guide/contributing/trace-replay.md docs/KNOWN_DISCREPANCIES.md CHANGELOG.md
git commit -m "docs: document v3 recorder coverage for s1 s2 and s3k"
```

## Task 8: Optional — Regenerate Historical S2/S3K Fixtures

**Files:**
- Modify: `src/test/resources/traces/s2/**` (create if new captures are added)
- Modify: `src/test/resources/traces/s3k/**` (create if new captures are added)

- [ ] **Step 1: Decide whether to land new fixtures**

There are currently no checked-in S2 or S3K trace fixtures under `src/test/resources/traces/` (confirmed in the research matrix). This task is optional: the recorders can land without any fixtures, and fixtures can be added later alongside the first S2 or S3K parity-regression test.

If the decision is to land an initial fixture, add one minimal BK2-derived run per game (e.g. first 600 frames of EHZ1 for S2, first 600 frames of AIZ1 for S3K) plus a matching replay test that parses the fixture and asserts schema v3 + monotonic counters. Do not wire it into `AbstractTraceReplayTest` yet — that would couple the fixture to the full S1 replay contract before parity is established.

- [ ] **Step 2: If landing fixtures, remove the heuristic fallback**

Once at least one real v3 fixture exists for every game that the replay harness is expected to cover, the one-shot "falling back to legacy heuristic" notice in `TraceData` and the `TraceData.isLagFrame(...)` helper (already deleted in the parent plan's Task 3) can be revisited. This is explicitly a follow-up, not a gate on landing the recorders.

- [ ] **Step 3: Commit (only if fixtures were added)**

```bash
git add src/test/resources/traces/s2 src/test/resources/traces/s3k src/test/java/com/openggf/tests/trace/s2 src/test/java/com/openggf/tests/trace/s3k
git commit -m "test: add initial s2 and s3k v3 trace fixtures"
```

## Validation Matrix

After each recorder lands, the following must all pass on the host machine with a local ROM + BK2:

```powershell
# Sanity: existing S1 replay suite still green (no regressions in shared Java code).
mvn -Dmse=relaxed "-Dtest=TestTraceDataParsing,TestTraceExecutionModel" test
mvn -Dmse=relaxed "-Dtest=com.openggf.tests.trace.s1.*TraceReplay*" test

# Recorder sanity: S2 physics.csv parses with schema v3 and passes the ad-hoc counter check from Task 3 Step 3.
# Recorder sanity: S3K physics.csv parses with schema v3 and passes the lag_counter check from Task 6 Step 3.
```

Everything above `Task 7 Step 5` must ship green before the known-discrepancy entry is closed out.

## Self-Review

- Spec coverage:
  - Counter addresses pre-resolved and locked to the matrix (avoids re-litigating Task 1 of the parent plan).
  - S2 and S3K recorders handled as sibling tasks, each with its own address research gate.
  - Explicit rule against synthesizing an S2 lag counter.
  - Docs and known-discrepancies closeout are explicit task steps, not afterthoughts.
- Placeholder scan:
  - No `TODO`, `TBD`, or "handle appropriately" placeholders remain.
  - Every per-game address that isn't in the matrix is routed through a research-doc `<fill>` placeholder that is explicitly a gate.
- Type consistency:
  - The CSV schema stays identical across S1/S2/S3K — the Java parser is not touched.
  - `metadata.json` game codes match the set already accepted by `TraceExecutionModel.forGame(String)`: `s1`, `s2`, `s3`, `s3k`.
- Risk register explicitly names the four drift hazards (status bits, player base, input latch, game-mode sentinel) and routes each into a concrete research step.

## Execution Handoff

Plan saved to `docs/superpowers/plans/2026-04-20-s2-s3k-trace-recorder-v3.md`. Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task, review between tasks, fast iteration. Especially useful because Tasks 1/4 are research-heavy and benefit from a fresh context for the disassembly reading.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints (one natural checkpoint after S2 lands, another after S3K lands).

Which approach?
