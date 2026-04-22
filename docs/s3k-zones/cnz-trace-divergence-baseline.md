# CNZ Trace Replay - Baseline Divergence Report

Captured: 2026-04-22
Test: `TestS3kCnzTraceReplay`
Trace: `src/test/resources/traces/s3k/cnz/`
Recorder: lua v3.1-s3k, profile `level_gated_reset_aware`

## Summary

- Total frames in trace: 42253
- First diverging frame: 1
- First diverging field: `x_speed`
- Total errors: 1635
- Total warnings: 1090

Latest checkpoint at failure: `gameplay_start z=3 a=0 ap=0 gm=12`
Latest zone/act state: `zoneact z=3 a=0 ap=0 gm=12`

The test fails almost immediately: at frame 0 the engine's Sonic is placed at the
recorder's start position `(0x0018, 0x0600)` with `x_speed=0, y_speed=0`, but the
ROM trace shows Sonic being carried eastward by Tails with `x_speed=0x0100` and
`y_speed` ramping up through the carry drop. The engine never applies the
Tails-carry intro, so the player immediately falls on its own trajectory and
cascades divergences through the rest of CNZ1.

Report filenames produced by the runtime are `s3k_cnz1_*` (derived from
`metadata.zone="cnz"` and `metadata.act=1`); they are archived here under the
task-spec-canonical `s3k_0300_*` names (zone id 3 decimal + act 0 zero-based) so
the plan's filenames match.

## First 20 divergences

| # | Frame (start) | Frame (end) | Field | Expected | Actual | Cascading | Likely cause |
| --- | ------------- | ----------- | ------- | --------- | --------- | --------- | ------------ |
| 1 | 1 | 196 | `x_speed` | `0x0100` | `0x0000` | false | C |
| 2 | 2 | 105 | `y_speed` | `0x0010` | `0x00A8` | true | C |
| 3 | 43 | 105 | `air` | `1` | `0` | true | C |
| 4 | 106 | 198 | `g_speed` | `0x0148` | `0x0000` | true | C |
| 5 | 107 | 107 | `y_speed` | `-0100` | `0x0000` | true | C |
| 6 | 193 | 198 | `y_speed` | `0x0000` | `0x0530` | true | C |
| 7 | 193 | 198 | `air` | `0` | `1` | true | C |
| 8 | 193 | 198 | `rolling` | `0` | `1` | true | C |
| 9 | 203 | 222 | `angle` | `0x0000` | `0x00FA` | true | C |
| 10 | 204 | 223 | `y_speed` | `0x0000` | `-00D0` | true | C |
| 11 | 210 | 215 | `x_speed` | `0x0600` | `0x0565` | false | C |
| 12 | 224 | 225 | `angle` | `0x0000` | `0x00FC` | true | C |
| 13 | 225 | 226 | `y_speed` | `0x0000` | `-0096` | true | C |
| 14 | 235 | 274 | `y_speed` | `-0610` | `-0700` | true | C |
| 15 | 250 | 266 | `x_speed` | `0x0579` | `0x0600` | false | C |
| 16 | 270 | 277 | `g_speed` | `0x0522` | `0x0600` | true | C |
| 17 | 270 | 281 | `air` | `0` | `1` | true | C |
| 18 | 270 | 352 | `rolling` | `0` | `1` | true | C |
| 19 | 274 | 286 | `x_speed` | `0x0552` | `0x04CA` | false | C |
| 20 | 280 | 363 | `y_speed` | `0x0000` | `0x00A8` | true | C |

Workstream-cause legend:
- **C** - Tails-carry intro (first ~300-600 frames of CNZ1)
- **D** - CNZ1 mini-boss
- **E** - CNZ2 end-boss
- **F** - Knuckles cutscenes
- **G** - Stragglers (generic object parity, pattern animator, palette cycling)

## Raw reports

- `docs/s3k-zones/cnz-trace-divergence-baseline.d/s3k_0300_report.json`
- `docs/s3k-zones/cnz-trace-divergence-baseline.d/s3k_0300_context.txt`

## Likely causes (hypothesis - verify during C-G workstreams)

- **Frames 1-~300: Tails-carry intro -> workstream C.** Frame 1's `x_speed`
  mismatch (`0x0100` expected, `0x0000` actual) is the dead give-away: the ROM
  starts with Sonic riding Tails's grasp, moving right at constant speed while
  Tails descends. The engine spawns Sonic free-standing at
  `(0x0018, 0x0600)` instead of being carried, so every field that depends on
  the intro arc diverges. All 20 first divergences live inside this window and
  are flagged `C`.
- **Remaining ~1615 errors:** will need triage after workstream C fixes the
  intro. Early evidence (from the broader JSON) suggests large spans of cascaded
  divergences extending into at least frame 654 (`x_speed` span 292-654,
  363 frames). The test only reached a portion of the 42253 trace frames before
  hitting its error threshold, so later divergences (CNZ1 mini-boss around
  camera X ~0x3000, CNZ2 arena post-act-transition ~16669-17276, Knuckles
  cutscenes, stragglers) are still unrecorded in this baseline - the report
  cuts off after the engine starts diverging in the first second of play.

## Next step

Dispatch workstream agents per the top-level spec's section 7. Workstream C
(Tails-carry intro) is the clear first priority: fix it, re-run this test, and
the baseline's "first-20" plus most of the cascaded errors should collapse.
Once C is green, re-capture the baseline so D-G have a clean divergence window
to work from.
