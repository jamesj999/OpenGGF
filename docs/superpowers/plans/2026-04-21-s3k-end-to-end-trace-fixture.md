# S3K End-to-End Trace Fixture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the authoritative Sonic 3&K AIZ intro-to-HCZ BK2 trace fixture, the elastic-window replay harness support it needs, and the default-suite replay test that stays green without faking ROM-length decompression stalls.

**Architecture:** Keep `physics.csv` on the existing schema-v3 path and add the new S3K semantics entirely through `aux_state.jsonl` checkpoint and `zone_act_state` events. On replay, keep the current strict binder for normal frames, but switch the S3K path to a two-cursor elastic-window controller: one cursor keeps BK2/input and execution-model advancement aligned with the recorded trace stream, while a separate strict-comparison cursor re-anchors at the recorded exit checkpoint frame.

**Tech Stack:** BizHawk 2.11 Lua recorder scripts, Java 21, JUnit 5, existing trace-replay test harness (`TraceData`, `TraceBinder`, `AbstractTraceReplayTest`, `DivergenceReport`), S3K disassembly under `docs/skdisasm/`.

---

## File Map

| Path | Action | Responsibility |
| --- | --- | --- |
| `docs/superpowers/research/2026-04-21-s3k-trace-addresses.md` | Create or replace | Freeze the RAM-backed checkpoint signals and the engine mirrors used by recorder/replay detection. |
| `docs/superpowers/specs/2026-04-21-s3k-end-to-end-trace-fixture-design.md` | Already updated | Final source of truth for the clarified elastic-window rules. |
| `tools/bizhawk/s3k_trace_recorder.lua` | Modify | Add the end-to-end profile, checkpoint emission, `zone_act_state`, deterministic same-frame ordering, and metadata needed for the real fixture. |
| `tools/bizhawk/record_s3k_trace.bat` | Modify if needed | Keep the S3K launcher aligned with the recorder profile and output directory shape. |
| `tools/bizhawk/README.md` | Modify | Document the end-to-end S3K recorder mode and the authoritative fixture import flow. |
| `docs/guide/contributing/trace-replay.md` | Modify | Contributor-facing instructions for recording/regenerating the S3K fixture. |
| `src/test/java/com/openggf/tests/trace/TraceEvent.java` | Modify | Add typed `checkpoint` and `zone_act_state` aux events. |
| `src/test/java/com/openggf/tests/trace/TraceData.java` | Modify | Add helpers for finding the latest checkpoint / zone-act event at or before a frame. |
| `src/test/java/com/openggf/tests/trace/TraceEventFormatter.java` | Modify | Add compact summaries for new checkpoint/state events. |
| `src/test/java/com/openggf/tests/trace/DivergenceReport.java` | Modify | Surface latest checkpoint and latest `zone_act_state` in summary/json/context. |
| `src/test/java/com/openggf/tests/trace/TraceBinder.java` | Modify | Add a report-construction path that preserves the current comparison contract while attaching trace aux context. |
| `src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java` | Modify | Replace the single-index strict replay loop with the two-cursor elastic-window loop for S3K. |
| `src/test/java/com/openggf/tests/trace/TestTraceDataParsing.java` | Modify | Parser tests for `checkpoint` and `zone_act_state`. |
| `src/test/java/com/openggf/tests/trace/TestTraceEventFormatting.java` | Modify | Summary-format tests for new aux event types. |
| `src/test/java/com/openggf/tests/trace/TestDivergenceReport.java` | Modify | Report-context tests for checkpoint/state lookups. |
| `src/test/java/com/openggf/tests/trace/s3k/S3kReplayCheckpointDetector.java` | Create | Pure replay-side detector keyed from engine state, not trace aux data. |
| `src/test/java/com/openggf/tests/trace/s3k/S3kElasticWindowController.java` | Create | Two-cursor controller for strict replay vs elastic windows. |
| `src/test/java/com/openggf/tests/trace/s3k/TestS3kReplayCheckpointDetector.java` | Create | Unit tests for required/optional checkpoint detection and sticky ordering. |
| `src/test/java/com/openggf/tests/trace/s3k/TestS3kElasticWindowController.java` | Create | Unit tests for entry, exit, drift budget, and re-anchor behavior. |
| `src/test/java/com/openggf/tests/trace/s3k/TestS3kAizTraceReplay.java` | Create | Authoritative default-suite replay test against the real fixture. |
| `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/` | Create | Authoritative BK2 + `metadata.json` + `physics.csv` + `aux_state.jsonl`. |
| `CHANGELOG.md` | Modify | Record the S3K end-to-end trace fixture landing. |

## Task 1: Freeze Recorder And Replay Signal Sources

**Files:**
- Create or replace: `docs/superpowers/research/2026-04-21-s3k-trace-addresses.md`
- Modify if duplicated: `docs/superpowers/research/2026-04-20-s3k-trace-addresses.md`

- [ ] **Step 1: Resolve the missing checkpoint signals from the disassembly and engine mirrors**

Run:

```powershell
rg -n "Apparent_act|Apparent_zone_and_act|Current_zone_and_act|Ctrl_1_locked|move_lock|AfterBoss_AIZ|Results|signpost|fire" `
  docs/skdisasm/sonic3k.constants.asm `
  docs/skdisasm/sonic3k.asm `
  src/main/java/com/openggf/level/LevelManager.java `
  src/main/java/com/openggf/game/sonic3k `
  src/main/java/com/openggf/game/sonic3k/objects
```

Expected: exact label locations for `Apparent_act`, `Game_mode`, `Ctrl_1_locked`, player `move_lock`, and the AIZ/HCZ transition logic the recorder and replay detector can both key from.

- [ ] **Step 2: Write the frozen checkpoint table**

Create the research note with this structure and fill it only with the signals confirmed in Step 1:

```md
## Required Checkpoints

| Checkpoint | Recorder-side signal | BizHawk offset(s) | Replay-side engine mirror | Evidence |
| --- | --- | --- | --- | --- |
| `intro_begin` | BK2 frame 0 | n/a | replay bootstrap frame 0 | spec anchor |
| `gameplay_start` | `Game_mode == $0C && move_lock == 0 && Ctrl_1_locked == 0` | `$F600`, player+`$32`, `$F7CA` | `LevelManager` gameplay + player control unlock | disasm + engine |
| `aiz1_fire_transition_begin` | exact AIZ fire-transition signal resolved in Step 1 | Step 1 resolved offset(s) | exact engine event/state mirror resolved in Step 1 | disasm + engine |
| `aiz2_reload_resume` | `actual_act == 1 && apparent_act == 0` immediately after reload | `$FE15`, `Apparent_act` | `LevelManager.getCurrentAct()` + `getApparentAct()` | disasm + engine |
| `aiz2_main_gameplay` | `actual_act == 1 && move_lock == 0` | `$FE15`, player+`$32` | `LevelManager` + player state | disasm + engine |
| `hcz_handoff_begin` | exact AIZ→HCZ transition signal resolved in Step 1 | Step 1 resolved offset(s) | exact engine event/state mirror resolved in Step 1 | disasm + engine |
| `hcz_handoff_complete` | HCZ actual zone + act 0 + `move_lock == 0` | zone/act + player+`$32` | `LevelManager` + player state | disasm + engine |
```

- [ ] **Step 3: Freeze the same-frame ordering and optional-checkpoint policy in the note**

Add this section verbatim to the research note so recorder and replay implementation use the same contract:

```md
## Emission And Detection Order

Within a single frame, recorder emission order is:

1. `zone_act_state`
2. `checkpoint`
3. existing object / routine / mode diagnostics in fixed detector order

Optional checkpoints (`aiz2_signpost_begin`, `aiz2_results_begin`) are diagnostics-only.
They may appear in recorder output and replay diagnostics, but they never drive elastic-window entry, exit, or pass/fail decisions.
```

- [ ] **Step 4: Reconcile the research file path with the spec**

Run:

```powershell
rg -n "2026-04-21-s3k-trace-addresses|2026-04-20-s3k-trace-addresses" docs/superpowers/specs docs/superpowers/plans docs/superpowers/research
```

Expected: all forward references point at `docs/superpowers/research/2026-04-21-s3k-trace-addresses.md`. If the `2026-04-20` file is only an interim draft, move its confirmed content into the `2026-04-21` note and remove or stop referencing the stale path.

- [ ] **Step 5: Commit the research freeze**

Run:

```bash
git add docs/superpowers/research/2026-04-21-s3k-trace-addresses.md docs/superpowers/specs/2026-04-21-s3k-end-to-end-trace-fixture-design.md
git commit -m "docs: freeze s3k trace checkpoint signals"
```

Expected: one documentation-only commit that locks the signal contract before code changes.

## Task 2: Add Typed Checkpoint And Zone/Act Aux Events

**Files:**
- Modify: `src/test/java/com/openggf/tests/trace/TraceEvent.java`
- Modify: `src/test/java/com/openggf/tests/trace/TraceData.java`
- Modify: `src/test/java/com/openggf/tests/trace/TraceEventFormatter.java`
- Modify: `src/test/java/com/openggf/tests/trace/TestTraceDataParsing.java`
- Modify: `src/test/java/com/openggf/tests/trace/TestTraceEventFormatting.java`

- [ ] **Step 1: Write failing parser and formatter tests first**

Add these tests:

```java
@Test
void parsesCheckpointEvent() {
    TraceEvent event = TraceEvent.parseJsonLine(
        """
        {"frame":1200,"event":"checkpoint","name":"aiz2_main_gameplay","actual_zone_id":0,"actual_act":1,"apparent_act":0,"game_mode":12}
        """.trim(),
        mapper);

    assertInstanceOf(TraceEvent.Checkpoint.class, event);
}

@Test
void parsesZoneActStateEvent() throws IOException {
    TraceData data = TraceData.load(Path.of("src/test/resources/traces/synthetic/basic_3frames"));
    TraceEvent.ZoneActState state = new TraceEvent.ZoneActState(12, 0, 1, 0, 12);
    assertEquals(0, state.actualZoneId());
}
```

Run:

```bash
mvn -Dtest=TestTraceDataParsing,TestTraceEventFormatting test
```

Expected: FAIL because `TraceEvent` does not yet have typed `Checkpoint` or `ZoneActState` records.

- [ ] **Step 2: Add the new typed aux events to `TraceEvent`**

Implement records and parse branches along these lines:

```java
record Checkpoint(
        int frame,
        String name,
        Integer actualZoneId,
        Integer actualAct,
        Integer apparentAct,
        Integer gameMode,
        String notes) implements TraceEvent {}

record ZoneActState(
        int frame,
        Integer actualZoneId,
        Integer actualAct,
        Integer apparentAct,
        Integer gameMode) implements TraceEvent {}
```

Use nullable `Integer` payloads so `intro_begin` can preserve `null` values before gameplay state is observable.

- [ ] **Step 3: Add `TraceData` lookup helpers for replay diagnostics**

Add helpers that scan backward by frame:

```java
public TraceEvent.Checkpoint latestCheckpointAtOrBefore(int frame) { ... }

public TraceEvent.ZoneActState latestZoneActStateAtOrBefore(int frame) { ... }
```

Keep the existing `eventsByFrame` structure; do not build a second aux index yet.

- [ ] **Step 4: Add compact formatter output for the new event types**

Add summary strings that remain short enough for context windows:

```java
case TraceEvent.Checkpoint checkpoint ->
        String.format("cp %s z=%s a=%s ap=%s gm=%s",
                checkpoint.name(),
                nullableInt(checkpoint.actualZoneId()),
                nullableInt(checkpoint.actualAct()),
                nullableInt(checkpoint.apparentAct()),
                nullableInt(checkpoint.gameMode()));
case TraceEvent.ZoneActState state ->
        String.format("zoneact z=%s a=%s ap=%s gm=%s",
                nullableInt(state.actualZoneId()),
                nullableInt(state.actualAct()),
                nullableInt(state.apparentAct()),
                nullableInt(state.gameMode()));
```

- [ ] **Step 5: Run the parser/formatter tests again**

Run:

```bash
mvn -Dtest=TestTraceDataParsing,TestTraceEventFormatting test
```

Expected: PASS. New tests confirm typed parsing, null-safe payload handling, and readable summaries.

- [ ] **Step 6: Commit the aux-event model**

Run:

```bash
git add src/test/java/com/openggf/tests/trace/TraceEvent.java src/test/java/com/openggf/tests/trace/TraceData.java src/test/java/com/openggf/tests/trace/TraceEventFormatter.java src/test/java/com/openggf/tests/trace/TestTraceDataParsing.java src/test/java/com/openggf/tests/trace/TestTraceEventFormatting.java
git commit -m "test: add s3k checkpoint aux event types"
```

## Task 3: Build A Pure Replay-Side Checkpoint Detector And Elastic Controller

**Files:**
- Create: `src/test/java/com/openggf/tests/trace/s3k/S3kReplayCheckpointDetector.java`
- Create: `src/test/java/com/openggf/tests/trace/s3k/S3kElasticWindowController.java`
- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kReplayCheckpointDetector.java`
- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kElasticWindowController.java`

- [ ] **Step 1: Write failing unit tests for the detector contract**

Add tests that prove:

```java
@Test
void introBeginEmitsAtReplayFrameZero() { ... }

@Test
void requiredCheckpointsEmitOnceInFixedOrder() { ... }

@Test
void optionalCheckpointsAreReportedButNotRequired() { ... }

@Test
void hczHandoffCompleteRequiresZoneActAndMoveLockClear() { ... }
```

The tests should drive the detector with a pure probe record rather than a live engine object, so the logic is deterministic and cheap to run.

- [ ] **Step 2: Define the probe and checkpoint-hit model**

Use a small immutable probe instead of reading engine singletons in the detector:

```java
record S3kCheckpointProbe(
        int replayFrame,
        Integer actualZoneId,
        Integer actualAct,
        Integer apparentAct,
        Integer gameMode,
        int moveLock,
        boolean fireTransitionActive,
        boolean hczTransitionActive,
        boolean signpostActive,
        boolean resultsActive) {}
```

Return typed hits that reuse the same payload shape as the aux `Checkpoint` record.

- [ ] **Step 3: Implement sticky checkpoint detection in fixed evaluation order**

Implement the detector with ordered predicate checks, not hash iteration:

```java
private static final List<String> REQUIRED_ORDER = List.of(
        "intro_begin",
        "gameplay_start",
        "aiz1_fire_transition_begin",
        "aiz2_reload_resume",
        "aiz2_main_gameplay",
        "hcz_handoff_begin",
        "hcz_handoff_complete");
```

The detector should:

- emit at most one copy of each named checkpoint
- allow optional checkpoint hits for diagnostics
- never read `TraceData` or trace aux files

- [ ] **Step 4: Write failing controller tests for elastic-window behavior**

Add controller tests that prove:

```java
@Test
void windowEntryKeepsStrictValidationOnEntryFrame() { ... }

@Test
void windowExitReanchorsStrictComparisonAtRecordedExitPlusOne() { ... }

@Test
void driftBudgetFailureTriggersStructuralDivergence() { ... }

@Test
void optionalCheckpointDoesNotOpenOrCloseAWindow() { ... }
```

- [ ] **Step 5: Implement the two-cursor controller**

Use separate drive and strict cursors:

```java
record ReplayCursorState(
        int driveTraceIndex,
        int strictTraceIndex,
        boolean strictComparisonEnabled) {}
```

Controller rules:

- outside elastic windows, `driveTraceIndex == strictTraceIndex`
- inside a window, `driveTraceIndex` advances every engine tick for BK2 input and execution-model selection
- `strictTraceIndex` does not compare rows again until the expected exit checkpoint is hit
- on exit, both cursors snap to `recordedExitCheckpointFrame + 1`
- if the engine consumes fewer or more ticks than the trace span, input-pointer discontinuity is accepted as diagnostic-only behavior for this fixture

- [ ] **Step 6: Run the detector/controller unit tests**

Run:

```bash
mvn -Dtest=TestS3kReplayCheckpointDetector,TestS3kElasticWindowController test
```

Expected: PASS. This gives a fast, deterministic proof that the elastic-window rules work before touching the end-to-end harness.

- [ ] **Step 7: Commit the detector/controller layer**

Run:

```bash
git add src/test/java/com/openggf/tests/trace/s3k/S3kReplayCheckpointDetector.java src/test/java/com/openggf/tests/trace/s3k/S3kElasticWindowController.java src/test/java/com/openggf/tests/trace/s3k/TestS3kReplayCheckpointDetector.java src/test/java/com/openggf/tests/trace/s3k/TestS3kElasticWindowController.java
git commit -m "test: add s3k elastic replay controller"
```

## Task 4: Integrate Elastic Replay And Aux-Aware Divergence Reporting

**Files:**
- Modify: `src/test/java/com/openggf/tests/trace/TraceBinder.java`
- Modify: `src/test/java/com/openggf/tests/trace/DivergenceReport.java`
- Modify: `src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java`
- Modify: `src/test/java/com/openggf/tests/trace/TestDivergenceReport.java`

- [ ] **Step 1: Write failing report-context tests**

Add tests like:

```java
@Test
void summaryIncludesLatestCheckpointBeforeFirstError() { ... }

@Test
void jsonIncludesLatestZoneActStateBeforeFirstError() { ... }

@Test
void contextWindowIncludesCheckpointAndZoneActLines() { ... }
```

Run:

```bash
mvn -Dtest=TestDivergenceReport test
```

Expected: FAIL because `DivergenceReport` has no access to aux-event context yet.

- [ ] **Step 2: Add a report-construction path that carries `TraceData`**

Keep the existing constructor for old tests, and add an overload:

```java
public DivergenceReport(List<FrameComparison> comparisons, TraceData trace) { ... }

public DivergenceReport buildReport(TraceData trace) {
    return new DivergenceReport(allComparisons, trace);
}
```

This preserves the current `TraceBinder.compareFrame(...)` contract while letting report rendering scan `TraceData` for the latest checkpoint and `zone_act_state`.

- [ ] **Step 3: Refactor the replay loop to use drive and strict cursors**

Replace the current `for (int i = 0; i < trace.frameCount(); i++)` loop with a `while` loop:

```java
int driveTraceIndex = 0;
TraceFrame previousDriveFrame = null;

while (driveTraceIndex < trace.frameCount()) {
    TraceFrame driveFrame = trace.getFrame(driveTraceIndex);
    TraceExecutionPhase phase = TraceExecutionModel.forGame(meta.game())
            .phaseFor(previousDriveFrame, driveFrame);
    int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
            ? fixture.skipFrameFromRecording()
            : fixture.stepFrameFromRecording();

    // validate BK2 input against driveFrame every tick
    // compare strictly only when the elastic controller says strict mode is active
    // otherwise advance only the drive cursor until the exit checkpoint is hit

    previousDriveFrame = driveFrame;
    driveTraceIndex = controller.nextDriveTraceIndex();
}
```

Important: inside elastic windows, continue validating BK2 input against the drive cursor; only `TraceBinder.compareFrame(...)` is suspended.

- [ ] **Step 4: Wire the S3K detector/controller only for S3K traces**

In `AbstractTraceReplayTest`, gate the new path on `meta.game().equals("s3k")`. Keep S1/S2 on the current strict path so this change does not risk existing replay coverage.

- [ ] **Step 5: Re-run harness and report tests**

Run:

```bash
mvn -Dtest=TestDivergenceReport,TestS3kReplayCheckpointDetector,TestS3kElasticWindowController,TestTraceDataParsing,TestTraceEventFormatting test
```

Expected: PASS. S3K elastic logic is now integrated without breaking the existing parser/report unit coverage.

- [ ] **Step 6: Commit the harness integration**

Run:

```bash
git add src/test/java/com/openggf/tests/trace/TraceBinder.java src/test/java/com/openggf/tests/trace/DivergenceReport.java src/test/java/com/openggf/tests/trace/AbstractTraceReplayTest.java src/test/java/com/openggf/tests/trace/TestDivergenceReport.java
git commit -m "test: integrate s3k elastic replay windows"
```

## Task 5: Finish The S3K Recorder And Import The Authoritative Fixture

**Files:**
- Modify: `tools/bizhawk/s3k_trace_recorder.lua`
- Modify: `tools/bizhawk/record_s3k_trace.bat`
- Modify: `tools/bizhawk/README.md`
- Modify: `docs/guide/contributing/trace-replay.md`
- Create: `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`

- [ ] **Step 1: Add an explicit end-to-end recorder profile**

Refactor the recorder start gating so the AIZ end-to-end fixture can begin at BK2 frame 0 instead of waiting for gameplay unlock:

```lua
local TRACE_PROFILE = "aiz_end_to_end"

local function should_start_recording()
    if TRACE_PROFILE == "aiz_end_to_end" then
        return emu.framecount() == 0
    end
    local ctrl_lock_timer = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local ctrl_locked = mainmemory.read_u8(ADDR_CTRL1_LOCKED)
    return game_mode == GAMEMODE_LEVEL and ctrl_lock_timer == 0 and ctrl_locked == 0
end
```

Keep the old gameplay-unlock path available for normal act-level S3K recording.

- [ ] **Step 2: Add deterministic `zone_act_state` and checkpoint emission**

Add recorder helpers that always emit in the same within-frame order:

```lua
local function emit_zone_act_state(frame, actual_zone_id, actual_act, apparent_act, game_mode) ... end
local function emit_checkpoint_once(name, payload) ... end
```

Use the frozen checkpoint signals from Task 1. Emit `intro_begin` unconditionally on recorded frame 0, emit `null` payloads when level state is not observable yet, and keep optional checkpoints report-only.

- [ ] **Step 3: Record the authoritative trace**

Run:

```powershell
tools\bizhawk\record_s3k_trace.bat `
  "Sonic and Knuckles & Sonic 3 (W) [!].gen" `
  "src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/s3k-aiz1-aiz2-sonictails.bk2"
```

Expected: `tools/bizhawk/trace_output/metadata.json`, `physics.csv`, and `aux_state.jsonl` are produced for the full intro-to-HCZ run.

- [ ] **Step 4: Import the generated files into the fixture directory**

Run:

```powershell
New-Item -ItemType Directory -Force src/test/resources/traces/s3k/aiz1_to_hcz_fullrun | Out-Null
Copy-Item tools/bizhawk/trace_output/metadata.json      src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/
Copy-Item tools/bizhawk/trace_output/physics.csv        src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/
Copy-Item tools/bizhawk/trace_output/aux_state.jsonl    src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/
```

Then verify the fixture shape:

```powershell
Get-ChildItem src/test/resources/traces/s3k/aiz1_to_hcz_fullrun
```

Expected: exactly four authoritative files in the directory: the BK2, `metadata.json`, `physics.csv`, and `aux_state.jsonl`.

- [ ] **Step 5: Update recorder documentation**

Add a short guide section showing:

```md
1. Keep the locked-on ROM at the repo root.
2. Run `tools/bizhawk/record_s3k_trace.bat <rom> <bk2>`.
3. Copy `tools/bizhawk/trace_output/{metadata.json,physics.csv,aux_state.jsonl}` into `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`.
4. Re-run `TestS3kAizTraceReplay`.
```

- [ ] **Step 6: Commit the recorder and fixture import**

Run:

```bash
git add tools/bizhawk/s3k_trace_recorder.lua tools/bizhawk/record_s3k_trace.bat tools/bizhawk/README.md docs/guide/contributing/trace-replay.md src/test/resources/traces/s3k/aiz1_to_hcz_fullrun
git commit -m "test: record s3k aiz to hcz replay fixture"
```

## Task 6: Add The Real Replay Test, Verify It, And Gate Parity Work

**Files:**
- Create: `src/test/java/com/openggf/tests/trace/s3k/TestS3kAizTraceReplay.java`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Add the authoritative replay test class**

Create:

```java
package com.openggf.tests.trace.s3k;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizTraceReplay extends AbstractTraceReplayTest {
    @Override protected SonicGame game() { return SonicGame.SONIC_3K; }
    @Override protected int zone() { return 0; }
    @Override protected int act() { return 0; }
    @Override protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun");
    }
}
```

- [ ] **Step 2: Run the focused replay stack**

Run:

```bash
mvn -Dtest=TestTraceDataParsing,TestTraceEventFormatting,TestDivergenceReport,TestS3kReplayCheckpointDetector,TestS3kElasticWindowController,TestS3kAizTraceReplay -Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen" -Dmse=off test
```

Expected:

- unit tests are green
- `TestS3kAizTraceReplay` either passes or produces `target/trace-reports/s3k_aiz1_report.json` and a matching context file

- [ ] **Step 3: If the replay is red, stop and use the report as the parity gate**

Use the first-error report, do not guess:

```powershell
Get-Content target/trace-reports/s3k_aiz1_report.json
Get-Content target/trace-reports/s3k_aiz1_context.txt
```

Expected: the report names the latest checkpoint and latest `zone_act_state` before the first divergence. Use that checkpoint boundary to decide which subsystem needs the next debugging plan.

- [ ] **Step 4: Update the changelog only once the replay test is green**

Add one entry summarizing:

```md
- Added the authoritative Sonic 3&K `aiz1_to_hcz_fullrun` trace fixture and default-suite replay test with checkpoint-aware elastic windows for the intro and fire-transition spans.
```

- [ ] **Step 5: Run the full default-suite gate**

Run:

```bash
mvn test -Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen" -Dmse=off
```

Expected: green default suite with `TestS3kAizTraceReplay` discovered normally. No `@Disabled`, no Surefire exclusion, no manual-only path.

- [ ] **Step 6: Commit the test landing**

Run:

```bash
git add src/test/java/com/openggf/tests/trace/s3k/TestS3kAizTraceReplay.java CHANGELOG.md
git commit -m "test: add s3k end-to-end trace replay"
```

## Self-Review Checklist

- Spec coverage:
  - Research freeze: Task 1
  - Recorder completion: Task 5
  - Replay-side checkpoint detector and elastic windows: Tasks 3 and 4
  - Checkpoint and `zone_act_state` parsing/reporting: Tasks 2 and 4
  - Fixture recording/import: Task 5
  - Default-suite replay test and verification: Task 6
- Placeholder scan:
  - No `TBD`, `TODO`, or “handle later” steps remain.
  - The one deliberate stop condition is parity work after the first real replay failure; that work must be driven by the concrete divergence report, not guessed in advance.
- Type consistency:
  - Use `TraceEvent.Checkpoint` / `TraceEvent.ZoneActState` everywhere for typed aux events.
  - Use the two-cursor controller terms `driveTraceIndex` and `strictTraceIndex` consistently.
