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
2. [AIZ1 Tails Rolling-Airborne Early Landing (Trace Frame 2150)](#aiz1-tails-rolling-airborne-early-landing-trace-frame-2150)
3. [CNZ1 CPU Tails Ground Over-Deceleration (Trace Frame 318)](#cnz1-cpu-tails-ground-over-deceleration-trace-frame-318)

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

## CNZ1 Trace Divergence (Post-F318 Fix)

**Location:** Unknown — under investigation. No longer ground-friction; now likely a missing object-collision (bumper/spring/switch) or slope-jump math.
**Trace reference:** `src/test/resources/traces/s3k/cnz`, first strict error at frame 383.

### Status (2026-04-24)

Frame 318 was caused by `AbstractTraceReplayTest.applyRecordedFirstSidekickState` calling `setMoveLockTimer(0)` every frame, which defeated ROM `Player_SlopeRepel`'s 30-frame move_lock counter and caused the engine to re-apply a −0x80 slip impulse every frame instead of skipping for 30 frames after the initial slip. Fixed in commit `28940b604` (preserve the engine's own move_lock across the per-frame reseed).

The CNZ1 trace replay's first strict error moved from frame 318 to frame 383 (+65 frames; total errors 5024 → 4997).

### Symptom (new)

At trace frame 383, ROM takes Tails from status `0x01` (on ground, direction left) to status `0x07` (airborne, rolling, direction left) while injecting a large leftward x_vel impulse and upward y_vel:

- F382 (both ROM and engine): Tails at `x=0x03BD, y=0x06DF, x_vel=0xFDFD, y_vel=0x00D3, angle=0xF0, air=0, status=0x01`
- F383 ROM: `x_vel=0xFB86 (−0x047A), y_vel=0xFAD5 (−0x052B), air=1, status=0x07` — delta_x = −0x277, delta_y = −0x5FE (large simultaneous push)
- F383 engine: `x_vel=−0x0199` (delta_x ≈ +0x6A, opposite sign)

Engine and ROM both transition Tails to `air=1, rolling=1` on this frame, but the velocity impulse is inverted.

### Suspected Cause

The combined magnitude and simultaneous change of x_vel and y_vel look like a ROM-side object-collision impulse (bumper, spring, hover fan, etc.) rather than a self-jump from input. CNZ1 has many such objects in the opening area. The engine may be missing the specific object at that position, or applying the collision impulse in the wrong direction. Candidates:

- A CNZ bumper that the engine hasn't placed at the right position
- A CNZ spring with a rotated launch angle (engine's launch math may not match)
- A slope-jump: Tails's x_vel might get rotated by the angle-0xF0 slope; if the engine computes the rotation with a different sign convention, x_vel flips

### Removal Condition

Remove once `TestS3kCnzTraceReplay`'s first strict error moves past frame 383 AND the root cause is identified.
