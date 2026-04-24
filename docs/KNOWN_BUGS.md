# Known Bugs and Unfinished Work (Engine-Wide)

This document tracks **bugs**, incomplete implementations, and known parity gaps that we intend to fix but haven't addressed yet. Entries here are *not* intentional — they're acknowledged problems with a plan (or hope) of eventual resolution.

For **intentional** deviations from the original ROMs (architectural choices, feature extensions, deliberate bug-fixes of ROM data), see [KNOWN_DISCREPANCIES.md](KNOWN_DISCREPANCIES.md).

Entries should include:
- **Location** — the file(s) where the bug lives, if known
- **Symptom** — what goes wrong and where you can observe it (test name, trace frame, manual repro)
- **Suspected cause** — best current theory, with ROM/disasm references when relevant
- **Removal condition** — what needs to be true for this entry to be deleted

---

## Table of Contents

1. [Trace Replay Recorder Coverage (Schema v3 Rollout)](#trace-replay-recorder-coverage-schema-v3-rollout)

---

## Trace Replay Recorder Coverage (Schema v3 Rollout)

**Location:** `src/test/java/com/openggf/tests/trace/*`, `tools/bizhawk/*`

### Symptom

BK2-derived fixture coverage is still incomplete across the full replay suite. Sonic 1 ships with established v3-native BK2 traces, and this branch also contains a Sonic 2 BK2-derived trace directory, but there is still no committed Sonic 3&K BK2-derived gameplay fixture driving replay parity in CI. Older or pre-v3 traces can therefore still reach the legacy heuristic path when they are loaded.

### Current State

The shared replay harness now understands schema v3 execution counters and uses `gameplay_frame_counter` plus `vblank_counter` when those columns are present. The Sonic 1, Sonic 2, and Sonic 3&K BizHawk recorders all emit schema v3, and committed synthetic v3 fixtures exercise the per-game metadata acceptance paths.

`TraceData` now logs a one-shot notice when a pre-v3 trace directory is loaded so the fallback is visible during test runs.

### Removal Condition

Remove this entry once at least one real BK2-derived Sonic 3&K fixture is checked in alongside the existing S1/S2 coverage, a replay test parses it as schema v3, and the legacy fallback path in `TraceExecutionModel` / `TraceData` is deleted.
