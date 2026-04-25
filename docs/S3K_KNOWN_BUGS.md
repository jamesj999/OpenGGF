# Known Bugs and Unfinished Work — Sonic 3 & Knuckles

This document tracks **Sonic 3 & Knuckles bugs**, incomplete implementations, and known parity gaps that we intend to fix but haven't addressed yet. Entries here are *not* intentional — they're acknowledged problems with a plan (or hope) of eventual resolution.

For **intentional** S3K deviations (architectural choices, feature extensions, deliberate bug-fixes of ROM data), see [S3K_KNOWN_DISCREPANCIES.md](S3K_KNOWN_DISCREPANCIES.md).

For general (cross-game) bugs, see [KNOWN_BUGS.md](KNOWN_BUGS.md).

Entries should include:
- **Location** — the file(s) where the bug lives, if known
- **Symptom** — what goes wrong and where you can observe it (test name, trace frame, manual repro)
- **Suspected cause** — best current theory, with ROM/disasm references when relevant
- **Removal condition** — what needs to be true for this entry to be deleted

---

## Table of Contents

1. [CNZ1 Miniboss Arena Entry — Music Play-In Missing](#cnz1-miniboss-arena-entry--music-play-in-missing)
2. [CNZ1 Trace F1685 — Wire-Cage Latch Frame Ordering](#cnz1-trace-f1685--wire-cage-latch-frame-ordering)
3. [AIZ1 Trace F2590 — Tails CATCH_UP_FLIGHT Trigger Path Mismatch](#aiz1-trace-f2590--tails-catch_up_flight-trigger-path-mismatch)

---

## CNZ1 Miniboss Arena Entry — Music Play-In Missing

**Location:** `Sonic3kCNZEvents.enterMinibossArena()`
**ROM Reference:** `sonic3k.asm:144841` (`moveq #cmd_FadeOut,d0; jsr Play_Music`) plus the boss-music play-in that follows when `Obj_CNZMiniboss` becomes active.

### Symptom

When `Obj_CNZMiniboss` crosses its camera-X gate (`$31E0`), `Sonic3kCNZEvents.enterMinibossArena()` mirrors the ROM's music fade-out via `audio().fadeOutMusic()`, but the miniboss-music play-in is not yet wired. Audio drops to silence between the fade-out and boss defeat instead of switching to the miniboss theme. All other arena-entry effects (camera lock, PLC `0x5D`, `Pal_CNZMiniboss` install, `Boss_flag`, wall-grab suppression) match the ROM bit-for-bit, so visual/gameplay parity is unaffected.

### Current State

The site is marked with an inline `TODO(T12)` comment. Workstream T12 ("CNZ miniboss audio handoff") owns wiring `Sonic3kMusic.MINIBOSS` (or the equivalent S3K music ID) into the existing `audio()` boss-music handoff once the miniboss audio routing lands.

No tests assert on music selection during the miniboss fight, so this gap does not block test coverage elsewhere.

### Removal Condition

Remove once the miniboss theme plays on arena entry and a regression test asserts on the active music ID between fade-out and boss defeat.

---

## AIZ1 Tails Rolling-Airborne Post-Jump y_speed Divergence (Trace Frame 2202)

**Location:** `PlayableSpriteMovement.doJumpHeight` or flight-gravity gate — the exact code path that mishandles Tails's rolling-jump airborne velocity is still open.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2202.

### Status

Frame 2150 was an earlier divergence on a `0xFA` slope zero-distance landing; that was fixed by narrowing the `Sonic3kZoneFeatureProvider.shouldTreatZeroDistanceAirLandingAsGround` angle window to exactly-flat (0x00 or 0xFF) per commit `ad232cf10`. The AIZ replay's first strict error has since moved to frame 2202.

### Symptom

At frame 2202 the engine produces `tails_y_speed = -0x02E0` vs the ROM's expected `-0x03E0` (engine is 0x0100 subpixels less upward than the ROM). Seed state at frame 2201: Tails `y_vel = -0x0418`, rolling+airborne (status 0x07, no rolling-jump bit), jumping flag set from a frame-1947 rolling jump.

The expected result is standard +0x38 air gravity: `-0x0418 + 0x38 = -0x03E0`. The engine is applying an extra ~0x0100 downward velocity somewhere in the air-movement path.

### Suspected Cause

The extra 0x100 matches `Tails_JumpHeight`'s jump-release cap behaviour: ROM `sonic3k.asm:28592` clips `y_vel` to `-0x0400` when `jumping` is set, `y_vel < -0x0400`, and no A/B/C button is *just-pressed* this frame. After the clip, gravity adds +0x38 → `-0x03C8`, still 0x018 above the engine's observed value. The remaining difference (and the 0x100 total gap) likely comes from a layered interaction: CPU-Tails input replay (button-press vs button-held distinction), rolling-airborne animation state, or an air-collision side-effect. Needs instrumentation.

### Removal Condition

Remove once `TestS3kAizTraceReplay`'s first strict error moves past frame 2202, OR a unit test pins the exact per-frame y_speed divergence for CPU-Tails rolling-airborne and a fix lands.

---

## CNZ1 Trace F1685 — Wire-Cage Ride Physics Not Producing y_vel from gSpeed

**Location:** `CnzWireCageObjectInstance` ride update / engine ground physics integration.
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 1685.

### Status (2026-04-25 iter-9)

Re-investigation showed the prior diagnosis (latch-frame-ordering) was incomplete. Trace data shows Tails is on-ground (`air=0`, `status=0x08`) for many frames before the latch frame F0694, transitioning between angle 0xD8 (slope) and 0xC0 (cage). The cage IS latching correctly. The bug is in the engine's per-frame ground physics not producing the expected y_vel from gSpeed at angle 0xC0.

### Symptom

`tails_y_speed mismatch (expected=-0D51, actual=-0B22)` at F1685.

ROM at F0694: gSpeed=0xD71, y_speed=-0xB22, ang=0xC0, on_object=true.
ROM at F0695: applies slope-resist (gSpeed → 0xD51, delta -0x20) then `loc_14B5C`'s gSpeed→y_vel conversion: `y_vel = (gSpeed × sin(0xC0)) / 256 = -0xD51`.

Engine at F0695: y_speed stays at -0xB22 (the F0694 reseed value). The engine's `modeNormal()`'s `calculateXYFromGSpeed()` is not running OR is running but the result is being overwritten.

### Diagnosed Cause

Per the iter-9 deep-dive: the cage's `setOnObject(true)` does not register Tails through `ObjectManager.SolidContacts`, so `hasObjectSupport()` returns false during `resolveGroundAttachment()`. Without object support, the engine probes terrain sensors at angle 0xC0 (vertical wall), finds no real terrain (cage is invisible level art), and re-sets `air=true`. Then on the next frame `modeAirborne()` runs gravity; this should produce `-0xB22 + 0x38 = -0xAEA`, but the trace shows `-0xB22` unchanged — suggesting an additional path is suppressing physics (likely `objectControlled=true` from the cage's `beginLatchedCooldown`).

### Attempted Fix (iter-9, reverted)

Adding gSpeed→y_speed seeding inside `CnzWireCageObjectInstance.latch()` (with `wasAirborneOnEntry` parameter to capture pre-`setAir(false)` state) had no measurable effect. The latch frame's airborne path is not being hit because Tails is already on-ground (`air=0`) when the latch fires — Tails was on a different ground/slope object the previous frame and transitions to the cage with `air=0` throughout.

### Required Investigation / Fix

Two complementary fixes likely needed:

1. **Make CnzWireCage register through `ObjectManager.SolidContacts`** so `hasObjectSupport()` returns true while latched. This would let `resolveGroundAttachment()` keep `air=false` instead of probing for non-existent terrain, allowing `modeNormal()`'s `calculateXYFromGSpeed()` to run and produce the correct y_vel.

2. **Verify the engine's per-frame ground physics at angle 0xC0** computes `y_vel = -gSpeed` correctly. Slope-resist should apply (`gSpeed += sin(0xC0) × 0x20 / 256 = -0x20`) and `calculateXYFromGSpeed` should yield `y_vel = -gSpeed`.

### Removal Condition

Remove once `TestS3kCnzTraceReplay`'s F1685 `tails_y_speed` mismatch is resolved.

---

## AIZ1 Trace F2667 — Sidekick Riding-State Not Re-Established by Per-Frame Trace Seed

**Location:** `AbstractTraceReplayTest.applyRecordedFirstSidekickState` per-frame sidekick state seed; interaction with `ObjectManager.SolidContacts.ridingStates`.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2667 (after the iter-10 F2590 fix).

### Status (2026-04-25 iter-11)

The F2590 fly-back-exit-gate fix advanced the first-divergence frame to F2667. ROM at F2667: Tails has been standing on Spike object slot 22 (top-facing, `loc_24090`) since the post-hurt-bounce landing at F2640, walking right with `g_speed = 0x01BC, x_speed = 0x01BC, status_byte = 0x00, stand_on_obj = 22`. ROM `SolidObjectFull_1P` takes the standing path (`p2_standing_bit` set on the spike from the F2640 `RideObject_SetRide` call) and dispatches to `MvSonicOnPtfm` — no bounding-box test, no side push, speed preserved.

### Symptom

`tails_x_speed mismatch (expected=0x01BC, actual=0x0000)` and matching `tails_g_speed` mismatch at F2667. Diagnostic side-log probe (`AizF2667SideLog` — temporary, removed) showed engine Tails position is correct (matches ROM through F2666) but `ObjectManager.getRidingObject(sidekick) == null` for the entire post-landing window. At F2667 the sidekick walks rightward into the spike's left collision edge (`relX_raw = 2`) and the engine's `resolveContactInternal` side path zeroes both `xSpeed` and `gSpeed` because the player is "moving into" the object.

### Diagnosed Cause

The trace replay's per-frame sidekick state seed (`applyRecordedFirstSidekickState`) writes position, velocity, `air`, `onObject` etc. directly. At F2640 (the actual landing frame) ROM Tails transitions airborne→grounded via the spike's `SolidObject_cont` → `RideObject_SetRide`, which both sets `p2_standing_bit` on the spike object and registers Tails as riding via `interact(a1)`. The engine seed bypasses this transition: when CSV F2640 has `air=0`, the seed simply writes `setAir(false)` directly. The engine's `SolidContacts.ridingStates` map is never populated for the sidekick+spike pair, so on subsequent frames the spike's solid resolution falls through to the bounding-box test and triggers a side push.

### Required Fix (deferred)

The trace's `sidekick_stand_on_obj` field carries the ROM `interact(a0)` slot index. Mapping that ROM slot to an engine `ObjectInstance` and registering it via `SolidContacts.ridingStates` from the seed is the cleanest fix. It requires:
- A ROM-slot → `ObjectInstance` lookup on the engine side (the engine doesn't currently expose ROM-equivalent slot indexing for sidekick seeding).
- A new package-private `ObjectManager` API to register a sidekick's riding state without running the full collision pipeline.

This is a trace-replay infrastructure change; deferring until after the AIZ trace test reaches frame parity at the 5K+ frame range or the trace recorder gains per-frame riding-state capture.

### Removal Condition

Remove once `TestS3kAizTraceReplay`'s first strict error advances past F2667 AND the engine's sidekick `ObjectManager.getRidingObject(sk)` returns the AIZ1 spike instance during the F2640-F2680 invulnerability window.

---

## AIZ1 Trace F2590 — Tails Fly-Back Exit Gate Per-Game Mask Mismatch (FIXED)

**Status:** Fixed in iter-10 by splitting `TailsRespawnStrategy.updateApproaching`'s exit-gate mask per game via `PhysicsFeatureSet.sidekickFlyLandStatusBlockerMask` and `sidekickFlyLandRequiresLeaderAlive`. Kept here for context — the F2590 entry rolled forward through several investigations before the per-game divergence was identified.

**Location:** `TailsRespawnStrategy.updateApproaching` (engine APPROACHING → NORMAL transition, equivalent to ROM `Tails_FlySwim_Unknown` exit at sonic3k.asm:26622-26648 / s2.asm:38870-38883).

**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2590.

### Symptom (pre-fix)

`tails_y_speed mismatch (expected=-0400, actual=0x02D8)` at F2590.

### Iter-10 Diagnosis (correct)

Side-log probe (`AizF2590SideLog` — temporary, removed after the fix
landed) showed engine Tails was permanently locked in
`SidekickCpuController.State.APPROACHING` with `object_controlled = true`
through the entire descent that culminated in F2590, so
`processInlineObjectForPlayer` short-circuited every spike
`onSolidContact` callback and Tails never received the standard
hurt-bounce.

`updateApproaching` could not exit because its status-blocker mask
was hard-coded to `0xD2`, the **S2** value (s2.asm:38872-38873
`andi.b #$D2,d2 / bne return`). Bit 1 (in_air) of Sonic's recorded
status was set throughout the descent, so the gate refused to land.

ROM **S3K** at the same call site (sonic3k.asm:26625) uses
`andi.b #$80,d2` — bit 7 only, which is not a real Sonic status flag.
S3K's gate practically never blocks, and lands as soon as residuals
are zero. ROM S3K also adds a leader-alive check that S2 lacks
(sonic3k.asm:26629-26630 `cmpi.b #6,(Player_1+routine).w / bhs`).

### Iter-10 Fix

- `PhysicsFeatureSet`: added two fields:
  - `sidekickFlyLandStatusBlockerMask` — `0xD2` (S2) / `0x80` (S3K) /
    `0` (S1, no CPU sidekick).
  - `sidekickFlyLandRequiresLeaderAlive` — `false` (S1/S2) / `true`
    (S3K, ROM `cmpi.b #6,(Player_1+routine).w`).
- `TailsRespawnStrategy.updateApproaching` now reads both fields off
  `sidekick.getPhysicsFeatureSet()`. The legacy `0xD2` mask is kept
  as a fallback when no feature set is resolved (legacy unit tests
  that build a sprite without a game module).
- `CrossGameFeatureProvider` and `TestHybridPhysicsFeatureSet`
  threaded through the new fields.

### Effect on AIZ trace replay

- First strict divergence frame moved 2590 → 2667.
- Errors 1558 → 1208. Warnings 2010 → 1633.

ROM at F2667 has Tails getting hurt-bounced again and recovering;
the engine doesn't yet — that's a separate divergence that surfaces
once the F2590 spike landing actually fires in the engine.

---

## CNZ1 Trace F825 — Tails Off-Screen Recovery Teleport Target Mismatch (FIXED)

**Status:** Fixed in commit `19ed59532`. Kept in this doc as the F825 entry rolled forward through several follow-up fixes (despawn-X marker, fc alignment, SPAWNING-gate) that consumed it.

**Location:** `SidekickCpuController` flight-recovery / off-screen teleport path. ROM `sub_13ECA` (sonic3k.asm:26800) teleports Tails to `(0x7F00, 0x0000)` when the catch-up routine resets; the engine's equivalent path teleports to `(0x4000, 0x0000)` instead.
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 825.

### Status (2026-04-24)

Frame 318 was caused by `AbstractTraceReplayTest.applyRecordedFirstSidekickState` calling `setMoveLockTimer(0)` every frame, defeating ROM `Player_SlopeRepel`'s 30-frame move_lock counter. Fixed in commit `28940b604`. First error moved 318 → 383.

Frame 383 was the Tails-CPU jump-trigger gate failing its 64-frame mask check because `SpriteManager.frameCounter` lagged `Level_frame_counter` by 1: ROM had `gfc=0x180` (mask=0, fires) at the moment the engine had `fc=0x17F` (mask=0x3F, no fire). The lag came from CNZ's seed state being recorded at `T_0.gfc=1` (gameplay starts immediately) while engine fc starts at 0 — for AIZ the seed is at `T_289.gfc=0` (still inside the intro, ROM's `Level_frame_counter` not yet incrementing) so engine fc=0 happens to align naturally. Fixed by pre-setting `SpriteManager.frameCounter = T_(K_start-1).gfc` at the trace replay loop start in `AbstractTraceReplayTest.replayS3kTrace` so engine `fc++` on each step keeps pace with ROM `Level_frame_counter` for both traces. Total errors 4997 → 4946 (−51); first error moved 383 → 825.

### Symptom (current)

At trace frame 825, ROM `tails_x = 0x7F00, tails_y = 0x0000, tails_air = 1`; engine `tails_x = 0x4000, tails_y = 0x0000, tails_air = 1`. Sonic state matches the trace exactly through this and many surrounding frames (`x=0x0997, y=0x0703, x_speed=0x05BB`, on-ground, rolling). The trace shows `sidekick_routine=2` here, indicating the ROM has just entered `Tails_CPU_routine = 2` (`Tails_Catch_Up_Flying`) — which is reached via `sub_13ECA` (sonic3k.asm:26800):

```
sub_13ECA:
    ...
    move.w  #2,(Tails_CPU_routine).w
    move.b  #$81,object_control(a0)
    move.b  #1<<Status_InAir,status(a0)
    move.w  #$7F00,x_pos(a0)
    move.w  #0,y_pos(a0)
    ...
```

The engine takes the same routine transition but writes `x_pos = 0x4000` instead of `0x7F00`. The teleport is a marker meaning "Tails is despawned, waiting to fly back in"; the exact value matters because the next frame's bounding/active checks use it.

### Suspected Cause

Engine's `SidekickCpuController.CATCH_UP_FLIGHT` / `FLIGHT_AUTO_RECOVERY` enter logic, or the despawn helper that runs when Tails leaves the camera bounds long enough, hardcodes `0x4000` somewhere instead of `0x7F00`. ROM's marker is the magic value `0x7F00` at multiple sites (`sub_13ECA`, dynamic Tails respawn). Search for `0x4000` constants in the sidekick / respawn / catch-up paths, or for any place the engine clamps a teleport X to a level-bounds-derived value.

### Removal Condition

Remove once `TestS3kCnzTraceReplay`'s first strict error moves past frame 825 AND the engine writes `tails_x = 0x7F00` on the catch-up-flight init frame.
