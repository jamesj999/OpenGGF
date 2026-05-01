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
2. [AIZ1 Trace F4679 — Sidekick Despawn Velocity & Position Semantic Gap (FIXED)](#aiz1-trace-f4679--sidekick-despawn-velocity--position-semantic-gap-fixed)
3. [CNZ1 Trace F1685 — Tails CPU Spurious Despawn on Barber-Pole→Wire-Cage Object Switch (FIXED)](#cnz1-trace-f1685--tails-cpu-spurious-despawn-on-barber-polewire-cage-object-switch-fixed)
4. [CNZ1 Trace F1740 — Wire Cage restoreObjectLatchIfTerrainClearedIt Overrode Slope-Repel Slip (FIXED)](#cnz1-trace-f1740--wire-cage-restoreobjectlatchifterrainclearedit-overrode-slope-repel-slip-fixed)
5. [CNZ1 Trace F1758 — Wire Cage Airborne-Capture object_control Bit 0 Missing (FIXED)](#cnz1-trace-f1758--wire-cage-airborne-capture-object_control-bit-0-missing-fixed)
6. [CNZ1 Trace F1791 — Tails CPU Auto-Jump Trigger Bit-7 Object Control Gate (FIXED)](#cnz1-trace-f1791--tails-cpu-auto-jump-trigger-bit-7-object-control-gate-fixed)
7. [AIZ1 Trace F2590 — Tails CATCH_UP_FLIGHT Trigger Path Mismatch](#aiz1-trace-f2590--tails-catch_up_flight-trigger-path-mismatch)
8. [AIZ1 Trace F2202 -- Phantom MonkeyDude Respawn Triggers Spurious Sidekick Bounce (FIXED)](#aiz1-trace-f2202----phantom-monkeydude-respawn-triggers-spurious-sidekick-bounce-fixed)
9. [AIZ1 Trace F5497 — Sidekick CPU Bound Override Stale After Act Transition (FIXED)](#aiz1-trace-f5497--sidekick-cpu-bound-override-stale-after-act-transition-fixed)
10. [AIZ Trace F5736 — Level_frame_counter Skips Tick on Seamless Act Reload (FIXED)](#aiz-trace-f5736--level_frame_counter-skips-tick-on-seamless-act-reload-fixed)
11. [AIZ Trace F6066 — CaterKillerJr Missed Obj_WaitOffscreen Gate (FIXED)](#aiz-trace-f6066--caterkillerjr-missed-obj_waitoffscreen-gate-fixed)
12. [CNZ1 Trace F2222 — Wire Cage Sidekick JUMP_RELEASE Spurious Fire (OPEN — needs ROM-aligned `Ctrl_2_pressed_logical` model)](#cnz1-trace-f2222--wire-cage-sidekick-jump_release-spurious-fire-open--needs-rom-aligned-ctrl_2_pressed_logical-model)
13. [AIZ Trace F6255 — Tails CPU Freed-Slot Despawn (RESOLVED)](#aiz-trace-f6255--tails-cpu-freed-slot-despawn-resolved)
14. [CNZ1 Trace F3649 — Tails Air-to-Ground Spring Boost Missed (RESOLVED)](#cnz1-trace-f3649--tails-air-to-ground-spring-boost-missed-resolved)
15. [CNZ1 Trace F6304 — Tails Misses CNZ Door Re-Land While Following Fast Leader (RESOLVED)](#cnz1-trace-f6304--tails-misses-cnz-door-re-land-while-following-fast-leader)
16. [CNZ1 Trace F7614 — Tails Spring Bounce Top-Landing 2-Pixel Drift (OPEN — next trace blocker)](#cnz1-trace-f7614--tails-spring-bounce-top-landing-2-pixel-drift)
17. [AIZ2 Trace F7127 — Tails Phantom Landing While Falling (RESOLVED)](#aiz2-trace-f7127--tails-phantom-landing-while-falling)
18. [AIZ2 Trace F7171 — Tails Killed Mid-Run vs. Engine Continuing Follow-Steering (OPEN — next AIZ blocker)](#aiz2-trace-f7171--tails-killed-mid-run-vs-engine-continuing-follow-steering)

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

## AIZ1 Trace F4679 — Sidekick Despawn Velocity & Position Semantic Gap (FIXED)

**Status:** Fixed in iter-19 by routing sidekick level-boundary kills through
a new `Kill_Character`-equivalent path. The fix introduces a `DespawnCause`
enum on `SidekickCpuController` and gates the existing `triggerDespawn()`
marker-warp body behind the non-`LEVEL_BOUNDARY` causes, with a new
`beginLevelBoundaryKill()` entry for `LEVEL_BOUNDARY` that mirrors ROM
`Kill_Character` (sonic3k.asm:21136-21151) by zeroing `xSpeed`/`ySpeed`/
`gSpeed`, clearing roll/push/rolljump bits, and parking the sidekick in
a new `State.DEAD_FALLING` engine state. The next frame's
`updateDeadFalling()` runs the `loc_1578E -> sub_123C2 -> sub_13ECA`
sequence (sonic3k.asm:29263, 24538, 26800): warp to despawn marker
`(0x7F00, 0)`, transition to `State.SPAWNING`, and apply the post-warp
`MoveSprite_TestGravity` +`$38` gravity write that ROM produces in the
same frame.

**Location:** `SidekickCpuController.beginLevelBoundaryKill()` /
`SidekickCpuController.updateDeadFalling()` /
`SidekickCpuController.applyDespawnMarker()`,
`PlayableSpriteMovement.doLevelBoundary()` (sidekick-CPU dispatch).
**ROM Reference:** `sonic3k.asm:21136` (`Kill_Character`), `sonic3k.asm:23172`
(`Player_LevelBound`), `sonic3k.asm:24538` (`sub_123C2`), `sonic3k.asm:26800`
(`sub_13ECA`), `sonic3k.asm:29263` (`loc_1578E` death routine), `sonic3k.asm:36068`
(`MoveSprite_TestGravity`).

### Pre-Fix Symptom

`TestS3kAizTraceReplay#replayMatchesTrace` first error at trace frame 4679:
```
tails_y_speed mismatch (expected=0x0000, actual=0x0198)
```

Engine `SidekickCpuController.despawn()` warped Tails to the despawn marker
`(0x7F00, 0)` immediately and left `(x_vel, y_vel, ground_vel)` carrying
the live-physics values from the previous frame, while ROM's
`Player_LevelBound -> Kill_Character` chain zeros the velocities on Frame
N before the death routine warps to the marker on Frame N+1.

### Diagnosed Cause

`SidekickCpuController.triggerDespawn()` was modelling `sub_13ECA` only
(the marker warp) for all despawn causes. Off-screen-timeout and
object-id-mismatch despawns naturally preserve velocity (ROM `sub_13ECA`
writes only x_pos/y_pos/object_control/status/Tails_CPU_routine and does
not touch x_vel/y_vel/ground_vel — sonic3k.asm:26800-26809), but the
level-boundary kill goes through `Kill_Character` first which DOES zero
the velocities. The engine collapsed both into one path.

### Fix

1. New `DespawnCause` enum on `SidekickCpuController`:
   - `LEVEL_BOUNDARY` (Player_LevelBound bottom kill plane)
   - `OFF_SCREEN_TIMEOUT` (engine 300-frame off-screen timeout)
   - `OBJECT_ID_MISMATCH` (S2 TailsCPU_CheckDespawn id mismatch)
   - `EXPLICIT` (test harness, level transitions)
2. New `State.DEAD_FALLING` enum value, dispatched via
   `updateDeadFalling()` in the per-frame state switch.
3. `triggerDespawn(DespawnCause)` overload that routes to
   `beginLevelBoundaryKill()` for `LEVEL_BOUNDARY`, otherwise calls
   `applyDespawnMarker()` directly (preserving the historic non-zeroing
   semantics for non-kill despawns).
4. `beginLevelBoundaryKill()` mirrors `Kill_Character`: zeros vels, clears
   roll/push/rolljump/onObject/pushing bits, sets in_air, transitions to
   `DEAD_FALLING`. Position is intentionally NOT warped this frame to
   match ROM's two-frame split where `sub_13ECA` runs on Frame N+1.
5. `updateDeadFalling()` runs the next frame: calls `applyDespawnMarker()`
   to warp + transition to `SPAWNING`, then writes y_speed=0x38 to mirror
   ROM's post-`sub_13ECA` `MoveSprite_TestGravity` +0x38 air-gravity write.
6. `applyDespawnMarker()` no longer zeros velocities (matches ROM
   `sub_13ECA` exactly). Off-screen-timeout and object-id-mismatch paths
   keep their pre-fix preserve-velocity semantics, which AIZ trace F2405
   exercises (Tails alive at F2404, sub_13EFC's flight_timer hits 5*60 at
   F2405, marker warp without velocity zeroing).
7. `PlayableSpriteMovement.doLevelBoundary()` calls
   `cpuController.despawn(DespawnCause.LEVEL_BOUNDARY)` instead of the
   no-arg `despawn()` overload.

### Result

- AIZ first strict error advances F4679 -> F5497 (1190 -> 1185 errors).
  F5497 is in AIZ act 2 reload territory (downstream divergence, not
  related to the kill-plane semantic gap; subsequently fixed — see
  "AIZ1 Trace F5497" entry below).
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ F1151,
  S3K CNZ F1815.

---

## AIZ1 Trace F5497 — Sidekick CPU Bound Override Stale After Act Transition (FIXED)

**Location:** `LevelManager.executeActTransition()` (`src/main/java/com/openggf/level/LevelManager.java`).
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error was at frame 5497 prior to fix.

### Symptom

At frame 5497 the engine produced `tails_x = 0x2F20` vs the ROM's
expected `tails_x = 0x00B1` — a 0x2E6F (≈11887 px) jump in a single
frame, immediately after the AIZ1 → AIZ2 seamless reload checkpoint
(`aiz2_reload_resume`) at frame 5496.

### Root Cause

`SidekickCpuController` keeps its own `minXBound`/`maxXBound`/
`maxYBound` overrides that `PlayableSpriteMovement.doLevelBoundary`
prefers over the live camera bounds for CPU sidekicks (mirroring ROM
`sonic3k.asm:36925/36945` Player_Boundary_Check_*). Per-zone event
handlers — notably `Sonic3kAIZEvents` boss arena lock — populate
those overrides during AIZ1; `Sonic3kLevelEventManager
.syncSidekickBoundsToCamera()` then refreshes them every frame at the
END of `update()`.

When the AIZ1 → AIZ2 seamless act transition fires (ROM
`AIZ1BGE_Finish` at `sonic3k.asm:104722-104771`), the engine's
`executeActTransition()` ran `restoreCameraBoundsForCurrentLevel()`
to pick up AIZ2 camera bounds but did NOT refresh the CPU bound
overrides. The next frame's `doLevelBoundary` therefore saw stale
AIZ1-boss-arena bounds (`minXBound = maxXBound = 0x2F10`),
computed `leftBoundary = 0x2F20`, found `predictedX = 0x00B2 < 0x2F20`,
and clamped Tails to `0x2F20` — teleporting the sidekick across the
entire AIZ2 reload offset (-0x2F00).

ROM has no analogous bug: its act-2 reload resets
`Camera_min_X_pos` / `Camera_min_Y_pos` (sonic3k.asm:104758-104762),
and Tails-CPU code reads those camera fields directly — there is no
separate "Tails CPU bounds" storage to fall out of sync.

### Fix

Step 7b in `executeActTransition()` now iterates
`spriteManager.getSidekicks()` after the camera bounds restore and
calls `cpu.setLevelBounds(cam.getMinX(), cam.getMaxX(),
max(cam.getMaxY(), cam.getMaxYTarget()))` to refresh each sidekick's
CPU bound override to the new act's camera bounds. This matches the
ROM semantics (camera reset = sidekick bound reset) without affecting
the per-frame `syncSidekickBoundsToCamera()` flow that was already
running successfully on every non-transition frame.

### Result

- AIZ first strict error advances F5497 → F5736 (1185 → 1184 errors).
  F5736 is a fresh tails_x_speed divergence in the AIZ2 main gameplay
  region.
- Cross-game baselines unchanged: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
  F1151, S3K CNZ F2175.


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

## CNZ1 Trace F1758 — Wire Cage Airborne-Capture object_control Bit 0 Missing (FIXED)

**Status:** Fixed in iter-14 by mirroring ROM `loc_3394C` (sonic3k.asm:69921) airborne-capture path in `CnzWireCageObjectInstance.tryLatch`. ROM sets `bset #0, object_control(a1)` (and cooldown=1) when capturing an airborne player, which gates `Tails_Modes` dispatch the next frame at `loc_1384A` (sonic3k.asm:26211). Engine was taking the grounded `loc_33958` branch when its terrain pass had pre-grounded Tails on the cage rim by the time the cage update ran (engine runs player physics first then objects). Fix: snapshot the player's pre-physics state at the start of `PlayableSpriteMovement.handleMovement` (new `capturePrePhysicsSnapshot`), then in `tryLatch` use the pre-physics air/angle/gSpeed (falling back to post-physics state for unit tests bypassing the physics tick). Hydration test seam (`AbstractTraceReplayTest.applyRecordedFirstSidekickState`) now also preserves `objectControlled` when the engine has the wire cage latched, since the trace CSV does not capture `object_control` and the existing hydration was zeroing the bit between cage capture and the next frame's physics gate. First strict error advanced F1758 → F1791 (next-frame Tails CPU AI auto-jump trigger / `Ctrl_2_logical` mirror — overlaps with AIZ F2721 work).

### Pre-iter-14 Status (preserved for context)

Iter-13 refined the iter-11 F1740 fix by replacing the `move_lock > 0` short-circuit in `restoreObjectLatchIfTerrainClearedIt` with a new `slopeRepelJustSlipped` per-tick flag. The flag is cleared at the start of every `handleMovement` call and set inside `Player_SlopeRepel` only on the frame where `bset #Status_InAir` actually fires.

Result: F1758's `tails_angle` (0x40), `tails_x` (0x134F — cage orbit position), `tails_y` (0x0709), `tails_air` (0), and `tails_ground_mode` (1) all match expected. First error field shifted from `tails_angle mismatch` to `tails_y_speed mismatch` (expected 0x0147, actual 0x0291). Total errors 4678 → 4625.

The remaining `tails_g_speed`/`tails_y_speed` divergence (engine 0x291, expected 0x271 at F1758 onward) is a slope-resist suppression issue: ROM appears to skip `Player_SlopeResist` for Tails on the cage from F1758 onward (no `+0x20` per frame at angle 0x40), while the engine adds `+0x20` per frame. Likely cause: ROM has set `bit 0` of `object_control(a0)` from the cage's `loc_339B6: bset #0, object_control(a1)` path (line 69958), which gates the entire physics dispatch in `loc_10BFC` (sonic3k.asm:21973-21976) and skips `Sonic_Modes`. Without per-frame `object_control(a0)` recording, can't verify this hypothesis directly.

### Pre-iter-13 Status (preserved for context)

The F1740 fix advanced the first strict error to F1758. ROM trace F1757 → F1758:
- **F1756**: Tails airborne (air=1, angle=0, mode=0), engine matches.
- **F1757**: Tails LANDS on slope at angle 0x40, mode 1, air=0. Engine matches.
- **F1758**: ROM teleports Tails x: 0x1309 → 0x134F (Δ +0x46) with x_speed=0, status=0x08 (on_object only, NO in_air). Engine releases Tails to airborne (angle=0, air=1) instead of staying on cage orbit.

The +0x46 x teleport at F1758 with x_speed=0 is the cage's `loc_33A50/loc_33BBA` orbit positioning (`x = cage.x + cosine(phase)/4 + y_radius * cosine(phase) / 256`). For phase ≈ 0x04 with cage.x = 0x1300 and y_radius = 19, that yields x ≈ 0x1352, which matches the trace 0x134F within rounding. So the cage RECAPTURED Tails between F1740 and F1757 (cooldown $10 expired by F1756), then ran the orbit at F1758.

### Diagnosed Cause

The engine's slope-repel correctly slips Tails into air at F1758 (angle 0x40 + gSpeed 0x271 < 0x280 sets Status_InAir, move_lock=30). Engine's cage `continueRide` then sees:
- `state.cooldown == 0` (latched at F1757, no release yet)
- `player.getAir() == true` (slope-repel slip)
- `gSpeed < MIN_SPEED_TO_CONTINUE (0x300)`
- → enters cooldown branch → `updateReleaseRide(player, state)` → in_air → `release()`

ROM appears to take a different path. By manual trace of `loc_339A0` with `gSpeed < 0x300 → loc_339B6 → set cooldown → bra loc_33ADE → loc_33B1E → in_air → bne loc_33B62 → release`, ROM should ALSO release. But the trace shows ROM at F1758 has status=0x08 (on_object only, NO in_air). That status pattern is inconsistent with both the slope-repel slip path AND the cage release path — one or both didn't fire in ROM at this frame.

Without per-frame `Tails_CPU_routine`, `Ctrl_2_logical`, and `move_lock` recording, pinpointing why ROM kept Tails on cage at F1758 (while engine releases) requires either (a) regenerating the trace with the v6 recorder extension that's already committed, or (b) stepping through ROM in BizHawk for F1755-F1760 to capture the exact ROM path firing.

### Required Investigation / Fix

1. **Regenerate the CNZ trace** with the v6 recorder extension committed in commit `4e6a2b77a` (`feat(trace): add per-frame Tails CPU state recorder + parser plumbing`). The new per-frame `cpu_state` events would expose `Tails_CPU_routine`, `move_lock`, `Status_InAir`, and `Ctrl_2_logical` at every frame so the F1758 ROM path can be observed directly. Trace regeneration was attempted but blocked by a BizHawk chromeless headless emulation issue in this environment.
2. Without per-frame CPU state, alternative is to step through ROM in BizHawk for F1755-F1760 with breakpoints on `Player_SlopeRepel`, `sub_338C4`, and `loc_33ADE` to capture the exact ROM path firing.

### Removal Condition

Met: `TestS3kCnzTraceReplay`'s first strict error has advanced past F1758 to F1791.

---

## CNZ1 Trace F1791 — Tails CPU Auto-Jump Trigger Bit-7 Object Control Gate (FIXED)

**Status:** Fixed in iter-15 by porting ROM's bit-7 (`bmi.w`) object_control gate to the engine's `SidekickCpuController.updateNormal()` early-out. ROM `Tails_Normal` Part 2 entry at `sonic3k.asm:26672` does `tst.b object_control(a0); bmi.w loc_13EBE` — only the sign bit (bit 7) suppresses Tails_CPU_Control. Bits 0-6 (CNZ wire cage's `$42`, MGZ twisting loop's `$43`, etc.) leave the CPU dispatcher running so the auto-jump trigger at `loc_13E9C` (sonic3k.asm:26775) can keep firing while the player is held by the controlling object — that is what lets ROM write `Ctrl_2_logical = 0x7878` so the cage's `loc_33ADE` (sonic3k.asm:70052) reads jump-pressed and launches Tails with `x_vel = -0x800, y_vel = -0x200`.

**Location:** `SidekickCpuController.updateNormal()` early-out, `AbstractPlayableSprite.objectControlAllowsCpu` flag, `CnzWireCageObjectInstance.beginLatchedCooldown()` and `continueRide()` cooldown paths.

### Diagnosed Cause

Engine `setObjectControlled(true)` is a single boolean covering both ROM bit 7 (full CPU + physics suppression: flight `$81`, despawn marker `$81`, super state `$83`, debug `$83`) and ROM bits 0-6 (per-object physics ownership where CPU still runs: cage bits 1+6, MGZ twisting loop bits 0+1+6). Engine's CPU controller was treating both cases identically and skipping the auto-jump trigger when Tails was riding the cage, so `inputJumpPress` was never set, and the cage's `tryJumpRelease` (which polls `player.isJumpJustPressed()`) never fired.

### Fix

1. New `objectControlAllowsCpu` flag on `AbstractPlayableSprite` (default `false` to preserve existing engine behaviour for bit-7 callers). Cleared automatically by `setObjectControlled(false)`. Bits-0-6 callers must set the flag alongside `setObjectControlled(true)` each time they re-assert physics ownership.
2. `SidekickCpuController.updateNormal()` early-exits only when `isObjectControlled() && !isObjectControlAllowsCpu()`, mirroring ROM's sign-bit-only `bmi.w`.
3. `CnzWireCageObjectInstance` sets the flag in `beginLatchedCooldown()` (airborne capture) and the two cooldown branches of `continueRide()` (release-cooldown, low-speed continuation).

### Result

- First strict error advances F1791 → F1815 (5144 → 5080 errors).
- Cross-game baselines unchanged (S1 GHZ pass, S1 MZ1 F311, S2 EHZ F1151, S3K AIZ F2919).
- F1815 is a 1-frame landing-detection timing divergence (engine Tails lands 1 frame after ROM); engine and ROM converge from F1816 onwards.

### Removal Condition

Met: `TestS3kCnzTraceReplay`'s first strict error has advanced past F1791 to F1815.

---

## CNZ1 Trace F1740 — Wire Cage `restoreObjectLatchIfTerrainClearedIt` Overrode Slope-Repel Slip (FIXED)

**Status:** Fixed in iter-11 by short-circuiting `restoreObjectLatchIfTerrainClearedIt` when `move_lock > 0`.

ROM `Player_SlopeRepel` (sonic3k.asm:23907) has NO `Status_OnObj` gate — it runs even when on object, and slips the player into air (`bset #Status_InAir`) when |gSpeed| < `$280` at a steep angle. The cage's released path (`loc_33ADE` → `loc_33B1E` → `bne loc_33B62`) then honours the in_air bit and runs a simple release that preserves `y_vel = -gSpeed` (no launch impulse — that's `loc_339CC` which only fires from the active-ride branch when gSpeed >= `$300`).

Engine's slope repel did fire and set `air = true` correctly (S3K's `slopeRepelChecksOnObject = false` ensured the on-object gate didn't block it). But the cage's `restoreObjectLatchIfTerrainClearedIt` hack — added to compensate for the engine's terrain-probe being too aggressive about marking the player airborne under invisible level art — reverted it, keeping Tails on the cage. Adding a `move_lock > 0` short-circuit lets the cage honour the slope-repel slip while preserving the original hack's behaviour for the terrain-probe case (terrain probes don't set move_lock).

First strict error advanced F1740 → F1758.

---

## CNZ1 Trace F1685 — Tails CPU Spurious Despawn on Barber-Pole→Wire-Cage Object Switch (FIXED)

**Status:** Fixed in iter-10 by gating the engine's despawn-on-object-id-mismatch path behind `PhysicsFeatureSet.sidekickDespawnUsesObjectIdMismatch`. S3K disables the path because ROM's `sub_13EFC` (`sonic3k.asm:26823`) compares the high word of the cached vs current object's routine pointer (`cmp.w (a3),d0`), and all S3K gameplay objects share the same high word `0x0003`, making the ROM check effectively dormant. Engine was comparing 8-bit object IDs (`0x4D` barber pole vs `0x4E` wire cage) and triggering despawn on legitimate same-region transitions. S2 keeps the existing behaviour because `TailsCPU_CheckDespawn` (`s2.asm:39067`) genuinely does compare object id bytes.

**Location:** `SidekickCpuController.checkDespawn()`, `PhysicsFeatureSet.sidekickDespawnUsesObjectIdMismatch`.
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 1685.

### Symptom (pre-fix)

`tails_y_speed mismatch (expected=-0D51, actual=-0B22)` at F1685, plus a cluster of cascading mismatches because the engine had Tails despawned to `(0x7F00, 0)` while the ROM kept Tails alive on the wire cage.

### Diagnosed Cause

ROM `sub_13EFC` reads the FIRST WORD of the object's data structure (`(a3)` = high 16 bits of the routine pointer) and compares against the cached `Tails_CPU_interact`. For S3K, all gameplay objects live in `0x0001xxxx-0x0007xxxx`, so the high word is identical for virtually every object the player can be on; the check therefore almost never fires in real ROM play. Engine instead compared 8-bit object IDs, which DO differ between any two distinct object types — at F1685 Tails legitimately moved from the CNZ barber pole (id `0x4D`, routine `loc_335A8`) to a CNZ wire cage (id `0x4E`, routine `loc_3385E`). Both routines live at `0x000338xx`, so ROM's high-word comparison would yield `0x0003 == 0x0003` (no despawn); engine's id comparison yielded `0x4D != 0x4E` (spurious despawn).

### Removal Condition

Removed when `TestS3kCnzTraceReplay`'s first strict error advanced past F1685.

---

## AIZ1 Trace F2667 — Sidekick vs. Spike Side-Push Triggers Where ROM Does Not (FIXED)

**Status:** Fixed in iter-12 by adding the ROM `SolidObject_cont` on-screen
gate to `ObjectManager.SolidContacts.processInlineObjectForPlayer`,
gated for S3K via `PhysicsFeatureSet.solidObjectOffscreenGate`. The v6.2-s3k
recorder added per-frame `object_state` and `interact_state` events that
made the camera-vs-spike geometry directly inspectable; with those events
the ROM control flow at F2667 became unambiguous. First strict error
advanced from F2667 to F2721 (a downstream Tails CPU-AI jump-trigger
divergence) without touching S1/S2 trace baselines.

**Location:** `ObjectManager.SolidContacts.processInlineObjectForPlayer` /
`resolveContactInternal` side-path zeroing on the AIZ1 top-spike
(`Sonic3kSpikeObjectInstance` slot 22, ROM `loc_24090` at
sonic3k.asm:49011).
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`,
first strict error at frame 2667 (after the iter-10 F2590 fix).

### Iter-12 Diagnosis (correct)

The v6.2-s3k recorder per-frame events at F2667 showed:

* Slot 22 spike: `x=0x1C80, y=0x03C0, status=0x00` (no p2_standing bit).
* Tails: `interact=0xB65C` (= slot 22, sticky from prior `sub_24280` hurt
  path), `object_control=0x00`.
* Camera at F2667: `x=0x1C99, y=0x035A`.
* Spike right edge in screen space: `0x1C80 + 0x10 - 0x1C99 = -9` -- the
  spike is fully off-screen left.

Hand-tracing ROM `Render_Sprites` (sonic3k.asm:36336-36370) confirms that
when the spike was last rendered (end of F2666) its bounding-box right
edge was already to the left of `Camera_X_pos_copy`, so Render_Sprites
cleared `render_flags` bit 7 via `bmi.s Render_Sprites_NextObj`.

At F2667 Obj_Spikes runs `loc_24090 -> SolidObjectFull -> SolidObjectFull_1P`
for Player_2.  With slot 22's standing bit clear, control reaches
`loc_1DF88` (sonic3k.asm:41390) which executes `tst.b render_flags(a0);
bpl.w loc_1E0A2`. Bit 7 is clear -> the branch is taken to
`loc_1E0A2 -> sub_1E0C2` (sonic3k.asm:41528) which only clears the
push-state bookkeeping and exits with `moveq #0, d4`. The collision /
side-push branches (`SolidObject_cont` at 41394, `loc_1E042` at 41468)
are not entered, so ROM never zeroes Tails's `ground_vel` or `x_vel`.
ROM CSV is consistent: tails_x_speed accelerates uninterrupted through
0x01B0 -> 0x01BC -> 0x01C8 across F2666-F2668 with no zeroing.

The S2 disassembly even documents this gate:

> SolidObject_OnScreenTest:
>   ; If the object is not on-screen, then don't try to collide with it.
>   ; This is presumably an optimisation, but this means that if Sonic
>   ; outruns the screen then he can phase through solid objects.
>   _btst #render_flags.on_screen,render_flags(a0)
>   _beq.w SolidObject_TestClearPush

(s2.asm:35140-35145.) S1 has the same shape via `Solid_ChkEnter` at
`s1disasm/_incObj/sub SolidObject.asm:124-126`.

### Iter-12 Fix

* Added `ObjectInstance.isWithinSolidContactBounds()` (default true) and an
  `AbstractObjectInstance` override that mirrors ROM `Render_Sprites`'s
  bit-7 semantic via `cameraBounds.contains(getX(), getY(), 16)` -- the
  16-pixel margin matches the typical ROM `width_pixels` for gameplay
  solids and avoids depending on `SolidObjectParams.halfWidth` (which
  reflects collision halfwidth, not render extent).
* Added `PhysicsFeatureSet.solidObjectOffscreenGate` (S3K=true, S1/S2=false
  for now to keep current trace baselines stable while the on-screen
  semantic is validated game-by-game).
* Inserted the gate in
  `ObjectManager.SolidContacts.processInlineObjectForPlayer`, after the
  riding-state branch (so MvSonicOnPtfm-equivalent platform carry stays
  unaffected) and before `resolveContact`. When the gate fires, only
  `provider.setPlayerPushing(player, false)` runs, mirroring ROM
  `sub_1E0C2`.

### Removal Condition

This entry remains as the iter-12 retrospective; the F2667 entry can be
deleted in a later cleanup pass. The S1/S2 false setting on
`solidObjectOffscreenGate` is intentional and should be revisited when
the S1 GHZ1 / MZ1 / S2 EHZ1 trace baselines are re-grounded.

### Recorder Inputs (iter-12)

The F2590 fly-back-exit-gate fix advanced the first-divergence frame to
F2667. Side-log probe instrumentation (temporary, removed) confirmed:

* Engine Tails position matches ROM exactly from the F2640 hurt-bounce
  landing through F2666 (running rightward with the same accelerating
  `g_speed`/`x_speed = 0x01B0 → 0x01A4 → ...` per frame).
* At engine fc=2378 (= trace F2667), engine sees Tails enter the spike's
  collision box at `playerX = 0x1C67` (spike x = 0x1C80, halfWidth = 27,
  `relX_raw = 2`). `resolveContactInternal` classifies as side contact
  (X-overlap = 2, Y-overlap = 31, Y > X) → side path → zeroes both
  `xSpeed` and `gSpeed`, snaps player back to `0x1C65`, sets `pushing=true`.
* ROM CSV at F2667 shows Tails kept moving (`x_speed = 0x01BC`,
  `g_speed = 0x01BC`) with no stop — implying ROM took a code path that
  preserved velocity.

### Open question

Hand-tracing ROM `SolidObject_cont` (sonic3k.asm:41394) with the same
inputs (`d0=2, d1=halfW=27, d2=height=16, d3=pY-oY+4=4, d4=spikeX,
default_y_radius=15, y_radius=15`) follows the same algebra to
`loc_1E034` and falls into `loc_1E042` (side path). At `tst.w d0` the
register is non-zero (positive, 2), `bmi` not taken, `tst.w x_vel`
positive, so `bra loc_1E056` zeros `ground_vel` and `x_vel` — exactly
the engine's behaviour. ROM's `interact(a0)` is set to slot 22 from
F2590's `sub_24280` hurt path (the recorder reports
`sidekick_stand_on_obj=22` continuously through F2640-F2670), but
`Status_OnObj` (bit 3) is cleared in the recorded `sidekick_status_byte`
at every post-landing frame.

The discrepancy implies one of:
1. ROM's spike (slot 22) is not running its `loc_24090` body at F2667
   (some out-of-range / proximity gate the engine doesn't replicate;
   `Sprite_OnScreen_Test2` at sonic3k.asm:37281 deletes the sprite when
   `(x_pos & 0xFF80) - Camera_X_pos_coarse_back > 0x280`, but the
   trace's `object_appeared`/`object_removed` events on slot 22 show it
   alive from F2474 to F2774, spanning F2667).
2. ROM has the spike's `p2_standing_bit` set from a path I haven't
   traced (e.g. an earlier frame's `loc_1E154` landing branch with
   `d3 ∈ [0, 0x10)` and player y_vel ≥ 0 calling `RideObject_SetRide`).
   Hand-tracing F2640 onwards the d3 transformations stay negative
   (Tails y > spike y), so the standing path is never entered through
   the conventional landing flow visible in the disassembly.
3. The `Sonic_AnglePos` re-grounding pass between Tails' physics and
   the spike's update sets `interact` and the spike's standing-bit via
   a path outside `SolidObjectFull` (e.g. ground collision detecting
   the spike as a solid surface and routing through a different solid
   handler).

### Attempted Fixes (reverted)

- **Iter-11a:** Added `ObjectManager.getObjectAtSlot(int)` and
  `ObjectManager.setRidingObject(player, instance, pieceIndex)` plus a
  call from `applyRecordedFirstSidekickState` that hydrates engine
  `SolidContacts.ridingStates` from the recorded
  `sidekick_stand_on_obj` slot whenever the recorded
  `Status_OnObj` bit (`statusByte & 0x08`) is set. The bit is never
  set for Tails post-F2640 in this trace, so the gate never fired —
  no measurable effect on F2667. Reverted to keep the diff minimal.
- **Iter-11b:** Same hydration with the gate dropped (always set when
  `standOnObj > 0`). Made things slightly worse (1208 → 1212 errors)
  by registering riding state during the F2590-F2640 hurt-bounce when
  ROM's `interact` field still pointed at the spike from `sub_24280`
  but Tails was airborne. Reverted.
- **Iter-11c:** Removed the `distX==0 && movingInto → zero speed`
  block in `resolveContactInternal`'s pre-movement side path (S2/S3K
  branch only). Did not affect F2667 because the `relX = 2` (not 0)
  at the divergent frame still routes to the standard
  `distX != 0 && movingInto` zero. Reverted.

The v6.2-s3k recorder bumped from v6.0-s3k by adding two diagnostic
event types (additive, no schema bump):

* `object_state` -- per-frame snapshot for every OST slot within
  `OBJECT_PROXIMITY` (160 px) of either Player_1 or Player_2: routine
  pointer, status byte (offset $22), subtype (offset $1C), x_pos /
  y_pos / x_radius / y_radius. For the Obj_Spikes top-facing variant
  ($24090) bits 3 and 4 of `status` reflect `p1_standing` /
  `p2_standing` (sonic3k.constants.asm
  `standing_mask = p1_standing|p2_standing`).
* `interact_state` -- per-frame snapshot per active player: `interact`
  field at offset $42 (RAM address of last object stood on, set by
  `RideObject_SetRide` / `loc_1E154`), the resolved OST slot index,
  and `object_control` (offset $2A) -- bit 7 set causes ROM
  `SolidObject_cont` (sonic3k.asm:41439) to take the `bmi.w loc_1E0A2`
  branch which skips side-push velocity zeroing.

The diagnostic events are advertised via `aux_schema_extras`
(`object_state_per_frame`, `interact_state_per_frame`) so parsers can
query them with `TraceMetadata.hasPerFrameObjectState()` /
`hasPerFrameInteractState()`. The events are READ-ONLY diagnostic input
to the trace-replay test; they are NOT hydrated into engine object state
(per the comparison-only invariant of trace replay). The fix uses them
to identify the ROM control-flow divergence and then matches ROM
natively in engine code.

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


---

## AIZ1 Trace F2202 -- Phantom MonkeyDude Respawn Triggers Spurious Sidekick Bounce (FIXED)

**Location:** `ObjectManager.Placement` (S3K spawn windowing) /
`AbstractS3kBadnikInstance.defeat` -> `removeFromActiveSpawns`.

**Symptom (resolved):** `TestS3kAizTraceReplay#replayMatchesTrace` first
strict divergence at frame 2202: `tails_y_speed expected=-0x3E0,
actual=-0x2E0` (delta `+0x100` = an extra enemy-defeat bounce). Tails
was at `(0x1845, 0x0421)` flying past a MonkeyDude that ROM destroyed at
F1722 via Sonic. ROM has the MonkeyDude permanently absent from this
spawn point through F2202; the engine had it alive again, so when Tails
overlapped it, the engine applied the `+0x100` bounce ROM never fires.

**Root cause (confirmed):** The engine's placement system was clearing
`destroyedInWindow` whenever a destroyed spawn's position fell outside
the live placement window (called from `trimLeftCountered`,
`trimRightCountered`, `trimLeftNonCounter`, `trimRightNonCounter`,
`refreshWindow`, `refreshCounterBased`, and the post-window-update else
branch in `update`). ROM's equivalent flag is bit 7 of
`Object_respawn_table[respawn_index]`, set by the cursor spawn helpers
(`sonic3k.asm` `loc_1BA40` / `loc_1BA92` / `sub_1BA0C` `bset #7,(a3)`)
and cleared **only** when a still-alive object self-destructs via
`Sprite_OnScreen_Test` / `Delete_Sprite_If_Not_In_Range` /
`Sprite_CheckDeleteTouch` (`sonic3k.asm:37271-37388` family --
`bclr #7,(a2)`). Once a player kill replaces the badnik with
`Obj_Explosion`, that path never runs, so bit 7 stays set permanently
until level init wipes the table at `loc_1B784`. Mirroring this, the
engine now leaves `destroyedInWindow` latched after a kill and only
clears it on level reset; `dormant` continues to handle the
alive-offscreen out-of-range case.

**Discovered via:** Fix for AIZ trace F3834 (sidekick enemy-bounce
self-destroy gating) which previously masked this divergence by
incorrectly skipping the bounce whenever Tails herself destroyed the
phantom badnik.

**Resolution:** `ObjectManager.Placement.permanentDestroyLatch` was
added (enabled via `ObjectManager.enablePermanentDestroyLatch()` in
`LevelManager` when `gameModule.getGameId() == GameId.S3K`).
`removeFromActive` only sets `destroyedInWindow` when the flag is on,
and the helper `clearDestroyedLatchOutsideWindow` was removed entirely
so the latch never clears mid-level. S1 / S2 do NOT enable the flag --
their ROM `ObjectsManager_Main` (docs/s2disasm/s2.asm:33402,
`tst.b 2(a0); bpl.s +`) only latches respawn-tracked spawns, modeled
by the engine's separate `remembered` flag, so non-tracked S1 / S2
spawns continue to re-spawn on cursor re-entry. After the fix
`TestS3kAizTraceReplay` first strict error advances past F2202 to
F4679 (a separate Tails respawn divergence). All baselines stay green:
S1 GHZ PASS, S1 MZ1 F311, S2 EHZ F1151, S3K CNZ F1815.

---

## AIZ Trace F5736 — Level_frame_counter Skips Tick on Seamless Act Reload (FIXED)

**Status:** Fixed by bumping `SpriteManager.frameCounter` by 1 inside
`LevelManager.applySeamlessTransition()` for `RELOAD_SAME_LEVEL` and
`RELOAD_TARGET_LEVEL` transitions.

**Location:** `LevelManager.applySeamlessTransition()` /
`LevelManager.advanceFrameCounterAcrossSeamlessReload()`,
`SpriteManager.frameCounter`,
`SidekickCpuController.updateNormal()` (loc_13E9C jump cadence gate).
**ROM Reference:** `sonic3k.asm` `VInt_0_Main` (Level_frame_counter
unconditionally incremented every gameplay frame),
`sonic3k.asm:26775 loc_13E9C` (Tails CPU 64-frame jump cadence gate
reading `(Level_frame_counter & $3F)`).

### Pre-Fix Symptom

`TestS3kAizTraceReplay#replayMatchesTrace` first error at trace frame
5736:
```
tails_x_speed mismatch (expected=-00C4, actual=0x000C)
tails_y_speed mismatch (expected=-0680, actual=0x0000)
tails_air mismatch (expected=1, actual=0)
tails_rolling mismatch (expected=1, actual=0)
```

ROM Tails was stuck pushing against AIZ2 terrain (status_byte=0x20
across F5712-F5735) and the `loc_13E9C` 64-frame jump cadence gate
fired at gfc=0x1540 (`& 0x3F == 0`), launching Tails out of his stuck
state with the `Tails_Jump` initial velocity `y_vel = -$680`. Engine
Tails was also stuck pushing but never triggered the jump.

### Diagnosed Cause

After AIZ act 1 → act 2 reload (F5496) the engine's
`SpriteManager.frameCounter` lagged ROM's `Level_frame_counter` by
exactly 1 frame for the rest of the trace. `Level_frame_counter` is
incremented in ROM's VBlank handler unconditionally every gameplay
frame, including the act-reload frame. The engine equivalent
(`SpriteManager.frameCounter`) only ticks inside `SpriteManager.update()`,
which is itself called from `LevelFrameStep.execute()`. Both `GameLoop`
and `HeadlessTestRunner.stepFrame()` `return` early after applying a
seamless transition, never reaching `LevelFrameStep.execute()`, so the
counter does not tick on the reload frame.

The cumulative effect: the engine's `(frameCounter & 0x3F)` was 0x3F
when ROM's was 0, so the Tails-CPU auto-jump cadence gate stayed shut
on every frame thereafter.

### Fix

`LevelManager.applySeamlessTransition()` now calls
`advanceFrameCounterAcrossSeamlessReload()` after `executeActTransition()`
for `RELOAD_SAME_LEVEL` and `RELOAD_TARGET_LEVEL`. The helper bumps
`SpriteManager.frameCounter` by 1 to mirror the ROM VBlank handler tick
the engine otherwise misses.

`MUTATE_ONLY` transitions are intentionally NOT patched: the AIZ1
fire-transition art-overlay path (`Sonic3kAIZEvents.applyFireTransitionMutation`)
runs mid-frame without skipping the rest of the gameplay loop, so it
must not double-tick the counter.

### Test Results

After the fix `TestS3kAizTraceReplay` first strict error advances past
F5736 to F6066 (a separate Sonic-only divergence in AIZ2 main gameplay),
total errors drop from 1184 to 1178. All cross-game baselines stay
green: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ F1151, S3K CNZ F2175.

---

## AIZ Trace F6066 — CaterKillerJr Missed Obj_WaitOffscreen Gate (FIXED)

**Location:** `src/main/java/com/openggf/game/sonic3k/objects/badniks/CaterkillerJrHeadInstance.java`
**Trace:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/`
**Symptom:** ROM at trace F6066 has Sonic on the ground in AIZ2 narrow
corridor running left at `g_speed=-0x98`, `air=0`, `status=0x01`.
Engine has Sonic in air with `x_speed=-0x200`, `y_speed=-0x400`,
`g_speed=0`, `air=1`, `status=0x06` — exact `applyHurt(NORMAL)`
knockback signature (sourceX > centreX → x dir = -1 → x_speed =
0x200 \* -1 = -0x200; y_speed = -0x400 hardcoded).
Stack trace: `runTouchResponsesForPlayer →
TouchResponses.processCollisionLoop → handleTouchResponse → applyHurt`
with `hitter=CaterkillerJrHeadInstance objId=8F x=0x07EF y=0x0326`.

### Root Cause

ROM `Obj_CaterKillerJr` at `sonic3k.asm:183317-183323` begins:
```
Obj_CaterKillerJr:
    jsr (Obj_WaitOffscreen).l
    moveq #0,d0
    move.b routine(a0),d0
    move.w CaterKillerJr_Index(pc,d0.w),d1
    jsr CaterKillerJr_Index(pc,d1.w)
    jmp Sprite_CheckDeleteTouch(pc)
```

`Obj_WaitOffscreen` (`sonic3k.asm:180266-180297`) replaces the object's
`(a0)` code pointer with `loc_85AD2` and saves the original return
address at `$34`. `loc_85AD2` runs every frame thereafter:

```
loc_85AD2:
    tst.b render_flags(a0)
    bmi.s loc_85B02       ; on-screen X (bit 7 set) → restore real code
    move.w x_pos(a0),d0
    andi.w #$FF80,d0
    sub.w (Camera_X_pos_coarse_back).w,d0
    cmpi.w #$280,d0
    bhi.s loc_85AF0        ; too far → delete
    jmp (Draw_Sprite).l    ; otherwise just draw the offscreen indicator
```

So the badnik enters a frozen "wait" state (no swing, no movement) until
the camera catches up and the on-screen X bit gets set.

The S3K placement cursor advances 1 chunk per frame, allocating slots
at the chunk-boundary transition (cam_x crossing 0x80 boundaries).
Because S3K loads its window at `cameraChunk + 0x280`, the cursor
allocates the CaterKillerJr's slot ~40 frames before the camera reaches
the spawn x. ROM stays in the offscreen wait the whole pre-roll.

The engine's `CaterkillerJrHeadInstance.update()` had no equivalent
gate — the swing state machine ran from spawn frame, so the badnik
moved at `-0x100` x-velocity for those ~40 extra frames and ended up
~0x29 (41) pixels further left than ROM by the time Sonic arrived.

This matters in the AIZ2 narrow corridor at spawn x=0x0850 (camera
window crossing chunk 0x0600 at gfc=5670 vs ROM at gfc=5713). The
displaced engine badnik happens to overlap Sonic at gfc=5770 (trace
F6066), triggering hurt; the ROM badnik is still 41 px to the right.

### Fix

`CaterkillerJrHeadInstance.update()` now early-returns when
`!isOnScreenX()`. Other AIZ badniks already had this guard
(`BlastoidBadnikInstance`, `BuggernautBadnikInstance`,
`MonkeyDudeBadnikInstance`, `BatbotBadnikInstance`,
`TunnelbotBadnikInstance`) — CaterKillerJr was the missing case.

The fix does not affect body segments. Their ROM entry point
(`loc_8778C` at `sonic3k.asm:183389-183395`) does **not** call
`Obj_WaitOffscreen`; bodies always run, ticking `waitTimer` per frame
even off-screen. The engine's `CaterkillerJrBodyInstance` already
matches that.

### Test Results

After the fix `TestS3kAizTraceReplay` first strict error advances from
F6066 to F6255 (separate Tails despawn handoff). Total errors rise from
1178 to 6782 because the test no longer cascades from a single early
hurt event and now exposes downstream Tails AI divergences.
All cross-game baselines stay green: S1 GHZ PASS, S1 MZ1 F311, S2 EHZ
F1151, S3K CNZ F2222.

---

## CNZ1 Trace F2222 — Wire Cage Sidekick JUMP_RELEASE Spurious Fire (FIXED via ROM `d6` register corruption-by-`Perform_Player_DPLC` model)

**Status:** FIXED in v6.3-s3k iteration via post-leader-release sidekick
gate in `CnzWireCageObjectInstance`. Engine
Tails gets launched off the cage with `x_vel=-0x800, y_vel=-0x200`
(`JUMP_RELEASE_X/Y_SPEED`) at F2222 while ROM Tails stays on the cage.

**Location:** `CnzWireCageObjectInstance.tryJumpRelease()` reads
`player.isJumpJustPressed()` which is engine edge-detection on the
sidekick's `inputJump` (= `(recordedInput & INPUT_JUMP) != 0`). For
sidekicks, `recordedInput` is read from leader (Sonic's)
`inputHistory[historyPos - 16]` populated each frame from the active
player's effective post-`controlLocked` `logicalInputState`.

**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict
error at frame 2222 (`tails_y_speed expected=-02EA actual=-0200`).

### Symptom

Sequence around the divergence (CSV / aux from v6.2 recorder):

| K (trace) | gfc    | Sonic input | Sonic on cage | Engine cage | ROM cage |
|-----------|--------|-------------|---------------|-------------|----------|
| 2204      | 0x089D | 0x08 RIGHT  | yes (status=08, OnObj=04) | latched     | latched |
| 2205      | 0x089E | 0x18 RIGHT+JUMP | released (status=02) | release fires (-800/-200) | release fires (-800/-200) |
| 2206      | 0x089F | 0x18 RIGHT+JUMP | airborne | airborne | airborne |
| ...       |        |             |               |             |          |
| 2221      | 0x08AE | 0x08 RIGHT  | airborne | Tails on cage, y_vel=-02EA | Tails on cage, y_vel=-02EA |
| 2222      | 0x08AF | 0x08 RIGHT  | airborne | **Tails LAUNCHED y_vel=-0200, air=1** | Tails on cage, y_vel=-02EA |

v6.2 aux `cpu_state` events for Tails confirm:
- F2221: `cpu_routine=6` (NORMAL), `ctrl2_held=0x48`, `ctrl2_pressed=0x48` (RIGHT+button_A pressed)
- F2222: `cpu_routine=6`, `ctrl2_held=0x48`, `ctrl2_pressed=0x08` (RIGHT only, NOT pressed)

### Diagnosed Cause (engine side)

Engine `recordedInput[K-16]` for Tails at iter K=2222 reads Sonic's
`logicalInputState` from iter K=2206. `logicalInputState` is computed
in `SpriteManager.publishInputState` from `effectiveJump =
(!controlLocked && space) || forcedJump`, i.e. it filters out jump
when the per-sprite `controlLocked` (engine analogue of ROM
`object_control bit 0`) is set.

ROM `Sonic_RecordPos` at sonic3k.asm:22132 records `Ctrl_1_logical`
unconditionally — only the GLOBAL `Ctrl_1_locked` (cutscenes / death,
sonic3k.asm:21542) blocks the recording, never the per-sprite
`object_control bit 0`. So ROM Stat_table for K=2204 has JUMP cleared
(BK2 had no jump pressed) but K=2205 has JUMP set (newly pressed) and
K=2206+ has JUMP set (held).

In the engine, Sonic enters cage cooldown at K=2192 (`gspeed=0x02F4 <
0x300`) which calls `setControlLocked(true)`. After that, every frame
through K=2204 stays in `state.cooldown!=0` → `tryJumpRelease` +
`updateReleaseRide`, which never re-call `setControlLocked(false)`.
Sonic stays controlLocked until the cage's `release()` path fires at
K=2205 (which clears it). Meanwhile every frame K=2192..K=2204 the
engine records `effectiveJump=false` into `inputHistory[K]`.

This produces a 1-frame skewed JUMP edge: engine `inputHistory[2204]`
= no JUMP, `inputHistory[2205]` = no JUMP (controlLocked still true
when publishInputState ran at iter 2205), `inputHistory[2206]` = JUMP
(controlLocked cleared by cage release at end of iter 2205).

Tails's edge detection at iter K=2222 (reading K=2206) sees a
false-to-true transition between iter K=2221 (reads K=2205, no JUMP)
and iter K=2222 (reads K=2206, JUMP). `jumpInputJustPressed=true`,
the cage's `tryJumpRelease` fires `releaseWithJumpImpulse`.

### Mystery (ROM side, unresolved)

Even with raw input recording (held-only, ignoring controlLocked) the
edge would shift to iter K=2221 (reads K=2205 = JUMP newly pressed)
which still does not match ROM. The v6.2 aux at F2221 shows
`ctrl2_pressed=0x48` (button_A bit 0x40 set), so ROM cage's
`andi.w #button_A_mask|button_B_mask|button_C_mask, d5; beq.s
loc_33B1E` (sonic3k.asm:70055) computes `0x4848 & 0x0070 = 0x0040`
— non-zero, which by my reading should branch to release at
sonic3k.asm:70057. But ROM CSV at K=2221 still shows
`tails_y_speed=-02EA` (cage ride speed, NOT release).

I exhaustively checked: `Tails_CPU_routine=6` (NORMAL, runs
TailsCPU_Normal not a Ctrl_2_logical zero-er), `cage cooldown=1` should
direct execution to `loc_33ADE`, no FixBugs path differences, no
`Tails_CPU_idle_timer` block (=0 throughout), no Player_2 handling
divergence (`a0=Player_2`, `bne.s loc_13830` taken), no
`Flying_carrying_Sonic_flag`. The trace reading and disassembly
suggest ROM cage SHOULD release Tails at F2221 — yet ROM definitively
does not.

### What Was Tried This Iteration

1. **Probe added** to `tryJumpRelease` and `SpriteManager` to log
   `aiJump`, `jumpJustPressed`, `forcedJumpPress`, `objectControlled`,
   `cooldown` per frame for Tails at frames 0x08AC-0x08B0. Confirmed:
   - aiJump first becomes true at engine frame 0x08AF (iter K=2222),
     not K=2221.
   - jumpJustPressed=true exactly once, at iter K=2222.
   - cage's tryJumpRelease only fires at iter K=2222.
2. **Raw input recording attempt:** modified
   `SpriteManager.publishInputState` to bypass per-sprite
   `controlLocked` filter and pass raw-or-forced inputs to
   `setLogicalInputState`. Result: divergence regressed from F2222 to
   F2221 (engine fires release one frame earlier — the same edge,
   shifted by Sonic's controlLocked filter being ON at K=2204 but OFF
   at K=2205 in raw-input world). Reverted.
3. **Trace regenerated** from v6.1-s3k schema to v6.2-s3k to obtain
   `cpu_state_per_frame` (Tails_CPU_routine, ctrl2_held, ctrl2_pressed)
   and `interact_state_per_frame` aux events. v6.2 confirmed
   `ctrl2_pressed=0x48` at F2221 and ROM still does not release —
   fundamentally contradicting my reading of the cage code.

### What's Likely Needed

A real fix needs ROM-side instrumentation that I don't have without a
debugger or live disassembly run, e.g.:

1. **Verify cage execution at F2221:** confirm cage's `loc_33ADE` is
   actually reached (vs being skipped because of `Delete_Sprite_If_Not_In_Range`
   trimming or some d6/standing-flag clear after Sonic's K=2205 release
   path that I'm missing).
2. **Verify d5 register at the moment of `andi #$70`:** capture d5 in
   ROM directly (BizHawk Lua `event.onmemoryexecute(0x33ADE, ...)`)
   to confirm it really has the 0x40 bit set when the andi runs, vs.
   some intermediate state where Tails CPU writes Ctrl_2_logical with
   different masks.
3. **Audit ROM cage `1(a2)` cooldown lifecycle for Tails:** maybe the
   cooldown is decrementing into 0 between frames in some path I
   haven't traced, which would make `loc_339A0`'s `tst.b 1(a2)` fall
   through to the gspeed check (which would then re-set cooldown=1
   and bra loc_33ADE — but then the andi check happens with the same
   d5).

### Recorder Bug Found

The v6.2 recorder writes `interact_state.object_control` reading from
player offset `0x2A`. Per `sonic3k.constants.asm:30/57`, offset `0x2A`
is **status**, not `object_control` (which is at `0x2E`). The reported
"object_control" values in v6.2 traces are actually status bytes —
this needs to be fixed in `tools/bizhawk/s3k_trace_recorder.lua`
before the diagnostic is reliable for object_control gating.

### Removal Condition

Resolve once `TestS3kCnzTraceReplay`'s first strict error advances
past F2222, with a fix backed by ROM-confirmed (not assumed) cage
execution path for the iter K=2221/K=2222 boundary.

### Resolution (v6.3-s3k Recorder + ROM-Side Memoryexecute Hook Diagnostics)

The fix landed when the v6.3-s3k recorder added BizHawk Lua
`event.onmemoryexecute` hooks at every cage routine entry
(`sub_338C4=0x338C4`, `loc_339A0=0x339A0`, `loc_33ADE=0x33ADE`,
`loc_33B1E=0x33B1E`, `loc_33B62=0x33B62`) and emitted a `cage_execution`
aux event per frame with the captured M68K register state
(`a0/a1/a2/d5/d6`), the per-player state byte `1(a2)`, and the
player's status / `object_control` bytes. Inspection of those events
across F2136-F2222 revealed:

1. **The cage's `d6` register is corrupted by the original ROM
   `Obj_CNZWireCage` bug.** With `FixBugs` disabled (the original ROM),
   the second `bsr.s sub_338C4` call uses
   `addq.b #p2_standing_bit-p1_standing_bit,d6` (sonic3k.asm:69843)
   to derive the Player_2 standing-bit mask rather than reloading
   `d6` cleanly. This carries whatever value `d6` had after the
   Player_1 call's `Perform_Player_DPLC` corruption. While the
   leader is actively rotating in `loc_33A6A` (sonic3k.asm:70016) /
   `loc_33BAA` (sonic3k.asm:70121), `Perform_Player_DPLC` runs and
   corrupts `d6 = 0`; the `addq.b #1,d6` then makes
   `d6 = 1` for Tails so `bset d6,status(a0)` in `sub_33C34`
   (sonic3k.asm:70181) sets bit 1 of the cage's status rather than
   `p2_standing_bit = 4`.
2. **F2136 capture of Tails:** `cage_execution` shows the cage hits
   `sub_338C4_entry` for Tails with `d6=0x01` and the resulting
   `bset d6,status(a0)` writes bit 1 to the cage's status byte
   (`cage_status` transitions from `0x09 = bits 0+3` to
   `0x0B = bits 0+1+3` over two frames). Tails's `object_control`
   becomes `0x42` (bits 6+1, set by `loc_3397A` at sonic3k.asm:69937).
3. **F2200 last cage-process for Tails:** while the leader was still
   in `loc_33B1E_continue` mode (sonic3k.asm:70070), `Perform_Player_DPLC`
   could still corrupt `d6` to 1 on some frames and the cage would
   process Tails through `loc_339A0_mounted` → `loc_33ADE_cooldown` →
   `loc_33B1E_continue`. F2200 was the last such frame; after that,
   Sonic's cage state byte hit `loc_33B62_release` (sonic3k.asm:70092)
   at F2205 and the cage's per-frame entry for Sonic switched to
   the cooldown-decrement-only path that does not call
   `Perform_Player_DPLC`.
4. **F2206-F2222 "ghost" state:** `cage_execution` shows the cage
   only hits `sub_338C4_entry` for Tails with `d6=0x04` (the
   correct `p2_standing_bit` value, no `Perform_Player_DPLC`
   corruption). `btst d6,status(a0)` reads bit 4 of `cage_status`
   which is clear (the cage's Tails standing flag is at bit 1, set
   by the `d6=1` corruption-bit at original capture). `bne.w loc_339A0`
   is not taken; falls through to capture-attempt at `loc_338D8`
   (sonic3k.asm:69881). The capture-attempt's
   `tst.b object_control(a1); bne.w locret_3399E`
   (sonic3k.asm:69896) exits immediately because Tails's
   `object_control = 0x43` retains the bits 6+1+0 from the
   F2136 capture sequence. ROM cage does **nothing** for Tails on
   these frames.
5. **F2262 ROM exit from stuck state:** `Tails_CPU_flight_timer`
   reaches `5*60 = 300` (per sonic3k.asm:26829), `sub_13ECA` warps
   Tails to `(0x7F00, 0)` and resets the CPU routine.

The engine doesn't model `Perform_Player_DPLC`'s `d6` corruption
side-effect, so the engine cage's mounted-mode logic ran every frame
the cage was loaded and `state.latched=true`. When `Tails_CPU`'s
auto-jump fired at F2221, the engine cage's `tryJumpRelease` saw
`isJumpJustPressed()=true` and fired `releaseWithJumpImpulse` at
F2222.

**Fix landed:** `CnzWireCageObjectInstance` now tracks
`leaderHasReleased` (set when the leader's per-frame processing
transitions `state.latched` from true to false). When the leader has
released and the engine is processing the sidekick, `continueRide`
short-circuits with `setObjectControlled(true)` /
`setObjectControlAllowsCpu(true)` preserved (matching ROM's persistent
`object_control = 0x43` marker on Tails). The sidekick stays in
stuck-frozen state, awaiting the `Tails_CPU_flight_timer` despawn
warp that frees her.

`TestS3kCnzTraceReplay` first strict error advances F2222 → F2262.
The new error is the `Tails_CPU` despawn warp at F2262 — engine's
`SidekickCpuController.despawnCounter` does not match ROM's
`Tails_CPU_flight_timer` because the engine resets the counter
whenever the sidekick is on-screen, while ROM checks
`render_flags bit 7` which can be cleared even for an on-screen
sidekick. That is the next iteration's concern, separate from the
F2222 cage bug.

---

## AIZ Trace F6255 — Tails CPU Freed-Slot Despawn (RESOLVED)

**Status:** Resolved. AIZ first strict error advances past F6255 to F6313 (a downstream sidekick AI divergence). Two engine fixes landed in this round:

1. **Top-only solids bypass the off-screen gate** — `ObjectManager.processInlineObjectForPlayer` now skips the `solidObjectOffscreenGate` for `provider.isTopSolidOnly()` providers, matching ROM. The ROM gate at `loc_1DF88` (sonic3k.asm:41390-41392) lives only in `SolidObjectFull_1P` (sonic3k.asm:41016-41018); the top-only routines `SolidObjectTop_1P` / `SolidObjectTopSloped_1P` / `SolidObjectTopSloped2_1P` (sonic3k.asm:41793, 41887, 41840) all branch directly into `loc_1E42E` / `SolidObjCheckSloped` / `SolidObjCheckSloped2` (sonic3k.asm:41982, 42095, 42071) without testing `render_flags(a0)`. The S2 sloped path (`SlopedSolid_SingleCharacter` -> `SlopedSolid_cont` at s2.asm:34927-34952, 35066) bypasses the on-screen test the same way. Without this exemption, Tails never resolved a sloped-top contact for the AIZ2 collapsing platform at x=0x08B0 — the platform's bbox right edge (0x90C) sat 0xD5 px past the camera left edge (0x985) at the moment Tails should have landed, so the existing universal off-screen gate dropped the contact and `setLatchedSolidObject(slot=16)` never fired.

2. **Collapsing-platform `Sprite_OnScreen_Test` reads cam_X directly** — `Sonic3kCollapsingPlatformObjectInstance.spriteOnScreenTestPasses()` now reads `services().camera().getX()` at call time instead of consulting a previous-frame cache. The round-13 cache was based on the mistaken premise that ROM `Load_Sprites` runs AFTER `Process_Sprites`; the disassembly (sonic3k.asm:7893 `jsr Load_Sprites`; 7894 `jsr Process_Sprites`; 7897 `jsr DeformBgLayer`) shows the real order is Load_Sprites -> Process_Sprites -> DeformBgLayer, so `Camera_X_pos_coarse_back` at frame N's Process_Sprites reflects `Camera_X_pos` at the start of frame N (= end of N-1, since DeformBgLayer is the per-frame camera tracker). The engine's `LevelFrameStep` mirrors that order by-construction (objects step 4, camera-update step 5), so a direct read at the platform's `update()` already supplies the correct value. The round-13 cache pulled cam_X from too far in the past and let the platform's destruction lag ROM by one frame, which in turn delayed the freed-slot despawn at F6255 by one frame.

With both fixes, Tails now lands on the platform at F6251, the platform destroys at F6254 (ROM-matching), and the freed-slot detection warps Tails to (0x7F00, 0) at F6255. AIZ trace error count holds steady (1960 vs 1959 prior); the first strict error now sits at F6313 with a sidekick AI divergence further downstream.

**Original problem (PARTIAL state in round 13):**

Platform lifecycle was ROM-aligned via `isPersistent()=true` + lagged-camera check, but Tails never registered a STANDING contact for the AIZ2 collapsing platform at x=0x08B0. Despawn cascade was reduced from 6782 -> 1959 errors / 5773 -> 2034 warnings, but AIZ first strict error stayed at F6255 because `lastRidingInstance` was null at the despawn check time.

**Location:** `Sonic3kCollapsingPlatformObjectInstance.spriteOnScreenTestPasses()` (lifecycle now ROM-aligned via `isPersistent()=true` + lagged-camera check), `SidekickCpuController.checkDespawn()` (S3K freed-slot path, infrastructure ready), `ObjectManager.SolidContacts.processInline*` (Tails-vs-platform contact resolution — root cause area), AIZ2 terrain-vs-object collision interaction.

**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`,
first strict error at frame 6255 (`tails_y mismatch expected=0x0000 actual=0x033B`).

### Symptom

Sequence around the divergence (CSV / aux from v6.3-s3k recorder):

| K     | gfc    | Tails state (ROM)                                  | ROM slot 16 (CollapsingPlatform x=0x08B0) |
|-------|--------|----------------------------------------------------|-------------------------------------------|
| 6250  | 0x1742 | sk_x=0x0870 sk_y=0x033F sk_air=0 sk_status=0x00     | status=0x80 (alive, no one standing)      |
| 6251  | 0x1743 | sk_x=0x0875 sk_y=0x033A sk_air=0 sk_status=0x08 (OnObj) | status=0x90 (Tails standing, $3A set)  |
| 6252  | 0x1744 | sk_x=0x087A sk_y=0x033B                              | status=0x90, $38 countdown decrementing   |
| 6253  | 0x1745 | sk_x=0x0880 sk_y=0x033B sk_status=0x08              | status=0x90, alive (cam_back=0x0880, diff=0) |
| 6254  | 0x1746 | sk_x=0x0885 sk_y=0x033B (still alive, still on slot) | object_removed (cam_back=0x0900, Sprite_OnScreen_Test deletes) |
| 6255  | 0x1747 | sk_x=0x7F00 sk_y=0x0000 sk_status=0x02 (in_air)     | (slot freed; SST zeroed)                  |

In ROM, Tails transitions from terrain to platform at F6251 — note the `sk_y` step from 0x033F down to 0x033A (which on the y-axis-down convention means UP 5 px) and the status flip from 0x00 → 0x08 (`OnObj` set). She rides the platform through F6254. At F6255 the slot was freed during F6254 by `Sprite_OnScreen_Test` (sonic3k.asm:37262); `sub_13EFC` (sonic3k.asm:26823) reads `(a3)=0`, mismatches cached `Tails_CPU_interact=0x0002`, falls into `sub_13ECA` (sonic3k.asm:26800) which warps Tails to `(0x7F00, 0)`.

In the engine, Tails has matching `(sk_x, sk_y)` up to F6254 but **never has `onSolidContact(standing=true)` fire for platform 0x08B0** — only platforms at x=0x05B0 and x=0x0E70 get Tails contacts in the entire trace. Without the standing contact, `setLatchedSolidObject` (ObjectManager.java:4431) never runs for slot 16, so `latchedSolidObjectInstance` stays unset and the freed-slot detection has no `lastRidingInstance` to compare against.

### Diagnosed Cause

Three layers — first two now resolved, third remains:

1. **Engine SidekickCpuController had no `(a3)=0` analog.** [Resolved in commit 2b8cd723f.] ROM's `cmp.w (a3),d0` mismatch fires on slot deletion (the SST is zeroed by `Delete_Referenced_Sprite` sonic3k.asm:36116). The engine added `latchedSolidObjectInstance` (`AbstractPlayableSprite`), `setLatchedSolidObject(int, ObjectInstance)` (ObjectManager wires this on `processInline*` standing/touchTop), `SidekickCpuController.lastRidingInstance` per-frame cache, and the `sub_13EFC` `(a3)=0` analog gated by `PhysicsFeatureSet.sidekickDespawnUsesRidingInstanceLoss`.

2. **Engine collapsing-platform lifecycle was off by one frame.** [Resolved in this commit.] `ObjectManager.unloadCounterBasedOutOfRange()` (ObjectManager.java:1842) reproduces ROM's `Sprite_OnScreen_Test` formula but feeds the **current** frame's `cameraX`. ROM's S3K `Sprite_OnScreen_Test` (sonic3k.asm:37262) uses `Camera_X_pos_coarse_back`, which `Load_Sprites` (sonic3k.asm:37545 `loc_1B7F2`) updates **after** `Process_Sprites` each frame — so the value seen during a given frame's object pass reflects the camera's X at the **end of the previous frame**. The engine's eager check destroyed the platform at frame K (cam_x=0x0985), one frame before ROM's frame K+1 deletion (using `Camera_X_pos_coarse_back` = end-of-K value = 0x0900, distance=0xFF80 > 0x280 → delete). Fix: `Sonic3kCollapsingPlatformObjectInstance.isPersistent()` returns `true` to bypass the eager engine OOR, plus `spriteOnScreenTestPasses()` runs the lagged-camera variant inside `update()` using `previousFrameCameraX` cached from the prior tick. With this, the platform now reaches `setDestroyed(true)` at gfc=0x1746 / F6254 — matching ROM exactly.

3. **Tails never lands on platform x=0x08B0 in the engine.** [Architectural blocker, current state.] ROM Tails transitions from terrain to platform at F6251 (sk_y 0x033F → 0x033A, status 0x00 → 0x08). Engine Tails has matching X/Y but never gets a `standing()` contact recorded for platform 0x08B0. Hypothesis: AIZ2 layout has terrain underneath the platform at a Y close to the platform's slope-data top surface, and the engine's solid-contact framework doesn't perform the terrain → object handover that ROM's `SolidObjectTopSloped2` (sonic3k.asm:41826) manages. Without a `standing()` contact, `setLatchedSolidObject` never runs, `latchedSolidObjectInstance` stays null, and the freed-slot detection in `SidekickCpuController.checkDespawn()` lines 1127-1137 has no `lastRidingInstance` to detect the loss of.

### Fix (Partial)

Landed in this commit:

- `Sonic3kCollapsingPlatformObjectInstance.isPersistent()` returns `true` so `ObjectManager.unloadCounterBasedOutOfRange()` (eager, current-frame) does not destroy the platform. Lifecycle now governed exclusively by the in-instance `spriteOnScreenTestPasses()`.
- `Sonic3kCollapsingPlatformObjectInstance.spriteOnScreenTestPasses()` ports ROM `Sprite_OnScreen_Test` (sonic3k.asm:37262) using a per-instance `previousFrameCameraX` cache — at end of `update()` we save the camera_x observed during this tick; next tick's `spriteOnScreenTestPasses()` reads it as the analog of ROM's `Camera_X_pos_coarse_back`. Distance threshold $280, unsigned 16-bit wrap, exactly matching ROM.
- `update()` runs `spriteOnScreenTestPasses()` in states 0/1/2, mirroring ROM's `loc_20594` → `sub_205B6` → `Sprite_OnScreen_Test` chain (sonic3k.asm:44814, 44830, 37262) which fires every frame in pre-collapse and solid-stay states. State 3 (post-fragment fall) keeps its existing `isOnScreen(128)` check matching ROM `loc_20620` `tst.b render_flags / bpl Delete_Current_Sprite` (sonic3k.asm:44879).

Effect: AIZ trace replay error count 6782 → 1959 (-71%), warning count 5773 → 2034 (-65%). All downstream cascades from premature platform destruction are gone. Cross-game baselines unchanged: S1 GHZ green, S1 MZ1 F311, S2 EHZ F1151, S3K CNZ F2262.

Not landed (architectural blocker):

- **Tails-vs-collapsing-platform standing contact in the engine.** Engine `ObjectManager.SolidContacts.processInline*` paths never fire `onSolidContact(standing=true)` for slot 16 / platform x=0x08B0 with Tails, even though Tails' position matches ROM frame-for-frame up to F6254. Investigation needed into:
  - Whether AIZ2 terrain immediately under the platform footprint masks the platform's top surface (terrain Y ≈ platform slope-data Y).
  - Whether the engine performs the equivalent of ROM `SolidObjectTopSloped2`'s "step UP onto the object" handover (sonic3k.asm:41826) when the player runs from terrain onto a sloped solid object whose top is slightly above terrain.
  - Whether the per-frame ordering (Tails physics resolves terrain collision **before** the object solid pass) causes Tails' Y to be locked to terrain before her body intersects the platform.

### Removal Condition

Resolve once `TestS3kAizTraceReplay`'s first strict error advances past F6255, with engine Tails landing on the AIZ2 collapsing platform at x=0x08B0 (visible as `setLatchedSolidObject(slot=16)` firing for Tails around F6251) and the freed-slot detection then warping her to `(0x7F00, 0)` at F6255 like ROM.

---

## CNZ1 Trace F3649 — Tails Air-to-Ground Spring Boost Missed (RESOLVED)

**Location:** `Sonic3kSpringObjectInstance.onSolidContact`, `Sonic3kSpringObjectInstance.checkHorizontalApproach`, `ObjectManager.SolidContacts.processInlineObjectForPlayer`
**ROM Reference:** `sub_23190` (sonic3k.asm:47890), `sub_2326C` (sonic3k.asm:47957), `Obj_Spring_Horizontal` (sonic3k.asm:47771), `word_22EF0` (sonic3k.asm:47651) — yellow horizontal spring strength `-$A00`.

### Symptom

`TestS3kCnzTraceReplay` first strict error at F3649: `tails_x_speed mismatch (expected=-0A00, actual=-0060)`. ROM Tails lands on a horizontal spring at slot 16 (position `0x1D37, 0x08B0`) at F3649 — the same frame she transitions from in-air to grounded — and the spring fires, setting `x_vel = -$A00, ground_vel = -$A00`. The engine does not fire the spring on Tails, leaving her with the air-physics-derived `x_speed = -$60`. Engine catches up at F3650 once ground physics propagates, producing a 1-frame phase shift in all downstream Tails state.

### Diagnostic Data

Captured by extending `s3k_trace_recorder.lua` (v6.4-s3k) with `event.onmemorywrite` hooks on Tails's `x_vel`/`y_vel` RAM addresses. Each hit records the M68K PC of the writing instruction. Hooks are window-restricted (default frames 3640–3660; override via `OGGF_S3K_VELOCITY_WRITE_RANGE`).

ROM frame-by-frame Tails `x_vel` writes:

| Frame | Writers (PC : value) | Notes |
|------:|----------------------|-------|
| F3645–F3648 | `0x14ECC : 0xFFD0..0xFFB8` | `Tails_InputAcceleration_Freespace` accel-while-airborne |
| **F3649** | `0x14ECC : 0xFFB8`, **`0x2319C : ...`** | First the air physics, then **the spring fires inside `sub_23190`** |
| F3650+ | `0x14B70 : 0xF600` | Ground physics: `x_vel = ground_vel * cos(angle)`. ground_vel was set to `-$A00` by `loc_231BE` during the spring fire. |

### Root Cause

Two related issues, both ROM-cited:

1. **Engine spring proactive zone (`sub_2326C` analog) only checks Player_1, not sidekicks.** `Sonic3kSpringObjectInstance.checkHorizontalApproach(player)` is called from `update(int frameCounter, PlayableEntity playerEntity)` with the leader only. ROM `sub_2326C` (sonic3k.asm:47957) explicitly checks **both** Player_1 (line 47973) and Player_2 (line 47999) every frame.

2. **Engine spring `onSolidContact` requires `touchSide()` for horizontal springs, but ROM fires on the standing flag.** `Obj_Spring_Horizontal` (sonic3k.asm:47780-47782) tests bit 0 of `swap`'d `d6` after `SolidObjectFull2_1P` — that is the **standing** flag (`p1_standing`/`p2_standing`), not a side flag. The engine's path
   ```java
   if (springType == TYPE_HORIZONTAL) {
       if (!contact.touchSide() || !isPlayerOnHorizontalSpringActiveSide(player)) return;
       applyHorizontalSpring(player);
   }
   ```
   gates on `touchSide()`. When Tails transitions air→ground inside the spring's hitbox in a single frame, the contact is reported as standing/touchTop, not side, so the spring does not fire.

The yellow horizontal spring strength `-$A00` matches the trace `tails_x_speed = -$0A00` exactly when the spring's `$30(a0)` was set by `Spring_Common` `word_22EF0[subtype & 2]` (subtype bit 1 selects yellow). ROM-side per-frame spring `subtype` instrumentation reads `0x00` from the OST byte at offset `0x2C`, but ROM CSV velocity matches yellow strength — likely a recorder-read quirk (see "Diagnostic data" above; the disassembly `subtype` at offset `0x2C` may be loaded into a different field at runtime, or the recorder reads it at a frame where it has been re-written; not load-bearing for the fix).

### Diagnosis Tooling Landed In This Branch

- `tools/bizhawk/s3k_trace_recorder.lua` — `v6.4-s3k`. Adds `event.onmemorywrite` hooks at Tails's `x_vel` (`0xFFB062`/`0xFFB063`) and `y_vel` (`0xFFB064`/`0xFFB065`), accumulates per-frame, flushes once per `on_frame_end` as `velocity_write` aux event listing each writer's PC and post-write value. Frame-window-gated (default `3640..3660`; set `OGGF_S3K_VELOCITY_WRITE_RANGE=START-END` to widen) so the trace size stays manageable.
- `aux_schema_extras` adds `velocity_write_per_frame`. Backward-compatible: existing traces without the key still load.
- `TraceEvent.VelocityWrite` record + parser case in `TraceEvent.parseJsonLine`.
- `TraceMetadata.hasPerFrameVelocityWrite()` boolean accessor.
- `TraceData.velocityWriteForFrame(frame, character)` lookup.

The CNZ test resources `physics.csv`/`aux_state.jsonl` in `src/test/resources/traces/s3k/cnz/` were **not** regenerated against this schema — the existing trace remains the reference for replay. The new diagnostic schema is for ad-hoc bug-hunt regenerations only.

### Fix Landed

`Sonic3kSpringObjectInstance.update()` now mirrors ROM `sub_2326C` (sonic3k.asm:47957)
over the leader **and** every active sidekick.  The previous code passed only
`playerEntity` (always the leader) to `checkHorizontalApproach`, so a CPU-controlled
Tails landing on a horizontal spring while sitting just outside the side-push
collision box never received the proactive-zone fire that the ROM gives Player_2
at sonic3k.asm:47999-48020.  After the fix the spring fires on Tails at F3649,
giving her `x_vel=-$0A00` like ROM, and the +8 px X bump from `sub_23190`
(sonic3k.asm:47893) puts her at `0x1D29` matching ROM trace.

The diagnosis's secondary suggestion ("switch horizontal-spring `onSolidContact`
to fire on the standing flag, not `touchSide`") was based on a misreading of the
d6 swap test at sonic3k.asm:47780-47782.  Re-derived: `p1_standing_bit = 3`
(sonic3k.constants.asm:133), so when `SolidObjectFull2_1P` calls
`addi.b #$D,d4 / bset d4,d6` on the side-push branch
(sonic3k.asm:41497-41498 / 41506-41507) it sets bit `3 + 0xD = 0x10`
of d6 (= bit 16).  After `swap d6 / andi.w #1,d6` the spring is testing
bit 16, which is the **side-push flag**, not the standing flag.  The engine's
existing `contact.touchSide()` gate is therefore the correct ROM equivalent;
the proactive zone is the only piece that was missing.

### Removal Condition

Removed.  `TestS3kCnzTraceReplay`'s first strict error advanced from F3649
to F3845 (a 196-frame advance into a different, sidekick-physics divergence
already on the open list).  Cross-game baselines unchanged: S1 GHZ green,
S1 MZ1 F311, S2 EHZ F1151, S3K AIZ F6313.

---

## AIZ Trace F6920 -- Sloped Collapsing Platform Ride Sample Ordering -- RESOLVED

**Resolution:** Fixed by the round-15 introduction of
`SolidObjectProvider.suppressSlopeSampleThisFrame()` and the matching
`Sonic3kCollapsingPlatformObjectInstance` `pendingTransitionSkip` /
`transitionFrameSlopeSkip` flags. ROM `loc_20594`'s state-1 -> state-2
transition (sonic3k.asm:44820) jumps to `ObjPlatformCollapse_CreateFragments`
(sonic3k.asm:45394), which `jmp`s to `Play_SFX` without falling through to
`sub_205B6` (sonic3k.asm:44830) -- so the slope sample is skipped exactly on
the transition frame. The engine's `update()` runs `performCollapse()` one
frame earlier than ROM's `$3A` arming (sonic3k.asm:44825 vs the engine's
`onSolidContact` setting `state=1` on the first standing frame), so the flag
is staged in `pendingTransitionSkip` during the engine's transition update
and promoted to `transitionFrameSlopeSkip` in the next update. The post-update
inline solid pass observes the active flag for exactly one frame, keeps the
player attached, and skips the y_pos write -- matching ROM's transition
frame. F5904 (`y=0x0317`) is preserved because that platform is already in
state 2 throughout. AIZ trace first-error advances from F6920 to F7127.

**Original symptom (kept for context):**

**Location:** `Sonic3kCollapsingPlatformObjectInstance`,
`ObjectManager.SolidContacts.processInlineRidingObject`.
**ROM Reference:** `SolidObjSloped2` (sonic3k.asm:41727),
`SolidObjectTopSloped2_1P` (sonic3k.asm:41840),
`Obj_CollapsingPlatform` `sub_205B6` / `loc_205DE`
(sonic3k.asm:44835, 44841).
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`,
first strict error at frame 6920.

### Symptom

`TestS3kAizTraceReplay#replayMatchesTrace` currently first fails at
F6920:

```text
y mismatch (expected=0x0342, actual=0x0341)
```

The trace has Sonic riding AIZ collapsing platform object 0x04 at
`x=0x0E70, y=0x0368`, ROM routine `loc_205DE`. X velocity, ground
velocity, angle, status bits, and object latch all still match at the
first failing frame, so this is a one-pixel vertical ride-surface ordering
problem rather than a broad physics divergence.

### Diagnosed Constraints

The ROM's continued-riding path samples sloped solids in
`SolidObjSloped2`: it computes `x_pos(player) - x_pos(object) + halfWidth`,
shifts right by one, applies horizontal flip after the shift, reads the raw
slope byte, writes `player.y_pos = object.y_pos - slope - y_radius`, then
applies object X carry (`sub.w d2,x_pos(a1)`) (sonic3k.asm:41727-41744).
S2's `MvSonicOnSlope` mirrors this order (s2.asm:35429), and S1's
`SlopeObject2` does the same for its riding path
(`docs/s1disasm/_incObj/53 Collapsing Floors.asm:221`).

That cross-game order rules out a simple "sample previous X" patch in the
shared rider path. A prior local experiment that sampled the previous X for
S3K sloped riding fixed the F6920 shape but regressed AIZ F5904 from
`y=0x0317` to `y=0x0316`, which means the signal is not "always previous X".
Any real fix needs to preserve the shared S1/S2/S3K slope sample order above,
or gate a proven S3K-specific divergence through `PhysicsFeatureSet`.

The object-level ordering signal is real but not yet isolated enough to land:
S3K `Obj_CollapsingPlatform` state `loc_205DE` calls `sub_205B6` before
decrementing its stay timer and before releasing the rider
(sonic3k.asm:44841), and `sub_205B6` itself calls
`SolidObjectTopSloped2` before `Sprite_OnScreen_Test`
(sonic3k.asm:44835). The engine currently keeps this object in
`AUTO_AFTER_UPDATE` mode and compensates state-2 release timing with an extra
stored tick. Moving it to the existing manual-checkpoint pattern used by S2
collapsing platforms is the likely architecture direction, but that rewrite
must re-home the standing-trigger, solid-stay, release, and F6255 off-screen
delete sequencing together; changing only the slope sample or only the
release tick is a trace-shaped hack.

### F6920 Slope-Sample Arithmetic (round-14 dispatch findings)

A round-14 inspection added concrete arithmetic that further constrains the
fix. At F6920 the trace columns settle to:

```text
F6919  player_x = 0x0E8E   y(expected) = 0x0342   y(engine) = 0x0342  match
F6920  player_x = 0x0E8B   y(expected) = 0x0342   y(engine) = 0x0341  drop-by-one
F6922  player_x = 0x0E85   y(expected) = 0x0341   y(engine) = 0x0341  match
```

(Frames F6917 and F6920 show the ROM repeating the previous frame's `y_pos`
for one extra frame; the engine instead drops by one pixel.)

`SolidObjSloped2` (sonic3k.asm:41727-41753) executes:

```
relX     = player.x_pos - platform.x_pos + width_pixels
sampleX  = relX >> 1
slopeY   = (signed) AIZ_SLOPE_DATA[sampleX]
player.y = platform.y - slopeY - player.y_radius
```

With `platform.x = 0x0E70`, `width_pixels = 0x3C`, `y_radius = 0x13`,
`platform.y = 0x0368`, `AIZ_SLOPE_DATA[0x2B] = 0x14`,
`AIZ_SLOPE_DATA[0x2D] = 0x13`:

```text
post-physics player_x = 0x0E8B  -> relX 0x57 -> sampleX 0x2B
                     y = 0x0368 - 0x14 - 0x13 = 0x0341  (engine result)

pre-physics  player_x = 0x0E8E  -> relX 0x5A -> sampleX 0x2D
                     y = 0x0368 - 0x13 - 0x13 = 0x0342  (ROM result)
```

So the ROM-correct slope sample at F6920 needs the **previous frame's**
`x_pos`, but the prior round-13 experiment that always sampled the previous
frame regressed F5904 (`y` 0x0317 -> 0x0316). The clean signal must therefore
account for whether `Sonic_Move` would have changed `x_pos` enough to cross a
2-pixel slope-data bucket boundary this frame, not be a blanket "previous
frame" patch.

Sweeping all of F6913-F6924 with the post-physics formula reproduces every
frame except F6913 (engine 0x0346 vs trace 0x0347 -- one pixel **too high**)
and F6920 (engine 0x0341 vs trace 0x0342 -- one pixel **too high**). Both
discrepancies share the property that the ROM "delays" the slope drop by one
frame relative to the engine. The engine matches every interior frame on
which sampling at the post-physics x lands inside the same slope-data bucket
as sampling at the pre-physics x.

### Hypotheses Ruled Out vs Still Open (round 14)

Ruled out, with cites:

- **Pre/post-physics Sonic ordering.** Process_Sprites (sonic3k.asm:35965)
  iterates Object_RAM in slot order; slot 0 (Player_1) runs first, slot 25
  (collapsing platform) runs after, so the platform's `sub_205B6` reads
  `x_pos(a1)` post-Sonic_Move + post-MoveSprite2 (sonic3k.asm:21620-21626).
  Always sampling pre-physics x is therefore wrong as a steady-state model
  even though it happens to fix F6920 and F6913.
- **Auto vs manual checkpoint timing of release.** F6920 is mid-countdown
  (state 2 / `loc_205DE`, $38 still > 0); the 0x30->0x00 release at the END
  of state 2 is what differs between AUTO_AFTER_UPDATE and MANUAL_CHECKPOINT.
  Reordering update vs solid checkpoint cannot move the slope sample
  evaluated **inside** that solid checkpoint, so a pure mode swap on the
  collapsing platform does not by itself address F6920.
- **`SolidObjSloped2` X-carry double application.** `sub.w x_pos(a0),d2 ;
  sub.w d2,x_pos(a1)` (sonic3k.asm:41748-41749) collapses to a no-op on this
  platform because `sub_205B6` loads `d4 = x_pos(a0)` (44834) before
  `SolidObjectTopSloped2_1P` saves `move.w d4,d2` (41863). So the carry does
  not adjust the player's x_pos and therefore cannot retroactively change
  the slope sample.

Open, needing investigation:

- **Standing-bit edge transitions.** `SolidObjectTopSloped2_1P`
  (sonic3k.asm:41840) has two paths: standing branch (re-sample slope) vs
  `SolidObjCheckSloped2` new-contact branch. Whether the platform's
  `p1_standing_bit` was set vs cleared this frame -- and whether
  `SolidObjCheckSloped2` writes y differently than `SolidObjSloped2` (e.g.
  with a different effective y_radius via the `loc_1C100` rolling fixup) --
  has not been ruled out. Specifically: `SolidObjCheckSloped2` is at
  `sonic3k.asm:42095` and is the new-landing path; if the bit got cleared
  for one frame (e.g. by a sibling fragment processing) the platform would
  re-enter via the new-contact path which uses a different snap formula.
  Confirm by inspecting platform-side `status` byte during F6919 and F6920
  in a BizHawk trace.
- **Pre-physics x_pos held by `MvSonicOnPtfm2` slope re-application.** S3K's
  `loc_205DE` only calls `sub_205B6` (which calls `SolidObjectTopSloped2`).
  But on Sonic's own slot path (Player_1 routine), `Sonic_Move` may
  short-circuit MoveSprite2 when Status_OnObj is set in some sub-paths.
  Verify by tracing whether `loc_10844` (sonic3k.asm:21620) or one of its
  alternates dispatches when `Status_OnObj` is set this specific frame.
- **CollidingObject_PtfmRiding interlock.** S3K may have a `Player_AnglePos`
  / `Player_SlopeRepel` (sonic3k.asm:21629-21630) tail that special-cases
  `Status_OnObj` and writes y_pos back to a snapshot, which would mean the
  platform's later `SolidObjSloped2` y-write gets clobbered by Sonic's own
  routine on the NEXT frame. If true, the trace's "expected" y for frame F
  is actually the y written by Sonic's routine at frame F+1 rather than the
  platform's y-write at frame F.

### What a Real Fix Likely Needs

Once the open hypotheses are settled, the fix has to:

1. Identify the ROM divergence that produces a one-pixel "y-hold" on
   F6917/F6920 with cite (likely a standing-bit transition or a ROM-side
   y_pos snap inside Sonic's own routine that the engine isn't reproducing).
2. Gate the divergence behind a `PhysicsFeatureSet` flag so S1/S2 are
   unaffected.
3. Preserve F5904 (`y = 0x0317`), pass F6920 (`y = 0x0342`), and not
   regress CNZ F6304 / S1 GHZ / S1 MZ / S2 EHZ baselines.

A blanket conversion of `Sonic3kCollapsingPlatformObjectInstance` from
AUTO_AFTER_UPDATE to MANUAL_CHECKPOINT is the right architectural cleanup
for the release-frame ordering (sub_205B6 before $38 decrement before
sub_205FC, sonic3k.asm:44850-44857), but it should be done as a separate,
independent commit since it does not by itself move the F6920 slope sample.

### Removal Condition

Resolved 2026-04-30 (round-15 dispatch): F5904 and F6920 both pass, S1
GHZ/MZ/S2 EHZ trace baselines unchanged, CNZ first-error stays at F6304.
Entry retained for context until a follow-up cleanup confirms no further
regressions.

---

## CNZ1 Trace F6304 — Tails Misses CNZ Door Re-Land While Following Fast Leader (RESOLVED)

**Status:** Resolved on `bugfix/ai-cnz-f6304-airborne-latch` (2026-04-30).
The first strict CNZ1 trace error advances from F6304 to F7614 (a
1,310-frame jump). Cause was the engine's solid-contact on-screen gate
(`AbstractObjectInstance.isWithinSolidContactBounds`) using a hardcoded
16-px margin and the current frame's camera position. ROM
`Render_Sprites` (sonic3k.asm:36336-36370) computes the on-screen flag
from each object's `width_pixels` (CNZ horizontal door byte_30FCE at
sonic3k.asm:66167 = `$20, $08`), and that flag is written at the END of
frame N — `SolidObjectFull` on frame N+1 reads the prior frame's value.
The fix exposes `getOnScreenHalfWidth()` on `AbstractObjectInstance`
(default 16, overridden to ROM `width_pixels` for the door) and adds a
`previousFrameCameraBounds` snapshot rolled forward in
`updateCameraBounds` so the gate matches ROM's one-frame-old observation
order. Cross-game safe: the gate is still feature-flagged on
`PhysicsFeatureSet.solidObjectOffscreenGate` (S3K only); the new accessor
just sharpens the per-object margin and the snapshot timing. Verified
green: S1 GHZ, S1 MZ, S2 EHZ, S3K AIZ first-error stable at F6920.

**Location:** `DoorObjectInstance` (sub=$80 horizontal-door variant),
`ObjectManager.SolidContacts` (Tails-side `SolidObjectFull` resolution),
`SidekickCpuController.updateNormalFollow` (`leader_fast` branch).
**ROM Reference:**
- `Obj_HCZCNZDEZDoor` `loc_31034` horizontal-door routine,
  `sub_310DA` proximity check (sonic3k.asm:66198-66280).
- `SolidObjectFull` (sonic3k.asm:42102+) — ROM solid-object full handler
  used at sonic3k.asm:66258 with `d1 = width_pixels + $B`,
  `d2 = height_pixels = 8`, `d3 = height_pixels + 1 = 9`.
- `Tails_CPU_Control` routine 6 = `loc_13D4A` follow-leader, fast-leader
  branch `loc_13DD0` / `loc_13E9C` (sonic3k.asm:26656-26786).
**Trace reference:** `src/test/resources/traces/s3k/cnz` (`cnz1_fullrun`),
first strict error at frame 6304.

### Symptom

`TestS3kCnzTraceReplay#replayMatchesTrace` first fails at F6304:

```text
tails_y mismatch (expected=0x0530, actual=0x0533)
```

Surrounding state at the failure (per `s3k_cnz1_context.txt` line 25):

- F6298–F6303: ROM and engine agree. Tails has `Status_InAir=1` after
  running off a previous door/platform; `tails_y` slowly drifts from
  0x0530 → 0x0532 in both due to a small `tails_y_speed` (≤ `0x0118`).
- F6304: ROM snaps `tails_y` back to `0x0530`, clears `Status_InAir`,
  zeroes `tails_y_speed`, and writes `Status_OnObj` with the CNZ door
  (`obj=00031034`, `sub=$80`, `@1940,0548`). Engine continues falling
  to `tails_y=0x0533` with `tails_y_speed=0x0150`, never crossing into
  the door's solid-top.
- The downstream `tails_g_speed` divergence (-0x0492 expected vs
  -0x0402 actual) and the cascading 3 145-error tail are direct
  consequences of Tails staying airborne for the rest of the run.

The `tails_x` / `sonic_x` columns remain in lock-step throughout the
window; the divergence is exclusively in the Tails-side vertical
landing path on the horizontal CNZ door.

### Diagnosed Constraints

The ROM-side trace diagnostics confirm the door is the catching object.
At F6304, `tailsInteract slot=13 ptr=B3C2 obj=00031034 rtn=00 st=12
@1940,0548 sub=80 tails rf=85 obj=00 onObj=true objP2=true active=true
destroyed=false`. ROM's `loc_31034` calls `SolidObjectFull` with d1=$2B
(half-width 0x20 + 0xB), d2=8 (top), d3=9 (bottom)
(sonic3k.asm:66250-66258). With Tails y_radius = 0x10 the ROM landing
target is `door.y - d2 - tails_y_radius = 0x0548 - 8 - 0x10 = 0x0530`,
matching the observed `tails_y=0x0530` after landing.

The engine has the door spawned and active in `ObjectManager` (placement
window `LOAD_AHEAD = 0x280` / camera_x = 0x17EE leaves the door at 0x1940
well inside the active window). `DoorObjectInstance` advertises
`SolidObjectParams(halfWidth + 0xB, halfHeight, halfHeight + 1)` =
(0x2B, 8, 9), matching the ROM `d1`/`d2`/`d3` triplet exactly. The trace's
`eng-near` filter is keyed off the **main player's** (Sonic's) centre
position with a 160 px box, so the door at 0x1940 first appears in the
nearby trace dump at F6308 (Sonic crosses dx ≤ 160) — that is a
diagnostic-formatter artefact, not a spawn-window symptom; the door is
solid-active for Tails the whole time.

The bug is therefore in the Tails-side application of `SolidObjectFull`
when Tails is in `Tails_CPU_routine = 6` (`loc_13D4A` follow-leader)
**with the leader running fast**:

- ROM order (sonic3k.asm:26656-26786): `loc_13D4A` → `loc_13D78` calls
  `sub_13EFC` to update `Tails_CPU_flight_timer` /
  `Tails_CPU_interact`, then performs steering Ctrl_2 writes, and the
  player/object update loop runs `Obj_HCZCNZDEZDoor::loc_31034 →
  SolidObjectFull` on Tails as part of the standard object pass. With
  Tails arriving with `tails_y_speed > 0` and `tails_y < door_top`,
  `SolidObjectFull` snaps `y_pos`, zeroes `y_vel`, sets `Status_OnObj`,
  and clears `Status_InAir` in the same frame.
- Engine: Tails stays in `state=NORMAL branch=leader_fast`
  (`SidekickCpuController.updateNormalFollow`), and the CNZ door's
  solid-resolution against Tails does not re-snap him in the
  approach-from-above shape. The engine's `eng-tails-cyl` formatter is
  cylinder-only, but the underlying SolidContacts pass should also
  process `DoorObjectInstance` for Tails the same way it does for Sonic.
  At F6304 the engine has `tails_y=0x0533`, `tails_y_speed=0x0150`,
  status remains `0x03` (Facing|InAir) end-of-frame.

The combination of the `leader_fast` follow branch (which can rewrite
Tails' `x_pos`/`Ctrl_2` mid-step) and the door's `loc_31034` solid pass
order is where the engine and ROM diverge. A prior CNZ1 split landed
horizontal-spring airborne-contact preservation
(commit 3d03f361a "Preserve S3K horizontal spring airborne contacts"),
which is structurally similar but specific to springs. The door variant
needs the same shape: keep Tails' airborne SolidObjectFull contact for
horizontal CNZ doors so the ROM-equivalent landing latches.

The trace recorder already emits sufficient diagnostic data
(`cpu_state_per_frame`, `interact_state_per_frame`,
`sidekick_interact_object_per_frame`) to drive this fix; no recorder
extension is required. The investigation needs to (a) confirm via
disassembly walk that ROM does call `SolidObjectFull` on Tails in
`Tails_CPU_routine = 6 leader_fast` for the same frame the door's
trigger advances, and (b) port the airborne-from-above latch shape
proven for horizontal springs into the CNZ door's Tails-side path,
gated by a `PhysicsFeatureSet` flag if it does not also apply to S2's
ARZ doors / S1's MZ doors. Cross-game parity is required: S2 EHZ trace
F1151 must stay green, S1 GHZ/MZ must stay green / F311 respectively,
and S3K AIZ trace must not regress past its current F6920 sloped-ride
blocker.

### Removal Condition

Removed on resolution: `TestS3kCnzTraceReplay#replayMatchesTrace` first
strict error has advanced to F7614 (see the next entry for details).

---

## CNZ1 Trace F7614 — Tails Jump Frame 2-Pixel y_pos Drift (RESOLVED)

**Resolution (2026-04-30):** Added
`PhysicsFeatureSet.solidObjectTopBranchAlwaysLiftsOnUpwardVelocity` (true
on S3K, false on S1/S2) so `ObjectManager.SolidContacts.resolveContactInternal`
applies the position lift on upward-velocity contacts instead of returning
null.  The lift mirrors ROM `loc_1E154` (sonic3k.asm:41606-41632) which
writes `subq.w #1, y_pos(a1)` and `sub.w d3, y_pos(a1)` BEFORE testing
`tst.w y_vel(a1) / bmi.s loc_1E198` — when y_vel < 0 the standing/
RideObject_SetRide effect is skipped but the lift has already happened.
With `distY=1` for the Spring_Horizontal-vs-Tails geometry the lift
produces +2 px of y_pos shift; combined with `Tails_Jump`'s rolling
radius adjustment (+1 px) the total +3 px matches the ROM trace exactly
(0x04B0 -> 0x04B3).

To preserve ROM's "lift only on fresh contact" semantics (ROM
`SolidObjectFull2_1P` at sonic3k.asm:41066-41067 only enters
`SolidObject_cont` when the object's `a0.d6` standing-bit is CLEAR), the
engine tracks a per-(player, object) standing-bit on
`SolidContacts.objectStandingBitSet` (set by `RideObject_SetRide`-
equivalent STANDING contacts; cleared at the top of
`processInlineObjectForPlayer` when the object's own pass sees the bit
set with the player airborne, mirroring `loc_1DCF0`).  The pre-clear
value is snapshotted into `objectStandingBitSnapshot` so the lift gate
in `resolveContactInternal` reads ROM's "bit was set at routine entry"
state.  Without that tracking, the AIZ yellow up-spring kick aftermath
at F2090->F2091 (Sonic landing on slot 13 spring at `(0x1980, 0x0439)`
with `d3=1`) would re-lift Sonic +2 px instead of routing through ROM's
`loc_1DCF0` air-unseat path.

**Test impact:** CNZ first strict error advances from F7614 to F7872;
total errors drop from 3200 to 2774.  AIZ first strict error stays at
F7171.  S1 GHZ, S1 MZ1, S2 EHZ trace baselines unchanged.

**Regression test:** `TestSolidObjectTopBranchUpwardLift` exercises the
flag values across SONIC_1, SONIC_2, SONIC_3K (failures here flag
ROM-divergence regressions on the per-game gating).

The historical investigation that led to this fix is preserved below.

---

## CNZ1 Trace F7614 — Tails Jump Frame 2-Pixel y_pos Drift (historical investigation)

**Location:** Sidekick `Tails_Jump` execution (CPU-pressed jump while
Tails is grounded near a CNZ vertical/down spring at @0x0E38,0x04D0
sub=$12).
**Trace reference:** `src/test/resources/traces/s3k/cnz` (`cnz1_fullrun`),
first strict error at frame 7614, 3,200 errors total.

### Symptom

`TestS3kCnzTraceReplay#replayMatchesTrace` first fails at F7614:

```text
tails_y mismatch (expected=0x04B3, actual=0x04B1)
```

Per the recorded `physics.csv` around vfc=7614/7615 (ROM frames F7613/F7614):

- F7613 (vfc=7614): tails y=0x04B0, y_sub=0xB000, y_speed=0,
  status=0x01 (Direction|grounded|not-rolling), y_radius=15
  (default Tails). Tails has been stationary at (0x0E40,0x04B0) from
  F7600 through F7613 with y_sub=0xB000 unchanged.
- F7614 (vfc=7615): tails y=0x04B3, y_sub=0xB000 (UNCHANGED),
  y_speed=-0x0680 (=$F980 two's complement), status=0x07
  (Direction|InAir|Roll), y_radius=14 (rolling).
- Engine end-state at F7614: tails y=0x04B1 (=0x04B0+1), y_speed
  matches ROM at -0x0680.
- Subsequent frames: 2-pixel y offset cascades, producing 3,200
  derivative trace errors.

The fact that y_sub stays exactly 0xB000 across F7613→F7614 is
load-bearing: the +3 px change is composed of integer-only word writes
to `y_pos`, **not** a `MoveSprite` velocity application. (A
`MoveSprite_TestGravity2` apply with y_vel=-$680 would produce y=0x04AA
y_sub=0x3000, which is the F7615 state — i.e. MoveSprite runs on F7615,
not on F7614.)

### Reframed Diagnosis (this round)

The previous round's "CNZ rotating cylinder release" hypothesis is
**wrong**. Diagnostic data:

- The aux-state stream contains `cnz_cylinder_state_per_frame` /
  `cnz_cylinder_execution_per_frame` events when an `Obj_CNZCylinder`
  is interacting with Tails (e.g. F4490+). **No cylinder events
  exist anywhere in F7610–F7616.** No cylinder is active.
- The trace `cpu_state` at F7613 shows `cpu_routine=6, flight_timer=59,
  ctrl2_pressed=0x04`. At F7614 `flight_timer=60, ctrl2_pressed=0x44`
  (jump-button bit set). The CPU is forcing a jump on F7614, not
  releasing from a cylinder.
- The y_speed constant `-$680` is **also** the value `Tails_Jump`
  produces from a level (angle=0) ground stance:
  `sonic3k.asm:28534 move.w #$680, d2; ... muls.w d2, d0; asr.l #8, d0;
  add.w d0, y_vel(a0)` — with `angle - $40 = $C0`, `sin($C0) = -$100`
  giving d0 = -$680.

So the F7614 transition is: Tails was on the ground at (0x0E40,0x04B0)
for 13 frames, the CPU pressed jump, `Tails_Jump` (sonic3k.asm:28519+)
fires, sets y_vel=-$680, sets rolling radii, and integer-adjusts y_pos
to compensate.

The recorded `tailsInteract sub=$12` is the **vertical/down** spring at
slot 17 (subtype=$12 dispatches via
`lsr.w #3,d0; andi.w #$E,d0` → index 2 → `Spring_Down`,
sonic3k.asm:47570), NOT a horizontal spring. The spring is not the
launch source: `Obj_Spring_Down`'s kick `sub_233CA`
(sonic3k.asm:48088+) requires `d4=-2` from `SolidObjectFull2_1P` and
produces y_vel=+$A00 (downward, after `neg.w` from stored -$A00),
which contradicts the observed -$680. Tails's y=0x04B0 is also 8 px
above where `MvSonicOnPtfm` would seat him on this spring
(`spring.y - d3 - y_radius = 0x04D0 - 9 - 15 = 0x04B8`), so Tails is
not actively riding the spring's top surface. The recorded
`stand_on_obj=0x11` (slot 17) appears to be a stale latch from earlier
contact.

### Pixel-Accounting

Engine end-of-frame: y=0x04B1 (=0x04B0+1).
ROM end-of-frame: y=0x04B3 (=0x04B0+3).

`Tails_Jump` on a non-rolling start (sonic3k.asm:28560-28567) writes
`y_radius=$E`, `x_radius=7`, `anim=2`, sets `Status_Roll`, then
sonic3k.asm:28571-28577:

```
move.b y_radius(a0),d0       ; d0 = $E
sub.b  default_y_radius(a0),d0 ; d0 = $E - $F = -1
ext.w  d0
sub.w  d0,y_pos(a0)          ; y_pos -= -1 → y_pos += 1
```

That accounts for **only +1 px** of the ROM's +3 px. After this,
`Tails_Jump` returns via `addq.l #4,sp; ... rts`
(sonic3k.asm:28556) which pops `Tails_Stand_Path`'s return address,
so `Player_SlopeResist`, `Tails_InputAcceleration_Path`, `Tails_Roll`,
`Tails_Check_Screen_Boundaries`, `MoveSprite_TestGravity2`,
`Call_Player_AnglePos`, and `Player_SlopeRepel` are all skipped this
frame. None of those run on F7614, so the floor-snap path
(`Player_AnglePos` line 18790: `add.w d1, y_pos(a0)` for d1>0) is not
reached on the jump frame.

**Origin of the missing +2 px is unidentified after this round's
walkthrough.** Candidates ruled out by direct ROM read or trace:

- `Tails_Jump` rolling adjustment alone: only +1 px (verified).
- `Player_AnglePos` floor snap: skipped on jump frame because of
  `Tails_Jump`'s `addq.l #4,sp` (verified).
- `MoveSprite_TestGravity2` velocity apply: would change y_sub from
  0xB000 to 0x3000, but trace y_sub stays 0xB000 — so MoveSprite did
  not run on F7614.
- Cylinder release: no cylinder is active at F7610-F7616 (verified
  by absence of `cnz_cylinder_state_per_frame` events).
- Spring kick (`sub_233CA`): produces y_vel=+$A00 not -$680, and
  requires top-landing d4=-2 which is not reached given the 8-px gap
  between Tails and the spring's seat.
- `Tails_CPU_Control` cpu_routine=6 path (`loc_13D4A`,
  sonic3k.asm:26656+): only modifies x_pos, not y_pos.
- Cylinder/spring per-frame y_pos write loops: no active cylinder;
  spring's `Obj_Spring_Down` `subq.w #1,y_pos(a1); sub.w d3,y_pos(a1)`
  block (`loc_1E154` sonic3k.asm:41614-41626) only fires when the
  rider's bottom-edge probe overlaps the spring's top by < $10 px,
  which is not the geometry here (rider 8 px above seat).

### Engine-Side Note

The engine's `PlayableSpriteMovement.doJump`
(`src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
lines 615-655) calls `sprite.applyRollingRadii(true)` then
`sprite.setY(getY() + getRollHeightAdjustment())`. For Tails,
`getRollHeightAdjustment()` returns `runHeight - rollHeight = 30 - 28 = 2`
(`PhysicsProfile.java` lines 67-68; `AbstractPlayableSprite.java`
line 3110). Adjusting top-left Y by +2 with the new height of 28
shifts the centre by +2−1 = +1 px (centre = topY + height/2;
height changed 30→28). So engine centre Y goes 0x04B0 → 0x04B1,
matching ROM's `Tails_Jump` rolling adjustment.

The engine therefore applies the rolling-radius/centre adjustment
correctly. The 2-pixel residual must be a separate ROM-side y_pos
write that has not yet been identified.

### Next Steps

1. **Recorder extension landed (v6.10-s3k).** The Bizhawk recorder
   `tools/bizhawk/s3k_trace_recorder.lua` already had the
   `position_write_per_frame` plumbing (added in v6.8 for the CNZ1
   F4790 work, mirroring `velocity_write_per_frame`). On this branch
   the default capture window now also covers F7600-7625 in addition
   to the original F4788-4792, and the env-var override
   `OGGF_S3K_POSITION_WRITE_RANGE` accepts semicolon-separated
   multi-windows (e.g. `4788-4792;7600-7625`). The CNZ fixture under
   `src/test/resources/traces/s3k/cnz/` was regenerated with the new
   window so `aux_state.jsonl.gz` now carries `position_write` events
   at F7600-7625 as well.

   The captured PCs at trace_frame=7614 (vfc=7615) are:

   ```
   y_pos_writes: [
     {pc:0x150D0, val:0x04B0},   # post Tails_Jump sub.w d0,y_pos(a0)
     {pc:0x1E172, val:0x04B1},   # post subq.w #1,y_pos(a1)  in loc_1E154
     {pc:0x1E182, val:0x04B0}    # post sub.w  d3,y_pos(a1)  in loc_1E154
   ]
   ```

   Mapping back to ROM:
   - `0x150D0` is the instruction immediately after `loc_150CC`'s
     `sub.w d0,y_pos(a0)` in `Tails_Jump`
     (sonic3k.asm:28576-28579) — the rolling-radius +1 px adjustment
     the engine already replicates via `getRollHeightAdjustment()`.
   - `0x1E172` and `0x1E182` are inside `loc_1E154` of
     `SolidObjectFull/SolidObject_cont` (sonic3k.asm:41606-41624) —
     the **top-of-platform** push-up branch:

     ```
     loc_1E154:
         subq.w  #4,d3
         ...
         subq.w  #1,y_pos(a1)            ; ← 0x1E172 captures post-write
         tst.b   (Reverse_gravity_flag).w
         beq.s   loc_1E17E
         neg.w   d3
         addq.w  #2,y_pos(a1)
     loc_1E17E:
         sub.w   d3,y_pos(a1)            ; ← 0x1E182 captures post-write
     ```

     This means the `Spring_Down` (subtype=$12) at slot $11 — which
     `tailsInteract sub=$12` and `stand_on_obj=0x11` confirm Tails
     was still latched against on F7614 — is calling
     `SolidObjectFull2_1P` and reaching the top-side branch to
     re-seat Tails one pixel above the spring's top + the d3 lift,
     **not** the cylinder hypothesis from prior rounds.

   Note on hook timing: BizHawk's `event.onmemorywrite` reads back
   the post-write `u16` value, but byte-granular hooks fire per byte
   so the `val` reported for word writes can lag the actual
   end-of-instruction state by one byte. Treat the PCs as
   load-bearing and the `val`s as a hint, not a ground truth.

2. **Engine-side fix attempted, deferred (round 2026-04-30).** The PCs
   above point at the engine's `SolidObjectProvider` top-contact
   resolution as the missing -2 px (or, in trace-coordinates, missing
   +2 px because ROM's overall delta is +3 while the engine only
   produces +1). Direct engine-side analogy and ROM walkthrough were
   completed but the `loc_1E154` precondition arithmetic does not
   match the captured PCs:

   - **Engine `loc_1E154` analogue located.** The
     `loc_1E154`/`loc_1E17E` lift sequence in `SolidObject_cont`
     (sonic3k.asm:41606-41624) writes
     `y_pos(a1) = y_pos(a0) - d2_orig - y_radius - 1` on top-contact
     resolution. The engine equivalent is
     `ObjectManager.SolidContacts.resolveContactInternal` (lines
     5969-5988) which sets
     `newCenterY = playerCenterY - distY + 3`; substituting
     `distY = playerCenterY - anchorY + 4 + maxTop` gives
     `newCenterY = anchorY - maxTop - 1` — algebraically equivalent
     to ROM (with `playerHalfHeight` factored back via `setY()` /
     centre rounding). The engine therefore matches ROM when this
     resolver path actually fires.

   - **Object-side `pX_standing_bit` was clear at F7613 end.**
     Aux-state `object_state` for slot 17 at vfc=7614 reports
     `status=0x01`: only bit 0 is set, both `p1_standing_bit=3` and
     `p2_standing_bit=4` are clear (sonic3k.constants.asm:133-134).
     So the spring's `SolidObjectFull2_1P` Player_2 call on F7614
     enters via `beq.w SolidObject_cont` (sonic3k.asm:41067), NOT via
     the bit-set / `MvSonicOnPtfm` path.

   - **`loc_1E154` precondition does NOT match the trace numbers.**
     With Tails y_pos(a1) = 0x04B1 (post `Tails_Jump` rolling +1),
     spring y_pos(a0) = 0x04D0, d2_orig = 8 (Spring_Down d2 arg),
     y_radius = 14 (Tails post-rolling), default_y_radius = 15:
     `loc_1DFD6` computes
     `d3 = (0x04B1 - 0x04D0) + 4 + 8 + 14 = -5 -> 0xFFFB` ->
     `andi.w #$FFF, d3 -> 0x0FFB`,
     `d4 = 15 + 8 + 8 + 14 = $2D`;
     `cmp.w d4, d3` -> `0x0FFB > $2D` ->
     `bhs.w loc_1E0A2` (no contact). For `loc_1E154` to fire,
     pre-`andi` d3 must be in `[0, $F]`, i.e. Tails y_pos must be in
     `[0x04B6, 0x04C5]`; 0x04B1 is 5 px above that window.

   - The captured PCs `0x1E172` / `0x1E182` with `character="tails"`
     are therefore not consistent with the geometry the trace shows
     on F7614. Possibilities to confirm with a recorder revision:
     - byte-write hook frame-bucket lag (the post-write u16 is read
       after the second byte fires, so the event may be ascribed to
       the wrong vfc/frame);
     - the recorder filters by Player_2 base address, but `(a1)` at
       0x1E172 / 0x1E182 may be Player_1 — the spring's
       `SolidObjectFull2_1P` runs both player calls back-to-back from
       `Obj_Spring_Down` and the Tails-y_pos-watching hook can fire
       on a Player_1 write if the watch range is wider than expected;
     - a different routine (not `loc_1E154`) is at the disassembly-
       equivalent address in the actual ROM (the disasm uses
       `loc_<addr>` labels but the runtime-PC-to-disasm-line mapping
       must be confirmed with `RomOffsetFinder` or an ROM byte dump
       at offset 0x1E170+).

   - Engine fix not implemented this round. Implementing the
     `subq.w #1; sub.w d3` lift on the engine's
     `resolveContactInternal` path with the precondition unverified
     risks producing a +2 px lift on every frame the
     `loc_1E154`-equivalent fires (which today is many AIZ/MGZ/HCZ
     contacts, not just CNZ F7614) — a cross-game regression.

3. **Recorder extended to v6.11-s3k (round 2026-04-30).** Two diagnostic
   improvements landed on `bugfix/ai-cnz-recorder-a1-target-and-radius`
   to address the geometric contradiction; the regenerated CNZ fixture is
   deferred to a follow-up round (no BizHawk in this agent environment):

   - **`position_write` hits now record `(a1)` and `(a0)`.** The
     `WRITE_DIAG.tails_xpos_record_hit` /
     `WRITE_DIAG.tails_ypos_record_hit` callbacks in
     `tools/bizhawk/s3k_trace_recorder.lua` now snapshot the M68K A0
     and A1 registers at the moment the `event.onmemorywrite` callback
     fires, and the per-frame `position_write` JSON event embeds them
     as `"a1":"0x........"` and `"a0":"0x........"` per hit. With the
     write-watch armed on Tails's address `(a1)` MUST be Tails when the
     hook fires for an `<op> y_pos(a1)` instruction, so a Player_1
     value would prove the hook is being attributed to the wrong
     `(an)` mode (e.g. an `(a0)` write that aliased the Tails address).
     Tracked in `TraceEvent.PositionWrite.Hit` as `int a1, int a0`
     fields with a 2-arg compatibility constructor for pre-v6.11
     fixtures.
   - **New `solid_object_cont_entry` event.** A new
     `event.onmemoryexecute` hook at PC=`0x1DF90` (the byte address of
     the `SolidObject_cont` label, sonic3k.asm:41394 — verified by
     walking ROM bytes from `loc_1DF88`'s `4A 28 0004` /
     `tst.b render_flags(a0)` at `0x1DF88`, then `6A 00 0114` /
     `bpl.w loc_1E0A2` at `0x1DF8C`, then
     `30 29 0010` / `move.w x_pos(a1),d0` at `0x1DF90`) records the
     `(a0)`/`(a1)`/d1/d2 register state along with the player's
     `y_radius` and `default_y_radius` (read live from RAM at
     `(a1) + 0x1E` and `(a1) + 0x16`), plus the player's pixel x/y and
     status. Default capture window mirrors `position_write`
     (`{4788-4792, 7600-7625}`); operators can override via the new
     `OGGF_S3K_SOLID_CONT_RANGE` env var (single `<lo>-<hi>` window or
     semicolon-separated multi-window). The aux schema advertises this
     event via the new `solid_object_cont_entry_per_frame` extra; the
     parser's `default ->` branch in `TraceEvent.parseEvent` tolerates
     it as a `StateSnapshot` for fixtures that lack a typed model.

   **ROM byte mapping verified.** Direct hex dump of
   `Sonic and Knuckles & Sonic 3 (W) [!].gen` at offset `0x1E150`
   confirms the `loc_1E154` block bytes match sonic3k.asm:41606-41624
   exactly. Specifically:
   - `0x1E154`: `5943` = `subq.w #4, d3`
   - `0x1E16E`: `5369 0014` = **`subq.w #1, y_pos(a1)`**
     (post-instruction PC = `0x1E172` ✓ matches the captured value)
   - `0x1E17E`: `9769 0014` = **`sub.w d3, y_pos(a1)`**
     (post-instruction PC = `0x1E182` ✓ matches the captured value)

   So `0x1E172` and `0x1E182` are the post-fetch PCs immediately AFTER
   the corresponding `<op> y_pos(a1)` instructions in the loc_1E154
   push-up branch. The disasm-label-to-ROM-offset mapping is
   straightforward (`loc_<hex>` = ROM byte offset); the captured PCs
   include BizHawk's standard "PC after instruction completion" lag
   for `event.onmemorywrite`. **Confirmed: 0x1E172 and 0x1E182 are
   inside the loc_1E154 push-up branch.** Possibility (3) from prior
   round (different routine at the disasm-equivalent address) is
   **ruled out**.

4. **Next-round actions (require BizHawk fixture regeneration):**

   With v6.11-s3k armed, the next BizHawk re-record needs to set
   `OGGF_S3K_TRACE_PROFILE=cnz1_fullrun`,
   `OGGF_S3K_POSITION_WRITE_RANGE=4788-4792;7600-7625` (default), and
   leave `OGGF_S3K_SOLID_CONT_RANGE` unset to take the default
   `{4788-4792, 7600-7625}` window. The regenerated
   `src/test/resources/traces/s3k/cnz/aux_state.jsonl.gz` should then
   contain, at trace_frame=7614:

   - `position_write` event with each of the three captured y_pos hits
     now carrying `a1`/`a0` — confirming whether `(a1)` was Tails
     ($FFB04A low word) or Sonic ($FFB000 low word) at write time, and
     whether `(a0)` points to the Spring_Down slot or some other OST
     slot.
   - One or more `solid_object_cont_entry` events for the same frame
     showing `(a0)`/`(a1)`, `y_radius`, `default_y_radius`, and the
     pre-resolution `player_y` so the d3/d4 derivation in
     `loc_1DFD6`+ can be reconstructed exactly.

   If the regenerated trace shows `(a1)=$FFB04A` (Tails) at
   `0x1E172`/`0x1E182` AND the recorded `y_radius`/`default_y_radius`
   are 14/15, then the geometric contradiction stands and the next
   investigation must look for a different `(a0)` solid object whose
   y_pos differs from the Spring_Down's `0x04D0` (e.g. a different
   slot reaching `loc_1E154` against Tails on the same frame).

   If `(a1)=$FFB000` (Sonic) at one or both PCs, then the recorder's
   address-watch is firing on an aliased Player_1 write and the prior
   round's "captured PCs prove a top-lift on Tails" conclusion is
   void; the F7614 +2 px residual must originate elsewhere.

   - **Object-loop ordering check.** A frame-by-frame walk of
     `Process_Sprites` order at vfc=7615 would isolate which slot
     touches `Player_2.y_pos` between Tails's `Tails_Modes` pass and
     end-of-frame. The new `position_write` `(a0)` field combined with
     `solid_object_cont_entry`'s `(a0)` (and its `solid_x`/`solid_y`)
     makes this tractable without an additional recorder revision.

5. **"Tails despawn" hypothesis refuted (round 2026-04-30 followup).**
   A round-launch user observation suggested ROM might have despawned
   Tails by F7614 so that the captured `0x1E172`/`0x1E182` y_pos writes
   were actually targeting Sonic via aliased addressing rather than
   Tails. The recorded fixture does not support this:

   - **Tails is alive and active across F7600-F7625.** The committed
     `physics.csv.gz` rows for vfc=7601-7626 show
     `sidekick_present=1`, `sidekick_routine=0x02`, `sidekick_status_byte`
     transitioning `0x01` (grounded, facing-right) -> `0x07`
     (grounded+InAir+Roll set by `Tails_Jump`) on vfc=7615 (F7614),
     and `sidekick_x` / `sidekick_y` updating every frame from the
     stationary `(0x0E47, 0x04B0)` pre-jump position into the post-jump
     parabola `(0x0E3F, 0x04B3)` -> `(0x0E26, 0x047D)`. A despawned
     Tails would have frozen coordinates and routine 0; the trace shows
     the opposite. Captured by reading the gzip CSV with
     `gameplay_frame_counter=0x1DB0..0x1DC8`, sidekick_* columns 22-36.
   - **Watch addresses physically pin to Player_2.** The recorder
     hooks `event.onmemorywrite` at byte addresses
     `M68K_RAM_BASE + 0xB04A + 0x14` and `+ 0x15`
     (`tools/bizhawk/s3k_trace_recorder.lua` lines 1534-1611, with
     `OBJ_TABLE_START = 0xB000`, `OBJ_SLOT_SIZE = 0x4A`,
     `SIDEKICK_BASE = 0xB04A`, `OFF_Y_POS = 0x14`). The watched bytes
     `$FFFFB05E` / `$FFFFB05F` are exactly Player_2's `y_pos` field
     (Player_2 base = `$FFFFB04A`, `y_pos` = offset $14). For
     `subq.w #1, y_pos(a1)` at runtime PC `0x1E16E` to fire the hook,
     `(a1) + 0x14` MUST equal `$FFFFB05E`, hence `(a1) = $FFFFB04A =
     Player_2 = Tails`. The "(a1) was Sonic" path is not physically
     reachable through this hook; a Sonic-targeted write of the same
     instruction would write to `$FFFFB014` and silently miss the
     watch entirely.
   - **CSV column inventory used.** Columns 0,2-9,18-22,23-29,30-36 of
     the CSV header (`frame, x, y, x_speed, y_speed, g_speed, angle,
     air, rolling, gameplay_frame_counter, stand_on_obj, vblank_counter,
     lag_counter, sidekick_present, sidekick_x, sidekick_y,
     sidekick_x_speed, sidekick_y_speed, sidekick_g_speed,
     sidekick_angle, sidekick_air, sidekick_rolling, sidekick_ground_mode,
     sidekick_x_sub, sidekick_y_sub, sidekick_routine,
     sidekick_status_byte, sidekick_stand_on_obj`) confirm the above.

   So the despawn hypothesis is **ruled out** without needing the
   v6.11-s3k regeneration. The geometric contradiction stands as
   stated in step 2 above: with `(a1)` definitively Tails and the
   captured y_pos values bracketing a `subq.w #1` then `sub.w d3`
   pattern that only appears in `loc_1E154` (ROM bytes verified in
   step 3), the ROM is reaching `loc_1E154` against Tails on F7614
   for some `(a0)` whose geometry differs from the Spring_Down at
   slot $11. The next-round actions in step 4 (regenerate fixture
   with v6.11-s3k armed so the captured `(a0)` reveals which solid
   object is the lifter) remain the unblocking step. No engine code
   change is safe to land in this round; doc-only.

6. **v6.11-s3k fixture regenerated; ROM-side path reframed (round 2026-04-30 followup).**
   The CNZ fixture under `src/test/resources/traces/s3k/cnz/` was
   regenerated against `lua_script_version: "6.11-s3k"` (commit
   `ce4278974`). Two structural findings landed by inspecting the
   regenerated `aux_state.jsonl.gz`:

   - **`(a0)` at the `loc_1E154` lift PCs is slot $11 = the same
     spring object Tails was riding.** The `position_write` event at
     trace_frame=7614 carries `a0=$FFFFB4EA` for both `0x1E172` and
     `0x1E182` writes (and `a1=$FFFFB04A` = Tails). Slot 17 = $FFB4EA
     given `OBJ_TABLE_START=$B000` and `OBJ_SLOT_SIZE=$4A`
     (17×$4A = $4EA). The `object_state` for slot 17 across F7600-7619
     reports `object_code=0x00023050, subtype=0x12, x=0x0E38,
     y=0x04D0`. That `object_code` is the function pointer the slot
     was initialized with by `Obj_Spring`'s dispatch
     (sonic3k.asm:47500-47513). Subtype `$12` selects index `2` via
     `lsr.w #3, d0; andi.w #$E, d0` — but **index 2 dispatches to
     `Spring_Down` per `Spring_Index` (sonic3k.asm:47517-47522)**, not
     to `Spring_Horizontal` as the d1/d2 width signature alone would
     suggest. Despite that, the captured d1/d2/d3 values from the
     regenerated `solid_object_cont_entry` event (`d1=0x0013, d2=0x000E`
     for the Tails-targeted iterations) match
     `Obj_Spring_Horizontal` (sonic3k.asm:47772-47774), **not**
     `Obj_Spring_Down`. The most likely explanation is that the slot's
     active routine pointer is updated post-init to
     `Obj_Spring_Horizontal` while the on-disk function-pointer field
     read by the recorder still shows the original `Obj_Spring`
     dispatcher pointer; the geometry the SolidObject helpers see
     belongs to the horizontal variant. Cross-checking with
     `Obj_Spring_Horizontal`'s `move.w #$13,d1; move.w #$E,d2;
     move.w #$F,d3` (lines 47772-47774) gives an exact match.

   - **The geometric contradiction in step 2 dissolves.** The
     "spring is 8 px below the rider's seat" math used `Spring_Down`'s
     `d2=8`, but the actual lifter at F7614 has `d2=0x0E` (horizontal
     spring's halfHeight). With Tails y_pos = 0x04B1 (post-`Tails_Jump`
     rolling +1), spring y_pos = 0x04D0, d2_orig = 0x0E,
     y_radius = 0x0E (Tails post-roll), default_y_radius = 0x0F:
     ```
     loc_1DFD6 d3 = (0x04B1 - 0x04D0) + 4 + 0x0E + 0x0E
                = -0x1F + 4 + 0x1C
                = 1
     cmpi.w #0x10, d3   →  1 < 0x10  →  blo.s loc_1E154 ✓
     loc_1E154:
       subq.w #4, d3    →  d3 = -3
       subq.w #1, y_pos(a1)  → y goes 0x04B1 → 0x04B0   (recorded `0x1E172 val=0x04B1` = pre-write hit timing)
       sub.w  d3, y_pos(a1)  → y goes 0x04B0 → 0x04B0 - (-3) = 0x04B3 (recorded `0x1E182 val=0x04B0` = pre-write hit timing)
     ```
     Net lift on F7614: y goes 0x04B1 → 0x04B3 (+2 px). Combined with
     `Tails_Jump`'s rolling +1, the total y_pos delta from F7613 end
     (0x04B0) to F7614 end (0x04B3) is exactly the +3 px the trace
     shows. **The ROM-side accounting is now complete.**

     The recorded BizHawk hook timing semantics for byte writes:
     `event.onmemorywrite` reads the watched word's value via
     `mainmemory.read_u16_be` AT THE MOMENT THE HOOK FIRES. For a
     `<op>.w y_pos(a1)` instruction the hook fires before the second
     byte's commit propagates back to `mainmemory`, so the captured
     `val` corresponds to the **pre-write** word value, not the
     post-write value. This explains the apparent -1 / -1 readings
     while the actual instruction effect is -1 (subq) then +3 (sub.w
     of d3=-3). The aux event field thus reads as "y just before this
     instruction wrote" rather than "y just after" — load-bearing for
     post-hoc reconstruction.

   - **Engine-side gap.** The engine's `resolveContactInternal`
     STANDING path (`ObjectManager.java:5969-5988`) sets
     `newCenterY = playerCenterY - distY + 3`, which is algebraically
     equivalent to ROM's `loc_1E154` final y. With `distY=1`,
     `newCenterY = playerCenterY + 2`, mapping Tails y from 0x04B1 to
     0x04B3. The engine therefore SHOULD produce 0x04B3 if the spring's
     resolveContact path fires for Tails on F7614 with these inputs.
     Engine's actual y at F7614 = 0x04B1 (only the +1 rolling-radius
     adjustment applied). The remaining gap is in the spring-vs-Tails
     contact dispatch, not in the lift arithmetic. Possible causes
     under investigation:
     - The spring's `isSolidFor` returns false for Tails this frame
       (e.g. `proactiveTriggeredThisUpdate.contains(player)` from a
       same-frame proactive trigger that ROM doesn't fire).
     - Tails's center coordinates at the time of the contact pass
       differ from ROM's by an integer pixel (e.g. the engine applies
       Tails_Jump's +1 BEFORE the contact pass while ROM applies it
       inside the same dispatch — the pre-/post-jump ordering matters
       for `distY`).
     - Sidekick's solid-contact pass for this spring is gated off
       (e.g. `useStickyBuffer=false` for sidekick, or
       `processCompatibilityCheckpoint` skips Tails when the leader
       is the active player for that solid).

   - **Trace-event parser fix landed.** The v6.11-s3k recorder emits
     `a0` and `a1` as 64-bit hex strings (e.g.
     `"0xFFFFFFFFFFFFB04A"`) because Lua's `string.format("%08X",
     ...)` with negative numbers under-truncates. The Java parser at
     `TraceEvent.parseHexInt` previously used `Integer.parseInt(hex,
     16)`, which fails on values >32-bit. Fixed to use
     `Long.parseUnsignedLong(hex, 16)` and cast to `int` (only the
     low 32 bits are semantically used as M68K addresses are
     24-bit). Without this fix the test cannot even load the
     regenerated fixture (`NumberFormatException: For input string:
     "FFFFFFFFFFFFB000"`).

   - **Cross-game safety verified.** S1 GHZ
     (`TestS1Ghz1TraceReplay`), S1 MZ
     (`TestS1Mz1TraceReplay`), and S2 EHZ
     (`TestS2Ehz1TraceReplay`) all PASS with the parser fix in
     place. AIZ first-error stays at F4679 (existing baseline).
     CNZ first-error stays at F7614 (the engine fix is deferred —
     the parser fix is required just to run the test).

   No engine-side fix lands in this round. The remaining work is
   isolating which dispatch gate prevents the engine from invoking
   `resolveContactInternal` for the spring/Tails pair on F7614, then
   landing a ROM-cited fix gated through `PhysicsFeatureSet` or via
   a spring-instance change that mirrors the ROM `Obj_Spring_Horizontal`
   per-frame `SolidObjectFull2_1P` dispatch independent of Tails's
   air state.

### Removal Condition

Remove this entry once `TestS3kCnzTraceReplay#replayMatchesTrace`
advances past F7614 with a ROM-cited fix that explains the +3 px
y_pos delta on the `Tails_Jump` frame. The fix must:

- Cite ROM lines in `docs/skdisasm/sonic3k.asm` for the y_pos write
  it implements.
- Preserve the comparison-only trace invariant (no per-frame writes
  from CSV/aux into the engine in committed test code).
- Keep S1 GHZ, S1 MZ1, S2 EHZ, and S3K AIZ traces green.
- If the fix is per-game, gate it through `PhysicsFeatureSet`, not
  `if (gameId == GameId.S3K)`.

---

## AIZ2 Trace F7127 — Tails Phantom Landing While Falling

**Status:** RESOLVED — root cause was a stuck `state==2`
(solid-stay) on `Sonic3kCollapsingPlatformObjectInstance` after Sonic
left the platform mid-stay, leaving the invisible-but-still-solid parent
in place indefinitely so that Tails (passing through the X range ~250
frames later) snap-landed on a phantom surface that ROM had already
demoted to the falling/fragments-only state. Fix: the engine's
`update()` switch in `Sonic3kCollapsingPlatformObjectInstance` now
unconditionally promotes `state=2` → `state=3` on the frame after
`releasePending` is set, mirroring ROM `loc_205DE` (sonic3k.asm:44850-44854)
which rewrites the action pointer to `loc_20620` whenever `$38`
underflows, regardless of whether either player still has the platform's
standing bit set. Previously the engine waited for the next solid pass
to see a standing contact via `onSolidContact` (sonic3k.asm:44864
`sub_205FC`) before transitioning, which is correct ONLY when the player
stays on the platform through fragmentation; ROM's transition is
unconditional.

The trace report's first error advances from F7127 to F7171 with this
fix in place. The four candidates flagged in the prior round
(collision-index off-by-one, top-solid-bit mismatch, AIZ2 reload-resume
pointer staleness, Tails airborne-rolling y_radius rule) were all ruled
out: the engine sensor probe at F7126/F7127 reported `dist=4` (no
penetration) and `resolved=false`, confirming terrain collision was
correct. The actual landing came from `ObjectManager.SolidContacts.
resolveContactInternal:5977` (the flat-solid top-landing path) chained
from `resolveSlopedContact:5679` for the F7126 CollapsingPlatform spawn
at `(0x0E70, 0x0368)`, which Sonic had triggered around F6929 (state=0
→ 1) and which the engine had carried through state=2 from ~F6943
onward without ever advancing to state=3.

**Location:** Sidekick airborne→ground transition path (Tails following
Sonic into the AIZ2 narrow corridor near `(0x0E42, 0x033B)`).
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`
(`aiz1_to_hcz_fullrun` profile), first strict error at frame 7127,
1,062 errors total.

### Symptom

`TestS3kAizTraceReplay#replayMatchesTrace` first fails at F7127:

```text
tails_y mismatch (expected=0x033B, actual=0x0339)
```

Per `target/trace-reports/s3k_aiz1_context.txt` around F7126-F7136:

- F7126: ROM and engine agree. Tails airborne+rolling at
  `(0x0E3E, 0x0335)`, x_speed=0x046C, y_speed=0x05B0 (falling fast),
  ground_vel=0x00A8, x_radius/y_radius standard.
- F7127: ROM has `tails_y=0x033B, y_speed=0x05E8, air=1, ground_vel=0x00A8`
  (Tails continues falling, gravity adds 0x38 to y_speed).
  Engine has `tails_y=0x0339, y_speed=0x0000, air=0,
  ground_vel=0x0484` (engine LANDS Tails, sets ground_vel=former
  x_speed). The 2-pixel y mismatch and the air=0 vs air=1 are the
  load-bearing divergences.
- F7128-F7140: Engine Tails walks slowly along non-existent terrain
  while ROM Tails keeps falling (y goes 0x033B→0x0340→0x0347→…,
  y_speed gains 0x38 per frame as expected for free-fall under
  gravity 0x38). The engine's phantom-landing y_pos drifts up by
  ~1 px per ~2 frames as it tries to walk a flat surface.

Diagnostic line at F7127:
```
tailsCpu status=06 obj=00 gv=00A8 xv=046C stat=06 input=7800
        branch=fallthrough_sub20 ctrl2=0000/00 post=0000,0000,00
tailsInteract slot=16 ptr=B4A0 obj=0002BF5A rtn=00 st=00 @0F85,0338
        sub=00 onObj=false objP2=false active=true
```

The recorded `branch=fallthrough_sub20` is **not** the divergence
itself; it is just the lua's diagnostic snapshot of which path the
ROM's `Tails_CPU_Control` (sonic3k.asm:26696, `loc_13DD0`) entered for
the *next* frame's input mask. At F7127, both ROM and engine see
Tails_CPU's leader sample as "leader grounded, slow gv<$400, no
on-object" → the d2 target gets `subi.w #$20,d2` (sonic3k.asm:26694),
hence the lua's `fallthrough_sub20` label. This is purely an input
mask, not a position write.

The interact slot's `obj=0002BF5A` resolves to `Obj_AnimatedStillSprites`
(sonic3k.asm:60394, `loc_2BF5A`) — a non-solid display object — so the
engine's `tailsInteract.active=true` is a stale visual reference, not a
solid-object collision.

### Diagnosed Constraints

The shape of the divergence (engine landing Tails 2 px above where ROM
keeps falling, with engine `ground_vel = 0x0484` exactly equal to the
prior `x_speed`) implies:

1. The engine's terrain probe (`CollisionSystem.resolveAirCollision`,
   `src/main/java/com/openggf/physics/CollisionSystem.java:476`) detects
   ground at engine `y=0x0339` and runs the airborne-landing handler,
   which sets `y_speed=0`, `ground_vel=x_speed`, clears `air`.
2. ROM's equivalent (`Sonic_DoLevelCollision` / `Tails_TouchFloor`,
   sonic3k.asm:24340-24369) finds *no* floor at the same y, so Tails
   continues falling.

This is at the AIZ2 reload-resume checkpoint (`cp aiz2_reload_resume
z=0 a=1 ap=0 gm=12`) so the divergence is **inside AIZ2**'s level
collision data after the AIZ1→AIZ2 seamless transition. Possible
causes:

- **AIZ2 collision-index off-by-one** in the engine's collision
  array load (`Sonic3kLevel.loadChunksWithCollision` /
  `decodeCollisionPointer`). A misaligned collision index for a
  specific AIZ2 chunk would phantom-place a floor 2 px higher than
  ROM at this exact location.
- **Top-solid bit (`top_solid_bit` / `lrb_solid_bit`)** mismatch. AIZ2
  uses dual-path collision (DUAL_PATH model, sonic3k.asm:41051+).
  If the engine has Tails reading the wrong path (Primary vs
  Secondary), a phantom floor could appear.
- **Camera-bound AIZ2 reload offset** — prior bugs in this file
  (F5497, F5736) document the AIZ2 reload checkpoint as fragile;
  another stale bound or path table after the seamless transition
  could plant a phantom floor pointer.
- **Y-radius drift** — Tails airborne+rolling has y_radius=$0E (14)
  in ROM (sonic3k.asm:68062 sets it on jump-from-cylinder; rolling
  also sets y_radius=$0E in `Sonic_RollMode` / `Sonic_Roll_BalanceCheck`,
  sonic3k.asm:24400+). If the engine's airborne ground-sensor probe
  uses 16 (standing) instead of 14 (rolling) for the y-offset, the
  sensor would hit ground 2 px earlier — exactly the observed shape.

The ROM's airborne floor probe uses `y_radius` (current, 14 when
rolling) to compute the sensor offset. Engine ground sensors (S3K
DUAL_PATH, `Sensor.scan` callers) need to be audited for whether they
read CURRENT y_radius or default y_radius for sidekick airborne probes.

This entry intentionally does not propose a fix until the camera bound
state, AIZ2 collision pointer table, and the airborne-rolling sensor
y_offset are each independently verified against `target/trace-reports/`
diagnostic output around F7126-F7128.

### Removal Condition

Remove this entry once `TestS3kAizTraceReplay#replayMatchesTrace`
advances past F7127 with a ROM-cited fix mapped to one of the four
candidates above. The fix must:

- Cite ROM lines in `docs/skdisasm/sonic3k.asm` for any sensor /
  collision / radius rule it changes (and cross-check `s2disasm` if
  the rule is shared).
- Preserve the comparison-only trace invariant (no per-frame writes
  from CSV/aux into the engine in committed test code).
- Keep S1 GHZ, S1 MZ1, S2 EHZ traces green.
- If the fix is per-game, gate it through `PhysicsFeatureSet`, not
  `if (gameId == GameId.S3K)`.

---

## AIZ2 Trace F7171 — Tails Killed Mid-Run vs. Engine Continuing Follow-Steering

**Status:** OPEN — next AIZ trace blocker after F7127 was resolved.

**Location:** AIZ2 reload-resume run (`cp aiz2_reload_resume z=0 a=1 ap=0
gm=12`). Tails is following Sonic east through the corridor near
`y_pos ≈ 0x047E`. Sonic is at `y_pos = 0x02FA`, well above. The two
players are at very different y-positions; Tails is on a lower path.

**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`,
`TestS3kAizTraceReplay#replayMatchesTrace` first strict error at
F7171, 1049 errors, 0 warnings.

### Symptom

```text
First error: frame 7171 -- tails_x_speed mismatch
    (expected=0x0000, actual=0x0120)
```

`target/trace-reports/s3k_aiz1_context.txt` rows F7166-F7172 show:

| Frame | tails_x | tails_y | xv | yv | gv | air |
|-------|---------|---------|----|----|----|-----|
| F7170 | 0x0ECE  | 0x047E  | 0x0114 | 0x0000 | 0x0114 | 0 |
| F7171 ROM | 0x0ECF | **0x0477** | **0x0000** | **-0x0700** | **0x0000** | **1** |
| F7171 ENG | 0x0ED0 | 0x047E  | 0x0120 | 0x0000 | 0x0120 | 0 |
| F7172 ROM | **0x7F00** | -0x0007 | 0x0000 | 0x0000 | 0x0000 | 1 |

The ROM-side shape is unmistakable: `y_speed = -0x0700`,
`x_speed = 0`, `g_speed = 0`, `air = 1`, with `routine = 6` written
into the sidekick's routine field at F7172 (per the
`sidekick=...rtn=06` aux line). This is **`Kill_Character`**
(sonic3k.asm:21141 `loc_1036E`):

```asm
loc_1036E:
    clr.b   status_secondary(a0)
    clr.b   status_tertiary(a0)
    move.b  #6,routine(a0)             ; routine 6 = dead/falling
    jsr     (Player_TouchFloor).l       ; restore default radii
    bset    #Status_InAir,status(a0)    ; air = 1
    move.w  #-$700,y_vel(a0)            ; bounce-up velocity
    move.w  #0,x_vel(a0)
    move.w  #0,ground_vel(a0)
    move.b  #$18,anim(a0)               ; death-animation id
```

The next frame F7172 then shows `tails_x = 0x7F00, tails_y = -0x0007`
— i.e. the Tails-CPU off-screen-watchdog respawn path
(`sub_13ECA` at sonic3k.asm:26800 writes `x_pos = 0x7F00`,
`y_pos = 0`) has fired, after which `MoveSprite_TestGravity`
(sonic3k.asm:36077) adds `+0x38` to `y_vel` and applies the velocity
to position, dropping y from 0 to -7. This confirms the death-and-
respawn chain ran end-to-end on the ROM side.

The engine, by contrast, runs `SidekickCpuController.updateNormal()`
with `branch=leader_fast` (Sonic's `g_speed = 0x0600 ≥ 0x0400`,
sonic3k.asm:26692-26694) and writes `tails_x_speed = 0x0120`,
`tails_g_speed = 0x0120`, `tails_y_speed = 0`, `air = 0` — a normal
right-input acceleration step, never invoking `Kill_Character`.

### What kills ROM Tails at F7171

The trigger is **not** any of:

- `Tails_CPU_Control` (sonic3k.asm:26354+): Both ROM and engine see
  `branch=leader_fast`, `ctrl2=0x0808` (right-pressed only, **no
  jump button**), `dx=0x004C`, `dy=0xFE7B`. The auto-jump latch at
  `loc_13E9C` (sonic3k.asm:26775) fails its 64-frame gate
  (`(Level_frame_counter+1).w & $3F = 0x18 ≠ 0`) and the distance
  gate (`|dx|=0x4C ≥ $40` and `Level_frame_counter & $FF =
  0xD8 ≠ 0`). So neither path reaches `loc_13E9C` — Tails is not
  jumping.
- `Tails_Jump` (sonic3k.asm:28519): reads
  `Ctrl_2_pressed_logical & A|B|C`. Trace `ctrl2=0808/08` has no
  jump-press bit (high byte `$08` = right_pressed, not A/B/C).
- `Obj_TwistedRamp` (sonic3k.asm:50001): launch gate requires
  `x_vel ≥ $400`. Tails has `x_vel = 0x0114 < 0x0400` → falls through.

The candidates that DO write `y_vel = -0x0700` AND zero `x_vel`/
`ground_vel` simultaneously are:

1. **`Player_LevelBound` / `Tails_Check_Screen_Boundaries` →
   `Kill_Character`** (sonic3k.asm:23172 / 28407 → 21141). Tails's
   y_pos=0x047E exceeds `Camera_max_Y_pos + 0xE0` (the bottom
   kill plane). `Tails_Check_Screen_Boundaries` `jmp`s into
   `Kill_Character`, which is reached BEFORE `MoveSprite` in the
   call chain — this means Tails position at F7171 stays at 0x047E,
   which contradicts the observed F7171 `tails_y = 0x0477`.
2. **`TouchResponse` hit on a deadly enemy/spike** (sonic3k.asm:
   26266 inside `Obj_Tails`). `TouchResponse` runs AFTER
   `Tails_Modes` / `MoveSprite`, so position has already advanced by
   one frame's velocity, then `Hurt_Sonic` → `loc_1036E`
   `Kill_Character` is invoked. The 7-pixel y-decrease (0x047E →
   0x0477) is consistent with `MoveSprite_TestGravity2` having
   already run with `y_vel = -0x0700` set BY a previous call —
   **but** `Tails_Stand_Path` only calls `MoveSprite` once.
3. **`sub_F846` background-collision crush kill** (sonic3k.asm:
   19946 + 27531-27533): inside `Tails_Stand_Path` the sequence
   `bsr.w sub_F846 / tst.w d1 / bmi.w Kill_Character` fires when
   `Background_collision_flag` is set and `FindFloor` returns a
   negative penetration. AIZ2 does not normally enable
   `Background_collision_flag`, so this candidate is unlikely.

The **observed F7171 position shift `0x047E → 0x0477` (y up by 7
pixels)** can only be produced by `MoveSprite` running with
`y_vel = -0x0700` already in place. The simplest reading consistent
with the trace is that `Kill_Character` ran early in the frame
(probably from the boundary check), which set `y_vel = -0x0700`,
and the post-Kill flow somehow also reached `MoveSprite_TestGravity2`
to apply that velocity. Locating the exact call site needs an
extended ROM trace recorder — the current aux only samples
end-of-frame state.

### Diagnosed Constraints

- `sprite.getY()` returns top-left Y, but ROM `y_pos(a0)` is
  ROM-centre Y. The engine's level-boundary check
  (`PlayableSpriteMovement.doLevelBoundary`, line ~1891):
  ```java
  if (sprite.getY() > effectiveMaxY + 224) {
      ...
      cpuController.despawn(LEVEL_BOUNDARY);
  ```
  computes `getY()` = `centreY - height/2`. For Tails with
  `height_pixels = 0x18 = 24`, `getY() = y_pos - 12`. The engine
  therefore triggers the boundary kill **12 pixels below** where
  ROM does. This is a long-standing latent off-by-12 that has not
  surfaced before because most kill-plane crossings happen far
  below the threshold (where the 12-pixel gap is invisible).
- ROM `Tails_Check_Screen_Boundaries` (sonic3k.asm:28428-28431)
  uses `cmp.w y_pos(a0),(Camera_max_Y_pos+0xE0) / blt.s` which is
  `y_pos > maxY + 0xE0` semantically (m68k `cmp.w src,dst` →
  `dst - src` then `blt` on signed-less). The engine's `>` matches
  this comparator; only the `getY()` vs `getCentreY()` side is off.
- Switching the engine to `getCentreY()` is a global change that
  affects Sonic too (Sonic height_pixels = 0x28 → 20 px offset).
  Audit needed: do other AIZ/CNZ traces depend on the current
  off-by-12 boundary semantics? `TestS3kAizTraceReplay`,
  `TestS3kCnzTraceReplay`, `TestS3kMgzTraceReplay` should be
  re-run after any change.
- The 7-pixel y-up at F7171 (after Kill_Character zeroes velocities
  but writes `y_vel=-0x0700`) needs an extended recorder probe to
  pinpoint whether `MoveSprite` ran post-Kill or whether
  `Player_TouchFloor` shifts `y_pos` for a non-rolling sidekick
  in some path the engine hasn't replicated.

### Ruled-Out Hypotheses

- **`Tails_CPU_auto_jump_flag` or `loc_13E9C` auto-jump**: The
  64-frame and distance gates fail at F7171 (`Level_frame_counter
  = 0x1AD8`, `|dx| = 0x4C ≥ $40`). Neither ROM nor engine sees the
  auto-jump trigger fire.
- **`Tails_CPU_flight_timer` despawn warp via `sub_13EFC`**: At
  F7171 Tails is on-screen (Sonic is at `y_pos=0x02FA`, Tails at
  `y_pos=0x047E`, both inside the camera 0x0EDA cam-X window of
  ±320). The trace `tails rf=04` shows render_flags bit 7 clear
  (off-screen high-bit), but Tails was visible in prior frames
  and the timer would not have accumulated the 5*60 frames needed
  to trigger `sub_13ECA` from this branch.
- **Tails_TwistedRamp launch**: requires `x_vel ≥ 0x0400`; Tails
  has only `0x0114`.

### Removal Condition

Remove this entry once `TestS3kAizTraceReplay#replayMatchesTrace`
advances past F7171 with a ROM-cited fix. The fix must:

- Cite ROM lines in `docs/skdisasm/sonic3k.asm` for any
  `Player_LevelBound` / `Tails_Check_Screen_Boundaries` /
  `Kill_Character` / `TouchResponse` / `sub_F846` rule it changes.
- Preserve the comparison-only trace invariant (no per-frame
  writes from CSV/aux into the engine in committed test code).
- Keep S1 GHZ, S1 MZ1, S2 EHZ, S3K AIZ post-F7127, and S3K CNZ
  pre-F7614 traces green (CNZ first-error must stay at or advance
  past F7614).
- If the fix is per-game, gate it through `PhysicsFeatureSet`,
  not `if (gameId == GameId.S3K)`.
- The boundary-kill `getY()` vs `getCentreY()` divergence
  identified above is the most promising candidate. Before
  switching, audit Sonic-side kill behaviour across traces
  (existing AIZ/CNZ blockers in this file may have implicitly
  calibrated against the off-by-12 semantics). The shared
  `getY()` call lives in `PlayableSpriteMovement.doLevelBoundary`
  (line ~1891), used uniformly by all sprites; if the change
  is needed only for sidekicks, gate via a new
  `PhysicsFeatureSet.levelBoundaryUsesCentreY` flag that defaults
  to `false` for current behaviour.

### Cross-Game Audit (2026-04-30)

All three games' bottom-boundary checks compare against
`y_pos(a0)` / `obY(a0)` — i.e. ROM-centre Y, not top-left:

| Game | ROM | File:Line | Compare |
|------|-----|-----------|---------|
| S1 (Sonic) | `Sonic_LevelBound` `.bottom` | `s1disasm/_incObj/01 Sonic.asm:1014` | `cmp.w obY(a0),d0 / blt.s .bottom` |
| S2 (Sonic) | `Sonic_LevelBound` `Sonic_Boundary_CheckBottom` | `s2.asm:36936-36951` | `cmp.w y_pos(a0),d0 / blt.s Sonic_Boundary_Bottom` |
| S3K (Sonic) | `Player_LevelBound` `Player_Boundary_CheckBottom` | `sonic3k.asm:23188-23196` | `cmp.w y_pos(a0),d0 / blt.s Player_Boundary_Bottom` |
| S3K (Tails CPU) | `Tails_Check_Screen_Boundaries` `loc_14F30` | `sonic3k.asm:28423-28431` | `cmp.w y_pos(a0),d0 / blt.s loc_14F56` |

S2 also has a `fixBugs` block (s2.asm:36938-36948) clamping
`Camera_Max_Y_pos` to its target, which the engine already mirrors via
`Math.max(camera.getMaxY(), camera.getMaxYTarget())`. S1 has the
equivalent `FixBugs` clamp at s1disasm/_incObj/01 Sonic.asm:1003-1012.

Engine `PlayableSpriteMovement.doLevelBoundary` (~line 1891) compares
`sprite.getY()` (top-left) instead of centre, which is the off-by-
`height/2` divergence. For Tails (`height_pixels = 0x18 = 24`)
that is 12 px; for Sonic (`height_pixels = 0x28 = 40`) that is 20 px.

The kill velocity writes (`x_vel = 0`, `y_vel = -0x700`,
`ground_vel = 0`) inside `Kill_Character` (sonic3k.asm:21141-21151)
are already replicated by:

- `SidekickCpuController.beginLevelBoundaryKill` for the CPU sidekick
  path (`AbstractPlayableSprite.setXSpeed/YSpeed/GSpeed` zeroes,
  sets `air=true`, then routine-6-equivalent `DEAD_FALLING` state).
- `AbstractPlayableSprite.applyDeath` for the human-controlled path
  (matches the same field writes with `setYSpeed((short) -0x700)`,
  see `applyPitDeath`).

So the missing piece is purely the **comparand**: switch
`sprite.getY()` to `sprite.getCentreY()` for the bottom kill plane
test. The proposed `PhysicsFeatureSet.levelBoundaryUsesCentreY`
flag should default to `true` for `SONIC_3K` (ROM-accurate) and
`false` for `SONIC_1` and `SONIC_2` until their trace baselines
are verified to honour the same kill semantics. The S1 GHZ/MZ1
and S2 EHZ traces were recorded against the engine's existing
top-left compare, so flipping the global default is too risky in
the same change. Once S3K AIZ/CNZ progress past their current
blockers, S1/S2 should be re-recorded or re-validated and the
flag can be flipped to `true` for all three games.

### Implementation Status (2026-04-30)

The centre-Y feature flag landed on this branch:

- `PhysicsFeatureSet.levelBoundaryUsesCentreY` added,
  `SONIC_3K = true`, `SONIC_1`/`SONIC_2 = false` (deferred until
  S1 GHZ/MZ1 and S2 EHZ trace baselines are re-recorded — ROM
  cites for those games at `s1disasm/_incObj/01 Sonic.asm:1014`
  and `s2.asm:36950` are unambiguous, so the flip is mechanical
  once trace baselines confirm parity).
- `PlayableSpriteMovement.doLevelBoundary` (line ~1891) now
  switches between `sprite.getCentreY()` and `sprite.getY()` based
  on the flag.
- Regression coverage in
  `src/test/java/com/openggf/sprites/managers/TestPlayableSpriteMovement.java`:
  `s3kBottomLevelBoundaryUsesCentreY` (kill fires when only
  centreY exceeds threshold),
  `s2BottomLevelBoundaryStaysOnTopLeftCompareUntilTraceRevalidation`,
  `s1BottomLevelBoundaryStaysOnTopLeftCompareUntilTraceRevalidation`
  (both confirm flag stays false and kill suppresses), and
  `s3kBottomLevelBoundaryRespectsTopLeftWhenCentreYBelowThreshold`
  (no false positives below threshold).

### Resolved — `Apparent_zone_and_act` resize gating

The miniboss-skip gate in `Sonic3kAIZEvents.updateAiz2SonicResize1`
and `updateAiz2KnuxResize1` has been corrected to read
`LevelManager.getApparentAct()` (mirroring ROM
`Apparent_zone_and_act`) instead of the legacy `enteredAsAct2`
boolean, which was set on every reload-resume that lacked a
queued fire continuation.  ROM cites:
`sonic3k.asm:39046-39058` (AIZ2_SonicResize1 apparent-act gate),
`sonic3k.asm:39157-39174` (AIZ2_KnuxResize1 same gate),
`sonic3k.asm:104627` (AIZ1_AIZ2_Transition does not write
`Apparent_zone_and_act`), `sonic3k.asm:10222` and `:61760`
(direct-entry / starpost-restore writes set it to `$0001`).
Regression coverage in `TestSonic3kAIZEvents`:
`aiz2FromFireTransitionDoesNotSkipMinibossPath` (existing) and
`aiz2ReloadResumeWithApparentAct0DoesNotSkipMinibossPath` (new).

### Residual blocker — AIZ2 miniboss-area geometry divergence at F7171

After the apparent-act gating fix, the AIZ trace still fails at
F7171 because of a downstream divergence in the miniboss-area
geometry, not in the resize gating itself.  Diagnostic capture
during replay:

- `Sonic3kAIZEvents.updateAiz2SonicResize1` correctly observes
  `apparentAct = 0` and routes into `SonicResize2` (no
  miniboss-skip).
- ROM at F7170 has Sonic at `x = 0x0F74`; the engine's Sonic
  stops advancing at `cameraX = 0x0E15 / spriteX = 0x0EB5`,
  ~0xC0 px short of the ROM trajectory and well below the
  `cameraX >= 0x0ED0` narrow trigger.
- Because the engine never reaches `cameraX >= 0x0ED0`,
  `Camera_max_Y_pos` stays at `AIZ2_DEFAULT_MAX_Y = 0x0590` and
  the centre-Y bottom-kill compare never fires; Tails is alive
  in the engine but dead in ROM at F7171, producing the
  cascading divergence.

The follow-up needs to find why the engine's Sonic and camera
stall around `cameraX = 0x0E15` while in the AIZ2 miniboss-area
approach.  Likely candidates: AIZ2 collision/layout differences
in the miniboss-arena entry, missing solid-floor geometry that
ROM uses to bridge the chasm before the miniboss spawns at
`Camera_X_pos >= 0x0F50` (`AIZ2_SonicResize2` lock-X), or a
delta in how the engine handles the miniboss-area approach
when the player has not yet defeated the miniboss.

Until that lands, the centre-Y change and the apparent-act
gating fix are both ROM-parity correct on their own; the AIZ
trace first-error frame remains at F7171 and is now blocked by
the geometry/collision divergence rather than by miss-gated
resize state.

### Update (2026-04-30) — Sonic stall resolved; F7171 advances to F4679

Subsequent diagnostic re-capture confirmed Sonic now reaches
the AIZ2 miniboss-area approach: at F7171 ROM cam=`0x0EDA` and
the engine cam matches.  Sonic position at F7170 also matches
ROM (`x = 0x0F74`).  The remaining F7171 mismatch was therefore
not "Sonic stalls"; instead the diff was Tails-only:

| Frame | tails_x | tails_y | xv | yv | gv | air |
|-------|---------|---------|----|----|----|-----|
| F7171 ROM | 0x0ECF | **0x0477** | **0x0000** | **-0x0700** | **0x0000** | **1** |
| F7171 ENG (post-y_vel-fix) | 0x0ED0 | 0x047E | 0x0000 | 0x0000 | 0x0000 | 1 |

Engine boundary check was firing the kill on the right frame,
zeroing x_vel/g_vel and setting Status_InAir, but
`SidekickCpuController.beginLevelBoundaryKill` was zeroing
y_vel as well.  ROM Kill_Character at sonic3k.asm:21149 writes
`y_vel = -$700`, not zero.  Because Tails_Check_Screen_Boundaries
reaches Kill_Character via `jmp` (sonic3k.asm:28443) and
Kill_Character ends with `rts` (sonic3k.asm:21159), control
unwinds to the *caller of* Tails_Check_Screen_Boundaries —
inside Tails_Stand_Path at sonic3k.asm:27526 the next call is
`MoveSprite_TestGravity2`, which falls through to MoveSprite2
(sonic3k.asm:36088,36053) and applies the freshly written
`y_vel = -$700` to `y_pos`.  That is the 7-pixel y-up
(`0x047E -> 0x0477`) recorded in the trace at F7171.

`SidekickCpuController.beginLevelBoundaryKill` now sets
`y_vel = -0x700` so the airborne movement manager's
SpeedToPos-equivalent (`modeNormal -> sprite.move`) replicates
the same in-frame 7-pixel shift.  The legacy
`TestSidekickCpuDespawnParity#levelBoundaryKillRunsTailsTouchFloorBeforeDeathState`
expected `y_vel = 0` post-kill; that expectation was calibrated
to the bug, so it is updated to `y_vel = -0x700` with the new
ROM cite block.

The AIZ trace first-error advances from F7171 to F4679,
inside the AIZ1→AIZ2 fire-transition window
(`cp aiz1_intro_refresh_begin`, cam ~`0x2D90`).  At F4679 ROM
records `tails_y = 0x040F` while the engine reports
`tails_y = 0x042F`.  The trace's `tailsAizBoundary` aux row
shows the boundary-check function clamped Tails to the right
edge (`x = 0x2D90`, `x_vel = 0`, `g_vel = 0`) without entering
the death path — so this is *not* a Kill_Character case; ROM
end-of-frame `y_vel = 0` is consistent with a non-kill flow.
Engine end-of-frame divergence (~32 px) at F4679 is therefore
a separate AIZ1 fire-transition physics gap (likely tied to
the bridge/tree object solidity or a secondary boundary
clamp) that the F7171 fix surfaces but does not address.

#### Residual blocker — AIZ1 left-boundary kill post-MoveSprite gap at F4679

`tailsAizBoundary cam=2D8B/4000 y=02E0/02E0 tree=2D41,0402,010F,0198->2D41,0402,010F,0198 boundary=kill 2D41,0402,010F,0198->2D90,0402,0000,0198 post=2D95,040F,0000,0000`

The earlier diagnosis ("32-pixel y-down gap, right-boundary
clamp") was partially incorrect.  Re-examination of the
recorder hooks (`tools/bizhawk/s3k_trace_recorder.lua` lines
2095-2103, 2152-2161) and ROM
`Tails_Check_Screen_Boundaries` (sonic3k.asm:28407-28451)
shows what actually happens at F4679:

1. Tails enters the boundary check at `(0x2D40, 0x0402)`,
   `vels = (0x010F, 0x0198)`.  `predictedX = x_pos + (x_vel<<8)`
   ≈ `0x2D42`.
2. ROM `Camera_min_X_pos` is `0x2D80` at this moment, so
   `Camera_min_X_pos + 0x10 = 0x2D90 > predictedX` and the
   `bhi.s loc_14F5C` branch (sonic3k.asm:28417) is taken.  The
   trace's recorded `cameraMinX = 0x2D8B` is the
   end-of-frame value (recorder calls `refresh_camera` at
   flush time, line 2133); the camera scrolled +11 px after
   the boundary check.
3. `loc_14F5C` (sonic3k.asm:28446-28451) clamps
   `x_pos = 0x2D90`, zeros x-subpixel, x_vel and ground_vel,
   then `bra.s loc_14F30` to the death-plane check.
4. `loc_14F30` reads `Camera_max_Y_pos = 0x02E0`, computes
   `0x02E0 + 0xE0 = 0x03C0`, and `cmp.w y_pos, d0 / blt.s`
   takes the branch to `loc_14F56` because `y_pos = 0x402 > 0x3C0`.
5. `jmp Kill_Character` runs.  Kill_Character writes
   `y_vel = -$700` (sonic3k.asm:21149), `x_vel = 0`,
   `ground_vel = 0`, then `rts`.  Because Kill_Character was
   reached via `jmp` (not `jsr`), the `rts` unwinds to the
   caller of Tails_Check_Screen_Boundaries, which for the
   airborne branch is `Tails_Stand_Freespace` at
   sonic3k.asm:27553-27567.  The next instruction is
   `jsr (MoveSprite_TestGravity).l` (sonic3k.asm:27559).
6. `MoveSprite_TestGravity` falls through to `MoveSprite`
   (sonic3k.asm:36032-36042) which adds gravity to y_vel and
   shifts `y_pos` by the **freshly written** `y_vel = -$700`
   (using the pre-gravity y_vel for position).  Tails moves
   up 7 px to `y = 0x3FB`.
7. `Player_JumpAngle` and `Tails_DoLevelCollision` run; the
   end-of-frame state recorded in the trace is
   `(0x2D95, 0x040F, vels=0)`.

Engine pre-fix path took the bottom-kill branch in
`PlayableSpriteMovement.modeAirborne` (line 446) and skipped
`doObjectMoveAndFall`, going straight from the kill writes to
`doLevelCollision`.  This left Tails at `y = 0x402` going into
collision (instead of `0x3FB` after the missing MoveSprite step)
and produced an end-of-frame `tails_y = 0x042F` — 32 px below ROM.

The pixel/iter-20 fix routes the kill path through
`doObjectMoveAndFall()` so the post-Kill_Character MoveSprite
step runs, mirroring ROM's
`Tails_Stand_Freespace -> MoveSprite_TestGravity` chain.  This
moves Tails up the missing 7 px and closes 16 px of the gap
(F4679 advances from `tails_y = 0x042F` to `tails_y = 0x041F`,
ROM expects `0x040F`).

The remaining 16-px gap is downstream and does not invalidate
the MoveSprite-step fix.  Likely sources still under
investigation:

- Engine `camera.getMinX()` at boundary-check time vs ROM
  `Camera_min_X_pos`.  AIZ1 fire-transition has the hollow-tree
  object writing camera bounds (`AizHollowTreeObjectInstance`
  CAMERA_RELEASE_MIN_X = 0x1300) and the AIZ1 dynamic resize
  (sonic3k.asm:38961-38974) writing `Camera_min_X_pos = 0x2D80`.
  Frame-order sensitivity here would change `leftBoundary` and
  thus the post-clamp x_pos that the ground sensor sees.
- `Tails_DoLevelCollision` ceiling-vs-floor dispatch under
  `y_vel < 0` (post-Kill_Character moving up).  ROM
  loc_15538 (ceiling path) walls + ceiling, no ground push;
  engine `resolveAirCollision` quadrant 0x80 routes to
  `doCeilingCollision` only.  If the engine's actual
  end-of-frame y_vel landed in a different quadrant a floor
  push could explain the residual.

A separate iteration is required to instrument the
camera-min-X timing and the post-kill collision quadrant to
finish closing F4679.

#### Resolution — `applyKillCharacterTouchFloorReset` y_pos delta computed from full height instead of y_radius

The residual 16-px gap at F4679 was traced (2026-04-30 iter-22)
to `SidekickCpuController.applyKillCharacterTouchFloorReset`.
When the rolling branch fires, it adjusts the sidekick's
centre-Y by `delta` to mirror ROM `Tails_TouchFloor`'s
`add.w d0, y_pos(a0)` step.  ROM computes
`d0 = old_y_radius - default_y_radius` (sonic3k.asm:29134-29141),
which for Tails roll->stand is `14 - 15 = -1` px.

The engine instead read
`delta = sidekick.getHeight() - sidekick.getStandYRadius()`,
which for the same roll->stand transition returns
`28 - 15 = +13` px (full visual height vs radius), shifting
Tails's centre-Y +13 in the wrong direction whenever the
sidekick was rolling at the moment of the kill.  Combined
with the +1 px from the height-based `getCentreY()` jump
inside `setRolling(false)`, the engine ended F4679 with
`tails_y = 0x041F` versus ROM `0x040F` (16-px gap).

The fix replaces the buggy expression with
`sidekick.getYRadius() - sidekick.getStandYRadius()`.  After
the fix the AIZ trace first strict error advances from
F4679 to F7171 (1050 -> 1049 errors).  Engine state at
F4679 now matches ROM: `tails_y = 0x040F`, `vels = 0`,
`x = 0x2D95`.

ROM cite block (sonic3k.asm:29133-29156, `Tails_TouchFloor`):

```
Tails_TouchFloor:
    move.b  y_radius(a0),d0          ; d0 = OLD y_radius
    move.b  default_y_radius(a0),y_radius(a0)
    btst    #Status_Roll,status(a0)
    beq.s   loc_1565E                ; not rolling -> skip y_pos delta
    bclr    #Status_Roll,status(a0)
    ...
    sub.b   default_y_radius(a0),d0  ; d0 = old_y_radius - default_y_radius
    ext.w   d0
    ...
    add.w   d0,y_pos(a0)             ; y_pos += d0 (sign-flipped by angle)
```

Source: `SidekickCpuController.applyKillCharacterTouchFloorReset`
(commit `bugfix/ai-aiz-f4679-residual-16px`).

#### Resolution — `AIZ2_SonicResize2` one-frame ordering + post-`sub_13ECA` MoveSprite step

The F7171 mismatch had two compounding causes that landed
together on this iteration:

1. **`AIZ2_SonicResize2` ran one frame late.**  ROM
   `Do_ResizeEvents` runs *inside* `DeformBgLayer`
   (sonic3k.asm:38303-38316) **after** `MoveCameraX` has
   committed the new `Camera_X_pos`.  Engine
   `LevelFrameStep` runs events (step 4) **before** the
   camera step (step 5), so `camera().getX()` returns the
   previous frame's value.  At F7170, ROM saw cam=`0xED4 ≥
   0xED0` and narrowed `Camera_max_Y_pos` to `$2B8` (the
   miniboss-area cap).  The engine's `updateAiz2SonicResize2`
   read cam=`0xECE` (end-of-F7169) and skipped the narrow,
   so F7171's `Tails_Check_Screen_Boundaries` (sonic3k.asm:
   28428-28443) saw the still-default `$590` cap, missed
   the kill plane, and Tails kept following Sonic instead
   of dying.  Fix: read `camera().previewNextX() & 0xFFFF`
   in `updateAiz2SonicResize2` so the check sees the same
   `Camera_X_pos` the next frame's player physics will see,
   matching the ROM call order.  This mirrors the prior
   AIZ1 fix at line ~515 in `Sonic3kAIZEvents.updateAct1`
   (CHANGELOG entry "AIZ1 dynamic-resize one-frame ordering
   fix").  Other `updateAiz2SonicResizeN` /
   `updateAiz2KnuxResizeN` routines retained
   `camera().getX()` to keep `TestSonic3kAIZEvents` passing
   (those tests directly poke the camera and rely on the
   raw value).

2. **`updateDeadFalling` overwrote the preserved
   `Kill_Character` y-velocity.**  ROM Frame N+1 enters the
   death routine `loc_157C8` (sonic3k.asm:29283), calls
   `sub_123C2` → `sub_13ECA` (sonic3k.asm:26800-26809) which
   warps `x_pos = $7F00, y_pos = 0` and crucially does **not**
   touch `y_vel`, then returns to `loc_157C8` and runs
   `MoveSprite_TestGravity` (sonic3k.asm:29285) which falls
   through to `MoveSprite` (sonic3k.asm:36032-36042).
   `MoveSprite` shifts `y_pos` by the still-preserved
   `y_vel = -$700` *before* the `+$38` gravity write,
   producing the trace's `(y = -$0007, y_vel = -$06C8)` at
   F7172.  The engine's `applyDespawnMarker` sets
   `objectControlled = true`, which flips
   `objectControlSuppressesMovement` (see
   `AbstractPlayableSprite.setObjectControlled`) and
   short-circuits the regular `PlayableSpriteMovement` path,
   so the post-warp `MoveSprite` step never ran.
   `updateDeadFalling` was instead writing
   `setYSpeed(0x38)` straight, calibrated to a different
   ROM scenario where `y_vel` happened to enter sub_13ECA
   as `0`.  Fix: capture `oldYSpeed` (from the preserved
   Kill_Character `-$700`), call `applyDespawnMarker()` to
   warp x/y/state, then apply the inlined
   `MoveSprite`-equivalent — `y_pos += oldYSpeed >> 8` and
   `y_vel = oldYSpeed + $38`.  This produces the F7172
   `(y = -7, y_vel = -$06C8)` end-of-frame state ROM records.

After both fixes the AIZ trace first strict error advances
from F7171 to F7235 (1049 → 1044 errors, F7172-F7234 all
green).  F7235 is a Sonic-side rolling `g_speed` divergence
(`g_speed = 0x0768` engine vs `0x0800` ROM at the rolling
top-speed cap) that is independent of the kill-plane chain
and out of scope for this iteration.

ROM cite block (sonic3k.asm:36032-36042, `MoveSprite`):

```
MoveSprite:
    move.w  x_vel(a0),d0
    ext.l   d0
    lsl.l   #8,d0
    add.l   d0,x_pos(a0)
    move.w  y_vel(a0),d0     ; OLD y_vel for position
    addi.w  #$38,y_vel(a0)   ; gravity AFTER capturing OLD y_vel
    ext.l   d0
    lsl.l   #8,d0
    add.l   d0,y_pos(a0)     ; y_pos += OLD y_vel >> 8
    rts
```

Sources: `Sonic3kAIZEvents.updateAiz2SonicResize2`,
`SidekickCpuController.updateDeadFalling` (commit
`bugfix/ai-aiz-f7171-leader-fast-redux`).

## CNZ1 Trace F7872 — Tails 1-Pixel x Drift After Sonic Jumps Off Rising Platform (OPEN)

**Status:** Investigated, cause identified, defer until OnObj clear timing is reworked.

**Symptom**

`TestS3kCnzTraceReplay#replayMatchesTrace` first strict error at frame 7872:
```
tails_x mismatch (expected=0x0CED, actual=0x0CEC) — 2774 errors total
```

The divergence is a single-pixel offset on Tails's x-position that propagates
forward (every subsequent `tails_x` is 1 px below the expected value). All
other Tails state (`tails_y`, `tails_x_speed`, `tails_y_speed`,
`tails_g_speed`, `tails_air`, `tails_rolling`) tracks ROM exactly through the
divergence window. The 1-pixel offset is small but downstream cascades make
the rest of the trace fail.

**Trigger frame context (F7872)**

Sonic was standing on the CNZ rising platform (`onObj=04` =
`CnzRisingPlatformInstance`) and jumped this frame:
- ROM `state_snapshot` records `air 0->1`, `rolling 0->1`,
  `on_object 1->0` for Sonic at vfc=7873.
- ROM `tailsCpu ... branch=leader_on_object` (Tails CPU saw Sonic still
  carrying `Status_OnObj` mid-frame).
- Engine `eng-tails-cpu ... branch=follow_steering` (Tails CPU saw Sonic's
  `isOnObject()` already false).

**Root cause**

ROM `Tails_CPU_Control` at `loc_13DA6` (sonic3k.asm:26688-26700) reads Sonic's
**current** `Status_OnObj` mid-frame:

```
loc_13DA6:
    lea     (Pos_table).w,a2
    move.w  #$10,d1
    lsl.b   #2,d1
    addq.b  #4,d1
    move.w  (Pos_table_index).w,d0
    sub.b   d1,d0
    move.w  (a2,d0.w),d2
    btst    #Status_OnObj,status(a1)   ; <-- current Sonic.Status_OnObj
    bne.s   loc_13DD0                  ; if set: skip subi.w #$20,d2
    cmpi.w  #$400,ground_vel(a1)
    bge.s   loc_13DD0
    subi.w  #$20,d2                    ; bias target X by 0x20 left
loc_13DD0:
    ...
```

ROM `Sonic_Jump` (sonic3k.asm:23288-23354, mirrored in s2.asm:37027-37080
and s1disasm/_incObj/01 Sonic.asm:1118-1167) sets `Status_InAir` and clears
`Status_Push`, but **does not** clear `Status_OnObj`. The OnObj bit is
cleared later in the frame, during object processing, by
`sub_1FF1E` (sonic3k.asm:44306-44319) and `loc_1FFC4` (sonic3k.asm:
44369-44381) when the platform that previously hosted Sonic detects he is
no longer standing on it (`bclr p1_standing_bit,status(a0)` followed by
`bclr Status_OnObj,(Player_1+status).w` when that bit was set). Because
ROM runs Player_1 → Player_2 → ObjectsLoad in that order
(sonic3k.asm:21626+ vs. 22300+ vs. 24450+), `Tails_CPU_Control` (called
from Player_2's update) sees Status_OnObj still set on the jump frame.

The engine clears OnObj at two earlier points:
- `PlayableSpriteMovement.doJump` calls `sprite.setOnObject(false)` at
  jump time (current line 642).
- `ObjectManager.SolidContacts.processInlineObjectForPlayer` air-unseat
  path calls `player.setOnObject(false)` whenever the player is airborne
  with a riding-object association (current line 4536).

Both run inside Sonic's player tick, so by the time `SidekickCpuController.
normalStep` runs (called from Tails's player iteration via
`SpriteManager.update` at line 349), `effectiveLeader.isOnObject()` already
returns `false` and `leadOffset > 0` causes `targetX -= 0x20` to fire.
That is the source of the 1-pixel offset: `subi.w #$20,d2` runs in the
engine but is skipped in ROM, so `dx = targetX - sidekick.getCentreX()` is
biased by 0x20 (0.125 px after the tracking gain), shifting Tails's
follow-steering nudge by 1 pixel west across the next several frames.

**Investigation history (this branch — `bugfix/ai-cnz-f7872-tails-x`)**

Two surgical fix attempts were tried and reverted:

1. **Remove `setOnObject(false)` from `doJump`.** Revealed that
   `ObjectManager.processInlineObjectForPlayer`'s air-unseat path
   (line 4520-4537) **also** clears OnObj as soon as Sonic.air becomes
   true, so removing the doJump line alone does not preserve OnObj
   through Tails CPU.
2. **Read Sonic's previous-frame status snapshot via
   `getStatusHistory(1)`.** Successfully shifts the F7872 branch from
   `follow_steering` to `leader_on_object`, **but** regresses CNZ1 to a
   new first error at F6409: ROM has Sonic airborne with OnObj=false at
   end of F6408, but the engine had recorded OnObj=true in the same
   slot, so `getStatusHistory(1)` returns true while ROM's CURRENT bit is
   false (Sonic was on the giant wheel; engine's clear timing for the
   wheel differs from ROM's). The off-by-one in OnObj timing exists
   across many objects, not just Cnz Rising Platform.

A third option — adding an OR fallback (`previousOnObject ||
leader.isOnObject()`) — also regressed F6409 because the previous-frame
read still mismatched ROM's state.

**Why no clean fix lands here**

Aligning the engine to ROM requires either:

- **(A)** Defer `setOnObject(false)` for *every* path that currently
  clears it during the player tick (doJump, SolidContacts air-unseat,
  per-object overrides) until the object phase, OR
- **(B)** Capture a frame-start `wasOnObjectAtPlayerTickStart` snapshot
  that is invalidated only by the object phase, not by the player tick.

(A) is invasive — touching every solid-object path and risking regressions
in other tests where engine code reads `isOnObject()` mid-tick (e.g.
`PlayableSpriteMovement.java:1984, 2510, 2807` and
`SidekickCpuController.java:1918, 1953`).
(B) is cleaner architecturally, but adding a new shadow flag and
threading it through `AbstractPlayableSprite.endOfTick` plus
`SpriteManager.beginPlayableFrame` is not a small surgical edit; it
needs cross-game (S1/S2/S3K) test coverage to avoid silently breaking
the existing leader-on-object branches in those games (S2 follow code
also reads `Status_OnObj` from the leader at `s2.asm:38933`+).

**Update (branch `bugfix/ai-on-object-at-frame-start`):** The
infrastructure half of option **(B)** has landed: a new
`onObjectAtFrameStart` field on `AbstractPlayableSprite` plus
`captureOnObjectAtFrameStart()` / `getOnObjectAtFrameStart()`
accessors, with the capture wired into all three
`SpriteManager.beginPlayableFrame` call sites
(`update`, `updateWithoutInput`, `warmUpCpuSidekicksOnly`) so every
playable sees its frame-start OnObj snapshotted before any player
tick can mutate it. The accessors are covered by
`TestOnObjectAtFrameStartSnapshot`. **However the snapshot is not yet
plumbed into `SidekickCpuController.normalStep`** because swapping it
in for the existing
`isOnObject() && !getAir()` gate uncovered a deeper engine-side OnObj
divergence that the snapshot alone cannot fix:

- Dropping the `&& !getAir()` filter and using only the snapshot at
  the line 812 / 1058 / 1119 gates DOES correctly flip CNZ F7872
  from engine `follow_steering` to the ROM-matching
  `leader_on_object` branch — F7872 advances to F7919.
- BUT it regresses AIZ1 to a new first error at F2021 (was F7381):
  Tails ends up 1 px more EAST than ROM. At F2021, ROM Sonic has
  `Status_OnObj=0` mid-frame (lua records `branch=fallthrough_sub20`)
  but the engine's frame-start snapshot reads OnObj=true, because
  the engine had `onObject=true` set at the END of F2020 while ROM
  had cleared it. So engine and ROM disagree about the frame-start
  OnObj value itself — the snapshot accurately captures the engine's
  state, but the engine's state diverged from ROM at F2020-end.

This is a different layer of the same bug: engine paths that SET or
CLEAR OnObj at object boundaries don't match ROM's
`sub_1FF1E`/`loc_1FFC4`/`SolidObjectTop` timing, so even a perfect
mid-frame snapshot inherits that earlier drift. Fully closing the
gap therefore needs option (A) — aligning the engine's OnObj clear
timing with ROM's object-phase processing — or a more selective
combination (e.g. snapshot only when the engine and ROM agree at the
frame boundary, fall back to live read otherwise). Both require
deeper investigation than this branch carried.

The snapshot infrastructure is left in place as the foundation for
the eventual option (B) wiring; until then `SidekickCpuController`
keeps its existing `isOnObject() && !getAir()` heuristic and
documents the unresolved root cause (engine OnObj clear/set timing
vs ROM object-phase processing) inline at the gate.

**Removal condition**

Either:

- A new `AbstractPlayableSprite.wasOnObjectAtFrameStart` snapshot is
  added, set by `beginPlayableFrame` from the prior frame's recorded
  state, and `SidekickCpuController` reads it for `loc_13DA6`'s
  Status_OnObj test in place of `effectiveLeader.isOnObject()`. S1/S2
  trace replay tests must also remain green, since the same
  pattern exists in `s1disasm/_incObj/01 Sonic.asm:1118-1167` and
  `s2.asm:37027-37080,38933+`.
- Or: every engine-side path that clears OnObj during the player tick
  is rerouted to defer the clear into the object phase, matching ROM
  ordering.

Either change must keep `TestS3kCnzTraceReplay` (and AIZ/S1/S2 trace
replays) green throughout.

**File pointers (engine state today, master branch)**

- `src/main/java/com/openggf/sprites/managers/PlayableSpriteMovement.java`
  line 631-660 — `doJump` clears OnObj at jump time.
- `src/main/java/com/openggf/level/objects/ObjectManager.java`
  line 4502-4537 — `processInlineObjectForPlayer` air-unseat path
  clears OnObj as soon as the player becomes airborne while a
  ridingState is held.
- `src/main/java/com/openggf/sprites/playable/SidekickCpuController.java`
  line 812 — leader_on_object check uses
  `effectiveLeader.isOnObject() && !effectiveLeader.getAir()`. The
  `&& !getAir()` half is also redundant compared to ROM
  (sonic3k.asm:26690 reads Status_OnObj alone) and should be revisited
  alongside option (A) or (B).

**Sources:** `bugfix/ai-cnz-f7872-tails-x` investigation notes (this
branch's commit body); `target/trace-reports/s3k_cnz1_context.txt`
F7860-F7880; `src/test/resources/traces/s3k/cnz/aux_state.jsonl.gz`
F6408-F7873.

**Update (branch `bugfix/ai-onobj-clear-timing-alignment`):** Wired the
existing frame-start snapshot into the three `loc_13DA6` mirror gates
(`SidekickCpuController.normalStep`, `resolveFollowSteeringDx`,
`resolveObjectOrderNudgeDx`) using
`effectiveLeader.getOnObjectAtFrameStart()` (no `&& !getAir()` filter,
to match ROM `btst #Status_OnObj,status(a1) / bne loc_13DD0` which tests
OnObj alone — `sonic3k.asm:26690-26691`). Result confirms the deeper
clear/set-timing divergence:

- **CNZ first-error advances F7872 -> F7919.** F7872 (Sonic-jumps-off-
  rising-platform) now correctly takes ROM's `leader_on_object` branch.
  F7919 is a NEW, distinct downstream divergence: `tails_g_speed
  expected=-0x588 actual=-0x800`, `tails_y_speed expected=0x400
  actual=-0x800`, `tails_x_speed expected=0x004 actual=-0x800`. All
  three velocities pinned to `-0x800` is the spring impulse magnitude
  used by `Obj81_Spring`/`Obj_VertSpring` family (sonic3k.asm:46850+).
  Tails is hitting a spring 47 frames later that ROM does not. Out of
  scope for OnObj timing — separate bug to investigate.
- **AIZ regresses F7381 -> F2021** (`tails_x expected=0x1967
  actual=0x1968`, Tails 1 px EAST of ROM). Reproduces the prior round's
  observation. ROM trace data at F2000-F2025
  (`src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/physics.csv.gz`):
  Sonic `status_byte=0x06` (InAir|Roll, OnObj=0) for F2000-F2019, then
  `0x03` (Facing_left|InAir, OnObj=0) F2020+. Sonic is **never** on an
  object across this airborne-roll-then-uncurl window — `Status_OnObj=0`
  throughout. ROM `tails_cpu_normal_step` events at F2020/F2021 record
  `delayed_stat=0x06`, `loc_13dd0_branch=fallthrough_sub20` — i.e.
  Tails CPU takes the `subi.w #$20,d2` bias path. The engine's
  `getOnObjectAtFrameStart()` returns `true` at F2021, meaning the
  engine **set or kept** Sonic's `onObject` true somewhere during the
  airborne F2000-F2020 window when ROM had it cleared. Engine's
  `setOnObject(true)` call sites in `ObjectManager.java` (lines 5701,
  5821, 5910, 6009, 6193) all fire from
  `MvSonicOnPtfm`-equivalent landing branches; ROM gates these with
  `btst #Status_InAir,status(a1) / bne <air-unseat>` (sonic3k.asm:
  41021-41031, 41070-41084, 41117-41128, 41798-41812). The candidate
  divergence is one of those `apply` blocks running for an airborne
  Sonic, but pinpointing which object/path requires diagnostic
  capture across the window — too large for this branch.

**Status this branch:** the clean fix does not land. The wiring change
is **not committed** because it regresses AIZ-full. The deeper engine
OnObj set/clear alignment with ROM's `SolidObjectFull*_1P` /
`SolidObjectTop*_1P` air-unseat gating remains the prerequisite. Next
round needs to identify the AIZ1 F2000-F2020 object whose engine-side
`apply` path sets `onObject=true` for an airborne Sonic, gate it on
`!player.getAir()` (matching ROM's `btst #Status_InAir`), and then
re-attempt the snapshot wiring.
