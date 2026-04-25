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
2. [Sidekick CPU On-Screen Detection — `checkDespawn` False Positives/Negatives](#sidekick-cpu-on-screen-detection--checkdespawn-false-positivesnegatives)
3. [CNZ1 Trace F1685 — Wire-Cage Latch Frame Ordering / Despawn Cascade](#cnz1-trace-f1685--wire-cage-latch-frame-ordering--despawn-cascade)
4. [AIZ1 Trace F2405 — Tails Flight-Timer Despawn Not Firing](#aiz1-trace-f2405--tails-flight-timer-despawn-not-firing)

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

## Sidekick CPU On-Screen Detection — `checkDespawn` False Positives/Negatives

**Location:** `SidekickCpuController.checkDespawn()` (`src/main/java/com/openggf/sprites/playable/SidekickCpuController.java` lines 957–984) — `onScreen` test uses `Camera.isVisibleForRenderFlag()` (geometric bounding-box test) while ROM `sub_13EFC` (sonic3k.asm:26816–26847) reads `render_flags(a0)` bit 7 set by `Draw_Sprite` after the VDP visibility test.

### Symptom

Two failure modes appear with the same root cause:

- **CNZ1 F1685 (engine despawns when ROM doesn't):** Around F1380–F1685, the engine treats Tails as off-screen for ≥300 consecutive frames and triggers `triggerDespawn()`, parking Tails at `(0x7F00, 0x0000)`. ROM's render flag never clears for that long, so ROM keeps Tails in the level. The reported error `tails_y_speed mismatch (expected=-0D51, actual=-0B22)` at F1685 is the engine's stale velocity from the despawn marker; once the engine despawns, the cage-latch and ground physics never re-run, so y_speed sits at whatever the per-frame reseed leaves on the marker frame.

- **AIZ1 F2405 (engine doesn't despawn when ROM does):** Tails goes off-screen at AIZ trace F0x839 (decimal 2105). ROM's `Tails_CPU_flight_timer` reaches `5*60 = 300` exactly at F0x965 (decimal 2405) and `sub_13EFC` calls `sub_13ECA` to despawn (parking at `(0x7F00, 0x0000)`). Engine's `despawnCounter` reaches only 25–40 because `Camera.isVisibleForRenderFlag()` returns `true` on frames where ROM's `render_flags` bit 7 was never set this frame, repeatedly resetting the counter.

### Diagnosed Cause

The engine's `Camera.isVisibleForRenderFlag(sprite)` does a geometric "is the sprite's bounding box inside the camera viewport" test using `renderFlagWidthPixels = 0x18` (24). ROM's `render_flags` bit 7 is set/cleared by `Draw_Sprite` in the per-frame VDP build pass, which uses the actual sprite mappings' bounding box and rejection thresholds. The two are equivalent on most frames but diverge in edge cases: very tall sprites (Tails rolling/flight), camera-shake/scroll deformation, and one-frame timing differences as the camera and sprite both move.

The divergence accumulates over hundreds of frames in a single trace, so even a small per-frame mismatch eventually causes one side to (a) reset its timer when the other side wouldn't (engine in CNZ) or (b) keep the timer running when the other side would reset (engine in AIZ — opposite direction).

### Removal Condition

Remove once `SidekickCpuController.checkDespawn()` consumes a render-flag value that exactly mirrors ROM's `render_flags` bit 7 — either by:
- routing through `SpriteManager.refreshPlayableRenderFlags()`'s cached value with verified ROM-equivalent semantics (sprite bounds taken from active mapping frame, not a fixed `renderFlagWidthPixels`), or
- recomputing the visibility test from VDP/Draw_Sprite-equivalent geometry rather than `Camera.isVisibleForRenderFlag()`.

Verification: `TestS3kCnzTraceReplay`'s engine no longer despawns Tails before F1685, AND `TestS3kAizTraceReplay`'s engine despawns Tails by F2405 (matching ROM in both directions).

---

## CNZ1 Trace F1685 — Wire-Cage Latch Frame Ordering / Despawn Cascade

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

## AIZ1 Trace F2405 — Tails Flight-Timer Despawn Not Firing

**Location:** `SidekickCpuController.checkDespawn()` — `despawnCounter` does not accumulate to 300 because the engine's `onScreen` test resets it on frames ROM's `render_flags` says off-screen.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2405.

### Symptom

`tails_y mismatch (expected=0x0000, actual=0x0454)` at F2405. ROM despawns Tails to `(0x7F00, 0x0000)` via `sub_13EFC` (sonic3k.asm:26816) → `sub_13ECA` once `Tails_CPU_flight_timer` hits `5*60 = 300`. The 300-frame off-screen count starts at AIZ trace F0x839 (decimal 2105) and expires exactly at F0x965 (decimal 2405). Engine's `despawnCounter` only reaches ~25-40 in the same window.

### Diagnosed Cause

Same as the on-screen detection entry above: the engine's `Camera.isVisibleForRenderFlag()` returns `true` on a non-trivial fraction of the off-screen window because ROM's `render_flags` bit 7 stays clear (Draw_Sprite never sets it). The geometric vs. VDP-build test divergence repeatedly resets `despawnCounter`.

This is the AIZ side of the same bug as the CNZ F1685 entry — opposite direction (engine misses despawn ROM does, vs. engine misfires despawn ROM doesn't). Both are blocked on the same render-flag-parity fix.

### Removal Condition

Remove once `TestS3kAizTraceReplay`'s engine despawns Tails by AIZ trace F0x965 (frame 2405) matching ROM, AND the first strict error advances past F2405.

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
