# S3K AIZ Intro Parity Gate Design

## Goal

Define the immediate gate for Sonic 3&K AIZ replay parity work so the branch
does not mix harness defects with real engine parity bugs.

The next step is not "fix the whole AIZ to HCZ run." The next step is to make
the authoritative BK2-backed replay gate trustworthy, then use that gate to
drive parity only through the AIZ intro window.

## Current Evidence

The branch already contains a real BK2-backed end-to-end S3K fixture:

- trace directory: `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`
- BK2 movie: `s3-aiz1-2-sonictails.bk2`
- recorded checkpoints:
  - `intro_begin` at trace frame `0`
  - `gameplay_start` at trace frame `1500`
  - `aiz1_fire_transition_begin` at trace frame `1651`
  - `aiz2_reload_resume` at trace frame `5610`
  - `aiz2_main_gameplay` at trace frame `5610`

The most recent saved replay failure is structural, not a gameplay-field
divergence:

- `Elastic window drift budget exhausted for gameplay_start`
- saved engine checkpoints show:
  - `intro_begin` at replay frame `0`
  - `gameplay_start` at replay frame `1528`

That means the current gate is failing inside the first elastic window before
normal post-intro gameplay becomes a reliable sync target.

There is also a spec/implementation mismatch in the replay harness:

- the committed design says elastic windows are:
  - `intro_begin -> gameplay_start`
  - `aiz1_fire_transition_begin -> aiz2_main_gameplay`
- the current controller implementation uses:
  - `intro_begin -> gameplay_start`
  - `gameplay_start -> aiz2_main_gameplay`

This mismatch must be resolved before using the replay result to make claims
about intro parity or later-run parity.

## Decision

Use a two-stage gate:

1. Fix the replay-window/controller contract so it matches the committed S3K
   trace-fixture design.
2. Re-run the authoritative BK2 replay and treat only the `intro_begin ->
   gameplay_start` span as the active parity target until that window either
   passes cleanly or fails with a concrete first divergence.

This is the recommended path because it removes one known false signal first.
Without that correction, intro debugging risks chasing a harness defect instead
of a ROM-parity bug.

## Required Contract

The replay harness must follow these rules:

- elastic window 1 opens on recorded `intro_begin` and closes on
  `gameplay_start`
- elastic window 2 opens on recorded `aiz1_fire_transition_begin` and closes on
  `aiz2_main_gameplay`
- `aiz1_fire_transition_begin` is a real semantic checkpoint
- optional checkpoints remain diagnostics-only and never act as elastic-window
  anchors
- replay remains strict outside elastic windows
- a structural failure inside a window is valid, but it must reflect the
  correct checkpoint pairing

## Investigation Scope After Harness Alignment

Once the window contract matches the design, the next parity loop is:

1. Run the authoritative S3K replay test.
2. If it fails before or at `gameplay_start`, treat AIZ intro parity as the
   immediate engine task.
3. Use BK2 playback, trace checkpoints, probe output, and disassembly to find
   the first real divergence inside the intro window.
4. Fix only the root cause needed to move the first red point forward.
5. Re-run the replay and repeat until the test gets past `gameplay_start` or
   produces a new first divergence after that checkpoint.

## Out Of Scope

This gate explicitly does not include:

- fixing the full AIZ to HCZ replay in one pass
- post-intro AIZ gameplay parity work before the intro window is trustworthy
- fire-transition parity work before the intro window is either green or proven
  no longer first-red
- splitting the authoritative fixture into smaller committed fixtures
- weakening the replay contract to ignore real mismatches

## Diagnostics To Prefer

When the replay is still red after harness alignment, prefer evidence in this
order:

1. the first replay failure and latest checkpoint boundary
2. replay probe output around the failing window
3. trace `aux_state.jsonl` checkpoint/state events
4. disassembly-backed AIZ intro behavior docs

Do not diagnose later-run drift until the earliest failing checkpoint window is
understood.

## Success Criteria

This gate is complete only when one of these is true:

- the authoritative replay gets past `gameplay_start` without structural window
  failure, or
- the authoritative replay fails with a concrete first divergence report tied to
  the intro window rather than a controller-budget error

Implementation planning may begin after this spec is approved. The first plan
must treat replay-window alignment as part of the gate, not as optional cleanup.
