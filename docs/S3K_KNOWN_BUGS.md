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
4. [CNZ1 Trace F1758 — Wire Cage Airborne-Capture object_control Bit 0 Missing (FIXED)](#cnz1-trace-f1758--wire-cage-airborne-capture-object_control-bit-0-missing-fixed)
5. [CNZ1 Trace F1791 — Tails CPU Auto-Jump Trigger Bit-7 Object Control Gate (FIXED)](#cnz1-trace-f1791--tails-cpu-auto-jump-trigger-bit-7-object-control-gate-fixed)
6. [AIZ1 Trace F2590 — Tails CATCH_UP_FLIGHT Trigger Path Mismatch](#aiz1-trace-f2590--tails-catch_up_flight-trigger-path-mismatch)

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
