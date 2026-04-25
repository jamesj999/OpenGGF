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
2. [CNZ1 Trace F1685 — Tails CPU Spurious Despawn on Barber-Pole→Wire-Cage Object Switch (FIXED)](#cnz1-trace-f1685--tails-cpu-spurious-despawn-on-barber-polewire-cage-object-switch-fixed)
3. [CNZ1 Trace F1740 — Wire Cage restoreObjectLatchIfTerrainClearedIt Overrode Slope-Repel Slip (FIXED)](#cnz1-trace-f1740--wire-cage-restoreobjectlatchifterrainclearedit-overrode-slope-repel-slip-fixed)
4. [CNZ1 Trace F1758 — Wire Cage Recapture vs Slope-Repel Race Condition](#cnz1-trace-f1758--wire-cage-recapture-vs-slope-repel-race-condition)
5. [AIZ1 Trace F2590 — Tails CATCH_UP_FLIGHT Trigger Path Mismatch](#aiz1-trace-f2590--tails-catch_up_flight-trigger-path-mismatch)

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

## CNZ1 Trace F1758 — Wire Cage Recapture vs Slope-Repel Race Condition

**Location:** `CnzWireCageObjectInstance.continueRide()` cooldown branch / `Player_SlopeRepel` / cage capture-vs-slope-repel ordering.
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 1758 (after F1740 fix in iter-11).

### Status (2026-04-25 iter-12)

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

Remove once `TestS3kCnzTraceReplay`'s first strict error advances past F1758.

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

## AIZ1 Trace F2590 — Tails CATCH_UP_FLIGHT Trigger Path Mismatch

**Location:** `SidekickCpuController.updateCatchUpFlight()` 64-frame gate vs. ROM's actual trigger-path used at this frame.
**Trace reference:** `src/test/resources/traces/s3k/aiz1_to_hcz_fullrun`, first strict error at frame 2590 (after iter-7 fixes).

### Status (2026-04-25)

Iter-6 fixed F2405 (engine never despawning Tails) by aligning the render-flag visibility Y-margin with ROM `Render_Sprites` (24 instead of 32). First error advanced F2405 → F2465.

Iter-7 fixed F2465 (stale x_speed=-0x01F9 carried across the despawn) by zeroing velocities on `TailsRespawnStrategy.beginApproach()` per ROM `loc_13B50`. First error advanced F2465 → F2590.

Iter-8 implemented BK2 P2 controller-input parsing (commit 33bfcc78d) and a fix for the pre-existing `p2Logical = p2Held` bug, but inspection of all three trace BK2 movies (S3K AIZ, S3K CNZ, S2 EHZ) showed **zero P2 input frames**. So F2590 is not driven by the Ctrl_2 A/B/C/START button-press path and another mechanism is responsible.

### Symptom

`tails_y_speed mismatch (expected=-0400, actual=0x02D8)` at F2590. ROM's CSV at trace frame 0x0A1E shows `sk_routine` transitioning 02 (CATCH_UP_FLIGHT) → 04 (FLIGHT_AUTO_RECOVERY) with y_speed=-0x0400 (the `Tails_JumpHeight` cap value). Engine still in CATCH_UP_FLIGHT with continued downward gravity (y_speed=+0x02D8 at +0x38/frame).

### Diagnosed Cause (UPDATED iter-9)

**The trace `sidekick_routine` field is NOT `Tails_CPU_routine`** — it is the sprite OST routine byte (offset 5 of the object slot). The 02→04 transition at frame 2590 represents the SPRITE routine moving from "alive, normal" (2) to "hurt" (4), via `HurtCharacter` after Tails contacts a Spike object (slot 22 in the AIZ trace, type `loc_24090`).

ROM path: `loc_24090` (Spikes) → `SolidObjectFull` → `SolidObject_cont` → `sub_24280` → `HurtCharacter(Tails)` → sets sprite routine = HURT (4) and `y_vel = -0x0400` (the standard upward hurt-bounce).

Engine equivalent: `Sonic3kSpikeObjectInstance` → `AbstractSpikeObjectInstance.onSolidContact()` → `applyHurt(currentX)` → `y_speed = -0x400`.

The path exists in the engine but doesn't fire at F2590 because of a 1-pixel geometric miss in the descending-player vs. top-solid-surface contact test in `ObjectManager.SolidContacts.processInlineObjectForPlayer()` / its inner `resolveContact()`. Engine's Tails centreY ≈ 0x03AF; spike top surface ≈ 0x03B0; the engine's overlap threshold rejects this 1-pixel contact while ROM accepts it.

### Suspected Cause

The engine's vertical overlap test for descending players hitting a top-solid surface uses a strict `>` rather than `>=` (or is missing a 1-pixel lookahead) that ROM's `SolidObject_cont` overlap test does not. Verified via the agent's frame-by-frame gravity trace: Tails is in free-fall (objectControlled=false, gSpeed=0) accumulating +0x38/frame, and the rejected contact is exactly the moment Tails would touch the spike top.

### Candidate Fix

`ObjectManager.SolidContacts.resolveContact()` or `processInlineObjectForPlayer()` — descending-player landing geometry. A `≥`-vs-`>` change OR a 1-pixel lookahead on the bottom-of-player vs. top-of-spike test.

Cross-game risk: the contact code is shared across all three games. Any change must be verified against S1/S2 traces. ROM disassemblies for `s1disasm` and `s2disasm` should be consulted to confirm the exact overlap-threshold semantics before changing.

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
