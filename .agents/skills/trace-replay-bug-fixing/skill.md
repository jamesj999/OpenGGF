---
name: trace-replay-bug-fixing
description: Use when investigating or fixing any *TraceReplay test failure across the engine. Covers the comparison-only invariant (trace data is read-only diagnostic input, never written back into engine state), the recorder/parser/comparator pipeline, the diagnose-fix-regen-loop workflow, cross-game parity rules, and how to extend the recorder when more diagnostic data is needed.
---

# Trace Replay Bug Fixing

Recorded BizHawk traces verify that the engine plays back ROM behaviour pixel-for-pixel given the same controller input. When a `*TraceReplay` test diverges, this skill describes how to diagnose, fix, and (when needed) regenerate traces — without taking shortcuts that mask engine bugs.

## Core Mission Rules (apply to all trace work)

1. **No hacks or dirty fixes.** Every behaviour change must be backed by the disassembly for the relevant game. Cite ROM file and line numbers in commits and code comments.
2. **You may regenerate a trace** when the recorded data is genuinely insufficient for diagnosis (missing per-frame data, broken setup, recorder schema changed). Use the `tools/bizhawk/record_*_trace.bat` launcher with the matching `.lua` recorder. Regeneration is part of the loop — don't avoid it. **But** do not regenerate just to "make the test match"; regenerate to gain visibility.
3. **If the engine architecture is missing or fundamentally broken**, or game objects/functionality aren't yet implemented, **plan and delegate**. Use review agents and parallel subagent execution for large-scope work. Don't try to land everything in one pass.
4. **Cross-game parity is non-negotiable.** The engine supports three games (Sonic 1, Sonic 2, Sonic 3 & Knuckles). Before changing any "root" code (physics, collision, sidekick AI, oscillation, rendering, audio, etc.), check the disassemblies for **all three games** to confirm whether the change is a universal correction or a per-game divergence. Universal corrections must keep all games' traces green. Per-game divergences must be gated behind a `PhysicsFeatureSet` flag (see CLAUDE.md "Per-Game Physics Framework"). **Never** branch on `if (gameId == GameId.S3K) ...`.

## The Core Invariant — Comparison Only, Never Sync

**The engine must be able to play back any BK2 movie and produce ROM-correct behaviour natively, with no trace data on its inputs.**

The trace replay tests prove this. They are not state-syncing harnesses.

- The **only** input the engine receives during a trace replay test is the BK2 controller stream (Player 1 buttons each frame). Everything else — sidekick AI, oscillator phase, object state, audio, RNG, sub-pixel position — must evolve natively from the same starting conditions ROM had at frame 0.
- `physics.csv` and `aux_state.jsonl` are **read-only diagnostic data**. They feed the divergence comparator and the divergence report. They are **not** allowed to write back into engine state in committed test code.
- **Pre-trace bootstrap is fine.** Setting starting position, RNG seed, oscillation pre-advance, and frame counter once at frame 0 is "load a save state at the BK2 starting point". The prohibition is on per-frame write-back during the comparison loop.
- **Diagnostic re-seeding is acceptable, but only as uncommitted exploratory work.** ("Does this divergence cascade from a single bad frame? Re-seed sidekick state at frame K and see what happens" — fine to try, not fine to land.)

If a trace replay test passes only because engine state is snapped back to ROM-correct values each frame, the engine has not been verified — it has been masked. That defeats the purpose.

### Concrete prohibitions

- No `applyRecordedFirstSidekickState`-style methods that copy CSV columns into engine sprites/managers/controllers as part of the per-frame test loop.
- No `hydrateFromRom*` calls in the per-frame loop. Such helpers may exist in engine code, but the test loop must not invoke them.
- New aux event types are **diagnostic only**. They feed the divergence report and per-frame comparator; they do not feed engine state.
- No "elastic windows" or "tolerance bands" introduced to suppress divergence around suspected engine bugs. If a comparison threshold is non-zero, it must reflect a known ROM/engine semantic difference declared as a tolerance because it is not a bug. Otherwise the bug is fixed in the engine and the threshold stays zero.

### What to do when the engine diverges from ROM

- Find the engine code path that should have produced the ROM-correct value but didn't. Fix the engine.
- If the engine has no equivalent path, port the ROM logic with disassembly citations and a `PhysicsFeatureSet` flag if it's game-divergent.
- If the trace lacks the diagnostic data needed to pinpoint the bug, extend the recorder. New fields are comparison context, not write-back targets.

## Pipeline Overview

```
+------------------------+     +------------------------+     +------------------------+
| BK2 movie (Bizhawk)   | --> | Lua recorder (.lua)    | --> | Trace files            |
| - P1 controller frames |     | - reads RAM each frame |     | - metadata.json        |
|                        |     | - writes physics.csv   |     | - physics.csv          |
|                        |     | - writes aux_state.jsonl|    | - aux_state.jsonl      |
+------------------------+     +------------------------+     +------------------------+
                                                                       |
                                                                       v
+------------------------+     +------------------------+     +------------------------+
| Engine simulation      | <-- | AbstractTraceReplayTest| <-- | TraceData parser       |
| - reads BK2 input only |     | - drives engine        |     | - reads metadata       |
| - native simulation    |     | - compares each frame  |     | - reads physics rows   |
+------------------------+     +------------------------+     | - reads aux events     |
        |                              |                      +------------------------+
        v                              v
+------------------------+     +------------------------+
| Engine state per frame | --> | TraceBinder.compareFrame| --> DivergenceReport
+------------------------+     +------------------------+
```

The arrow from `TraceData` into the engine simulation goes ONLY into `compareFrame`. There is no arrow from `TraceData` into engine state.

## File Layout

### Recorder (`tools/bizhawk/`)

- `<game>_trace_recorder.lua` — per-game lua scripts launched inside Bizhawk-2.11 with `--lua <recorder>` and `--movie <bk2>`. Each frame the script reads RAM, classifies the frame phase, and emits one CSV row plus zero-or-more aux JSONL events.
- `record_<game>_trace.bat` — Windows launcher wrapping Bizhawk's headless mode.
- `trace_output/` — scratch directory the recorder writes to. Outputs are *manually copied* into the test resources tree.

Lua-side schema versioning:
- `local TRACE_SCHEMA_VERSION = 5` — bumped only when CSV columns change.
- `local LUA_SCRIPT_VERSION = "<version>"` — bumped on any recorder change (CSV, aux, or behaviour).
- `aux_schema_extras` — list of optional aux event types this trace contains. Parsers opt in by checking `TraceMetadata.has<Feature>()`.

### Trace files (`src/test/resources/traces/<game>/<zone>/`)

- `metadata.json` — game, zone, act, BK2 frame offset, trace frame count, oscillation pre-advance, character set, lua/recorder version, ROM checksum, profile, `aux_schema_extras`.
- `physics.csv` — one row per recorded frame. Frame numbers are hex; fields documented in the recorder's CSV header function.
- `aux_state.jsonl` — one JSON object per line. Standard event types: `zone_act_state`, `checkpoint`, `state_snapshot`, `mode_change`, `slot_dump`, `object_appeared`, `object_near`, `object_removed`. Plus opt-in events declared in `aux_schema_extras` (e.g. `cpu_state` per-frame for sidekick CPU state).
- `*.bk2` — the BK2 movie. Bizhawk replays this against the ROM to drive the recording. `bk2_frame_offset` in metadata is where recording starts inside the BK2.

Pre-trace setup events (frame `-1`) capture starting state for one-time bootstrap (player position history, RNG seed, oscillator phase, object snapshots).

### Parser (`src/main/java/com/openggf/trace/`)

- `TraceData` — top-level loader. `load(Path)` reads metadata + physics + aux. Random-access by frame.
- `TraceFrame` — one CSV row.
- `TraceMetadata` — parsed metadata.json. `hasPerFrameXxx()` accessors return true when the corresponding `aux_schema_extras` key is present.
- `TraceEvent` — sealed type hierarchy of aux event records.
- `TraceBinder` — frame-by-frame comparator. `compareFrame(expected, actual...)` records divergences into a `DivergenceReport`.
- `DivergenceReport` — summarises errors and warnings. `getContextWindow(frame, radius)` produces a human-readable side-by-side dump.
- `<engine>.hydrateFromRomXxx` helpers — engine helpers may exist for one-off probes, but **are not invoked from the test per-frame loop** (per the core invariant).

### Test framework (`src/test/java/com/openggf/tests/trace/`)

- `AbstractTraceReplayTest` — abstract base. Subclasses provide game/zone/act/path; the base class loads metadata, validates configuration, drives BK2 playback via `HeadlessTestFixture`, runs the per-frame comparator, and writes the divergence report.
- Concrete subclasses: one per recorded zone.

## Workflow — Diagnose, Fix, Regen, Loop

```
1. Run the failing trace test:
     mvn test -Dtest=<Test*TraceReplay> -DfailIfNoTests=false

2. Read target/trace-reports/<game>_<zone>_report.json (errors[0])
   and target/trace-reports/<game>_<zone>_context.txt
   (divergence window: ROM vs engine side-by-side).

3. Locate the diverging field at the first error frame K:
     - Player physics (x, y, x_speed, ...): inspect physics CSV row K.
     - Aux state (objects, checkpoints, CPU state): inspect aux_state.jsonl events at frame K.

4. Find the matching ROM routine in the relevant disassembly:
     - docs/s1disasm/, docs/s2disasm/, docs/skdisasm/
     - Use the s1disasm-guide / s2disasm-guide / s3k-disasm-guide skills
       to navigate, plus the RomOffsetFinder tool for offset lookups.
   Read the ROM logic completely. Compare with the engine path.

5. Identify the divergence. Choose the fix:
     - Engine code path missing a step that ROM does:    add it.
     - Engine code path doing a step ROM doesn't:        remove it (carefully).
     - Engine code path with wrong constant/threshold:   fix the value.
     - Per-game divergence:                              add a PhysicsFeatureSet flag.
     - Test infrastructure asserting wrong behaviour:    fix the test (with disasm citation).

6. If you can't pinpoint the bug because the trace lacks the right data:
     - Extend the recorder lua with a new aux event type.
     - Bump LUA_SCRIPT_VERSION; add an opt-in key to aux_schema_extras.
     - Add a matching TraceEvent record + parser handler.
     - Wire the new data into DivergenceReport rendering or a probe class.
     - Regenerate the affected trace(s).
     - DO NOT wire the new data into engine-state mutation in the test loop.

7. Implement the fix:
     - Disassembly-cited (file + line numbers).
     - Cross-check the other two games' disassemblies for shared code.
     - Gate per-game divergences via PhysicsFeatureSet.

8. Run the trace test plus cross-game traces:
     mvn test -Dtest='Test<Game1>Ghz1TraceReplay,Test<Game1>Mz1TraceReplay,Test<Game2>Ehz1TraceReplay,Test<Game3><Zone>TraceReplay' -DfailIfNoTests=false
   All previously-green traces must stay green; the targeted trace
   should advance its first error frame (or, ideally, become green).

9. Commit with proper trailers (see Branch Documentation Policy in
   CLAUDE.md/AGENTS.md). No --no-verify.

10. Loop: read the new first-error frame, repeat from step 3.
```

## Trace Regeneration

When you need new diagnostic data, regenerate the trace. The proven Windows PowerShell pattern:

```powershell
$env:OGGF_<GAME>_TRACE_PROFILE = "<profile>"
Set-Location <repo or worktree root>
if (Test-Path "tools\bizhawk\trace_output") { Remove-Item -Recurse -Force "tools\bizhawk\trace_output" }
& "tools\bizhawk\record_<game>_trace.bat" `
    "C:\path\to\<rom file>.gen" `
    "src\test\resources\traces\<game>\<zone>\<bk2 file>.bk2" `
    "<profile>"
```

Output lands in `tools/bizhawk/trace_output/`. Copy `metadata.json`, `physics.csv`, `aux_state.jsonl` into the test resources tree (the `.bk2` is unchanged) and commit the regen as a separate logical change from any recorder schema change.

Profiles are declared inside the lua via `is_*_profile()` predicates — check the recorder for the available list. Common ones: gameplay-unlock starts at controls-active, level-gated-reset-aware starts at gameplay and discards on soft-reset, end-to-end starts at BK2 frame 0.

## Recorder Extension Recipe

When a divergence can't be pinpointed without more ROM-side state:

1. **Lua side.** Add a helper function (e.g. `write_<feature>_per_frame()`) that reads the RAM block of interest and emits a JSONL line with a new `event` type. Call it from the per-frame entry. Bump `LUA_SCRIPT_VERSION`. Add an opt-in key to `aux_schema_extras` (e.g. `"<feature>_per_frame"`).
2. **Java parser.** Add a new sealed-record type to `TraceEvent` (e.g. `TraceEvent.<Feature>State`). Parse the new JSON event in `TraceEvent.parseJsonLine`. Add `TraceMetadata.hasPerFrame<Feature>()` and `TraceData.<feature>StateForFrame(frame)`. Keep parsers tolerant — old traces without the new key must still load.
3. **Diagnostic use.** Wire the new data into `DivergenceReport.getContextWindow` rendering, or into a dedicated probe class for targeted bug investigation. **Do not** wire it into engine state mutation in the per-frame test loop.
4. **Regenerate the affected trace(s).** Commit the regen separately from the recorder schema change so reviewers can see the data churn distinctly.

## Cross-Game Sanity Checks

Always run all green trace tests every iteration when touching shared code:

```
mvn test -Dtest='*TraceReplay' -DfailIfNoTests=false
```

For S3K work specifically, also keep the S3K must-keep-green tests green:
- `TestS3kAiz1SkipHeadless`
- `TestSonic3kLevelLoading`
- `TestSonic3kBootstrapResolver`
- `TestSonic3kDecodingUtils`

If a fix is genuinely game-divergent (different games' ROMs really do behave differently), add a flag to `PhysicsFeatureSet`, set the right value on each game's `SONIC_1`/`SONIC_2`/`SONIC_3K` constant, and branch on the flag at the call site.

## When to Stop and Plan

Per mission rule 3, hand work off when scope expands beyond a clean fix:

- Multiple objects/badniks need to be implemented (use the `<game>-implement-object` and `<game>-implement-boss` skills).
- A whole zone needs bringing up (use `s3k-zone-bring-up` for S3K; pattern transfers).
- A subsystem (audio driver, collision framework, animation pipeline) needs significant rework.
- A trace bug requires recorder schema changes + parser updates + multiple engine fixes — split into commits/agents per concern.

Plan first, dispatch parallel subagents per independent concern (use the `superpowers:dispatching-parallel-agents` skill), then integrate.

## Related Skills

When working through a trace bug you'll often pull these in:

- **Disassembly navigation:** `s1disasm-guide`, `s2disasm-guide`, `s3k-disasm-guide` — label conventions, file structure, RomOffsetFinder commands.
- **Object/badnik implementation:** `s1-implement-object`, `s2-implement-object`, `s3k-implement-object`, `s1-implement-boss`, `s2-implement-boss`, `s3k-implement-boss`.
- **Trace recording (game-specific):** `s1-trace-replay`, `s1-retro-trace`.
- **S3K specific:** `s3k-plc-system`, `s3k-zone-events`, `s3k-zone-validate`, `s3k-zone-analysis`, `s3k-zone-bring-up`, `s3k-palette-cycling`, `s3k-parallax`, `s3k-animated-tiles`.
- **Generic engineering process:** `superpowers:systematic-debugging`, `superpowers:dispatching-parallel-agents`, `superpowers:writing-plans`, `superpowers:test-driven-development`, `superpowers:verification-before-completion`, `superpowers:requesting-code-review`.

## Why This Matters

The mission is faithful pixel-for-pixel reimplementation. Trace replay tests are the proof. If they're allowed to lean on synced trace data each frame, the proof is hollow — bugs hide behind the synchronisation and the test green-lights anyway. Honest tests force honest engine fixes. That's how progress compounds: every fix makes the next divergence visible instead of building on top of a masked one.
