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
