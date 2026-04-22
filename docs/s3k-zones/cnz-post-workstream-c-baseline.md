# CNZ Trace Replay - Post-Workstream-C Baseline

Captured: 2026-04-22
Test: `TestS3kCnzTraceReplay`
After: workstream C (Tails-carry intro) merge at 45747e3ec555cef52c82ee3febd2decb44232521

## Summary

- Previous baseline: 1635 errors, first divergence frame 1
- Post-C baseline: 2547 errors, first divergence frame 0
- Improvement: -912 errors (regression — count rose by 912), first-frame shift -1 (frame 0 is now first, not frame 1)

## Notes on the regression

The carry trigger is applied **one frame too early**. In the ROM trace, frame 0 has
`x_speed=0x0000` (Sonic is still spawning), and the carry velocity (`x_speed=0x0100`)
only appears at frame 1. The engine's `Sonic3kCnzCarryTrigger` enters `CARRY_INIT`
on the first gameplay tick (frame 0), which copies Tails's velocity onto Sonic at
frame 0 — before ROM does. This reverses the frame-0 mismatch direction:

- **Pre-C (frame 1):** ROM expected `x_speed=0x0100`, engine had `0x0000` (engine not carrying)
- **Post-C (frame 0):** ROM expected `x_speed=0x0000`, engine has `0x0100` (engine carrying one frame early)

The off-by-one also cascades: the y-speed divergence at frame 3 (`0x0018` expected,
`0x00A8` actual) reflects the engine accumulating free-fall gravity for one extra frame
before the carry controller's gravity gate takes effect, producing a slightly different
descent arc throughout the carry window. A follow-up workstream D or a C patch should
investigate whether `Tails_CPU_routine` routine 0x0C fires after the first VBlank (i.e.,
starting on the _second_ gameplay frame), not on the first tick.

## First 20 divergences (post-C)

| # | Frame (start) | Field | Expected | Actual | Likely workstream |
|---|---------------|-------|----------|--------|-------------------|
| 1 | 0 | `x_speed` | `0x0000` | `0x0100` | C |
| 2 | 3 | `y_speed` | `0x0018` | `0x00A8` | C |
| 3 | 41 | `air` | `1` | `0` | C |
| 4 | 43 | `x_speed` | `0x0118` | `0x0000` | C |
| 5 | 106 | `g_speed` | `0x0148` | `0x0000` | C |
| 6 | 107 | `y_speed` | `-0100` | `0x0000` | C |
| 7 | 193 | `y_speed` | `0x0000` | `0x0530` | C |
| 8 | 193 | `air` | `0` | `1` | C |
| 9 | 193 | `rolling` | `0` | `1` | C |
| 10 | 198 | `angle` | `0x0000` | `0x00FA` | C |
| 11 | 199 | `x_speed` | `0x05D1` | `0x0549` | C |
| 12 | 199 | `y_speed` | `0x0000` | `-00C7` | C |
| 13 | 201 | `g_speed` | `0x05E9` | `0x0564` | C |
| 14 | 218 | `angle` | `0x0000` | `0x00FC` | C |
| 15 | 219 | `y_speed` | `0x0000` | `-0090` | C |
| 16 | 238 | `y_speed` | `-0568` | `-0700` | C |
| 17 | 250 | `x_speed` | `0x0579` | `0x0600` | C |
| 18 | 270 | `x_speed` | `0x0522` | `0x049F` | C |
| 19 | 270 | `g_speed` | `0x0522` | `0x0600` | C |
| 20 | 270 | `air` | `0` | `1` | C |

All 20 first divergences remain in workstream C territory (carry window, frames 0–270).
The carry trigger's off-by-one is responsible for the higher error count: the entire carry
arc is shifted by one frame, so carry-derived divergences that the original baseline had
at frames 1–196 (x_speed) now start at frames 0–43, with extra cascades introduced by
the premature gravity gate activation.

## Next dispatch

Based on the new first-20 distribution, workstreams applicable:
- [ ] C-patch (off-by-one carry trigger): **DISPATCH FIRST**. All 20 entries are still in
  C territory. The carry trigger fires one frame early. Suspect `Tails_CPU_routine` 0x0C
  initializes on the second gameplay frame (frame 1), not the first (frame 0). Patch
  `Sonic3kCnzCarryTrigger` to skip the first frame before entering `CARRY_INIT`.
  Re-run trace after the patch to verify first-divergence frame moves past 400 and total
  errors drop below 1000.
- [ ] D (CNZ1 mini-boss): **DEFER** until C-patch clears the carry window and exposes
  the mini-boss range in the first-20.
- [ ] E (CNZ2 end-boss): **DEFER** — not visible in the current first-20 (carry cascade
  dominates); dispatch after D.
- [ ] F (Knuckles cutscenes): **DEFER** — not visible in the current first-20; dispatch
  after C-patch.
- [ ] G (Stragglers): **DEFER** — post-C cascade still dominates; dispatch last.

## Wider guard status

Ran against commit 45747e3ec:

```
Tests run: 115, Failures: 0, Errors: 0, Skipped: 0
```

Tests included:
`TestS3kAiz1SkipHeadless`, `TestSonic3kLevelLoading`, `TestSonic3kBootstrapResolver`,
`TestSonic3kDecodingUtils`, `TestS3kCnzCarryHeadless`, `TestSidekickCpuControllerCarry`,
`TestSonic3kCnzCarryTrigger`, `TestObjectControlledGravity`, `TestPhysicsProfile`,
`TestCollisionModel`.

**Pre-existing failures** (not introduced by workstream C, not changed since before
workstream C):
- `TestPhysicsProfileRegression` (18 errors) — `RuntimeManager.createGameplay()` throws
  `EngineServices have not been configured` in this branch's test harness; no workstream C
  file touches its dependencies.
- `TestSpindashGating` (4 errors) — same `EngineServices` singleton configuration error;
  no workstream C file touches its dependencies.

Both pre-existing failures have the same root cause (singleton configuration gap in the
test harness) and are not regressions from this workstream.

## AIZ replay regression

`TestS3kAizTraceReplay` continues to fail on the pre-existing
`aiz1_fire_transition_begin` checkpoint issue documented in
`docs/s3k-zones/cnz-task7-regression-note.md`. Workstream H owns that
recovery and does not block this baseline.
