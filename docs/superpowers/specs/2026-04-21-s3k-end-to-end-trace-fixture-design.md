# S3K End-to-End Trace Fixture Design

## Goal

Add a real Sonic 3&K trace fixture and replay test built from the BK2 movie
`s3k-aiz1-aiz2-sonictails.bk2`, covering the continuous path from the Angel Island
intro cutscene through AIZ1 gameplay, the seamless fire-curtain transition into
AIZ2, the rest of Angel Island, and the outro handoff into Hydrocity.

The fixture is explicitly end-to-end. It is not split into independent segment
tests, because the main value is proving that the seamless transition path works
correctly under one continuous BK2 and one continuous ROM trace.

## Why This Needs More Than A Normal Trace

S3K AIZ is not a simple zone/act replay:

- The run begins in intro/cutscene state before normal gameplay.
- The AIZ1 fire sequence reloads AIZ2 art and progression state mid-run.
- During that transition, the engine and ROM intentionally distinguish current
  gameplay progression state from the apparent act presentation state.
- The fixture should continue past the player-controlled section and through
  the HCZ handoff/cutscene frames, because the zone transition logic is part of
  what we are validating.

The trace therefore needs explicit phase/checkpoint annotations so failures can
be understood in terms of the last clean phase boundary reached, even though any
divergence still fails the test.

## Recommended Approach

Use one authoritative fixture directory containing:

- `s3k-aiz1-aiz2-sonictails.bk2`
- `metadata.json`
- `physics.csv`
- `aux_state.jsonl`

The recorder remains responsible for producing the authoritative trace files.
The replay harness remains strict: any divergence is a test failure. The only
new diagnostic layer is recorder-emitted checkpoint metadata inside
`aux_state.jsonl` plus replay-report plumbing that exposes the latest
checkpoint reached before divergence.

This keeps the continuous replay contract intact while making long-run failures
diagnosable.

## Checkpoint Model

### Event Type

Add a new aux event type for S3K:

```json
{
  "frame": 1234,
  "event": "checkpoint",
  "name": "aiz1_fire_transition_begin",
  "actual_zone_id": 0,
  "actual_act": 0,
  "apparent_act": 0,
  "game_mode": 12,
  "notes": "optional short recorder note"
}
```

The important rule is that checkpoint payloads must carry:

- `actual_*`: current gameplay zone/act state
- `apparent_act`: title-card / presentation-side act state

In S3K this is ROM-backed, not engine-invented: the recorder must read the ROM
RAM label `Apparent_act` directly. The engine's `LevelManager.getApparentAct()`
is the Java-side mirror of that ROM state. There is no separate apparent-zone
RAM surface for this fixture, so checkpoint and audit events track:

- `actual_zone_id`
- `actual_act`
- `apparent_act`

For this fixture, `actual_zone_id` is authoritative for zone progression. The
important divergence window is AIZ's fire transition, where `currentAct` has
advanced to AIZ2 continuation state while `apparentAct` intentionally remains
at the prior act presentation value. Outside transition/signpost/results
windows, `actual_act` and `apparent_act` are expected to be equal.

For intro frames that occur before the level state is observable, these fields
must be emitted as `null`, not guessed defaults.

### Why A New Event Type

`routine_change` and `mode_change` already exist, but they are too low-level to
act as stable replay milestones:

- they do not carry zone/act progression state
- they are tied to object/control behavior, not test-phase meaning
- they are not named in terms a replay report can surface directly

`checkpoint` is therefore a separate semantic event type whose job is to mark
human-meaningful milestones in the end-to-end run.

### Required Checkpoints

The initial implementation should emit these named checkpoints:

- `intro_begin`
- `gameplay_start`
- `aiz1_fire_transition_begin`
- `aiz2_reload_resume`
- `aiz2_main_gameplay`
- `hcz_handoff_begin`
- `hcz_handoff_complete`

Optional checkpoints may be added only when they can be detected from explicit
ROM/runtime state that would be present in future runs too:

- `aiz2_signpost_begin`
- `aiz2_results_begin`

### Emission Rules

Checkpoint emission must be sparse and deterministic:

- Emit once when a phase boundary is first crossed.
- Do not emit every frame while a phase remains active.
- Detection should be based on ROM/runtime state already used by the engine or
  recorder, not on brittle frame constants.
- Prefer explicit state transitions already represented by S3K event logic over
  camera-position guesses when both are available.

### Required Checkpoint Signals

Each required checkpoint must be keyed from a concrete source signal:

| Checkpoint | Source signal |
| --- | --- |
| `intro_begin` | First recorded frame of the BK2. This fixture records from movie start, not from gameplay unlock. |
| `gameplay_start` | First frame where S3K is in level gameplay and the player control-lock timer reaches zero. |
| `aiz1_fire_transition_begin` | Rising edge of the dedicated AIZ fire-transition state becoming active. This must be keyed from the same ROM-visible/event-driven transition state the engine models, not from camera position. |
| `aiz2_reload_resume` | First frame after the seamless reload where `actual_act == 1` while ROM `Apparent_act == 0`. This is the main act-divergence checkpoint. |
| `aiz2_main_gameplay` | First post-reload frame where `actual_act == 1` and normal gameplay control is restored (`move_lock == 0`). |
| `hcz_handoff_begin` | First frame where the AIZ-to-HCZ transition path becomes active, keyed from actual zone progression / transition state rather than cutscene timing constants. |
| `hcz_handoff_complete` | First frame where HCZ is the actual zone and the seamless handoff has completed enough that the run is stably inside HCZ state. |

The exact RAM labels/addresses behind these signals are a required research
deliverable before implementation. The implementation plan must freeze them in
a companion research note before recorder code is finalized.

## Recorder Design

### Fixture Scope

Create an S3K recorder flow parallel to the existing S1/S2 workflow, but the
fixture format stays the same v3 contract:

- `game = "s3k"`
- `trace_schema = 3`
- `csv_version = 4`

No physics.csv schema expansion is allowed for this work. All S3K-specific
phase/checkpoint data lives in `aux_state.jsonl`.

For a fixture that starts before gameplay, the recorder must support recording
from BK2 frame 0 instead of waiting for the gameplay-unlock trigger used by the
normal act-level recorders. `gameplay_start` becomes a checkpoint inside the
trace, not the recording start condition.

### State Tracking

The S3K recorder must track enough state to emit meaningful checkpoints:

- gameplay-frame counter
- vblank counter
- lag counter
- actual zone/act
- apparent act
- game mode / cutscene state if available

In concrete engine terms, this means:

- `actual_zone_id` / `actual_act`: the current zone/act progression state
- `apparent_act`: ROM `Apparent_act` as recorded from emulator RAM

There is no distinct apparent zone surface in the engine today, so the trace
does not invent one.

The recorder should also emit a `zone_act_state` diagnostic event whenever any
of these values changes, even if that change does not map to a named
checkpoint. This gives a low-level audit trail beneath the semantic checkpoint
events without spamming every frame.

Suggested payload:

```json
{
  "frame": 1200,
  "event": "zone_act_state",
  "actual_zone_id": 0,
  "actual_act": 1,
  "apparent_act": 0
}
```

`game_mode` must be emitted as the raw integer game-mode byte read from ROM RAM
(`Game_mode`), not as a hex string. If a field is not yet observable at the
start of the intro recording, emit `null`.

`zone_act_state` is an edge-triggered audit event, not a debounced semantic
checkpoint. Emit it on every observed change of `actual_zone_id`,
`actual_act`, or `apparent_act`, including transient but real ROM state
changes. Checkpoints remain the stable, human-meaningful layer.

### Determinism Requirement

The recorder path must be deterministic on repeated runs of the same BK2:

- re-recording the same BK2 twice with the same BizHawk/core version must
  produce byte-identical `physics.csv`
- `metadata.json` must record:
  `bizhawk_version`, `genesis_core`, and a non-empty `rom_checksum`

This is required because the fixture spans seamless reload behavior and needs a
replayable emulator baseline.

## Replay Test Design

### Test Shape

Add one real replay test for the full fixture:

- one BK2
- one trace directory
- one replay pass
- any divergence causes test failure

The test should not stop at the AIZ1/AIZ2 boundary or at loss of control. It
must continue through the entire recorded HCZ handoff.

### CI Stance

The first real S3K end-to-end replay test is expected to be diagnostic-only
until parity is good enough for green CI. It must not make default `mvn test`
red.

Initial merge stance:

- the fixture itself may land immediately
- the real replay test lands as manual / opt-in coverage, following the
  existing replay-suite pattern rather than the default green test set

The implementation plan must state the exact mechanism used (`pom.xml`
exclusion, explicit manual test naming, or equivalent), but the outcome is
fixed: this fixture may not degrade default CI status while it is still
expected to diverge. It only graduates into the default green test set after it
passes end-to-end on the pinned BizHawk/core environment.

### Reporting Behavior

When a replay fails, the report should include:

- the first failing frame as it already does
- the latest checkpoint reached at or before that frame
- the most recent `zone_act_state` event at or before that frame

This should be implemented by scanning aux events backward from the first error
frame at report-render time. It does not require changing the core
`TraceBinder` comparison contract or stuffing checkpoint state into every
`FrameComparison`.

Visible contract:

- `DivergenceReport.toSummary()` appends the latest checkpoint before the first
  error when one exists
- `DivergenceReport.toJson()` adds top-level fields for
  `latest_checkpoint_before_first_error` and
  `latest_zone_act_state_before_first_error`
- `getContextWindow(centreFrame, radius)` includes the latest checkpoint and
  latest `zone_act_state` event at or before `centreFrame` whenever available

This is enough to answer:

- did we diverge before or after the fire transition started?
- did we survive the seamless reload into AIZ2?
- did we reach the HCZ handoff at all?

### Success Criteria

The end-to-end fixture is considered structurally valid when:

- the recorder can capture the full BK2 without manual intervention
- `TraceData.load(...)` accepts the generated files as schema v3 S3K data
- the replay harness can consume the full fixture directory and produce
  checkpoint-aware diagnostics
- the fixture can be re-recorded deterministically under the pinned BizHawk/core
  environment

Physics parity is a separate question. The first version of this test may fail
on gameplay divergence; that is acceptable as long as the fixture and
diagnostic contract are correct.

## Files In Scope

Expected implementation files:

- `tools/bizhawk/s3k_trace_recorder.lua`
- `tools/bizhawk/record_s3k_trace.bat`
- `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`
- `src/test/java/com/openggf/tests/trace/s3k/...`
- trace-report formatting/parsing code under `src/test/java/com/openggf/tests/trace/`

Single-zone fixtures keep the existing `<zone><act>_fullrun/` naming.
Multi-zone end-to-end fixture directories use:

- `<start-zone><start-act>_to_<end-zone>_fullrun/`

For this fixture: `aiz1_to_hcz_fullrun/`

The implementation should reuse the current trace parser and replay structure
where possible. This design does not require a new file format or a new replay
engine.

## Non-Goals

- Do not split the authoritative fixture into multiple segment tests.
- Do not add S3K-only columns to `physics.csv`.
- Do not weaken replay strictness by allowing mismatches to continue as test
  success.
- Do not encode phase boundaries as hardcoded frame numbers in committed test
  code.

## Risks And Mitigations

### Risk: transition-state ambiguity

The AIZ fire sequence can make current and apparent act disagree.

Mitigation:
- always capture both actual and apparent act in checkpoint and zone-act events

### Risk: long-run failures remain opaque

A full-run test can fail early and hide later sections.

Mitigation:
- recorder-emitted checkpoints identify the last successfully reached phase

### Risk: overfitting checkpoint detection to one movie

If checkpoint detection depends on this exact run's camera timing, it will be
fragile.

Mitigation:
- key checkpoints off explicit game/event state when available

## Implementation Follow-On

The next step after this spec is an implementation plan that breaks the work
into:

1. S3K recorder completion
2. checkpoint and zone/act aux-event design
3. fixture recording and import
4. replay test and checkpoint-aware reporting
5. end-to-end verification on the real BK2
