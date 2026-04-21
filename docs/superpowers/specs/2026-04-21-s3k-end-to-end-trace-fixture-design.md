# S3K End-to-End Trace Fixture Design

## Goal

Add a real Sonic 3&K trace fixture and replay test built from the BK2 movie
`s3-aiz1&2-sonictails.bk2`, covering the continuous path from the Angel Island
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
  zone/act from apparent zone/act.
- The fixture should continue past the player-controlled section and through
  the HCZ handoff/cutscene frames, because the zone transition logic is part of
  what we are validating.

The trace therefore needs explicit phase/checkpoint annotations so failures can
be understood in terms of the last clean phase boundary reached, even though the
test still fails on first divergence.

## Recommended Approach

Use one authoritative fixture directory containing:

- `s3-aiz1&2-sonictails.bk2`
- `metadata.json`
- `physics.csv`
- `aux_state.jsonl`

The recorder remains responsible for producing the authoritative trace files.
The replay harness remains strict and fails on first mismatch. The only new
diagnostic layer is recorder-emitted checkpoint metadata inside `aux_state.jsonl`
plus replay-report plumbing that exposes the latest checkpoint reached before
divergence.

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
  "zone": "aiz",
  "actual_zone_id": 0,
  "actual_act": 0,
  "apparent_zone_id": 0,
  "apparent_act": 0,
  "game_mode": "...",
  "notes": "optional short recorder note"
}
```

The important rule is that checkpoint payloads must carry both:

- `actual_*`: ROM current zone/act state used for gameplay/runtime state
- `apparent_*`: title-card / presentation-side zone/act state

This is required for the AIZ fire transition, where AIZ2 tiles and progression
state can be active while the apparent act presentation has not fully advanced
the same way.

### Required Checkpoints

The initial implementation should emit these named checkpoints:

- `intro_begin`
- `gameplay_start`
- `aiz1_fire_transition_begin`
- `aiz2_reload_resume`
- `aiz2_main_gameplay`
- `hcz_handoff_begin`
- `hcz_handoff_complete`

Optional checkpoints may be added if the movie cleanly covers them and they are
easy to detect without fragile heuristics:

- `aiz2_signpost_begin`
- `aiz2_results_begin`
- `control_lost_for_handoff`

### Emission Rules

Checkpoint emission must be sparse and deterministic:

- Emit once when a phase boundary is first crossed.
- Do not emit every frame while a phase remains active.
- Detection should be based on ROM/runtime state already used by the engine or
  recorder, not on brittle frame constants.
- Prefer explicit state transitions already represented by S3K event logic over
  camera-position guesses when both are available.

## Recorder Design

### Fixture Scope

Create an S3K recorder flow parallel to the existing S1/S2 workflow, but the
fixture format stays the same v3 contract:

- `game = "s3k"`
- `trace_schema = 3`
- `csv_version = 4`

No physics.csv schema expansion is allowed for this work. All S3K-specific
phase/checkpoint data lives in `aux_state.jsonl`.

### State Tracking

The S3K recorder must track enough state to emit meaningful checkpoints:

- gameplay-frame counter
- vblank counter
- lag counter
- actual zone/act
- apparent zone/act
- game mode / cutscene state if available

The recorder should also emit a zone/act-state diagnostic event whenever either
actual or apparent zone/act changes, even if that change does not map to a
named checkpoint. This gives a low-level audit trail beneath the semantic
checkpoint events.

Suggested payload:

```json
{
  "frame": 1200,
  "event": "zone_act_state",
  "actual_zone_id": 0,
  "actual_act": 1,
  "apparent_zone_id": 0,
  "apparent_act": 0
}
```

## Replay Test Design

### Test Shape

Add one real replay test for the full fixture:

- one BK2
- one trace directory
- one replay pass
- first mismatch fails the test

The test should not stop at the AIZ1/AIZ2 boundary or at loss of control. It
must continue through the entire recorded HCZ handoff.

### Reporting Behavior

When a replay fails, the report should include:

- the first failing frame as it already does
- the latest checkpoint reached at or before that frame
- the most recent `zone_act_state` event at or before that frame

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
