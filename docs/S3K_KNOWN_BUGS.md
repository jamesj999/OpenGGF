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

## CNZ1 Trace F1685 — Wire-Cage Latch Frame Ordering

**Location:** `CnzWireCageObjectInstance.latch()` interaction with `LevelFrameStep` execution order, downstream of the on-screen detection issue above.
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 1685 with the iter-5 fixes in place.

### Status (2026-04-25)

Two related parity gaps converge here:

1. The engine's `LevelFrameStep` inline-solid path runs player physics BEFORE cage object updates; ROM `ExecuteObjects` runs the cage's latch logic before `Tails_Control` → `Tails_InputAcceleration_Path`. So the engine's first-on-cage frame misses one application of slope-resist + CPU-input acceleration that ROM applies at angle 0xC0.

2. Independently, the engine's despawn detection (see entry above) parks Tails at the despawn marker around F1380, so by the time the cage frame F1685 is compared, Tails has been despawned and `objectControlled=true` short-circuits all physics.

### Symptom

`tails_y_speed mismatch (expected=-0D51, actual=-0B22)` at F1685. ROM y_speed evolves from -0xB22 to -0xD51 (delta -0x22F) via slope-resist + CPU-input accel at angle 0xC0; engine y_speed is frozen at -0xB22 because physics never runs (objectControlled set by despawn).

### Suspected Cause

The despawn cascade dominates: even if the cage latch ordering were fixed, the engine has already despawned Tails so the latch path never executes. The on-screen-detection fix above must land first; only then will the cage-latch ordering issue surface as a real divergence (and only then can we tell whether the latch-ordering also needs a fix).

### Removal Condition

Remove once `TestS3kCnzTraceReplay`'s engine no longer despawns Tails before F1685 AND the F1685 `tails_y_speed` mismatch is resolved (either passes or moves further into the trace).

---

## AIZ1 Trace F2590 — Tails CATCH_UP_FLIGHT Trigger Path Mismatch

**Location:** `SidekickCpuController.updateCatchUpFlight()` 64-frame gate vs. ROM's actual trigger-path used at this frame.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2590 (after iter-7 fixes).

### Status (2026-04-25)

Iter-6 fixed F2405 (engine never despawning Tails) by aligning the render-flag visibility Y-margin with ROM `Render_Sprites` (24 instead of 32). First error advanced F2405 → F2465.

Iter-7 fixed F2465 (stale x_speed=-0x01F9 carried across the despawn) by zeroing velocities on `TailsRespawnStrategy.beginApproach()` per ROM `loc_13B50`. First error advanced F2465 → F2590.

Iter-8 implemented BK2 P2 controller-input parsing (commit 33bfcc78d) and a fix for the pre-existing `p2Logical = p2Held` bug, but inspection of all three trace BK2 movies (S3K AIZ, S3K CNZ, S2 EHZ) showed **zero P2 input frames**. So F2590 is not driven by the Ctrl_2 A/B/C/START button-press path and another mechanism is responsible.

### Symptom

`tails_y_speed mismatch (expected=-0400, actual=0x02D8)` at F2590. ROM's CSV at trace frame 0x0A1E shows `sk_routine` transitioning 02 (CATCH_UP_FLIGHT) → 04 (FLIGHT_AUTO_RECOVERY) with y_speed=-0x0400 (the `Tails_JumpHeight` cap value). Engine still in CATCH_UP_FLIGHT with continued downward gravity (y_speed=+0x02D8 at +0x38/frame).

### Diagnosed Cause (open)

ROM's `Tails_Catch_Up_Flying` (sonic3k.asm:26474) has two trigger paths:

1. Ctrl_2 A/B/C/START press → immediate `loc_13B50`
2. 64-frame `(Level_frame_counter & $3F) == 0` gate → `loc_13B50`

The engine fires path 2 at gfc=0x0900 (= AIZ frame 2593, three frames AFTER the ROM transitioned). At gfc=0x08FD/0x08FE (AIZ frames 2589/2590), `mask & 0x3F` is non-zero — the 64-frame gate does NOT fire. Path 1 fails because the BK2 has no P2 input.

This means ROM is triggering via a third path the engine doesn't model — possibly:
- `Tails_FlySwim_Unknown` (routine 0x04) is reached via a different route the engine misses
- The ROM's `Level_frame_counter` differs slightly from the engine's `frameCounter` at this point (alignment drift over 2400+ frames)
- A different sub-routine sets `Tails_CPU_routine = 4` directly

### Removal Condition

Remove once `TestS3kAizTraceReplay`'s first strict error advances past F2590 AND the engine's CATCH_UP_FLIGHT → FLIGHT_AUTO_RECOVERY transition fires on the same trace frame as the ROM (verified by tracking `sidekick_routine` in the trace).

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
